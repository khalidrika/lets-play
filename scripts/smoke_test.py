#!/usr/bin/env python3
"""Live HTTP functional checks. Requires running API + MongoDB. No dependencies."""
import json
import os
import sys
import uuid
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

BASE = os.environ.get('BASE_URL', 'http://localhost:8080').rstrip('/')


def request(method, path, body=None, token=None):
    headers = {'Accept': 'application/json'}
    if body is not None:
        headers['Content-Type'] = 'application/json'
    if token:
        headers['Authorization'] = 'Bearer ' + token
    payload = json.dumps(body).encode() if body is not None else None
    req = Request(BASE + path, data=payload, method=method, headers=headers)
    try:
        with urlopen(req, timeout=15) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except HTTPError as exc:
        raw = exc.read()
        return exc.code, json.loads(raw) if raw else None


def expect(status, method, path, body=None, token=None):
    got, content = request(method, path, body, token)
    if got != status:
        raise AssertionError(f'{method} {path}: expected {status}, got {got}: {content}')
    print(f'PASS {got} {method} {path}')
    return content


def main():
    suffix = uuid.uuid4().hex[:12]
    password = 'ExampleSecret123!'
    alice = {'name': 'Alice', 'email': f'alice-{suffix}@example.com', 'password': password}
    bob = {'name': 'Bob', 'email': f'bob-{suffix}@example.com', 'password': password}
    a = expect(201, 'POST', '/auth/register', alice)['accessToken']
    b = expect(201, 'POST', '/auth/register', bob)['accessToken']
    expect(409, 'POST', '/auth/register', alice)
    expect(401, 'POST', '/auth/login', {**alice, 'password': 'wrong'})
    expect(200, 'GET', '/products')
    expect(401, 'POST', '/products', {'name': 'Mouse', 'description': 'Good', 'price': 1})
    expect(400, 'POST', '/products', {'name': 'Mouse', 'description': 'Good', 'price': -1}, a)
    p = expect(201, 'POST', '/products', {'name': 'Mouse', 'description': 'Good', 'price': 19.5}, a)
    pid = p['id']
    assert p['userId']
    expect(200, 'GET', '/products/' + pid)
    expect(200, 'GET', '/products/mine', token=a)
    expect(403, 'PUT', '/products/' + pid, {'name': 'Changed', 'description': 'No', 'price': 1}, b)
    expect(403, 'DELETE', '/products/' + pid, token=b)
    expect(403, 'GET', '/users', token=a)
    user_summary = expect(200, 'GET', '/users/me', token=a)
    assert 'password' not in user_summary
    expect(200, 'PUT', '/products/' + pid,
           {'name': 'Mouse v2', 'description': 'Good', 'price': 20.0}, a)
    if os.getenv('ADMIN_EMAIL') and os.getenv('ADMIN_PASSWORD'):
        admin_token = expect(200, 'POST', '/auth/login', {
            'email': os.environ['ADMIN_EMAIL'], 'password': os.environ['ADMIN_PASSWORD']
        })['accessToken']
        expect(200, 'GET', '/users', token=admin_token)
        created = expect(201, 'POST', '/users', {
            'name': 'Charlie', 'email': f'charlie-{suffix}@example.com',
            'password': password, 'role': 'USER'
        }, admin_token)
        assert 'password' not in created
        uid = created['id']
        expect(200, 'GET', '/users/' + uid, token=admin_token)
        expect(200, 'PUT', '/products/' + pid,
               {'name': 'Admin changed it', 'description': 'Valid', 'price': 22}, admin_token)
        expect(204, 'DELETE', '/users/' + uid, token=admin_token)
    else:
        print('SKIP admin checks: set ADMIN_EMAIL and ADMIN_PASSWORD in environment')
    expect(204, 'DELETE', '/products/' + pid, token=a)
    expect(404, 'GET', '/products/' + pid)
    print('ALL ASSERTIONS PASSED')


if __name__ == '__main__':
    try:
        main()
    except (AssertionError, URLError, KeyError, ValueError) as exc:
        print('FAILED:', exc, file=sys.stderr)
        sys.exit(1)

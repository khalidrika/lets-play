Let's Play — Secure REST API
Spring Boot 4.1.2 / Java 21 / MongoDB / Spring Security / JWT (HS256).

A small e-commerce-style REST API with users, products, owners and administrators. Responses are JSON (except successful DELETE, which intentionally has no body). This project contains no SQL database, no frontend, and no default admin password.
1. Prerequisites
- Java 21 (java -version)
- Apache Maven 3.6.3+ (mvn -version)
- MongoDB 7+ reachable at localhost:27017, OR Docker + docker compose, OR a MongoDB Atlas URI
- openssl for generating a secret (or any cryptographically secure 32+ byte random generator)
Ubuntu without sudo: a pre-installed MongoDB service or a MongoDB Atlas connection can be used. Docker is optional, not required. Maven can be extracted into $HOME if you cannot use apt.

2. Run locally
cp .env.example .env
openssl rand -base64 32
Edit .env and set JWT_SECRET to that output (never commit it). Replace ADMIN_EMAIL and ADMIN_PASSWORD with your own; use a unique password of at least 12 characters. The sample admin values in .env.example are placeholders and are not functional credentials.

Start MongoDB using one option:
# With Docker (only if installed):
docker compose up -d mongodb

# Or use your already-running MongoDB server / MongoDB Atlas.
# Atlas: set MONGODB_URI in .env to its connection string.
Start the API:
./scripts/start.sh
# OR load .env yourself and run:
# set -a && source .env && set +a && mvn spring-boot:run
API base URL: http://localhost:8080

Admin bootstrap: on the first boot of an empty database, an ADMIN account is created from ADMIN_EMAIL/`ADMIN_PASSWORD`. If any admin already exists, bootstrap does not change it. Normal public registration always creates role USER. Never accept a role from the registration request.
Run tests / package
set -a && source .env && set +a
mvn test
mvn clean package
java -jar target/lets-play-1.0.0.jar
The Maven tests are pure unit tests with Mockito and do not require MongoDB.

With the API + MongoDB actually running, test end-to-end behavior using Python 3 (no extra packages):
set -a && source .env && set +a
python3 scripts/smoke_test.py
The smoke test creates randomly named accounts/products and checks 400/401/403/404/409, ownership, admin functionality and 204 delete responses. This does require a running API.
3. API endpoint matrix
Method	Endpoint	Access	Expected success
POST	/auth/register	Public	201 + access token
POST	/auth/login	Public	200 + access token
GET	/products	Public	200 + list
GET	/products/{id}	Public	200 + product
GET	/products/mine	Authenticated	200 + own products
POST	/products	Authenticated	201 + product + Location
PUT	/products/{id}	Owner or ADMIN	200 + product
DELETE	/products/{id}	Owner or ADMIN	204 (empty body)
GET	/users/me	Authenticated	200 + own user summary
GET	/users	ADMIN	200 + list
GET	/users/{id}	ADMIN	200 + user
POST	/users	ADMIN	201 + user + Location
PUT	/users/{id}	ADMIN	200 + user
DELETE	/users/{id}	ADMIN	204 (empty body)


/users/me is a narrow exception to admin-only user endpoints, allowing users to see only themselves. Passwords, even password hashes, never appear in responses.
3.1 Register a normal user
curl -i -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Alice","email":"alice@example.com","password":"SamplePass123!"}'
Response example:
{"accessToken":"<SIGNED_JWT>","tokenType":"Bearer","expiresIn":1800}
3.2 Log in (admin or normal user)
curl -i -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"SamplePass123!"}'
Copy accessToken into an environment variable for your shell (TOKEN='the.jwt.token'), then:
3.3 Public products
curl -i http://localhost:8080/products
curl -i http://localhost:8080/products/REPLACE_PRODUCT_ID
3.4 Create a product (authenticated user)
curl -i -X POST http://localhost:8080/products \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Gaming Headset","description":"USB-C gaming headset","price":79.99}'
The userId is assigned from the verified JWT, not from user JSON. Clients cannot decide who owns a product.
3.5 Update / delete product
curl -i -X PUT http://localhost:8080/products/REPLACE_PRODUCT_ID \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Wireless Headset","description":"Updated description","price":89.50}'

curl -i -X DELETE http://localhost:8080/products/REPLACE_PRODUCT_ID \
  -H "Authorization: Bearer $TOKEN"
Non-owners who are not admins get 403 Forbidden. Nonexistent products get 404 Not Found.
3.6 Admin CRUD users
First sign in with the ADMIN_EMAIL and ADMIN_PASSWORD configured in .env and use that token. For example:
curl -i http://localhost:8080/users -H "Authorization: Bearer $TOKEN"

curl -i -X POST http://localhost:8080/users \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Bob","email":"bob@example.com","password":"OtherPass123!","role":"USER"}'

curl -i -X PUT http://localhost:8080/users/REPLACE_USER_ID \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Bob Updated","email":"bob@example.com","role":"USER"}'

curl -i -X DELETE http://localhost:8080/users/REPLACE_USER_ID \
  -H "Authorization: Bearer $TOKEN"
The full PUT requires name/email/role. An omitted password leaves it unchanged. A supplied password is BCrypt-hashed before storage. An admin cannot delete or demote their own admin account. Deleting a user also deletes their products.
4. Data design
users collection:
{"_id":"...","name":"Alice","email":"alice@example.com","password":"$2a$12$...","role":"USER"}
products collection:
{"_id":"...","name":"Gaming Headset","description":"USB-C","price":79.99,"userId":"..."}
The user has many products. The product references its owner using userId. MongoDB indexes users.email uniquely and products.userId to speed up ownership lookup. Emails are normalized to lowercase for case-insensitive identity.
5. Status codes and errors
Status	Meaning	Example
400	Invalid JSON or field validation	negative price, missing email
401	Invalid credentials, no token, expired token	failed login
403	Authenticated but insufficient permission	modifying someone else's product
404	Resource doesn't exist	missing product ID
405	HTTP method not supported	PATCH instead of PUT
409	Conflict	email already registered
503	Database temporarily unavailable	MongoDB connection failure
500	Unexpected internal failure (sanitized)	unexpected server exception


Controller-layer errors use a consistent JSON envelope like:
{
  "timestamp": "2026-10-29T12:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Only the owner or an admin may modify this product",
  "path": "/products/123"
}
Security filter 401/403 responses are deliberately minimal JSON with status, error, message.
6. Security model
- Spring Security validates JWT signatures (HS256), issuer and expiry; JWTs expire after 30 minutes.
- JWT subject is the user's database ID. Roles are reloaded from MongoDB on every authenticated request, so deleting or demoting an admin takes effect immediately.
- Public registration always creates USER; only ADMIN can use the role-bearing user endpoints.
- Passwords are salted/hashed with BCrypt strength 12; hashes are never serialized by API DTOs.
- No raw MongoDB query strings or client-controlled operators are executed; repositories receive typed values.
- Product ownership is checked in service methods (defense against changes in HTTP route configuration).
- CORS origins configured via CORS_ALLOWED_ORIGINS (never use wildcard in production).
- No session cookies; CSRF is disabled only because authentication uses explicitly supplied bearer headers, not cookies.
- HTTPS is required in production: use a TLS-terminating reverse proxy / load balancer and secure MongoDB connection. The local development example uses HTTP only.
- JWT_SECRET, admin password and MongoDB credentials stay in environment variables, never in Git.
Important production limitations
This is a functional learning project, not a production-ready service. Recommended hardening before real deployment: IP-aware distributed login rate limiting (Redis), token revocation/password-change token versions, observability, pagination, stable indexes migrations, MongoDB transactions on replica sets for cascaded deletion, integration tests against MongoDB Testcontainers, security audit, and reverse proxy HTTPS. As with all real servers, unexpected problems can still produce 5XX responses; the goal is to return a controlled JSON response instead of an unhandled exception or leaking a stack trace.
7. Code map
src/main/java/com/letsplay/api/
  LetsPlayApplication.java
  model/         User, Product, Role
  dto/           input and output schemas (never expose password)
  repository/    MongoRepository for User / Product
  service/       authentication and CRUD business rules
  controller/    REST endpoints
  config/        security/JWT/admin setup
  exception/     global JSON errors
src/test/java/com/letsplay/api/service/
  AuthServiceTest, ProductServiceTest, UserServiceTest
References
- https://docs.spring.io/spring-boot/reference/
- https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- https://www.mongodb.com/docs/manual/
See also EXPLAIN_DARIJA.md (Darija walk-through) and postman/lets-play.postman_collection.json.

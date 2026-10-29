# شرح المشروع بالدارجة (Audit / Oral)

## شنو كيدير Let's Play؟

هاد المشروع Backend REST API بـ Spring Boot و MongoDB. عندنا جوج Documents فـ MongoDB:

- **User:** `id, name, email, password, role`.
- **Product:** `id, name, description, price, userId`.

`userId` هو ID ديال المالك. User واحد يقدر يكون عندو بزاف ديال Products (one-to-many).

## كيفاش كتدوز Request؟

1. Client (Postman ولا frontend) كيسيفط HTTP request لـ `localhost:8080`.
2. Spring Security Filter Chain كتأكد واش endpoint Public ولا Protected. إلا كان Bearer JWT، كتفك التوقيع وكتراجع expiration وissuer، وكتجيب role الحالية من MongoDB.
3. Spring MVC كيوصل للـ `@RestController` المناسب (`AuthController`, `ProductController`, `UserController`).
4. `@Valid` كتتحقق من البيانات ديال DTO قبل ما يوصل الطلب للـ Service.
5. Controller كينادي Service، اللي فيه business logic وchecking ديال owner/admin.
6. Service كيتعامل مع `MongoRepository`، اللي كيدير عمليات MongoDB.
7. النتيجة كتخرج JSON؛ والأخطاء كيتكلف بهم `GlobalExceptionHandler`.

## شكون يقدر يدير شنو؟

- **Visitor:** GET `/products` ولا GET `/products/{id}` بلا Token.
- **USER:** يقدر يسجل، يدخل، ينشئ Products، ويعدل/يمسح غير Products ديالو.
- **ADMIN:** عندو CRUD كامل ديال Users والـ Products كاملين.

فـ `ProductService.checkOwnerOrAdmin` كنقارن `product.userId` مع `jwt.subject`، ولا كنشوف واش `role == ADMIN`. ممنوع نعتمد على `userId` اللي جا من Client فالإنشاء.

## علاش password ماكيبانش؟

`User` كتخزن BCrypt hash فالـ database، ولكن كنعطيو Client غير `UserResponse` اللي فيها `id,name,email,role`. الـ password/hash مكايناش فـ DTO ديال response نهائيا.

## JWT كيفاش خدام؟

`POST /auth/login` كيتأكد من email/password بـ BCrypt، ومن بعد كيولد Token موقّع بـ secret فيه `sub = userId` و `exp`. كل request محمية كتجي بـ:

```text
Authorization: Bearer YOUR_TOKEN
```

Security كتتحقق من Signature/Expiration، وكتقلب فـ DB على الـ User والـ Role الحالية. Token مزور ولا منتهي الصلاحية => 401.

## أخطاء audit مشهورة

- `400`: معلومات غير صالحة (مثلاً price سالب).
- `401`: ما كاينش token أو password غلط أو token خاسر.
- `403`: User حاول يعدل منتوج ديال User آخر.
- `404`: ID ديال Product ولا User ما لقايناهش.
- `409`: Email déjà existant.
- `201`: سجلنا User أو Product جديد.
- `200`: GET, login, update ناجح.
- `204`: Delete ناجح بلا response body.

## علاش ما استعملناش SQL؟

حيت الشرط هو MongoDB. `UserRepository` و `ProductRepository` كيتعاملو مع MongoDB باستعمال Spring Data، و`@Document` كتعني collection.

## كيفاش نجيب Admin؟

كتكتب `ADMIN_EMAIL` و `ADMIN_PASSWORD` فـ `.env` قبل أول Run (مع MongoDB database فارغة). App كينشئ أول admin أوتوماتيكيا. `POST /auth/register` عمرها ما كتخلي User يصنع Admin.

## ملاحظات مهمة

`PUT /users/{id}` admin-only، و`GET /users/me` استثناء باش User يشوف غير الحساب ديالو. CORS محدودة؛ HTTPS خاصو يكون فالـ deployment. Rate limiting ما مطبقش دابا حيث bonus، وخصو implementation محترف إلا بغيت تنشر تطبيق فيه users حقيقيين.

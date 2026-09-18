# SWP391_G2_SE2055-JV

```
backend/    Spring Boot REST API (Java 17, MySQL, Flyway)
frontend/   Giao diện người dùng
docs/       ERD và sơ đồ nghiệp vụ
```

## Chạy backend

Chạy từ trong thư mục `backend/` (file `.env` nằm ở đây):

```
cd backend
./mvnw spring-boot:run
```

Cấu trúc package trong `backend/src/main/java/com/example/SWP391_G2_SE2055_JV`:

```
config/       Security, JPA, Web config, OAuth2 handlers
controller/   REST controllers
service/      Business logic
repository/   Spring Data JPA repositories
entity/       JPA entities
dto/          Request / response objects
enums/        Enum trạng thái và role
exception/    Exception + GlobalExceptionHandler
utils/        Helper dùng chung
```

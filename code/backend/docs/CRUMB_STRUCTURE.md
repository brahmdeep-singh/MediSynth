# CRUMB reference project → MediSynth

## What CRUMB actually contains (inspected from `CRUMB-Backend-main.zip`)

```
CRUMB-Backend-main/
├── pom.xml                       Spring Boot 4.1.1, Java 21, JJWT 0.12.6, Lombok, PostgreSQL
├── mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties
├── .gitignore, .gitattributes
└── src/
    ├── main/java/com/personal/crumb/
    │   ├── CrumbApplication.java
    │   ├── DTO/          AuthResponse (record)
    │   ├── config/       SecurityConfig, JWTFilter
    │   ├── controller/   AuthorizationController, ProductController, HelloController
    │   ├── model/        Users, Role, AuthProvider, UserPrincipal, Product, ProductCategory
    │   ├── repo/         UserRepo, ProductRepo
    │   └── service/      UserService, JwtService, MyUserDetailsService, ProductService
    ├── main/resources/application.properties
    └── test/java/com/personal/crumb/CrumbApplicationTests.java   (contextLoads only)
```

Architecture: layered `controller → service → repo → model`; stateless JWT (HS256, JJWT) checked by a
`OncePerRequestFilter` placed before `UsernamePasswordAuthenticationFilter`; BCrypt; `UserPrincipal` adapts the
`Users` entity to `UserDetails` with a `ROLE_` authority; DTOs as Java records; Lombok on entities; CORS bean for
`http://localhost:5173`.

## What MediSynth keeps (structure, packages, names)

| CRUMB | MediSynth |
|---|---|
| `com.personal.crumb` | `com.personal.medisynth` |
| `CrumbApplication` | `MediSynthApplication` |
| `DTO/`, `config/`, `controller/`, `model/`, `repo/`, `service/` | same six packages |
| `AuthorizationController` (`/api/auth`) | same name and base path |
| `Users`, `UserRepo`, `UserService`, `JwtService`, `MyUserDetailsService`, `UserPrincipal` | same names |
| `SecurityConfig`, `JWTFilter` (in `config/`) | same names and place |
| `AuthResponse` record | same name (richer content) |
| Maven wrapper, `.gitignore`, `.gitattributes` | copied/extended |
| Boot 4.1.1, Java 21, JJWT 0.12.6, Lombok, PostgreSQL driver | same versions |

Added (not in CRUMB): `exception/` (global handler), `validation/` (password rule), Flyway migrations,
`RegistrationRequest`/`DoctorProfile`/`PatientProfile`/`Appointment`/`AuditLog`/`BootstrapState` entities,
rate limiting, admin provisioning, and a real test suite.

CRUMB's business logic (food products, categories, GitHub OAuth) was **not** copied.

## Where MediSynth deliberately differs (security reasons)

| CRUMB | MediSynth |
|---|---|
| `@RequestBody Users` entity binds client JSON, so `role` can be mass-assigned | DTO records only; role/status are decided server-side |
| JWT subject = email, no status check on login | subject = user id; `ACTIVE` status required at login **and** on every request |
| `AuthResponse(email, accessToken)` | `AuthResponse(accessToken, tokenType, expiresInMs, user{id,name,email,role})` |
| Field injection (`@Autowired`) | constructor injection |
| `JwtService.validateToken` swallows all exceptions and `JWTFilter` trusts email in a token before validating | one `extractUserId` that verifies signature, expiry and issuer; role always re-read from the DB |
| `ddl-auto` default, no migrations | Flyway + `ddl-auto=validate` |
| Hard-coded CORS origin, no `PATCH` | configurable origins, `PATCH` allowed |
| One `contextLoads` test | integration tests (real PostgreSQL) plus unit tests for JWT and password rules |

## ⚠ Security finding in the CRUMB repository you uploaded

`CRUMB-Backend-main/src/main/resources/application.properties` contains a **committed database password, a GitHub
OAuth client secret and a JWT signing secret**. Anyone who can read that repo can use them. If that repository is
public or shared: rotate the GitHub OAuth client secret, change the database password and generate a new JWT secret,
then move them to environment variables (as MediSynth does). The values were not reused or copied here.

# TruckCar — User-Identity Microservice

Microservicio de **identidad, autenticación y autorización** de la plataforma
TruckCar. Gestiona el ciclo de vida de usuarios (conductores, coordinadores,
administradores), autenticación JWT (access + refresh tokens) y publica
eventos de dominio al bus de eventos compartido.

---

## Stack

| Tecnología | Versión |
|---|---|
| Java | 21 |
| Spring Boot | 3.3.5 |
| PostgreSQL | 16 |
| Flyway | Migraciones versionadas |
| JWT | jjwt 0.12.6 |
| MapStruct | 1.5.5.Final |
| Docker | Multi-stage build |

---

## Arquitectura del Microservicio

### 1. Diagrama de Contexto (Ecosistema TruckCar)

```mermaid
flowchart TB
    subgraph Clientes["Clientes de la Plataforma"]
        MobileApp["📱 App Móvil (Flutter / RN)<br/>Conductores de Carga"]
        WebPanel["💻 Torre de Control (React Web)<br/>Coordinadores y Despachadores"]
    end

    subgraph Perimetro["Perímetro de Entrada"]
        ApiGateway["🛡️ API Gateway (Kong / AWS API Gateway)<br/>• Enrutamiento • Rate Limiting • SSL"]
    end

    subgraph UserIdentityBoundary["Microservicio User-Identity (Puerto 8080)"]
        direction TB
        Security["🔐 Spring Security 6<br/>Stateless JWT Filter"]
        RestControllers["🎮 Capa REST (Controllers)<br/>AuthController / UserController"]
        BusinessServices["⚙️ Capa de Negocio (Services)<br/>AuthService / UserService / JwtProvider"]
        DataRepositories["💾 Capa de Persistencia<br/>UserRepository / RefreshTokenRepository"]
        
        Security --> RestControllers
        RestControllers --> BusinessServices
        BusinessServices --> DataRepositories
    end

    subgraph Datos["Almacenamiento de Datos"]
        PostgresDB[("🐘 PostgreSQL 16<br/>truckcar_identity")]
    end

    subgraph Eventos["Bus de Eventos Asíncrono"]
        KafkaBus[["📨 Apache Kafka / AWS MSK"]]
        FleetsService["🚛 Microservicio Flotas"]
        DispatchService["📋 Microservicio Asignación"]
    end

    MobileApp -->|"HTTPS /api/v1/auth/*"| ApiGateway
    WebPanel -->|"HTTPS /api/v1/users/*"| ApiGateway
    ApiGateway -->|"HTTP Interno"| Security
    DataRepositories -->|"JDBC / Flyway"| PostgresDB
    BusinessServices -.->|"Publica eventos: UserRegistered, UserRoleChanged"| KafkaBus
    KafkaBus -.->|"Consume eventos"| FleetsService
    KafkaBus -.->|"Consume eventos"| DispatchService
```

### 2. Arquitectura Interna por Capas

```mermaid
flowchart TD
    Req["Petición HTTP entrante"] --> JwtFilter["JwtAuthenticationFilter<br/>(Valida firma HMAC-SHA512)"]
    JwtFilter --> SecContext["SecurityContextHolder<br/>(Establece ROLE y Principal)"]
    SecContext --> Dispatcher["Spring MVC DispatcherServlet"]

    subgraph Presentation["Capa Web / Presentación"]
        Dispatcher --> AuthCtrl["AuthController"]
        Dispatcher --> UserCtrl["UserController"]
        GlobalEx["GlobalExceptionHandler<br/>(@RestControllerAdvice)"] -.->|Intercepta errores| Req
    end

    subgraph ServiceLayer["Capa de Negocio y Seguridad"]
        AuthCtrl --> AuthSvc["AuthServiceImpl"]
        UserCtrl --> UserSvc["UserServiceImpl"]
        AuthSvc -.-> JwtProv["JwtProvider"]
        AuthSvc -.-> PassEnc["BCryptPasswordEncoder"]
        AuthSvc & UserSvc -.-> Mapper["UserMapper (MapStruct)"]
        AuthSvc & UserSvc -.-> DomainEvents["DomainEventPublisher"]
    end

    subgraph PersistenceLayer["Capa de Persistencia"]
        AuthSvc & UserSvc --> UserRepo["UserRepository"]
        AuthSvc --> TokenRepo["RefreshTokenRepository"]
        UserRepo & TokenRepo --> DB[("PostgreSQL 16 / H2")]
    end
```

### 3. Flujo de Autenticación y Autorización (JWT)

#### 3.1. Flujo de Registro e Inicio de Sesión (Emisión de Tokens)

```mermaid
sequenceDiagram
    autonumber
    actor Cliente as 📱 Cliente (App / Web)
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthServiceImpl
    participant PassEnc as PasswordEncoder (BCrypt)
    participant JwtProv as JwtProvider
    participant DB as PostgreSQL 16
    participant Bus as Event Publisher

    %% Caso Registro
    Note over Cliente,Bus: Escenario A: Registro de Nuevo Usuario
    Cliente->>AuthCtrl: POST /api/v1/auth/register
    AuthCtrl->>AuthSvc: register(request)
    AuthSvc->>DB: findByEmail(email)
    alt Email ya existe en BD
        AuthSvc-->>Cliente: 409 Conflict (UserAlreadyExistsException)
    else Email disponible
        AuthSvc->>PassEnc: encode(rawPassword)
        PassEnc-->>AuthSvc: passwordHash
        AuthSvc->>DB: save(User)
        AuthSvc->>JwtProv: generateAccessToken() + generateRefreshToken()
        JwtProv-->>AuthSvc: Token Pair
        AuthSvc->>DB: save(RefreshToken)
        AuthSvc->>Bus: publish(UserRegisteredEvent)
        AuthSvc-->>Cliente: 201 Created: { accessToken, refreshToken, tokenType, expiresIn }
    end

    %% Caso Login
    Note over Cliente,Bus: Escenario B: Inicio de Sesión (Login)
    Cliente->>AuthCtrl: POST /api/v1/auth/login (email, password)
    AuthCtrl->>AuthSvc: login(request)
    AuthSvc->>DB: findByEmail(email)
    AuthSvc->>PassEnc: matches(rawPassword, storedHash)
    alt Credenciales inválidas
        AuthSvc-->>Cliente: 401 Unauthorized (BadCredentialsException)
    else Credenciales válidas
        AuthSvc->>JwtProv: generateAccessToken() + generateRefreshToken()
        AuthSvc->>DB: save(RefreshToken)
        AuthSvc-->>Cliente: 200 OK: { accessToken, refreshToken, tokenType, expiresIn }
    end
```

#### 3.2. Flujo de Validación de Petición Protegida y Control de Acceso (RBAC)

```mermaid
sequenceDiagram
    autonumber
    actor Cliente as 📱 Cliente Autenticado
    participant SecurityFilter as JwtAuthenticationFilter
    participant JwtProv as JwtProvider
    participant SecurityContext as SecurityContextHolder
    participant UserCtrl as UserController
    participant UserSvc as UserServiceImpl

    Cliente->>SecurityFilter: Petición HTTP con Header Authorization: Bearer <token>
    SecurityFilter->>JwtProv: validateToken(token)

    alt Token expirado o firma inválida
        SecurityFilter-->>Cliente: 401 Unauthorized (JwtAuthenticationEntryPoint)
    else Token válido
        SecurityFilter->>JwtProv: getUserIdFromToken() y getRoleFromToken()
        JwtProv-->>SecurityFilter: userId (UUID), role ("CONDUCTOR")
        SecurityFilter->>SecurityContext: setAuthentication(userId, [ROLE_CONDUCTOR])
        SecurityFilter->>UserCtrl: Continúa a controlador correspondiente
        UserCtrl->>UserSvc: getProfile(userId)
        UserSvc-->>Cliente: 200 OK (UserResponse DTO)
    end
```

### 4. Modelo Entidad-Relación (Base de Datos)

```mermaid
erDiagram
    USERS ||--o{ REFRESH_TOKENS : "posee"

    USERS {
        uuid id PK "UUID identificador único"
        varchar email UK "Correo electrónico único"
        varchar password_hash "Hash seguro con algoritmo BCrypt"
        varchar first_name "Nombre(s)"
        varchar last_name "Apellido(s)"
        varchar phone_number "Número de teléfono"
        varchar document_type "CC, CE, PASAPORTE, NIT"
        varchar document_number "Número de documento de identidad"
        varchar role "Rol: CONDUCTOR, COORDINADOR, ADMIN"
        varchar status "Estado: ACTIVE, PENDING_VERIFICATION, SUSPENDED"
        timestamptz created_at "Fecha y hora de creación"
        timestamptz updated_at "Fecha y hora de actualización"
        timestamptz last_login_at "Fecha del último inicio de sesión"
    }

    REFRESH_TOKENS {
        uuid id PK "UUID identificador del token"
        uuid user_id FK "FK referencia a users(id) ON DELETE CASCADE"
        varchar token UK "Token criptográfico opaco único"
        timestamptz expires_at "Fecha límite de vigencia (7 días)"
        boolean revoked "Estado de validez (false=activo, true=revocado)"
    }
```

---

## Requisitos previos

- **Java 21** (JDK)
- **Maven 3.9+** (o usar el wrapper `./mvnw`)
- **Docker** y **Docker Compose**

---

## Levantar localmente

### 1. Iniciar PostgreSQL con Docker Compose

```bash
docker compose up -d postgres
```

Esto levanta PostgreSQL en `localhost:5432` con:
- DB: `truckcar_identity`
- User: `truckcar`
- Password: `truckcar`

> **Tip:** También puedes levantar pgAdmin en `localhost:5050` con
> `docker compose up -d pgadmin`

### 2. Compilar y ejecutar el servicio

```bash
# Compilar (sin tests)
./mvnw clean package -DskipTests

# Opción A: Probar rápidamente en memoria (sin requerir Docker ni PostgreSQL)
./mvnw spring-boot:run "-Dspring-boot.run.profiles=standalone"

# Opción B: Ejecutar con PostgreSQL local (requiere Docker o Postgres en localhost:5432)
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
```

El servicio arranca en `http://localhost:8080`.
*(En el modo `standalone`, la consola de base de datos H2 queda disponible en `http://localhost:8080/h2-console` con JDBC URL `jdbc:h2:mem:truckcar_identity` y usuario `sa`)*.

### 3. Acceder a Swagger UI

```
http://localhost:8080/swagger-ui.html
```

---

## Ejecutar tests

### Tests unitarios

```bash
./mvnw test
```

### Tests de integración (requiere Docker para Testcontainers)

```bash
./mvnw verify
```

> Los tests de integración usan **Testcontainers** para levantar una
> instancia de PostgreSQL real automáticamente — no necesitas tener
> Postgres corriendo manualmente.

---

## Build con Docker

```bash
# Construir la imagen
docker build -t truckcar/user-identity:latest .

# Secreto JWT compartido con api-gateway y Notifications (Base64, >= 256 bits).
# Genérelo una vez, use el mismo valor en los tres servicios y nunca lo versione.
export TRUCKCAR_JWT_SECRET="$(openssl rand -base64 64 | tr -d '\n')"

# Ejecutar el contenedor
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/truckcar_identity \
  -e DB_USERNAME=truckcar \
  -e DB_PASSWORD=truckcar \
  -e TRUCKCAR_JWT_SECRET="$TRUCKCAR_JWT_SECRET" \
  -e SPRING_PROFILES_ACTIVE=local \
  truckcar/user-identity:latest
```

---

## Endpoints principales

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Registro de usuario (solo `CONDUCTOR` o `COORDINADOR`; `ADMIN` responde 400) |
| `POST` | `/api/v1/auth/login` | Login (access + refresh token) |
| `POST` | `/api/v1/auth/refresh` | Renovar access token |
| `POST` | `/api/v1/auth/logout` | Revocar refresh token |
| `GET` | `/api/v1/users/me` | Perfil del usuario autenticado |
| `PUT` | `/api/v1/users/me` | Actualizar perfil propio |
| `GET` | `/api/v1/users/{id}` | Consultar usuario (ADMIN/COORDINADOR) |
| `GET` | `/api/v1/users` | Listado paginado (ADMIN) |
| `PATCH` | `/api/v1/users/{id}/status` | Suspender/reactivar (ADMIN) |
| `PATCH` | `/api/v1/users/{id}/role` | Cambiar rol (ADMIN) |

---

## Variables de entorno

| Variable | Descripción | Default |
|---|---|---|
| `DB_URL` | JDBC URL de PostgreSQL | `jdbc:postgresql://localhost:5432/truckcar_identity` |
| `DB_USERNAME` | Usuario de la BD | `truckcar` |
| `DB_PASSWORD` | Contraseña de la BD | `truckcar` |
| `TRUCKCAR_JWT_SECRET` | Secreto para firmar JWTs (Base64, >= 256 bits). Debe ser **el mismo** en User-Identity, api-gateway y Notifications | *(obligatorio: sin él el servicio no arranca)* |
| `JWT_SECRET` | Nombre heredado del secreto; solo se usa si `TRUCKCAR_JWT_SECRET` no está definido | — |
| `JWT_ACCESS_EXPIRATION` | TTL del access token (ms) | `900000` (15 min) |
| `JWT_REFRESH_EXPIRATION` | TTL del refresh token (ms) | `604800000` (7 días) |
| `SERVER_PORT` | Puerto del servidor | `8080` |

---

## Estructura del proyecto

```
src/main/java/escuelaing/edu/co/truckcar/User_Identity/
├── controller/          # REST controllers
├── service/             # Interfaces de lógica de negocio
│   └── impl/            # Implementaciones
├── repository/          # Spring Data JPA repositories
├── model/               # Entidades JPA + enums
├── dto/
│   ├── request/         # DTOs de entrada
│   └── response/        # DTOs de salida
├── mapper/              # MapStruct (entity <-> DTO)
├── security/            # JWT filter, SecurityConfig
├── exception/           # Excepciones + GlobalExceptionHandler
├── config/              # OpenAPI, beans de configuración
├── event/               # Eventos de dominio + publisher
└── UserIdentityApplication.java
```

---

## Perfiles de Spring

| Perfil | Uso |
|---|---|
| `standalone` | Prueba rápida en memoria (H2, sin requerir Docker/Postgres) |
| `local` | Desarrollo local (SQL logging, PostgreSQL) |
| `dev` | Ambiente compartido de desarrollo |
| `prod` | Producción (ECS Fargate) |
| `test` | Tests de integración |
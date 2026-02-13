# Arhitectură Multi-Tenant SaaS - Aplicație Management pentru Afaceri Mici

> **Versiune:** 1.0  
> **Data:** Februarie 2026  
> **Target:** Saloane, cabinete medicale, service-uri auto, magazine mici

---

## 📋 Cuprins

1. [Prezentare Generală](#1-prezentare-generală)
2. [Cazuri de Utilizare](#2-cazuri-de-utilizare)
3. [Arhitectura Sistemului](#3-arhitectura-sistemului)
4. [Frontend: Angular Web Application](#4-frontend-angular-web-application)
5. [Backend: Spring Boot Multi-Tenant](#5-backend-spring-boot-multi-tenant)
6. [Database: PostgreSQL Database-per-Tenant](#6-database-postgresql-database-per-tenant)
7. [Autentificare & Securitate](#7-autentificare--securitate)
8. [Deployment pe Google Cloud Platform](#8-deployment-pe-google-cloud-platform)
9. [Funcționalități Cheie](#9-funcționalități-cheie)
10. [Monitoring & Observability](#10-monitoring--observability)
11. [Costuri Estimate](#11-costuri-estimate)
12. [Plan de Implementare](#12-plan-de-implementare)
13. [Decizii Arhitecturale](#13-decizii-arhitecturale)
14. [Riscuri & Mitigări](#14-riscuri--mitigări)

---

## 1. Prezentare Generală

### 1.1 Ce Este Aplicația?

O platformă SaaS (Software as a Service) pentru afaceri mici care permite:
- **Tenants** (frizerii, cabinete, service-uri) să își gestioneze mai multe **locații/magazine**
- Fiecare locație are **staff** (admin + personal) și **clienți**
- Staff-ul poate gestiona: programări, fișe clienți, istoric tratamente/servicii
- Clienții primesc **SMS-uri de reminder** pentru programări
- Clienți au acces la **portal propriu** pentru a-și vedea programările și istoricul

### 1.2 Caracteristici Cheie

✅ **Multi-tenant** - Izolare completă date între tenanți  
✅ **Multi-location** - Un tenant poate avea multiple puncte de lucru  
✅ **Role-based access** - Admin, staff, client  
✅ **Cloud-native** - Scalabilă, hosted pe Google Cloud  
✅ **Responsive** - Acces din browser (desktop/mobile/tablet)  
✅ **Automatizări** - SMS reminders, notificări  
✅ **Istoric complet** - Fișe clienți, servicii oferite  

---

## 2. Cazuri de Utilizare

### 2.1 Actor: Proprietar Salon (Tenant Admin)

1. **Signup & Onboarding**
   - Se înregistrează cu email, parolă, nume business, slug (ex: `salon-maria`)
   - Sistemul creează automat: tenant DB, user admin, settings default
   
2. **Setup Locații**
   - Adaugă 1+ locații: "Salon Maria Centru", "Salon Maria Militari"
   - Configurează program lucru, servicii oferite, prețuri

3. **Gestionare Staff**
   - Invită staff prin email → aceștia primesc link activare cont
   - Atribuie roluri: Admin (acces complet) vs Staff (acces limitat)
   - Setează permisiuni per locație

### 2.2 Actor: Staff Salon

1. **Login & Acces Aplicație**
   - Login cu email + parolă (tenant auto-detectat din subdomain/JWT)
   - Dashboard: programări zilei, clienți noi, alerte

2. **Gestionare Clienți**
   - Adaugă client nou: nume, telefon, email, observații
   - Vizualizează istoric: tratamente anterioare, preferințe, alergii
   - Actualizează fișă după fiecare vizită

3. **Programări**
   - Calendar view: zilnic/săptămânal/lunar
   - Creează programare: client, serviciu, dată/oră, staff asignat
   - Sistem trimite SMS automat cu 24h înainte
   - Marchează programare: finalizată/anulată/no-show

### 2.3 Actor: Client

1. **Acces Portal Client**
   - Primește link de la staff sau accesează direct `salon-maria.app.ro/client`
   - Login cu telefon + cod SMS (passwordless) sau email/parolă

2. **Vizualizare Date Proprii**
   - Programări viitoare + istoricul complet
   - Detalii servicii primite, note staff
   - **NU poate crea programări** (only staff can - evităm double booking)

---

## 3. Arhitectura Sistemului

### 3.1 Diagram High-Level

```
┌─────────────────────────────────────────────────────────────────────┐
│                         UTILIZATORI                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │   Admin      │  │    Staff     │  │   Client     │             │
│  │  (Browser)   │  │  (Browser)   │  │  (Browser)   │             │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘             │
└─────────┼──────────────────┼──────────────────┼───────────────────┘
          │                  │                  │
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    FRONTEND LAYER (Angular)                         │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │  Angular 17+ SPA                                              │ │
│  │  - Hosted: Cloud Storage + Cloud CDN                          │ │
│  │  - URL: salon-maria.app.ro (subdomain per tenant)             │ │
│  │  - Auth: JWT stored in localStorage/sessionStorage            │ │
│  │  - Routing: Angular Router + lazy loading modules             │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────┬───────────────────────────────────────┘
                              │ HTTPS (REST API)
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    API GATEWAY (Optional)                           │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │  Cloud Load Balancer + Cloud Armor (DDoS protection)          │ │
│  │  - SSL/TLS termination                                         │ │
│  │  - Rate limiting per tenant                                    │ │
│  │  - IP whitelist/blacklist                                      │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                 BACKEND LAYER (Spring Boot)                         │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │  Spring Boot 3.2+ (Java 17)                                   │ │
│  │  Hosted: Cloud Run (auto-scaling 0-1000 instances)            │ │
│  │                                                                │ │
│  │  ┌─────────────────────────────────────────────────────────┐  │ │
│  │  │  Tenant Interceptor (extracts tenantId from JWT)        │  │ │
│  │  └────────────────────┬────────────────────────────────────┘  │ │
│  │                       │                                        │ │
│  │  ┌────────────────────▼────────────────────────────────────┐  │ │
│  │  │  Tenant Context (ThreadLocal storage)                   │  │ │
│  │  └────────────────────┬────────────────────────────────────┘  │ │
│  │                       │                                        │ │
│  │  ┌────────────────────▼────────────────────────────────────┐  │ │
│  │  │  AbstractRoutingDataSource (DB routing per tenant)      │  │ │
│  │  └────────────────────┬────────────────────────────────────┘  │ │
│  │                       │                                        │ │
│  │  ┌────────────────────▼────────────────────────────────────┐  │ │
│  │  │  Business Logic (Services, Controllers, Repositories)   │  │ │
│  │  │  - TenantService, LocationService, StaffService         │  │ │
│  │  │  - ClientService, AppointmentService, SMSService        │  │ │
│  │  └──────────────────────────────────────────────────────────┘  │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  DATABASE LAYER (PostgreSQL)                        │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │  Google Cloud SQL (PostgreSQL 15)                             │ │
│  │                                                                │ │
│  │  ┌────────────────────────────────────────────────────────┐   │ │
│  │  │  MASTER DB: tenant_registry                            │   │ │
│  │  │  Tables:                                               │   │ │
│  │  │  - tenants (id, slug, db_name, created_at, active)    │   │ │
│  │  │  - tenant_config (feature_flags, limits, billing)     │   │ │
│  │  └────────────────────────────────────────────────────────┘   │ │
│  │                                                                │ │
│  │  ┌────────────────────────────────────────────────────────┐   │ │
│  │  │  TENANT DB: tenant_123_salon_maria                     │   │ │
│  │  │  Tables:                                               │   │ │
│  │  │  - users, locations, clients, appointments             │   │ │
│  │  │  - services, client_history, notifications             │   │ │
│  │  └────────────────────────────────────────────────────────┘   │ │
│  │                                                                │ │
│  │  ┌────────────────────────────────────────────────────────┐   │ │
│  │  │  TENANT DB: tenant_456_cabinet_ionescu                 │   │ │
│  │  │  (same schema, different data)                         │   │ │
│  │  └────────────────────────────────────────────────────────┘   │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   EXTERNAL SERVICES                                 │
│  ┌────────────────┐  ┌────────────────┐  ┌─────────────────┐      │
│  │  SMS Provider  │  │  Email Service │  │  Cloud Storage  │      │
│  │  (Twilio/      │  │  (SendGrid/    │  │  (backups,      │      │
│  │   SMSLink)     │  │   Cloud Email) │  │   uploads)      │      │
│  └────────────────┘  └────────────────┘  └─────────────────┘      │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 Flux Complet Request → Response

```
1. User accesează: salon-maria.app.ro
   ↓
2. Cloud CDN servește Angular app (static files)
   ↓
3. User face login: POST /api/auth/login
   Body: { email, password, tenantSlug: "salon-maria" }
   ↓
4. Backend verifică în tenant_registry DB dacă există tenant "salon-maria"
   ↓
5. Switch la DB: tenant_123_salon_maria
   ↓
6. Verifică credentials în users table
   ↓
7. Generează JWT cu payload: { userId, tenantId, roles, exp }
   ↓
8. Response: { token: "eyJ...", user: {...} }
   ↓
9. Frontend salvează JWT în localStorage
   ↓
10. Request ulterior: GET /api/appointments
    Headers: { Authorization: "Bearer eyJ..." }
   ↓
11. TenantInterceptor extrage tenantId din JWT
   ↓
12. TenantContext.setCurrentTenant(tenantId) → ThreadLocal
   ↓
13. AbstractRoutingDataSource switch la DB corect
   ↓
14. appointmentRepository.findAll() → query pe DB tenant
   ↓
15. Response: [ { id: 1, client: {...}, date: "..." }, ... ]
```

---

## 4. Frontend: Angular Web Application

### 4.1 De Ce Angular Web (NU Electron Desktop)?

| Criterii | Angular Web (Cloud) | Electron Desktop |
|----------|---------------------|------------------|
| **Accesibilitate** | ✅ Orice device cu browser | ❌ Instalare pe fiecare PC |
| **Updates** | ✅ Deploy = instant live | ❌ User trebuie să download update |
| **Mobile support** | ✅ Responsive design | ❌ Zero support Android/iOS |
| **Mentenanță** | ✅ O singură codebase | ❌ Build separat per OS |
| **Costuri** | ✅ ~€10/lună (Cloud Storage) | ❌ Support, instalare, debugging |
| **Offline mode** | ⚠️ PWA poate cache parțial | ✅ Full offline (dar complicat sync) |

**DECIZIE: Angular Web App** cu Progressive Web App (PWA) pentru suport offline parțial.

### 4.2 Structură Frontend

```
src/
├── app/
│   ├── core/                      # Singleton services, guards
│   │   ├── auth/
│   │   │   ├── auth.service.ts    # Login, logout, JWT management
│   │   │   ├── auth.guard.ts      # Route protection
│   │   │   └── tenant.service.ts  # Tenant context management
│   │   ├── interceptors/
│   │   │   ├── jwt.interceptor.ts # Auto-add JWT to headers
│   │   │   └── error.interceptor.ts
│   │   └── services/
│   │       └── api.service.ts     # Base HTTP service
│   │
│   ├── shared/                    # Reusable components, pipes, directives
│   │   ├── components/
│   │   │   ├── header/
│   │   │   ├── sidebar/
│   │   │   └── loading-spinner/
│   │   ├── pipes/
│   │   └── models/                # TypeScript interfaces
│   │       ├── user.model.ts
│   │       ├── client.model.ts
│   │       └── appointment.model.ts
│   │
│   ├── features/                  # Feature modules (lazy loaded)
│   │   ├── auth/
│   │   │   ├── login/
│   │   │   ├── signup/
│   │   │   └── auth-routing.module.ts
│   │   │
│   │   ├── dashboard/
│   │   │   ├── dashboard.component.ts
│   │   │   └── dashboard-routing.module.ts
│   │   │
│   │   ├── clients/
│   │   │   ├── client-list/
│   │   │   ├── client-detail/
│   │   │   ├── client-form/
│   │   │   └── clients-routing.module.ts
│   │   │
│   │   ├── appointments/
│   │   │   ├── appointment-calendar/
│   │   │   ├── appointment-form/
│   │   │   └── appointments-routing.module.ts
│   │   │
│   │   ├── staff/
│   │   │   ├── staff-list/
│   │   │   ├── staff-invite/
│   │   │   └── staff-routing.module.ts
│   │   │
│   │   ├── locations/
│   │   │   ├── location-list/
│   │   │   ├── location-form/
│   │   │   └── locations-routing.module.ts
│   │   │
│   │   └── client-portal/        # Client-facing views
│   │       ├── my-appointments/
│   │       ├── my-history/
│   │       └── client-portal-routing.module.ts
│   │
│   └── app-routing.module.ts
│
├── environments/
│   ├── environment.ts             # Local dev
│   ├── environment.staging.ts
│   └── environment.prod.ts        # Production (Cloud)
│
└── assets/
    ├── images/
    ├── styles/
    └── i18n/                      # Internationalization (RO/EN)
```

### 4.3 Tenant Resolution Frontend

**Opțiunea A: Subdomain per Tenant** (Recomandat)
- URL: `salon-maria.app.ro`, `cabinet-ionescu.app.ro`
- Avantaje: Professional look, tenant auto-detectat
- Implementare:
  ```typescript
  // tenant.service.ts
  getTenantFromSubdomain(): string {
    const hostname = window.location.hostname;
    // Extract: salon-maria from salon-maria.app.ro
    const parts = hostname.split('.');
    return parts[0]; // "salon-maria"
  }
  ```

**Opțiunea B: Path-based Routing**
- URL: `app.ro/salon-maria`, `app.ro/cabinet-ionescu`
- Avantaje: Mai simplu DNS, un singur SSL cert
- Dezavantaje: Mai puțin professional

### 4.4 Deployment Frontend (Cloud Storage + CDN)

```bash
# Build production
ng build --configuration production

# Upload la Cloud Storage
gsutil -m cp -r dist/app/* gs://your-app-frontend/

# Setează Cloud CDN
gcloud compute backend-buckets create frontend-bucket \
  --gcs-bucket-name=your-app-frontend

# Load Balancer + SSL
gcloud compute url-maps create frontend-lb \
  --default-backend-bucket=frontend-bucket
```

**Rezultat**: Frontend servit global prin Cloud CDN, latență <50ms.

---

## 5. Backend: Spring Boot Multi-Tenant

### 5.1 Structură Backend

```
src/main/java/com/yourapp/
├── config/
│   ├── MultiTenantConfig.java           # DataSource routing setup
│   ├── SecurityConfig.java              # JWT, CORS, auth
│   ├── JpaConfig.java
│   └── AsyncConfig.java                 # Background jobs
│
├── tenant/
│   ├── TenantContext.java               # ThreadLocal tenant storage
│   ├── TenantInterceptor.java           # Extract tenant from JWT
│   ├── TenantRoutingDataSource.java     # AbstractRoutingDataSource impl
│   ├── TenantService.java
│   └── TenantRepository.java
│
├── auth/
│   ├── AuthController.java              # /api/auth/login, /signup
│   ├── JwtTokenProvider.java
│   └── UserDetailsServiceImpl.java
│
├── user/
│   ├── User.java                        # Entity
│   ├── UserService.java
│   ├── UserRepository.java
│   └── UserController.java
│
├── location/
│   ├── Location.java
│   ├── LocationService.java
│   └── LocationController.java
│
├── client/
│   ├── Client.java
│   ├── ClientDTO.java
│   ├── ClientService.java
│   ├── ClientRepository.java
│   └── ClientController.java
│
├── appointment/
│   ├── Appointment.java
│   ├── AppointmentDTO.java
│   ├── AppointmentService.java
│   ├── AppointmentRepository.java
│   └── AppointmentController.java
│
├── notification/
│   ├── SMSService.java                  # Twilio/SMSLink integration
│   ├── EmailService.java
│   └── NotificationScheduler.java       # @Scheduled SMS reminders
│
└── exception/
    ├── GlobalExceptionHandler.java
    ├── TenantNotFoundException.java
    ├── AppointmentNotFoundException.java
    └── UnauthorizedException.java
```

### 5.2 Tenant Context (ThreadLocal)

```java
// TenantContext.java
public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
```

### 5.3 Tenant Interceptor (Extract from JWT)

```java
// TenantInterceptor.java
@Component
public class TenantInterceptor implements HandlerInterceptor {
    
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        String token = extractToken(request);
        if (token != null) {
            String tenantId = jwtTokenProvider.getTenantIdFromToken(token);
            TenantContext.setCurrentTenant(tenantId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, 
                               HttpServletResponse response, 
                               Object handler, 
                               Exception ex) {
        TenantContext.clear(); // Cleanup ThreadLocal
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

### 5.4 Dynamic DataSource Routing

```java
// TenantRoutingDataSource.java
public class TenantRoutingDataSource extends AbstractRoutingDataSource {
    
    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getCurrentTenant();
    }
}

// MultiTenantConfig.java
@Configuration
public class MultiTenantConfig {

    @Autowired
    private TenantService tenantService;

    @Bean
    public DataSource dataSource() {
        Map<Object, Object> resolvedDataSources = new HashMap<>();
        
        // Master DB (tenant registry)
        DataSource masterDataSource = createDataSource(
            "jdbc:postgresql://localhost:5432/tenant_registry",
            "user", "password"
        );
        
        // Load all tenant DBs at startup
        List<Tenant> tenants = tenantService.findAllActiveTenants();
        for (Tenant tenant : tenants) {
            DataSource tenantDataSource = createDataSource(
                tenant.getJdbcUrl(),
                tenant.getDbUser(),
                tenant.getDbPassword()
            );
            resolvedDataSources.put(tenant.getId(), tenantDataSource);
        }

        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();
        routingDataSource.setDefaultTargetDataSource(masterDataSource);
        routingDataSource.setTargetDataSources(resolvedDataSources);
        routingDataSource.afterPropertiesSet();
        
        return routingDataSource;
    }

    private DataSource createDataSource(String url, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        return new HikariDataSource(config);
    }
}
```

### 5.5 Authentication Flow

```java
// AuthController.java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    
    @Autowired
    private TenantService tenantService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // 1. Validate tenant exists
        Tenant tenant = tenantService.findBySlug(request.getTenantSlug())
            .orElseThrow(() -> new TenantNotFoundException("Invalid tenant"));
        
        // 2. Set tenant context
        TenantContext.setCurrentTenant(tenant.getId());
        
        // 3. Authenticate user (queries tenant DB)
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
            )
        );
        
        // 4. Generate JWT with tenantId in payload
        String token = jwtTokenProvider.generateToken(auth, tenant.getId());
        
        return ResponseEntity.ok(new AuthResponse(token));
    }
}

// JwtTokenProvider.java
public String generateToken(Authentication auth, String tenantId) {
    UserPrincipal user = (UserPrincipal) auth.getPrincipal();
    
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + JWT_EXPIRATION_MS);

    return Jwts.builder()
        .setSubject(user.getId().toString())
        .claim("tenantId", tenantId)
        .claim("roles", user.getAuthorities())
        .setIssuedAt(now)
        .setExpiration(expiryDate)
        .signWith(SignatureAlgorithm.HS512, JWT_SECRET)
        .compact();
}

public String getTenantIdFromToken(String token) {
    Claims claims = Jwts.parser()
        .setSigningKey(JWT_SECRET)
        .parseClaimsJws(token)
        .getBody();
    
    return claims.get("tenantId", String.class);
}
```

---

## 6. Database: PostgreSQL Database-per-Tenant

### 6.1 Strategii Multi-Tenancy - Comparație

| Strategie | Izolare Date | Performanță | Scalabilitate | Backup/Restore | Costuri |
|-----------|--------------|-------------|---------------|----------------|---------|
| **Database-per-tenant** | ✅✅✅ Maximă | ✅✅ Bună | ✅✅✅ Excellent | ✅✅✅ Per tenant | €€€ |
| **Schema-per-tenant** | ✅✅ Bună | ✅✅✅ Excellent | ✅✅ Bună | ✅✅ Per schema | €€ |
| **Row-level (tenantId column)** | ⚠️ Depinde de cod | ✅✅✅ Excellent | ✅ Limitată | ⚠️ Complicat | € |

**DECIZIE: Database-per-Tenant**
- Izolare maximă (regulamente GDPR, medical data)
- Backup/restore independent per tenant
- Migration schema fără downtime pentru alți tenanți
- Dacă un tenant corupe data → alții safe

### 6.2 Schema Master DB (tenant_registry)

```sql
-- Database: tenant_registry

CREATE TABLE tenants (
    id VARCHAR(50) PRIMARY KEY,           -- UUID
    slug VARCHAR(100) UNIQUE NOT NULL,    -- "salon-maria"
    name VARCHAR(255) NOT NULL,           -- "Salon Maria SRL"
    db_name VARCHAR(100) NOT NULL,        -- "tenant_abc123_salon_maria"
    db_host VARCHAR(255),                 -- Cloud SQL instance
    db_port INTEGER DEFAULT 5432,
    db_user VARCHAR(100),
    db_password_encrypted TEXT,
    status VARCHAR(20) DEFAULT 'active',  -- active, suspended, deleted
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE tenant_config (
    tenant_id VARCHAR(50) PRIMARY KEY REFERENCES tenants(id),
    max_locations INTEGER DEFAULT 1,
    max_staff INTEGER DEFAULT 10,
    max_clients INTEGER DEFAULT 1000,
    max_appointments_per_month INTEGER DEFAULT 500,
    features JSONB,                       -- {"sms_enabled": true, "email_enabled": false}
    billing_plan VARCHAR(50),             -- "basic", "pro", "enterprise"
    subscription_ends_at TIMESTAMP
);

CREATE TABLE tenant_usage (
    id SERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) REFERENCES tenants(id),
    month VARCHAR(7),                     -- "2026-02"
    appointments_count INTEGER DEFAULT 0,
    sms_sent INTEGER DEFAULT 0,
    storage_mb INTEGER DEFAULT 0
);
```

### 6.3 Schema Tenant DB (tenant_abc123_salon_maria)

```sql
-- Fiecare tenant DB are aceeași schemă

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(20),
    role VARCHAR(20),                     -- ADMIN, STAFF, CLIENT
    location_id BIGINT,                   -- NULL = access all locations
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE locations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address TEXT,
    phone VARCHAR(20),
    email VARCHAR(255),
    working_hours JSONB,                  -- {"mon": "09:00-18:00", ...}
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE clients (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) UNIQUE NOT NULL,
    email VARCHAR(255),
    date_of_birth DATE,
    gender VARCHAR(10),
    notes TEXT,                           -- Observații, preferințe, alergii
    location_id BIGINT REFERENCES locations(id),
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE services (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    duration_minutes INTEGER,
    price DECIMAL(10, 2),
    location_id BIGINT REFERENCES locations(id),
    active BOOLEAN DEFAULT true
);

CREATE TABLE appointments (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT REFERENCES clients(id) NOT NULL,
    location_id BIGINT REFERENCES locations(id) NOT NULL,
    staff_id BIGINT REFERENCES users(id),
    service_id BIGINT REFERENCES services(id),
    appointment_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(20) DEFAULT 'scheduled', -- scheduled, confirmed, completed, cancelled, no_show
    notes TEXT,
    sms_reminder_sent BOOLEAN DEFAULT false,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE client_history (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT REFERENCES clients(id) NOT NULL,
    appointment_id BIGINT REFERENCES appointments(id),
    service_performed VARCHAR(255),
    notes TEXT,                           -- Fișă tratament
    photos JSONB,                         -- URLs to Cloud Storage
    performed_by BIGINT REFERENCES users(id),
    performed_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    type VARCHAR(20),                     -- SMS, EMAIL, PUSH
    recipient VARCHAR(255),               -- phone or email
    subject VARCHAR(255),
    message TEXT,
    status VARCHAR(20),                   -- pending, sent, failed
    sent_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes pentru performanță
CREATE INDEX idx_appointments_date ON appointments(appointment_date);
CREATE INDEX idx_appointments_client ON appointments(client_id);
CREATE INDEX idx_appointments_staff ON appointments(staff_id);
CREATE INDEX idx_clients_phone ON clients(phone);
CREATE INDEX idx_users_email ON users(email);
```

### 6.4 Flyway Migrations (Schema Versioning)

```
src/main/resources/db/migration/
├── master/                           # Master DB migrations
│   ├── V1__create_tenants_table.sql
│   ├── V2__create_tenant_config.sql
│   └── V3__create_tenant_usage.sql
│
└── tenant/                           # Tenant DB migrations (template)
    ├── V1__create_users_table.sql
    ├── V2__create_locations.sql
    ├── V3__create_clients.sql
    ├── V4__create_services.sql
    ├── V5__create_appointments.sql
    ├── V6__create_client_history.sql
    └── V7__create_notifications.sql
```

**Când se creează tenant nou:**
1. Backend creează DB nou: `CREATE DATABASE tenant_abc123_salon_maria;`
2. Rulează toate migrations din `tenant/` pe DB-ul nou
3. Inserează record în `tenants` table (master DB)
4. Actualizează DataSource pool cu noul tenant

---

## 7. Autentificare & Securitate

### 7.1 Flow Complet Autentificare

```
1. User accesează: salon-maria.app.ro/login

2. Frontend detectează tenant din subdomain: "salon-maria"

3. User introduce: email + password

4. POST /api/auth/login
   {
     "tenantSlug": "salon-maria",
     "email": "maria@salon.ro",
     "password": "SecurePass123!"
   }

5. Backend:
   a) Query tenant_registry DB: SELECT * FROM tenants WHERE slug='salon-maria'
   b) Switch la tenant DB: tenant_abc123_salon_maria
   c) Query users table: SELECT * FROM users WHERE email='maria@salon.ro'
   d) Verify password hash (BCrypt)
   e) Generate JWT:
      {
        "sub": "user-id-123",
        "tenantId": "abc123",
        "roles": ["ADMIN"],
        "locationId": null,
        "exp": 1234567890
      }

6. Response:
   {
     "token": "eyJhbGciOiJIUzUxMiJ9...",
     "user": {
       "id": 123,
       "email": "maria@salon.ro",
       "firstName": "Maria",
       "role": "ADMIN"
     }
   }

7. Frontend salvează token în localStorage

8. Toate request-urile următoare:
   Headers: { "Authorization": "Bearer eyJhbGc..." }
```

### 7.2 Roluri & Permisiuni

| Rol | Permisiuni |
|-----|-----------|
| **ADMIN** | - Full access la toate funcțiile<br>- Gestionează staff, locații<br>- Configurări tenant<br>- Acces financiar/rapoarte |
| **STAFF** | - Adaugă/editează clienți<br>- Creează/modifică programări<br>- Acces fișe clienți<br>- NU poate șterge date<br>- NU acces settings/billing |
| **CLIENT** | - Vizualizare programări proprii<br>- Vizualizare istoric propriu<br>- Editare date contact proprii<br>- NU poate crea programări<br>- NU vede alți clienți |

### 7.3 Securitate Cloud SQL

```yaml
# application-gcp.properties
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=30000

# Cloud SQL Socket Factory (private IP)
spring.cloud.gcp.sql.instance-connection-name=${GCP_PROJECT}:${GCP_REGION}:${INSTANCE_NAME}
spring.cloud.gcp.sql.database-name=tenant_registry
spring.datasource.socketFactory=com.google.cloud.sql.postgres.SocketFactory

# SSL enforcement
spring.datasource.url=jdbc:postgresql:///${DB_NAME}?socketFactory=...&sslmode=require
```

**Best Practices:**
- ✅ Cloud SQL cu **Private IP** (nu public)
- ✅ Connection prin **Cloud SQL Proxy** / Socket Factory
- ✅ Passwords stored în **Google Secret Manager**
- ✅ Database backups automate zilnic + point-in-time recovery
- ✅ Audit logging enable (cine a accesat ce date)

---

## 8. Deployment pe Google Cloud Platform

### 8.1 Arhitectură GCP

```
┌─────────────────────────────────────────────────────────────┐
│                    Cloud Load Balancer                      │
│              (SSL/TLS, DDoS protection)                     │
└────────────┬──────────────────────────┬─────────────────────┘
             │                          │
             │ (static)                 │ (API)
             ▼                          ▼
┌──────────────────────┐   ┌──────────────────────────────────┐
│  Cloud Storage +CDN  │   │       Cloud Run (Backend)        │
│  (Angular frontend)  │   │  - Auto-scale 0-1000 instances   │
│                      │   │  - Pay per request               │
│  salon-maria.app.ro  │   │  - CPU: 2 vCPU, 4GB RAM          │
└──────────────────────┘   └─────────────┬────────────────────┘
                                         │
                                         │ (Private VPC)
                                         ▼
                           ┌──────────────────────────────────┐
                           │   Cloud SQL (PostgreSQL)         │
                           │   - Instance: db-f1-micro →      │
                           │     db-n1-standard-2             │
                           │   - Storage: 10GB → 500GB SSD    │
                           │   - Private IP only              │
                           │   - Automated backups            │
                           └──────────────────────────────────┘
                                         │
                                         ▼
                           ┌──────────────────────────────────┐
                           │   Cloud Storage (Backups)        │
                           │   - DB dumps                     │
                           │   - Client photos/documents      │
                           └──────────────────────────────────┘

    ┌───────────────────────────────────────────────────────┐
    │           External Services                           │
    │  - Secret Manager (passwords, API keys)               │
    │  - Cloud Logging (centralized logs)                   │
    │  - Cloud Monitoring (metrics, alerts)                 │
    │  - Cloud Scheduler (cron jobs pentru SMS reminders)   │
    └───────────────────────────────────────────────────────┘
```

### 8.2 Cloud Run Deployment

```yaml
# cloudbuild.yaml (GitHub Actions trigger)
steps:
  # Build JAR
  - name: 'maven:3.9-eclipse-temurin-17'
    entrypoint: 'mvn'
    args: ['clean', 'package', '-DskipTests']
  
  # Build Docker image
  - name: 'gcr.io/cloud-builders/docker'
    args: [
      'build',
      '-t', 'gcr.io/$PROJECT_ID/app-backend:$SHORT_SHA',
      '-t', 'gcr.io/$PROJECT_ID/app-backend:latest',
      '.'
    ]
  
  # Push to Container Registry
  - name: 'gcr.io/cloud-builders/docker'
    args: ['push', 'gcr.io/$PROJECT_ID/app-backend:$SHORT_SHA']
  
  # Deploy to Cloud Run
  - name: 'gcr.io/cloud-builders/gcloud'
    args:
      - 'run'
      - 'deploy'
      - 'app-backend'
      - '--image=gcr.io/$PROJECT_ID/app-backend:$SHORT_SHA'
      - '--region=europe-west1'
      - '--platform=managed'
      - '--allow-unauthenticated'
      - '--memory=2Gi'
      - '--cpu=2'
      - '--max-instances=100'
      - '--min-instances=1'
      - '--set-env-vars=SPRING_PROFILES_ACTIVE=gcp'
      - '--vpc-connector=projects/$PROJECT_ID/locations/europe-west1/connectors/vpc-connector'

timeout: '1200s'
```

### 8.3 Infrastructure as Code (Terraform - Optional)

```hcl
# main.tf
resource "google_sql_database_instance" "main" {
  name             = "app-postgres-instance"
  database_version = "POSTGRES_15"
  region           = "europe-west1"

  settings {
    tier = "db-n1-standard-2"
    
    backup_configuration {
      enabled                        = true
      start_time                     = "03:00"
      point_in_time_recovery_enabled = true
    }

    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.main.id
    }
  }
}

resource "google_cloud_run_service" "backend" {
  name     = "app-backend"
  location = "europe-west1"

  template {
    spec {
      containers {
        image = "gcr.io/${var.project_id}/app-backend:latest"
        
        resources {
          limits = {
            cpu    = "2000m"
            memory = "2Gi"
          }
        }

        env {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "gcp"
        }
      }
    }

    metadata {
      annotations = {
        "autoscaling.knative.dev/maxScale" = "100"
        "autoscaling.knative.dev/minScale" = "1"
      }
    }
  }
}
```

---

## 9. Funcționalități Cheie

### 9.1 SMS Reminders (Automated)

```java
// NotificationScheduler.java
@Component
public class NotificationScheduler {

    @Autowired
    private AppointmentRepository appointmentRepository;
    
    @Autowired
    private SMSService smsService;

    // Run every hour
    @Scheduled(cron = "0 0 * * * *")
    public void sendAppointmentReminders() {
        // Get all active tenants
        List<String> tenants = tenantService.getAllActiveTenantIds();
        
        for (String tenantId : tenants) {
            TenantContext.setCurrentTenant(tenantId);
            
            try {
                // Find appointments 24 hours from now that haven't been reminded
                LocalDateTime tomorrow = LocalDateTime.now().plusHours(24);
                List<Appointment> appointments = appointmentRepository
                    .findByAppointmentDateTimeBetweenAndSmsReminderSentFalse(
                        tomorrow.minusHours(1),
                        tomorrow.plusHours(1)
                    );
                
                for (Appointment apt : appointments) {
                    String message = String.format(
                        "Reminder: Programare la %s pe %s la ora %s. Salon Maria - 0723456789",
                        apt.getLocation().getName(),
                        apt.getAppointmentDate(),
                        apt.getStartTime()
                    );
                    
                    smsService.sendSMS(apt.getClient().getPhone(), message);
                    
                    apt.setSmsReminderSent(true);
                    appointmentRepository.save(apt);
                }
            } finally {
                TenantContext.clear();
            }
        }
    }
}

// SMSService.java (Twilio implementation)
@Service
public class SMSService {
    
    @Value("${twilio.account-sid}")
    private String accountSid;
    
    @Value("${twilio.auth-token}")
    private String authToken;
    
    @Value("${twilio.phone-number}")
    private String fromPhone;

    public void sendSMS(String toPhone, String message) {
        Twilio.init(accountSid, authToken);
        
        Message.creator(
            new PhoneNumber(toPhone),
            new PhoneNumber(fromPhone),
            message
        ).create();
        
        log.info("SMS sent to {} for tenant {}", toPhone, TenantContext.getCurrentTenant());
    }
}
```

### 9.2 Calendar View (Appointment Management)

**Frontend (Angular):**
- Library: **FullCalendar** (https://fullcalendar.io/docs/angular)
- Features:
  - Day/Week/Month views
  - Drag & drop appointments
  - Color coding per staff/service
  - Click to create new appointment

**Backend API:**
```java
@GetMapping("/api/appointments/calendar")
public List<AppointmentDTO> getCalendarAppointments(
    @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate start,
    @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate end,
    @RequestParam(required = false) Long locationId,
    @RequestParam(required = false) Long staffId
) {
    return appointmentService.findByDateRange(start, end, locationId, staffId);
}
```

### 9.3 Client History (Fișe Tratamente)

```java
@PostMapping("/api/clients/{clientId}/history")
public ClientHistoryDTO addHistoryEntry(
    @PathVariable Long clientId,
    @RequestBody ClientHistoryRequest request,
    @RequestParam(required = false) MultipartFile[] photos
) {
    // Upload photos to Cloud Storage
    List<String> photoUrls = new ArrayList<>();
    if (photos != null) {
        for (MultipartFile photo : photos) {
            String url = cloudStorageService.upload(
                photo,
                String.format("tenants/%s/clients/%d/photos", 
                    TenantContext.getCurrentTenant(), 
                    clientId)
            );
            photoUrls.add(url);
        }
    }
    
    return clientHistoryService.create(clientId, request, photoUrls);
}
```

---

## 10. Monitoring & Observability

### 10.1 Logging Strategy

```java
// Adaugă tenantId în toate log-urile folosind SLF4J MDC
@Component
public class LoggingInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null) {
            MDC.put("tenantId", tenantId);
            MDC.put("tenantSlug", tenantService.getSlugById(tenantId));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, 
                               HttpServletResponse response, 
                               Object handler, 
                               Exception ex) {
        MDC.clear();
    }
}

// Log format în Cloud Logging:
// {
//   "severity": "INFO",
//   "message": "Appointment created successfully",
//   "tenantId": "abc123",
//   "tenantSlug": "salon-maria",
//   "userId": 456,
//   "timestamp": "2026-02-13T10:30:00Z"
// }
```

### 10.2 Metrics (Cloud Monitoring)

```yaml
Metrics to Track:
- Request rate per tenant (req/sec)
- Response latency p50, p95, p99 per tenant
- Error rate per tenant (%)
- Active appointments per tenant
- Database connection pool usage per tenant
- SMS sent per tenant per day
- Storage used per tenant (MB)

Dashboards:
1. Overview Dashboard
   - Total active tenants
   - Total requests/sec
   - Average latency
   - Error rate

2. Per-Tenant Dashboard (filterable)
   - Request volume
   - Error logs
   - Database query performance
   - Feature usage (appointments created, SMS sent)

Alerts:
- Error rate > 5% for any tenant → Email admin
- Latency p95 > 2s → Slack notification
- Database CPU > 80% → Auto-scale instance
- Tenant approaching usage limits → Email tenant admin
```

### 10.3 Health Checks

```java
@RestController
@RequestMapping("/actuator")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<HealthStatus> health() {
        // Check master DB
        boolean masterDbHealthy = checkMasterDbConnection();
        
        // Check sample tenant DBs (random 5 tenants)
        Map<String, Boolean> tenantDbHealth = checkTenantDatabases();
        
        // Check external services
        boolean smsServiceHealthy = checkSMSService();
        
        HealthStatus status = new HealthStatus(
            masterDbHealthy && tenantDbHealth.values().stream().allMatch(v -> v),
            masterDbHealthy,
            tenantDbHealth,
            smsServiceHealthy
        );
        
        return ResponseEntity.ok(status);
    }
}
```

---

## 11. Costuri Estimate

### 11.1 Breakdown Costuri (10-50 Tenanți)

| Serviciu | Configurație | Cost/Lună (EUR) |
|----------|-------------|-----------------|
| **Cloud Run (Backend)** | 1-5 instanțe active, 2 vCPU, 4GB RAM | €30-80 |
| **Cloud SQL** | db-n1-standard-1, 50GB SSD, backups | €60-120 |
| **Cloud Storage** | 10GB frontend, 50GB backups/photos | €2-5 |
| **Cloud CDN** | 100GB egress, 10M requests | €10-20 |
| **Cloud Load Balancer** | SSL termination | €18 |
| **Cloud Logging** | 50GB logs/lună | €5-10 |
| **SMS Provider (Twilio)** | 2000 SMS/lună @ €0.04/SMS | €80 |
| **Secret Manager** | 100 secrets | €1 |
| **TOTAL** | - | **€206-334/lună** |

**Cost per Tenant:** €4-7/lună (la 50 tenanți)

### 11.2 Pricing Model pentru Clienți

| Plan | Preț/Lună | Features |
|------|----------|----------|
| **Starter** | €29 | 1 locație, 3 staff, 200 programări/lună, 100 SMS |
| **Professional** | €79 | 3 locații, 10 staff, 1000 programări/lună, 500 SMS |
| **Enterprise** | €199 | Unlimited locații, 50 staff, unlimited programări, 2000 SMS |

**Break-even:** ~10 tenanți pe plan Starter sau 5 tenanți pe plan Professional

---

## 12. Plan de Implementare

### Faza 1: MVP (8-10 săptămâni)

**Săptămâna 1-2: Setup Infrastructure**
- [ ] Setup GCP project, enable APIs
- [ ] Create Cloud SQL instance (master + 1 tenant DB)
- [ ] Setup VPC, Cloud Run service
- [ ] Configure GitHub Actions CI/CD
- [ ] Setup local development environment

**Săptămâna 3-4: Backend Core**
- [ ] Implement tenant routing (AbstractRoutingDataSource)
- [ ] Authentication & JWT (login, signup)
- [ ] User management (CRUD)
- [ ] Location management
- [ ] Client management (CRUD)

**Săptămâna 5-6: Appointments & Scheduling**
- [ ] Appointment CRUD APIs
- [ ] Calendar view backend logic
- [ ] Conflict detection (double booking prevention)
- [ ] Basic SMS integration (Twilio setup)

**Săptămâna 7-8: Frontend Angular**
- [ ] Setup Angular project, routing
- [ ] Login/Signup pages
- [ ] Dashboard (staff view)
- [ ] Client list & detail pages
- [ ] Appointment calendar (FullCalendar integration)
- [ ] Appointment creation form

**Săptămâna 9-10: Testing & Deploy MVP**
- [ ] Unit tests (backend services)
- [ ] Integration tests (API endpoints)
- [ ] Frontend E2E tests (Cypress)
- [ ] Deploy to GCP (staging environment)
- [ ] Load testing (JMeter - simulate 100 concurrent users)
- [ ] Beta testing cu 2-3 tenanți reali

### Faza 2: Production Features (6-8 săptămâni)

**Săptămâna 11-12: Client Portal**
- [ ] Client authentication (SMS code login)
- [ ] Client dashboard (view appointments, history)
- [ ] Profile editing

**Săptămâna 13-14: Advanced Features**
- [ ] Client history/fișe tratamente cu foto upload
- [ ] Automated SMS reminders (scheduler)
- [ ] Email notifications
- [ ] Search & filters (clients, appointments)

**Săptămâna 15-16: Admin & Reporting**
- [ ] Tenant onboarding flow (self-service signup)
- [ ] Admin panel pentru tenant settings
- [ ] Rapoarte: programări/lună, revenue, top clients
- [ ] Export data (CSV, PDF)

**Săptămâna 17-18: Polish & Production Deploy**
- [ ] Performance optimization (DB indexes, caching)
- [ ] Security audit (penetration testing)
- [ ] Documentation (API docs, user manuals)
- [ ] Production deployment
- [ ] Marketing website (landing page)

### Faza 3: Growth Features (Ongoing)

- [ ] Mobile app (React Native or Flutter)
- [ ] Online payment integration (Stripe)
- [ ] Loyalty program (puncte, discount-uri)
- [ ] Marketing automation (email campaigns)
- [ ] Advanced analytics (Google Analytics integration)
- [ ] Multi-language support (EN, RO, HU)
- [ ] Integrations (Google Calendar sync, Facebook Messenger booking)

---

## 13. Decizii Arhitecturale

### 13.1 De Ce NU Microservicii per Tenant?

| Aspect | Microservicii per Tenant | Monolith Multi-Tenant |
|--------|-------------------------|----------------------|
| **Deployment** | Deploy separat per tenant (overhead HUGE) | Un singur deploy pentru toți |
| **Mentenanță** | Update 100 tenants = 100 deploys | Update o dată, propagare automată |
| **Costuri** | 100 tenants = 100 Cloud Run services | 100 tenants = 1-10 instanțe (shared) |
| **Monitoring** | 100 dashboards separate | 1 dashboard cu filtre per tenant |
| **Bug fixes** | Fix în 100 locuri | Fix o dată |
| **Scalare** | Manual per tenant | Auto-scale bazat pe load total |

**Concluzie:** Microservicii per tenant = OVER-ENGINEERING pentru această aplicație. Justificat doar dacă:
- Tenants au cerințe COMPLET diferite (SaaS pentru industrii diferite)
- Compliance requirements cer izolare fizică completă (ex: banking)

### 13.2 De Ce Database-per-Tenant (nu Schema-per-Tenant)?

**Schema-per-tenant:**
- ✅ Mai ieftin (o singură instanță DB)
- ❌ Backup/restore complicat (trebuie să extragi doar o schemă)
- ❌ Un tenant care corupe DB afectează potențial alții
- ❌ Migration schema = risc pentru toți tenanții simultan

**Database-per-tenant:**
- ✅ Izolare COMPLETĂ (backup, restore, corruption = per tenant)
- ✅ Migration independentă (test pe un tenant înainte de rollout global)
- ✅ Ștergere tenant = drop database (compliant cu GDPR)
- ⚠️ Costuri ușor mai mari (dar Cloud SQL suportă 1000+ databases pe o instanță)

### 13.3 De Ce Angular Web (nu Electron Desktop)?

**Electron Desktop:**
- ✅ Full offline capability
- ❌ Instalare pe fiecare PC (suport IT)
- ❌ Update = user trebuie să descarce manual
- ❌ Zero suport Android/iOS
- ❌ 100MB+ app size

**Angular Web (PWA):**
- ✅ Acces instant din browser (zero instalare)
- ✅ Updates instant (deploy = live pentru toți)
- ✅ Responsive = funcționează pe mobile/tablet
- ✅ PWA = partial offline + install pe home screen
- ⚠️ Necesită internet pentru features critice (OK pentru use case)

---

## 14. Riscuri & Mitigări

### 14.1 Riscuri Tehnice

| Risc | Impact | Probabilitate | Mitigare |
|------|--------|---------------|----------|
| **Cloud SQL connection pool exhaustion** | HIGH | MEDIUM | HikariCP max connections per tenant, monitoring, auto-scaling DB instance |
| **Tenant data leak (greșit routing)** | CRITICAL | LOW | Unit tests extensive, integration tests, code review, audit logging |
| **Cloud Run cold starts (>2s)** | MEDIUM | HIGH | Set min-instances=1, optimize Spring Boot startup (native image cu GraalVM) |
| **SMS delivery failure** | MEDIUM | MEDIUM | Retry mechanism, fallback la email, monitoring delivery rates |
| **Database migration failure** | HIGH | LOW | Test migrations pe staging, backup înainte de migrate, rollback plan |

### 14.2 Riscuri de Business

| Risc | Mitigare |
|------|----------|
| **Churn rate ridicat (clienți pleacă)** | - Onboarding excelent, training<br>- Support rapid (chat, email)<br>- Feature requests implementation<br>- Preț competitiv |
| **Competiție (alte SaaS-uri)** | - Focus pe piața RO (localizare, suport RO)<br>- Integrări specifice RO (facturare RO, SMS provider RO)<br>- Pricing adaptat pieței RO |
| **Scalare prea rapidă** | - Auto-scaling infrastructure (Cloud Run, Cloud SQL)<br>- Load testing regulat<br>- Capacity planning (forecast bazat pe growth) |

---

## 15. Next Steps (Acțiuni Concrete)

### 15.1 Înainte de Coding

- [ ] **Validare cerințe cu potențiali clienți** (interviuri cu 5-10 saloane/cabinete)
  - Ce features sunt MUST-HAVE vs nice-to-have?
  - Cât ar plăti per lună?
  - Ce probleme au cu soluțiile actuale (Excel, agende hârtie)?

- [ ] **Competiție research**
  - Identifică competitori (Booksy, Fresha, Planity)
  - Ce fac ei bine/prost?
  - Care este differentiator-ul tău?

- [ ] **Mockups/Wireframes** (Figma)
  - Design UI pentru: login, dashboard, calendar, client list, appointment form
  - User feedback pe designs (înainte de coding!)

### 15.2 Setup Inițial

- [ ] **GCP Account Setup**
  ```bash
  gcloud projects create your-app-project
  gcloud config set project your-app-project
  gcloud services enable sqladmin.googleapis.com run.googleapis.com
  ```

- [ ] **Git Repository**
  ```bash
  mkdir app-backend app-frontend
  git init
  # Push la GitHub
  ```

- [ ] **Local Development Environment**
  - Java 17, Maven, Docker Desktop
  - Node.js 20+, Angular CLI
  - PostgreSQL 15 local (Docker)
  - IntelliJ IDEA / VS Code

---

## 16. Resurse & Documentație

### 16.1 Tehnologii Folosite

**Backend:**
- Spring Boot 3.2+ (https://spring.io/projects/spring-boot)
- Spring Data JPA (https://spring.io/projects/spring-data-jpa)
- Flyway Migrations (https://flywaydb.org/)
- Twilio SMS API (https://www.twilio.com/docs/sms)
- JWT (io.jsonwebtoken:jjwt)

**Frontend:**
- Angular 17+ (https://angular.io/)
- FullCalendar (https://fullcalendar.io/docs/angular)
- Angular Material (https://material.angular.io/)
- RxJS (https://rxjs.dev/)

**Infrastructure:**
- Google Cloud Run (https://cloud.google.com/run/docs)
- Cloud SQL for PostgreSQL (https://cloud.google.com/sql/docs/postgres)
- Cloud Storage (https://cloud.google.com/storage/docs)

### 16.2 Tutoriale Recomandate

1. **Multi-Tenancy in Spring Boot**
   - https://www.baeldung.com/spring-abstract-routing-data-source
   - https://www.baeldung.com/hibernate-5-multitenancy

2. **Google Cloud Run Deployment**
   - https://cloud.google.com/run/docs/quickstarts/build-and-deploy/deploy-java-service

3. **Angular + JWT Authentication**
   - https://jasonwatmore.com/post/2022/11/15/angular-14-jwt-authentication-example-tutorial

---

## 17. Concluzie & Recomandări

### 17.1 Arhitectură Recomandată (TL;DR)

✅ **Frontend:** Angular Web App (PWA) pe Cloud Storage + CDN  
✅ **Backend:** Un singur Spring Boot monolith pe Cloud Run cu tenant routing  
✅ **Database:** Cloud SQL PostgreSQL cu database-per-tenant  
✅ **Tenant Identification:** Subdomain (salon-maria.app.ro) + JWT tenantId  
✅ **SMS:** Twilio sau SMSLink.ro  
✅ **Deployment:** GitHub Actions → Cloud Run (auto-scaling)  

### 17.2 De Ce Această Arhitectură?

1. **Simplitate:** O aplicație de menținut, nu 100 de microservicii
2. **Cost-effective:** €200-300/lună pentru 50 tenanți (vs €1000+ cu microservicii)
3. **Scalabilitate:** Cloud Run auto-scale până la 1000 instanțe
4. **Izolare:** Database-per-tenant = zero risc de data leak
5. **Mentenabilitate:** Bug fix o dată = propagare instant la toți tenanții
6. **Time-to-market:** MVP în 8-10 săptămâni (vs 6+ luni pentru microservicii)

### 17.3 Ce Evităm?

❌ **Electron desktop app** (complicat update, zero mobile support)  
❌ **Microservicii per tenant** (overhead deployment, costuri HUGE)  
❌ **Row-level multi-tenancy (tenantId column)** (risc data leak, backup complicat)  
❌ **Public IP Cloud SQL** (security risk)  
❌ **Manual deployment** (GitHub Actions automate totul)  

---

## 18. Întrebări Deschise pentru Discuție

1. **Tenant Onboarding:** Self-service signup (automat) sau manual approval?
2. **Pricing:** Free trial 14 zile sau demo cu sales call?
3. **Client Booking:** Clienții pot crea programări singuri sau doar staff?
4. **Payment:** Plata online în app sau doar cash/POS la locație?
5. **Branding:** White-label (fiecare tenant își pune logo) sau branding uniform?
6. **Mobile App:** Nativ Android/iOS sau doar PWA?
7. **Offline Mode:** Critical sau can live without pentru MVP?
8. **Multi-Language:** Doar RO sau și EN/HU de la început?

---

**Document Version:** 1.0  
**Last Updated:** Februarie 13, 2026  
**Author:** GitHub Copilot  
**Status:** DRAFT pentru Review & Iteration

---

**Contact pentru Clarificări:**
- Repository: https://github.com/olzma/fasttest
- Project: spring-boot-fasttest

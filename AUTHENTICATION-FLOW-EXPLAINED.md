# 🔐 Flow Complet Autentificare - Explicație Pas cu Pas

> **Acest document explică în detaliu cum funcționează autentificarea multi-tenant, de la login până la încărcarea datelor în frontend.**

---

## 📋 Cuprins

1. [Prezentare Generală](#prezentare-generală)
2. [Exemplu Concret: Maria, Proprietara Salonului](#exemplu-concret-maria-proprietara-salonului)
3. [Flow Detaliat - 15 Pași](#flow-detaliat---15-pași)
4. [Concepte Cheie Explicate](#concepte-cheie-explicate)
5. [Securitate](#securitate)
6. [Întrebări Frecvente](#întrebări-frecvente)

---

## Prezentare Generală

În arhitectura multi-tenant, **autentificarea** trebuie să rezolve două probleme:
1. **Cine ești tu?** (user authentication)
2. **La ce tenant aparții?** (tenant identification)

**Soluția:** JWT Token care conține atât `userId` cât și `tenantId` în payload.

**Flow-ul simplu:**
```
User accesează salon-maria.app.ro
  → Frontend detectează tenant din URL
  → User face login
  → Backend verifică în DB-ul tenantului
  → Generează JWT cu tenantId
  → Toate request-urile ulterioare folosesc JWT pentru a ști pe ce DB să lucreze
```

---

## Exemplu Concret: Maria, Proprietara Salonului

**Personaj:** Maria Popescu, proprietara salonului "Salon Maria"  
**Tenant slug:** `salon-maria`  
**Tenant ID:** `abc123`  
**Database:** `tenant_abc123_salon_maria`  
**Email:** `maria@salon.ro`  
**Rol:** `ADMIN`

---

## Flow Detaliat - 15 Pași

### **PASUL 1: Maria deschide aplicația în browser**

**Acțiune:**
```
Maria tastează în browser: https://salon-maria.app.ro
```

**Ce se întâmplă:**
1. Browser-ul face request la **Cloud CDN** (Google Cloud Storage)
2. CDN-ul returnează aplicația **Angular** (HTML, CSS, JavaScript)
3. Angular se încarcă în browser (dar **NU are date încă**, doar interfața goală)

**Frontend (Angular) detectează tenantul din URL:**

```typescript
// tenant.service.ts
export class TenantService {
  
  getTenantFromSubdomain(): string {
    const hostname = window.location.hostname; // "salon-maria.app.ro"
    const parts = hostname.split('.');         // ["salon-maria", "app", "ro"]
    return parts[0];                           // "salon-maria" ← TENANT SLUG
  }
}
```

**Rezultat:** 
- Frontend știe că user-ul vrea să acceseze tenantul `"salon-maria"`
- Această informație va fi trimisă la backend la login

---

### **PASUL 2: Maria vede ecranul de login**

Angular afișează formularul:

```
┌─────────────────────────────────┐
│   Salon Maria - Login           │
├─────────────────────────────────┤
│ Email:    [________________]    │
│ Password: [________________]    │
│           [  LOGIN  ]           │
└─────────────────────────────────┘
```

**Maria introduce:**
- **Email:** `maria@salon.ro`
- **Password:** `SecurePass123!`

---

### **PASUL 3: Frontend trimite request de login la backend**

Când Maria dă click pe "LOGIN", Angular face request:

```typescript
// auth.service.ts (Frontend Angular)
export class AuthService {
  
  constructor(
    private http: HttpClient,
    private tenantService: TenantService
  ) {}
  
  login(email: string, password: string): Observable<AuthResponse> {
    const tenantSlug = this.tenantService.getTenantFromSubdomain(); // "salon-maria"
    
    const body = {
      tenantSlug: tenantSlug,      // "salon-maria"
      email: email,                // "maria@salon.ro"
      password: password           // "SecurePass123!"
    };
    
    // POST request la backend
    return this.http.post<AuthResponse>('https://api.app.ro/api/auth/login', body);
  }
}
```

**Request HTTP arată așa:**

```http
POST https://api.app.ro/api/auth/login
Content-Type: application/json

{
  "tenantSlug": "salon-maria",
  "email": "maria@salon.ro",
  "password": "SecurePass123!"
}
```

---

### **PASUL 4: Backend primește request-ul (Spring Boot)**

Request-ul ajunge la `AuthController.java`:

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private TenantService tenantService;
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // request.tenantSlug = "salon-maria"
        // request.email = "maria@salon.ro"
        // request.password = "SecurePass123!"
        
        // Continuăm în PASUL 5...
    }
}

// LoginRequest.java (DTO)
@Data
public class LoginRequest {
    private String tenantSlug;  // "salon-maria"
    private String email;       // "maria@salon.ro"
    private String password;    // "SecurePass123!"
}
```

---

### **PASUL 5: Backend caută tenantul în MASTER DATABASE**

```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    // 1. Verifică dacă tenantul există în tenant_registry
    Tenant tenant = tenantService.findBySlug(request.getTenantSlug())
        .orElseThrow(() -> new TenantNotFoundException("Tenant inexistent"));
    
    // Query executat pe MASTER DB:
    // SELECT * FROM tenants WHERE slug = 'salon-maria'
}
```

**În baza de date `tenant_registry` (MASTER DB):**

```sql
tenants table:
┌──────────┬──────────────┬──────────────────────────────┬────────┬────────────┐
│ id       │ slug         │ db_name                      │ status │ created_at │
├──────────┼──────────────┼──────────────────────────────┼────────┼────────────┤
│ abc123   │ salon-maria  │ tenant_abc123_salon_maria    │ active │ 2025-01-15 │
│ xyz789   │ cabinet-ion  │ tenant_xyz789_cabinet_ion    │ active │ 2025-02-01 │
│ def456   │ service-auto │ tenant_def456_service_auto   │ active │ 2025-02-10 │
└──────────┴──────────────┴──────────────────────────────┴────────┴────────────┘
```

**Rezultat obiect `Tenant`:**

```java
tenant.getId()     = "abc123"
tenant.getSlug()   = "salon-maria"
tenant.getDbName() = "tenant_abc123_salon_maria"
tenant.getStatus() = "active"
```

**⚠️ Important:** În acest moment, conexiunea DB este încă pe **MASTER DB** (`tenant_registry`).

---

### **PASUL 6: Backend switch-uiește la DATABASE-UL TENANTULUI**

```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    // 1. Am găsit tenantul
    Tenant tenant = tenantService.findBySlug(request.getTenantSlug())
        .orElseThrow(() -> new TenantNotFoundException("Tenant inexistent"));
    
    // 2. Setează tenant context (ThreadLocal)
    TenantContext.setCurrentTenant(tenant.getId()); // "abc123"
    
    // De acum înainte, toate query-urile merg pe DB-ul tenantului!
}
```

**Ce se întâmplă sub capotă:**

```java
// TenantContext.java
public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    
    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId); // salvează "abc123" în thread-ul curent
    }
    
    public static String getCurrentTenant() {
        return CURRENT_TENANT.get(); // va returna "abc123"
    }
    
    public static void clear() {
        CURRENT_TENANT.remove(); // curăță după procesare
    }
}
```

**Ce este ThreadLocal?**
- Variabilă care există **doar pentru thread-ul curent** (request-ul curent HTTP)
- Request de la Maria (thread 1) → `TenantContext = "abc123"`
- Request de la Ion (thread 2) → `TenantContext = "xyz789"`
- **NU se amestecă niciodată** între request-uri

**Acum, DataSource-ul switch-uiește automat:**

```java
// TenantRoutingDataSource.java
public class TenantRoutingDataSource extends AbstractRoutingDataSource {
    
    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getCurrentTenant(); // returnează "abc123"
    }
}
```

**Rezultat:**
```
ÎNAINTE: conexiune la "tenant_registry" (master DB)
ACUM:    conexiune la "tenant_abc123_salon_maria" (DB-ul salonului Maria)
```

Spring Data JPA va folosi automat această conexiune pentru toate query-urile următoare.

---

### **PASUL 7: Backend verifică credențialele în DB-ul tenantului**

```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    // 1. Am găsit tenantul
    Tenant tenant = tenantService.findBySlug(request.getTenantSlug())
        .orElseThrow(() -> new TenantNotFoundException("Tenant inexistent"));
    
    // 2. Am setat tenant context
    TenantContext.setCurrentTenant(tenant.getId());
    
    // 3. Autentifică user-ul (query-urile merg AUTOMAT pe tenant_abc123_salon_maria)
    Authentication auth = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            request.getEmail(),    // "maria@salon.ro"
            request.getPassword()  // "SecurePass123!"
        )
    );
}
```

**Spring Security execută automat:**

```java
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    
    @Autowired
    private UserRepository userRepository; // JPA repository
    
    @Override
    public UserDetails loadUserByUsername(String email) {
        // Query executat pe tenant_abc123_salon_maria (datorită TenantContext)
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        return new UserPrincipal(user);
    }
}
```

**Query SQL executat pe `tenant_abc123_salon_maria`:**

```sql
SELECT * FROM users 
WHERE email = 'maria@salon.ro' 
  AND active = true
```

**Rezultat din DB:**

```sql
users table (în tenant_abc123_salon_maria):
┌────┬──────────────────┬─────────────────────────────┬────────┬──────────┬───────┬────────┐
│ id │ email            │ password_hash               │ f_name │ l_name   │ role  │ active │
├────┼──────────────────┼─────────────────────────────┼────────┼──────────┼───────┼────────┤
│ 1  │ maria@salon.ro   │ $2a$10$N9qo8uL...xyz123     │ Maria  │ Popescu  │ ADMIN │ true   │
│ 2  │ ana@salon.ro     │ $2a$10$A3bcd5e...abc789     │ Ana    │ Ion      │ STAFF │ true   │
└────┴──────────────────┴─────────────────────────────┴────────┴──────────┴───────┴────────┘
```

**Backend verifică parola:**

```java
// Spring Security folosește BCryptPasswordEncoder automat
boolean matches = BCrypt.checkpw("SecurePass123!", "$2a$10$N9qo8uL...xyz123");
// Returnează: true ✅ (parola corectă)
```

**Dacă parola e greșită:**
```java
// Aruncă: BadCredentialsException
// Frontend primește: 401 Unauthorized
```

---

### **PASUL 8: Backend generează JWT TOKEN**

După autentificare reușită:

```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    // 1-3. Am autentificat user-ul
    Tenant tenant = tenantService.findBySlug(request.getTenantSlug())
        .orElseThrow(() -> new TenantNotFoundException("Tenant inexistent"));
    
    TenantContext.setCurrentTenant(tenant.getId());
    
    Authentication auth = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            request.getEmail(),
            request.getPassword()
        )
    );
    
    // 4. Generează JWT token cu tenantId în payload
    String token = jwtTokenProvider.generateToken(auth, tenant.getId());
    
    // Continuăm...
}
```

**Implementare `JwtTokenProvider.java`:**

```java
@Component
public class JwtTokenProvider {
    
    @Value("${jwt.secret}")
    private String JWT_SECRET; // Secret pentru semnătura JWT
    
    private static final long JWT_EXPIRATION_MS = 86400000; // 24 ore
    
    public String generateToken(Authentication auth, String tenantId) {
        UserPrincipal user = (UserPrincipal) auth.getPrincipal();
        
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + JWT_EXPIRATION_MS); // +24h
        
        return Jwts.builder()
            .setSubject(user.getId().toString())         // userId = "1"
            .claim("tenantId", tenantId)                 // tenantId = "abc123" ← KEY!
            .claim("tenantSlug", "salon-maria")          // pentru logging/debugging
            .claim("roles", user.getAuthorities())       // ["ADMIN"]
            .claim("email", user.getEmail())             // "maria@salon.ro"
            .setIssuedAt(now)                            // timestamp creare
            .setExpiration(expiryDate)                   // timestamp expirare
            .signWith(SignatureAlgorithm.HS512, JWT_SECRET) // semnătură HMAC-SHA512
            .compact();
    }
}
```

**JWT Token generat arată așa:**

```
eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIiwidGVuYW50SWQiOiJhYmMxMjMiLCJ0ZW5hbnRTbHVnIjoic2Fsb24tbWFyaWEiLCJyb2xlcyI6WyJBRE1JTiJdLCJlbWFpbCI6Im1hcmlhQHNhbG9uLnJvIiwiaWF0IjoxNzM5NDY3MjAwLCJleHAiOjE3Mzk1NTM2MDB9.aB3cD4eF5gH6iJ7kL8mN9oP0qR1sT2uV3wX4yZ5aB3cD4eF5gH6iJ7kL8mN9oP0qR1sT2uV3wX4yZ5
```

**Structura JWT (3 părți separate prin `.`):**

```
HEADER . PAYLOAD . SIGNATURE
```

**1. HEADER (base64 encoded):**
```json
{
  "alg": "HS512",  // algoritmul de criptare (HMAC-SHA512)
  "typ": "JWT"     // tipul de token
}
```

**2. PAYLOAD (base64 encoded) - ATENȚIE: Nu este criptat, doar encodat!**
```json
{
  "sub": "1",                    // subject = userId
  "tenantId": "abc123",          // ← FOARTE IMPORTANT! ID-ul tenantului
  "tenantSlug": "salon-maria",   // slug pentru debugging
  "roles": ["ADMIN"],            // rolurile user-ului
  "email": "maria@salon.ro",     // email pentru convenience
  "iat": 1739467200,             // issued at (când a fost creat)
  "exp": 1739553600              // expiration (când expiră)
}
```

**3. SIGNATURE (garantează integritatea):**
```javascript
HMACSHA512(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  JWT_SECRET  // secretul cunoscut doar de backend
)
```

**De ce este sigur?**
- Oricine poate **decoda** și **citi** payload-ul (base64 decode)
- **NIMENI** nu poate **modifica** payload-ul fără să știe `JWT_SECRET`
- Dacă modifici payload-ul, semnătura nu mai corespunde → backend respinge token-ul

---

### **PASUL 9: Backend returnează token-ul la frontend**

```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    // 1-4. Am generat JWT token
    Tenant tenant = tenantService.findBySlug(request.getTenantSlug())
        .orElseThrow(() -> new TenantNotFoundException("Tenant inexistent"));
    
    TenantContext.setCurrentTenant(tenant.getId());
    
    Authentication auth = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            request.getEmail(),
            request.getPassword()
        )
    );
    
    String token = jwtTokenProvider.generateToken(auth, tenant.getId());
    
    // 5. Construiește response cu token + info user
    UserPrincipal userPrincipal = (UserPrincipal) auth.getPrincipal();
    
    AuthResponse response = new AuthResponse(
        token,
        new UserDTO(
            userPrincipal.getId(), 
            userPrincipal.getEmail(), 
            userPrincipal.getFirstName(), 
            userPrincipal.getLastName(), 
            userPrincipal.getRole()
        )
    );
    
    return ResponseEntity.ok(response);
}
```

**HTTP Response:**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIiwidGVuYW50SWQiOiJhYmMxMjMi...",
  "user": {
    "id": 1,
    "email": "maria@salon.ro",
    "firstName": "Maria",
    "lastName": "Popescu",
    "role": "ADMIN"
  }
}
```

---

### **PASUL 10: Frontend primește token-ul și îl salvează**

Angular primește response-ul:

```typescript
// auth.service.ts (Angular)
export class AuthService {
  
  constructor(
    private http: HttpClient,
    private router: Router
  ) {}
  
  login(email: string, password: string): Observable<AuthResponse> {
    const tenantSlug = this.tenantService.getTenantFromSubdomain();
    
    const body = { tenantSlug, email, password };
    
    return this.http.post<AuthResponse>('/api/auth/login', body)
      .pipe(
        tap((response: AuthResponse) => {
          // Salvează token în localStorage (browser storage)
          localStorage.setItem('auth_token', response.token);
          
          // Salvează și user info pentru display
          localStorage.setItem('current_user', JSON.stringify(response.user));
          
          // Redirect la dashboard
          this.router.navigate(['/dashboard']);
        })
      );
  }
  
  getToken(): string | null {
    return localStorage.getItem('auth_token');
  }
  
  getCurrentUser(): User | null {
    const userJson = localStorage.getItem('current_user');
    return userJson ? JSON.parse(userJson) : null;
  }
  
  logout(): void {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('current_user');
    this.router.navigate(['/login']);
  }
}
```

**Browser localStorage după login:**

```javascript
// Inspect în Chrome DevTools → Application → Local Storage
localStorage = {
  "auth_token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIiwidGVuYW50SWQi...",
  "current_user": '{"id":1,"email":"maria@salon.ro","firstName":"Maria","lastName":"Popescu","role":"ADMIN"}'
}
```

**⚠️ Securitate localStorage:**
- ✅ Simplu de implementat
- ❌ Vulnerabil la XSS attacks (dacă site-ul are vulnerabilități JavaScript)
- 🔐 Alternativa: **HttpOnly cookies** (mai sigur, dar mai complex)

---

### **PASUL 11: Frontend cere date (programări, clienți, etc.)**

Maria este acum pe `/dashboard` și Angular vrea să încarce programările zilei:

```typescript
// appointment.service.ts (Angular)
export class AppointmentService {
  
  constructor(private http: HttpClient) {}
  
  getAppointments(): Observable<Appointment[]> {
    // JWT Interceptor adaugă AUTOMAT token-ul în header (vezi mai jos)
    return this.http.get<Appointment[]>('/api/appointments');
  }
}
```

**JWT Interceptor (Angular) - adaugă automat token-ul:**

```typescript
// jwt.interceptor.ts
@Injectable()
export class JwtInterceptor implements HttpInterceptor {
  
  constructor(private authService: AuthService) {}
  
  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    // Obține token din localStorage
    const token = this.authService.getToken();
    
    if (token) {
      // Clonează request-ul și adaugă header Authorization
      request = request.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
    }
    
    return next.handle(request);
  }
}
```

**HTTP Request trimis la backend:**

```http
GET https://api.app.ro/api/appointments
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIiwidGVuYW50SWQiOiJhYmMxMjMi...
```

---

### **PASUL 12: Backend interceptează request-ul și extrage tenantId din JWT**

**Înainte** ca request-ul să ajungă la `AppointmentController`, trece prin **`TenantInterceptor`:**

```java
@Component
public class TenantInterceptor implements HandlerInterceptor {
    
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        // 1. Extrage token din header Authorization
        String token = extractToken(request);
        // token = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIi..."
        
        if (token != null) {
            // 2. Decodează token și extrage tenantId
            String tenantId = jwtTokenProvider.getTenantIdFromToken(token);
            // tenantId = "abc123"
            
            // 3. Setează tenant context pentru acest request
            TenantContext.setCurrentTenant(tenantId);
            // Acum toate query-urile vor merge pe DB-ul tenant_abc123_salon_maria
        }
        
        return true; // continuă cu procesarea request-ului
    }

    @Override
    public void afterCompletion(HttpServletRequest request, 
                               HttpServletResponse response, 
                               Object handler, 
                               Exception ex) {
        // 4. Curăță ThreadLocal după ce request-ul s-a terminat
        TenantContext.clear();
        // IMPORTANT: Evită memory leaks și confuzia între request-uri
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        // bearerToken = "Bearer eyJhbGciOiJIUzUxMiJ9..."
        
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // scapă de "Bearer " prefix
        }
        return null;
    }
}
```

**Decodare JWT pentru a extrage tenantId:**

```java
// JwtTokenProvider.java
public String getTenantIdFromToken(String token) {
    Claims claims = Jwts.parser()
        .setSigningKey(JWT_SECRET)  // verifică semnătura
        .parseClaimsJws(token)      // aruncă excepție dacă token modificat
        .getBody();                 // extrage payload
    
    return claims.get("tenantId", String.class); // "abc123"
}
```

**Rezultat:**
- ✅ `TenantContext.getCurrentTenant()` returnează `"abc123"`
- ✅ Toate query-urile SQL vor merge automat pe DB-ul `tenant_abc123_salon_maria`

**Configurare Interceptor în Spring:**

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private TenantInterceptor tenantInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**")      // aplică pe toate API-urile
                .excludePathPatterns("/api/auth/**"); // EXCLUDE login/signup
    }
}
```

**De ce exclude `/api/auth/**`?**
- La login, user-ul **NU are încă JWT token**
- Tenant-ul se determină din `request.body.tenantSlug`, nu din JWT

---

### **PASUL 13: Controller procesează request-ul**

Request-ul ajunge la controller:

```java
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    @Autowired
    private AppointmentService appointmentService;

    @GetMapping
    public ResponseEntity<List<AppointmentDTO>> getAppointments() {
        // TenantContext.getCurrentTenant() = "abc123" (setat de TenantInterceptor)
        
        // Service face query (va merge automat pe DB-ul corect)
        List<AppointmentDTO> appointments = appointmentService.findAll();
        
        return ResponseEntity.ok(appointments);
    }
}
```

**Service execută query:**

```java
@Service
public class AppointmentService {
    
    @Autowired
    private AppointmentRepository appointmentRepository;
    
    public List<AppointmentDTO> findAll() {
        // Repository face query pe DB-ul corect AUTOMAT
        List<Appointment> appointments = appointmentRepository.findAll();
        
        // SQL executat pe tenant_abc123_salon_maria:
        // SELECT * FROM appointments 
        // WHERE appointment_date = CURRENT_DATE
        // ORDER BY start_time
        
        return appointments.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    private AppointmentDTO toDTO(Appointment appointment) {
        return new AppointmentDTO(
            appointment.getId(),
            appointment.getClient().getFirstName() + " " + appointment.getClient().getLastName(),
            appointment.getService().getName(),
            appointment.getAppointmentDate(),
            appointment.getStartTime(),
            appointment.getEndTime(),
            appointment.getStatus()
        );
    }
}
```

**Query-ul merge AUTOMAT pe DB-ul corect datorită `AbstractRoutingDataSource`:**

```
1. Repository.findAll() este apelat
   ↓
2. Spring Data JPA cere o conexiune de la DataSource
   ↓
3. AbstractRoutingDataSource.determineCurrentLookupKey() 
   returnează TenantContext.getCurrentTenant() = "abc123"
   ↓
4. DataSource switch-uiește conexiunea la tenant_abc123_salon_maria
   ↓
5. Query executat: SELECT * FROM appointments...
   ↓
6. Rezultate returnate din DB-ul tenant_abc123_salon_maria
```

**SQL executat pe `tenant_abc123_salon_maria`:**

```sql
SELECT 
  a.id,
  a.appointment_date,
  a.start_time,
  a.end_time,
  a.status,
  c.first_name || ' ' || c.last_name as client_name,
  s.name as service_name
FROM appointments a
JOIN clients c ON a.client_id = c.id
JOIN services s ON a.service_id = s.id
WHERE a.appointment_date = CURRENT_DATE
ORDER BY a.start_time
```

**Rezultat din DB:**

```sql
┌────┬─────────────────┬────────────┬──────────┬───────────┬────────────────────┬─────────────────┐
│ id │ appointment_date│ start_time │ end_time │ status    │ client_name        │ service_name    │
├────┼─────────────────┼────────────┼──────────┼───────────┼────────────────────┼─────────────────┤
│101 │ 2026-02-13      │ 10:00:00   │ 12:00:00 │ scheduled │ Ana Ionescu        │ Coafură+Vopsit  │
│102 │ 2026-02-13      │ 14:00:00   │ 15:30:00 │ confirmed │ Elena Popa         │ Manichiură      │
│103 │ 2026-02-13      │ 16:00:00   │ 17:00:00 │ scheduled │ Mihai Georgescu    │ Tunsoare        │
└────┴─────────────────┴────────────┴──────────┴───────────┴────────────────────┴─────────────────┘
```

---

### **PASUL 14: Backend returnează datele la frontend**

```java
@GetMapping
public ResponseEntity<List<AppointmentDTO>> getAppointments() {
    List<AppointmentDTO> appointments = appointmentService.findAll();
    return ResponseEntity.ok(appointments); // Spring convertește automat în JSON
}
```

**HTTP Response:**

```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "id": 101,
    "clientName": "Ana Ionescu",
    "serviceName": "Coafură + Vopsit",
    "date": "2026-02-13",
    "startTime": "10:00",
    "endTime": "12:00",
    "status": "scheduled"
  },
  {
    "id": 102,
    "clientName": "Elena Popa",
    "serviceName": "Manichiură",
    "date": "2026-02-13",
    "startTime": "14:00",
    "endTime": "15:30",
    "status": "confirmed"
  },
  {
    "id": 103,
    "clientName": "Mihai Georgescu",
    "serviceName": "Tunsoare",
    "date": "2026-02-13",
    "startTime": "16:00",
    "endTime": "17:00",
    "status": "scheduled"
  }
]
```

---

### **PASUL 15: Frontend afișează datele în UI**

Angular primește datele:

```typescript
// appointments.component.ts
export class AppointmentsComponent implements OnInit {
  
  appointments: Appointment[] = [];
  loading = true;
  
  constructor(private appointmentService: AppointmentService) {}
  
  ngOnInit(): void {
    this.loadAppointments();
  }
  
  loadAppointments(): void {
    this.appointmentService.getAppointments().subscribe(
      (data: Appointment[]) => {
        this.appointments = data;
        this.loading = false;
      },
      (error) => {
        console.error('Error loading appointments:', error);
        this.loading = false;
      }
    );
  }
}
```

**Template HTML:**

```html
<!-- appointments.component.html -->
<div class="dashboard">
  <h2>Salon Maria - Dashboard</h2>
  <h3>Programări astăzi ({{ today | date:'dd MMM yyyy' }})</h3>
  
  <div *ngIf="loading" class="spinner">Loading...</div>
  
  <div *ngIf="!loading" class="appointments-list">
    <div *ngFor="let apt of appointments" class="appointment-card">
      <div class="time">{{ apt.startTime }} - {{ apt.endTime }}</div>
      <div class="client">{{ apt.clientName }}</div>
      <div class="service">{{ apt.serviceName }}</div>
      <div class="status" [class.scheduled]="apt.status === 'scheduled'"
                         [class.confirmed]="apt.status === 'confirmed'">
        {{ apt.status }}
      </div>
    </div>
  </div>
</div>
```

**Maria vede în browser:**

```
┌─────────────────────────────────────────────────────────────────┐
│  Salon Maria - Dashboard                     👤 Maria | Logout  │
├─────────────────────────────────────────────────────────────────┤
│  Programări astăzi (13 Feb 2026):                               │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 10:00 - 12:00  │ Ana Ionescu     │ Coafură+Vopsit    │📅│   │
│  ├──────────────────────────────────────────────────────────┤   │
│  │ 14:00 - 15:30  │ Elena Popa      │ Manichiură        │✅│   │
│  ├──────────────────────────────────────────────────────────┤   │
│  │ 16:00 - 17:00  │ Mihai Georgescu │ Tunsoare          │📅│   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  [+ Adaugă programare nouă]                                     │
└─────────────────────────────────────────────────────────────────┘
```

**✅ Succes! Maria vede programările din baza ei de date (`tenant_abc123_salon_maria`), nu din alte saloane!**

---

## 📊 Vizualizare Completă Flow

```
┌───────────────────────────────────────────────────────────────────┐
│ PASUL 1-2: Maria deschide salon-maria.app.ro/login               │
│            Frontend detectează tenant="salon-maria"               │
└────────────────────────────┬──────────────────────────────────────┘
                             │
                             ▼
┌───────────────────────────────────────────────────────────────────┐
│ PASUL 3: Frontend → POST /api/auth/login                         │
│          Body: { tenantSlug, email, password }                    │
└────────────────────────────┬──────────────────────────────────────┘
                             │
                             ▼
┌───────────────────────────────────────────────────────────────────┐
│ PASUL 4-5: Backend caută în MASTER DB (tenant_registry)          │
│            SELECT * FROM tenants WHERE slug='salon-maria'         │
│            Găsește: id="abc123", db_name="tenant_abc123_..."      │
└────────────────────────────┬──────────────────────────────────────┘
                             │
                             ▼
┌───────────────────────────────────────────────────────────────────┐
│ PASUL 6: Backend setează TenantContext.setCurrentTenant("abc123")│
│          DataSource switch la tenant_abc123_salon_maria           │
└────────────────────────────┬──────────────────────────────────────┘
                             │
                             ▼
┌───────────────────────────────────────────────────────────────────┐
│ PASUL 7: Backend verifică credentials în DB tenant               │
│          SELECT * FROM users WHERE email='maria@salon.ro'         │
│          BCrypt verifică password_hash ✅                         │
└────────────────────────────┬──────────────────────────────────────┘
                             │
                             ▼
┌───────────────────────────────────────────────────────────────────┐
│ PASUL 8-9: Backend generează JWT cu tenantId="abc123" în payload │
│            Response: { token: "eyJ...", user: {...} }             │
└────────────────────────────┬──────────────────────────────────────┘
                             │
                             ▼
┌───────────────────────────────────────────────────────────────────┐
│ PASUL 10: Frontend salvează token în localStorage                │
│           localStorage.setItem('auth_token', token)               │
│           Redirect → /dashboard                                   │
└────────────────────────────┬──────────────────────────────────────┘
                             │
                             ▼
┌───────────────────────────────────────────────────────────────────┐
│ PASUL 11: Frontend → GET /api/appointments                       │
│           Header: Authorization: Bearer eyJ...                    │
└────────────────────────────┬──────────────────────────────────────┘
                             │
                             ▼
┌───────────────────────────────────────────────────────────────────┐
│ PASUL 12: TenantInterceptor extrage tenantId="abc123" din JWT    │
│           TenantContext.setCurrentTenant("abc123")                │
└────────────────────────────┬──────────────────────────────────────┘
                             │
                             ▼
┌───────────────────────────────────────────────────────────────────┐
│ PASUL 13: Controller → Service → Repository                      │
│           Query AUTOMAT pe tenant_abc123_salon_maria              │
│           SELECT * FROM appointments WHERE date=CURRENT_DATE      │
└────────────────────────────┬──────────────────────────────────────┘
                             │
                             ▼
┌───────────────────────────────────────────────────────────────────┐
│ PASUL 14-15: Backend → JSON response → Frontend afișează UI      │
│              Maria vede programările din salonul ei               │
└───────────────────────────────────────────────────────────────────┘
```

---

## Concepte Cheie Explicate

### **1. JWT Token - Ce Este?**

**JSON Web Token** = un string format din 3 părți: `HEADER.PAYLOAD.SIGNATURE`

**Caracteristici:**
- ✅ **Self-contained:** conține toate info necesare (userId, tenantId, roles)
- ✅ **Stateless:** serverul NU trebuie să țină sesiuni în memorie/DB
- ✅ **Semnat:** garantează că nu a fost modificat (HMAC-SHA512)
- ❌ **NU este criptat:** oricine poate decoda și citi payload-ul

**Exemplu decodare:**
```javascript
// În browser console (Chrome DevTools)
const token = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIiwidGVuYW50SWQi...";
const parts = token.split('.');
const payload = JSON.parse(atob(parts[1])); // base64 decode
console.log(payload);
// Output:
// {
//   "sub": "1",
//   "tenantId": "abc123",
//   "roles": ["ADMIN"],
//   "email": "maria@salon.ro",
//   "iat": 1739467200,
//   "exp": 1739553600
// }
```

**De ce este sigur dacă oricine poate citi payload-ul?**
- Payload-ul conține doar info non-sensibile (userId, tenantId, roles)
- **NU pune parole, date card, etc. în JWT!**
- Semnătura (`SIGNATURE`) garantează că nimeni nu poate **modifica** payload-ul

**Cum verifică backend semnătura?**
```java
Jwts.parser()
    .setSigningKey(JWT_SECRET)  // secret cunoscut doar de backend
    .parseClaimsJws(token)      // aruncă excepție dacă semnătura e greșită
    .getBody();
```

---

### **2. TenantContext (ThreadLocal)**

**ThreadLocal** = variabilă care există **doar pentru thread-ul curent**

**De ce este necesar?**
- Serverul procesează **multiple request-uri simultan** (multe thread-uri)
- Request de la Maria (thread 1) → tenantId = "abc123"
- Request de la Ion (thread 2) → tenantId = "xyz789"
- **Trebuie să se izoleze** ca să nu se amestece datele!

**Implementare:**
```java
public class TenantContext {
    // ThreadLocal = variabilă unică per thread
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    
    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId); // salvează în thread-ul curent
    }
    
    public static String getCurrentTenant() {
        return CURRENT_TENANT.get(); // citește din thread-ul curent
    }
    
    public static void clear() {
        CURRENT_TENANT.remove(); // IMPORTANT: curăță după request
    }
}
```

**⚠️ IMPORTANT: Curățare ThreadLocal**
```java
@Override
public void afterCompletion(...) {
    TenantContext.clear(); // OBLIGATORIU!
}
```

**De ce clear() este important?**
- Thread-urile sunt **refolosite** (thread pool)
- Dacă NU cureți, următorul request pe același thread va avea tenantId greșit!
- Poate duce la **data leak** între tenanți (CRITICAL BUG!)

---

### **3. AbstractRoutingDataSource**

**Ce face?**
- Spring verifică `determineCurrentLookupKey()` înainte de fiecare query SQL
- Returnează cheia (tenantId) pentru a alege conexiunea DB corectă
- Switch-uiește automat conexiunea

**Implementare:**
```java
public class TenantRoutingDataSource extends AbstractRoutingDataSource {
    
    @Override
    protected Object determineCurrentLookupKey() {
        // Cheie = tenantId curent din ThreadLocal
        return TenantContext.getCurrentTenant();
    }
}
```

**Configurare DataSources:**
```java
@Bean
public DataSource dataSource() {
    Map<Object, Object> dataSources = new HashMap<>();
    
    // Master DB
    DataSource masterDS = createDataSource("jdbc:postgresql://.../tenant_registry");
    
    // Tenant DBs
    List<Tenant> tenants = getAllTenants();
    for (Tenant tenant : tenants) {
        DataSource tenantDS = createDataSource(tenant.getJdbcUrl());
        dataSources.put(tenant.getId(), tenantDS); // key = "abc123", value = DataSource
    }
    
    TenantRoutingDataSource routing = new TenantRoutingDataSource();
    routing.setDefaultTargetDataSource(masterDS);
    routing.setTargetDataSources(dataSources);
    routing.afterPropertiesSet();
    
    return routing;
}
```

**Flow la runtime:**
```
1. Repository.findAll() este apelat
   ↓
2. Spring JPA cere conexiune: dataSource.getConnection()
   ↓
3. AbstractRoutingDataSource.determineCurrentLookupKey()
   returnează TenantContext.getCurrentTenant() = "abc123"
   ↓
4. Spring lookup: targetDataSources.get("abc123")
   returnează DataSource pentru tenant_abc123_salon_maria
   ↓
5. Query executat pe DB-ul corect
```

**Developer-ul scrie cod simplu:**
```java
List<Appointment> appointments = appointmentRepository.findAll();
// Routing se întâmplă TRANSPARENT în background!
```

---

### **4. Interceptor Pattern**

**Ce este un Interceptor?**
- Cod care rulează **ÎNAINTE și DUPĂ** fiecare request HTTP
- Similar cu middleware în Express.js sau filters în Servlet

**Implementare:**
```java
@Component
public class TenantInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(...) {
        // Rulează ÎNAINTE de Controller
        // Extrage tenantId din JWT → setează TenantContext
        return true; // continuă cu request-ul
    }
    
    @Override
    public void afterCompletion(...) {
        // Rulează DUPĂ Controller (chiar dacă a fost excepție)
        // Curăță TenantContext
        TenantContext.clear();
    }
}
```

**Înregistrare în Spring:**
```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private TenantInterceptor tenantInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**")           // aplică pe toate API-urile
                .excludePathPatterns("/api/auth/**"); // EXCLUDE login
    }
}
```

**Ordinea de execuție:**
```
Request → TenantInterceptor.preHandle()
          → Controller
          → Service
          → Repository (query cu tenant corect)
          → Response
          → TenantInterceptor.afterCompletion()
```

---

## Securitate

### **Scenariul 1: Atacator interceptează token-ul Mariei**

**Cum se întâmplă?**
- Man-in-the-middle attack (WiFi public nesecurizat)
- XSS attack (JavaScript malițios pe site)
- Malware pe device-ul Mariei

**Ce poate face atacatorul?**
```
Atacatorul obține: eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIiwidGVuYW50SWQiOiJhYmMxMjMi...
```
- ❌ Poate folosi token-ul pentru a accesa datele salonului Maria
- ❌ Poate crea/modifica/șterge programări
- ❌ Poate accesa date clienți (GDPR violation!)

**Mitigări:**

1. **HTTPS Obligatoriu (SSL/TLS)**
   - Criptează toată comunicația browser ↔ server
   - Man-in-the-middle nu poate intercepta token-ul

2. **Token-uri Short-Lived**
   ```java
   private static final long JWT_EXPIRATION_MS = 3600000; // 1 oră (nu 24h)
   ```
   - Reduce window-ul de atac

3. **Refresh Tokens**
   ```
   Login → primești 2 tokens:
   - Access Token (JWT, 15 min, în localStorage)
   - Refresh Token (random UUID, 7 zile, în HttpOnly cookie)
   
   După 15 min:
   - POST /api/auth/refresh (trimite refresh token)
   - Backend verifică în DB → generează JWT nou
   ```

4. **HttpOnly Cookies** (mai sigur decât localStorage)
   ```java
   Cookie cookie = new Cookie("auth_token", token);
   cookie.setHttpOnly(true);  // JavaScript nu poate accesa
   cookie.setSecure(true);    // doar HTTPS
   cookie.setPath("/");
   response.addCookie(cookie);
   ```

5. **Rate Limiting**
   ```java
   @RateLimiter(permits = 100, timeUnit = TimeUnit.MINUTES)
   public ResponseEntity<?> getAppointments() { ... }
   ```

6. **IP Whitelisting** (pentru admin)
   ```java
   if (user.getRole() == ADMIN && !allowedIps.contains(request.getRemoteAddr())) {
       throw new UnauthorizedException("Admin access only from office IP");
   }
   ```

---

### **Scenariul 2: Atacator modifică token-ul**

**Atacatorul încearcă:**
```javascript
// 1. Decodează token (base64 decode)
const token = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIi...";
const parts = token.split('.');
const payload = JSON.parse(atob(parts[1]));

// 2. Modifică payload (promovează la ADMIN)
payload.roles = ["ADMIN"];
payload.tenantId = "xyz789"; // încearcă să acceseze alt tenant!

// 3. Re-encodează
const newPayload = btoa(JSON.stringify(payload));
const newToken = parts[0] + '.' + newPayload + '.' + parts[2];
```

**Backend verifică semnătura:**
```java
try {
    Jwts.parser()
        .setSigningKey(JWT_SECRET)  // secret cunoscut doar de backend
        .parseClaimsJws(newToken)   // ← ARUNCĂ EXCEPȚIE!
        .getBody();
} catch (SignatureException e) {
    // Token-ul a fost modificat!
    throw new UnauthorizedException("Invalid token signature");
}
```

**Response:**
```http
HTTP/1.1 401 Unauthorized
{
  "status": 401,
  "message": "Invalid token signature",
  "timestamp": 1739467890
}
```

**✅ Token-ul modificat este automat respins!**

---

### **Scenariul 3: Atacator încearcă SQL Injection**

**Request malițios:**
```http
GET /api/clients?name=Maria'; DROP TABLE clients; --
```

**Spring Data JPA folosește Prepared Statements automat:**
```java
// Backend (JPA Repository)
List<Client> findByFirstNameContaining(String name);

// Query generat automat:
SELECT * FROM clients WHERE first_name LIKE ?
// Parametru: "%Maria'; DROP TABLE clients; --%"
```

**Rezultat:**
- ✅ `name` este tratat ca **string literal**, nu cod SQL
- ✅ SQL Injection **IMPOSIBIL** cu JPA/Hibernate

**⚠️ EXCEPȚIE: Native Queries**
```java
// VULNERABIL:
@Query(value = "SELECT * FROM clients WHERE name = '" + name + "'", nativeQuery = true)
List<Client> findByNameUnsafe(String name);

// SIGUR:
@Query(value = "SELECT * FROM clients WHERE name = :name", nativeQuery = true)
List<Client> findByNameSafe(@Param("name") String name);
```

---

## Întrebări Frecvente

### **Q: De ce nu salvăm tenantId în sesiune server-side?**

**A:** JWT = **stateless authentication**

**Comparație:**

| Aspect | JWT (stateless) | Session (stateful) |
|--------|-----------------|-------------------|
| **Storage** | Token în client (localStorage) | SessionId în server (Redis/memcached) |
| **Scalare** | ✅ Perfect pentru microservicii | ❌ Session sharing între servere complicat |
| **Memory** | ✅ Zero memorie pe server | ❌ RAM usage × număr useri |
| **Load Balancer** | ✅ Orice server poate procesa orice request | ⚠️ Sticky sessions necesare |

**Exemplu scalare:**
```
Setup 1: JWT (stateless)
- 1000 instanțe Cloud Run
- Request de la Maria poate fi procesat de ORICE instanță
- Zero sincronizare între instanțe

Setup 2: Sessions (stateful)
- 1000 instanțe Cloud Run
- Request de la Maria TREBUIE să ajungă la instanța care are sesiunea ei
- Necesită Redis cluster pentru session sharing (cost + complexitate)
```

---

### **Q: De ce tenantId în JWT, nu doar în URL?**

**A:** URL poate fi modificat de user (security risk)

**Scenariul de atac:**
```
1. Maria se loghează pe salon-maria.app.ro
2. Primește JWT cu tenantId="abc123"
3. Atacatorul modifică URL: salon-maria.app.ro → cabinet-ionescu.app.ro
4. Dacă tenantId ar fi doar din URL → ar accesa datele cabinet-ului Ion!
```

**Cu JWT:**
```
1. Maria se loghează → JWT cu tenantId="abc123"
2. Orice URL accesează → backend extrage tenantId="abc123" din JWT
3. Modificare URL este IGNORATĂ
4. Maria vede DOAR datele salonului ei
```

---

### **Q: Ce se întâmplă la logout?**

**Frontend:**
```typescript
logout(): void {
  localStorage.removeItem('auth_token');
  localStorage.removeItem('current_user');
  this.router.navigate(['/login']);
}
```

**Backend:**
- Token-ul rămâne **valid** până la expirare
- Backend **NU poate "invalida" un JWT** (stateless by design)

**Problemă:**
```
1. Maria se loghează → JWT expiră în 24h
2. După 1 oră, Maria face logout
3. Dacă cineva fură token-ul → poate fi folosit 23 ore!
```

**Soluții:**

**Opțiunea 1: Short-lived tokens + Refresh tokens**
```java
Access Token: 15 minute
Refresh Token: 7 zile (stored în DB, poate fi invalidat)
```

**Opțiunea 2: Token Blacklist (Redis)**
```java
@PostMapping("/logout")
public ResponseEntity<?> logout(@RequestHeader("Authorization") String token) {
    String jti = jwtTokenProvider.getJwtId(token); // unique ID
    long ttl = jwtTokenProvider.getExpirationSeconds(token);
    
    // Adaugă în blacklist (Redis)
    redisTemplate.opsForValue().set(
        "blacklist:" + jti,
        "revoked",
        ttl,
        TimeUnit.SECONDS
    );
    
    return ResponseEntity.ok("Logged out");
}

// În TenantInterceptor
if (redisTemplate.hasKey("blacklist:" + jti)) {
    throw new UnauthorizedException("Token revoked");
}
```

---

### **Q: Cum funcționează Refresh Tokens?**

**Setup:**
```java
// La login, generează 2 tokens
String accessToken = generateAccessToken(user, tenant);   // JWT, 15 min
String refreshToken = UUID.randomUUID().toString();       // Random, 7 zile

// Salvează refresh token în DB
RefreshToken rt = new RefreshToken();
rt.setToken(refreshToken);
rt.setUserId(user.getId());
rt.setTenantId(tenant.getId());
rt.setExpiresAt(LocalDateTime.now().plusDays(7));
refreshTokenRepository.save(rt);

// Returnează ambele
return new AuthResponse(accessToken, refreshToken, user);
```

**Frontend salvează:**
```typescript
localStorage.setItem('access_token', response.accessToken);   // 15 min
localStorage.setItem('refresh_token', response.refreshToken); // 7 zile
```

**După 15 minute, access token expiră:**
```typescript
// HTTP Interceptor detectează 401 Unauthorized
intercept(request: HttpRequest<any>, next: HttpHandler) {
  return next.handle(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !request.url.includes('/auth/')) {
        // Token expirat, încearcă refresh
        return this.refreshToken().pipe(
          switchMap((newToken) => {
            // Retry request-ul original cu token nou
            const cloned = request.clone({
              setHeaders: { Authorization: `Bearer ${newToken}` }
            });
            return next.handle(cloned);
          })
        );
      }
      return throwError(error);
    })
  );
}

refreshToken(): Observable<string> {
  const refreshToken = localStorage.getItem('refresh_token');
  return this.http.post<AuthResponse>('/api/auth/refresh', { refreshToken })
    .pipe(
      map(response => {
        localStorage.setItem('access_token', response.accessToken);
        return response.accessToken;
      })
    );
}
```

**Backend refresh endpoint:**
```java
@PostMapping("/auth/refresh")
public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
    // Verifică refresh token în DB
    RefreshToken rt = refreshTokenRepository.findByToken(request.getRefreshToken())
        .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
    
    // Verifică dacă a expirat
    if (rt.getExpiresAt().isBefore(LocalDateTime.now())) {
        refreshTokenRepository.delete(rt);
        throw new UnauthorizedException("Refresh token expired");
    }
    
    // Generează access token NOU
    User user = userRepository.findById(rt.getUserId()).orElseThrow();
    String newAccessToken = jwtTokenProvider.generateAccessToken(
        user, 
        rt.getTenantId()
    );
    
    return ResponseEntity.ok(new RefreshResponse(newAccessToken));
}
```

**Beneficii:**
- ✅ Access token short-lived (15 min) → risc redus dacă este furat
- ✅ Refresh token în DB → poate fi invalidat la logout
- ✅ User experience bun (nu trebuie să se relogheze la fiecare 15 min)

---

### **Q: Ce se întâmplă dacă 2 request-uri ajung simultan de la useri diferiți?**

**Scenario:**
```
Request 1 (Maria, thread 1): GET /api/appointments
Request 2 (Ion, thread 2):   GET /api/appointments
```

**Procesare:**
```
Thread 1:
  TenantInterceptor.preHandle() → TenantContext.set("abc123")
  Query pe tenant_abc123_salon_maria
  TenantInterceptor.afterCompletion() → TenantContext.clear()

Thread 2 (SIMULTAN):
  TenantInterceptor.preHandle() → TenantContext.set("xyz789")
  Query pe tenant_xyz789_cabinet_ion
  TenantInterceptor.afterCompletion() → TenantContext.clear()
```

**NU se amestecă** datorită **ThreadLocal**:
- Thread 1 → `TenantContext = "abc123"` (variabilă locală thread-ului 1)
- Thread 2 → `TenantContext = "xyz789"` (variabilă locală thread-ului 2)

---

### **Q: Cum testăm tenant routing?**

**Unit Test:**
```java
@SpringBootTest
class TenantRoutingTest {
    
    @Autowired
    private AppointmentRepository appointmentRepository;
    
    @Test
    void testTenantIsolation() {
        // Setup: creează data în 2 tenants
        TenantContext.setCurrentTenant("abc123");
        Appointment apt1 = new Appointment();
        apt1.setClientName("Ana");
        appointmentRepository.save(apt1);
        TenantContext.clear();
        
        TenantContext.setCurrentTenant("xyz789");
        Appointment apt2 = new Appointment();
        apt2.setClientName("Ion");
        appointmentRepository.save(apt2);
        TenantContext.clear();
        
        // Test: tenant abc123 vede doar Ana
        TenantContext.setCurrentTenant("abc123");
        List<Appointment> apts = appointmentRepository.findAll();
        assertEquals(1, apts.size());
        assertEquals("Ana", apts.get(0).getClientName());
        TenantContext.clear();
        
        // Test: tenant xyz789 vede doar Ion
        TenantContext.setCurrentTenant("xyz789");
        apts = appointmentRepository.findAll();
        assertEquals(1, apts.size());
        assertEquals("Ion", apts.get(0).getClientName());
        TenantContext.clear();
    }
}
```

---

## Resurse Suplimentare

**Documentație:**
- [JWT.io](https://jwt.io/) - Decodare JWT online
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
- [Baeldung - Multi-Tenancy](https://www.baeldung.com/spring-abstract-routing-data-source)

**Tools:**
- Postman - testare API-uri cu JWT
- Chrome DevTools → Application → Local Storage (vezi token-ul salvat)
- jwt.io - decodare JWT pentru debugging

---

**Document Version:** 1.0  
**Last Updated:** Februarie 13, 2026  
**Author:** GitHub Copilot  

**💡 Pentru întrebări suplimentare despre autentificare, consultă documentul principal: [MULTI-TENANT-ARCHITECTURE.md](MULTI-TENANT-ARCHITECTURE.md)**

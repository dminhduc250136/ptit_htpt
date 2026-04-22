# Microservices Architecture: E-Commerce System

**Project:** PTIT HTPT E-Commerce Platform  
**Architecture Pattern:** Microservices (Spring Boot)  
**Target Deployment:** Docker Compose (dev), Kubernetes-ready (prod)  
**Researched:** April 22, 2026  
**Purpose:** Educational reference for distributed system patterns

---

## System Overview

### Services Architecture

```
                        ┌─────────────────┐
                        │  API Gateway    │
                        │  (Port 8080)    │
                        └────────┬────────┘
                                 │
                ┌────────────────┼────────────────┐
                │                │                │
        ┌───────▼───────┐  ┌────▼──────┐  ┌────▼──────┐
        │  User Service │  │  Product  │  │   Order   │
        │  (8081)       │  │  Service  │  │  Service  │
        │               │  │  (8082)   │  │  (8083)   │
        └───────┬───────┘  └─────┬────┘  └────┬──────┘
                │                 │            │
        ┌───────▼────────┐  ┌────▼─────┐  ┌──▼──────────┐
        │  Auth Service  │  │ Inventory │  │  Payment    │
        │  (Redis)       │  │ Service   │  │  Service    │
        │                │  │ (8085)    │  │  (8084)     │
        └────────────────┘  └───┬──────┘  └──┬──────────┘
                                 │           │
                            ┌────▼───────────▼──┐
                            │ Notification      │
                            │ Service (8086)    │
                            │ (Async - Message) │
                            └───────────────────┘

        ┌──────────────────────────────────────────┐
        │         Event Bus / Message Queue         │
        │    (RabbitMQ / Kafka for async comms)    │
        └──────────────────────────────────────────┘

        ┌──────────────────────────────────────────┐
        │   Databases (Independent per Service)    │
        │ User-DB │ Product-DB │ Order-DB │ etc    │
        └──────────────────────────────────────────┘
```

---

## Service Boundaries & Ownership

### 1. **API Gateway Service** (Port 8080)
- **Responsibility:** Single entry point, request routing, rate limiting, authentication delegation
- **Owns:** Request/response transformation, API versioning, aggregate responses
- **Data Model:** None (stateless router)
- **Dependencies:** All downstream services
- **Key Endpoints:**
  - `/api/users/*` → User Service
  - `/api/products/*` → Product Service
  - `/api/orders/*` → Order Service
  - `/api/payments/*` → Payment Service

**Technology:** Spring Cloud Gateway or Netflix Zuul

```yaml
# Gateway Route Config Example
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://user-service:8081
          predicates:
            - Path=/api/users/**
          filters:
            - RewritePath=/api/users/(?<path>.*), /$\{path}
        
        - id: order-service
          uri: http://order-service:8083
          predicates:
            - Path=/api/orders/**
```

### 2. **User Service** (Port 8081)
- **Responsibility:** User registration, profile management, authentication tokens
- **Owns:** User identity, credentials (hashed), user profiles, roles
- **Data Model:**
  ```
  User {
    id (UUID)
    email (unique)
    password_hash
    profile {
      name, phone, avatar_url
    }
    roles [CUSTOMER, ADMIN, VENDOR]
    created_at, updated_at
  }
  ```
- **Database:** PostgreSQL (User-DB)
- **API Contract:**
  ```
  POST   /users/register        → Create user
  POST   /users/login           → Authenticate (returns JWT)
  GET    /users/{id}            → Get profile (auth required)
  PUT    /users/{id}            → Update profile
  GET    /users/{id}/validate   → Internal: validate user exists
  ```

### 3. **Product Service** (Port 8082)
- **Responsibility:** Product catalog, inventory sync, product metadata
- **Owns:** Product definitions, categories, pricing, images, descriptions
- **Data Model:**
  ```
  Product {
    id (UUID)
    sku (unique)
    name, description
    category_id
    price
    images []
    vendor_id
    active (boolean)
    created_at
  }
  
  Category {
    id, name, parent_id
  }
  ```
- **Database:** PostgreSQL (Product-DB)
- **API Contract:**
  ```
  GET    /products              → List products (paginated, filterable)
  GET    /products/{id}         → Get product detail
  GET    /products/search       → Search by name/category
  POST   /products              → Create (vendor/admin only)
  PUT    /products/{id}         → Update
  GET    /products/{id}/stock   → Query stock (internal, calls Inventory)
  ```

### 4. **Inventory Service** (Port 8085)
- **Responsibility:** Stock levels, warehouse management, reservations
- **Owns:** Quantity on hand, reserved quantities, stock movements
- **Data Model:**
  ```
  Inventory {
    product_id (FK)
    quantity_available
    quantity_reserved
    quantity_allocated
    last_updated
  }
  
  StockMovement {
    id, product_id, type (ADD/REMOVE/RESERVE)
    quantity, reason, timestamp
  }
  ```
- **Database:** PostgreSQL (Inventory-DB) + Redis cache for real-time quantities
- **API Contract:**
  ```
  GET    /inventory/{product_id}        → Get stock level
  POST   /inventory/reserve             → Reserve stock (Order Service)
  POST   /inventory/release-reserve     → Release reservation
  POST   /inventory/consume             → Deduct from inventory (after payment)
  ```

### 5. **Order Service** (Port 8083)
- **Responsibility:** Order orchestration, order workflow, status tracking
- **Owns:** Order data, line items, order status progression
- **Orchestrates:** Inventory (reserve), Payment (charge), Notification (send)
- **Data Model:**
  ```
  Order {
    id (UUID)
    user_id (FK)
    status (PENDING→CONFIRMED→PROCESSING→SHIPPED→DELIVERED)
    items [
      { product_id, quantity, unit_price, subtotal }
    ]
    total_amount
    shipping_address
    created_at, updated_at
  }
  
  OrderEvent {
    order_id, event_type, timestamp, details
  }
  ```
- **Database:** PostgreSQL (Order-DB)
- **API Contract:**
  ```
  POST   /orders                        → Create order (user auth required)
  GET    /orders/{id}                   → Get order details
  GET    /orders                        → List user's orders
  PUT    /orders/{id}/cancel            → Cancel order
  POST   /orders/{id}/confirm           → Trigger payment
  ```

### 6. **Payment Service** (Port 8084)
- **Responsibility:** Payment processing, transaction records, refunds
- **Owns:** Payment records, transaction logs, PCI-compliant processing
- **Data Model:**
  ```
  Payment {
    id (UUID)
    order_id (FK)
    user_id (FK)
    amount
    currency
    status (PENDING→AUTHORIZED→CAPTURED→FAILED/REFUNDED)
    method (CARD, PAYPAL, etc)
    gateway_transaction_id
    created_at
  }
  
  PaymentLog {
    payment_id, timestamp, status_change, metadata
  }
  ```
- **Database:** PostgreSQL (Payment-DB)
- **Integrations:** Stripe/PayPal API (external)
- **API Contract:**
  ```
  POST   /payments/authorize             → Authorize amount
  POST   /payments/{id}/capture          → Capture authorized amount
  POST   /payments/{id}/refund           → Issue refund
  GET    /payments/{id}                  → Get payment status (internal)
  ```

### 7. **Notification Service** (Port 8086)
- **Responsibility:** Async notifications, email/SMS dispatch
- **Owns:** Notification templates, delivery records, retries
- **Consumes:** Order.created, Order.shipped, Payment.completed events
- **Data Model:**
  ```
  Notification {
    id, user_id, type, status, content
    created_at, sent_at
  }
  
  NotificationTemplate {
    type (ORDER_CONFIRMATION, SHIPPED, etc)
    subject, body, placeholders
  }
  ```
- **Database:** MongoDB (log-friendly) or PostgreSQL
- **Messaging:** RabbitMQ / Kafka consumer
- **No HTTP API** (async-only, driven by events)

---

## Communication Patterns

### Pattern 1: Synchronous (REST + Spring RestTemplate/WebClient)

**Used for:** Data lookups, validations, immediate responses

```java
// OrderService validates user and checks inventory SYNCHRONOUSLY
@Service
public class OrderService {
  
  @Autowired
  private UserServiceClient userClient;
  
  @Autowired
  private InventoryServiceClient inventoryClient;
  
  public Order createOrder(CreateOrderRequest req) {
    // 1. Validate user exists
    User user = userClient.getUser(req.getUserId());
    if (user == null) throw new UserNotFoundException();
    
    // 2. Reserve inventory for each item
    for (OrderItem item : req.getItems()) {
      boolean reserved = inventoryClient.reserve(
        item.getProductId(), 
        item.getQuantity()
      );
      if (!reserved) {
        throw new InsufficientStockException(item.getProductId());
      }
    }
    
    // 3. Create order
    Order order = new Order(user.getId(), req.getItems());
    return orderRepository.save(order);
  }
}
```

**Service Clients with Circuit Breaker:**

```java
@Configuration
public class ServiceClientsConfig {
  
  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
      .setConnectTimeout(Duration.ofSeconds(2))
      .setReadTimeout(Duration.ofSeconds(5))
      .build();
  }
  
  @Bean
  public UserServiceClient userServiceClient(RestTemplate rest) {
    return new UserServiceClient(rest, "http://user-service:8081");
  }
}

@Service
public class UserServiceClient {
  
  private final RestTemplate rest;
  private final String baseUrl;
  
  @CircuitBreaker(name = "userService", fallbackMethod = "getUserFallback")
  @Retry(name = "userService")
  public User getUser(String userId) {
    return rest.getForObject(
      baseUrl + "/users/" + userId,
      User.class
    );
  }
  
  public User getUserFallback(String userId, Exception ex) {
    log.warn("User service unavailable, using cached fallback", ex);
    // Return cached data or throw exception
    throw new ServiceUnavailableException("User service down");
  }
}
```

**Resilience4j Configuration:**

```yaml
resilience4j:
  circuitbreaker:
    instances:
      userService:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 5s
        failureRateThreshold: 50
        slowCallRateThreshold: 50
        slowCallDurationThreshold: 2s
        
  retry:
    instances:
      userService:
        maxAttempts: 3
        waitDuration: 1000
        retryExceptions:
          - java.net.ConnectException
          - java.io.IOException
```

---

### Pattern 2: Asynchronous (Event-Driven via RabbitMQ/Kafka)

**Used for:** Non-critical notifications, analytics, eventual consistency

**Event Publishing (Order Service):**

```java
@Service
public class OrderService {
  
  @Autowired
  private RabbitTemplate rabbitTemplate;
  
  @Autowired
  private OrderRepository orderRepository;
  
  public Order createOrder(CreateOrderRequest req) {
    // ... validation code ...
    
    Order order = orderRepository.save(new Order(req));
    
    // Publish event (asynchronously handled elsewhere)
    OrderCreatedEvent event = new OrderCreatedEvent(
      order.getId(),
      order.getUserId(),
      order.getItems(),
      order.getTotalAmount()
    );
    
    rabbitTemplate.convertAndSend(
      "order-exchange",
      "order.created",
      event
    );
    
    return order;
  }
  
  public Order confirmOrder(String orderId) {
    Order order = orderRepository.findById(orderId).orElseThrow();
    
    // Request payment asynchronously
    PaymentRequiredEvent paymentEvent = new PaymentRequiredEvent(
      orderId, order.getUserId(), order.getTotalAmount()
    );
    
    rabbitTemplate.convertAndSend(
      "payment-exchange",
      "payment.required",
      paymentEvent
    );
    
    return order;
  }
}
```

**Event Consumption (Notification Service):**

```java
@Service
public class NotificationListener {
  
  @Autowired
  private NotificationService notificationService;
  
  @RabbitListener(queues = "order.created.notification.queue")
  public void onOrderCreated(OrderCreatedEvent event) {
    log.info("Order created event received: {}", event.getOrderId());
    
    Notification notification = new Notification(
      event.getUserId(),
      NotificationType.ORDER_CONFIRMATION,
      "Your order #" + event.getOrderId() + " has been created"
    );
    
    notificationService.send(notification);
  }
  
  @RabbitListener(queues = "order.shipped.notification.queue")
  public void onOrderShipped(OrderShippedEvent event) {
    Notification notification = new Notification(
      event.getUserId(),
      NotificationType.SHIPMENT_NOTIFICATION,
      "Your order has been shipped with tracking: " + event.getTrackingNumber()
    );
    
    notificationService.send(notification);
  }
}
```

**RabbitMQ Configuration:**

```java
@Configuration
public class RabbitMQConfig {
  
  // Exchanges
  public static final String ORDER_EXCHANGE = "order-exchange";
  public static final String PAYMENT_EXCHANGE = "payment-exchange";
  public static final String NOTIFICATION_EXCHANGE = "notification-exchange";
  
  // Queues
  public static final String ORDER_CREATED_QUEUE = "order.created.queue";
  public static final String PAYMENT_REQUIRED_QUEUE = "payment.required.queue";
  public static final String NOTIFICATION_QUEUE = "notification.queue";
  
  // Order Events
  @Bean
  public TopicExchange orderExchange() {
    return new TopicExchange(ORDER_EXCHANGE, true, false);
  }
  
  @Bean
  public Queue orderCreatedQueue() {
    return new Queue(ORDER_CREATED_QUEUE, true);
  }
  
  @Bean
  public Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange orderExchange) {
    return BindingBuilder.bind(orderCreatedQueue)
      .to(orderExchange)
      .with("order.created");
  }
  
  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
```

---

### Pattern 3: Service-to-Service Authentication

**Using JWT Tokens with Service-to-Service Communication:**

```java
@Component
public class ServiceAuthInterceptor implements ClientHttpRequestInterceptor {
  
  @Value("${service.auth.secret:shared-secret-key}")
  private String serviceSecret;
  
  @Override
  public ClientHttpResponse intercept(HttpRequest request, byte[] body, 
                                      ClientHttpRequestExecution execution) {
    // Generate service token (short-lived)
    String token = generateServiceToken();
    request.getHeaders().set("X-Service-Token", token);
    request.getHeaders().set("X-Service-Name", "order-service");
    
    return execution.execute(request, body);
  }
  
  private String generateServiceToken() {
    return Jwts.builder()
      .setSubject("order-service")
      .setIssuedAt(new Date())
      .setExpiration(new Date(System.currentTimeMillis() + 300000)) // 5 min
      .signWith(SignatureAlgorithm.HS512, serviceSecret)
      .compact();
  }
}
```

**Token Validation Filter:**

```java
@Component
public class ServiceAuthFilter implements Filter {
  
  @Value("${service.auth.secret}")
  private String serviceSecret;
  
  private static final List<String> ALLOWED_SERVICES = Arrays.asList(
    "order-service", "payment-service", "inventory-service", "notification-service"
  );
  
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, 
                       FilterChain chain) throws ServletException, IOException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    
    // Skip auth for public endpoints
    if (isPublicEndpoint(httpRequest.getRequestURI())) {
      chain.doFilter(request, response);
      return;
    }
    
    String token = httpRequest.getHeader("X-Service-Token");
    String serviceName = httpRequest.getHeader("X-Service-Name");
    
    if (token == null || !validateToken(token, serviceName)) {
      ((HttpServletResponse) response).sendError(
        HttpServletResponse.SC_UNAUTHORIZED, 
        "Invalid service token"
      );
      return;
    }
    
    chain.doFilter(request, response);
  }
  
  private boolean validateToken(String token, String serviceName) {
    try {
      Claims claims = Jwts.parser()
        .setSigningKey(serviceSecret)
        .parseClaimsJws(token)
        .getBody();
      
      return ALLOWED_SERVICES.contains(serviceName) &&
             claims.getExpiration().after(new Date());
    } catch (JwtException e) {
      return false;
    }
  }
  
  private boolean isPublicEndpoint(String uri) {
    return uri.startsWith("/health") || uri.startsWith("/actuator");
  }
}
```

---

### Pattern 4: Distributed Error Handling & Compensation

**Saga Pattern for Order Processing:**

```java
@Service
public class OrderSagaOrchestrator {
  
  @Autowired
  private OrderRepository orderRepository;
  @Autowired
  private PaymentServiceClient paymentClient;
  @Autowired
  private InventoryServiceClient inventoryClient;
  
  @Transactional
  public void processOrder(Order order) {
    try {
      // Step 1: Reserve inventory
      List<String> reservationIds = inventoryClient.reserveAll(order.getItems());
      order.addReservationIds(reservationIds);
      
      // Step 2: Request payment
      Payment payment = paymentClient.charge(order.getId(), order.getTotalAmount());
      if (!payment.isSuccessful()) {
        // Compensation: Release inventory
        inventoryClient.releaseReservations(reservationIds);
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        return;
      }
      
      // Step 3: Confirm order
      order.setStatus(OrderStatus.CONFIRMED);
      orderRepository.save(order);
      
    } catch (Exception e) {
      // Compensation: Release reserved inventory
      if (order.getReservationIds() != null) {
        inventoryClient.releaseReservations(order.getReservationIds());
      }
      order.setStatus(OrderStatus.FAILED);
      log.error("Order processing failed", e);
    }
  }
}
```

**Compensating Transaction Handler:**

```java
@Service
public class CompensationHandler {
  
  @Autowired
  private InventoryServiceClient inventoryClient;
  
  @Autowired
  private PaymentServiceClient paymentClient;
  
  public void compensateFailedOrder(Order order) {
    // Release all inventory reservations
    try {
      inventoryClient.releaseReservations(order.getReservationIds());
      log.info("Released inventory for order {}", order.getId());
    } catch (Exception e) {
      log.error("Failed to release inventory", e);
      // Alert admin - manual intervention needed
    }
    
    // Refund payment if captured
    if (order.getPayment().isCaptured()) {
      try {
        paymentClient.refund(order.getPayment().getId());
        log.info("Refund issued for order {}", order.getId());
      } catch (Exception e) {
        log.error("Failed to refund payment", e);
        // Alert admin - manual intervention needed
      }
    }
  }
}
```

---

## Data Management Strategies

### Pattern 1: Database Per Service

**Principle:** Each microservice owns its database. No cross-service direct database access.

```
┌─────────────────────────────────────────────────────────┐
│                   Microservices                          │
├──────────────┬──────────────┬──────────────┬────────────┤
│ User Service │Product Service│Order Service│Payment Svc│
└──────┬───────┴──────┬────────┴──────┬───────┴──────┬─────┘
       │              │               │              │
   PostgreSQL    PostgreSQL      PostgreSQL     PostgreSQL
   (user-db)     (product-db)    (order-db)     (payment-db)
```

**Benefits:**
- Services can choose appropriate database (relational, document, key-value)
- Independent scaling and optimization
- Schema changes don't affect other services

**Implementation:**

```yaml
# Order Service - application.yml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/order_service_db
    username: order_user
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQL10Dialect

# Product Service - application.yml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/product_service_db
    username: product_user
    password: ${DB_PASSWORD}
```

---

### Pattern 2: Distributed Transactions (Saga Pattern)

**Problem:** Cannot use 2-phase commit across databases. Use compensating transactions instead.

**Choreography-Based Saga (Event-Driven):**

```
Order Service          Inventory Service       Payment Service
     │                       │                      │
     │─ Order.Created ──────>│                      │
     │                       │─ Inventory.Reserved ─│
     │                       │                      │─ Payment.Charged
     │<──────────────────────────────────────────────│
     │                       │                      │
  [COMMIT]              [COMMIT]               [COMMIT]

OR on failure:

     │─ Order.Created ──────>│                      │
     │                       │─ Inventory.Reserved ─│
     │                       │                      │─ Payment.FAILED ──>
     │<───────────────────── Order.Failed ─────────<│
     │                       │                      │
     │─ Order.Compensation ─>│                      │
     │                       │─ Inventory.Released ─┤
     │                       │                      │
  [ROLLBACK]             [ROLLBACK]
```

**Implementation:**

```java
// Event-based saga using Spring State Machine
@Configuration
@EnableStateMachine
public class OrderSagaStateMachineConfig {
  
  @Bean
  public StateMachineBuilder.Builder<OrderSagaState, OrderSagaEvent> builder(
      StateMachineBuilder.Builder<OrderSagaState, OrderSagaEvent> builder) throws Exception {
    
    builder
      .configureStates()
        .withStates()
        .initial(OrderSagaState.PENDING)
        .states(EnumSet.allOf(OrderSagaState.class))
        .end(OrderSagaState.COMPLETED)
        .end(OrderSagaState.COMPENSATED)
      .and()
      .configureTransitions()
        .withExternal()
          .source(OrderSagaState.PENDING)
          .target(OrderSagaState.INVENTORY_RESERVED)
          .event(OrderSagaEvent.RESERVE_INVENTORY)
        .and()
        .withExternal()
          .source(OrderSagaState.INVENTORY_RESERVED)
          .target(OrderSagaState.PAYMENT_CHARGED)
          .event(OrderSagaEvent.CHARGE_PAYMENT)
        .and()
        .withExternal()
          .source(OrderSagaState.PAYMENT_CHARGED)
          .target(OrderSagaState.COMPLETED)
          .event(OrderSagaEvent.CONFIRM_ORDER);
    
    return builder;
  }
}

enum OrderSagaState {
  PENDING, INVENTORY_RESERVED, PAYMENT_CHARGED, COMPLETED, COMPENSATED
}

enum OrderSagaEvent {
  RESERVE_INVENTORY, CHARGE_PAYMENT, CONFIRM_ORDER, COMPENSATE
}
```

---

### Pattern 3: Data Consistency Strategies

**Eventual Consistency:**
- Use events to propagate state changes
- Accept temporary inconsistency
- Converge to consistency within acceptable timeframe

```java
@Service
public class ProductSyncService {
  
  @RabbitListener(queues = "product-update.queue")
  public void onProductPriceChanged(ProductPriceChangedEvent event) {
    // Product Service updates inventory cache with new price
    Product product = productRepository.findById(event.getProductId())
      .orElseThrow();
    
    product.setPrice(event.getNewPrice());
    product.setPriceUpdatedAt(Instant.now());
    productRepository.save(product);
    
    // Invalidate cache
    cacheManager.getCache("product-cache")
      .evict(event.getProductId());
  }
}
```

**Caching for Read Consistency:**

```java
@Service
@CacheConfig(cacheNames = "products")
public class ProductService {
  
  @Cacheable(key = "#id", unless = "#result == null")
  public Product getProduct(String id) {
    return productRepository.findById(id).orElse(null);
  }
  
  @CacheEvict(key = "#id")
  public Product updateProduct(String id, ProductUpdate update) {
    Product product = productRepository.findById(id).orElseThrow();
    product.setPrice(update.getPrice());
    return productRepository.save(product);
  }
}
```

**Cache Configuration:**

```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10 minutes
      cache-null-values: false
      
  data:
    redis:
      host: redis
      port: 6379
      timeout: 2000ms
```

---

## Build Order & Dependencies

### Critical Path Analysis

```
Phase 0: Infrastructure Setup
├─ Docker Compose configuration
├─ Network setup (bridge network for local development)
└─ Common libraries & utilities

Phase 1: Foundation Services (PARALLEL)
├─ User Service
│  ├─ DB schema (user table)
│  ├─ User model & repository
│  ├─ Authentication endpoints
│  └─ JWT token generation
└─ API Gateway (basic routing)

Phase 2: Data Services (PARALLEL, depends on Phase 1)
├─ Product Service
│  ├─ DB schema
│  ├─ Product catalog endpoints
│  └─ Search/filter endpoints
└─ Inventory Service
   ├─ DB schema
   ├─ Stock tracking
   └─ Reservation logic

Phase 3: Transaction Services (PARALLEL, depends on Phase 1-2)
├─ Order Service
│  ├─ Order creation with validation
│  ├─ Synchronous calls to User/Inventory
│  ├─ Event publishing for async flows
│  └─ Order status tracking
└─ Payment Service
   ├─ Payment processing
   ├─ Refund logic
   └─ Transaction logging

Phase 4: Async Services (depends on Phase 3)
└─ Notification Service
   ├─ Event listeners setup
   ├─ Email template rendering
   └─ Retry mechanism

Phase 5: Integration & Testing
├─ Integration tests across services
├─ Saga compensation tests
├─ Load testing
└─ Documentation
```

### Recommended Build Sequence

**Week 1-2: Foundation**
1. Set up Docker Compose with PostgreSQL and RabbitMQ
2. Implement API Gateway (Spring Cloud Gateway)
3. Implement User Service with JWT authentication
4. Create common libraries (DTO, exceptions, utilities)

**Week 2-3: Data Layer**
5. Implement Product Service with search capabilities
6. Implement Inventory Service with reservation logic
7. Add Redis caching for inventory quantities

**Week 3-4: Transaction Layer**
8. Implement Order Service with saga orchestration
9. Implement Payment Service (with mock payment gateway initially)
10. Wire up async events via RabbitMQ
11. Add circuit breakers and resilience patterns

**Week 4-5: Async & Polish**
12. Implement Notification Service (email/SMS)
13. Distributed tracing (Spring Cloud Sleuth)
14. Comprehensive integration tests
15. Load testing and optimization

### Dependency Graph

```
API Gateway
│
├─ User Service (auth dependency)
│  └─ DB: user_db
│
├─ Product Service
│  ├─ DB: product_db
│  └─ Depends on: User (vendor_id validation)
│
├─ Order Service ★ CRITICAL PATH
│  ├─ DB: order_db
│  ├─ Depends on: User Service (validate user)
│  ├─ Depends on: Product Service (validate products)
│  ├─ Depends on: Inventory Service (reserve stock)
│  ├─ Depends on: Payment Service (charge payment)
│  └─ Publishes: order.created, order.confirmed, order.shipped
│
├─ Inventory Service
│  ├─ DB: inventory_db
│  ├─ Cache: Redis (stock levels)
│  └─ Depends on: Product Service (get product details)
│
├─ Payment Service
│  ├─ DB: payment_db
│  └─ External: Stripe/PayPal API
│
└─ Notification Service
   ├─ DB: notifications_db (optional)
   ├─ Depends on: RabbitMQ (event consumer only)
   ├─ Consumes: order.created, order.shipped, payment.completed
   └─ External: Email/SMS gateway
```

**Order of Internal Service Calls:**
```
User Request → API Gateway
              ↓
        1. User Service (validate user)
        ↓
        2. Product Service (get product details)
        ↓
        3. Inventory Service (check/reserve stock) ← Most likely to fail
        ↓
        4. Payment Service (process payment) ← Critical
        ↓
        5. Order Service (persist order) ← Point of no return
        ↓
        6. RabbitMQ (publish events asynchronously)
        ↓
        7. Notification Service (async consumer)
```

---

## Scalability Considerations

### 1. **Service Independence**

Each service must be independently deployable and scalable:

```yaml
# Docker Compose - independent service scaling
version: '3.8'
services:
  order-service:
    build: ./services/order
    environment:
      - DB_HOST=postgres
      - DB_NAME=order_db
      - RABBITMQ_HOST=rabbitmq
    depends_on:
      - postgres
      - rabbitmq
    networks:
      - ecommerce
    deploy:
      replicas: 1  # Scale independently in production

  product-service:
    build: ./services/product
    environment:
      - DB_HOST=postgres
      - DB_NAME=product_db
      - REDIS_HOST=redis
    depends_on:
      - postgres
      - redis
    networks:
      - ecommerce
    deploy:
      replicas: 1

  # Each service can scale independently
  notification-service:
    build: ./services/notification
    environment:
      - RABBITMQ_HOST=rabbitmq
    depends_on:
      - rabbitmq
    networks:
      - ecommerce
    deploy:
      replicas: 3  # More instances for async processing
```

---

### 2. **Caching Strategy**

**Multi-Level Caching:**

```
Request → API Gateway Cache (10s) → Service Cache (5min) → DB
```

**Implementation:**

```java
@Configuration
public class CachingConfig {
  
  @Bean
  public CacheManager cacheManager(RedisConnectionFactory factory) {
    RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
      .entryTtl(Duration.ofMinutes(5))
      .serializeValuesWith(
        RedisSerializationContext.SerializationPair
          .fromSerializer(new GenericJackson2JsonRedisSerializer())
      );
    
    return RedisCacheManager.create(factory);
  }
}

@Service
public class ProductService {
  
  @Cacheable(value = "products", key = "#id", 
             unless = "#result == null || #result.inactive")
  public Product getProduct(String id) {
    return productRepository.findById(id).orElse(null);
  }
  
  // Cache warming on startup
  @EventListener(ApplicationReadyEvent.class)
  public void warmCache() {
    List<Product> topProducts = productRepository.findTopByPopularity(100);
    topProducts.forEach(p -> cacheTemplate.set("products:" + p.getId(), p));
  }
}
```

---

### 3. **Load Distribution**

**API Gateway Load Balancing:**

```java
@Configuration
public class LoadBalancingConfig {
  
  @Bean
  public ServiceInstanceListSupplier discoveryClientServiceInstanceListSupplier(
      ConfigurableApplicationContext context) {
    return ServiceInstanceListSupplier.builder()
      .withDiscoveryClient()
      .withHealthChecks()
      .build(context);
  }
  
  @Bean
  public LoadBalancerClient loadBalancerClient(
      LoadBalancerClientFactory clientFactory) {
    return new BlockingLoadBalancerClient(clientFactory);
  }
}
```

**Horizontal Scaling Pattern:**

```yaml
# kubernetes/order-service-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 3  # Start with 3 instances
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      containers:
      - name: order-service
        image: order-service:latest
        ports:
        - containerPort: 8083
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        env:
        - name: DB_HOST
          value: postgres-service
        - name: RABBITMQ_HOST
          value: rabbitmq-service
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8083
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8083
          initialDelaySeconds: 10
          periodSeconds: 5
```

---

### 4. **Database Optimization**

**Indexing Strategy:**

```sql
-- Order Service
CREATE INDEX idx_order_user_id ON orders(user_id);
CREATE INDEX idx_order_created_at ON orders(created_at DESC);
CREATE INDEX idx_order_status ON orders(status);

-- Product Service
CREATE INDEX idx_product_category ON products(category_id);
CREATE INDEX idx_product_sku ON products(sku);
CREATE INDEX idx_product_active ON products(active);

-- Inventory Service
CREATE INDEX idx_inventory_product_id ON inventory(product_id);
CREATE INDEX idx_stock_movement_product_created ON stock_movements(product_id, created_at DESC);

-- Payment Service
CREATE INDEX idx_payment_order_id ON payments(order_id);
CREATE INDEX idx_payment_status ON payments(status);
```

**Connection Pooling:**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
```

---

### 5. **Asynchronous Processing**

**RabbitMQ Performance Tuning:**

```java
@Configuration
public class RabbitMQPerformanceConfig {
  
  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(jsonMessageConverter());
    return template;
  }
  
  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
  
  @Bean
  public SimpleMessageListenerContainer messageListenerContainer(
      ConnectionFactory connectionFactory) {
    SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.setConcurrentConsumers(10);  // Parallel processing
    container.setMaxConcurrentConsumers(20);
    container.setPrefetchCount(10);  // Batch processing
    return container;
  }
}
```

**Batch Processing:**

```java
@Service
public class BatchNotificationService {
  
  @Scheduled(fixedDelay = 5000)
  public void processBatchNotifications() {
    List<Notification> pending = notificationRepository.findByStatus(
      NotificationStatus.PENDING,
      PageRequest.of(0, 100)
    );
    
    if (pending.isEmpty()) return;
    
    List<CompletableFuture<Boolean>> futures = pending.stream()
      .map(notification -> CompletableFuture.supplyAsync(
        () -> sendNotification(notification)
      ))
      .collect(Collectors.toList());
    
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .thenAccept(v -> log.info("Batch of {} notifications processed", pending.size()));
  }
}
```

---

## Component Relationships & Data Flow

### Happy Path: Complete Order Flow

```
1. CLIENT REQUEST
   POST /api/orders
   {
     "userId": "user-123",
     "items": [
       { "productId": "prod-456", "quantity": 2 }
     ],
     "shippingAddress": { ... }
   }
   ↓

2. API GATEWAY
   • Validates JWT token from Authorization header
   • Routes to Order Service
   ↓

3. ORDER SERVICE (Create Order)
   A. Validate user exists
      GET http://user-service:8081/users/user-123
      ↓ User Service validates JWT, returns User
   
   B. Validate products & get current pricing
      GET http://product-service:8082/products/prod-456
      ↓ Returns Product with current price
   
   C. Reserve inventory
      POST http://inventory-service:8085/inventory/reserve
      {
        "productId": "prod-456",
        "quantity": 2,
        "orderId": "order-789"
      }
      ↓ If insufficient stock → throw exception, rollback
   
   D. Create Order record (pending)
      INSERT INTO orders (user_id, total_amount, status)
      ↓ status = PENDING
   
   E. Publish event asynchronously
      rabbitTemplate.convertAndSend("order-exchange", "order.created", event)
      ↓

4. PAYMENT SERVICE (async or blocking)
   A. Wait for order confirmation
   B. POST /payments/authorize
      {
        "orderId": "order-789",
        "amount": 299.99,
        "paymentMethod": "card"
      }
   
   C. Call external gateway (Stripe)
      → Stripe authorizes payment
      → Returns transaction_id
   
   D. Update Payment record
      status = AUTHORIZED
   
   E. Publish event: payment.authorized
      ↓

5. ORDER SERVICE (Confirm Order)
   A. Listen for payment.authorized event
   B. Update order status → CONFIRMED
   C. Publish event: order.confirmed
      ↓

6. INVENTORY SERVICE (Confirm Reservation)
   A. Listen for order.confirmed
   B. Convert reservation → allocation
   C. Reduce quantity_available by reserved amount
   D. Update stock_movements log
      ↓

7. NOTIFICATION SERVICE (async)
   A. Listen for order.created event
      → Send ORDER_CONFIRMATION email
   
   B. Listen for payment.captured event
      → Send PAYMENT_RECEIVED email
   
   C. Listen for order.shipped event
      → Send SHIPMENT_NOTIFICATION with tracking
   ↓

8. RESPONSE TO CLIENT
   HTTP 201 Created
   {
     "orderId": "order-789",
     "status": "PENDING",
     "totalAmount": 299.99,
     "items": [...]
   }
```

### Error Flow: Payment Failure

```
ORDER SERVICE
├─ Create Order (PENDING)
├─ Publish order.created
└─ Call PAYMENT SERVICE

PAYMENT SERVICE
├─ Authorize payment → FAILED
└─ Publish payment.failed

ORDER SERVICE (Saga Compensation)
├─ Listen for payment.failed
├─ Publish order.compensation_needed
└─ Update order status → PAYMENT_FAILED

INVENTORY SERVICE
├─ Listen for order.compensation_needed
├─ Release reservation
└─ Restore quantity_available

NOTIFICATION SERVICE
├─ Listen for order.failed
└─ Send PAYMENT_FAILED email with retry option

CLIENT
└─ Receives 402 Payment Required or retry prompt
```

---

## Deployment Architecture

### Docker Compose (Local Development)

```yaml
# docker-compose.yml
version: '3.8'

services:
  # Data layer
  postgres:
    image: postgres:14-alpine
    environment:
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./scripts/init-dbs.sql:/docker-entrypoint-initdb.d/init.sql
    networks:
      - ecommerce

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    networks:
      - ecommerce

  rabbitmq:
    image: rabbitmq:3.11-management-alpine
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    ports:
      - "5672:5672"
      - "15672:15672"  # Management UI
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    networks:
      - ecommerce

  # Microservices
  api-gateway:
    build: ./services/gateway
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
    depends_on:
      - user-service
      - product-service
      - order-service
    networks:
      - ecommerce

  user-service:
    build: ./services/user
    ports:
      - "8081:8081"
    environment:
      - DB_HOST=postgres
      - DB_NAME=user_db
      - REDIS_HOST=redis
    depends_on:
      - postgres
      - redis
    networks:
      - ecommerce

  product-service:
    build: ./services/product
    ports:
      - "8082:8082"
    environment:
      - DB_HOST=postgres
      - DB_NAME=product_db
      - REDIS_HOST=redis
    depends_on:
      - postgres
      - redis
    networks:
      - ecommerce

  order-service:
    build: ./services/order
    ports:
      - "8083:8083"
    environment:
      - DB_HOST=postgres
      - DB_NAME=order_db
      - RABBITMQ_HOST=rabbitmq
      - USER_SERVICE_URL=http://user-service:8081
      - PRODUCT_SERVICE_URL=http://product-service:8082
      - INVENTORY_SERVICE_URL=http://inventory-service:8085
      - PAYMENT_SERVICE_URL=http://payment-service:8084
    depends_on:
      - postgres
      - rabbitmq
    networks:
      - ecommerce

  inventory-service:
    build: ./services/inventory
    ports:
      - "8085:8085"
    environment:
      - DB_HOST=postgres
      - DB_NAME=inventory_db
      - REDIS_HOST=redis
    depends_on:
      - postgres
      - redis
    networks:
      - ecommerce

  payment-service:
    build: ./services/payment
    ports:
      - "8084:8084"
    environment:
      - DB_HOST=postgres
      - DB_NAME=payment_db
      - STRIPE_API_KEY=${STRIPE_API_KEY}
    depends_on:
      - postgres
    networks:
      - ecommerce

  notification-service:
    build: ./services/notification
    ports:
      - "8086:8086"
    environment:
      - RABBITMQ_HOST=rabbitmq
      - SMTP_HOST=${SMTP_HOST}
      - SMTP_PASSWORD=${SMTP_PASSWORD}
    depends_on:
      - rabbitmq
    networks:
      - ecommerce

volumes:
  postgres_data:
  redis_data:
  rabbitmq_data:

networks:
  ecommerce:
    driver: bridge
```

### Production Deployment (Kubernetes)

```yaml
# kubernetes/namespace.yml
apiVersion: v1
kind: Namespace
metadata:
  name: ecommerce

---
# kubernetes/postgres-statefulset.yml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: ecommerce
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:14-alpine
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: password
        volumeMounts:
        - name: postgres-storage
          mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
  - metadata:
      name: postgres-storage
    spec:
      accessModes: [ "ReadWriteOnce" ]
      resources:
        requests:
          storage: 10Gi

---
# kubernetes/order-service-deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: ecommerce
spec:
  replicas: 3
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      containers:
      - name: order-service
        image: order-service:1.0
        ports:
        - containerPort: 8083
        env:
        - name: DB_HOST
          value: postgres.ecommerce.svc.cluster.local
        - name: DB_NAME
          value: order_db
        - name: RABBITMQ_HOST
          value: rabbitmq.ecommerce.svc.cluster.local
        - name: USER_SERVICE_URL
          value: http://user-service:8081
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8083
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8083
          initialDelaySeconds: 10
          periodSeconds: 5
---
# kubernetes/order-service-service.yml
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: ecommerce
spec:
  selector:
    app: order-service
  ports:
  - protocol: TCP
    port: 8083
    targetPort: 8083
  type: ClusterIP
```

---

## Security Considerations

### 1. **Inter-Service Communication**

```java
// Service-to-service mutual TLS
@Configuration
public class MutualTlsConfig {
  
  @Bean
  public RestTemplate restTemplate(
      RestTemplateBuilder builder,
      @Value("${client.ssl.key-store}") String keyStore,
      @Value("${client.ssl.key-store-password}") String keyStorePassword) {
    
    return builder
      .requestFactory(() -> {
        HttpComponentsClientHttpRequestFactory factory = 
          new HttpComponentsClientHttpRequestFactory();
        factory.setHttpClient(HttpClients.custom()
          .setSSLContext(sslContext(keyStore, keyStorePassword))
          .build());
        return factory;
      })
      .build();
  }
  
  private SSLContext sslContext(String keyStore, String password) {
    // Implementation of mTLS context
  }
}
```

### 2. **Data Encryption**

```yaml
# Encryption at rest for sensitive data
spring:
  jpa:
    hibernate:
      jdbc:
        batch_size: 20
    properties:
      hibernate:
        format_sql: true

# Encrypt PII in payments database
@Entity
@Table(name = "payments")
public class Payment {
  
  @Id
  private String id;
  
  @Column(columnDefinition = "BYTEA")
  @Convert(converter = EncryptedStringConverter.class)
  private String cardNumber;  // Encrypted at rest
  
  @Column(columnDefinition = "BYTEA")
  @Convert(converter = EncryptedStringConverter.class)
  private String cvv;  // Encrypted at rest
}
```

---

## Summary

This architecture enables:

✅ **Independent Scaling** - Each service scales based on demand  
✅ **Technology Choice** - Services choose appropriate databases/frameworks  
✅ **Resilience** - Circuit breakers, retries, fallbacks  
✅ **Asynchronous Processing** - Events decouple services  
✅ **Distributed Transactions** - Saga pattern for consistency  
✅ **Easy Testing** - Services testable in isolation  
✅ **Clear Ownership** - Each team owns a service boundary  

**Key Challenges & Mitigations:**

| Challenge | Pattern |
|-----------|---------|
| Eventual consistency | Event-driven, caching, idempotency |
| Distributed debugging | Distributed tracing, centralized logging |
| Data synchronization | Sagas, compensating transactions |
| Service discovery | Kubernetes service discovery or Consul |
| Configuration management | ConfigServer, environment variables |
| API versioning | URL versioning, content negotiation |
| Testing complexity | Contract testing, integration test suites |

This architecture is production-ready and scales from development (Docker Compose) to enterprise (Kubernetes) deployments.

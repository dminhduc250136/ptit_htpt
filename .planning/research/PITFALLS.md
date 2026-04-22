# Domain Pitfalls: Microservices E-Commerce Systems

**Domain:** Microservices-based e-commerce platform  
**Stack:** Spring Boot (backend) + Next.js (frontend)  
**Team Context:** Students learning architecture patterns  
**Researched:** April 2026  

---

## Executive Summary

Microservices e-commerce systems are prone to cascading failures, distributed transaction complexity, and observability gaps that can go undetected until production. This document identifies the critical pitfalls that derail e-commerce microservices and provides concrete detection strategies and prevention techniques for each development phase.

---

## ARCHITECTURAL PITFALLS

### Pitfall 1: Over-Engineering with Unnecessary Services

**What goes wrong:**  
Services are created prematurely to achieve microservices "look" rather than solving real problem boundaries. Results in dozens of tiny services with heavy inter-service coupling—effectively a distributed monolith with network calls.

**Why it happens:**
- Team enthusiasm for microservices without understanding domain boundaries
- Following deployment architecture (services per team) before business capability decomposition
- Treating each code file or layer as a service candidate
- No clear service separation criteria established

**Consequences:**
- Increased network latency and failure points
- Complex service mesh configuration needed
- Harder to trace bugs across services
- Operational overhead explodes (20 services = 20 deployment pipelines, 20 configurations, 20 monitoring systems)
- Team context-switching across many codebases

**Warning signs (detect early):**
- Service has only 1-2 endpoints or 1-2 domain concepts
- Services are stateless pass-throughs (no real business logic)
- More time spent on inter-service communication than business logic
- Service deployment doesn't correspond to team structure or business capabilities
- Hard to explain what one service is "responsible for" in business terms

**Prevention:**
- **Design phase:** Decompose around business capabilities first (Order Management, Inventory, Payments), not technical layers
- Use bounded contexts from Domain-Driven Design to define service boundaries
- Start with 3-4 core services minimum; expand only when service responsibility grows beyond team capacity or change frequency diverges
- Document each service's domain, responsibilities, and why it exists
- Use [microservices.io decomposition patterns](https://microservices.io/patterns/decomposition/decompose-by-business-capability.html)
- Rule: Each service should be independently deployable and handle a complete business capability

**Which phase to address:**
- **Architecture phase (MVP):** Critical—decide on core services before building
- Test decomposition with domain experts before implementation
- Refactor if wrong, but catch it early (month 1-2, not month 6)

---

### Pitfall 2: Tight Coupling Between Services

**What goes wrong:**  
Services depend on internal implementation details of other services (hardcoded URLs, brittle request payloads, synchronous cascading calls). Changing one service breaks others.

**Why it happens:**
- Direct HTTP calls without abstraction (REST endpoints tightly coupled to internal domain models)
- Shared libraries that expose internal structures across service boundaries
- No versioning strategy or backward compatibility thinking
- Request objects leaked from one service to another

**Consequences:**
- Cannot deploy services independently (one change cascades)
- Service updates become coordination events across teams
- Debugging becomes chain tracing (A calls B calls C calls D)
- Difficult to scale individual services without changing contracts
- One service failure affects all consumers

**Warning signs:**
- Service A imports or directly references Service B's domain objects
- Changing a service's internal SQL/ORM model breaks clients
- Request payloads include fields that aren't needed but are included "just in case"
- Services use each other's internal DTOs directly
- API contracts aren't versioned; new fields break existing clients

**Prevention:**
- **Define service contracts explicitly:** Use OpenAPI/Swagger for REST APIs; define event schemas for async communication
- **Implement Tolerant Reader pattern:** Services accept and ignore unknown fields; only require essential data
- **Version APIs:** Include version in endpoint URL (`/v1/orders`, `/v2/orders`) or accept version header
- **Use external DTOs for contracts:** Never leak service-internal models across boundaries
- **Async first for cross-service communication:** Use events/messages instead of synchronous REST calls where possible
- **Document breaking changes:** Communicate before deploying breaking API changes
- Reference: [12-factor app #4: Treat backing services as attached resources](https://12factor.net/backing-services)

**Which phase to address:**
- **API Design phase:** Define stable service contracts with versioning BEFORE implementation
- **Every deployment:** Check for backward compatibility before deploying

---

### Pitfall 3: Shared Database Across Services

**What goes wrong:**  
Multiple services read/write the same database—creates hidden coupling and distributed transaction problems. Changes to schema affect all consumers; transaction isolation becomes nightmare.

**Why it happens:**
- "Reuse" mindset from monolith development
- Ease of sharing data in SQL joins
- Unclear service boundaries (service A and B both own "customer" data)
- Performance optimization (avoiding service calls by direct DB access)

**Consequences:**
- Cannot scale individual services independently (database becomes bottleneck)
- Schema changes require coordination across teams
- Circular dependencies (service A modifies schema; service B breaks)
- Distributed transaction complexity (2PC doesn't work in microservices)
- Data integrity violations go undetected until production

**Warning signs:**
- Multiple services connect to same database with same credentials
- Services JOIN data from tables owned by other services
- Schema changes documented as "notify all teams"
- Transaction logs show cross-service database locks
- Different services own different tables in same database (no logical separation)

**Prevention:**
- **Enforce database per service:** Each service owns and manages its database exclusively
- When service B needs data from service A, service B calls service A's API—does NOT query service A's database
- **Implement Saga pattern for cross-service transactions:** Orchestration or choreography-based sagas instead of distributed 2PC
- Use [Saga pattern](https://microservices.io/patterns/data/saga.html) with either:
  - **Choreography:** Services publish events (Order Created → Inventory Service listens → reserves stock)
  - **Orchestration:** Saga coordinator orchestrates steps (CreateOrderSaga directs steps)
- For data replication needs, use event-driven sync (service A publishes events; service B subscribes and maintains read replica)
- Reference: [Database per Service pattern](https://microservices.io/patterns/data/database-per-service.html)

**Which phase to address:**
- **Data modeling phase:** Decide database boundaries with service boundaries
- **Every service addition:** Review and enforce database ownership

---

### Pitfall 4: Missing Circuit Breakers and Timeouts

**What goes wrong:**  
Service A calls Service B; Service B is slow or down. Service A threads wait forever (or very long). Threads exhaust; Service A becomes unresponsive to all requests. Failure cascades upstream.

**Why it happens:**
- "It's internal; we trust the network" (network is unreliable)
- No timeout configured on HTTP clients
- No fallback logic for failed calls
- Synchronous service calls without resilience patterns
- Testing only happy path (service B is always up)

**Consequences:**
- One slow service brings down entire system
- Resource exhaustion (thread pools empty)
- Requests pile up in queue; eventual crash
- Recovery is slow (manual restart needed)
- Cascading failures across entire platform

**Warning signs:**
- Service response times spike unexpectedly
- Thread pool logs show "Waiting for response from Service B"
- Memory usage climbs during peak load (requests accumulating)
- One service outage takes down multiple dependent services
- No timeout exceptions in logs; just hangs

**Prevention:**
- **Implement Circuit Breaker pattern:** Use Netflix Hystrix, Resilience4j, or Spring Cloud CircuitBreaker
  - If failure rate exceeds threshold (e.g., 50% failed requests), circuit "trips"
  - Subsequent requests fail immediately (fail-fast) instead of waiting
  - After timeout period, allow test requests; if successful, reset circuit
- **Set explicit timeouts:** All HTTP calls must have connection timeout + read timeout
  - Example: 5s connection timeout, 10s read timeout
  - Prevent threads from hanging indefinitely
- **Implement bulkheads:** Separate thread pools for different services
  - Service A calls Service B and Service C; don't use shared thread pool
  - If Service B exhausts its pool, doesn't affect Service C calls
- **Provide fallbacks:** For circuit-open state, return cached data or sensible default
  - Order service can't reach Inventory service? Return "inventory check pending" instead of failing order creation
- **Test failure scenarios:** Chaos engineering—deliberately break services to verify resilience
- Reference: [Circuit Breaker pattern](https://microservices.io/patterns/reliability/circuit-breaker.html)

**Which phase to address:**
- **Service communication phase:** Configure circuit breakers before first inter-service calls
- **Load testing:** Verify behavior under failure before production

---

### Pitfall 5: N+1 Query Problems in Service Calls

**What goes wrong:**  
Service A needs data from Service B for multiple records. Instead of batch call, loops and calls Service B once per record. Client: N records → N service calls. Linear degradation of performance.

**Why it happens:**
- Treating service calls like in-process function calls (they're not—network is expensive)
- No batch query API on downstream service
- Tight loop: `for (order in orders) { customer = getCustomer(order.customerId); }`
- Misunderstanding that service calls have ~100x latency vs in-process calls

**Consequences:**
- Service performance collapses with 100+ orders (100 service calls + network overhead)
- Upstream service becomes bottleneck on downstream service
- Cascading latency: if Order service does N calls to Customer service, and Customer service does M calls to Inventory service, total is N×M calls
- Timeouts trigger; requests fail

**Warning signs:**
- Service call count doesn't match record count (should be roughly 1:1, not N:1)
- Latency per request scales linearly with number of related records
- Logs show same downstream service called repeatedly in short time
- Load testing with 100 records shows 10× latency vs 10 records

**Prevention:**
- **Batch queries:** Provide batch endpoints for related data
  - `GET /customers/{ids}` instead of looping `GET /customers/{id}`
  - Return all customers in single call
- **API Composition pattern:** Aggregate data at API Gateway or BFF layer
  - API Gateway fetches Order list, then single batch call to Customer service for all customer data
  - Single network roundtrip instead of N roundtrips
- **Event-driven data sync:** If same data queried repeatedly, sync it to local read replica
  - Order service maintains local cache of customer names (updated via Customer domain events)
  - No service calls needed for read-only customer data
- **Denormalization:** Duplicate non-frequently-changing data in calling service's database
  - Order record includes customer name (denormalized)
  - When customer name changes, event triggers update in Order service
- **GraphQL or similar:** For flexible query patterns, use GraphQL to prevent over-fetching/under-fetching

**Which phase to address:**
- **Query optimization phase:** Profile API calls before performance testing
- **Load testing:** Identify N+1 problems early with realistic data volumes

---

## DATA & CONSISTENCY PITFALLS

### Pitfall 6: Distributed Transaction Complexity (2PC Attempts)

**What goes wrong:**  
Team attempts distributed transactions using 2PC (two-phase commit) across multiple services. 2PC is blocking, slow, and unreliable in distributed systems. Creates deadlocks, poor performance, high failure rates.

**Why it happens:**
- Monolith mindset: "ACID transactions worked before; use 2PC"
- Misunderstanding distributed systems: network failures, partial failures aren't rare
- Pressure for immediate consistency (must return success immediately)
- Lack of awareness of Saga pattern alternative

**Consequences:**
- 2PC blocks all participants while coordinator makes decision; system becomes unresponsive
- Network partition = deadlock (participants waiting for coordinator decision)
- Failure rates increase significantly (more participants = more failure points)
- Rollback is complex and slow
- System violates CAP theorem and loses availability

**Warning signs:**
- Database logs show long-held locks or XA protocol transactions
- Order creation takes 5+ seconds (blocked on 2PC)
- Timeouts increase during load testing
- "Distributed transaction timeout" errors in logs
- Multiple services with shared transaction coordinator

**Prevention:**
- **Use Saga pattern instead:** Sagas are sequences of local transactions, not distributed 2PC
  - Each service performs local transaction + publishes event
  - Next service listens to event and performs its transaction
  - If step fails, saga executes compensating transactions (undo previous steps)
- **Two approaches:**
  - **Choreography:** Services publish events; subscribers react autonomously (simpler, harder to visualize)
  - **Orchestration:** Saga orchestrator coordinates steps explicitly (centralized, easier to understand flow)
- **Eventual consistency:** Accept that consistency takes time; design for eventual convergence
- **Idempotency:** Ensure operations can be repeated without side effects (safe for retries)
- Reference: [Saga pattern](https://microservices.io/patterns/data/saga.html), Martin Fowler on [distributed transactions](https://martinfowler.com/articles/microservices.html#APIgatewaysandatamanagement)

**Which phase to address:**
- **Transaction design phase:** Choose Saga over 2PC during requirements
- **Proof of concept:** Build sample saga flow before implementation

---

### Pitfall 7: Eventual Consistency Confusion

**What goes wrong:**  
Team doesn't understand eventual consistency. Implements saga correctly but fails to handle stale reads, dirty reads, or anomalies that appear before consistency arrives. Users see conflicting data.

**Why it happens:**
- Assumption: "After saga completes, all data is consistent" (false—intermediate states exist)
- No isolation level specified for reads (read-uncommitted races)
- Cache invalidation not implemented
- UI assumes reads reflect writes immediately

**Consequences:**
- User creates order; sees "order not found" briefly (inventory sync not yet complete)
- Order shows status "pending" then "confirmed" then "pending" again (race between saga steps)
- Payment service approves; Order service hasn't received event yet; user sees "payment pending"
- Double-charging or overselling (inconsistent reads during saga execution)

**Warning signs:**
- Users report state inconsistencies: "Order was confirmed then disappeared"
- Race condition bugs that only appear under load
- Cache invalidation race conditions (UI shows stale state)
- Timing-dependent test failures (same test fails sometimes, passes others)
- Events arriving out of order or duplicated

**Prevention:**
- **Understand isolation anomalies:** Recognize dirty reads, non-repeatable reads, phantom reads
  - Design sagas to tolerate these anomalies
  - Use compensating transactions to handle conflicting concurrent sagas
- **Version data:** Include version/timestamp in records
  - Order has version=1; saga increments to version=2
  - Optimistic locking prevents stale reads
- **Separate reads from writes:** Use CQRS for frequently-read data
  - Write service handles saga mutations
  - Read service maintains eventual-consistency read model
  - UI queries read model (accepts staleness window of seconds)
- **Assume events are duplicated/reordered:**
  - Make operations idempotent (same event processed twice = same result)
  - Handle out-of-order events gracefully
- **Communicate staleness to users:** "Order confirmed. Inventory sync in progress (usually <2s)"
- **Set staleness budget:** Define max age of data acceptable (e.g., 5s for inventory, 30s for analytics)

**Which phase to address:**
- **Data consistency phase:** Define consistency requirements before saga implementation
- **Testing:** Test with delayed/reordered events to catch anomalies

---

### Pitfall 8: Data Synchronization Between Services (Event Failure)

**What goes wrong:**  
Event publishing fails silently or events are lost. Service A publishes "Order Created" event; Service B never receives it. Inventory never decreases; Customer service never receives notification. Silent data divergence.

**Why it happens:**
- Event published to message broker, but message broker crashes before delivery
- Transactional outbox pattern not implemented
- Event delivery not idempotent; duplicate events processed as separate transactions
- Message broker failure not monitored
- No dead-letter queue for unprocessable events

**Consequences:**
- Inventory oversells (order created, but inventory not decremented)
- Customer sees order but no payment received
- Subtle data corruption (hard to detect, easy to miss)
- Silent failures (no exception thrown, but operation incomplete)
- Eventual consistency never arrives (data permanently diverged)

**Warning signs:**
- Order count != Payment count (unmatched orders)
- Inventory numbers inconsistent across services
- Event processing exceptions silently logged but not handled
- Dead-letter queue accumulating messages
- Gap between "Order Created" events and "Customer Notified" events

**Prevention:**
- **Use Transactional Outbox pattern:**
  - Persist domain event in same database transaction as mutation
  - Separate process polls table and publishes events to message broker
  - Ensures write and publish are atomic; no loss
- **Use Event Sourcing:** Store events as primary source of truth
  - Replay events to reconstruct state
  - No "sync" needed; all derived state computed from events
- **Idempotent event processing:** Same event processed twice = same result
  - Use event ID + service ID as dedup key
  - Idempotency token in requests
- **Dead-letter queues:** Failed events go to DLQ for manual review
- **Monitoring:** Alert on event lag between services (publish time vs. consumption time)
- Reference: [Transactional Outbox pattern](https://microservices.io/patterns/data/transactional-outbox.html), [Event Sourcing](https://microservices.io/patterns/data/event-sourcing.html)

**Which phase to address:**
- **Event architecture phase:** Decide on outbox or event sourcing before event implementation
- **Testing:** Test message broker failures; verify recovery

---

### Pitfall 9: Cascading Failures and Timeout Storms

**What goes wrong:**  
Service A times out waiting for Service B. Service A retries (exponential backoff not configured). Retry storms hit Service B. Service B overloaded, times out to Service C. Cascade continues; entire system collapses.

**Why it happens:**
- No exponential backoff; immediate retries
- No retry limit; retries forever
- No bulkhead isolation; shared thread pool
- Service B overload not detected; requests keep queuing

**Consequences:**
- Entire system becomes unresponsive
- Recovery takes minutes (manual intervention needed)
- Error rates spike to 100%
- Resource exhaustion (memory, file descriptors)
- Cascading outage across entire platform

**Warning signs:**
- Request latency spikes suddenly from 200ms to 5000ms+
- Error rate jumps from 0% to 50%+ in seconds
- "Too many open files" or memory exhaustion errors
- Logs show rapid retry loops
- CPU spikes as system retries failures

**Prevention:**
- **Exponential backoff with jitter:**
  ```
  delay = min(baseDelay * 2^attempts + random(0, 1000ms), maxDelay)
  ```
  - First retry: 100ms, then 200ms, 400ms, 800ms, 1.6s, max 30s
  - Random jitter prevents thundering herd
- **Circuit breaker:** Stop retrying when service is down
  - After N consecutive failures, fail-fast instead of retrying
  - Gives service time to recover
- **Bulkheads:** Separate thread pools per service
  - Service A→B calls use threadpool-AB (10 threads)
  - Service A→C calls use threadpool-AC (10 threads)
  - If B is down, C is unaffected
- **Deadline propagation:** Timeouts flow through call chain
  - Client has 30s deadline
  - Service A calls B with 25s deadline (leave margin)
  - Service B calls C with 20s deadline
  - Prevents waste on doomed requests
- **Graceful degradation:** Return cached/default response instead of failing
- Reference: [Microservices: Design for failure](https://martinfowler.com/articles/microservices.html#DesignForFailure)

**Which phase to address:**
- **Communication configuration:** Implement exponential backoff, circuit breaker, bulkheads in all clients
- **Load testing:** Trigger failures; verify cascade prevention

---

## SECURITY PITFALLS

### Pitfall 10: Service-to-Service Authentication Gaps

**What goes wrong:**  
Service A calls Service B without authentication. Any service (or external attacker) can impersonate Service A, access Service B data, perform operations as Service A.

**Why it happens:**
- "Internal network; must be safe" (false—insider threats, compromised containers)
- Assuming network firewall is sufficient (it's not—lateral movement)
- Overlooking service discovery: how does Service B know Service A is legitimate?
- No mutual TLS or token validation implemented

**Consequences:**
- Lateral movement: Attacker compromises one service; gains access to all
- Service impersonation: Attacker calls Inventory service as Order service; deletes stock
- Data breach: All service data accessible via unguarded APIs
- Compliance violation: No audit trail of who called whom

**Warning signs:**
- Service-to-service API calls have no authorization header
- Service B accepts requests without verifying caller identity
- Network traffic unencrypted (HTTP instead of HTTPS)
- No service-to-service token or certificate validation
- Service discovery returns all services accessible to all other services

**Prevention:**
- **Mutual TLS (mTLS):** Each service has certificate; validates peer certificates
  - Service mesh (Istio, Linkerd) handles mTLS automatically
  - Or manual: configure TLS on all service endpoints
- **Service-to-service tokens:** API Gateway issues signed JWT token to calling service
  - Token includes caller service identity + scopes
  - Calling service includes token in requests to other services
  - Receiving service validates signature + checks caller identity
- **Service identity verification:** Service registry authenticates service registration
  - Only services with valid certificate can register
  - Service discovery only returns services to authenticated callers
- **Zero-trust networking:** Assume internal network is compromised
  - Encrypt all traffic (mTLS)
  - Authenticate all requests (service-to-service tokens)
  - Authorize all requests (service B checks if service A has permission)
- Reference: [Access Token pattern](https://microservices.io/patterns/security/access-token.html), [12-factor: Backing services](https://12factor.net/backing-services)

**Which phase to address:**
- **Architecture phase:** Design service identity and authentication before building
- **First service-to-service call:** Implement mTLS or token validation immediately

---

### Pitfall 11: API Gateway Security Misconfiguration

**What goes wrong:**  
API Gateway is single entry point but security misconfigured. Gateway doesn't validate JWTs correctly, allows invalid tokens, doesn't enforce HTTPS, doesn't rate-limit. Attackers bypass all downstream security.

**Why it happens:**
- Assumption: "API Gateway is just routing; security is downstream"
- JWT validation not enabled or configured incorrectly (wrong secret, no expiry check)
- HTTPS not enforced (HTTP traffic logged with tokens)
- Rate limiting disabled or too permissive
- CORS misconfigured (allows requests from anywhere)

**Consequences:**
- Attackers bypass authentication entirely
- Brute force attacks on login endpoints (no rate limiting)
- Token expiry not enforced; stolen old tokens still work
- HTTPS downgrade attacks (tokens in plaintext)
- CSRF attacks (CORS allows cross-site requests)

**Warning signs:**
- API Gateway accepts invalid or expired JWTs
- HTTP traffic (not HTTPS) observed on API Gateway
- Same IP address making 1000s of requests (no rate limiting)
- CORS allows `*` (all origins)
- JWT secret hardcoded or default value used

**Prevention:**
- **JWT validation:** Gateway must validate:
  - Signature (using published secret/public key)
  - Expiry (token must not be expired)
  - Issuer (token issued by trusted auth service)
  - Audience (token for this service, not another)
  - Configure leeway for clock skew (30s max)
- **Enforce HTTPS:** No HTTP allowed; redirect HTTP→HTTPS
- **Rate limiting per IP:** Prevent brute force
  - 10 failed login attempts → IP blocked for 15 minutes
  - 100 requests/minute per IP → 429 Too Many Requests
- **CORS restrictions:** Only allow requests from known frontend domains
  - Not `*` (all origins)
  - Explicit whitelist of allowed origins
- **Secure token handling:**
  - Tokens in Authorization header (not URL query params)
  - HttpOnly cookies for web browsers (prevents XSS access)
  - No tokens in logs or error messages
- **Health check bypass:** Some endpoints (e.g., `/health`) bypass security (needed for load balancer)
  - Explicitly allow health checks without auth
  - Don't expose sensitive endpoints without auth

**Which phase to address:**
- **API Gateway setup:** Configure authentication + HTTPS before first deployment
- **Security review:** Manual audit of gateway configuration

---

### Pitfall 12: Token/Session Management Chaos

**What goes wrong:**  
Tokens/sessions managed inconsistently across services. One service validates JWT properly; another trusts any token with correct format. Session token format differs per service. Logout doesn't invalidate tokens everywhere.

**Why it happens:**
- Each service team implements security independently
- No shared library or standard for token validation
- JWT lifetime set inconsistently (1 hour vs 7 days)
- Logout clears local session but doesn't revoke JWT
- Refresh token mechanism not implemented

**Consequences:**
- User logs out; old JWT still valid for 7 days (service B trusts JWT, doesn't check revocation)
- One service requires login every hour; another has 7-day session (inconsistent UX)
- Token format incompatible between services (one expects JTI claim, another doesn't)
- Compromised token remains valid indefinitely (no revocation mechanism)
- Cross-service session handling broken

**Warning signs:**
- Different token expiry times across services
- User logout doesn't sign out all services
- Stolen token remains valid long after discovery
- Session format varies between services
- No refresh token implementation (long-lived tokens for security)

**Prevention:**
- **Centralized auth service:** Single service issues all tokens, validates all tokens
  - All services call auth service to validate token (or use cached public key)
  - Token format standardized (specific claims required)
  - Single source of truth for token lifetime
- **Standard claims in JWT:**
  - `sub` (subject): user ID
  - `exp` (expiry): absolute timestamp
  - `iat` (issued at): token creation time
  - `jti` (JWT ID): unique token ID for revocation
  - All services validate these identically
- **Short-lived access tokens + refresh tokens:**
  - Access token: 15 minutes (short; limited exposure if stolen)
  - Refresh token: 7 days (long-lived; used to get new access token)
  - On logout, both tokens invalidated
  - Refresh token stored server-side; revoked immediately
- **Token revocation:**
  - Store invalidated token JTI in cache (Redis) with expiry = token exp time
  - Before accepting token, check if JTI in revocation cache
  - Logout adds JTI to cache
- **Use libraries:** Don't implement JWT validation manually; use proven libraries
  - Spring Security with OAuth2 / OIDC
  - Auth0, Keycloak for centralized auth
- Reference: [JWT best practices](https://tools.ietf.org/html/rfc8949), OWASP

**Which phase to address:**
- **Authentication design:** Choose centralized auth + token strategy before building
- **First login implementation:** Get token management right from start

---

### Pitfall 13: Input Validation Inconsistencies

**What goes wrong:**  
Services validate input inconsistently. Service A checks email format; Service B doesn't. Service A rejects negative quantities; Service B doesn't. Leads to data corruption and security exploits.

**Why it happens:**
- No shared validation library or specification
- Each service team validates independently
- Validation logic lives in multiple places (client, API, database)
- Client-side validation assumed sufficient (it's not—can be bypassed)

**Consequences:**
- SQL injection (Service B doesn't validate input; passes to SQL)
- XSS attacks (Service B doesn't sanitize output; user data rendered unsanitized)
- Data corruption (negative quantities, invalid emails stored)
- Business logic errors (Service A expects email; gets empty string)
- Security bypasses (email validation bypassed on one service)

**Warning signs:**
- Validation error messages vary between services
- Database contains invalid data (negative quantities, malformed emails)
- Security scan reports injection vulnerabilities
- Client-side validation only; server-side missing
- No unit tests for validation logic

**Prevention:**
- **Server-side validation only:** Client validation is UX improvement, not security
  - Always validate on server before persistence/use
  - Never trust client input
- **Shared validation library:** All services use same validation rules
  - Create validation library (e.g., `ptit-validators`) with common rules
    - Email format
    - Quantity ≥ 0
    - Name length 1-255
    - etc.
  - All services import and use shared validators
- **Input sanitization:** Remove/escape dangerous characters
  - SQL: Use parameterized queries (never string concatenation)
  - HTML: Escape HTML entities in user data
  - URLs: Encode special characters
- **Explicit whitelist:** Only allow known-good characters
  - Email: alphanumeric + special chars `+.-_@`
  - Quantity: digits only
  - Name: letters, spaces, hyphens
- **Database schema validation:** Add constraints to database
  - NOT NULL, CHECK (quantity > 0), UNIQUE, FOREIGN KEY
  - Database enforces rules even if application validation bypassed
- **Logging:** Log validation failures (potential attacks)
  - Alert on suspicious patterns (repeated SQL injection attempts)

**Which phase to address:**
- **Validation library creation:** Build before any service implementation
- **Every API endpoint:** Validate all inputs before use

---

## PERFORMANCE PITFALLS

### Pitfall 14: Chatty Service Communication

**What goes wrong:**  
Services exchange many small messages instead of batching. Order Service calls Inventory Service 1000s of times per second (once per product). Network overhead dominates; latency explodes.

**Why it happens:**
- Treating service calls like in-process calls (they're not)
- N+1 query pattern (loop and call for each record)
- Lack of API design discipline
- Fine-grained APIs encourage chatty clients

**Consequences:**
- Latency proportional to number of services involved (call overhead multiplies)
- Network bandwidth consumed by message headers (actually larger than payload)
- Timeout cascades (one slow service times out; retry storms)
- System becomes latency-sensitive; any delay cascades

**Warning signs:**
- Service call count doesn't match business operation (should be ~1 call per operation, not 1000)
- Network traffic overhead > 50% of payload
- Latency increases linearly with data volume (10 records = 10 calls, 100 records = 100 calls)
- CPU high but utilization low (threads waiting for network)

**Prevention:**
- **Batch APIs:** Multiple records per request
  - `GET /customers?ids=1,2,3` returns all customers in one call
  - `POST /inventory/check` with array of product IDs
- **Async/Fire-and-forget:** For non-critical operations
  - Don't wait for response; return immediately
  - Process asynchronously (event-driven)
- **API composition:** Client specifies relationships to fetch
  - `GET /orders?include=customer,items` fetches order + customer + items in single call
  - Prevent need for multiple service calls
- **Caching:** Reduce repeated calls
  - Cache customer data locally (refresh every 5 minutes)
  - Cache doesn't need real-time accuracy for read-only data
- **GraphQL or similar:** Query language specifying exactly what data needed
  - Prevents over-fetching (unnecessary fields) and under-fetching (need multiple queries)
- Reference: [12-factor: Backing services](https://12factor.net/backing-services)

**Which phase to address:**
- **API design:** Design batch endpoints before implementation
- **Load testing:** Measure service call count and latency; identify chatty patterns

---

### Pitfall 15: Inadequate Caching Strategies

**What goes wrong:**  
Services don't cache, or cache incorrectly. Every request queries database, hits downstream service, or recomputes. High latency; database overloaded.

**Why it happens:**
- "Caching is hard; avoid it" (false—caching is essential)
- Cache invalidation not implemented
- Cache hit rate low because cache key wrong or TTL too short
- Stale data served without acknowledgment to user

**Consequences:**
- Database CPU maxed; requests timeout
- Unnecessary service calls flood network
- User experience slow (all requests hit database)
- Downstream service throttles (overload)
- Cost high (more compute to handle cache misses)

**Warning signs:**
- Database query count high; cache hit rate low
- Latency increases during peak hours (cache effect)
- Same query executed thousands of times per second
- Database CPU at 80%+ but requests fast (cache would help)
- No Cache-Control headers or TTL configuration

**Prevention:**
- **Cache taxonomy:**
  - **L1 local cache:** In-process (fast, limited size)
    - Use for data that never changes or changes rarely
    - Loaded on service startup; kept in memory
    - Example: feature flags, configuration
  - **L2 distributed cache:** Redis/Memcached (shared across service instances)
    - Use for frequently-accessed data (user profiles, product details)
    - TTL typically 1-5 minutes
    - Example: customer data, inventory counts
  - **L3 database cache:** Database query cache (database-level)
    - Some databases cache query results automatically
    - Example: PostgreSQL, MySQL query cache
- **Cache invalidation strategies:**
  - **TTL (Time-to-Live):** Cache expires after N seconds (simple, may serve stale data)
  - **Event-driven:** When data changes, event invalidates cache
    - Product name changed → Product service emits "ProductUpdated" event
    - All services listening invalidate product cache
  - **Dependency tracking:** Cache knows dependencies; invalidates on change
    - Order cache depends on Inventory cache; if inventory changes, order cache invalidated
- **Cache key design:** Ensure cache key includes all parameters affecting result
  - Bad: `cache_key = "orders"` (shared across all requests)
  - Good: `cache_key = "orders_{userId}_{sortBy}"` (unique per user and sort)
- **Cache warming:** Pre-load cache on startup
  - Popular products, frequently-accessed customers
  - Reduces cold-start latency
- **Cache stampede prevention:**
  - When cache expires, 1000s of requests hit database simultaneously (stampede)
  - Use lock: first request refreshes; others wait for result
  - Or: refresh cache before expiry (background job)
- **HTTP caching:** Leverage browser/CDN caching
  - Set Cache-Control headers on read-only data
  - `Cache-Control: public, max-age=300` (cache 5 minutes)
  - Clients cache responses; reduce server load

**Which phase to address:**
- **Performance optimization:** Add caching when queries/calls identified as bottleneck
- **Load testing:** Measure database load; add caching if high

---

### Pitfall 16: Missing Async Operations

**What goes wrong:**  
All operations synchronous. Payment processing waits for email sending. Email slow? Payment API slow. User waits 30 seconds for order confirmation (5s payment + 25s email).

**Why it happens:**
- Simplicity mindset: synchronous is easier than async
- Unfamiliarity with async patterns (event-driven, messaging)
- Tight coupling of unrelated operations

**Consequences:**
- Latency high (wait for all serial operations)
- Bottleneck on slowest operation (email provider latency)
- Tight coupling (payment system depends on email system)
- Timeout failures (operation takes too long)

**Warning signs:**
- API response time = sum of all downstream operation times
- Email or notification system failures cause order failures
- Latency P99 >> P50 (some requests slow because waiting for async operation)
- Request timeout; operation partially completed
- "Important operations" fail if unrelated service is down

**Prevention:**
- **Async for non-critical operations:**
  - Create order (synchronous) → return order ID immediately
  - Send confirmation email (asynchronous) → processed in background
  - Send push notification (asynchronous) → processed in background
  - Update analytics (asynchronous) → processed in background
- **Event-driven architecture:**
  - Order Service publishes "OrderCreated" event
  - Email Service listens; sends confirmation email
  - Analytics Service listens; updates dashboard
  - Services decoupled; each handles independently
- **Message queue (FIFO, at-least-once delivery):**
  - Producer (Order Service) publishes event to queue
  - Consumer (Email Service) processes from queue
  - Guaranteed delivery; can retry on failure
  - Example: RabbitMQ, AWS SQS, Apache Kafka
- **Background jobs (scheduled async):**
  - Batch processing: every hour, compute overnight analytics
  - Cleanup: delete old logs; archive old orders
  - Example: Spring @Scheduled, Quartz, AWS Lambda
- **User feedback:**
  - Synchronous: "Email sent" (user sees result immediately)
  - Asynchronous: "Confirmation sent (typically <2 minutes)" (user understands delay)
  - Or: webhook/polling to get result asynchronously
- **Timeout handling:**
  - Non-critical operation timeout? Use sensible default instead of failing
  - Send order confirmation email; if timeout, still confirm order (retry email later)

**Which phase to address:**
- **Architecture phase:** Design critical vs non-critical operations
- **Every operation:** Consider if synchronous or async is appropriate

---

### Pitfall 17: Database Query Optimization Neglect

**What goes wrong:**  
Services execute unoptimized queries. N+1 queries, full table scans, missing indexes. Database CPU maxes; query latency explodes.

**Why it happens:**
- No query analysis during development (focus on correctness)
- ORM generates inefficient SQL (N+1 problem)
- Missing indexes (not obvious which queries are slow)
- Test data small; doesn't expose slowness until production

**Consequences:**
- Database CPU bottleneck (maxes at 80-90%)
- Query timeouts (exceed database timeout)
- Slow response times affect all services
- Cascading failures (services timeout waiting for DB)
- Expensive hardware spend to handle load

**Warning signs:**
- Database CPU high; response times slow
- Query logs show same query executed repeatedly
- Full table scans on large tables (logs show seq scan)
- Missing indexes on frequently-queried columns
- ORM generates N+1 queries for relationships

**Prevention:**
- **EXPLAIN ANALYZE queries:** Understand query execution plan
  - Identify seq scans (slow) vs index scans (fast)
  - Identify join strategies (hash join vs nested loop)
  - Identify estimated vs actual rows (plan accuracy)
- **Index frequently-queried columns:**
  - `CREATE INDEX idx_orders_user_id ON orders(user_id)`
  - Queries on user_id become 10-100× faster
  - But: every index has write cost; only index needed columns
- **Avoid N+1 queries:**
  - Instead of loop + query, use JOIN or batch query
  - JPA: use `FETCH JOIN` or batch size configuration
  - Manual SQL: use JOINs to fetch all data in one query
- **Optimize ORM usage:**
  - Eager load (FETCH JOIN) for needed relationships
  - Don't lazy load in loops
  - Use projections (select only needed columns)
- **Denormalization:** Store precomputed values to avoid joins
  - Order record includes `total_amount` (denormalized from order items)
  - Queries don't need JOIN to items table
  - Event updates total_amount when items change
- **Partitioning:** For large tables, partition by date/range
  - `orders` table partitioned by month
  - Queries on recent orders hit small partition (fast)
- **Monitoring:** Log slow queries automatically
  - PostgreSQL: `log_min_duration_statement = 1000` (log queries > 1s)
  - Set alert on slow query count spike

**Which phase to address:**
- **Load testing:** Identify slow queries under realistic load
- **Monitoring setup:** Enable slow query logging in production

---

## DEVELOPMENT PITFALLS

### Pitfall 18: Complex Local Development Setup

**What goes wrong:**  
To run application locally, developer must run 10+ services (Order, Inventory, Customer, Payment, etc.), databases, message brokers, caches. Setup takes hours; Docker config outdated; integration broken.

**Why it happens:**
- Microservices require many components
- No single docker-compose or orchestration for full stack
- Documentation out of sync with actual services
- Each service team maintains setup independently

**Consequences:**
- New developer onboarding: 2+ days of setup instead of 30 minutes
- Services not running locally? Developers can't test; bugs found in CI
- Developers skip local testing; rely on CI (slow feedback loop)
- Setup breaks with each service change; frustration
- Integration tests can't run locally

**Warning signs:**
- Developer doc is 50+ pages of setup steps
- "Just use staging; your setup probably won't work"
- docker-compose.yml references services that don't exist
- Frequent issues: "works for me" → "doesn't work for you"
- Integration tests only in CI; not run locally

**Prevention:**
- **docker-compose.yml for full stack:**
  - Include all services: order, inventory, customer, payment, auth
  - Include dependencies: PostgreSQL, Redis, RabbitMQ, Elasticsearch
  - Single command: `docker-compose up` starts everything
- **Service stubs/mocks for optional services:**
  - Don't need all services; stub the ones not being developed
  - Inventory Service stub returns fake data (no real service needed)
  - Only run Order Service + dependent services for feature work
- **Script setup:**
  ```bash
  ./scripts/dev-setup.sh
  # Installs dependencies, starts containers, runs migrations
  # Single command; developer ready in 5 minutes
  ```
- **Database migrations on startup:**
  - Service auto-applies pending migrations on startup
  - No manual schema setup needed
- **Pre-populated test data:**
  - docker-compose.yml seeds databases with test users, products
  - Developer immediately has data to work with
- **Troubleshooting guide:**
  - Common issues and solutions documented
  - "Containers won't start?" → "check `docker ps`; restart with `docker-compose restart`"
- **Makefile for common tasks:**
  ```makefile
  setup:     docker-compose up -d
  test:      gradle test
  logs:      docker-compose logs -f
  clean:     docker-compose down -v
  ```

**Which phase to address:**
- **MVP setup:** Establish docker-compose and setup docs from day 1
- **Onboarding:** Test setup process with new developer

---

### Pitfall 19: Incomplete Error Handling

**What goes wrong:**  
Services don't handle errors properly. Service call fails; error not caught; exception propagates; user sees 500 error. Or: error swallowed; operation silently fails.

**Why it happens:**
- Focus on happy path (works when everything succeeds)
- Lazy error handling (catch Exception; print stack trace)
- Async errors unobserved (background job fails; no one knows)
- Retry logic missing

**Consequences:**
- User experience broken (500 errors on failed operations)
- Silent failures (operation appears to succeed, but didn't)
- Cascading failures (error not caught; propagates to caller)
- Hard to debug (error context lost)
- Data corruption (partial failure; state inconsistent)

**Warning signs:**
- Stack traces in logs with no action taken
- "Works sometimes; fails sometimes" (unhandled race conditions)
- User data missing (operation appeared to succeed)
- No error messages; just "Request failed"
- Async job failures unnoticed (background job log ignored)

**Prevention:**
- **Explicit error handling:**
  ```java
  try {
    inventory.reserve(order.items);
  } catch (OutOfStockException e) {
    order.status = "FAILED";
    order.error = "Out of stock: " + e.getMessage();
    return orderResponse;  // Explicit error to user
  }
  ```
- **Fallback/default behavior:**
  - Service call fails? Use cached/default value
  - Email sending fails? Log for retry; order still created
  - Analytics service slow? Skip analytics; don't slow down order
- **Retry with exponential backoff:**
  ```java
  ExponentialBackoffRetry retry = new ExponentialBackoffRetry(
    baseDelay: 100ms,
    maxDelay: 30s,
    maxAttempts: 3
  );
  ```
- **Circuit breaker (detect failures early):**
  - After N failures, fail immediately instead of retrying
  - Gives service time to recover
- **Logging context:**
  - Log error with context: user ID, order ID, operation
  - Not just exception message; full context for debugging
- **Async error handling:**
  - Background jobs must log success/failure
  - Failed jobs go to dead-letter queue for review
  - Alert on job failure
- **User-facing error messages:**
  - Generic error to user (don't expose internals)
  - Specific error in logs (for debugging)
  - Example: User sees "Order creation failed. Retry or contact support."
  - Logs show "PaymentService connection timeout; exceeded max retries"

**Which phase to address:**
- **Every API:** Implement error handling for each endpoint
- **Testing:** Test error cases, not just happy path

---

### Pitfall 20: Poor Observability and Logging

**What goes wrong:**  
Services run in production; something fails; engineer can't determine what happened. No logs, no traces, no metrics. "It worked yesterday; don't know why it's failing now."

**Why it happens:**
- Observability afterthought (not built in from start)
- Too much logging (logs too noisy; important messages hidden)
- Too little logging (not enough context)
- Logs not correlated (can't trace request across services)
- No alerting (failures not noticed until users complain)

**Consequences:**
- MTTR (mean time to recovery) very high (hours to determine issue)
- Silent failures (service degraded; not detected)
- Difficult debugging (can't recreate production issues)
- Blind operations (no visibility into system)
- Reactionary (fixes bugs after users report)

**Warning signs:**
- "Let me SSH into production and check logs" (should be accessible from monitoring dashboard)
- When service is slow, engineer says "I don't know; restart it"
- No correlation ID in logs (can't trace request across services)
- Dashboard shows error rate but not root cause
- Incidents resolved by "restart the service"

**Prevention:**
- **Three pillars of observability:** logs, metrics, traces
- **Distributed tracing:**
  - Assign unique trace ID to each request
  - Trace ID passed to all services in call chain
  - All logs include trace ID (can search logs for request)
  - Example: Spring Cloud Sleuth + Zipkin
  ```
  Trace ID: abc123
  Service A: 2ms
    └─ Service B: 8ms
      └─ Service C: 5ms (timed out after 10ms)
  Total: slow due to timeout in Service C
  ```
- **Structured logging (JSON):**
  - Not: `Order created for user john with total 100`
  - But: `{"event": "order_created", "user_id": "john", "amount": 100, "trace_id": "abc123"}`
  - Enables parsing, searching, aggregation
- **Log levels appropriately:**
  - ERROR: failures requiring action (payment failed, database down)
  - WARN: degraded but functioning (retry attempt #3, slow query)
  - INFO: important events (user login, order created)
  - DEBUG: detailed flow (entering function, variable values) — usually disabled in production
- **Metrics (quantitative):**
  - Request count per service
  - Request latency (p50, p95, p99)
  - Error rate per endpoint
  - Database query count
  - Cache hit rate
  - Alert on anomalies (error rate spike, latency spike)
- **Dashboards:**
  - System overview: all services status, error rates, latencies
  - Service detail: requests, errors, latencies, dependencies
  - Business metrics: orders per minute, payment success rate
  - Updated in real-time; engineer can see issues immediately
- **Log aggregation:**
  - Logs from all services sent to central location (Elasticsearch, Splunk)
  - Searchable across all services
  - Long-term storage (30+ days)
- **Alerting on critical issues:**
  - Error rate > 5% → page on-call engineer
  - P99 latency > 10s → alert
  - Service down → page immediately
  - Smart alerting (don't alert on transient issues; wait for sustained)
- Reference: [Distributed tracing pattern](https://microservices.io/patterns/observability/distributed-tracing.html), [Log aggregation pattern](https://microservices.io/patterns/observability/application-logging.html)

**Which phase to address:**
- **MVP:** Set up distributed tracing + log aggregation from day 1
- **Every service:** Include trace ID in all logs
- **Load testing:** Verify observability works under load

---

### Pitfall 21: Missing Tests (Unit, Integration, Contract)

**What goes wrong:**  
Services deployed without tests. Service A changes API; Service B still expects old format; integration breaks. Or: service has bug; no test catches it.

**Why it happens:**
- Pressure to ship quickly (skip tests to save time)
- Integration testing hard (depends on other services)
- No consumer-driven contracts (API changes not validated against consumers)
- Testing cost unclear (perceived as overhead)

**Consequences:**
- Bugs found in production (not development)
- API changes break consumers (coordinated deployments needed)
- Regression bugs (working feature breaks with change)
- High MTTR (hours to fix; had tests would have caught it in minutes)

**Warning signs:**
- Service has <50% code coverage
- Integration tests don't exist ("too hard; use staging")
- New API version breaks existing clients
- Same bug appears multiple times (no test prevents regression)
- Deployment requires coordinated release (breaking change)

**Prevention:**
- **Unit tests:** Test service logic in isolation
  - Test order calculation, discount logic, validation
  - Mock dependencies (database, other services)
  - Goal: >80% code coverage of business logic
  ```java
  @Test
  void calculateTotal_appliesDiscount() {
    Order order = new Order();
    order.addItem(new Product("Widget", 100), 2);
    order.setDiscount(0.1);  // 10% discount
    assertEquals(180, order.calculateTotal());  // 2 * 100 - 20
  }
  ```
- **Integration tests:** Test service with real dependencies
  - Start real database; run actual SQL queries
  - Test with message broker; ensure messages consumed
  - Goal: test happy path + error cases per endpoint
  ```java
  @Test
  void createOrder_persists_and_publishesEvent() {
    Order order = orderService.createOrder(customerId, items);
    
    // Verify order in database
    Order persisted = orderRepository.findById(order.id);
    assertNotNull(persisted);
    
    // Verify event published
    ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(...);
    verify(eventPublisher).publish(captor.capture());
    assertEquals(order.id, captor.getValue().getOrderId());
  }
  ```
- **Consumer-driven contract tests:** API consumers define expectations
  - Order Service (consumer) defines what data it expects from Customer Service (provider)
  - Contract test verifies Customer Service returns expected data
  - Breaks if Customer Service changes API in breaking way
  - Prevents integration surprises
- **End-to-end tests:** Full flow in staging
  - User creates account → places order → receives confirmation email
  - Run against all services in staging
  - Slow; run before production deployment
- **Load/performance tests:** Verify system handles load
  - 1000 concurrent users placing orders
  - Identify bottlenecks (database, slow service)
  - Baseline response times

**Which phase to address:**
- **Every feature:** Write tests before shipping
- **API changes:** Update contract tests; verify consumers still work
- **Before production deployment:** Run full test suite

---

## SUMMARY TABLE: PITFALLS BY PHASE

| Pitfall | MVP | Design | Architecture | Implementation | Testing | Production |
|---------|-----|--------|--------------|-----------------|---------|-----------|
| Over-engineering services | ✓ | ✓ | | | | |
| Tight coupling | ✓ | ✓ | ✓ | | | |
| Shared database | ✓ | ✓ | ✓ | | | |
| Missing circuit breakers | ✓ | | ✓ | ✓ | ✓ | |
| N+1 queries | | | ✓ | ✓ | ✓ | |
| Distributed transactions | ✓ | ✓ | | | | |
| Eventual consistency confusion | ✓ | ✓ | ✓ | | | |
| Data sync failures | | ✓ | ✓ | ✓ | | |
| Cascading failures | | | ✓ | ✓ | ✓ | |
| Service-to-service auth | ✓ | ✓ | | | | |
| API Gateway misconfiguration | ✓ | ✓ | | | | |
| Token/session chaos | ✓ | ✓ | | | | |
| Input validation gaps | | ✓ | ✓ | ✓ | | |
| Chatty communication | | | ✓ | ✓ | ✓ | |
| Inadequate caching | | | ✓ | ✓ | ✓ | |
| Missing async | | ✓ | ✓ | ✓ | | |
| Query optimization | | | | ✓ | ✓ | ✓ |
| Complex local setup | | ✓ | ✓ | ✓ | | |
| Incomplete error handling | | | ✓ | ✓ | ✓ | |
| Poor observability | | ✓ | ✓ | ✓ | ✓ | |
| Missing tests | | | ✓ | ✓ | ✓ | |

**Key:** Tick mark indicates when to address pitfall

---

## CRITICAL PITFALLS FOR MVP (Must Address First)

1. **Service Decomposition** (Pitfall 1): Define core services around business capabilities
2. **Database per Service** (Pitfall 3): Each service owns its data
3. **Distributed Transactions** (Pitfall 6): Use Saga pattern; don't attempt 2PC
4. **Service Authentication** (Pitfall 10): Implement mTLS or service-to-service tokens
5. **API Gateway Security** (Pitfall 11): Enforce HTTPS, JWT validation, rate limiting
6. **Circuit Breakers** (Pitfall 4): Configure before first inter-service call
7. **Observability** (Pitfall 20): Implement distributed tracing + log aggregation from day 1
8. **Local Development** (Pitfall 18): docker-compose.yml working before MVP deployment

---

## PREVENTION WORKFLOW

1. **Design phase (Week 1-2):**
   - Review pitfalls 1, 3, 6, 10, 11
   - Document service boundaries, data ownership, auth strategy
   - Design saga flow for cross-service transactions

2. **Architecture phase (Week 2-3):**
   - Pitfalls 2, 4, 5, 12, 13, 18
   - Set up API contracts, circuit breaker config, validation library
   - Establish docker-compose for local dev

3. **Implementation (Week 3+):**
   - Pitfalls 7, 8, 9, 14, 15, 16, 17, 19, 21
   - Implement error handling, caching, async operations
   - Write tests for each feature

4. **Testing (Week final):**
   - Pitfalls 4, 9, 17, 20, 21
   - Load testing for cascading failures, N+1 queries, caching
   - End-to-end tests for saga flows

5. **Production (Ongoing):**
   - Monitor all pitfalls 4, 9, 14, 17, 20
   - Alert on anomalies (cascading failures, slow queries, observability gaps)
   - Post-mortem on incidents; prevent recurrence

---

## REFERENCES

- Martin Fowler: [Microservices](https://martinfowler.com/articles/microservices.html)
- Chris Richardson: [Microservices Patterns](https://microservices.io/patterns/)
- 12-factor app: [https://12factor.net/](https://12factor.net/)
- [Release It! Design and Deploy Production-Ready Software](https://pragprog.com/titles/mnee2/release-it-second-edition/)
- Netflix: [Hystrix - Latency and Fault Tolerance](https://github.com/Netflix/Hystrix)
- [Spring Cloud Sleuth - Distributed Tracing](https://spring.io/projects/spring-cloud-sleuth)

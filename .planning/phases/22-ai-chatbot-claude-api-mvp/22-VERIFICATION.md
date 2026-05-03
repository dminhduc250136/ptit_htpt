# Phase 22 — Verification Checklist

**Phase:** 22 — AI Chatbot Claude API MVP
**Run order:** §1 → §6 trong môi trường dev local hoặc docker compose.

> Manual UAT runbook + automated test gating cho `/gsd-verify-work 22`.
> Mọi check ✅ → phase ready for sign-off.

---

## §1. Pre-flight: secrets & env

- [ ] `cat .env.example | grep ANTHROPIC_API_KEY` — line tồn tại trong example
- [ ] User đã obtain Anthropic API key: `https://console.anthropic.com` → API Keys → Create Key
- [ ] `echo $ANTHROPIC_API_KEY` (host shell) hoặc set trong `.env` ở repo root (compose đọc qua `${ANTHROPIC_API_KEY}` — confirmed `docker-compose.yml` line 119)
- [ ] `grep -rn "NEXT_PUBLIC_ANTHROPIC" sources/frontend/src/` → KHÔNG có hit nào (key-leak guard, mitigates T-22-03)
- [ ] `cat sources/frontend/Dockerfile` — env vars truyền RUNTIME qua `docker compose ... environment:` block, KHÔNG hard-code BUILD time. Confirmed: Dockerfile hiện chỉ `npm install / npm run build / npm start`, env injection diễn ra ở compose level → ✅ pattern đúng.
- [ ] `curl -fsS https://api.anthropic.com/v1/messages -H "x-api-key: $ANTHROPIC_API_KEY" -H "anthropic-version: 2023-06-01" -H "content-type: application/json" -d '{"model":"claude-haiku-4-5","max_tokens":16,"messages":[{"role":"user","content":"hi"}]}' | jq .` → status 200 + content trả về

## §2. Smoke (curl)

Yêu cầu: stack đang chạy (`docker compose up -d` hoặc `cd sources/frontend && npm run dev`). User đã login để có JWT.

- [ ] Lấy JWT của user thường: login `demo@tmdt.local / admin123` → DevTools → `localStorage.getItem('accessToken')` (hoặc inspect cookie `auth_present` + lấy bearer từ Network tab)
- [ ] Stream chat:
  ```bash
  curl -N -X POST http://localhost:3000/api/chat/stream \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"message":"xin chào, gợi ý laptop tầm 20 triệu"}'
  ```
  Kết quả mong đợi: chunked stream `{"type":"delta",...}\n` lặp đi lặp lại, kết thúc `{"type":"done","sessionId":N,"usage":{...}}`
- [ ] List sessions:
  ```bash
  curl -X GET http://localhost:3000/api/chat/sessions \
    -H "Authorization: Bearer $TOKEN" | jq .data.content
  ```
  → array có ≥1 entry
- [ ] List messages của session đầu:
  ```bash
  curl -X GET http://localhost:3000/api/chat/sessions/<id>/messages \
    -H "Authorization: Bearer $TOKEN"
  ```
  → array có 2 messages (user + assistant)
- [ ] **IDOR guard (T-22-04):** lấy JWT user khác (register tài khoản mới), gọi `/sessions/<id_của_user_đầu>/messages` → 403 FORBIDDEN
- [ ] Admin suggest-reply:
  ```bash
  curl -X POST http://localhost:3000/api/admin/orders/<order_id>/suggest-reply \
    -H "Authorization: Bearer $ADMIN_TOKEN"
  ```
  → JSON envelope `{data:{text:"..."}}`
- [ ] **Admin role bypass (T-22-05):** user JWT (non-admin) gọi cùng endpoint → 403 FORBIDDEN

## §3. DB inspection

Container name (confirmed `docker compose ps` Phase 22): `tmdt-use-gsd-postgres-1`. Nếu compose project đặt tên khác (e.g., `tmdt-use-gsd_postgres_1`), thay theo output thực tế.

- [ ] `docker exec -it tmdt-use-gsd-postgres-1 psql -U tmdt -d tmdt -c "\\dt chat_svc.*"` → liệt kê 2 bảng `chat_sessions`, `chat_messages`
- [ ] `... -c "\\d chat_svc.chat_sessions"` → `user_id` column type là `character varying(36)` / VARCHAR(36) (KHÔNG phải BIGINT) — khớp UUID type của `user_svc.users.id` (D-19)
- [ ] `... -c "SELECT COUNT(*) FROM chat_svc.chat_sessions;"` ≥ 1 sau khi smoke §2
- [ ] `... -c "SELECT role, LEFT(content,40) FROM chat_svc.chat_messages ORDER BY created_at DESC LIMIT 4;"` → xen kẽ user/assistant

## §4. Automated tests

- [ ] `cd sources/frontend && npx tsc --noEmit` → 0 errors
- [ ] `cd sources/frontend && npm run lint` → 0 errors
- [ ] `cd sources/frontend && npm run build` → success
- [ ] `cd sources/frontend && npx playwright test e2e/chatbot-customer.spec.ts e2e/chatbot-admin.spec.ts e2e/chatbot-edge.spec.ts --project=chromium` → all green (10 tests)
- [ ] Regression: `cd sources/frontend && npx playwright test --project=chromium` → existing v1.2/v1.3 specs still green (Phase 17 admin order detail không vỡ do `/admin/orders/[id]` thêm card AI)

## §5. Manual UAT (3 items per VALIDATION.md)

### UAT-1. Prompt cache hit
1. Mở chatbot, gửi tin "tư vấn laptop"
2. Đợi xong, gửi tiếp "iPhone 15 Pro" (cùng session)
3. Tail server log: `docker logs -f tmdt-use-gsd-frontend-1 | grep "\[chat\]"`
4. ✅ PASS nếu lượt thứ 2 thấy line metric `cache_read=N` với N > 0 (Anthropic SDK trả `cache_read_input_tokens > 0` — confirms D-04 prompt caching active)

### UAT-2. Stream abort cleanup
1. Gửi prompt dài: "viết 500 từ về Macbook Pro M3"
2. Ngay khi delta đầu tiên xuất hiện, đóng modal (✕)
3. Tail server log
4. ✅ PASS nếu thấy `[chat] abort signal received session=N` trong vòng 1s — mitigates T-22-07 (resource leak)

### UAT-3. Vietnamese response quality
1. Gửi 5 prompts từ catalog: "tư vấn laptop", "iPhone 15 Pro còn không?", "so sánh chuột Logitech G Pro X vs Razer DeathAdder V3", "bàn phím cơ tầm 2 triệu", "tai nghe gaming có mic"
2. ✅ PASS nếu cả 5 response toàn tiếng Việt, không xen English giữa câu (cho phép tên brand/model giữ nguyên)

## §6. Sign-off

- [ ] Tất cả §1-§5 checked
- [ ] Không có `console.error` trong DevTools khi sử dụng chatbot (trừ test errors cố ý)
- [ ] Anthropic dashboard `console.anthropic.com → Usage` thấy traffic Phase 22 (model `claude-haiku-4-5`)

---

## Coverage Mapping — Success Criteria → Test Coverage

Mỗi success criterion từ ROADMAP (5 items) phải có owning plan + test/manual UAT.

| # | ROADMAP Success Criterion | Owning Plan | Automated Test | Manual UAT |
|---|---------------------------|-------------|----------------|------------|
| 1 | Customer chatbot streaming Vietnamese, login required | 22-02 (anthropic helper), 22-05 (stream route), 22-07 (FloatingChatButton + ChatPanel) | `chatbot-customer.spec.ts` › happy path / markdown / guest CTA | UAT-3 (VN quality) |
| 2 | Chat history persisted in `chat_svc` schema, owner-bound | 22-01 (schema), 22-05 (sessions/messages REST) | `chatbot-customer.spec.ts` › history persist; §2 smoke IDOR | §3 DB inspection |
| 3 | Product context injection top-N + system prompt cacheable | 22-03 (product search), 22-05 (buildContextXml + cache_control) | `chatbot-edge.spec.ts` › xml escape (UI side) | UAT-1 (cache hit) |
| 4 | Admin AI suggest-reply, manual confirm (no auto-send) | 22-04 (suggest-reply route), 22-06 (SuggestReplyModal + button) | `chatbot-admin.spec.ts` › modal + disclaimer + textarea editable + copy enabled | §2 smoke admin role bypass |
| 5 | Cost & safety guards: rate limit, key-leak guard, max input/output | 22-02 (rate-limit), 22-05 (input validation) | `chatbot-edge.spec.ts` › rate limit + retry + key-leak grep | §1 pre-flight key check |

## REQ Coverage Matrix

| REQ | Description | Test File | Test Name |
|-----|-------------|-----------|-----------|
| AI-01 | Customer chatbot streaming + UI | `chatbot-customer.spec.ts` | happy path / markdown render / guest CTA |
| AI-02 | Vietnamese system prompt + prompt caching | manual | UAT-1 cache_read / UAT-3 VN quality |
| AI-03 | Product context injection (top-N) | `chatbot-customer.spec.ts` + `chatbot-edge.spec.ts` | history persist (product mention) + xml escape |
| AI-04 | Persist chat history (sessions/messages) | `chatbot-customer.spec.ts` + §2 smoke + §3 DB | history persist + IDOR curl + DB rows |
| AI-05 | Admin AI suggest-reply (manual confirm) | `chatbot-admin.spec.ts` + §2 smoke | suggest reply modal + role bypass curl |

## Known Limitations (deferred — see CONTEXT.md `<deferred>`)

- Single-instance rate limiter (in-memory `Map`). Multi-instance / horizontal scale → cần Redis (defer v1.4+).
- KHÔNG agentic tool-use, KHÔNG function-calling, KHÔNG RAG embedding (D-08 lock).
- KHÔNG multilingual (Vietnamese-only — D lock).
- KHÔNG guest chat (D-05 lock).
- KHÔNG auto-confirm order từ chatbot (D-07 lock).
- KHÔNG voice / image multimodal.
- KHÔNG cost dashboard cho admin — chỉ log token usage.
- KHÔNG helpful-vote/feedback trên message assistant.

---

*Phase 22 ready for `/gsd-verify-work` khi tất cả check trên ✅.*

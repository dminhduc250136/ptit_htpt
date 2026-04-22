# AI Trigger Specification (Phase 2.1)

## Mục tiêu
Tài liệu này mô tả cơ chế trigger cho AI Agent trong Cursor theo hướng:
- Rõ file nào là trigger source.
- Rõ trigger condition.
- Rõ action bắt buộc (actionable constraints), không viết kiểu narrative mơ hồ.

Reviewer (Claude/Codex) có thể dùng tài liệu này để đánh giá đồng thời:
1. Cấu trúc hệ thống tài liệu.
2. Cơ chế nạp context và kiểm soát hành vi agent.

---

## 1) Trigger Sources hiện tại

### A. Root `AGENTS.md`
- **Phạm vi:** toàn repository.
- **Vai trò:** định nghĩa read order và domain routing ở mức dự án.
- **Lưu ý:** đây là guidance layer cho agent, không phải hard guarantee của runtime.

### B. `.cursor/rules/context-bootstrap.mdc`
- **Phạm vi:** `alwaysApply: true`.
- **Vai trò:** minimal bootstrap (nhẹ) để tránh context bloat.
- **Nội dung chính:** project identity + guardrails; không ép đọc full docs ở mọi phiên.

### C. `.cursor/rules/context-bootstrap-full.mdc`
- **Phạm vi:** `alwaysApply: false` (description-based).
- **Vai trò:** full bootstrap cho non-trivial tasks.
- **Nội dung chính:** khi cần deep context thì đọc `PROJECT_CONTEXT.md`, `README.md`, `_index.json`.

### D. `.cursor/rules/domain-doc-routing.mdc`
- **Phạm vi:** `alwaysApply: false` (description-based).
- **Vai trò:** route theo tầng docs khi task cần domain understanding hoặc impact analysis.
- **Nội dung chính:** map intent -> docs layer; yêu cầu hỏi lại nếu intent mơ hồ.
- **Description chuẩn dùng trong file rule:**
  - `Route agent to correct documentation layer strategy ba architecture technical-spec when task involves domain understanding impact analysis UC implementation or service design changes`

### E. `.cursor/rules/frontend-context.mdc`
- **Phạm vi:** `globs: sources/frontend/**`.
- **Vai trò:** frontend overlay.
- **Nội dung chính:** đọc `sources/frontend/AGENTS.md` trước khi sửa frontend.

### F. `.cursor/rules/backend-context.mdc`
- **Phạm vi:** `globs: sources/backend/services/**`.
- **Vai trò:** backend overlay (future-ready cho microservices code layout).
- **Nội dung chính:** đọc context/domain docs tương ứng trước khi sửa backend services.

---

## 2) Trigger Flow (runtime guidance, non-guaranteed)

## Step 0 — Session bootstrap
Luôn áp dụng minimal bootstrap (`context-bootstrap.mdc`) để giữ guardrails nhẹ.

Với task không đơn giản, full bootstrap (`context-bootstrap-full.mdc`) được kích hoạt và agent được yêu cầu đọc:
1. `PROJECT_CONTEXT.md`
2. `README.md`
3. `_index.json`

**Expected outcome**
- Hiểu scope sản phẩm, quy ước tài liệu, mapping UC/TS/service.
- Giữ `PROJECT_CONTEXT.md` root là single source of truth.

## Step 1 — Classify task intent
Agent phân loại task vào một trong các nhóm:
- Business alignment / rule clarification
- Use case behavior
- Architecture/service design
- Implementation/API contract
- Frontend UI task

**Fallback rule**
- Nếu intent/domain chưa rõ, agent phải hỏi lại user trước khi route docs sâu.

## Step 2 — Route theo tầng tài liệu
Agent ưu tiên theo thứ tự:
- `strategy/` (WHY)
- `ba/` (WHAT)
- `architecture/` (HOW high-level)
- `technical-spec/` (HOW implementation)

## Step 3 — Route theo domain
Domain chính:
- User
- Product
- Order

Agent ưu tiên complete context cho domain chính trước, sau đó mới mở rộng domain phụ.

## Step 4 — Frontend/Backend overlays
- Nếu task chạm `sources/frontend/**`: áp dụng `frontend-context.mdc`.
- Nếu task chạm `sources/backend/services/**`: áp dụng `backend-context.mdc`.

---

## 3) Actionable Constraints theo từng tầng

## `strategy/` constraints
Before suggesting logic or code changes, agent MUST confirm:
- [ ] Business rationale và KPI liên quan đã rõ từ `strategy/`
- [ ] Rule group liên quan đã được xác định (pricing/inventory/order/payment/...)

If not clear:
- [ ] Ask clarification question.

## `ba/` constraints
Before implementation, agent MUST confirm:
- [ ] UC tương ứng đã được xác định.
- [ ] Preconditions/main flow/alternative or exception flow đã được kiểm tra.
- [ ] Acceptance criteria bị ảnh hưởng đã được nhận diện.

## `architecture/` constraints
Before changing service behavior, agent MUST confirm:
- [ ] Không vi phạm service boundary (không cross-service DB access).
- [ ] Sequence/class model liên quan đã được kiểm tra.
- [ ] Event/API contract impact đã được xác định (kể cả versioning nếu cần).

## `technical-spec/` constraints
Before finalizing code/API output, agent MUST confirm:
- [ ] TS file cho UC tương ứng đã được đối chiếu.
- [ ] Output nhất quán với BA + Architecture assumptions.
- [ ] Nếu TS thiếu hoặc stale: nêu rõ gap và đề xuất cập nhật docs.

---

## 4) Trigger Matrix (Intent -> Docs)

| Task intent | Docs đọc bắt buộc | Docs đọc bổ sung |
|---|---|---|
| Làm rõ business rule | `strategy/02-business-rules.md` | `strategy/services/*.md`, `ba/uc-*.md` |
| Implement/bugfix theo UC | `_index.json`, `ba/uc-*.md`, `technical-spec/ts-*.md` | `architecture/services/*.md` |
| Đổi thiết kế service/event | `architecture/00-overview.md`, `architecture/services/*.md` | `strategy/`, `technical-spec/` |
| Chỉnh frontend UI | `PROJECT_CONTEXT.md`, `sources/frontend/AGENTS.md` | UC + TS + `architecture/frontend.md` |
| Chỉnh backend service | `PROJECT_CONTEXT.md`, `_index.json`, `architecture/services/*.md` | UC + TS + business rules liên quan |
| Đánh giá impact end-to-end | `strategy/`, `ba/`, `architecture/`, `technical-spec/` | docs domain phụ liên quan |

---

## 5) Review Checklist cho Claude/Codex

- Trigger source có tồn tại thật không?
  - `AGENTS.md` root
  - `.cursor/rules/*.mdc`
- Có tách rõ guidance vs guaranteed runtime behavior không?
- `alwaysApply` có đang tối ưu context budget không (minimal vs full bootstrap)?
- Description của description-based rules có đủ keyword để kích hoạt đúng ngữ cảnh không?
- Có fallback khi intent mơ hồ không (ask clarification)?
- Trigger behavior có ở dạng actionable constraints/checklist không?
- Trigger frontend/backend có đối xứng theo domain code path không?
- Có giữ single source of truth cho project context không?

---

## 6) Non-goals

- Không thay thế design review/architecture review của con người.
- Không ép agent luôn đọc toàn bộ docs cho mọi task nhỏ.
- Không tạo folder wrapper chỉ để đặt tên "AI context" mà không có trigger thật.

---

## 7) Trạng thái hiện tại

- Đã có trigger rules qua `.cursor/rules/*.mdc`.
- Đã có routing guide qua `AGENTS.md` root.
- Đã chốt `PROJECT_CONTEXT.md` root là single source of truth.
- Bootstrap đã tách 2 lớp: minimal always-on + full bootstrap description-based.
- `domain-doc-routing.mdc` không còn always-apply để giảm context overhead.
- Đã bổ sung trigger backend qua `backend-context.mdc`.

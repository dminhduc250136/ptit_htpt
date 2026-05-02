---
phase: 10-user-svc-schema-profile-editing
verified: 2026-04-27T05:30:00Z
status: human_needed
score: 8/8 must-haves verified (automated)
overrides_applied: 0
deferred:
  - truth: "User upload avatar JPEG/PNG/WebP <= 2MB qua Avatar section → backend magic-byte verify + Thumbnailator resize 256x256 → luu BYTEA"
    addressed_in: "Backlog (ACCT-04 explicitly deferred per D-08)"
    evidence: "ROADMAP Phase 10 Note: 'ACCT-04 (avatar upload) DEFERRED to backlog per CONTEXT D-08 — Phase 10 ship Profile Editing only. Success Criteria 2 & 3 (avatar upload flows) deferred together.'"
  - truth: "User upload .exe hoac file > 2MB → backend reject 422 voi error code ro rang"
    addressed_in: "Backlog (ACCT-04)"
    evidence: "ROADMAP SC-3 co note defer cung voi SC-2. CONTEXT D-08 defer toan bo V3 migration + multipart + Thumbnailator."
human_verification:
  - test: "Profile Info form end-to-end submit"
    expected: "Login, vao /profile/settings, sua fullName + phone hop le, click 'Luu thay doi' → toast 'Da cap nhat' xuat hien ~3s; navbar top-right hien thi ten moi ngay (khong reload)"
    why_human: "Requires docker stack running; navbar update via useAuth().login() can only be visually confirmed"
  - test: "Client-side validation block"
    expected: "Nhap phone = 'abc' → submit → field error 'So dien thoai khong hop le' hien thi ngay, form khong submit (no network call)"
    why_human: "Client-side zod validation behavior requires browser interaction"
  - test: "Backend 400 fieldErrors mapped per field"
    expected: "Giả lập backend tra 400 fieldErrors[{field:'phone',message:'...'}] → setError to field trong rhf, hien field-level error"
    why_human: "Requires mocking backend response or running integration test"
  - test: "Security section (Phase 9) intact"
    expected: "Section 'Doi mat khau' van hien thi day du 3 input fields + submit → doi mat khau thanh cong"
    why_human: "Requires docker stack + user with known password to verify end-to-end"
  - test: "Avatar section static placeholder"
    expected: "Section 'Anh dai dien' hien thi initials circle (chu cai dau email) + text 'Tinh nang tai anh dai dien se co trong ban cap nhat sau.' Khong co file input"
    why_human: "Visual verification required; static rendering depends on profileEmail state loaded from getMe()"
---

# Phase 10: User-Svc Schema + Profile Editing — Verification Report

**Phase Goal:** Lay foundation cho user-svc profile editing — GET/PATCH /users/me backend, rhf+zod form tai /profile/settings (Profile Info section), navbar refresh qua useAuth().login(). ACCT-04 avatar deferred per D-08.
**Verified:** 2026-04-27T05:30:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (Must-Haves Tong Hop)

Must-haves duoc lay tu: ROADMAP.md SC-1 + SC-4 (SC-2/SC-3 defer per D-08) + Plan frontmatter cua 3 plans.

| #  | Truth | Status | Evidence |
|----|-------|--------|----------|
| 1  | UserDto co 9 components bao gom `boolean hasAvatar` | VERIFIED | `UserDto.java` line 19: `boolean hasAvatar` — 9 components co comment D-06 |
| 2  | UpdateMeRequest record voi jakarta validation (Size 1-120 fullName + Pattern phone VN-loose) | VERIFIED | `UpdateMeRequest.java` lines 15-20: `@Size(min=1,max=120)` + `@Pattern(regexp="^\\+?[0-9\\s-]{7,20}$")` |
| 3  | UserProfileService co getMe(userId) + updateMe(userId, req) voi partial update pattern | VERIFIED | `UserProfileService.java` lines 37+51: 2 methods, if-not-null conditional setters, `@Service @Transactional` |
| 4  | UserMeController co @GetMapping + @PatchMapping root /users/me, moi handler goi extractUserIdFromBearer | VERIFIED | `UserMeController.java` lines 57+73+105: GET+PATCH+password methods; extractUserIdFromBearer tai dung o ca 3 (lines 61, 78, 96) |
| 5  | package.json frontend co react-hook-form, zod, @hookform/resolvers trong dependencies | VERIFIED | `package.json`: `"react-hook-form": "^7.74.0"`, `"zod": "^4.3.6"`, `"@hookform/resolvers": "^5.2.2"` — ca 3 trong `dependencies` block |
| 6  | User type trong types/index.ts co hasAvatar?: boolean; services/users.ts co getMe() + patchMe() + UpdateMeBody | VERIFIED | `types/index.ts` line 49: `hasAvatar?: boolean`; `users.ts` lines 58-69: 3 exports dung dau |
| 7  | /profile/settings render 3 sections theo thu tu (Profile Info / Avatar / Security), dung rhf+zod, submit thanh cong → patchMe + useAuth().login + showToast | VERIFIED (static) | `page.tsx`: profileSchema (lines 29-37), useForm<ProfileFormData> (line 53), patchMe (line 74), login({...user, name:...}) (line 76), showToast('Da cap nhat') (line 77), Avatar placeholder (lines 187-193), Security section intact (lines 195-248) |
| 8  | Gateway route user-service-me(-base) DUNG TRUOC user-service-base | VERIFIED | `application.yml` lines 53-67: `user-service-me-base` (Path=/api/users/me) + `user-service-me` (Path=/api/users/me/**) ca hai TRUOC `user-service-base` (Path=/api/users) — line 53/59 < line 67 |

**Score:** 8/8 truths verified (static analysis)

---

### Deferred Items

Items chua duoc ship nhung da explicitly defer trong ROADMAP — KHONG phai gaps.

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | Avatar upload (ACCT-04): magic-byte verify, Thumbnailator 256x256, BYTEA luu tru | Backlog (explicitly deferred per D-08) | ROADMAP Phase 10 Note: "ACCT-04 DEFERRED to backlog per CONTEXT D-08. SC-2 & SC-3 deferred together." |
| 2 | V3 Flyway migration (ALTER users ADD avatar BYTEA) | Backlog | CONTEXT D-09: "V3 reservation giu trong ROADMAP nhung KHONG execute Phase 10" |
| 3 | GET /api/users/{id}/avatar binary serve | Backlog | Phu thuoc vao V3 migration + ACCT-04 implementation |

---

### Required Artifacts

| Artifact | Provided By | Status | Chi Tiet |
|----------|-------------|--------|----------|
| `sources/backend/user-service/.../web/UpdateMeRequest.java` | Plan 10-01 Task 2 | VERIFIED | Record co 2 fields co annotation, compile duoc |
| `sources/backend/user-service/.../service/UserProfileService.java` | Plan 10-01 Task 2 | VERIFIED | @Service @Transactional, getMe + updateMe substantive |
| `sources/backend/user-service/.../web/UserMeController.java` | Plan 10-01 Task 3 | VERIFIED | 3 endpoints, inject profileService + passwordService |
| `sources/backend/user-service/.../domain/UserDto.java` | Plan 10-01 Task 1 | VERIFIED | 9-component record, hasAvatar comment D-06 |
| `sources/backend/user-service/.../domain/UserMapper.java` | Plan 10-01 Task 1 | VERIFIED | toDto() pass `false` tai arg thu 7 voi comment |
| `sources/frontend/package.json` | Plan 10-02 Task 1 | VERIFIED | 3 deps trong dependencies block |
| `sources/frontend/src/types/index.ts` | Plan 10-03 Task 1 | VERIFIED | hasAvatar?: boolean trong User interface line 49 |
| `sources/frontend/src/services/users.ts` | Plan 10-03 Task 1 | VERIFIED | getMe, patchMe, UpdateMeBody exported |
| `sources/frontend/src/app/profile/settings/page.tsx` | Plan 10-03 Task 2 | VERIFIED | 3 sections, rhf+zod, navbar sync, Phase 9 intact |
| `sources/frontend/src/app/profile/settings/page.module.css` | Plan 10-03 Task 2 | VERIFIED | .avatarPlaceholder, .comingSoon, .readonly co mat |

---

### Key Link Verification

| From | To | Via | Status | Chi Tiet |
|------|----|-----|--------|----------|
| `UserMeController` | `UserProfileService` | Constructor injection | VERIFIED | Lines 39-48: `private final UserProfileService profileService` + constructor |
| `UserMeController.getMe/updateMe` | `extractUserIdFromBearer(authHeader)` | JWT subject claim | VERIFIED | Lines 61 + 78: ca 2 handlers goi extractUserIdFromBearer truoc delegate |
| `UserMapper.toDto` | `UserDto(9 args)` | false tai arg thu 7 | VERIFIED | `UserMapper.java` line 19: `false, // D-06: hasAvatar Phase 10 luon false` |
| `page.tsx onSubmitProfile` | `patchMe()` | rhfHandleSubmit wrapper | VERIFIED | Line 74: `await patchMe({ fullName: data.fullName, phone: data.phone \|\| undefined })` |
| `patchMe success` | `useAuth().login(updatedUser)` | AuthProvider context update | VERIFIED | Line 76: `login({ ...user, name: updated.fullName ?? user.name })` — dung AuthProvider's user shape (`{ id, email, name }`) |
| `useEffect mount` | `getMe().then(...)` | Fetch hydrate form defaults | VERIFIED | Lines 60-70: alive flag + reset() + setProfileEmail() trong then() |
| `form errors` | `showToast(...)` | useToast hook | VERIFIED | Lines 77, 87, 89: showToast duoc goi trong ca success va error paths |
| `getMe()/patchMe()` | `httpGet/httpPatch('/api/users/me')` | services/http.ts wrapper | VERIFIED | `users.ts` lines 63-68: httpGet<User> + httpPatch<User> voi path '/api/users/me' |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|-------------------|--------|
| `UserProfileService.getMe` | `UserEntity entity` | `userRepo.findById(userId)` → DB query | Yes — JPA findById goi DB | FLOWING |
| `UserProfileService.updateMe` | `UserEntity entity` | `userRepo.findById` + `userRepo.save` | Yes — JPA read + write DB | FLOWING |
| `page.tsx profileEmail` | `profileEmail state` | `getMe().then(me => setProfileEmail(me.email))` | Yes — from real API call | FLOWING |
| `page.tsx form defaults` | `fullName, phone fields` | `getMe().then(me => reset({...}))` | Yes — from real API call | FLOWING |
| `hasAvatar in UserDto` | `boolean hasAvatar` | `UserMapper.toDto` hardcode `false` | Intentional stub (D-06/D-08) | STATIC (by design) |

**Ghi chu:** `hasAvatar=false` la intentional stub co comment D-06 — KHONG phai defect. Avatar Phase 12+ will wire real value.

---

### Behavioral Spot-Checks

Step 7b: SKIPPED — backend can only be tested khi docker stack is running. Frontend checks duoi day la static module checks.

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| react-hook-form da install | grep `"react-hook-form"` package.json | Found `"^7.74.0"` | PASS |
| zod da install | grep `"zod"` package.json | Found `"^4.3.6"` | PASS |
| @hookform/resolvers da install | grep `"@hookform/resolvers"` package.json | Found `"^5.2.2"` | PASS |
| useForm import trong page.tsx | grep `useForm` page.tsx | Line 4: `import { useForm }` | PASS |
| getMe() trong users.ts goi dung endpoint | grep `/api/users/me` users.ts | Lines 64+68 | PASS |
| Gateway me route TRUOC base | grep -n `user-service-me` application.yml | Lines 53,59 < line 67 | PASS |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|------------|-------------|-------------|--------|----------|
| ACCT-03 | 10-01, 10-02, 10-03 | Profile fullName/phone editing — form rhf+zod, GET/PATCH /api/users/me, navbar sync | SATISFIED (automated) | Backend: UserMeController GET+PATCH + UserProfileService; Frontend: profileSchema + patchMe + login({...user, name}) |
| ACCT-04 | 10-01 (deferred_requirements) | Avatar upload — multipart, magic-byte, Thumbnailator, BYTEA | DEFERRED (intentional) | CONTEXT D-08, ROADMAP Phase 10 Note. Avatar section la static placeholder. Backlog. |

**ACCT-04 trong REQUIREMENTS.md** duoc map vao Phase 10 trong Traceability table — nhung da co explicit defer note trong ROADMAP.md. Coi la DEFERRED, khong phai gap.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `UserMapper.java` | 19 | `false` hardcoded cho hasAvatar | INFO | Intentional stub (D-06 comment) — Avatar Phase 12+ |
| `page.tsx` | 76 | `login({ ...user, name: updated.fullName ?? user.name })` dung `user.name` (AuthProvider shape) thay vi `user.fullName` (User type) | WARNING | Khong phai bug: AuthProvider.user co type `{ id, email, name }` (khac User interface trong types/index.ts). `name` trong AuthProvider duoc set tu `username` khi login (login/page.tsx:46). Sau patchMe, name duoc update dung `updated.fullName` — Header hien thi dung. Nhung neu `updated.fullName` la null/empty, fallback ve `user.name` (= username) thay vi empty string — co the confuse user. |
| `page.tsx` | 76 | `user.name` — AuthProvider User type khong co `fullName` field | INFO | Type mismatch giua AuthProvider's `user` shape va `types/index.ts User` interface. Khong gay bug runtime (JavaScript spread extra fields), nhung lam code kho hieu. Cai thien trong future refactor. |

**Phan loai:** Khong co blocker. 1 warning (minor UX edge case, khong anh huong happy path).

---

### Human Verification Required

Cac kiem tra sau can docker stack chay hoac browser interaction:

#### 1. Profile Info Form — Submit Happy Path

**Test:** Login voi user demo, vao `/profile/settings`, sua fullName thanh "Nguyen Van Test", phone thanh "+84 901 234 567", click "Luu thay doi"
**Expected:** Toast "Da cap nhat" xuat hien ~3s; navbar top-right user name thay doi ngay thanh "Nguyen Van Test" (khong reload page)
**Why human:** Requires running docker stack + full auth flow; navbar update via useAuth().login() context change phai duoc verify bang mat

#### 2. Client-side Validation (Zod)

**Test:** Nhap phone = "abc", click "Luu thay doi"
**Expected:** Field error "So dien thoai khong hop le" hien ra ngay duoi input phone; form khong gui request den backend (no network activity)
**Why human:** Zod client-side validation behavior can only be confirmed via browser devtools network tab

#### 3. Backend 400 fieldErrors Mapping

**Test:** Submit form voi phone vuot qua regex (hoac mock backend tra 400 voi fieldErrors)
**Expected:** `setError` called per field → field-level error message hien thi dung field tuong ung
**Why human:** Can either mock API or observe actual 400 from backend; cannot verify setError() behavior statically

#### 4. Phase 9 Security Section Intact

**Test:** Trong cung trang `/profile/settings`, dien oldPassword/newPassword/confirmPassword hop le, click "Doi mat khau"
**Expected:** Doi mat khau thanh cong (hoac tra loi "Mat khau hien tai khong dung" neu sai), khong bi break boi Phase 10 changes
**Why human:** Requires docker stack + user with known password

#### 5. Avatar Placeholder Visual

**Test:** Vao `/profile/settings` khi da login
**Expected:** Section "Anh dai dien" hien thi initials circle (chu cai dau cua email), text "Tinh nang tai anh dai dien se co trong ban cap nhat sau.", KHONG co file input
**Why human:** Static rendering co dieu kien (chu cai lay tu `profileEmail` sau khi `getMe()` tra ve) — phai verify bang mat

---

### Gaps Summary

Khong co gaps bat buoc phai giai quyet. Tat ca 8/8 must-haves VERIFIED bang static analysis.

**Deferred items (2):** ACCT-04 avatar upload va V3 migration da explicitly deferred per D-08 + ROADMAP note — khong phai gap.

**Human verification items (5):** Tat ca cac kiem tra manual can docker stack hoac browser interaction. Code da wired dung theo static analysis — human items la UAT confirmation, khong phai code gap.

---

_Verified: 2026-04-27T05:30:00Z_
_Verifier: Claude (gsd-verifier)_

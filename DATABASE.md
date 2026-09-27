# TextMe - Supabase Database Setup & Schema

This document contains the complete database schema, Row Level Security (RLS) policies, and RPC functions required for the TextMe backend on Supabase.

---

## How to Apply Schema to Your Supabase Project

1. Log in to your [Supabase Dashboard](https://supabase.com/dashboard).
2. Select your project (or create a new project).
3. Navigate to **SQL Editor** in the left sidebar.
4. Click **New Query**.
5. Copy the entire contents of [`supabase/schema.sql`](file:///run/media/dwnt/DATA/project_dewanto/android_app/TextMe/supabase/schema.sql) and paste them into the SQL Editor.
6. Click **Run** (or press `Ctrl + Enter` / `Cmd + Enter`).

> [!IMPORTANT]
> **Schema Grants & Error 42501 Fix**:
> If you encounter `42501 permission denied for schema public`, ensure lines 285-293 of `supabase/schema.sql` are executed:
> ```sql
> GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
> GRANT ALL ON ALL TABLES IN SCHEMA public TO anon, authenticated, service_role;
> GRANT ALL ON ALL ROUTINES IN SCHEMA public TO anon, authenticated, service_role;
> GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated, service_role;
> ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated, service_role;
> ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON ROUTINES TO anon, authenticated, service_role;
> ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;
> ```


---

## Tables Overview

### 1. `identities`
- `id` (UUID): Identity Identifier.
- `user_id` (UUID): References `auth.users(id)`.
- `public_id` (VARCHAR(8)): Server-generated 8-character uppercase alphanumeric Public ID (e.g. `K7F2A91X`), generated via PostgreSQL function `generate_public_id()`.
- `created_at` (TIMESTAMPTZ): Server creation timestamp.
- `status` (TEXT): `active`, `expired`, `revoked`.
- `auto_rotation_enabled` (BOOLEAN): Defaults to `false` (disabled by default; user can enable in Settings).

### 2. `chat_requests`
- `id` (UUID): Request Identifier.
- `sender_identity_id` (UUID): References `identities(id)`.
- `sender_public_id` (TEXT): Snapshot of sender's 8-character Public ID at request creation.
- `receiver_identity_id` (UUID): References `identities(id)`.
- `receiver_public_id` (TEXT): Snapshot of receiver's 8-character Public ID.
- `status` (TEXT): `pending`, `accepted`, `rejected`, `cancelled`, `expired`.
- `created_at` / `updated_at` (TIMESTAMPTZ).

### 3. `conversations`
- `id` (UUID): 1-to-1 Conversation Identifier.
- `participant_a` (UUID): References `identities(id)`.
- `participant_b` (UUID): References `identities(id)`.
- `expires_at` (TIMESTAMPTZ): Derived from `MIN(participant_a.expires_at, participant_b.expires_at)`.
- `status` (TEXT): `active`, `expired`, `deleted`.

### 4. `messages`
- `id` (UUID): Message Identifier.
- `conversation_id` (UUID): References `conversations(id)`.
- `sender_identity_id` (UUID): References `identities(id)`.
- `ciphertext` (TEXT): End-to-End Encrypted payload (server never sees plaintext).
- `nonce` (TEXT): Cryptographic AES-GCM IV/nonce.
- `created_at` (TIMESTAMPTZ).

### 5. `blocks`
- `id` (UUID): Block record.
- `blocker_identity_id` (UUID): Blocker identity.
- `blocked_identity_id` (UUID): Blocked identity.
- `created_at` (TIMESTAMPTZ): Block creation timestamp.
- **Enforcement**:
  - Blocked users cannot send chat requests to the blocker (enforced via `chat_requests` RLS `NOT EXISTS` check and repository validation).
  - Incoming requests from blocked users are excluded from inbox sync.
  - Users can view and manage their own block records.


---

## Security & Row Level Security (RLS)

- **RLS Enabled**: Enforced on all tables.
- **Data Isolation**: Users can only observe and interact with records belonging to their active identity.
- **Server Timestamp Authority**: Expiration decisions rely on `now()`, preventing client clock tampering.
- **Plaintext Storage Protection**: The database only stores encrypted `ciphertext` and `nonce`.

---

## RPC Functions

### `accept_chat_request(p_request_id UUID)`
- **Security**: `SECURITY DEFINER` with `SET search_path = public`.
- **Functionality**:
  1. Validates that the caller's active identity matches `receiver_identity_id`.
  2. Ensures the request is in `pending` status (locked with `FOR UPDATE`).
  3. Verifies both sender and receiver identities are `active` and non-expired. If invalid, cancels the request and aborts.
  4. Computes conversation `expires_at = LEAST(sender.expires_at, receiver.expires_at)`.
  5. Creates or retrieves the 1-to-1 conversation between `sender_identity_id` and `receiver_identity_id`.
  6. Updates request status to `accepted`.
  7. Returns the created/active `conversations` record.


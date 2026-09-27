-- TextMe PostgreSQL Database Schema & RLS Policies for Supabase

-- Enable Extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- SERVER-SIDE PUBLIC ID GENERATOR
CREATE OR REPLACE FUNCTION public.generate_public_id()
RETURNS VARCHAR(8)
LANGUAGE plpgsql
AS $$
DECLARE
    chars TEXT := 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    result VARCHAR(8) := '';
    i INTEGER;
    is_unique BOOLEAN := FALSE;
BEGIN
    WHILE NOT is_unique LOOP
        result := '';
        FOR i IN 1..8 LOOP
            result := result || substr(chars, floor(random() * length(chars) + 1)::integer, 1);
        END LOOP;

        IF NOT EXISTS (SELECT 1 FROM public.identities WHERE public_id = result) THEN
            is_unique := TRUE;
        END IF;
    END LOOP;

    RETURN result;
END;
$$;

-- 1. IDENTITIES TABLE
CREATE TABLE IF NOT EXISTS public.identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    public_id VARCHAR(8) NOT NULL UNIQUE DEFAULT public.generate_public_id(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '24 hours'),
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'expired', 'revoked')),
    auto_rotation_enabled BOOLEAN NOT NULL DEFAULT false
);

ALTER TABLE public.identities ADD COLUMN IF NOT EXISTS auto_rotation_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.identities ALTER COLUMN auto_rotation_enabled SET DEFAULT false;
ALTER TABLE public.identities ALTER COLUMN public_id SET DEFAULT public.generate_public_id();

-- TRIGGER TO ENSURE CANONICAL SERVER-GENERATED PUBLIC_ID
CREATE OR REPLACE FUNCTION public.ensure_identity_public_id()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.public_id IS NULL OR NEW.public_id = '' THEN
        NEW.public_id := public.generate_public_id();
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trigger_ensure_public_id ON public.identities;
CREATE TRIGGER trigger_ensure_public_id
    BEFORE INSERT ON public.identities
    FOR EACH ROW EXECUTE FUNCTION public.ensure_identity_public_id();

-- AUTOMATICALLY CREATE IDENTITY RECORD WHEN A NEW USER SIGNS UP IN AUTH.USERS
CREATE OR REPLACE FUNCTION public.handle_new_user_identity()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.identities (user_id, status, auto_rotation_enabled)
    VALUES (NEW.id, 'active', false)
    ON CONFLICT DO NOTHING;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created_identity ON auth.users;
CREATE TRIGGER on_auth_user_created_identity
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user_identity();

CREATE INDEX IF NOT EXISTS idx_identities_public_id ON public.identities(public_id) WHERE status = 'active';
CREATE INDEX IF NOT EXISTS idx_identities_user_id ON public.identities(user_id) WHERE status = 'active';
CREATE INDEX IF NOT EXISTS idx_identities_expires_at ON public.identities(expires_at);

-- RPC FUNCTION: GET OR CREATE ACTIVE IDENTITY
CREATE OR REPLACE FUNCTION public.get_or_create_active_identity(p_user_id UUID DEFAULT auth.uid())
RETURNS SETOF public.identities
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_identity public.identities%ROWTYPE;
BEGIN
    SELECT * INTO v_identity
    FROM public.identities
    WHERE user_id = p_user_id
      AND status = 'active'
      AND expires_at > now()
    ORDER BY created_at DESC
    LIMIT 1;

    IF v_identity.id IS NOT NULL THEN
        RETURN NEXT v_identity;
        RETURN;
    END IF;

    UPDATE public.identities
    SET status = 'revoked'
    WHERE user_id = p_user_id AND status = 'active';

    INSERT INTO public.identities (user_id, status, auto_rotation_enabled)
    VALUES (p_user_id, 'active', false)
    RETURNING * INTO v_identity;

    RETURN NEXT v_identity;
    RETURN;
END;
$$;

-- 2. CHAT REQUESTS TABLE
CREATE TABLE IF NOT EXISTS public.chat_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_identity_id UUID NOT NULL REFERENCES public.identities(id) ON DELETE CASCADE,
    sender_public_id TEXT NOT NULL DEFAULT '',
    receiver_identity_id UUID NOT NULL REFERENCES public.identities(id) ON DELETE CASCADE,
    receiver_public_id TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'rejected', 'cancelled', 'expired')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT prevent_self_request CHECK (sender_identity_id <> receiver_identity_id)
);

ALTER TABLE public.chat_requests 
ADD COLUMN IF NOT EXISTS sender_public_id TEXT NOT NULL DEFAULT '',
ADD COLUMN IF NOT EXISTS receiver_public_id TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_chat_requests_receiver ON public.chat_requests(receiver_identity_id, status);
CREATE INDEX IF NOT EXISTS idx_chat_requests_sender ON public.chat_requests(sender_identity_id, status);

-- 3. CONVERSATIONS TABLE
CREATE TABLE IF NOT EXISTS public.conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_a UUID NOT NULL REFERENCES public.identities(id) ON DELETE CASCADE,
    participant_b UUID NOT NULL REFERENCES public.identities(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'expired', 'deleted')),
    terminated_reason TEXT,
    terminated_by UUID REFERENCES public.identities(id) ON DELETE SET NULL,
    CONSTRAINT prevent_self_conversation CHECK (participant_a <> participant_b)
);

CREATE INDEX IF NOT EXISTS idx_conversations_participants ON public.conversations(participant_a, participant_b) WHERE status = 'active';

-- TRIGGER FUNCTION: TERMINATE CONVERSATIONS AND CANCEL PENDING REQUESTS ON IDENTITY REVOCATION
CREATE OR REPLACE FUNCTION public.handle_identity_revocation()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NEW.status = 'revoked' AND OLD.status = 'active' THEN
        -- 1. Terminate all active conversations involving this identity
        UPDATE public.conversations
        SET status = 'expired',
            terminated_reason = 'identity_rotated',
            terminated_by = OLD.id
        WHERE (participant_a = OLD.id OR participant_b = OLD.id)
          AND status = 'active';

        -- 2. Invalidate all pending chat requests sent by or to this identity
        UPDATE public.chat_requests
        SET status = 'cancelled',
            updated_at = now()
        WHERE (sender_identity_id = OLD.id OR receiver_identity_id = OLD.id)
          AND status = 'pending';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trigger_on_identity_revocation ON public.identities;
CREATE TRIGGER trigger_on_identity_revocation
    AFTER UPDATE OF status ON public.identities
    FOR EACH ROW
    WHEN (NEW.status = 'revoked')
    EXECUTE FUNCTION public.handle_identity_revocation();

-- RPC FUNCTION: ACCEPT CHAT REQUEST ATOMICALLY
CREATE OR REPLACE FUNCTION public.accept_chat_request(p_request_id UUID)
RETURNS SETOF public.conversations
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_req public.chat_requests%ROWTYPE;
    v_my_identity_id UUID;
    v_sender public.identities%ROWTYPE;
    v_receiver public.identities%ROWTYPE;
    v_conv_expires TIMESTAMPTZ;
    v_conv public.conversations%ROWTYPE;
BEGIN
    v_my_identity_id := public.get_my_active_identity_id();
    IF v_my_identity_id IS NULL THEN
        RAISE EXCEPTION 'Active identity not found for current authenticated user';
    END IF;

    -- Fetch and lock pending chat request
    SELECT * INTO v_req
    FROM public.chat_requests
    WHERE id = p_request_id
      AND receiver_identity_id = v_my_identity_id
      AND status = 'pending'
    FOR UPDATE;

    IF v_req.id IS NULL THEN
        RAISE EXCEPTION 'Chat request is no longer pending or does not exist';
    END IF;

    -- Check sender identity is active and valid
    SELECT * INTO v_sender
    FROM public.identities
    WHERE id = v_req.sender_identity_id;

    IF v_sender.id IS NULL OR v_sender.status <> 'active' OR v_sender.expires_at <= now() THEN
        UPDATE public.chat_requests
        SET status = 'cancelled', updated_at = now()
        WHERE id = p_request_id;
        RAISE EXCEPTION 'Sender public ID has changed or expired';
    END IF;

    -- Check receiver identity is active and valid
    SELECT * INTO v_receiver
    FROM public.identities
    WHERE id = v_req.receiver_identity_id;

    IF v_receiver.id IS NULL OR v_receiver.status <> 'active' OR v_receiver.expires_at <= now() THEN
        UPDATE public.chat_requests
        SET status = 'cancelled', updated_at = now()
        WHERE id = p_request_id;
        RAISE EXCEPTION 'Receiver identity is no longer active or has expired';
    END IF;

    v_conv_expires := LEAST(v_sender.expires_at, v_receiver.expires_at);

    -- Check if active conversation already exists between participants
    SELECT * INTO v_conv
    FROM public.conversations
    WHERE ((participant_a = v_req.sender_identity_id AND participant_b = v_req.receiver_identity_id)
        OR (participant_a = v_req.receiver_identity_id AND participant_b = v_req.sender_identity_id))
      AND status = 'active'
      AND expires_at > now()
    LIMIT 1;

    IF v_conv.id IS NULL THEN
        INSERT INTO public.conversations (
            participant_a,
            participant_b,
            expires_at,
            status
        ) VALUES (
            v_req.sender_identity_id,
            v_req.receiver_identity_id,
            v_conv_expires,
            'active'
        ) RETURNING * INTO v_conv;
    END IF;

    -- Update request status
    UPDATE public.chat_requests
    SET status = 'accepted', updated_at = now()
    WHERE id = p_request_id;

    RETURN NEXT v_conv;
    RETURN;
END;
$$;

-- 4. MESSAGES TABLE
CREATE TABLE IF NOT EXISTS public.messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    sender_identity_id UUID NOT NULL REFERENCES public.identities(id) ON DELETE CASCADE,
    ciphertext TEXT NOT NULL,
    nonce TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation ON public.messages(conversation_id, created_at ASC);

-- 5. BLOCKS TABLE
CREATE TABLE IF NOT EXISTS public.blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_identity_id UUID NOT NULL REFERENCES public.identities(id) ON DELETE CASCADE,
    blocked_identity_id UUID NOT NULL REFERENCES public.identities(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(blocker_identity_id, blocked_identity_id)
);

-- ROW LEVEL SECURITY (RLS)
ALTER TABLE public.identities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.blocks ENABLE ROW LEVEL SECURITY;

-- HELPER FUNCTION: GET ACTIVE IDENTITY ID
CREATE OR REPLACE FUNCTION public.get_my_active_identity_id()
RETURNS UUID
LANGUAGE sql SECURITY DEFINER STABLE
SET search_path = public
AS $$
    SELECT id FROM public.identities
    WHERE user_id = auth.uid()
      AND status = 'active'
      AND expires_at > now()
    ORDER BY created_at DESC
    LIMIT 1;
$$;

-- RLS POLICIES: IDENTITIES
CREATE POLICY "Users can view active public IDs"
    ON public.identities FOR SELECT
    USING (status = 'active' AND expires_at > now());

DROP POLICY IF EXISTS "Users can manage their own identity" ON public.identities;
CREATE POLICY "Users can manage their own identity"
    ON public.identities FOR ALL
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

-- RLS POLICIES: CHAT REQUESTS
DROP POLICY IF EXISTS "Users can view chat requests sent to or from them" ON public.chat_requests;
CREATE POLICY "Users can view chat requests sent to or from them"
    ON public.chat_requests FOR SELECT
    USING (
        (sender_identity_id = public.get_my_active_identity_id()
         OR receiver_identity_id = public.get_my_active_identity_id())
        AND NOT EXISTS (
            SELECT 1 FROM public.blocks b
            WHERE b.blocker_identity_id = public.get_my_active_identity_id()
              AND (b.blocked_identity_id = chat_requests.sender_identity_id OR b.blocked_identity_id = chat_requests.receiver_identity_id)
        )
    );

DROP POLICY IF EXISTS "Users can send chat requests" ON public.chat_requests;
CREATE POLICY "Users can send chat requests"
    ON public.chat_requests FOR INSERT
    WITH CHECK (
        sender_identity_id = public.get_my_active_identity_id()
        AND NOT EXISTS (
            SELECT 1 FROM public.blocks b
            WHERE b.blocker_identity_id = chat_requests.receiver_identity_id
              AND b.blocked_identity_id = chat_requests.sender_identity_id
        )
    );

DROP POLICY IF EXISTS "Users can update chat requests" ON public.chat_requests;
CREATE POLICY "Users can update chat requests"
    ON public.chat_requests FOR UPDATE
    USING (
        sender_identity_id = public.get_my_active_identity_id()
        OR receiver_identity_id = public.get_my_active_identity_id()
    );

-- RLS POLICIES: BLOCKS
DROP POLICY IF EXISTS "Users can view their blocks" ON public.blocks;
CREATE POLICY "Users can view their blocks"
    ON public.blocks FOR SELECT
    USING (
        blocker_identity_id = public.get_my_active_identity_id()
        OR blocked_identity_id = public.get_my_active_identity_id()
    );

DROP POLICY IF EXISTS "Users can create blocks" ON public.blocks;
CREATE POLICY "Users can create blocks"
    ON public.blocks FOR INSERT
    WITH CHECK (blocker_identity_id = public.get_my_active_identity_id());

DROP POLICY IF EXISTS "Users can delete their blocks" ON public.blocks;
CREATE POLICY "Users can delete their blocks"
    ON public.blocks FOR DELETE
    USING (blocker_identity_id = public.get_my_active_identity_id());


-- RLS POLICIES: CONVERSATIONS
DROP POLICY IF EXISTS "Participants can view their active conversations" ON public.conversations;
DROP POLICY IF EXISTS "Participants can view their conversations" ON public.conversations;
CREATE POLICY "Participants can view their conversations"
    ON public.conversations FOR SELECT
    USING (
        participant_a = public.get_my_active_identity_id()
        OR participant_b = public.get_my_active_identity_id()
        OR participant_a IN (SELECT id FROM public.identities WHERE user_id = auth.uid())
        OR participant_b IN (SELECT id FROM public.identities WHERE user_id = auth.uid())
    );

DROP POLICY IF EXISTS "Participants can create conversations" ON public.conversations;
CREATE POLICY "Participants can create conversations"
    ON public.conversations FOR INSERT
    WITH CHECK (
        participant_a = public.get_my_active_identity_id()
        OR participant_b = public.get_my_active_identity_id()
    );

DROP POLICY IF EXISTS "Participants can update conversations" ON public.conversations;
CREATE POLICY "Participants can update conversations"
    ON public.conversations FOR UPDATE
    USING (
        participant_a = public.get_my_active_identity_id()
        OR participant_b = public.get_my_active_identity_id()
        OR participant_a IN (SELECT id FROM public.identities WHERE user_id = auth.uid())
        OR participant_b IN (SELECT id FROM public.identities WHERE user_id = auth.uid())
    );

DROP POLICY IF EXISTS "Participants can delete conversations" ON public.conversations;
CREATE POLICY "Participants can delete conversations"
    ON public.conversations FOR DELETE
    USING (
        participant_a = public.get_my_active_identity_id()
        OR participant_b = public.get_my_active_identity_id()
        OR participant_a IN (SELECT id FROM public.identities WHERE user_id = auth.uid())
        OR participant_b IN (SELECT id FROM public.identities WHERE user_id = auth.uid())
    );

-- RLS POLICIES: MESSAGES
CREATE POLICY "Participants can view messages in their conversations"
    ON public.messages FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.conversations c
            WHERE c.id = messages.conversation_id
              AND (c.participant_a = public.get_my_active_identity_id() OR c.participant_b = public.get_my_active_identity_id())
              AND c.status = 'active'
              AND c.expires_at > now()
        )
    );

CREATE POLICY "Participants can send messages to their active conversations"
    ON public.messages FOR INSERT
    WITH CHECK (
        sender_identity_id = public.get_my_active_identity_id()
        AND EXISTS (
            SELECT 1 FROM public.conversations c
            WHERE c.id = messages.conversation_id
              AND (c.participant_a = public.get_my_active_identity_id() OR c.participant_b = public.get_my_active_identity_id())
              AND c.status = 'active'
              AND c.expires_at > now()
        )
    );

-- ENABLE REALTIME
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'identities'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.identities;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'chat_requests'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.chat_requests;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'conversations'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.conversations;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'messages'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
    END IF;
END $$;

-- 6. PERMISSIONS & SCHEMA GRANTS (CRITICAL FOR POSTGREST / RLS ACCESS)
-- Ensures anon and authenticated roles have USAGE on schema public and access to tables/routines.
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL ROUTINES IN SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON ROUTINES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;


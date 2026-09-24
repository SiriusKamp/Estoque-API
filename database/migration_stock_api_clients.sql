-- Canonical migration: also kept in the Vite project's supabase/migration_stock_api_clients.sql.
-- Apply once to the existing database before starting the Spring write API.
CREATE TABLE IF NOT EXISTS public.stock_api_clients (
  id uuid PRIMARY KEY,
  stock_id uuid NOT NULL REFERENCES public.stocks(id) ON DELETE CASCADE,
  name text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 100),
  key_hash text NOT NULL UNIQUE CHECK (key_hash ~ '^[0-9a-f]{64}$'),
  created_by uuid NOT NULL REFERENCES public.users(id),
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS stock_api_clients_stock ON public.stock_api_clients(stock_id,active,id);
ALTER TABLE public.stock_api_clients ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.stock_api_clients FROM PUBLIC, anon, authenticated;
-- Then apply migration_stock_api_jdbc.sql and connect Spring as stock_api.

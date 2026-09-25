-- Apply after migration_stock_api_clients.sql and repair_product_kit_images.sql
-- (when needed). Run as the database owner, never as stock_api.
-- The login password is intentionally set out of band; see README.md.
BEGIN;

DO $migration$
BEGIN
  IF to_regclass('public.stock_api_clients') IS NULL
     OR to_regclass('public.product_kit_images') IS NULL
     OR to_regclass('public.production_runs') IS NULL
     OR to_regclass('public.product_catalog') IS NULL
     OR to_regclass('public.lot_details') IS NULL
     OR to_regclass('public.movement_details') IS NULL
     OR to_regprocedure('public.inventory_dashboard(uuid,integer)') IS NULL THEN
    RAISE EXCEPTION 'Complete o modelo de lotes, kits, imagens e clientes da API antes de configurar o usuário JDBC.';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'stock_api') THEN
    CREATE ROLE stock_api LOGIN NOINHERIT NOBYPASSRLS;
  END IF;
  IF EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'stock_api'
      AND (rolsuper OR rolbypassrls OR rolcreaterole OR rolinherit OR NOT rolcanlogin)
  ) THEN
    RAISE EXCEPTION 'stock_api deve ser LOGIN NOINHERIT sem superuser, CREATEROLE ou BYPASSRLS.';
  END IF;
END
$migration$;

GRANT USAGE ON SCHEMA public TO stock_api;

-- auth.uid() reads this same verified transaction-local claim. Supabase does
-- not grant custom JDBC roles USAGE on its managed auth schema, so keep the
-- stock ownership helper invoker-scoped and independent of that schema.
CREATE OR REPLACE FUNCTION public.owns_stock(_stock_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY INVOKER SET search_path = '' AS $function$
  SELECT EXISTS (
    SELECT 1 FROM public.stocks s
    WHERE s.id = _stock_id
      AND s.user_id = nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
  )
$function$;

-- A stock key must be checked before its owner is known. Only the server-side
-- JDBC role can call this function; no table SELECT is needed without a claim.
CREATE OR REPLACE FUNCTION public.authenticate_stock_api_client(
  p_stock_id uuid, p_key_hash text
)
RETURNS TABLE(client_id uuid, stock_owner_id uuid)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $function$
  SELECT c.id, s.user_id
  FROM public.stock_api_clients c
  JOIN public.stocks s ON s.id = c.stock_id
  WHERE c.stock_id = p_stock_id AND c.key_hash = p_key_hash AND c.active
$function$;
REVOKE ALL ON FUNCTION public.authenticate_stock_api_client(uuid,text)
  FROM PUBLIC, anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.authenticate_stock_api_client(uuid,text) TO stock_api;

-- These invoker views require grants and RLS on every underlying table.
GRANT SELECT ON public.stocks, public.product_types, public.products,
  public.competitor_prices, public.kit_products, public.suppliers,
  public.inventory_operations, public.purchases, public.stock_lots,
  public.inventory_operation_items, public.inventory_movements,
  public.product_kit_images, public.production_runs, public.measurement_units
  TO stock_api;
GRANT SELECT ON public.product_catalog, public.product_type_catalog,
  public.lot_details, public.movement_details, public.kit_capacity TO stock_api;
GRANT EXECUTE ON FUNCTION public.owns_stock(uuid),
  public.content_base(numeric,text),
  public.save_product(uuid,uuid,jsonb,jsonb,jsonb),
  public.receive_lots(uuid,text,jsonb),
  public.decrement_inventory(uuid,text,jsonb),
  public.produce_kit(uuid,text,jsonb),
  public.import_inventory_batch(uuid,text,jsonb,boolean),
  public.estimate_recipe(uuid,jsonb),
  public.estimate_kit_capacity(uuid,uuid),
  public.inventory_dashboard(uuid,integer)
  TO stock_api;

-- Spring writes stocks/types directly; all other inventory writes use
-- SECURITY DEFINER RPCs that verify auth.uid() and stock ownership.
GRANT INSERT (user_id,name), UPDATE (name), DELETE ON public.stocks TO stock_api;
GRANT INSERT (stock_id,name), UPDATE (name), DELETE ON public.product_types TO stock_api;
GRANT SELECT (id,stock_id,active),
  INSERT (id,stock_id,name,key_hash,created_by), UPDATE (active)
  ON public.stock_api_clients TO stock_api;

DROP POLICY IF EXISTS stock_api_stocks_owner ON public.stocks;
CREATE POLICY stock_api_stocks_owner ON public.stocks FOR ALL TO stock_api
  USING (user_id = (SELECT auth.uid()))
  WITH CHECK (user_id = (SELECT auth.uid()));
DROP POLICY IF EXISTS stock_api_product_types_owner ON public.product_types;
CREATE POLICY stock_api_product_types_owner ON public.product_types FOR ALL TO stock_api
  USING (public.owns_stock(stock_id)) WITH CHECK (public.owns_stock(stock_id));
DROP POLICY IF EXISTS stock_api_products_read ON public.products;
CREATE POLICY stock_api_products_read ON public.products FOR SELECT TO stock_api
  USING (public.owns_stock(stock_id));
DROP POLICY IF EXISTS stock_api_competitor_prices_read ON public.competitor_prices;
CREATE POLICY stock_api_competitor_prices_read ON public.competitor_prices FOR SELECT TO stock_api
  USING (EXISTS (SELECT 1 FROM public.products p
    WHERE p.id = product_id AND public.owns_stock(p.stock_id)));
DROP POLICY IF EXISTS stock_api_kit_products_read ON public.kit_products;
CREATE POLICY stock_api_kit_products_read ON public.kit_products FOR SELECT TO stock_api
  USING (EXISTS (SELECT 1 FROM public.products k
    WHERE k.id = kit_id AND public.owns_stock(k.stock_id)));
DROP POLICY IF EXISTS stock_api_measurement_units_read ON public.measurement_units;
CREATE POLICY stock_api_measurement_units_read ON public.measurement_units FOR SELECT TO stock_api
  USING (true);
DROP POLICY IF EXISTS stock_api_suppliers_read ON public.suppliers;
CREATE POLICY stock_api_suppliers_read ON public.suppliers FOR SELECT TO stock_api
  USING (public.owns_stock(stock_id));
DROP POLICY IF EXISTS stock_api_operations_read ON public.inventory_operations;
CREATE POLICY stock_api_operations_read ON public.inventory_operations FOR SELECT TO stock_api
  USING (public.owns_stock(stock_id));
DROP POLICY IF EXISTS stock_api_purchases_read ON public.purchases;
CREATE POLICY stock_api_purchases_read ON public.purchases FOR SELECT TO stock_api
  USING (EXISTS (SELECT 1 FROM public.inventory_operations o WHERE o.id = operation_id));
DROP POLICY IF EXISTS stock_api_lots_read ON public.stock_lots;
CREATE POLICY stock_api_lots_read ON public.stock_lots FOR SELECT TO stock_api
  USING (EXISTS (SELECT 1 FROM public.products p WHERE p.id = product_id));
DROP POLICY IF EXISTS stock_api_items_read ON public.inventory_operation_items;
CREATE POLICY stock_api_items_read ON public.inventory_operation_items FOR SELECT TO stock_api
  USING (EXISTS (SELECT 1 FROM public.inventory_operations o WHERE o.id = operation_id));
DROP POLICY IF EXISTS stock_api_movements_read ON public.inventory_movements;
CREATE POLICY stock_api_movements_read ON public.inventory_movements FOR SELECT TO stock_api
  USING (EXISTS (SELECT 1 FROM public.inventory_operation_items i WHERE i.id = item_id));
DROP POLICY IF EXISTS stock_api_kit_images_read ON public.product_kit_images;
CREATE POLICY stock_api_kit_images_read ON public.product_kit_images FOR SELECT TO stock_api
  USING (EXISTS (SELECT 1 FROM public.products p
    WHERE p.id = product_kit_id AND p.is_kit AND public.owns_stock(p.stock_id)));
DROP POLICY IF EXISTS stock_api_production_runs_read ON public.production_runs;
CREATE POLICY stock_api_production_runs_read ON public.production_runs FOR SELECT TO stock_api
  USING (EXISTS (SELECT 1 FROM public.inventory_operations o
    WHERE o.id = operation_id AND public.owns_stock(o.stock_id)));

-- The row policy protects key provisioning and revocation. Key hashes are not
-- selected by the runtime role; lookup is through the restricted function.
DROP POLICY IF EXISTS stock_api_clients_owner_read ON public.stock_api_clients;
CREATE POLICY stock_api_clients_owner_read ON public.stock_api_clients FOR SELECT TO stock_api
  USING (public.owns_stock(stock_id));
DROP POLICY IF EXISTS stock_api_clients_owner_insert ON public.stock_api_clients;
CREATE POLICY stock_api_clients_owner_insert ON public.stock_api_clients FOR INSERT TO stock_api
  WITH CHECK (created_by = auth.uid() AND public.owns_stock(stock_id));
DROP POLICY IF EXISTS stock_api_clients_owner_update ON public.stock_api_clients;
CREATE POLICY stock_api_clients_owner_update ON public.stock_api_clients FOR UPDATE TO stock_api
  USING (public.owns_stock(stock_id)) WITH CHECK (public.owns_stock(stock_id));

NOTIFY pgrst, 'reload schema';
COMMIT;

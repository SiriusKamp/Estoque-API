# Estoque API

Serviço Spring para assumir os acessos de estoque feitos diretamente pelo
frontend Vite em `C:\Users\sirius.alves\Projetos\sirius-cosmical-stock2`.
As listas, dashboard, receitas e operações de produtos/lotes possuem contratos
HTTP. O build `npm run build:api` do Vite usa essas rotas e bloqueia chamadas
diretas ao Supabase no navegador. Auth e Storage continuam serviços do Supabase,
acessados pelo Spring.

## Contratos disponíveis

As rotas do frontend exigem `Authorization: Bearer <access_token>` do Supabase Auth.
O token deve ter `sub` UUID, emissor e audiência configurados. No modo `jwks`
(padrão), o Spring verifica sua assinatura assimétrica com o JWKS do projeto.
Projetos com assinatura legada HS256, cujo JWKS contém `keys: []`, precisam de
`STOCK_JWT_MODE=remote`: o Spring consulta `GET /auth/v1/user` com `apikey` e o
Bearer recebido, compara o usuário retornado com `sub` e exige emissor,
audience `authenticated`, role `authenticated` e token não expirado. Esse modo
segue a [recomendação do Supabase](https://supabase.com/docs/guides/auth/jwts#verifying-with-a-shared-secret-signing-key)
e não exige copiar a chave secreta JWT para o Spring.

| Rota | Origem no monólito | Situação |
| --- | --- | --- |
| `GET /api/v1/stocks` | `src/hooks/useStocks.tsx` | Implementada |
| `POST/PUT/DELETE /api/v1/stocks[/{stockId}]` | `useStocks.tsx` (cadastro/edição/exclusão) | Implementada |
| `GET /api/v1/stocks/{stockId}/product-types` | `useInventoryRows('product_types')`, `ProductTypes.tsx` | Implementada |
| `POST/PUT/DELETE /api/v1/stocks/{stockId}/product-types[/{typeId}]` | `ProductTypes.tsx`, `useProductTypes.ts` | Implementada |
| `GET /api/v1/stocks/{stockId}/products` | `src/hooks/useProducts.ts#useProducts` | Implementada |
| `GET /api/v1/stocks/{stockId}/products/{productId}/competitor-prices` | `Products.tsx#ProductEditor` | Implementada |
| `GET /api/v1/stocks/{stockId}/suppliers` | `src/hooks/useProducts.ts#useSuppliers` | Implementada |
| `GET /api/v1/stocks/{stockId}/lots` | `useInventoryRows('lot_details')`, `Lots.tsx` | Implementada |
| `GET /api/v1/stocks/{stockId}/movements` | `useInventoryRows('movement_details')`, `Movements.tsx` | Implementada |
| `GET /api/v1/stocks/{stockId}/dashboard?days=7` | `Dashboard.tsx` | Implementada |
| `POST /api/v1/stocks/{stockId}/recipe-estimates` | `useRecipeEstimate` | Implementada |
| `GET /api/v1/stocks/{stockId}/kit-contents?kitIds=...` | `useRecipe`, `useKitContents` | Implementada |
| `GET /api/v1/stocks/{stockId}/kit-capacities?kitIds=...` | `useKitCapacities` | Implementada |
| `POST /api/v1/stocks/{stockId}/products` | `save_product` (produto ou kit novo) | Implementada |
| `PUT /api/v1/stocks/{stockId}/products/{productId}` | `save_product` (edição) | Implementada |
| `POST /api/v1/stocks/{stockId}/receipts` | `receive_lots` | Implementada |
| `POST /api/v1/stocks/{stockId}/productions` | `produce_kit` | Implementada |
| `POST /api/v1/stocks/{stockId}/imports/{kind}` | `import_inventory_batch` | Implementada |
| `POST /api/v1/stocks/{stockId}/decrements` | `Movements.tsx` → `decrement_inventory` | Implementada |
| `POST /api/v1/stocks/{stockId}/api-clients` | Credencial do serviço de pedidos/comandas | Implementada |
| `DELETE /api/v1/stocks/{stockId}/api-clients/{clientId}` | Revogação da credencial | Implementada |
| `POST /api/v1/integrations/stocks/{stockId}/decrements` | Baixa de pedidos/comandas; exige `X-Stock-Key` | Implementada |
| `POST /api/v1/auth/login` (`/Login`) e `/register` (`/cadastro`) | `Auth.tsx` | Implementada |
| `POST /api/v1/auth/refresh`, `/recover`, `/logout` | `useAuth.tsx`, `Auth.tsx` | Implementada |
| `GET /api/v1/auth/user`, `PUT /api/v1/auth/password` | Sessão e troca de senha | Implementada |
| `GET/POST/DELETE /api/v1/stocks/{stockId}/kit-images` | Relação e upload/remoção em Storage | Implementada |
| `GET /api/v1/stocks/{stockId}/kit-images/{kitId}/content` | Imagem privada para os cards | Implementada |

As três listas paginadas retornam `{ "rows": [], "total": 0, "page": 0, "size": 30 }`.
`page` começa em zero e `size` aceita 1 a 100. `products` aceita os filtros
`search`, `kind=all|products|kits`, `active=all|active|inactive`,
`availability=all|available|empty` e `typeId`. As buscas por nome/código usam
qualquer trecho literal, inclusive quando o usuário digita `%` ou `_`.
`lots` aceita `productId`, `balance=all|available|empty`,
`expiry=all|expired|soon`, `search`, `from` e `to`.
`movements` aceita `productId`, `kind=IN|OUT|PRODUCE`, `search`, `from` e `to`.
`from` é inclusivo e `to` exclusivo; ambos são instantes ISO-8601 com fuso,
como os valores que o Vite atualmente produz em `dateBoundary`.
`dashboard` aceita 1 a 366 dias. As rotas de kits aceitam 1 a 100 IDs por
requisição. `recipe-estimates` recebe `{ "items": [{ "productId": "uuid",
"quantity": 100, "unit": "ml" }] }`. As estimativas apenas leem o estoque;
FIFO e validação final continuam nas operações transacionais do banco.
Produtos novos recebem `{ "data": {...}, "competitorPrices": [...] | null,
"kitProducts": [...] | null }`; a edição usa o mesmo corpo. Entradas e
produções recebem `{ "reference": "...", "data": {...} }`. A importação usa
`{ "rows": [...], "updateExisting": false }` e `kind` igual a `types`,
`products`, `lots` ou `decrements`, com até 100 linhas por pedido HTTP.

## Ativação de baixas HTTP

No banco existente, aplique **uma vez** `database/migration_stock_api_clients.sql`
se ainda faltar a tabela. Depois aplique `database/migration_stock_api_jdbc.sql`
para criar a role JDBC restrita e a função de autenticação de chaves. Se
`product_kit_images` estiver ausente, execute primeiro
`supabase/repair_product_kit_images.sql` do projeto Vite e depois
`supabase/storage_kit_images.sql`. As migrações não são aplicadas
automaticamente. Não rode `schema.sql` inteiro sobre uma instalação existente.

O dono do estoque cria uma credencial com JWT enviando
`POST /api/v1/stocks/{stockId}/api-clients` e `{ "name": "Comandas" }`.
A resposta contém `id`, `name` e `key`; guarde `key` como segredo no serviço
de pedidos. Ela só é mostrada nessa resposta. Revogue-a pelo `DELETE` acima.
Cada chave vale para exatamente um estoque.

Ao concluir um pedido, o serviço envia `POST
/api/v1/integrations/stocks/{stockId}/decrements` com cabeçalho `X-Stock-Key`
e, por exemplo:

```json
{
  "reference": "pedido:7a195c:concluido",
  "reason": "Pedido 7a195c concluído",
  "items": [
    { "productId": "00000000-0000-0000-0000-000000000001", "quantity": 2, "unit": "un" },
    { "productId": "00000000-0000-0000-0000-000000000002", "quantity": 100, "unit": "ml" }
  ]
}
```

`occurredAt` é opcional e usa ISO-8601 com fuso. Se presente, mantenha o
mesmo valor nas tentativas. `reference` deve ser única por estoque e estável
para o mesmo evento. A resposta `200` contém `operationId` e `reference`;
uma repetição idêntica devolve o mesmo `operationId`. A mesma referência com
dados diferentes retorna `409`. Saldo insuficiente retorna `409` sem baixa
parcial. Após timeout, repita o mesmo corpo com a mesma referência. O serviço
de pedidos deve confirmar a conclusão somente após sucesso ou manter o
evento pendente para nova tentativa; a transação/outbox do lado de pedidos
ainda não foi implementada.

O RPC `decrement_inventory` segue como única implementação de FIFO,
conversão entre unidades e ml/g, histórico imutável e idempotência. O Spring
valida JWT ou chave, aplica a identidade do dono de forma local à transação
e chama o RPC. A origem da integração fica no JSON imutável da operação.

## Configuração

Configure no ambiente:

Em desenvolvimento, `application.properties` carrega opcionalmente o arquivo
`.env` da raiz do projeto. Ele é ignorado pelo Git. Em produção, configure as
mesmas variáveis no ambiente do serviço; não publique o arquivo local.

- `STOCK_DB_URL`, `STOCK_DB_USER=stock_api`, `STOCK_DB_PASSWORD`: conexão JDBC
  com o PostgreSQL que já possui o esquema de lotes e as views
  `product_catalog` e `product_type_catalog`. Defina uma senha forte para a
  role `stock_api` fora do repositório e guarde-a somente no ambiente do
  servidor. Essa role não pode ter `BYPASSRLS` ou privilégios de superusuário.
  Não execute `supabase/schema.sql` novamente em uma instalação existente.
- `SUPABASE_AUTH_ISSUER`: por exemplo,
  `https://<projeto>.supabase.co/auth/v1`.
- `STOCK_JWT_MODE`: use `remote` no projeto atual, pois o JWKS está vazio.
  O padrão `jwks` só serve para projetos com chave de assinatura assimétrica.
  No modo remoto, cada acesso protegido depende do Supabase Auth; token
  rejeitado recebe `401`, e falha ou limitação temporária do Auth recebe `503`.
- `SUPABASE_JWKS_URI`: necessário apenas no modo `jwks`; por exemplo,
  `https://<projeto>.supabase.co/auth/v1/.well-known/jwks.json`.
- `STOCK_API_ALLOWED_ORIGINS`: origens HTTP permitidas, separadas por vírgula;
  em desenvolvimento o padrão é `http://localhost:5173`.
- `SUPABASE_URL` e `SUPABASE_PUBLISHABLE_KEY`: endereço e chave pública do
  mesmo projeto Supabase já usado pelo Vite. A API envia login, cadastro,
  recuperação e arquivos WebP ao Auth/Storage com essa chave. Aceita a chave
  moderna `sb_publishable_*` ou a chave pública `anon` legada; a chave moderna
  vai no cabeçalho `apikey`, e o token do usuário vai em `Authorization` quando
  existe. Não use `service_role` para esses endpoints.
- `STOCK_AUTH_REDIRECT_URL`: URL fixa de confirmação e recuperação, por exemplo
  `http://localhost:5173/auth`. Cadastre-a também nos Redirect URLs do Supabase.

No Vite, `VITE_STOCK_API_URL` é a origem da API, por exemplo
`http://localhost:8080`. Sem ela, os hooks mantêm a leitura direta do
Supabase. Com ela, login, cadastro, recuperação, renovação de sessão e imagens
dos kits também passam pelo Spring. A confirmação por e-mail redireciona ao
Vite com um token no fragmento da URL; o Vite entrega esse token à API para
validação, sem consultar o Supabase diretamente. Listas, dashboard, estimativas, conteúdo/capacidade dos kits,
salvamento de produtos e kits, entrada, produção, importação e baixa manual
usam HTTP. O Supabase Auth continua sendo o provedor de identidades por trás da API.

`migration_stock_api_jdbc.sql` concede apenas as leituras/escritas e execuções
necessárias à role `stock_api`. O Spring aplica o `sub` do JWT no contexto de
cada transação antes de consultar dados protegidos por RLS. Não use `postgres`
como usuário JDBC: ele pode contornar RLS. A chave de pedidos/comandas é
verificada pela função restrita `authenticate_stock_api_client` antes de
aplicar a identidade do proprietário à baixa.

## Publicar no Render

Crie um **Web Service** a partir deste repositório e selecione **Docker** como
runtime. O `Dockerfile` usa Java 21 e empacota o JAR. O Spring escuta em
`0.0.0.0` na porta definida por `PORT` pelo Render; localmente usa 8080.
O `.dockerignore` impede que o `.env` local entre na imagem. Não configure
um comando de início adicional nem aplique `schema.sql` durante o build.

No painel de variáveis do serviço, configure `STOCK_DB_URL`,
`STOCK_DB_USER`, `STOCK_DB_PASSWORD`, `SUPABASE_URL`,
`SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_AUTH_ISSUER` e
`STOCK_JWT_MODE=remote`. Use `STOCK_API_ALLOWED_ORIGINS` com a origem exata
do frontend (por exemplo `https://seu-front.vercel.app`) e
`STOCK_AUTH_REDIRECT_URL` com essa origem seguida de `/auth`. Essas
variáveis são de **execução**; não coloque a senha do banco no Dockerfile,
no Git ou nas variáveis `VITE_` do front.

O endereço JDBC inferido no `.env` local usa a conexão direta do Supabase.
Confirme no painel **Connect** do Supabase se a hospedagem alcança esse
endereço. Se precisar do pooler de **sessão** para IPv4, copie host/porta do
painel e use `STOCK_DB_USER=stock_api.<project-ref>`. Evite o pooler de
transação para este JDBC. A role `stock_api` precisa existir no banco remoto,
com senha definida, antes do primeiro deploy funcional.

Depois que a URL `https://<servico>.onrender.com` estiver ativa, execute o
smoke HTTP documentado no projeto Vite e valide login, leitura, escrita e
imagens com um estoque de teste. Só então configure no Vercel
`VITE_STOCK_API_URL` com essa **origem**, use `npm run build:api` e publique
uma prévia do front. O corte das permissões diretas do navegador vem por
último, após validar essa prévia.

## Próximos blocos

1. Com o banco e o Spring configurados, executar os testes Maven e o smoke HTTP
   de leitura `node tests/api-smoke.mjs` do projeto Vite com uma conta de teste.
2. Validar login, cadastro com confirmação, recuperação, imagens e operações de
   estoque com o Supabase real em um estoque de teste; publicar uma prévia Vite
   com `npm run build:api` e repetir os fluxos antes do corte.
3. Depois da estabilização, avaliar a remoção das permissões PostgREST diretas
   do navegador sem interromper as políticas de Storage usadas pelo Spring.

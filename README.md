# Multitenant SaaS MCP Demo

Starter dimostrativo per un SaaS multitenant con backend Java/Spring, PostgreSQL, due dashboard React con Mantine e server MCP Streamable HTTP. I dati di dominio `WorkItem` sono esempi sostituibili.

> **Ambito:** questo repository è una demo locale, non un servizio pronto per dati reali o produzione. Il profilo di sviluppo include credenziali note e JWT HMAC; il profilo `production` richiede un issuer OAuth/OIDC esterno. Prima di un deployment servono revisione di sicurezza indipendente, test isolati, gestione operativa dei segreti, rate limiting distribuito, backup e monitoraggio.

## Funzionalità

- Tenant chiamati `Company 1` e `Company 2`, con slug `company1` e `company2`.
- Utenti standard che possono appartenere a più tenant.
- Ruoli membership `ADMIN` e `MEMBER`; il superadmin è un attributo dell'utente, non un ruolo tenant.
- Dashboard tenant per leggere e gestire i work item secondo membership e ruolo.
- Dashboard superadmin per creare tenant, utenti e membership e consultare gli elenchi.
- Server MCP con gli stessi controlli applicativi della dashboard: tenancy selezionata esplicitamente, membership verificata a ogni operazione e scrittura riservata ad `ADMIN`.
- Migrazioni schema versionate con Flyway; Hibernate in produzione esegue solo `validate`.

## Requisiti

- macOS con Homebrew, oppure installazione manuale dei requisiti.
- Java 21 o superiore.
- Maven.
- Node.js 20.19+ oppure 22.12+ e npm.
- PostgreSQL 16, avviato localmente.

Il progetto non usa Docker. Su macOS, `make setup` installa Maven, PostgreSQL 16 e Node tramite Homebrew, avvia PostgreSQL, crea il database `tenancy_platform` e installa le dipendenze dei due frontend.

```sh
make setup
```

Se i requisiti sono già installati, avvia solo PostgreSQL e crea il database:

```sh
make db-start
make db-create
npm --prefix frontend/tenant install
npm --prefix frontend/superadmin install
```

Il Makefile assume PostgreSQL locale su `localhost:5432`, database `tenancy_platform` e utente `postgres`. Si possono cambiare con `DB_HOST`, `DB_PORT`, `DB_NAME` e `DB_USER`.

## Avvio locale

Avvia i componenti in terminali separati dalla cartella del progetto:

```sh
make backend
make tenant-ui
make admin-ui
```

Indirizzi:

| Componente | Indirizzo |
| --- | --- |
| Dashboard utente | http://127.0.0.1:5173 |
| Dashboard superadmin | http://127.0.0.1:5174 |
| API e MCP | http://127.0.0.1:8080 |
| PostgreSQL | localhost:5432 |

Vite limita entrambi i frontend a `127.0.0.1`. Il backend usa la stessa interfaccia locale per default. Le dashboard usano `VITE_API_URL` se impostato, altrimenti `http://127.0.0.1:8080`.

## Credenziali demo

Tutti gli account demo usano la password `ChangeMe123!`:

| Account | Email | Accesso |
| --- | --- | --- |
| Superadmin | `root@example.test` | Dashboard superadmin |
| User 1 | `user1@email.com` | `ADMIN` in Company 1; `MEMBER` in Company 2 |
| User 2 | `user2@email.com` | `MEMBER` in Company 1 |

In modalità Vite development (`npm run dev`) il login mostra un pulsante **Entra in demo**. Il pulsante è nascosto nelle build di produzione.

Il seed viene eseguito al primo avvio locale quando `APP_SEED_ENABLED=true` (predefinito fuori dal profilo `production`). Impostare `APP_SEED_ENABLED=false` per disabilitarlo. La migrazione `V2__rename_demo_data.sql` rinomina Acme/Globex solo se riconosce anche i due account demo e le loro membership iniziali; aggiorna poi le email demo corrispondenti, lasciando invariati gli ID e i work item. Per un database già esistente Flyway applica la migrazione al successivo avvio del backend.

Le credenziali sono pubbliche e non vanno riutilizzate. Il profilo `production` disabilita il seed per default e rifiuta il login locale username/password.

## Modello e autorizzazioni

- `UserAccount`: identità globale, email, hash password e flag `superadmin`.
- `Tenancy`: organizzazione a cui appartengono dati isolati.
- `Membership`: associazione univoca tra utente e tenant con ruolo `ADMIN` o `MEMBER`.
- `WorkItem`: entità dimostrativa con `tenancy_id` obbligatorio.

Le richieste tenant-scoped REST usano `X-Tenant-Id`. Il backend non si fida del solo header: controlla che l'utente autenticato appartenga al tenant e che abbia `ADMIN` per creare, modificare o cancellare. Le query work item sono vincolate a `tenancy_id`. Il superadmin ha endpoint separati e non riceve accesso implicito ai dati business tramite MCP.

Gli endpoint `/api/superadmin/*` verificano il flag superadmin server-side. `GET /api/superadmin/memberships` restituisce membership con utente, email, tenant e ruolo; la UI usa questo endpoint per mostrare l'elenco e il modulo per crearne di nuove.

## API REST

### Autenticazione e tenant

- `POST /api/auth/login` — login locale, solo fuori da `production`.
- `GET /api/auth/me` — utente autenticato.
- `GET /api/auth/tenancies` — tenant e ruoli dell'utente autenticato.

### Work item

- `GET /api/work-items` — elenco per il tenant nell'header `X-Tenant-Id`.
- `POST /api/work-items` — crea; richiede membership `ADMIN`.
- `PUT /api/work-items/{id}` — aggiorna; richiede `ADMIN`.
- `DELETE /api/work-items/{id}` — elimina; richiede `ADMIN`.

### Superadmin

- `GET|POST /api/superadmin/tenancies` — elenco e creazione tenant.
- `GET|POST /api/superadmin/users` — elenco e creazione utenti.
- `GET /api/superadmin/memberships` — elenco membership.
- `POST /api/superadmin/memberships` — associa utente e tenant con un ruolo.

Le chiamate protette usano `Authorization: Bearer <token>`. Il login locale restituisce un JWT di sviluppo valido un'ora.

## Server MCP

Endpoint Streamable HTTP stateless: `POST /mcp`. I tool disponibili sono:

- `tenancies_list` — tenant e membership dell'utente autenticato.
- `work_items_list` — work item di un tenant cui l'utente appartiene.
- `work_items_create` — crea un elemento `OPEN`; richiede `ADMIN`.
- `work_items_update` — modifica titolo, descrizione e stato; richiede `ADMIN`.
- `work_items_delete` — elimina; richiede `ADMIN`.

Ogni tool usa i service applicativi, che applicano i controlli membership, tenancy e ruolo. Le chiamate sono registrate con nome tool, UUID utente/tenant, esito e durata; argomenti e contenuto business non sono scritti nei log di audit.

### Collegamento MCP locale

Il login locale emette JWT di sviluppo, ma il profilo MCP demo è utile per un client che non offre un flusso OAuth. Avvialo in un terminale:

```sh
export MCP_DEMO_ACCESS_KEY="$(openssl rand -hex 32)"
make backend-mcp-demo MCP_DEMO_ACCESS_KEY="$MCP_DEMO_ACCESS_KEY"
```

Avvia il proxy locale in un altro terminale con la stessa chiave:

```sh
make mcp-demo-proxy MCP_DEMO_ACCESS_KEY="$MCP_DEMO_ACCESS_KEY"
```

Il backend demo ascolta solo su `127.0.0.1:8080`; il proxy ascolta solo su `127.0.0.1:8081`, inoltra esclusivamente `POST /mcp`, limita il corpo della richiesta e verifica la chiave. Questa chiave di test è trasmessa come parametro `mcp_demo_key` nell'URL e può comparire in configurazioni o log del client: usa solo dati fittizi e non riutilizzarla.

Senza un bearer token, il profilo demo MCP agisce come `user1@email.com`: può scrivere in Company 1 (`ADMIN`) e solo leggere in Company 2 (`MEMBER`).

Non esporre il profilo `mcp-demo` a Internet. Per un test ChatGPT tramite un tunnel temporaneo serve un endpoint HTTPS raggiungibile da ChatGPT che inoltri solo al proxy locale; il connettore va configurato senza autenticazione MCP, perché il proxy verifica la chiave nell'URL. Arresta il tunnel e ruota la chiave al termine del test. Non usare questa modalità per un deployment condiviso.

### Profilo `production`

Il profilo `production` usa Spring Security Resource Server e accetta access token JWT firmati dall'issuer OAuth/OIDC configurato. Richiede:

- scope `mcp:connect`;
- audience corrispondente all'URL MCP (`MCP_RESOURCE_URI`);
- claim `user_id` con UUID dell'utente applicativo.

Il ruolo superadmin viene letto dall'utente nel database e non è preso dal token. Il progetto implementa il resource server, non l'authorization server o il flusso di login OAuth per i frontend.

L'issuer deve generare `user_id` da una mappatura attendibile e immutabile dell'identità; il progetto non implementa la registrazione o il collegamento degli account OAuth. Verificare questa policy dell'issuer prima di configurare il profilo.

Variabili richieste dal profilo `production`:

| Variabile | Scopo |
| --- | --- |
| `SERVER_ADDRESS` | Interfaccia di ascolto del backend (predefinita `0.0.0.0` nel profilo `production`; limitare l'accesso con la rete/gateway) |
| `DB_URL` | JDBC PostgreSQL |
| `DB_USER`, `DB_PASSWORD` | Credenziali database |
| `JWT_SECRET` | Segreto di firma JWT; almeno 32 byte, fornito da secret manager |
| `MCP_PUBLIC_BASE_URL` | URL pubblico HTTPS del servizio MCP |
| `MCP_RESOURCE_URI` | Audience esatta del resource server, normalmente `<MCP_PUBLIC_BASE_URL>/mcp` |
| `MCP_ISSUER_URI` | Issuer OAuth/OIDC HTTPS |
| `MCP_ALLOWED_HOSTS` | Host MCP autorizzati, con porta se non standard |
| `MCP_ALLOWED_ORIGINS` | Origini browser autorizzate per MCP |
| `CORS_ALLOWED_ORIGINS` | Origini esatte delle dashboard |
| `APP_SEED_ENABLED` | Lasciare `false` in produzione |

Esempio di avvio (valori segnaposto, da sostituire tramite secret manager e configurazione runtime):

```sh
SPRING_PROFILES_ACTIVE=production \
SERVER_ADDRESS='0.0.0.0' \
DB_URL='jdbc:postgresql://db.example.internal:5432/tenancy' \
DB_USER='tenancy_app' \
DB_PASSWORD='<secret>' \
JWT_SECRET='<secret di almeno 32 byte>' \
MCP_PUBLIC_BASE_URL='https://mcp.example.com' \
MCP_RESOURCE_URI='https://mcp.example.com/mcp' \
MCP_ISSUER_URI='https://identity.example.com' \
MCP_ALLOWED_HOSTS='mcp.example.com' \
MCP_ALLOWED_ORIGINS='https://chatgpt.com' \
CORS_ALLOWED_ORIGINS='https://app.example.com,https://admin.example.com' \
APP_SEED_ENABLED=false \
mvn spring-boot:run
```

Non inserire valori reali nei file versionati, negli argomenti salvati della shell o nei log. Lo snippet è illustrativo, non sostituisce una configurazione di deployment.

## Configurazione tecnica

- Spring Boot `4.1.1`, Java `21`, Spring AI MCP `2.0.1`.
- PostgreSQL con Flyway; in sviluppo Hibernate usa `update`, in produzione `validate`.
- JWT locale basato su secret; la configurazione locale predefinita è esclusivamente per sviluppo.
- BCrypt con costo 12 per gli hash password.
- CORS configurabile tramite origini esatte; non configurare wildcard con credenziali o token.
- Il filtro MCP verifica host e, se presente, origin per ridurre il rischio di DNS rebinding.
- `McpAuditService` omette i dati degli argomenti dai log applicativi.

## Test e build

```sh
make test
make build
```

Il target make test esegue i test backend. Il target make build esegue test e packaging Maven, poi crea le build Vite dei due frontend.

## Note di sicurezza prima di un deployment

Questo starter non deve essere collegato a dati reali senza ulteriore lavoro. In particolare:

1. integrare il flusso di autenticazione dei frontend con un identity provider OAuth/OIDC e non usare il login demo;
2. sostituire i dati demo, le password note e la chiave JWT di sviluppo;
3. imporre rate limiting e limiti di payload/concorrenza su gateway e login;
4. configurare TLS, secret manager, backup cifrati, restore testato, retention e monitoraggio;
5. verificare versioni e vulnerabilità delle dipendenze con strumenti aggiornati nel processo CI;
6. completare test isolati di autorizzazione cross-tenant, ruoli, flussi MCP e migrazioni; la suite attuale include solo test unitari iniziali e non sostituisce questi test d'integrazione;
7. rivedere gestione account, revoca delle sessioni, recupero password, audit, logging e requisiti privacy;
8. proteggere il database con un account a privilegi minimi, rete privata e policy operative;
9. non esporre il profilo `mcp-demo` o l'URL con chiave temporanea su un ambiente condiviso.

Una configurazione OAuth, TLS o proxy dichiarata nel README non dimostra che il deployment reale sia correttamente impostato. Verificare ogni controllo nell'ambiente che ospita il servizio.

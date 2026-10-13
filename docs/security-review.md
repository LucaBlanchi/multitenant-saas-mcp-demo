# Revisione di sicurezza

Data: 13 ottobre 2026. Ambito: sorgenti della demo, base `f4c7b26` e modifiche di sicurezza incluse in questo commit.

## Metodo ed esito

Revisione secondo la [skill ufficiale Cloudflare Security Audit](https://github.com/cloudflare/security-audit-skill), profilo standard: ricognizione, revisione delle superfici, verifica indipendente dei candidati e controllo finale della copertura. Non è una certificazione rilasciata da Cloudflare.

La revisione copre 16 unità relative ad autenticazione, OAuth, MCP, autorizzazione dei servizi, persistenza, frontend, proxy e configurazione della demo. Nel codice finale non sono rimasti candidati aperti o vulnerabilità confermate dalla revisione. Due ipotesi sul codice precedente sono state ricontrollate indipendentemente dopo le correzioni e respinte per il codice attuale.

L'audit è una revisione dei sorgenti: non ha interrogato servizi, database, tunnel o account esterni. L'ambiente disponibile non garantiva tutti i limiti OS su rete, disco e processi richiesti dalla skill. Con eccezione esplicitamente autorizzata dall'utente sono stati eseguiti separatamente i test backend offline con mock; non sono presentati come riproduzioni indipendenti dell'audit.

## Accesso personale e isolamento

- OAuth lega login e refresh all'UUID immutabile dell'utente. Il client non sceglie identità o privilegi tramite argomenti dei tool.
- I token richiedono firma valida, issuer, scadenza, subject e audience della risorsa. MCP richiede `mcp:connect`; le API REST in produzione richiedono `api:access`.
- Utente e flag superadmin vengono riletti dal database. Account rimossi non vengono autenticati.
- REST e MCP usano gli stessi servizi: membership corrente per ogni operazione, lettura per MEMBER, scrittura per ADMIN. Superadmin non scavalca la membership.
- Aggiornamenti e cancellazioni cercano l'oggetto insieme al tenant. Cambiare tenant o ID non concede accesso a oggetti esterni.
- OAuth usa PKCE, consenso e callback verificati. Il server locale emette token per una sola risorsa e rifiuta target espliciti diversi.

## Correzioni aggiuntive

- Recupero CIMD fuori dal monitor condiviso, con URL ammessi rigorosamente, timeout, limite alla risposta, cache limitata e massimo quattro richieste simultanee.
- Le dashboard invalidano il contesto precedente a logout o cambio azienda e scartano risposte tardive; svuotano i dati privati precedenti.
- Proxy demo limitato a backend loopback e percorsi OAuth/MCP, con limiti a connessioni, durata e dimensioni dei messaggi, gestione degli abort e intestazioni di sicurezza.
- JWT di sviluppo senza privilegi memorizzati nel token; i privilegi correnti vengono caricati dal database. Il profilo di sviluppo ordinario non accetta MCP.

## Verifiche eseguite

`mvn -o test`: **55 test, zero errori, zero fallimenti, zero test saltati**. Repository e decoder sono mock; MockMvc esegue la catena di filtri in memoria, senza server di rete, PostgreSQL o tunnel.

I casi includono accesso anonimo, token invalidi, claim obbligatori, scope, identità cancellate, cambio account, revoca della membership, lettura MEMBER, scritture negate ai MEMBER, scrittura ADMIN, oggetti di altri tenant, assenza di bypass superadmin, binding OAuth all'UUID, resource parameter e validazione CIMD.

Controllati inoltre whitespace del diff, sintassi del proxy e assenza nei file pubblicati di chiavi private, token comuni e URL temporanei del tunnel. Questi controlli non sostituiscono una scansione completa dei segreti o delle dipendenze.

## Limiti e lavoro prima della produzione

- `oauth-local` e Quick Tunnel sono strumenti temporanei di prova: chiavi, client e autorizzazioni sono in memoria. Non costituiscono l'infrastruttura OAuth di produzione.
- In cloud vanno verificati issuer e mapping degli account, persistenza delle autorizzazioni, gestione e revoca dei token, TLS, ingress, rate limiting, database e backup.
- Questa revisione non dimostra il comportamento end-to-end di ChatGPT, proxy, browser e PostgreSQL, né verifica advisory aggiornati delle dipendenze.
- Gli elenchi sono ancora privi di paginazione e alcuni conteggi fanno query per membership: prima di scalare servono limiti e query aggregate.
- Una revoca impedisce le richieste successive; non annulla una scrittura già accettata. Il logout delle dashboard scarta risposte tardive, ma non revoca da solo tutti i token OAuth.
- I frontend di sviluppo conservano il bearer in localStorage. Per il frontend cloud va definita e verificata la gestione delle sessioni e dei token.

La demo è pubblicabile con questi limiti dichiarati. L'assenza di problemi confermati nell'ambito esaminato non garantisce assenza di vulnerabilità né prontezza per la produzione.

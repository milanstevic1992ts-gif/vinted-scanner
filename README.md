# Vinted Scanner

APK Android standalone per raccogliere annunci condivisi dal telefono, confrontarli, stimare il valore osservato e individuare possibili occasioni senza dipendere da un server esterno.

## Stato attuale

Versione applicazione: **0.6.0**

### Fase 1 — Fondazioni
- [x] progetto Android nativo Kotlin + Jetpack Compose
- [x] SQLite locale
- [x] dashboard
- [x] ricerche salvate
- [x] WorkManager periodico
- [x] scoring locale
- [x] importazione annunci via Condividi Android
- [ ] connettore remoto autorizzato

### Fase 2 — Scanner
- [x] creazione/modifica/eliminazione ricerca
- [x] pausa/riattivazione ricerca
- [x] filtri prezzo/taglia/marca/condizione
- [x] apertura ricerca su Vinted
- [x] deduplicazione annunci
- [x] storico prezzi locale
- [x] notifiche occasione
- [x] watchlist/preferiti

### Fase 3 — Intelligence
- [x] tokenizzazione locale di titoli e note
- [x] confronto per similarità
- [x] mediana robusta dei prezzi comparabili
- [x] numero campioni e confidenza della stima
- [x] margine netto stimato con spese extra
- [x] rilevazione parole/segnali di rischio
- [x] apprendimento locale da preferiti
- [x] feedback negativo "Non mi interessa"
- [x] boost/malus personale trasparente
- [x] test automatici del motore Intelligence

### Fase 4 — Premium UX
- [x] dashboard avanzata
- [x] grafico storico prezzi
- [x] archivio con filtri Tutti / Watch / Scartati
- [x] modalità Live foreground con notifica persistente
- [x] backup JSON locale con selettore file Android
- [x] tema premium chiaro/scuro
- [x] icona app dedicata
- [x] rifinitura card, score, confidenza e margine

### Fase 5 — Motore sorgenti annunci
- [x] interfaccia `ListingSource` separata dal motore Intelligence
- [x] catalogo sorgenti
- [x] Condivisione Android tracciata come sorgente reale
- [x] predisposizione sorgente remota autorizzata
- [x] diagnostica persistente SQLite
- [x] stato Pronta / In attesa / Scansione / Non configurata / Errore / Disattivata
- [x] ultimo evento, ultimo scan e ultimo successo
- [x] conteggio ultimo giro e totale ricevuti
- [x] memorizzazione ultimo errore
- [x] possibilità di disattivare una sorgente automatica
- [x] pannello Diagnostica Sorgenti nella UI
- [x] test automatici del catalogo sorgenti

### Fase 6 — Comparabili avanzati
- [x] firma prodotto strutturata
- [x] riconoscimento marca
- [x] riconoscimento modello tramite token significativi
- [x] riconoscimento taglia esplicita
- [x] classificazione categoria prodotto
- [x] normalizzazione condizione
- [x] esclusione marche incompatibili
- [x] esclusione categorie incompatibili
- [x] esclusione taglie note incompatibili
- [x] similarità strutturata + similarità testuale
- [x] filtro robusto prezzi anomali con MAD
- [x] mediana calcolata solo sui comparabili utilizzabili
- [x] similarità media dei comparabili
- [x] conteggio outlier rimossi
- [x] firma comparabile visibile nelle card
- [x] migrazione SQLite v5
- [x] backup aggiornato con qualità comparabili
- [x] test marca / taglia / categoria / outlier

## Come funziona l'Intelligence locale

Vinted Scanner non inventa un prezzo "AI". Usa gli annunci presenti nel database locale:

1. normalizza il titolo;
2. costruisce una firma prodotto con marca, modello, taglia, categoria e condizione;
3. elimina comparabili incompatibili per marca/categoria/taglia nota;
4. combina similarità strutturata e testuale;
5. rimuove prezzi anomali con filtro robusto MAD;
6. usa la mediana dei comparabili rimasti;
7. mostra numero confronti, similarità media, outlier rimossi e confidenza 0–100;
8. sottrae prezzo di acquisto e spese extra;
9. applica rischi testuali e preferenze personali allo score.

Il cuore insegna un segnale positivo. **Non mi interessa** insegna un segnale negativo. I pesi restano nel database locale del telefono.

## Sorgenti annunci

Il core usa l'interfaccia `ListingSource`, quindi UI, archivio, Intelligence e notifiche non dipendono da uno specifico provider.

Sorgenti presenti nella 0.5.0:
- **Condivisione Android**: attiva e realmente utilizzabile;
- **Sorgente remota autorizzata**: architettura pronta, ma intenzionalmente marcata `Non configurata` finché non viene collegato un accesso autorizzato reale.

Il pannello Diagnostica Sorgenti mostra stato, ultimo evento, ultimo scan, ultimo successo, quantità ricevute ed eventuali errori.

Il progetto non include bypass di login, anti-bot, automazioni di acquisto o meccanismi per aggirare protezioni della piattaforma.

## Stack

- Kotlin / AGP 9 built-in Kotlin
- Jetpack Compose + Material 3
- SQLite
- WorkManager
- Android 7.0+ (minSdk 24)
- compileSdk / targetSdk 37
- Gradle 9.6
- Compose BOM 2026.09.00

## Modalità Live

La modalità Live è avviata solo dall'utente e resta visibile tramite una notifica persistente. Il servizio esegue il ciclo dello scanner ogni 60 secondi usando esclusivamente il connettore configurato nell'app. Se non è presente una sorgente remota autorizzata, non inventa dati né aggira protezioni della piattaforma.

## Backup

Dalla Home è possibile esportare un file JSON versionato con ricerche, archivio e preferenze locali. Il file viene salvato nella posizione scelta dall'utente tramite il selettore documenti Android.

## CI

GitHub Actions esegue:
- test unitari;
- build `assembleDebug`;
- upload dell'APK debug come artifact;
- stato commit `ci/android`.

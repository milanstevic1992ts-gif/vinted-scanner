# Vinted Scanner

APK Android standalone per raccogliere annunci condivisi dal telefono, confrontarli, stimare il valore osservato e individuare possibili occasioni senza dipendere da un server esterno.

## Stato attuale

Versione applicazione: **0.3.0**

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
- [ ] dashboard avanzata
- [ ] grafici prezzo
- [ ] pagina archivio/feedback
- [ ] modalità live foreground
- [ ] backup/esportazione locale
- [ ] rifinitura grafica premium

## Come funziona l'Intelligence locale

Vinted Scanner non inventa un prezzo "AI". Usa gli annunci presenti nel database locale:

1. normalizza il titolo;
2. cerca annunci con termini sufficientemente simili;
3. usa una mediana dei prezzi comparabili;
4. mostra quanti confronti sono stati usati;
5. calcola una confidenza 0–100;
6. sottrae prezzo di acquisto e spese extra;
7. applica rischi testuali e preferenze personali allo score.

Il cuore insegna un segnale positivo. **Non mi interessa** insegna un segnale negativo. I pesi restano nel database locale del telefono.

## Sorgenti annunci

Il core usa l'interfaccia `ListingSource` per evitare di legare l'app a un endpoint fragile.

Sono previsti:
- importazione manuale/condivisione da Android;
- sorgenti autorizzate dall'utente;
- Vinted Pro Integrations quando l'account è abilitato.

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

## CI

GitHub Actions esegue:
- test unitari;
- build `assembleDebug`;
- upload dell'APK debug come artifact;
- stato commit `ci/android`.

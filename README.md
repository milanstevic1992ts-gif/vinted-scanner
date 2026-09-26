# Vinted Scanner

Android-first scanner per monitorare ricerche salvate, confrontare annunci e individuare rapidamente possibili occasioni.

## Obiettivo

- APK standalone: nessun server Debian necessario
- dati e storico salvati localmente sul telefono
- scansioni periodiche tramite WorkManager
- modalità live progettata separatamente
- apertura dell'annuncio nell'app/sito Vinted
- motore di scoring trasparente: prezzo, freschezza, condizioni, margine stimato e segnali di rischio

> Il progetto non include credenziali, bypass, automazioni di acquisto o meccanismi per aggirare limiti della piattaforma.

## Stack

- Kotlin
- Jetpack Compose
- Material 3
- SQLite locale
- WorkManager
- Android 7.0+ (minSdk 24)

## Roadmap

### Fase 1 — Fondazioni
- [x] struttura Android
- [x] dashboard Compose
- [x] modello ricerche
- [x] archivio locale
- [x] scoring locale
- [x] worker periodico
- [ ] connettore sorgente annunci

### Fase 2 — Scanner
- [ ] creazione/modifica ricerca
- [ ] filtri prezzo/taglia/marca/condizione
- [ ] deduplicazione annunci
- [ ] storico prezzi
- [ ] notifiche occasione

### Fase 3 — Intelligence
- [ ] stima prezzo osservato
- [ ] margine netto stimato
- [ ] rilevazione parole di rischio nella descrizione
- [ ] preferenze per categorie e brand
- [ ] apprendimento locale dalle decisioni dell'utente

### Fase 4 — Premium UX
- [ ] dashboard avanzata
- [ ] grafici prezzo
- [ ] watchlist
- [ ] modalità live foreground
- [ ] backup/esportazione locale

## Build

Il progetto usa AGP 9.4, Gradle 9.6 e Compose BOM 2026.09.00.

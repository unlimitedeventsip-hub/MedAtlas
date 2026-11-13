# MedAtlas Companion (laptop) — prototip offline

**Acesta este un proiect web/PWA separat de APK-ul Android**, construit pe
aceleași concepte și câmpuri JSON pentru notițe. Codul și interfața sunt
originale MedAtlas, nu cod sau elemente copiate din Nebo.

## Lansare pe Windows, macOS sau Linux

1. Deschide terminalul în folderul `desktop`.
2. Pornește un server static local: `python -m http.server 8765`
   (pe Windows funcționează și `py -m http.server 8765`).
3. Deschide `http://localhost:8765` în Microsoft Edge sau Chrome.
4. În browser alege **Install this site as an app** / **Instalează aplicația**
   dacă opțiunea este disponibilă. Altfel, funcționează normal într-o filă.
5. După prima încărcare, fișierele aplicației sunt memorate prin service worker.
   Documentele și notițele sunt stocate local în IndexedDB, în acel browser.

**Nu folosi `file://`** pentru a deschide direct `index.html`: service worker
și unele funcții PWA necesită `localhost` sau HTTPS.

## Funcții actuale

- Bibliotecă locală offline, căutare, PDF în cititorul browserului.
- Import TXT/DOCX/PPTX cu extragere de text de bază; aspectul Office **nu
  este reprodus fidel**. Documentele originale sunt păstrate ca fișiere locale
  în IndexedDB (în limitele stocării browserului).
- Notițe text editabile, autosave și căutare.
- Foaie de scriere vectorială cu pix, creion, pensulă, marker și radieră;
  suport pentru mouse, deget și stylus (presiune când browserul o oferă), Undo/Redo.
- Export / import `notes.json` cu câmpuri compatibile cu
  `StudyNote` Android: `id`, `documentId`, `page`, `heading`,
  `body`, `course`, `paper`, `createdAt`, `updatedAt` etc.,
  inclusiv `strokes` în formatul vectorial MedAtlas (`tool`, `color`,
  `width`, `points`).
- La import, un ID existent cu text diferit devine **copie separată**,
  evitând suprascrieri automate.

## Limitări esențiale

- Nu există încă sincronizare automată între Android și laptop.
- Exportul/importul manual transferă **textul și traseele vectoriale**;
  nu transferă imaginile raster, stickerele sau documentele originale.
- Android poate importa/exporta acest fișier din butonul de transfer din
  Bibliotecă. Nu introduce manual fișiere în directoarele private ale aplicației.
- Stocarea IndexedDB aparține profilului browserului și poate fi ștearsă de
  browser sau utilizator. Exportă periodic notițele în JSON și păstrează o copie.
- Instalarea PWA depinde de browser, suportul manifestului și politicile
  locale ale sistemului de operare. Nu este încă un `.exe` / `.dmg` nativ.

## Protecția datelor

Nu sunt folosite analytics, reclame, conturi, API-uri cloud sau permisiuni
de cameră. PDF-urile sunt redate local. Datele rămân în IndexedDB până la
ștergere/transfer explicit.

## Testare

Verifică: import PDF, introducere note, autosave după refresh, import/export,
duplicarea conflictelor, vizualizare Word/PPTX simplă, relansare offline și
instalarea din browser. Deocamdată nu este o versiune de producție validată.

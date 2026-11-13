# MedAtlas v2 — audit de calitate si plan de lucru

Actualizat: 13 noiembrie 2026. Statusuri bazate pe cod si pe Android CI;
nu substituie testele reale de pe tableta Lenovo.

## Remedieri v2.0.1

- [x] Salvare atomica pentru notite, flashcarduri, documente, trasee ink,
  stickere, comentarii, casete text si text OCR: fisierul vechi complet ramane
  disponibil daca aplicatia se opreste in timpul scrierii.
- [x] Filtrare fisiere temporare si auxiliare in backupul ZIP.
- [x] Verificarea numelor interne ale documentelor si identificatorilor; un
  backup cu metadate de tip path traversal este refuzat inainte de restaurare.
- [x] Previzualizarea DOCX / PPTX nu mai reincarca documentul la fiecare
  recompozitie; pozitia si zoom-ul nu se reseteaza inutil.
- [x] Inchidere explicita WebView cand utilizatorul paraseste cititorul Office.
- [x] Teste Android pentru salvare intrerupta si backup ostil.
- [ ] Confirmare CI pe ramura v2.0.1.
- [ ] Instalare actualizare reala peste v2.0.0 pe Lenovo, fara pierderea datelor.

## Probleme de completat in urmatoarele iteratii

| Prioritate | Zona | Situatie actuala | Criteriu de acceptare |
|---|---|---|---|
| P0 | Protejarea datelor | Backup ZIP exista; restaurarea este protejata pentru erori obisnuite, dar nu tranzactionala la oprire brusca | Restaurare rezistenta la oprirea procesului, jurnal de tranzactie si recuperare la pornire |
| P0 | Testare Lenovo | Android CI verde; teste fizice de stylus, 3D, OAuth si Drive lipsesc | Checklist executat integral si raport de probleme rezolvate |
| P1 | Google Drive | Backup unidirectional si restaurare manuala; fara sync de editari | Sincronizare bidirectionala cu detectie conflicte, export reversibil si testare multi-device |
| P1 | Nadia AI | Cautare si raspunsuri din documente; ancorarea la sursa si pagina este partiala | Raspunsuri cu citari verificabile si comportament explicit cand lipsesc sursele |
| P1 | Office | DOCX/PPTX local in aplicatie, randare simplificata | Paginare si geometrie imbunatatite; masurarea fidelitatii pe documente reale |
| P1 | Classroom | Refresh la pornire dupa acord; cache offline | Test OAuth pe cont real si verificarea reautorizarii expirate |
| P1 | UI | Liquid Glass comun, dar exista controale secundare duplicate in unele module | Audit complet pe tableta, fara doua actiuni pentru acelasi lucru |
| P2 | Biblioteca | Catalog de resurse si import; pachetul complet de >100 manuale nu este furnizat | Inventar licente, descarcare in limite legale si actualizari verificabile |
| P2 | Anatomie/clinic | Atlas 3D si cazuri virtuale; validare clinica partiala | Revizie medicala a structurilor, diagnosticelor si tratamentelor |
| P2 | Performanta | APK mare cu atlas 3D offline | Masurare RAM, timp pornire, dimensiune stocare si utilizare cu fisiere mari |

## Reguli de design / acceptare

- Un singur ecran de Biblioteca; fara dublarea comenzilor de import/backup.
- Mod implicit Citire pentru cursuri; schimbarea in Editare e explicita.
- Instrumentele avansate apar doar cand se folosesc, fara a aglomera pagina.
- Orice functie cloud trebuie sa functioneze predictibil offline sau sa arate
  o eroare inteligibila; fara ferestre de autorizare surpriza.
- Niciun fisier / notita nu se sterge automat fara confirmare, cu exceptia
  rotatiei explicite a copiilor automate doar in folderul ales.
- CI obligatoriu: build debug, certificat Classroom, teste Android, APK release
  semnat; testele fizice Lenovo sunt criteriu separat de acceptare.

## Testare practica initiala

1. Exporta backup ZIP, verifica existenta lui, **nu dezinstala** v2.0.0.
2. Instaleaza MedAtlas-v2.0.1.apk peste aplicatia existenta.
3. Confirma ca notitele, documentele si dosarele exista.
4. Creeaza o notita, schimba pix/creion/marker si culoare, inchide si redeschide.
5. Deschide DOCX si deruleaza; schimba o optiune de interfata si verifica sa
   nu revina la inceput. Deschide apoi PPTX si schimba slide-urile.
6. Verifica Citire vs Editare, Classroom offline, backup Drive si atlasul 3D.
7. Raporteaza ecranul, pasii, rezultatul asteptat/observat si versiunea Android.

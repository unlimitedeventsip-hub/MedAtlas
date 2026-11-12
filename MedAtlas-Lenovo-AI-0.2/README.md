# MedAtlas ♡ — Nadia's Medical Workspace

**Versiune sursă 0.2 — Android nativ, romantic premium**, pentru Lenovo Idea Tab Plus cu stylus, vertical + orizontal.

## Implementat în sursă

- Intro ECG cu inimă și mesajul „Nadia, te iubesc”.
- Biblioteca de cursuri PDF, DOCX, PPTX cu import local. Word/PPTX sunt afișate ca text extras, nu în aspectul original.
- Cititor PDF și adnotări pen: pix, highlight, sublinieri, cercuri, forme, săgeți, Lasso, radieră, zoom, undo/redo, save automat, export PDF adnotat.
- Încercuiește selecție, deschide ChatGPT Plus prin partajare, web search, notițe.
- Modul AI integrat configurabil: explicații în aplicație, rezumate, grile, întrebări libere, surse web, desen medical PNG inserat ca sticker. **Necesită backend propriu cu OpenAI API și cheie facturată separat.**
- OCR local, reguli offline de prioritizare și sublinieri AI propuse (confirmate înainte de aplicare), rezumat extractiv, quiz offline și flashcarduri.
- Trei stickers medicale de exemplu create pentru proiect. Imaginile AI se pot adăuga la pagină.
- Fără reclame. Documentele rămân pe tabletă până la o operație de partajare sau cerere explicită către AI.

## Compatibilitate și limitări

Acesta este un **proiect sursă, nu un APK compilat/verificat**. Compilarea necesită GitHub Actions/Android SDK. `backend/worker.mjs` a trecut testele Node în această sesiune. Modelul pentru imagini folosit acum este `gpt-image-2.5-flare` (modelul mai vechi `gpt-image-1` nu mai este disponibil). Schema medicală generată de AI trebuie verificată.

Modul AI poate primi numai textul OCR indexat sau selecția trimisă explicit, nu citește în mod automat toate paginile. Pentru studiu pe întregul curs, indexează în loturi cu OCR. Nu există încă evaluare clinică validată a răspunsurilor sau editor Word/PPTX fidel. Nu încărca informații identificabile despre pacienți.

## Cum compilezi

**Windows:** citește `INSTALARE-WINDOWS.md` și rulează `PUBLICA-GITHUB.ps1`. Repository-ul privat GitHub va compila cu `.github/workflows/android.yml`. După build, descarcă artifactul `MedAtlas-Lenovo-debug-apk`.

**Backend AI:** citește `backend/README.md`. Nu include `OPENAI_API_KEY` în sursă sau în aplicația Android.

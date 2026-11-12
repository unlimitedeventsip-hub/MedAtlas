# MedAtlas Nadia — Android Beta 0.5

Aplicație de studiu medical pentru tableta Lenovo, construită cu Kotlin + Jetpack Compose. Aplicația este un prototip educațional, **nu un dispozitiv medical**. Codul este în subfolderul `MedAtlas-Lenovo-AI-0.2/`; istoricul folderului a fost păstrat pentru compatibilitatea workflow-ului.

## Implementat în cod și compilat prin GitHub Actions

- Lansare directă în bibliotecă fără ecran introductiv sau animație. Mod portret/peisaj.
- Biblioteca PDF/DOCX/PPTX; Word/PowerPoint sunt redate ca text extras, nu layout original.
- Cititor PDF cu scroll continuu, zoom și pix, marker, smart underline, forme, Lasso, radieră.
- OCR local, text extras editabil, quiz și rezumate offline.
- Text tastat poziționabil pe PDF, cu editare, mutare, culoare, dimensiune, export PDF.
- Comentarii ancorate de pagină, stickere și bibliografie link/sursă/licență.
- Import imagine cu decupare dreptunghiulară; atlas cu căutare externă după termeni.
- Nadia AI: partajare către ChatGPT Plus; client pentru backend OpenAI (backend neconfigurat implicit).
- Carnet de curs pe materii: note căutabile și editabile, asociere cu documente, desen cu pen-ul păstrat ca imagine, dictare vocală prin serviciul Android și OCR opțional pentru manuscris.
- Studiu: flashcarduri cu repetare spațiată; Spital Virtual cu 6 cazuri fictive interactive și voce TTS.
- Laborator medical: fișe introductive și quiz-uri pentru ECG, radiografie, CT, monitor, instrumente și farmacologie.
- Anatomie 3D: fereastră cu atlase *externe* și posibilitatea de a le deschide în browser; modelele NU sunt incluse offline.
- Classroom: activitate Android separată, OAuth Google, sincronizare cursuri/materiale/teme, import opțional PDF/DOCX/PPTX din Drive cu limite de mărime. Necesită configurarea Google Cloud, client OAuth Android, SHA-1 stabil și permisiuni; vezi `CLASSROOM-CONFIGURARE.md`.
- Backup/restore ZIP local și export PDF adnotat.

## Limitări esențiale

- Google Classroom OAuth **nu este funcțional pentru un utilizator real până la configurarea Google Cloud de către proprietar și testarea autorizării**.
- Importul Classroom este disponibil în timpul sincronizării la deschiderea ferestrei, nu automat în fundal cu aplicația închisă.
- Abonamentul ChatGPT Plus nu finanțează OpenAI API; pentru AI direct în MedAtlas este necesar backend securizat și API billing separat.
- 3D-ul este găzduit de furnizorii externi, presupune internet; nu folosim modelele drept instrument de diagnostic.
- Modelele medicale, imaginile, simulările, diagnosticele și conduita din joc nu sunt validate ca ghiduri clinice.
- OCR pentru scris de mână este experimental; trebuie corectat de utilizator.
- Nu există încă atlas 3D nativ offline, sute de cazuri generate AI, evaluare vocală liberă cu pacient, editor Office fidel sau căutare completă semantică.
- Nu încărca date identificabile despre pacienți.

## Build

GitHub → Actions → `Build MedAtlas Android APK` → ultimele builduri verzi → Artifacts → `MedAtlas-Lenovo-debug-apk`.

Înainte de a dezinstala versiunea veche, exportă un backup ZIP din MedAtlas → Setări.

## Instalare & securitate

Citește `INSTALARE-WINDOWS.md`, `CLASSROOM-CONFIGURARE.md` și `backend/README.md`.
Nu include niciodată `OPENAI_API_KEY`, parole sau fișiere `.jks` în repository. Pentru un APK semnat stabil, folosește GitHub Secrets.

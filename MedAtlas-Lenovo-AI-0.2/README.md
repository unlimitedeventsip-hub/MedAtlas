# MedAtlas Nadia — Android Beta 0.7

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
- Studiu: flashcarduri cu repetare spațiată; Spital Virtual cu 6 cazuri acute și 94 de pacienți ambulatorii fictivi în 10 profiluri. Fiecare are fișe salvate local pentru consultație, ziua 7 și luna 3. Răspunsul la grilă are corectare automată; textul liber se compară cu explicația și conduita de referință.
- Laborator medical: 12 buletine fictive cu întrebări de interpretare și explicații, plus fișe introductive și quiz-uri pentru ECG, radiografie, CT, monitor, instrumente și farmacologie.
- Anatomie 3D: fereastră cu atlase *externe* și posibilitatea de a le deschide în browser; modelele NU sunt incluse offline.
- Enciclopedia organelor: index filtrabil și căutabil cu structuri din 11 sisteme, localizare și funcție pentru fiecare dintre cele 199 de structuri distincte, exemple de investigații unde sunt disponibile și acces la manualul OpenStax.
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


## Continuarea dezvoltării

- [ ] Verificare editorială medicală pentru cazuri, grile și conținut anatomic.
- [ ] Extindere la investigații și traiectorii dependente de deciziile studentului. Evoluțiile actuale sunt scenarii prestabilite.
- [ ] Validare pe tableta Lenovo: tastatură, rotație, TTS, reluare după închiderea aplicației și actualizare peste versiunea instalată.
- [ ] Extinderea atlasului cu histologie, vascularizație, inervație și ilustrații documentate pentru fiecare structură.
- [ ] Testarea Classroom pe contul destinat, cu aplicația semnată și OAuth configurat.
- [ ] Animația personalizată pentru Nadia și verificarea celorlalte cerințe de prezentare pe dispozitiv.

### Resurse pentru studiu

Grilele offline sunt exerciții originale. Laboratorul include acces online la [exemplele oficiale USMLE](https://www.usmle.org/exam-resources/step-1-materials/step-1-sample-test-questions).
Pentru aprofundare: [feritină](https://medlineplus.gov/lab-tests/ferritin-blood-test/), [HbA1c](https://medlineplus.gov/lab-tests/hemoglobin-a1c-hba1c-test/) și [anatomie renală microscopică](https://openstax.org/books/anatomy-and-physiology-2e/pages/25-4-microscopic-anatomy-of-the-kidney).

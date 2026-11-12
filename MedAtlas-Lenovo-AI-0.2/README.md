# MedAtlas Nadia — Android Beta 0.8

Aplicație de studiu medical pentru tableta Lenovo, construită cu Kotlin + Jetpack Compose. Aplicația este un prototip educațional, **nu un dispozitiv medical**. Codul este în subfolderul `MedAtlas-Lenovo-AI-0.2/`; istoricul folderului a fost păstrat pentru compatibilitatea workflow-ului.

## Implementat în cod și compilat prin GitHub Actions

- Intro nativ elegant pentru Nadia, cu inimă vectorială fină și mesaje personale; poate fi dezactivat sau reactivat din Setări. Mod portret/peisaj.
- Biblioteca PDF/DOCX/PPTX; Word/PowerPoint sunt redate ca text extras, nu layout original.
- Cititor PDF cu scroll continuu, zoom și pix, marker, smart underline, forme, Lasso, radieră.
- OCR local, text extras editabil, quiz și rezumate offline.
- Text tastat poziționabil pe PDF, cu editare, mutare, culoare, dimensiune, export PDF.
- Comentarii ancorate de pagină, stickere și bibliografie link/sursă/licență.
- Import imagine cu decupare dreptunghiulară; atlas cu căutare externă după termeni.
- Nadia AI: partajare către ChatGPT Plus; client pentru backend OpenAI (backend neconfigurat implicit).
- Carnet de curs pe materii: note căutabile și editabile, asociere cu documente, desen cu pen-ul păstrat ca imagine, dictare vocală prin serviciul Android și OCR opțional pentru manuscris.
- Studiu: flashcarduri cu repetare spațiată; Spital Virtual cu 6 cazuri acute și 94 de pacienți ambulatorii fictivi în 10 profiluri. Fiecare are fișe salvate local pentru consultație, ziua 7 și luna 3. Conduita aleasă schimbă scenariul educațional de urmărire, cu opțiuni sigur/nesigur; nu este un model fiziologic. Răspunsurile la grile sunt corectate automat, iar textul liber rămâne pentru comparație și autoevaluare.
- Laborator medical: 24 de buletine fictive cu interpretare și explicații; fișe introductive ECG, radiografie, CT, monitor, instrumente și farmacologie. Simulare examen offline cu selecție aleatorie din 40 de grile originale (laborator + reevaluare clinică), punctaj și explicații.
- Anatomie 3D: fereastră cu atlase *externe* și posibilitatea de a le deschide în browser; modelele NU sunt incluse offline.
- Enciclopedia organelor: index filtrabil și căutabil cu structuri din 11 sisteme, localizare și funcție pentru cele 199 de structuri distincte, trei planșe istorice offline (inimă, plămân, rinichi), plus 11 scheme originale vectoriale offline (stilizate, nu ilustrații anatomice validate), exemple de investigații unde sunt disponibile și linkuri către OpenStax.
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
- [x] Intro nativ pentru Nadia cu opțiune activare/dezactivare și reluare din Setări.
- [ ] Verificare vizuală a intro-ului pe Lenovo și revizuirea estetică.

### Resurse pentru studiu

Grilele offline sunt exerciții originale. Laboratorul include acces online la [exemplele oficiale USMLE](https://www.usmle.org/exam-resources/step-1-materials/step-1-sample-test-questions).
Pentru aprofundare: [feritină](https://medlineplus.gov/lab-tests/ferritin-blood-test/), [HbA1c](https://medlineplus.gov/lab-tests/hemoglobin-a1c-hba1c-test/) și [anatomie renală microscopică](https://openstax.org/books/anatomy-and-physiology-2e/pages/25-4-microscopic-anatomy-of-the-kidney).

## Ce nu este încă finalizat

- Nu avem încă sute de **scenarii distincte** validate; cele 94 de cazuri ambulatorii refolosesc 10 profiluri bine documentate.
- Opțiunile de management influențează **narațiunea educațională**, nu o simulare farmacologică reală sau un prognostic clinic calculat.
- Examenul offline conține 40 de întrebări originale, nu o bază exhaustivă de subiecte oficiale.
- Cele 11 scheme vectoriale sunt indicii vizuale stilizate; atlasul nu conține încă câte o imagine diagnostică verificată pentru fiecare structură.
- Conectarea reală a contului Classroom cere OAuth și permisiuni corespunzătoare.
- Nadia AI direct în aplicație cere configurarea sigură a backend-ului și facturare API separată.

### Referințe pentru interpretarea unor grile adiționale

- MedlinePlus, [Troponin Test](https://medlineplus.gov/lab-tests/troponin-test/): markerul de leziune miocardică necesită corelare clinică.
- MedlinePlus, [D-Dimer Test](https://medlineplus.gov/lab-tests/d-dimer-test/): rezultat pozitiv nespecific.
- MedlinePlus, [Lipase Tests](https://medlineplus.gov/lab-tests/lipase-tests/): interpretarea lipazei în afecțiuni pancreatice.

Aceste linkuri sunt puncte de verificare editorială. Grilele sunt originale și nu copiază subiecte oficiale.

# MedAtlas Nadia — Android Beta 0.9

### Folosire pe tabletă

Din **Studiu** se deschid spitalul, examenul, atlasul și laboratorul. Din fișa unui pacient, **Consultă atlasul** și **Deschide laboratorul** afișează resursele asociate. Înapoi revine la pacient. La relansarea spitalului, **Continuă ultima fișă** reia vizita.
Într-un document deschis, **Atlas** afișează 30 de stickere anatomice cu miniaturi și cele trei schițe existente. Atingerea unei miniaturi inserează imaginea pe pagina curentă. Stickerele BodyParts3D își păstrează sursa și licența în bibliografie.
Examenul offline selectează 20 de întrebări din trei domenii. Cronometrul continuă la ieșire, iar la final se afișează scorul, rezultatele pe domenii și corecturile.
Exportul ZIP include fișele clinice. Restaurarea unei arhive vechi, fără secțiune clinică, păstrează fișele actuale.

## Lista celor 12 categorii

| Cerință | Stadiu și verificare rămasă |
| --- | --- |
| Classroom | Certificatul APK este verificat în CI. Autentificarea, sincronizarea și importul Drive trebuie probate cu contul destinatar. |
| Nadia AI | Client și worker implementate. Adresa implicită este configurată. Autorizarea serviciului și testul conversației din aplicație rămân necesare. |
| Animație pentru Nadia | Monogramă desenată progresiv în antet; biblioteca este disponibilă imediat. |
| Imagini anatomice | 30 de redări cu proveniență BodyParts3D, trei planșe istorice; revizuirea medicală independentă este deschisă. |
| Corp 3D offline | Model nativ OpenGL din 1.258 componente cu selectarea straturilor, rotire și zoom. |
| 100 de scenarii | 100 de fișe și două trasee de urmărire pentru fiecare dintre cele 16 profiluri. Cazurile înrudite împart investigații și răspunsuri la planul ales. |
| Dialog și feedback AI | Dictare editabilă, răspuns vocal și feedback pe fișă implementate; necesită test live cu serviciile configurate. |
| Proceduri | Stație interactivă de măsurare a tensiunii; operațiile simulate și manevrele 3D rămân deschise. |
| ECG, RX, CT, analize | 12 fragmente ECG reale offline, 30 de buletine/exerciții și acces la radiografie și patru secțiuni CT la sursă. Cititorul RX/CT offline rămâne deschis. |
| Grile și examen | Simulare cronometrată cu reluare, scor pe domeniu și revizuirea greșelilor. Acoperire actuală: anatomie, laborator, clinică. |
| Validare medicală | Necesită revizuire și semnătura unui specialist. Histologia, vascularizația, inervația și patologia pentru toate structurile rămân incomplete. |
| Lenovo Tab Pen | Necesită probă de instalare/actualizare pe dispozitiv, pen, orientare, voce, backup și acces Classroom pe contul destinat. |

### Resurse pentru studiu

Grilele offline sunt exerciții originale. Laboratorul include acces online la [exemplele oficiale USMLE](https://www.usmle.org/exam-resources/step-1-materials/step-1-sample-test-questions).
Pentru aprofundare: [feritină](https://medlineplus.gov/lab-tests/ferritin-blood-test/), [HbA1c](https://medlineplus.gov/lab-tests/hemoglobin-a1c-hba1c-test/) și [anatomie renală microscopică](https://openstax.org/books/anatomy-and-physiology-2e/pages/25-4-microscopic-anatomy-of-the-kidney).

- BodyParts3D, © The Database Center for Life Science, [CC Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/). [Arhiva și termenii actuali](https://dbarchive.biosciencedbc.jp/data/bodyparts3d/LATEST/README_e.html). MedAtlas a grupat și cuantificat plasele și a generat proiecții ortografice colorate.
- Moody G., Mark R. [MIT-BIH Arrhythmia Database 1.0.0](https://doi.org/10.13026/C2F305), PhysioNet, ODC-By 1.0. Primele 6 secunde din prima derivație a 12 înregistrări, 360 Hz, conversie după calibrarea fiecărei înregistrări. Moody GB, Mark RG. The impact of the MIT-BIH Arrhythmia Database. IEEE Eng Med Biol Mag. 2001;20(3):45–50.
- [RX toracică PA](https://commons.wikimedia.org/wiki/File:Chest_Xray_PA_3-8-2010.png), Stillwaterising, și [serie CT cerebrală](https://commons.wikimedia.org/wiki/File:CT_of_a_normal_brain,_axial_14.png), Mikael Häggström, CC0. Imaginile se consultă la sursă.

## Istoric și documentația funcțiilor anterioare



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
- Modul **Cărți medicale** în Studiu: 13 referințe către editori și import în lot pentru multe PDF-uri deținute legal. În build se încearcă includerea unor PDF-uri originale, complete, de la OpenStax/Oregon State, iar numărul realmente disponibil este extras din catalogul generat doar pentru fișierele descărcate cu succes (CDN-urile pot bloca descărcarea automată).
- Cărțile offline sunt stocate în APK ca PDF-uri nemodificate; în modul Cărți poți adăuga un PDF inclus în biblioteca documentelor. Pentru volumele mari importul folosește memorie/disk în flux și se face în coroutine de I/O.


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

## Cărți medicale — drepturi și numărarea corectă

Ținta de **peste 100 de cărți medicale complete offline** nu este încă realizată și nu trebuie prezentată ca finalizată. Nu confunda 13 linkuri de bibliografie cu 13 manuale instalate integral.

`tools/bundle_open_books.py` încearcă la fiecare build să obțină 4 ediții PDF cu drepturi de reutilizare educațională, de la OpenStax/Oregon State. Editorii pot refuza accesul automat prin HTTP 403; în acest caz PDF-ul nu este inclus și catalogul raportează doar volumele realmente preluate. Fișierele păstrează identitatea și atribuirea originală.

Pentru a depăși 100 de cărți fără a crea un APK de câțiva GB, sunt necesare pachete offline distribuite separat, un catalog verificat de titluri și condiții de licențiere individuale, precum și testarea pe dispozitiv. Unele cărți au condiții CC BY-NC-SA; aplicația/biblioteca destinată reutilizării comerciale poate avea restricții. Nu copia automat cărți din NCBI Bookshelf: pentru descărcări sistematice se folosesc numai canalele permise de NLM, iar licențele diferă.

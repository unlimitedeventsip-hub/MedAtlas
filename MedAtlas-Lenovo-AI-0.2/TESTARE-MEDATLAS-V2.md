# MedAtlas v2.0.0 — plan de instalare și testare Lenovo

Aceasta este versiunea **v2.0.0 (versionCode 20)**. APK-ul semnat cu aceeași
cheie MedAtlas ca versiunile precedente poate actualiza aplicația **fără
dezinstalare**, păstrând notițele. Nu dezinstala aplicația înainte de actualizare.

## 0. Înainte de instalare

- [ ] Din Bibliotecă → Drive/Backup, salvează manual o arhivă ZIP, preferabil și într-un loc separat.
- [ ] Verifică faptul că arhiva există și că ai acces la ea.
- [ ] Descarcă artefactul **MedAtlas-v2.0.0-signed-release** din GitHub Actions
  (rularea cu build, 25+ teste Android și APK release verificat).
- [ ] Extrage **MedAtlas-v2.0.0.apk** din arhiva artefactului.
- [ ] Nu partaja parole Google, MEDATLAS_APP_TOKEN sau cheia de semnare în feedback.

## 1. Instalare fără ștergerea datelor

Metoda A: copiază APK-ul pe tabletă, deschide-l și alege **Actualizează**.

Metoda B, cu USB debugging, din folderul Windows platform-tools:

```powershell
.\adb.exe devices
.\adb.exe install -r "C:\cale\catre\MedAtlas-v2.0.0.apk"
```

Dacă apare **INSTALL_FAILED_UPDATE_INCOMPATIBLE**, oprește-te:
nu dezinstala aplicația. Înseamnă că certificatul versiunii instalate nu
corespunde celui v2. Informează echipa înainte de a face modificări.

## 2. Checklist de testare (tabletă reală)

| Ordine | Test | Rezultat |
|---|---|---|
| 1 | Pornește MedAtlas, intră în Bibliotecă, verifică documentele și notițele existente | ☐ |
| 2 | Deschide PDF, derulează cu deget/stylus, fă zoom în modul Citire | ☐ |
| 3 | Confirmă că atingerea cu pixul în Citire nu scrie sau șterge | ☐ |
| 4 | Treci în Editare, desenează o linie, ieși, revino și verifică persistența | ☐ |
| 5 | Creează o notiță, încearcă Pix / Creion / Marker / Radieră, schimbă culori și grosimi | ☐ |
| 6 | Importă DOCX (tabel și imagine), deschide direct în aplicație | ☐ |
| 7 | Importă PPTX (minim 3 slide-uri), schimbă slide-urile și verifică text/imagine | ☐ |
| 8 | Conectează Classroom dacă nu e autorizat, apoi repornește și verifică actualizarea automată | ☐ |
| 9 | Dezactivează Wi-Fi și verifică afișarea cursurilor Classroom salvate anterior | ☐ |
| 10 | Activează backup Drive într-un folder compatibil și încearcă „Salvează o copie acum” | ☐ |
| 11 | Deschide lista copiilor automate Drive și verifică arhiva; nu restaura peste date reale fără backup separat | ☐ |
| 12 | Deschide Atlas → Corp 3D, rotește, zoom și comută „Schelet” | ☐ |
| 13 | Verifică fișele clinice, ECG și examenul cu întrebări | ☐ |
| 14 | Pornește Nadia AI cu date de test, verifică un răspuns și sursa afișată | ☐ |
| 15 | Repornește tableta și verifică din nou accesul la notițe și biblioteca locală | ☐ |

Pentru verificarea recuperării ZIP, folosește un dispozitiv sau profil
separat ori date fictive: restaurarea poate înlocui fișiere locale existente.

## 3. Raport de eroare (un incident = un raport)

- **Dispozitiv și Android:** model tabletă, versiune Android
- **Versiune aplicație:** MedAtlas 2.0.0 (20)
- **Ecran și acțiune:** de exemplu Bibliotecă → PDF → Citire → stylus
- **Ce trebuia să se întâmple / Ce s-a întâmplat**
- **Reproductibilitate:** întotdeauna / uneori / o singură dată
- **Dovezi:** captură fără date personale; logcat anonimizat dacă există

Pentru erori de pornire, din platform-tools:

```powershell
.\adb.exe logcat -d -t 2500 > medatlas-v2-logcat.txt
```

Atenție: logcat poate conține identificatori sau date personale.
Elimină secretele și informațiile sensibile înainte să îl trimiți.

## 4. Criterii de acceptare v2

- APK release semnat cu **certificatul MedAtlas existent**, nu o cheie nouă
- versiune 2.0.0, versionCode 20, package ro.medatlas.study
- teste Android emulator verzi și backup de recuperare disponibil
- fără pierderea notițelor la instalarea prin actualizare
- testare manuală pentru OpenGL, OAuth Classroom, Google Drive și stilou

**Notă:** previzualizarea Word și PowerPoint este internă, dar nu reproduce
perfect teme Office complexe sau animații. Atlasul 3D este testat funcțional
prin comutarea straturilor; fidelitatea sa vizuală necesită tabletă reală.

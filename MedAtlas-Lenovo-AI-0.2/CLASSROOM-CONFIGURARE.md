# Google Classroom în MedAtlas 0.4 — configurarea oficială

Fereastra **Classroom** este separată de cititorul PDF. Aplicația conține un client Android bazat pe Google Play Services AuthorizationClient și citește cursurile, materialele și temele prin Google Classroom REST API. Nu trimite parola universitară către MedAtlas. Accesul este numai de citire.

## Obligatoriu înainte de prima conectare

1. În [Google Cloud Console](https://console.cloud.google.com/) creează/selectează un proiect. Activează **Google Classroom API**. Configurează Google Auth Platform / OAuth consent screen, inclusiv utilizatorii de test dacă aplicația este în modul Testing.
2. Creează un **OAuth client pentru Android** cu numele pachetului exact: `ro.medatlas.study`.
3. Pentru **SHA-1**, este important să folosești o cheie de semnare persistentă. GitHub Actions creează automat chei debug diferite pe rulări dacă nu configurezi secreții; un OAuth client asociat uneia dintre acele semnături se va rupe la APK-ul următor.
4. Creează local un fișier de semnare (nu îl publica):
   ```powershell
   keytool -genkeypair -v -keystore medatlas-signing.jks -alias medatlas -keyalg RSA -keysize 3072 -validity 3650
   keytool -list -v -keystore medatlas-signing.jks -alias medatlas
   ```
   Copiază **SHA1** din ultimul rezultat în clientul Google Cloud.
5. În repository GitHub → Settings → Secrets and variables → Actions, adaugă:
   - `MEDATLAS_KEYSTORE_BASE64`: Base64 din fișierul JKS (PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\\cale\\medatlas-signing.jks"))`)
   - `MEDATLAS_STORE_PASSWORD`: parola keystore-ului
   - `MEDATLAS_KEY_ALIAS`: `medatlas`
   - `MEDATLAS_KEY_PASSWORD`: parola cheii
   Nu introduce secrete în conversație și nu încărca JKS în GitHub ca fișier public.
6. Recompilează MedAtlas din GitHub Actions și instalează APK-ul semnat. Prima trecere de la un APK semnat cu vechea cheie debug poate necesita reinstalare. **Exportă backup ZIP al cursurilor din MedAtlas înainte de dezinstalare.**

## Permisiuni necesare

- `classroom.courses.readonly`
- `classroom.courseworkmaterials.readonly`
- `classroom.coursework.me.readonly`

Google poate impune verificări suplimentare ale aplicației/consimțământului sau restricții administratorului facultății. Modul Testing are limitări ale utilizatorilor și duratei consimțământului. Dacă autentificarea e blocată, verifică erorile returnate de fereastra Classroom.

## Ce face versiunea 0.4

La apăsarea **Classroom** din bara de jos se deschide o activitate Android separată. Butonul **Conectează / Actualizează** cere acord pentru permisiunile de mai sus și interoghează API-ul de cursuri și materiale și teme; afișează linkurile existente, dacă sunt furnizate de Google. Datele sunt citite în memorie la accesare, nu sunt sincronizate în fundal.

**Nu include încă descărcare automată a fișierelor Google Drive** sau editarea temelor. Aceste acțiuni necesită permisiuni Drive și un flux de import separat. Nu presupune că un link Classroom permite descărcarea automată a materialului.

## Test

După activarea OAuth, intră în aplicație → Classroom → Conectează / Actualizează → alege contul autorizat → acceptă permisiunile. Lista de cursuri ar trebui să apară după sincronizare. Testarea completă cere un cont Google real și un proiect OAuth configurat de proprietar.

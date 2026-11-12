# MedAtlas pentru Lenovo — GitHub → APK → tabletă

## 1. Publică sursa în GitHub (repository privat)

Instalează [Git for Windows](https://git-scm.com/download/win) și [GitHub CLI](https://cli.github.com/). Pentru GitHub CLI poți rula în PowerShell `winget install --id GitHub.cli`.

Extrage arhiva `MedAtlas-Lenovo-AI-0.2.zip`, deschide PowerShell în folderul extras și rulează:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\PUBLICA-GITHUB.ps1
```

La prima utilizare, GitHub CLI poate deschide browserul pentru autorizare (nu copia parole sau tokenuri în ChatGPT). Scriptul creează repository-ul **privat** `MedAtlas-Nadia`, publică codul și lansează automat GitHub Actions.

**Notă:** această metodă folosește autorizarea GitHub de pe calculatorul tău. Conectorul GitHub din ChatGPT nu poate crea repository-uri noi în sesiunea curentă; nu încărcăm MedAtlas în proiectele tale existente de cinema și reclame.

## 2. Obține APK-ul

Deschide GitHub → `MedAtlas-Nadia` → **Actions** → `Build MedAtlas APK`. La rularea cu simbol verde, deschide build-ul, apoi **Artifacts** → `MedAtlas-Lenovo-debug-apk` pentru a descărca `app-debug.apk`.

Sau din folderul proiectului rulează:

```powershell
.\DESCARCA-INSTALEAZA-APK.ps1
```

Acest script descarcă ultimul APK construit cu succes și încearcă instalarea prin ADB.

## 3. Instalare pe Lenovo Idea Tab Plus

Activează opțiunile dezvoltator (Settings → About tablet → apasă repetat pe Build number), apoi USB debugging. Conectează prin USB și acceptă mesajul de autorizare de pe Lenovo. Verifică în PowerShell:

```powershell
& "$env:USERPROFILE\Downloads\platform-tools-latest-windows\platform-tools\adb.exe" devices
```

Adaptează calea dacă `adb.exe` este direct în alt subfolder. Alternativ copiază `app-debug.apk` pe tabletă și instalează manual, acceptând instalarea din surse necunoscute dacă Android o solicită.

## 4. ChatGPT Plus sau Nadia AI

- **ChatGPT Plus**: din editor, încercuiește sau selectează → `Întreabă ChatGPT / Partajează`. Alege aplicația ChatGPT instalată pe tabletă. Folosește contul deja conectat. Acesta este un flux de partajare, nu un chat încorporat.
- **Nadia AI in-app**: necesită separat backend-ul Cloudflare Worker din `backend/` și un cont OpenAI API cu billing activ. Urmează `backend/README.md`, apoi introdu în **MedAtlas → Setări** URL-ul HTTPS al Worker-ului și tokenul privat generat de tine. Nu pune niciodată cheia OpenAI API în aplicație sau în repository.

## 5. Stadiul actual

Sursa Android 0.2, Worker-ul și fișierele de GitHub Actions sunt pregătite. Au trecut verificările de XML, JavaScript și teste pentru Worker, **dar compilarea integrală Android nu a fost executată în acest mediu**, din lipsa Android SDK. Până nu apare un build verde și test pe tableta Lenovo, nu este confirmat că APK-ul va porni fără ajustări.

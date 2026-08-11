# 🍇 ViñaRecibos — Documentación completa

**App Android de recibos de sueldo para contratistas de viñas y frutales de Mendoza (Ley 23.154)**
Recibo en formato nuevo **Decreto 407/2026** (4 secciones + gráfico de costos).

---

## 📋 Índice
1. [Comandos GitHub (subir + compilar)](#1-comandos-github)
2. [Estructura del proyecto](#2-estructura)
3. [Backend (VPS)](#3-backend)
4. [App Android](#4-app-android)
5. [AdMob](#5-admob)
6. [Centro de ayuda IA (DeepSeek)](#6-centro-de-ayuda)
7. [OCR de recibos (Gemini)](#7-ocr-de-recibos)
8. [Pantallas y flujo](#8-pantallas-y-flujo)
9. [Secretos y seguridad](#9-secretos-y-seguridad)
10. [Checklist publicación Play Store](#10-checklist-play-store)

---

## 1. Comandos GitHub

### Repositorio
- **Repo:** `Agro-bot2026/recibos-vina` (**PRIVADO**)
- **Rama:** `main`
- **Compilación:** GitHub Actions automática en cada `push` a `main`
- **APK resultante:** artifact `RecibosVina-debug` en Actions

### Subir cambios y compilar
```bash
cd /root/recibos-vina

# 1. Ver qué cambió
git status

# 2. Agregar TODO lo modificado
git add -A

# 3. Commit con mensaje descriptivo
git commit -m "descripción de lo que cambié"

# 4. Subir → dispara la compilación automáticamente
git push origin main
```

### Ver el estado de la compilación
```bash
TOKEN=$(cat ~/.git-credentials | grep -oP '(?<=:)[^:@]+(?=@)')
curl -s -H "Authorization: token $TOKEN" \
  "https://api.github.com/repos/Agro-bot2026/recibos-vina/actions/runs?per_page=1" | \
  python3.12 -c "import json,sys; d=json.load(sys.stdin); r=d['workflow_runs'][0]; print(f\"{r['display_title'][:40]} | {r['status']} | {r.get('conclusion')}\"); print(r['id'])"
```

### Descargar la APK compilada
```bash
TOKEN=$(cat ~/.git-credentials | grep -oP '(?<=:)[^:@]+(?=@)')
# Cambiar RUN_ID por el número que sale del comando anterior
RUN_ID=XXXXXXXX
ART_ID=$(curl -s -H "Authorization: token $TOKEN" \
  "https://api.github.com/repos/Agro-bot2026/recibos-vina/actions/runs/$RUN_ID/artifacts" | \
  python3.12 -c "import json,sys; print(json.load(sys.stdin)['artifacts'][0]['id'])")
curl -sL -H "Authorization: token $TOKEN" -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/Agro-bot2026/recibos-vina/actions/artifacts/$ART_ID/zip" \
  -o /tmp/apk.zip
unzip -o /tmp/apk.zip -d /tmp/apk && cp /tmp/apk/*.apk /root/RecibosVina.apk
```

### Ver error de compilación (si falla)
```bash
TOKEN=$(cat ~/.git-credentials | grep -oP '(?<=:)[^:@]+(?=@)')
JOB_ID=$(curl -s -H "Authorization: token $TOKEN" \
  "https://api.github.com/repos/Agro-bot2026/recibos-vina/actions/runs/$RUN_ID/jobs" | \
  python3.12 -c "import json,sys; print(json.load(sys.stdin)['jobs'][0]['id'])")
curl -sL -H "Authorization: token $TOKEN" -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/Agro-bot2026/recibos-vina/actions/jobs/$JOB_ID/logs" | \
  grep -E "error:|\.java:" | head -10
```

### ⚠️ Antes de hacer push — verificar que NO haya secretos
```bash
cd /root/recibos-vina
grep -rE "sk-fc44|ghp_|DUx2|private_key|ADMIN_KEY|ca-app-pub-4478373683231277~3057073988" \
  --include="*.java" --include="*.html" --include="*.js" --include="*.xml" . 2>/dev/null | grep -v ".git/" | head -5
# Si sale algo → NO pushear, arreglar primero
```

---

## 2. Estructura

```
/root/recibos-vina/
├── .github/workflows/build-apk.yml   ← CI: compila la APK en cada push
├── .gitignore                        ← excluye build/, *.apk, *.keystore, node_modules, *.db, /opt/
├── assets/logo-app.jpg               ← logo del usuario (1254×1254)
├── backend/                          ← API Node/Express (corre en el VPS)
│   ├── server.js                     ← TODA la API (:8400)
│   ├── recibo_pdf.js                 ← genera el PDF 407/2026 (puppeteer)
│   ├── schema.sql                    ← esquema SQLite
│   └── package.json                  ← express, pdfkit, puppeteer, sqlite3
└── app/                              ← proyecto Android
    ├── build.gradle                  ← AGP 8.2.2
    ├── settings.gradle               ← rootProject "RecibosVina"
    ├── gradlew + gradle/wrapper/     ← gradle-8.2-bin
    └── app/
        ├── build.gradle              ← applicationId com.recibosvina.app, compileSdk 34, minSdk 24
        ├── src/main/AndroidManifest.xml
        └── src/main/
            ├── assets/               ← HTML premium (WebView)
            │   ├── index.html            ← pantalla principal (2 botones + huella)
            │   ├── login_contratista.html
            │   ├── login_patron.html
            │   ├── panel_contratista.html
            │   ├── panel_patron.html
            │   ├── privacidad.html
            │   ├── help.html            ← chat con IA
            │   └── style.css
            ├── java/com/recibosvina/app/
            │   ├── MainActivity.java        ← principal (WebView + bridge)
            │   ├── WebViewBase.java         ← base común (bridge: goBack/goHome/privacidad/ayuda/anuncios)
            │   ├── PatronLoginActivity.java
            │   ├── ContratistaLoginActivity.java
            │   ├── PatronPanelActivity.java ← registrar/generar/subir/mis recibos + OCR
            │   ├── ContratistaPanelActivity.java
            │   ├── PrivacyActivity.java
            │   ├── HelpActivity.java
            │   ├── BiometricHelper.java     ← huella dactilar
            │   └── AdHelper.java            ← AdMob (interstitial/recompensado/app open)
            └── res/
                ├── layout/activity_main.xml, activity_web.xml
                ├── drawable/bg_input.xml
                ├── mipmap-*/ic_launcher.png ← iconos desde el logo
                └── values/colors.xml, strings.xml, themes.xml
```

---

## 3. Backend

**Servidor:** VPS `157.250.202.243` (este) · Puerto **8400** · Service systemd `recibos-vina`

### Comandos útiles
```bash
systemctl status recibos-vina      # estado
systemctl restart recibos-vina     # reiniciar
journalctl -u recibos-vina -n 50   # logs
ufw allow 8400/tcp                 # puerto (ya abierto)
```

**Base de datos:** SQLite en `/opt/recibos-vina/recibos.db`
**Recibos generados:** `/opt/recibos-vina/generados/`
**Firmas:** `/opt/recibos-vina/firmas/`

### Endpoints
| Método | Ruta | Función |
|--------|------|---------|
| GET | `/api/health` | Health check |
| POST | `/api/patron/registrar` | Registra patrón (nombre + nº viñedo) |
| POST | `/api/patron/login` | Login patrón (nombre + viñedo) |
| POST | `/api/patron/contratista` | Registra contratista (CUIL + nombre) |
| POST | `/api/patron/generar_recibo` | Genera PDF 407/2026 (puppeteer) |
| POST | `/api/patron/subir_recibo` | Sube recibo existente (foto/PDF) |
| POST | `/api/patron/leer_recibo` | OCR del recibo (Gemini + tesseract) |
| GET | `/api/patron/recibos` | Historial del patrón |
| POST | `/api/contratista/validar_acceso` | Validación CUIL + nombre |
| GET | `/api/contratista/lista_recibos` | Recibos del contratista |
| POST | `/api/contratista/subir_firma` | Sube foto de firma |
| POST | `/api/contratista/firmar_recibo` | Estampa firma + fecha |
| POST | `/api/ayuda` | Chat IA (DeepSeek) |

### Instalar dependencias del backend (si cambian)
```bash
cd /root/recibos-vina/backend
npm install
systemctl restart recibos-vina
```

---

## 4. App Android

### Datos del proyecto
| Dato | Valor |
|------|-------|
| applicationId | `com.recibosvina.app` |
| compileSdk | 34 |
| minSdk | 24 |
| Gradle | 8.2 (wrapper) |
| AGP | 8.2.2 |
| JDK | 17 (en Actions) |

### API_URL (dónde apunta la app)
`MainActivity.java` → `http://157.250.202.243:8400` (cleartext habilitado en manifest)

### Compilar localmente (opcional, para test rápido)
```bash
cd /root/recibos-vina/app
chmod +x gradlew
./gradlew assembleDebug --no-daemon
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Pantallas (todas con diseño premium oscuro)
1. **MainActivity** — "Soy patrón" / "Soy contratista" / "Ingresar con huella" + modelo del celular en el status
2. **PatronLogin** — registrar o ingresar (toggle con "Ingresar aquí")
3. **ContratistaLogin** — CUIL + nombre
4. **PatronPanel** — 4 tarjetas: Registrar contratista · Subir recibo · Mis recibos · Privacidad
5. **ContratistaPanel** — Subir firma · Ver recibos (badges firmado/sin firmar) + botón Firmar
6. **Privacy** — política completa
7. **Help** — chat con IA

### Bridge JS ↔ Java (AndroidBridge)
| Método | Uso |
|--------|-----|
| `getDeviceModel()` | Modelo del celular |
| `openPatron()` / `openContratista()` | Navegar |
| `biometricAuth()` | Huella dactilar |
| `openPrivacy()` / `openHelp()` / `openAdSettings()` | Links |
| `goBack()` / `goHome()` | Navegación |
| `registrarPatron(nombre, viniedo)` / `loginPatron(...)` | Patrón |
| `registrarContratista(cuil, nombre)` | Patrón panel |
| `leerRecibo()` → `reciboLeido(json)` | OCR (callback JS) |
| `guardarReciboTalCual(cuil, periodo)` | Subir sin convertir |
| `convertirRecibo(cuil, periodo, rem, norem, concepto)` | Convertir a 407 |
| `verRecibosPatron()` | Historial |
| `subirFirma()` / `verRecibos()` / `firmarRecibo(id)` | Contratista |

---

## 5. AdMob

### IDs (producción — ya activos en la app)
| Tipo | Ad unit |
|------|---------|
| App ID | `ca-app-pub-4478373683231277~3057073988` |
| Interstitial (Recibo1) | `ca-app-pub-4478373683231277/6804747303` |
| Recompensado (Recibos2) | `ca-app-pub-4478373683231277/5777892085` |
| App Open (Recibos3) | `ca-app-pub-4478373683231277/2405769075` |

**Dónde viven:** `app/build.gradle` (meta-data), `AndroidManifest.xml` (App ID), `AdHelper.java` (ad units)

### Cuándo se muestran
| Anuncio | Momento |
|---------|---------|
| **App Open** | Al abrir la app — **máx 1 cada 5 minutos** (política AdMob) |
| **Interstitial** | Al generar/convertir recibo (patrón) y al subir firma (contratista) |
| **Recompensado** | Cada 3 veces que ves tus recibos (contratista) |

### Políticas aplicadas (avisos de AdMob ya cumplidos)
- ✅ App open con intervalo 5 min (no satura)
- ✅ "Gestionar anuncios" → `adssettings.google.com` (en privacidad + ayuda)
- ✅ Anuncios distinguibles (icono ⓘ lo pone Google solo)
- ✅ No más anuncios que contenido

---

## 6. Centro de ayuda

**Chat con IA (DeepSeek)** en la pantalla Ayuda.

- **Endpoint:** `POST /api/ayuda` → llama a `api.deepseek.com` (modelo `deepseek-chat`)
- **Key:** `/etc/ghost-license/deepseek.key` (600, solo root)
- **Contexto:** el backend le da las FAQs reales de la app (no inventa pantallas)
- **Chips rápidos:** Subir firma · Firmar recibo · Entrar · Nombre no coincide · Anuncios · Gestionar anuncios

**Costos:** ~$0.27 USD por millón de tokens (una respuesta = fracciones de centavo)

---

## 7. OCR de recibos

**Gemini 2.5 Flash (Vertex AI)** lee el recibo subido por el patrón → JSON estructurado.

### Cómo funciona
```
Foto/PDF → pdftoppm (si es PDF) → Gemini 2.5 Flash → JSON:
{periodo, cuil, remunerativo, no_remunerativo, deducciones_total, neto, concepto}
→ ventana editable → "Guardar tal cual" o "Convertir al formato nuevo"
```

### Archivos (en el VPS, NO en GitHub)
| Archivo | Descripción |
|---------|-------------|
| `/etc/ghost-license/vertexai-key.json` | Service account `vertex-ai-user@cleanbot-8f137` (600) |
| `/opt/recibos-vina/ocr/scan_gemini.py` | Script Python (Vertex AI SDK) |
| `/opt/recibos-vina/ocr/venv/` | Venv con `google-cloud-aiplatform` |

### Probar Gemini directo
```bash
/opt/recibos-vina/ocr/venv/bin/python /opt/recibos-vina/ocr/scan_gemini.py /ruta/recibo.pdf recibo.pdf
```

### Si Gemini falla → respaldo tesseract
```bash
apt install tesseract-ocr tesseract-ocr-spa poppler-utils   # ya instalados
```

---

## 8. Pantallas y flujo

### Flujo PATRÓN
```
1. Soy patrón → Registrar (nombre + viñedo) o Ingresar (viñedo + nombre)
2. Panel: 4 tarjetas
   ├─ Registrar contratista (CUIL + nombre)
   ├─ Subir recibo → elegir foto/PDF → Gemini lee → ventana editable
   │    ├─ "Guardar tal cual" (sube el archivo)
   │    └─ "Convertir al formato nuevo" (genera PDF 407/2026)
   ├─ Mis recibos (historial con estado firma)
   └─ Política de privacidad
```

### Flujo CONTRATISTA
```
1. Soy contratista → CUIL + nombre (validación flexible: sin importar
   mayúsculas, tildes ni orden)
2. Panel:
   ├─ Subir mi firma (foto del papel firmado, 1 sola vez, por CUIL)
   └─ Ver mis recibos → lista con badges → FIRMAR (estampa firma + fecha)
3. Al firmar puede pedir la huella si está habilitada
```

---

## 9. Secretos y seguridad

| Secreto | Ubicación | Permisos |
|---------|-----------|----------|
| Token GitHub | `~/.git-credentials` | — |
| Key DeepSeek | `/etc/ghost-license/deepseek.key` | 600 |
| Key Vertex AI | `/etc/ghost-license/vertexai-key.json` | 600 |
| Admin key Cloudflare | `/etc/ghost-license/cf-admin.key` | 600 |
| Creds Ualá | `/etc/ghost-license/uala-credentials.json` | 600 |

**Reglas:**
- NUNCA versionar secretos (verificar con grep antes de cada push)
- La app SOLO habla con el backend (las keys viven en el VPS)
- El chat IA y el OCR usan las keys del servidor, nunca del cliente

---

## 10. Checklist Play Store

- [ ] Crear cuenta de desarrollador (US$25 pago único)
- [ ] Generar AAB (no APK) — el workflow compila APK; para Play hay que cambiar
      `assembleDebug` → `bundleRelease` + firmar con keystore
- [ ] Ficha: nombre "ViñaRecibos", descripción, capturas
- [ ] Declarar URL de la política de privacidad (¿pública? — se puede servir desde
      `configs.charly-tricks.dev` como `privacidad.html`)
- [ ] sellers.json en AdMob: ya **Transparente + dominio verificado** ✅
- [ ] Formulario de datos de privacidad (Play Console)
- [ ] Test con anuncios reales (los de producción ya están puestos)
- [ ] Consentimiento GDPR/EEA (para Europa — no urgente en Argentina)

---

*Última actualización: 11/08/2026 · Repo `Agro-bot2026/recibos-vina` (privado) · VPS 157.250.202.243*

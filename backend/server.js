/**
 * 🦇 RECIBOS DE SUELDO — Viñas y Frutales (Mendoza)
 * Backend API: registro de patrones/contratistas, recibos y firmas
 * CON AUTENTICACIÓN: password (patrón) + PIN (contratista) + tokens de sesión
 *
 * Puerto: 8400
 */
const express = require('express');
const sqlite3 = require('sqlite3');
const bcrypt = require('bcrypt');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const https = require('https');

const app = express();
app.use(express.json({ limit: '50mb' }));

// CORS restringido — el WebView de la app manda Authorization, no hace falta '*'
app.use((req, res, next) => {
    res.setHeader('Access-Control-Allow-Origin', '*'); // WebView sin origin fijo; el riesgo real ya lo tapa el token
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
    if (req.method === 'OPTIONS') return res.sendStatus(204);
    next();
});

const DB_PATH = '/opt/recibos-vina/recibos.db';
const FIRMAS_DIR = '/opt/recibos-vina/firmas';
const RECIBOS_DIR = '/opt/recibos-vina/recibos';

fs.mkdirSync('/opt/recibos-vina', { recursive: true });
fs.mkdirSync(FIRMAS_DIR, { recursive: true });
fs.mkdirSync(RECIBOS_DIR, { recursive: true });

const db = new sqlite3.Database(DB_PATH);
db.exec(fs.readFileSync(__dirname + '/schema.sql', 'utf-8'));

const SALT_ROUNDS = 10;
const SESION_DIAS = 30;

// ═══════════════════════════════════════════
// AUTH: helpers
// ═══════════════════════════════════════════

function crearSesion(tipo, referenciaId, cb) {
    const token = crypto.randomBytes(32).toString('hex');
    db.run(
        `INSERT INTO sesiones (token, tipo, referencia_id, expires_at) VALUES (?,?,?, datetime('now', '+${SESION_DIAS} days'))`,
        [token, tipo, referenciaId],
        (err) => cb(err, token)
    );
}

// Middleware: exige token válido de tipo 'patron' y adjunta req.patron_id
function requierePatron(req, res, next) {
    const auth = req.headers['authorization'] || '';
    const token = auth.startsWith('Bearer ') ? auth.slice(7) : null;
    if (!token) return res.status(401).json({ ok: false, error: 'Falta iniciar sesión' });
    db.get(
        `SELECT referencia_id FROM sesiones WHERE token=? AND tipo='patron' AND expires_at > datetime('now')`,
        [token],
        (err, row) => {
            if (err || !row) return res.status(401).json({ ok: false, error: 'Sesión inválida o vencida, volvé a entrar' });
            req.patron_id = row.referencia_id;
            next();
        }
    );
}

// Middleware: exige token válido de tipo 'contratista' y adjunta req.contratista_id
function requiereContratista(req, res, next) {
    const auth = req.headers['authorization'] || '';
    const token = auth.startsWith('Bearer ') ? auth.slice(7) : null;
    if (!token) return res.status(401).json({ ok: false, error: 'Falta iniciar sesión' });
    db.get(
        `SELECT referencia_id FROM sesiones WHERE token=? AND tipo='contratista' AND expires_at > datetime('now')`,
        [token],
        (err, row) => {
            if (err || !row) return res.status(401).json({ ok: false, error: 'Sesión inválida o vencida, volvé a entrar' });
            req.contratista_id = row.referencia_id;
            next();
        }
    );
}

function normalizarNombre(n) {
    return (n || '')
        .toLowerCase()
        .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-z0-9\s]/g, ' ')
        .split(/\s+/).filter(Boolean).sort().join(' ');
}
function nombresCoinciden(registrado, ingresado) {
    return normalizarNombre(registrado) === normalizarNombre(ingresado);
}

// ═══════════════════════════════════════════
// PATRÓN: registro y login (sin auth previa)
// ═══════════════════════════════════════════

app.post('/api/patron/registrar', async (req, res) => {
    const { nombre, numero_viniedo, cuit, telefono, password } = req.body || {};
    if (!nombre || !numero_viniedo || !password) {
        return res.status(400).json({ ok: false, error: 'Faltan nombre, número de viñedo o contraseña' });
    }
    if (password.length < 6) {
        return res.status(400).json({ ok: false, error: 'La contraseña debe tener al menos 6 caracteres' });
    }
    try {
        const hash = await bcrypt.hash(password, SALT_ROUNDS);
        db.run(
            'INSERT INTO patrones (nombre, numero_viniedo, cuit, telefono, password_hash) VALUES (?,?,?,?,?)',
            [nombre, numero_viniedo, cuit || null, telefono || null, hash],
            function (err) {
                if (err) {
                    if (String(err.message).includes('UNIQUE')) {
                        return res.status(409).json({ ok: false, error: 'Ese número de viñedo ya está registrado. Iniciá sesión.' });
                    }
                    return res.status(500).json({ ok: false, error: err.message });
                }
                crearSesion('patron', this.lastID, (e, token) => {
                    if (e) return res.status(500).json({ ok: false, error: 'Error creando sesión' });
                    res.json({ ok: true, patron_id: this.lastID, token });
                });
            }
        );
    } catch (e) {
        res.status(500).json({ ok: false, error: 'Error interno' });
    }
});

app.post('/api/patron/login', (req, res) => {
    const { numero_viniedo, password } = req.body || {};
    if (!numero_viniedo || !password) {
        return res.status(400).json({ ok: false, error: 'Faltan número de viñedo y contraseña' });
    }
    db.get('SELECT id, nombre, password_hash FROM patrones WHERE numero_viniedo=?', [numero_viniedo], async (e, p) => {
        if (!p) return res.status(404).json({ ok: false, error: 'No hay un patrón registrado con ese número de viñedo' });
        const okPass = await bcrypt.compare(password, p.password_hash);
        if (!okPass) return res.status(401).json({ ok: false, error: 'Contraseña incorrecta' });
        crearSesion('patron', p.id, (err, token) => {
            if (err) return res.status(500).json({ ok: false, error: 'Error creando sesión' });
            res.json({ ok: true, patron_id: p.id, nombre: p.nombre, token });
        });
    });
});

// ═══════════════════════════════════════════
// PATRÓN: todo lo demás requiere token (requierePatron)
// patron_id sale del token, NUNCA del body — así nadie puede
// pedir datos de otro patrón cambiando un número.
// ═══════════════════════════════════════════

app.get('/api/patron/recibos', requierePatron, (req, res) => {
    db.all(
        `SELECT r.id, r.periodo, r.firmado, r.firma_fecha, r.created_at, c.nombre AS contratista, c.cuil
         FROM recibos r JOIN contratistas c ON c.id = r.contratista_id
         WHERE r.patron_id=? ORDER BY r.created_at DESC`,
        [req.patron_id],
        (e, recibos) => res.json({ ok: true, recibos: recibos || [] })
    );
});

app.post('/api/patron/subir_recibo', requierePatron, (req, res) => {
    const { contratista_cuil, periodo, archivo_base64 } = req.body || {};
    if (!contratista_cuil || !periodo || !archivo_base64) {
        return res.status(400).json({ ok: false, error: 'Faltan datos' });
    }
    db.get('SELECT id FROM contratistas WHERE cuil=? AND patron_id=?', [contratista_cuil, req.patron_id], (e, c) => {
        if (!c) return res.status(404).json({ ok: false, error: 'Contratista no encontrado para este patrón' });
        try {
            const fname = `${contratista_cuil}_${periodo.replace(/\s+/g, '_')}_${Date.now()}.pdf`;
            const fpath = path.join(RECIBOS_DIR, fname);
            fs.writeFileSync(fpath, Buffer.from(archivo_base64, 'base64'));
            db.run('INSERT INTO recibos (patron_id, contratista_id, periodo, archivo_path) VALUES (?,?,?,?)',
                [req.patron_id, c.id, periodo, fpath],
                function (err) {
                    if (err) return res.status(500).json({ ok: false, error: err.message });
                    res.json({ ok: true, recibo_id: this.lastID });
                });
        } catch (err) {
            res.status(500).json({ ok: false, error: 'Error guardando archivo: ' + err.message });
        }
    });
});

// Registrar contratista: el patrón define un PIN que le pasa al trabajador
app.post('/api/patron/contratista', requierePatron, async (req, res) => {
    const { cuil, nombre, pin } = req.body || {};
    if (!cuil || !nombre || !pin) {
        return res.status(400).json({ ok: false, error: 'Faltan datos (cuil, nombre, pin)' });
    }
    if (!/^\d{4,6}$/.test(pin)) {
        return res.status(400).json({ ok: false, error: 'El PIN debe ser de 4 a 6 números' });
    }
    try {
        const pinHash = await bcrypt.hash(pin, SALT_ROUNDS);
        db.run('INSERT OR IGNORE INTO contratistas (cuil, nombre, patron_id, pin_hash) VALUES (?,?,?,?)',
            [cuil, nombre, req.patron_id, pinHash],
            function (err) {
                if (err) return res.status(500).json({ ok: false, error: err.message });
                if (this.changes === 0) {
                    return res.status(409).json({ ok: false, error: 'Ese CUIL ya está registrado' });
                }
                res.json({ ok: true, contratista_id: this.lastID });
            });
    } catch (e) {
        res.status(500).json({ ok: false, error: 'Error interno' });
    }
});

app.post('/api/patron/generar_recibo', requierePatron, (req, res) => {
    const { contratista_cuil, periodo, concepto, remunerativo, no_remunerativo, deducciones } = req.body || {};
    if (!contratista_cuil || !periodo || !remunerativo) {
        return res.status(400).json({ ok: false, error: 'Faltan datos (contratista_cuil, periodo, remunerativo)' });
    }
    db.get('SELECT id, nombre, cuil FROM contratistas WHERE cuil=? AND patron_id=?', [contratista_cuil, req.patron_id], async (e, c) => {
        if (!c) return res.status(404).json({ ok: false, error: 'Contratista no encontrado para este patrón' });
        try {
            const { generarReciboPDF } = require('./recibo_pdf.js');
            const d = {
                empleador_nombre: '—',
                cuit_empleador: '—',
                nombre: c.nombre,
                cuil: c.cuil,
                obra_social: 'OSPRERA',
                periodo,
                concepto: concepto || 'HAS EN PRODUCCIÓN',
                remunerativo: Number(remunerativo),
                no_remunerativo: Number(no_remunerativo || 0),
                deducciones: deducciones || [
                    { cod: '301', concepto: 'JUBILACIÓN 11%', monto: Math.round(Number(remunerativo) * 0.11 * 100) / 100 },
                    { cod: '302', concepto: 'LEY 19.032 (PAMI) 3%', monto: Math.round(Number(remunerativo) * 0.03 * 100) / 100 },
                    { cod: '303', concepto: 'OBRA SOCIAL', monto: Math.round(Number(remunerativo) * 0.06 * 100) / 100 },
                ],
                contribuciones: [
                    { concepto: 'ART (Riesgos del Trabajo)', unidad: '≈ 3,50%', monto: Math.round(Number(remunerativo) * 0.035 * 100) / 100 },
                    { concepto: 'Contribución Jubilación / SIPA', unidad: '18,00%', monto: Math.round(Number(remunerativo) * 0.18 * 100) / 100 },
                    { concepto: 'Contribución Obra Social', unidad: '6,00%', monto: Math.round(Number(remunerativo) * 0.06 * 100) / 100 },
                    { concepto: 'Seguro de Vida Obligatorio', unidad: 'fijo', monto: 1024.21 },
                    { concepto: 'Fondo de Desempleo (empleador)', unidad: '1,50%', monto: Math.round(Number(remunerativo) * 0.015 * 100) / 100 },
                    { concepto: 'Costos CCT / Paritaria', unidad: '—', monto: 0 },
                ],
            };
            const r = await generarReciboPDF(d);
            db.run('INSERT INTO recibos (patron_id, contratista_id, periodo, archivo_path) VALUES (?,?,?,?)',
                [req.patron_id, c.id, periodo, r.path],
                function (err) {
                    if (err) return res.status(500).json({ ok: false, error: err.message });
                    res.json({ ok: true, recibo_id: this.lastID, pdf: r.path, neto: r.neto });
                });
        } catch (err) {
            res.status(500).json({ ok: false, error: 'Error generando PDF: ' + err.message });
        }
    });
});

// ═══════════════════════════════════════════
// CONTRATISTA: login con CUIL + nombre + PIN
// ═══════════════════════════════════════════

app.post('/api/contratista/validar_acceso', (req, res) => {
    const { cuil, nombre, pin } = req.body || {};
    if (!cuil || !nombre || !pin) return res.status(400).json({ ok: false, error: 'Faltan cuil, nombre y pin' });
    db.get('SELECT id, nombre, pin_hash, firma_activa FROM contratistas WHERE cuil=?', [cuil], async (e, c) => {
        if (!c) return res.status(404).json({ ok: false, error: 'No estás registrado. Pedile a tu patrón que te registre.' });
        if (!nombresCoinciden(c.nombre, nombre)) {
            return res.status(403).json({ ok: false, error: 'El nombre no coincide con el CUIL. Verificá.' });
        }
        const okPin = await bcrypt.compare(pin, c.pin_hash);
        if (!okPin) return res.status(401).json({ ok: false, error: 'PIN incorrecto' });
        crearSesion('contratista', c.id, (err, token) => {
            if (err) return res.status(500).json({ ok: false, error: 'Error creando sesión' });
            res.json({ ok: true, contratista: { id: c.id, nombre: c.nombre, firma_activa: c.firma_activa }, token });
        });
    });
});

// ═══════════════════════════════════════════
// CONTRATISTA: requiere token (requiereContratista)
// ═══════════════════════════════════════════

app.get('/api/contratista/recibos', requiereContratista, (req, res) => {
    db.all('SELECT id, periodo, firmado, firma_fecha, created_at FROM recibos WHERE contratista_id=? ORDER BY created_at DESC',
        [req.contratista_id], (e, recibos) => res.json({ ok: true, recibos: recibos || [] }));
});

app.post('/api/contratista/firma', requiereContratista, (req, res) => {
    const { firma_base64 } = req.body || {};
    if (!firma_base64) return res.status(400).json({ ok: false, error: 'Falta la firma' });
    const fname = `firma_${req.contratista_id}_${Date.now()}.png`;
    const fpath = path.join(FIRMAS_DIR, fname);
    fs.writeFileSync(fpath, Buffer.from(firma_base64, 'base64'));
    db.run('UPDATE contratistas SET firma_path=?, firma_activa=1 WHERE id=?', [fpath, req.contratista_id], (e2) => {
        res.json({ ok: true, mensaje: 'Firma guardada!' });
    });
});

app.post('/api/contratista/firmar', requiereContratista, (req, res) => {
    const { recibo_id } = req.body || {};
    if (!recibo_id) return res.status(400).json({ ok: false, error: 'Falta recibo_id' });
    db.get('SELECT firma_path FROM contratistas WHERE id=?', [req.contratista_id], (e, c) => {
        if (!c.firma_path) return res.status(400).json({ ok: false, error: 'Primero subí tu firma (sacale foto a tu firma en papel)' });
        db.run("UPDATE recibos SET firmado=1, firma_fecha=datetime('now') WHERE id=? AND contratista_id=?",
            [recibo_id, req.contratista_id],
            function (err) {
                if (err) return res.status(500).json({ ok: false, error: err.message });
                if (this.changes === 0) return res.status(404).json({ ok: false, error: 'Recibo no encontrado o no te pertenece' });
                res.json({ ok: true, mensaje: 'Recibo firmado!', firma_path: c.firma_path });
            });
    });
});

// ═══════════════════════════════════════════
// Centro de ayuda (DeepSeek) — no maneja datos sensibles, sin auth
// ═══════════════════════════════════════════

const DEEPSEEK_KEY_PATH = '/etc/ghost-license/deepseek.key';
const AYUDA_CONTEXTO = [
    'Sos el asistente de ayuda de ViñaRecibos, una app de recibos de sueldo para contratistas de viñas y frutales de Mendoza, Argentina (ley 23.154).',
    'Respondé en español rioplatense, corto y claro, con pasos numerados si hace falta.',
    '',
    'Datos útiles que conocés:',
    '- Pantalla principal: botones "Soy patrón" y "Soy contratista", más "Ingresar con huella".',
    '- El contratista entra con CUIL + nombre + PIN (el patrón le da el PIN al registrarlo).',
    '- La firma se sube UNA vez tocando "Subir mi firma" en el panel del contratista (foto de la firma en papel).',
    '- Los recibos se ven tocando "Ver mis recibos" en el panel del contratista.',
    '- El recibo se firma tocando el botón "Firmar" en la lista de recibos.',
    '- El patrón entra con número de viñedo + contraseña.',
    '- Los recibos se generan en formato nuevo (Decreto 407/2026): 4 secciones + gráfico de costos.',
    '- La app es gratuita y se financia con anuncios de AdMob.',
    '- Contacto: info@charly-tricks.dev',
    '- NO inventes secciones ni pantallas que no existen.',
].join('\n');

app.post('/api/ayuda', (req, res) => {
    const { pregunta } = req.body || {};
    if (!pregunta || !pregunta.trim()) return res.status(400).json({ ok: false, error: 'Falta la pregunta' });
    let key;
    try { key = fs.readFileSync(DEEPSEEK_KEY_PATH, 'utf-8').trim(); } catch (e) {}
    if (!key) return res.status(500).json({ ok: false, error: 'Asistente no configurado todavía' });

    const body = JSON.stringify({
        model: 'deepseek-chat',
        messages: [{ role: 'system', content: AYUDA_CONTEXTO }, { role: 'user', content: pregunta }],
        max_tokens: 500,
        temperature: 0.4,
    });
    const reqApi = https.request({
        hostname: 'api.deepseek.com', path: '/chat/completions', method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${key}`, 'Content-Length': Buffer.byteLength(body) },
    }, (r) => {
        let data = '';
        r.on('data', (c) => data += c);
        r.on('end', () => {
            try {
                const parsed = JSON.parse(data);
                if (r.statusCode === 200 && parsed.choices && parsed.choices[0]) {
                    res.json({ ok: true, respuesta: parsed.choices[0].message.content.trim() });
                } else {
                    res.status(502).json({ ok: false, error: `DeepSeek ${r.statusCode}: ${parsed.error?.message || 'error'}` });
                }
            } catch (e) {
                res.status(502).json({ ok: false, error: 'Respuesta inválida del asistente' });
            }
        });
    });
    reqApi.on('error', (e) => res.status(500).json({ ok: false, error: `Error de conexión: ${e.message}` }));
    reqApi.write(body);
    reqApi.end();
});

// ═══════════════════════════════════════════
// PATRÓN: OCR de recibo — requiere auth (maneja datos de sueldo)
// ═══════════════════════════════════════════

const { execSync } = require('child_process');
const TMP_OCR = '/tmp/recibos-ocr';
const GEMINI_VENV = '/opt/recibos-vina/ocr/venv/bin/python';
const GEMINI_SCRIPT = '/opt/recibos-vina/ocr/scan_gemini.py';

app.post('/api/patron/leer_recibo', requierePatron, (req, res) => {
    const { archivo_base64, nombre_archivo } = req.body || {};
    if (!archivo_base64) return res.status(400).json({ ok: false, error: 'Falta el archivo' });

    fs.mkdirSync(TMP_OCR, { recursive: true });
    const ext = (nombre_archivo || 'recibo.png').includes('.') ? nombre_archivo.split('.').pop().toLowerCase() : 'png';
    const inPath = path.join(TMP_OCR, `in_${Date.now()}.${ext}`);

    try {
        fs.writeFileSync(inPath, Buffer.from(archivo_base64, 'base64'));
        try {
            const salida = execSync(`"${GEMINI_VENV}" "${GEMINI_SCRIPT}" "${inPath}" "${nombre_archivo || 'recibo.png'}"`,
                { timeout: 90000, encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 });
            const datos = JSON.parse(salida.trim());
            return res.json({ ok: true, datos, motor: 'gemini' });
        } catch (e) {
            console.log('Gemini falló, usando tesseract:', e.message.split('\n')[0]);
        }
        try {
            let imgPath = inPath;
            if (ext === 'pdf') {
                try {
                    const outBase = inPath.replace(/\.[^.]+$/, '');
                    execSync(`pdftoppm -png -r 200 -f 1 -l 1 "${inPath}" "${outBase}"`, { timeout: 30000 });
                    const png = `${outBase}-1.png`;
                    if (fs.existsSync(png)) imgPath = png;
                } catch (e) {}
            }
            const texto = execSync(`tesseract "${imgPath}" stdout -l spa --psm 6 2>/dev/null`, { timeout: 60000, encoding: 'utf-8' });
            const datos = extraerDatosRecibo(texto);
            return res.json({ ok: true, texto, datos, motor: 'tesseract' });
        } catch (e) {
            return res.status(500).json({ ok: false, error: 'No pude leer el archivo' });
        }
    } catch (e) {
        res.status(500).json({ ok: false, error: 'Error procesando el archivo: ' + e.message });
    } finally {
        try { fs.unlinkSync(inPath); } catch (e) {}
    }
});

function extraerDatosRecibo(texto) {
    const t = texto.replace(/\r/g, '');
    const datos = {};
    const mPeriodo = t.match(/(ENERO|FEBRERO|MARZO|ABRIL|MAYO|JUNIO|JULIO|AGOSTO|SEPTIEMBRE|OCTUBRE|NOVIEMBRE|DICIEMBRE)\s+20\d{2}/i);
    if (mPeriodo) datos.periodo = mPeriodo[0].toUpperCase();
    const mCuilContratista = t.match(/Contratista:\s*[^\n]*?\b(20[- ]?\d{8}[- ]?\d)\b/i)
        || t.match(/Contratista:.*?CUIL:\s*(20[- ]?\d{8}[- ]?\d)/is)
        || t.match(/(?:Contratista|CONTRATISTA)[\s\S]{0,120}?\b(20[- ]?\d{8}[- ]?\d)\b/i);
    if (mCuilContratista) {
        const c = mCuilContratista[1] || mCuilContratista[0];
        datos.cuil = c.replace(/\s+/g, '');
    }
    if (!datos.cuil) {
        const cuiles = t.match(/\b20[- ]?\d{8}[- ]?\d\b/g);
        if (cuiles && cuiles.length) datos.cuil = cuiles[cuiles.length - 1].replace(/\s+/g, '');
    }
    const lineasRem = t.split('\n').filter(l => /REM\.?\s*C\/D|REMUNERATIVO|REM\.?\s*C\/D\.?/i.test(l) && !/C\/Hs/i.test(l));
    for (const linea of lineasRem) {
        const m = linea.match(/\$?\s*([\d]{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})?)/);
        if (m && m[1].length >= 4) { datos.remunerativo = parseNumero(m[1]); break; }
    }
    if (datos.remunerativo == null) {
        const mRem = t.match(/(?:REM\.?\s*C\/D|REMUNERATIVO|REM\.?\s*C\/D\.?|SUELDO BRUTO)[:\s]*\$?\s*([\d][\d.,]*)/i);
        if (mRem && mRem[1].length >= 4) datos.remunerativo = parseNumero(mRem[1]);
    }
    const lineasNoRem = t.split('\n').filter(l => /REM\.?\s*S\/D|NO REMUNERATIVO|REM\.?\s*S\/D\.?/i.test(l) && !/C\/Hs/i.test(l));
    for (const linea of lineasNoRem) {
        const m = linea.match(/\$?\s*([\d]{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})?)/);
        if (m && m[1].length >= 4) { datos.no_remunerativo = parseNumero(m[1]); break; }
    }
    if (datos.no_remunerativo == null) {
        const mNoRem = t.match(/(?:REM\.?\s*S\/D|NO REMUNERATIVO|REM\.?\s*S\/D\.?)[:\s]*\$?\s*([\d][\d.,]*)/i);
        if (mNoRem && mNoRem[1].length >= 4) datos.no_remunerativo = parseNumero(mNoRem[1]);
    }
    const mDed = t.match(/(?:TOTAL\s*DEDUCCIONES|DEDUCCIONES|DEDUCC\.?)[:\s]*\$?\s*([\d][\d.,]*)/i);
    if (mDed && mDed[1].includes(',')) datos.deducciones_total = parseNumero(mDed[1]);
    const mNeto = t.match(/(?:SUELDO\s*NETO|NETO|TOTAL\s*NETO)[:\s]*\$?\s*([\d][\d.,]*)/i);
    if (mNeto && mNeto[1].includes(',')) datos.neto = parseNumero(mNeto[1]);
    const mHas = t.match(/(\d+(?:\.\d+)?)\s*HAS?(?:\s+EN\s+PRODUCCI[OÓ]N)?/i);
    if (mHas && !mHas[1].includes('.')) datos.concepto = `${mHas[1]} HAS EN PRODUCCIÓN`;
    return datos;
}

function parseNumero(s) {
    const limpio = s.replace(/\$/g, '').replace(/\s/g, '');
    if (limpio.includes(',')) return parseFloat(limpio.replace(/\./g, '').replace(',', '.'));
    return parseFloat(limpio);
}

// ─── Health ───
app.get('/api/health', (req, res) => res.json({ ok: true, app: 'Recibos Viña', version: '0.2.0-auth' }));

const PORT = process.env.PORT || 8400;
app.listen(PORT, () => console.log(`🦇 Recibos Viña API en :${PORT} (con autenticación)`));

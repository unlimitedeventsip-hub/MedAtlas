/* MedAtlas Companion — native offline image detection.
 * Uses browser BarcodeDetector / TextDetector only when exposed by the browser.
 * No remote scripts, network calls, camera permissions or auto-opening QR URLs.
 */
const FORMATS = ['qr_code','data_matrix','code_128','ean_13','pdf417'];

export function normalizeCodes(values) {
  if (!Array.isArray(values)) return [];
  return values.slice(0,60).map(item => ({
    format: String(item?.format || 'code').slice(0,40),
    value: String(item?.rawValue || '').trim().slice(0,2000)
  })).filter(item => item.value.length > 0);
}
export function normalizeText(values) {
  if (!Array.isArray(values)) return [];
  return values.slice(0,300).map(item =>
    String(item?.rawValue || item?.text || '').trim().slice(0,4000)
  ).filter(Boolean);
}

/**
 * Treat each API as optional. A browser without either detector can still
 * open and edit notes; it receives a truthful "unavailable" message.
 * Fake detectors can be injected for offline Node tests.
 */
export async function scanImage(image, BarcodeType=globalThis.BarcodeDetector,
  TextType=globalThis.TextDetector) {
  const result = {
    codes: [], lines: [],
    barcodeAvailable: typeof BarcodeType === 'function',
    textAvailable: typeof TextType === 'function',
    warnings: []
  };

  if (result.barcodeAvailable) {
    try {
      const supported = typeof BarcodeType.getSupportedFormats === 'function'
        ? await BarcodeType.getSupportedFormats() : FORMATS;
      const formats = FORMATS.filter(format => supported.includes(format));
      if (formats.length === 0) {
        result.warnings.push('Browserul nu suportă formatele de cod necesare.');
      } else {
        const detector = new BarcodeType({ formats });
        result.codes = normalizeCodes(await detector.detect(image));
      }
    } catch {
      result.warnings.push('Detectarea codurilor nu este disponibilă pentru această imagine.');
    }
  } else {
    result.warnings.push('Scanarea QR nu este disponibilă în acest browser.');
  }

  if (result.textAvailable) {
    try {
      const detector = new TextType();
      result.lines = normalizeText(await detector.detect(image));
    } catch {
      result.warnings.push('Recunoașterea textului nu este disponibilă pentru această imagine.');
    }
  } else {
    result.warnings.push(
      'OCR de imagini nu este disponibil în acest browser; folosește MedAtlas Android.'
    );
  }
  return result;
}

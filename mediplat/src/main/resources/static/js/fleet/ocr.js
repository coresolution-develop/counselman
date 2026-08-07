// 계기판 촬영 이미지 처리 + OCR.
// - 한 번의 canvas 처리로 (a) 업로드용 축소본과 (b) OCR용 중앙 크롭을 동시에 생성한다(재사용).
// - Tesseract.js는 촬영 후 lazy-load 한다. 로드/인식 실패는 조용히 무시하고 수동 입력으로 대체한다.
//   사진 자체가 진짜 증빙이므로 OCR은 어디까지나 타이핑 편의(보조 수단)다.

const TESSERACT_CDN = 'https://cdn.jsdelivr.net/npm/tesseract.js@5/dist/tesseract.min.js';
const UPLOAD_MAX_WIDTH = 1280;

/**
 * 촬영 파일에서 업로드용 축소 Blob과 OCR용 중앙 크롭 canvas를 만든다.
 * canvas 처리가 불가하면 원본 파일을 업로드본으로 그대로 반환한다(ocrCanvas=null).
 */
export async function prepareCapture(file) {
    try {
        const bitmap = await createImageBitmap(file);
        const scale = Math.min(1, UPLOAD_MAX_WIDTH / bitmap.width);
        const uw = Math.max(1, Math.round(bitmap.width * scale));
        const uh = Math.max(1, Math.round(bitmap.height * scale));

        const uploadCanvas = document.createElement('canvas');
        uploadCanvas.width = uw;
        uploadCanvas.height = uh;
        uploadCanvas.getContext('2d').drawImage(bitmap, 0, 0, uw, uh);
        const uploadBlob = await toBlob(uploadCanvas, 'image/jpeg', 0.7);

        // 오버레이 프레임과 동일한 중앙 가로 밴드만 크롭 → 인식률↑
        const cw = Math.round(uw * 0.9);
        const ch = Math.round(uh * 0.32);
        const ox = Math.round((uw - cw) / 2);
        const oy = Math.round((uh - ch) / 2);
        const ocrCanvas = document.createElement('canvas');
        ocrCanvas.width = cw;
        ocrCanvas.height = ch;
        ocrCanvas.getContext('2d').drawImage(uploadCanvas, ox, oy, cw, ch, 0, 0, cw, ch);

        return { uploadBlob: uploadBlob || file, ocrCanvas };
    } catch (e) {
        return { uploadBlob: file, ocrCanvas: null };
    }
}

/** 크롭 canvas에서 숫자만 인식한다. 실패/오프라인이면 빈 문자열. */
export async function recognizeOdometer(ocrCanvas) {
    if (!ocrCanvas) {
        return '';
    }
    let worker;
    try {
        const Tesseract = await ensureTesseract();
        worker = await Tesseract.createWorker('eng');
        await worker.setParameters({ tessedit_char_whitelist: '0123456789' });
        const { data } = await worker.recognize(ocrCanvas);
        return (data && data.text ? data.text : '').replace(/\D/g, '');
    } catch (e) {
        return '';
    } finally {
        if (worker) {
            try { await worker.terminate(); } catch (e) { /* noop */ }
        }
    }
}

let tesseractPromise = null;

function ensureTesseract() {
    if (window.Tesseract) {
        return Promise.resolve(window.Tesseract);
    }
    if (!tesseractPromise) {
        tesseractPromise = new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = TESSERACT_CDN;
            script.async = true;
            script.onload = () => resolve(window.Tesseract);
            script.onerror = () => {
                tesseractPromise = null;
                reject(new Error('OCR 엔진 로드 실패'));
            };
            document.head.appendChild(script);
        });
    }
    return tesseractPromise;
}

function toBlob(canvas, type, quality) {
    return new Promise((resolve) => canvas.toBlob(resolve, type, quality));
}

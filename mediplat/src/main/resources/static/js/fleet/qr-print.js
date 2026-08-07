// 차량 QR 인쇄. self-host된 qrcode.min.js(davidshimjs/qrcodejs)로 렌더한다.
// QR이 인코딩하는 주소는 "현재 접속한 origin + /fleet/t/{token}"이다. 즉 관리자가 접속한 주소가
// 그대로 QR에 담기므로, 실측 시 반드시 '휴대폰에서 접근 가능한 실제 서비스 주소'로 접속해 인쇄해야 한다.
(function () {
    var sheet = document.querySelector('.qr-sheet');
    if (!sheet) {
        return;
    }
    var token = sheet.getAttribute('data-qr-token');
    if (!token) {
        return;
    }
    var url = window.location.origin + '/fleet/t/' + encodeURIComponent(token);

    var urlEl = document.getElementById('qr-url');
    if (urlEl) {
        urlEl.textContent = url;
    }

    // localhost/사설 주소로 인쇄하면 폰이 접근 못 함 → 경고 노출(되짚음 ③)
    var host = window.location.hostname;
    var unreachable = host === 'localhost' || host === '127.0.0.1' || host === '0.0.0.0';
    var warnEl = document.getElementById('qr-warn');
    if (unreachable && warnEl) {
        warnEl.hidden = false;
    }

    if (typeof window.QRCode === 'undefined') {
        if (urlEl) {
            urlEl.textContent = 'QR 라이브러리를 불러오지 못했습니다. 주소: ' + url;
        }
        return;
    }
    new window.QRCode(document.getElementById('qr'), {
        text: url,
        width: 220,
        height: 220,
        correctLevel: window.QRCode.CorrectLevel.M,
    });
})();

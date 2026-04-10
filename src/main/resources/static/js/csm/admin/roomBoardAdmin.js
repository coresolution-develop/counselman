document.addEventListener('DOMContentLoaded', () => {
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || '';
  const requestHeaders = {
    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
  };
  if (csrfToken && csrfHeader) {
    requestHeaders[csrfHeader] = csrfToken;
  }

  const roomConfigRows = document.querySelectorAll('.room-config-row');
  const form = document.getElementById('roomConfigForm');
  const previewWrap = document.getElementById('previewWrap');
  const previewBody = document.getElementById('previewBody');
  const rawText = document.getElementById('rawText');
  const snapshotDate = document.getElementById('snapshotDate');
  const snapshotTime = document.getElementById('snapshotTime');
  const sourceType = document.getElementById('sourceType');
  const importMessage = document.getElementById('importMessage');

  const setNow = () => {
    const now = new Date();
    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    const dd = String(now.getDate()).padStart(2, '0');
    const hh = String(now.getHours()).padStart(2, '0');
    const mi = String(now.getMinutes()).padStart(2, '0');
    if (snapshotDate && !snapshotDate.value) snapshotDate.value = `${yyyy}-${mm}-${dd}`;
    if (snapshotTime && !snapshotTime.value) snapshotTime.value = `${hh}:${mi}`;
    const startDate = document.getElementById('startDate');
    const endDate = document.getElementById('endDate');
    if (startDate && !startDate.value) startDate.value = `${yyyy}-${mm}-${dd}`;
    if (endDate && !endDate.value) endDate.value = '9999-12-31';
  };
  setNow();

  roomConfigRows.forEach((row) => {
    row.addEventListener('click', () => {
      roomConfigRows.forEach((item) => item.classList.remove('rb-row-selected'));
      row.classList.add('rb-row-selected');
      const data = row.dataset;
      form.querySelector('#roomConfigId').value = data.id || '';
      form.querySelector('#wardName').value = data.wardName || '';
      form.querySelector('#roomName').value = data.roomName || '';
      form.querySelector('#startDate').value = data.startDate || '';
      form.querySelector('#endDate').value = data.endDate || '';
      form.querySelector('#licensedBeds').value = data.licensedBeds || '';
      form.querySelector('#careType').value = data.careType || '';
      form.querySelector('#statusWalk').value = data.statusWalk || 'N';
      form.querySelector('#statusDiaper').value = data.statusDiaper || 'N';
      form.querySelector('#statusOxygen').value = data.statusOxygen || 'N';
      form.querySelector('#statusSuction').value = data.statusSuction || 'N';
      form.querySelector('#nursingCost').value = data.nursingCost || '';
      form.querySelector('#note').value = data.note || '';
      form.querySelector('#useYn').value = data.useYn || 'Y';
      window.scrollTo({ top: form.offsetTop - 100, behavior: 'smooth' });
    });
  });

  document.getElementById('resetFormBtn')?.addEventListener('click', () => {
    form.reset();
    form.querySelector('#roomConfigId').value = '';
    roomConfigRows.forEach((item) => item.classList.remove('rb-row-selected'));
    setNow();
  });

  const buildPayload = () => new URLSearchParams({
    sourceType: sourceType.value || 'EXCEL_DETAIL',
    snapshotDate: snapshotDate.value || '',
    snapshotTime: snapshotTime.value || '',
    rawText: rawText.value || ''
  });

  const renderPreview = (data) => {
    previewBody.innerHTML = '';
    const rows = Array.isArray(data.rows) ? data.rows : [];
    if (!rows.length) {
      previewWrap.style.display = 'none';
      importMessage.textContent = data.message || '미리보기 데이터가 없습니다.';
      return;
    }
    rows.forEach((row) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>${row.wardName || ''}</td>
        <td>${row.roomName || ''}</td>
        <td>${row.patientNo || ''}</td>
        <td>${row.patientName || ''}</td>
        <td>${row.gender || ''}</td>
        <td>${row.age || ''}</td>
        <td>${row.admissionDate || ''}</td>
        <td>${row.doctorName || ''}</td>
        <td>${row.patientType || ''}</td>
        <td>${row.memo || ''}</td>
      `;
      previewBody.appendChild(tr);
    });
    previewWrap.style.display = '';
    importMessage.textContent = `${data.message || '미리보기 완료'} / 인식 ${data.parsedCount || 0}건`;
  };

  document.getElementById('previewImportBtn')?.addEventListener('click', async () => {
    if (!rawText.value.trim()) {
      alert('붙여넣기 데이터를 입력해 주세요.');
      return;
    }
    const response = await fetch('/csm/admin/room-board/import/preview', {
      method: 'POST',
      headers: requestHeaders,
      body: buildPayload().toString()
    });
    const data = await response.json();
    if (!response.ok) {
      alert(data.message || '미리보기에 실패했습니다.');
      return;
    }
    renderPreview(data);
  });

  document.getElementById('saveImportBtn')?.addEventListener('click', async () => {
    if (!rawText.value.trim()) {
      alert('붙여넣기 데이터를 입력해 주세요.');
      return;
    }
    if (!confirm('현재 붙여넣기 데이터를 저장하시겠습니까?')) {
      return;
    }
    const response = await fetch('/csm/admin/room-board/import/save', {
      method: 'POST',
      headers: requestHeaders,
      body: buildPayload().toString()
    });
    const data = await response.json();
    if (!response.ok) {
      alert(data.message || '가져오기에 실패했습니다.');
      return;
    }
    alert(`가져오기 완료: ${data.parsedCount || 0}건`);
    window.location.reload();
  });
});

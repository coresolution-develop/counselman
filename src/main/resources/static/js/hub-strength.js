/* 비밀번호 강도 인디케이터 (회원가입 · 계정 설정 공용).
   서버 검증을 대신하지 않는다 — 입력 중 눈으로 확인시켜 주는 표시일 뿐이다. */
(() => {
  'use strict';

  const LABELS = ['약함', '보통', '안전'];

  document.querySelectorAll('[data-strength-for]').forEach((meter) => {
    const input = document.getElementById(meter.dataset.strengthFor);
    if (!input) return;

    const bars = [...meter.querySelectorAll('.hub-strength__bar')];
    const text = meter.querySelector('.hub-strength__text');

    // 8자 이상 + 영문 + 숫자를 만족하면 3칸, 둘만 만족하면 2칸.
    const score = (value) => {
      if (!value) return 0;
      let hits = 0;
      if (value.length >= 8) hits += 1;
      if (/[A-Za-z]/.test(value)) hits += 1;
      if (/[0-9]/.test(value)) hits += 1;
      return hits;
    };

    const render = () => {
      const n = score(input.value);
      bars.forEach((bar, i) => bar.classList.toggle('hub-strength__bar--on', i < n));
      meter.classList.toggle('hub-strength--ok', n === 3);
      if (text) text.textContent = n === 0 ? '' : LABELS[n - 1];
    };

    input.addEventListener('input', render);
    render();
  });
})();

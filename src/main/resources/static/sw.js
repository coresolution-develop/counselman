// 병실현황판 PWA 서비스워커.
// 데이터는 항상 실시간이어야 하므로 캐싱하지 않고 네트워크로 통과시킨다(설치 가능 요건 충족용 최소 구현).
self.addEventListener('install', (e) => { self.skipWaiting(); });
self.addEventListener('activate', (e) => { e.waitUntil(self.clients.claim()); });
self.addEventListener('fetch', (e) => { /* network passthrough */ });

// 차량운행관리 관리자 콘솔: 차량/운전자/운행 수정 다이얼로그 프리필 + 닫기.
// 서버 렌더 표의 각 '수정/정정' 버튼 data-* 를 읽어 다이얼로그 폼을 채우고 action을 지정한다.
(function () {
    function q(sel) {
        return document.querySelector(sel);
    }

    function openDialog(dialog, form, actionPath) {
        if (!dialog || !form) {
            return;
        }
        form.setAttribute('action', actionPath);
        if (typeof dialog.showModal === 'function') {
            dialog.showModal();
        } else {
            dialog.setAttribute('open', '');
        }
    }

    function setField(form, name, value) {
        var el = form.elements[name];
        if (el) {
            el.value = value == null ? '' : value;
        }
    }

    document.querySelectorAll('.fleet-edit-vehicle').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var form = q('#form-vehicle');
            var d = btn.dataset;
            setField(form, 'plateNo', d.plate);
            setField(form, 'name', d.name);
            setField(form, 'modelName', d.model);
            setField(form, 'department', d.dept);
            openDialog(q('#dlg-vehicle'), form, '/fleet/admin/vehicles/' + d.id + '/update');
        });
    });

    document.querySelectorAll('.fleet-edit-driver').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var form = q('#form-driver');
            var d = btn.dataset;
            setField(form, 'name', d.name);
            setField(form, 'department', d.dept);
            setField(form, 'employeeNumber', d.emp);
            setField(form, 'username', d.username);
            setField(form, 'phone', d.phone);
            openDialog(q('#dlg-driver'), form, '/fleet/admin/drivers/' + d.id + '/update');
        });
    });

    document.querySelectorAll('.fleet-edit-trip').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var form = q('#form-trip');
            var d = btn.dataset;
            setField(form, 'purpose', d.purpose);
            setField(form, 'odometerStart', d.start);
            setField(form, 'odometerEnd', d.end);
            setField(form, 'purposeMemo', d.memo);
            // 진행 중(ONGOING) 운행은 종료 계기판 정정 불가 → 종료칸 숨김
            var endWrap = q('#trip-end-wrap');
            var ongoing = d.status === 'ONGOING';
            if (endWrap) {
                endWrap.style.display = ongoing ? 'none' : '';
            }
            if (ongoing) {
                setField(form, 'odometerEnd', '');
            }
            openDialog(q('#dlg-trip'), form, '/fleet/admin/trips/' + d.id + '/update');
        });
    });

    document.querySelectorAll('.fleet-dialog [data-close]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var dlg = btn.closest('dialog');
            if (dlg) {
                if (typeof dlg.close === 'function') {
                    dlg.close();
                } else {
                    dlg.removeAttribute('open');
                }
            }
        });
    });
})();

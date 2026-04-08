// 숨겨진 input에서 값 읽기 (데이터 없을 때도 JS 에러 없이 동작)
function readNumberValue(id, fallback = 0) {
	const el = document.getElementById(id);
	if (!el || el.value === undefined || el.value === null || el.value === '') return fallback;
	const n = Number(el.value);
	return Number.isFinite(n) ? n : fallback;
}

const firstYear = readNumberValue('firstYear');
const firstMonth = readNumberValue('firstMonth');
const lastYear = readNumberValue('lastYear');
const lastMonth = readNumberValue('lastMonth');

let admissionTypeData = []; // 전체 데이터를 저장해두는 전역 변수
let cachedAdmissionTypeData = [];
let cachedAdmissionSuccessData = [];
console.log("📌 날짜정보:", firstYear, firstMonth, lastYear, lastMonth);
const COMMON_TYPE_COLORS = {
    '기타': '#C98282',      // 기타는 항상 이 색상
    '대외협력': '#829FC9',   // 대외협력은 항상 이 색상
    '미지정': '#C99782',    // 미지정은 항상 이 색상
    '직원유치': '#8289C9',   // 직원유치는 항상 이 색상
    '협력병원': '#C9AD82',
    // 여기에 다른 타입들도 추가하고 원하는 색상을 지정합니다.
    // 위에 파이차트에 정의된 21가지 색상 중 원하는 색상을 골라 사용하거나 새로운 색상을 정의합니다.
    // 예를 들어, Highcharts 기본 색상 팔레트를 재사용할 수도 있습니다.
    'default_color_1': '#C9C282',
    'default_color_2': '#A6C982',
    'default_color_3': '#829FC9',
    'default_color_4': '#C98297',
    'default_color_5': '#BBC982',
    // ... 필요한 만큼 추가
};
// 월 옵션 구성
$('#yearSelect').on('change', function() {
	const selectedYear = Number($(this).val());
	const $monthSelect = $('#monthSelect').empty();
	let start = 1, end = 12;
	if (selectedYear === firstYear) start = firstMonth;
	if (selectedYear === lastYear) end = lastMonth;
	if (firstYear === lastYear) { start = firstMonth; end = lastMonth; }
	for (let i = start; i <= end; i++) {
		const selected = (selectedYear === lastYear && i === lastMonth) ? 'selected' : '';
		$monthSelect.append(`<option value="${i}" ${selected}>${i}월</option>`);
	}
});


console.log(`firstYear=${firstYear}, firstMonth=${firstMonth}, lastYear=${lastYear}, lastMonth=${lastMonth}`);


// 연도/월 또는 상담자 변경 시 통계 전체 재로딩
$('#yearSelect, #monthSelect, #counselorFilter').on('change', function() {
	const year = Number($('#yearSelect').val());
	const month = Number($('#monthSelect').val());
	const counselor = $('#counselorFilter').val(); // 선택된 상담자 값 가져오기

    // 년/월 값이 모두 있어야만 통계 로드
	if (year && month) {
		loadStatistics(year, month, counselor);
	}
});

//// 상담자 변경 시 통계 재로딩
//$('#counselorFilter').on('change', function() {
//	renderAdmissionTypeTable(cachedAdmissionTypeData);                      // 그대로 사용
//	renderAdmissionSuccessTypeTable(cachedAdmissionSuccessData, cachedAdmissionTypeData);  // ✅ 정확히 분리
//});
// 📌 통계 전체 불러오기 (counselor 파라미터가 모든 ajax 요청에 추가됨)
function loadStatistics(year, month, counselor = '') { // counselor 기본값은 '전체'
	const $chartArea = $('#chartarea').empty();

	// 모든 ajax 요청의 data 객체에 counselor를 추가합니다.
	const requestData = { year, month, counselor };

	// 상담 총 건수
	const allDataPromise = $.ajax({
		url: "/csm/statistics1",
		method: "GET",
		data: requestData, // 변경
		success: renderMonthlyTable,
		error: () => alert('상담 통계 조회 실패')
	});

	// 상담형태별 건수
	$.ajax({
		url: "/csm/statisticsType",
		method: "GET",
		data: requestData, // 변경
		success: renderCounselMethodTable,
		error: () => alert('상담형태별 통계 조회 실패')
	});

	// 입원연계 통계 + 차트 + 누적막대차트
	const successDataPromise = $.ajax({
		url: "/csm/statisticsAdmissionSuccess",
		method: "GET",
		data: requestData, // 변경
		success: data => {
			renderAdmissionChart(data);
			renderAdmissionStatsTable(cachedAdmissionSuccessData, cachedAdmissionTypeData);
			renderCounselorStackedMonthlyChart(data);
		},
		error: () => alert('입원완료 통계 조회 실패')
	});

	$.ajax({
		url: '/csm/statisticsByAdmissionType',
		method: 'GET',
		data: requestData, // 변경
		success: function(data) {
			cachedAdmissionTypeData = data;
			// '전체 상담자'로 조회했을 때만 상담자 목록을 다시 채웁니다.
			if (!counselor) {
				populateCounselorSelect(data);
			}
			renderAdmissionTypeTable(data);
		},
		error: function() {
			alert('연계유형 통계 조회 실패');
		}
	});

	$.ajax({
		url: '/csm/statisticsByAdmissionTypeSuccess',
		method: 'GET',
		data: requestData, // 변경
		success: function(data) {
			cachedAdmissionSuccessData = data;
			renderAdmissionSuccessTypeTable(data, cachedAdmissionTypeData);
			renderAdmissionSuccessTypeChart(data);
			renderAdmissionSuccessTypePieChart(data);
		},
		error: function() {
			alert('입원연계 성공 건수 조회 실패');
		}
	});

	$.ajax({
		url: '/csm/statisticsNonAdmissionReason',
		method: 'GET',
		data: requestData, // 변경
		success: function(data) {
			renderNonAdmissionReasonTable(data);
		},
		error: function() {
			alert('미입원 사유 통계 조회 실패');
		}
	});

	// 상담 vs 입원 비교 (Promise 로직은 그대로 유지)
	Promise.all([allDataPromise, successDataPromise])
    .then(([allData, successData]) => {
        // 이 시점에서는 allData와 successData가 모두 로드된 상태여야 합니다.
        console.log("Promise.all - Final successData:", successData);
        console.log("Promise.all - Final allData:", allData);

        // 여기서 renderAdmissionStatsTable 호출
        renderAdmissionStatsTable(successData, allData);
    })
    .catch(error => {
        console.error("Error loading statistics:", error);
        $('#admissionStatsTable').html('<tr><td colspan="99">데이터 로드 중 오류가 발생했습니다.</td></tr>');
    });
}



function renderCounselorStackedMonthlyChart(data) {
	const grouped = {}, monthSet = new Set(), counselorSet = new Set();

	// 상담자별 월별 데이터 그룹화
	data.forEach(({ month, counselor, count }) => {
		if (!counselor || !month) return;
		if (!grouped[counselor]) grouped[counselor] = {};
		grouped[counselor][month] = count;
		monthSet.add(month);
		counselorSet.add(counselor);
	});

	const months = [...monthSet].sort(); // x축
	const counselors = [...counselorSet].sort();

	const series = counselors.map(counselor => ({
		name: counselor,
		data: months.map(m => grouped[counselor][m] || 0)
	}));

	drawCounselorStackedMonthlyBarChart(months, series);
}
// 📌 페이지 초기 로딩
$(document).ready(function() {
	if (!window.HAS_DATA) {
		return;
	}
	$('#yearSelect').trigger('change');
	setTimeout(() => {
		const year = Number($('#yearSelect').val());
		const month = Number($('#monthSelect').val());
		const counselor = $('#counselorFilter').val();
		if (year && month) loadStatistics(year, month, counselor);
	}, 100);

	$('#exportStatisticsBtn').on('click', function() {
		const year = Number($('#yearSelect').val());
		const month = Number($('#monthSelect').val());
		const counselor = $('#counselorFilter').val() || '';
		if (!year || !month) {
			alert('내보낼 년월을 먼저 선택해주세요.');
			return;
		}
		const params = new URLSearchParams({
			year: String(year),
			month: String(month),
			counselor: counselor
		});
		window.location.href = `/csm/statistics/export?${params.toString()}`;
	});
});

function drawTotalCounselChart(totalPerMonth) {
	const labels = Object.keys(totalPerMonth).sort();
	const values = labels.map(month => totalPerMonth[month]);
	const formattedLabels = labels.map(m => m.replace('-', '.'));

	Highcharts.chart('counselTotalChart', {
		chart: {
			zoomType: 'xy',
			type: 'column',
			backgroundColor: 'white',
			style: {
				fontFamily: 'Pretendard',
				fontWeight: '400'
			}
		},
		title: { text: null },
		xAxis: { categories: formattedLabels },
		yAxis: {
			title: { text: null },
			stackLabels: {
				enabled: false
			},
			labels: {
				style: {
					color: '#ABABAB'
				}
			},
		},
		legend: {
			align: 'center',
			verticalAlign: 'top',
			borderColor: '',
			borderWidth: 0,
			shadow: false,
			itemDistance: 20
		},
		tooltip: {
			shared: true,
			valueSuffix: '건'
		},
		plotOptions: {
			series: {
				dataLabels: {
					enabled: true
				}
			}
		},
		credits: { enabled: false },
		exporting: { enabled: false },
		series: [
			{
				type: 'column',
				name: '상담 총 건수',
				data: values,
				color: '#264272'
			},
			//      {
			//        type: 'spline',
			//        name: '상담 총 건수 (추이선)',
			//        data: values
			//      }
		]
	});
}
let admissionChart;

function renderAdmissionChart(data) {
	if (!Array.isArray(data)) {
		$('#admissionChartCanvas').html('<div style="text-align:center;color:#999;">표시할 데이터가 없습니다.</div>');
		return;
	}
	// ⚠️ 상담자 정보 없이 월별 총 건수만 필터링
	const monthlyTotalMap = {};

	data.forEach(({ month, count, counselor }) => {
		if (!month || !count || !counselor) return;
		if (!monthlyTotalMap[month]) monthlyTotalMap[month] = 0;
		monthlyTotalMap[month] += count;
	});

	const months = Object.keys(monthlyTotalMap).sort();
	const counts = months.map(m => monthlyTotalMap[m]);
	if (months.length === 0) {
		$('#admissionChartCanvas').html('<div style="text-align:center;color:#999;">표시할 데이터가 없습니다.</div>');
		return;
	}

	Highcharts.chart('admissionChartCanvas', {
		chart: {
			zoomType: 'xy',
			type: 'column',
			backgroundColor: 'white',
			style: {
				fontFamily: 'Pretendard',
				fontWeight: '400'
			}
		},
		title: { text: null },
		xAxis: { categories: months.map(m => m.replace('-', '.')) },
		yAxis: {
			title: { text: null },
			stackLabels: {
				enabled: false
			},
			labels: {
				style: {
					color: '#ABABAB'
				}
			},
		},
		legend: {
			align: 'center',
			verticalAlign: 'top',
			borderColor: '',
			borderWidth: 0,
			shadow: false,
			itemDistance: 20
		},
		tooltip: {
			shared: true,
			valueSuffix: '건'
		},
		plotOptions: {
			series: {
				dataLabels: {
					enabled: true
				}
			}
		},
		credits: { enabled: false },
		exporting: { enabled: false },
		series: [
			{
				type: 'column',
				name: '입원 연계 성공 건수',
				data: counts,
				color: '#264272'
			},
			//      {
			//        type: 'spline',
			//        name: '추이',
			//        data: counts
			//      }
		]
	});
}

function renderCompareBarChart(months, counselCounts, admissionCounts) {
	const categories = months.map(m => m.replace('-', '.'));

	Highcharts.chart('compareChart', {
		chart: { type: 'column' },
		title: { text: null },
		xAxis: { categories: categories },
		yAxis: {
			title: { text: null },
			stackLabels: {
				enabled: false
			},
			labels: {
				style: {
					color: '#ABABAB'
				}
			},
		},
		legend: {
			align: 'center',
			verticalAlign: 'top',
			borderColor: '',
			borderWidth: 0,
			shadow: false,
			itemDistance: 20
		},
		tooltip: {
			shared: true,
			valueSuffix: '건'
		},
		plotOptions: {
			series: {
				dataLabels: {
					enabled: true
				}
			}
		},
		credits: { enabled: false },
		exporting: { enabled: false },
		series: [
			{
				name: '상담 총 건수',
				data: counselCounts,
				color: '#264272'
			},
			{
				name: '입원 연계 총 건수',
				data: admissionCounts,
				color: '#F99C11'
			}
		]
	});
}

function drawCounselorStackedMonthlyBarChart(months, seriesData) {
	if (!document.getElementById('chartarea')) {
		return;
	}
	const categories = months.map(m => m.replace('-', '.'));

	Highcharts.chart('chartarea', {
		chart: {
			type: 'column',
			backgroundColor: 'white',
			style: {
			    fontFamily: 'Pretendard',  
				fontWeight: '400'  
			},
			marginTop: 80
		},
		colors: [
			"#C98282", // hsl(  0,40%,65%)
			"#829FC9", // hsl(216,40%,65%)
			"#C99782", // hsl( 18,40%,65%)
			"#8289C9", // hsl(234,40%,65%)
			"#C9AD82", // hsl( 36,40%,65%)
			"#C9C282", // hsl( 54,40%,65%)
			"#A6C982", // hsl( 90,40%,65%)
			"#829FC9", // hsl(216,40%,65%)
			"#C98297", // hsl(342,40%,65%)
			"#BBC982", // hsl( 72,40%,65%)
			"#90C982", // hsl(108,40%,65%)
			"#82C989", // hsl(126,40%,65%)
			"#BB82C9", // hsl(288,40%,65%)
			"#82C99F", // hsl(144,40%,65%)
			"#C982C2", // hsl(306,40%,65%)
			"#82C9B4", // hsl(162,40%,65%)
			"#82C9C9", // hsl(180,40%,65%)
			"#82B4C9", // hsl(198,40%,65%)
			"#9082C9", // hsl(252,40%,65%)
			"#A682C9", // hsl(270,40%,65%)
			"#C982AD"  // hsl(324,40%,65%)
		],
		title: { text: null },
		xAxis: {
			categories: categories,
			title: { text: null },
			crosshair: false,
			labels: {
			    style: {
			        color: '#474747'
			    }
			}
		},
		yAxis: {
			min: 0,
			max: 100,
			title: {
				text: ''
			},
			stackLabels: {
				enabled: false
			},
			formatter() {
				return `${this.total}건`;
			},
			labels: {
				style: {
					color: '#ABABAB'
				}
			},
			labels: {
				format: '{value}%'
			},
		},
		legend: {
			align: 'center',
			verticalAlign: 'top',
			borderColor: '',
			borderWidth: 0,
			shadow: false,
			itemDistance: 20
		},
		tooltip: {
			pointFormat: '<span style="color:{series.color}">{series.name}</span>: <b>{point.percentage:.1f}%</b> ({point.y}건)<br/>',
			shared: true
		},
		plotOptions: {
			column: {
				stacking: 'percent', // 💡 퍼센트 누적
				borderWidth: 0,
				borderColor: 'transparent',
				dataLabels: {
					enabled: true,
					format: '{point.percentage:.1f}%',
				}
			}
		},
		credits: { enabled: false },
		exporting: { enabled: false },
		legend: { align: 'right', verticalAlign: 'top' },
		series: seriesData
	});
}
function renderAdmissionSuccessTypeChart(successData) {
	const selectedCounselor = $('#counselorFilter').val();
	const normalized = successData.map(d => ({
		...d,
		type: (d.type && d.type.trim()) ? d.type.trim() : '미지정'
	}));

	const filtered = selectedCounselor
		? normalized.filter(d => d.counselor === selectedCounselor)
		: normalized;

	const monthsSet = new Set(filtered.map(d => d.month));
	if (monthsSet.size === 0) {
		$('#admissionSuccessTypeChart').html('<div style="text-align:center;color:#999;">표시할 데이터가 없습니다.</div>');
		return;
	}

	const months = [...monthsSet].sort();
	const types = [...new Set(filtered.map(d => d.type))].sort(); // 타입도 정렬하여 일관성 유지

	const seriesData = types.map(type => ({
		name: type,
		data: months.map(month =>
			filtered.find(d => d.month === month && d.type === type)?.count || 0
		),
		// 📌 여기에 색상을 할당합니다.
		// COMMON_TYPE_COLORS 맵에서 해당 타입의 색상을 가져오고, 없으면 기본 색상을 할당합니다.
		color: COMMON_TYPE_COLORS[type] || COMMON_TYPE_COLORS['default_color_1'] // 맵에 없는 타입에 대한 폴백 색상
	}));
    
    // console.log("Bar chart seriesData with colors:", seriesData); // 확인용

	Highcharts.chart('admissionSuccessTypeChart', {
		chart: {
			type: 'column',
			backgroundColor: 'white',
			style: {
			    fontFamily: 'Pretendard',  
				fontWeight: '400'  
			},
			marginTop: 80
		},
		// 📌 Highcharts의 전역 'colors' 배열은 여기서 제거하거나 비워둡니다.
		// 각 시리즈에 직접 색상을 지정했기 때문입니다.
		colors: [], // 또는 이 줄을 완전히 제거해도 됩니다.
		title: { text: null },
		xAxis: {
			categories: months.map(m => m.replace('-', '. ')),
			title: { text: null },
			crosshair: false,
			labels: {
			    style: {
			        color: '#474747'
			    }
			}
		},
		yAxis: {
			min: 0,
			max: 100, // 백분율 스택 차트이므로 max: 100이 적절
			title: {
				text: ''
			},
			stackLabels: {
				enabled: false
			},
			// formatter: function() { return `${this.total}건`; } // 스택 라벨이 비활성화되어 있으므로 이 formatter는 적용되지 않습니다.
			labels: {
				format: '{value}%', // y축 레이블을 백분율로 포맷
				style: {
					color: '#ABABAB'
				}
			},
		},
		legend: { 
			align: 'center', // 범례 위치 중앙으로 변경 (개선)
			verticalAlign: 'top',
			borderColor: '',
			borderWidth: 0,
			shadow: false,
			itemDistance: 20 // 범례 항목 간 간격
		},
		tooltip: {
			pointFormat: '<span style="color:{series.color}">{series.name}</span>: <b>{point.percentage:.1f}%</b> ({point.y}건)<br/>',
			shared: true
		},
		plotOptions: {
			column: {
				stacking: 'percent', // 100% 스택 컬럼 차트
				dataLabels: {
					enabled: true,
					format: '{point.percentage:.1f}%', // 각 세그먼트의 백분율 표시
					style: { textOutline: 'none' } // 텍스트 외곽선 제거
				}
			}
		},
		credits: { enabled: false },
		exporting: { enabled: false },
		// legend 속성이 두 번 정의되어 있습니다. 하나를 제거하거나 병합해야 합니다.
		// 위의 legend 속성만 남기는 것이 좋습니다.
		// legend: { align: 'right', verticalAlign: 'top' }, // 이 부분은 중복이므로 제거합니다.
		series: seriesData
	});
}
function normalizeType(type) {
    // type이 null, undefined, 또는 빈 문자열일 경우 '미지정'으로 통일
    if (!type || type.trim() === '') {
        return '미지정';
    }
    // 그 외의 경우, 앞뒤 공백 제거 후 반환
    return type.trim();
}
function renderAdmissionSuccessTypePieChart(successData) {
	const months = [...new Set(successData.map(d => d.month))].sort();
	const lastMonthStr = months[months.length - 1];
	const displayMonthStr = lastMonthStr?.replace('-', '. ') || '-';

	const filteredByMonth = successData.filter(d => d.month === lastMonthStr);

	const aggregatedData = filteredByMonth.reduce((acc, item) => {
		const name = normalizeType(item.type);
		const count = item.count || 0;
		acc[name] = (acc[name] || 0) + count;
		return acc;
	}, {});

	const chartData = Object.keys(aggregatedData).map(name => ({
		name: name,
		y: aggregatedData[name],
		color: COMMON_TYPE_COLORS[name] || COMMON_TYPE_COLORS['default_color_1'] // 정의된 색상이 없으면 기본 색상 사용
	})).filter(item => item.y > 0);
	console.log("Final chartData for pie chart:", chartData); // 여기에 추가

	const container = $('#admissionSuccessTypePie');

	if (chartData.length === 0) {
		container.html('<div style="text-align:center;color:#999;">표시할 데이터가 없습니다.</div>');
		return;
	}

	Highcharts.chart('admissionSuccessTypePie', {
		chart: {
			type: 'pie',
			backgroundColor: 'white',
			style: { fontFamily: 'Pretendard' },
			margin: [0, 150, 0, 0]
		},
		colors: [],
		title: { text: '', verticalAlign: 'middle' },
		tooltip: {
			pointFormat: '<b>{point.y}건</b> ({point.percentage:.1f}%)'
		},
		accessibility: {
			point: { valueSuffix: '%' }
		},
		legend: {
			enabled: true,
			align: 'right',
			verticalAlign: 'middle',
			layout: 'vertical',
			itemMarginBottom: 12,
			labelFormatter: function () {
				return `${this.name}: <span style="font-weight:bold;">${this.percentage.toFixed(1)}%</span>`;
			}
		},
		plotOptions: {
			pie: {
                // 📌 size 속성을 추가하여 차트 크기 조절
				size: '40%', // 숫자를 조절하여 크기 변경 가능 (e.g., '80%', '150px')
				innerSize: '50%',
				showInLegend: true,
				dataLabels: { enabled: false },
				borderWidth: 0,
				borderColor: 'transparent'
			}
		},
		credits: { enabled: false },
		exporting: { enabled: false },
		series: [{
			name: '비율',
//			colorByPoint: true,
			data: chartData
		}]
	});
}
function renderMonthlyTable(data) {
	if (!Array.isArray(data) || data.length === 0) {
		$('#monthlyStatsTable').html('<thead><tr><th>상담자</th><th>데이터</th></tr></thead><tbody><tr><td colspan="2">표시할 데이터가 없습니다.</td></tr></tbody>');
		drawTotalCounselChart({});
		return;
	}
	const months = [...new Set(data.map(row => row.month))].sort();
	const counselors = [...new Set(data.map(row => row.counselor))];
	if (months.length === 0) {
		$('#monthlyStatsTable').html('<thead><tr><th>상담자</th><th>데이터</th></tr></thead><tbody><tr><td colspan="2">표시할 데이터가 없습니다.</td></tr></tbody>');
		drawTotalCounselChart({});
		return;
	}

	const tableData = {};
	const totalPerMonth = {};
	const totalPerCounselor = {};

	let lastMonth = months[months.length - 1];
	let prevMonth = months.length >= 2 ? months[months.length - 2] : null;
	let totalThisMonth = totalPerMonth[lastMonth] || 0;

	counselors.forEach(function(name) {
		tableData[name] = {};
	});

	data.forEach(function(row) {
		const month = row.month;
		const counselor = row.counselor;
		const count = row.count;

		tableData[counselor][month] = count;
		totalPerMonth[month] = (totalPerMonth[month] || 0) + count;
		totalPerCounselor[counselor] = (totalPerCounselor[counselor] || 0) + count;
	});

	const lastMonthLabel = lastMonth.replace('-', '. ');

	let html = '<thead><tr><th>상담자</th>';
	months.forEach(function(m) {
		const formatted = m.replace(/(\d{4})-(\d{1,2})/, (_, y, mo) => `${y}. ${mo.padStart(2, '0')}`);
		html += '<th>' + formatted + '</th>';
	});
	html += '<th>상담자별 비율</th><th>월평균 상담건수</th><th>총상담건수</th>';
	html += '</tr></thead><tbody>';

	counselors.forEach(function(counselor) {
		let sum = 0;
		html += '<tr><td>' + counselor + '</td>';

		months.forEach(function(month) {
			const count = tableData[counselor][month] || 0;
			html += '<td>' + count + '</td>';
			sum += count;
		});

		const lastCount = tableData[counselor][lastMonth] || 0;
		const avg = (sum / months.length).toFixed(2);
		const ratio = totalPerMonth[lastMonth] > 0
			? ((lastCount / totalPerMonth[lastMonth]) * 100).toFixed(2) + '%'
			: '-';

		html += '<td>' + ratio + '</td><td>' + avg + '건</td><td>' + sum + '건</td></tr>';
	});

	html += '<tr class="total" style=""><td>상담 총 건수</td>';
	let totalSum = 0;
	months.forEach(function(month) {
		const val = totalPerMonth[month] || 0;
		totalSum += val;
		html += '<td>' + val + '</td>';
	});
	html += '<td>100.00%</td><td>' + (totalSum / months.length).toFixed(2) + '건</td><td>' + totalSum + '건</td></tr>';

	html += `<tr class="pmcount" style=""><td>전월대비 증감건수</td>`;
	months.forEach(function(month, idx) {
		if (idx === 0) {
			html += '<td>-</td>';
		} else {
			const prev = totalPerMonth[months[idx - 1]] || 0;
			const curr = totalPerMonth[month] || 0;
			const diff = curr - prev;

			if (diff > 0) {
				html += `<td><span style="color:#264272;">▲ ${diff}</span></td>`;
			} else if (diff < 0) {
				html += `<td><span style="color:#F99C11;">▼ ${Math.abs(diff)}</span></td>`;
			} else {
				html += '<td>-</td>';
			}
		}
	});
	html += '<td></td><td></td><td></td></tr>';

	html += '<tr class="pmper" style=""><td>전월대비 증감률(%)</td>';
	months.forEach(function(month, idx) {
		if (idx === 0) {
			html += '<td>-</td>';
		} else {
			const prev = totalPerMonth[months[idx - 1]] || 0;
			const curr = totalPerMonth[month] || 0;

			if (prev === 0) {
				html += '<td>-</td>';
			} else {
				const rate = (((curr - prev) / prev) * 100).toFixed(2);
				const color = rate > 0 ? '#264272' : rate < 0 ? '#F99C11' : 'black';
				const symbol = rate > 0 ? '▲ ' : rate < 0 ? '▼ ' : '';
				html += `<td><span style="color:${color};">${symbol}${Math.abs(rate)}%</span></td>`;
			}
		}
	});
	html += '<td></td><td></td><td></td></tr>';

	html += '</tbody>';

	$('#monthlyStatsTable').html(html);
}
function renderCounselMethodTable(data) {
	if (!Array.isArray(data) || data.length === 0) {
		$('#monthlyMethodStatsTable').html('<thead><tr><th>상담형태</th><th>데이터</th></tr></thead><tbody><tr><td colspan="2">표시할 데이터가 없습니다.</td></tr></tbody>');
		return;
	}
	const months = [...new Set(data.map(row => row.month))].sort();
	const methods = [...new Set(data.map(row => row.method))];
	if (months.length === 0) {
		$('#monthlyMethodStatsTable').html('<thead><tr><th>상담형태</th><th>데이터</th></tr></thead><tbody><tr><td colspan="2">표시할 데이터가 없습니다.</td></tr></tbody>');
		return;
	}

	const tableData = {};
	const totalPerMonth = {};
	const totalPerMethod = {};

	// 초기화
	methods.forEach(method => {
		tableData[method] = {};
	});

	// 데이터 집계
	data.forEach(row => {
		const { month, method, count } = row;
		tableData[method][month] = count;
		totalPerMonth[month] = (totalPerMonth[month] || 0) + count;
		totalPerMethod[method] = (totalPerMethod[method] || 0) + count;
	});

	// 마지막 달, 라벨
	const lastMonth = months[months.length - 1];
	const lastMonthLabel = lastMonth.replace('-', '.');

	let html = '<thead><tr><th>상담형태</th>';
	months.forEach(m => {
		const label = m.replace('-', '.');
		html += `<th>${label}</th>`;
	});
	html += `<th>상담자별 비율</th><th>월평균 상담건수</th><th>총상담건수</th>`;
	html += '</tr></thead><tbody>';

	// 각 상담형태별 데이터 행
	methods.forEach(method => {
		let sum = 0;
		html += `<tr><td>${method}</td>`;

		months.forEach(month => {
			const cnt = tableData[method][month] || 0;
			html += `<td>${cnt}</td>`;
			sum += cnt;
		});

		const lastCount = tableData[method][lastMonth] || 0;
		const totalLastMonth = totalPerMonth[lastMonth] || 0;
		const ratio = totalLastMonth > 0 ? ((lastCount / totalLastMonth) * 100).toFixed(2) + '%' : '-';
		const avg = (sum / months.length).toFixed(2);

		html += `<td>${ratio}</td><td>${avg}건</td><td>${sum}건</td></tr>`;
	});

	// 총합 행
	html += '<tr class="total"><td>총합</td>';
	let grandTotal = 0;
	months.forEach(month => {
		const val = totalPerMonth[month] || 0;
		grandTotal += val;
		html += `<td>${val}</td>`;
	});
	html += `<td>100.00%</td><td>${(grandTotal / months.length).toFixed(2)}건</td><td>${grandTotal}건</td></tr>`;

	html += '</tbody>';

	$('#monthlyMethodStatsTable').html(html);
	drawTotalCounselChart(totalPerMonth);
}
function renderAdmissionStatsTable(successData, allData) {
	console.log("renderAdmissionStatsTable - successData:", successData);
    console.log("renderAdmissionStatsTable - allData:", allData);
    if (!Array.isArray(successData) || !Array.isArray(allData)) {
        $('#admissionStatsTable').html('<tr><td colspan="99">데이터가 올바르지 않습니다.</td></tr>');
        return;
    }

    // 모든 월 목록을 allData와 successData에서 추출하여 합치고 정렬.
    // filter(Boolean)을 사용하여 null, undefined, 빈 문자열 등을 제거합니다.
    const allMonths = [...new Set([...allData, ...successData].map(d => d.month).filter(Boolean))].sort();
    const monthLabels = allMonths.map(m => m.replace('-', '.'));

    if (allMonths.length === 0) {
        $('#admissionStatsTable').html('<tr><td colspan="99">표시할 월별 데이터가 없습니다.</td></tr>');
        return;
    }

    const lastMonthStr = allMonths[allMonths.length - 1];
    if (!lastMonthStr) { // lastMonthStr가 undefined일 경우
        $('#admissionStatsTable').html('<tr><td colspan="99">마지막 월 데이터를 찾을 수 없습니다.</td></tr>');
        return;
    }

    // allData와 successData에서 모든 상담자 이름을 추출하고 중복을 제거하여 정렬
    const counselors = [...new Set([
        ...allData.map(d => d.counselor).filter(Boolean),
        ...successData.map(d => d.counselor).filter(Boolean)
    ])].sort();

    const totalAll = {}, totalSuccess = {};
    const monthAll = {}, monthSuccess = {};

    // 모든 상담자에 대해 monthAll과 monthSuccess 객체를 미리 초기화
    counselors.forEach(c => {
        totalAll[c] = 0;
        totalSuccess[c] = 0;
        monthAll[c] = {};   // { '이선경': {} }
        monthSuccess[c] = {}; // { '이선경': {} }
    });

    // allData를 monthAll에 채우기
    allData.forEach(d => {
        const { month, counselor, count = 0 } = d;
        if (!counselor || !month) return; // counselor 또는 month가 없으면 스킵

        // 이미 위에서 monthAll[counselor]가 초기화되었지만, 혹시 모를 경우를 대비한 방어코드 유지
        if (!monthAll[counselor]) {
            monthAll[counselor] = {};
        }
        totalAll[counselor] = (totalAll[counselor] || 0) + count;
        monthAll[counselor][month] = (monthAll[counselor][month] || 0) + count;
    });

    // successData를 monthSuccess에 채우기
    successData.forEach(d => {
        const { month, counselor, count = 0 } = d;
        if (!counselor || !month) return; // counselor 또는 month가 없으면 스킵

        // 이미 위에서 monthSuccess[counselor]가 초기화되었지만, 혹시 모를 경우를 대비한 방어코드 유지
        if (!monthSuccess[counselor]) {
            monthSuccess[counselor] = {};
        }
        totalSuccess[counselor] = (totalSuccess[counselor] || 0) + count;
        monthSuccess[counselor][month] = (monthSuccess[counselor][month] || 0) + count;
    });

    let html = '<thead><tr><th>상담자</th>';
    monthLabels.forEach(label => {
        html += `<th>${label}</th>`;
    });
    html += `<th>연계유형별 비율</th><th>월평균 건수</th><th>총 건수</th>`;
    html += '</tr></thead><tbody>';

    const counselorTotalLast = counselors.reduce((acc, c) => acc + ((monthSuccess[c] && monthSuccess[c][lastMonthStr]) || 0), 0);

    counselors.forEach(c => {
        let sum = 0;
        html += `<tr><td>${c}</td>`;
        allMonths.forEach(m => {
            // chart.js:908 이 오류가 발생하는 지점이 이 라인일 가능성이 매우 높습니다.
            // monthSuccess[c]가 undefined일 경우를 대비하여 `monthSuccess[c] &&` 추가
            const val = (monthSuccess[c] && monthSuccess[c][m]) || 0;
            html += `<td>${val}</td>`;
            sum += val;
        });
        const avg = (sum / allMonths.length).toFixed(2);
        
        const lastMonthSuccess = (monthSuccess[c] && monthSuccess[c][lastMonthStr]) || 0;
        const lastMonthAll = (monthAll[c] && monthAll[c][lastMonthStr]) || 0; // monthAll[c]도 안전하게 접근
        
        let ratio = '-';
        if (lastMonthAll > 0) {
            ratio = ((lastMonthSuccess / lastMonthAll) * 100).toFixed(2) + '%';
        } else if (lastMonthSuccess === 0 && lastMonthAll === 0) {
            ratio = '0.00%';
        } else {
            ratio = 'N/A';
        }

        html += `<td>${ratio}</td><td>${avg}</td><td>${sum}</td></tr>`;
    });

    // ... (이하 동일한 로직, monthSuccess[c] 및 monthAll[c] 안전 접근 유지)
    // ⬇ 총 건수: 마지막월 비율 합산 계산 추가
    html += `<tr class="total"><td>총 건수</td>`;
    allMonths.forEach(m => {
        const total = counselors.reduce((acc, c) => acc + ((monthSuccess[c] && monthSuccess[c][m]) || 0), 0);
        html += `<td>${total}</td>`;
    });

    const ratioSum = counselors.reduce((acc, c) => {
        const val = (monthSuccess[c] && monthSuccess[c][lastMonthStr]) || 0;
        return acc + (counselorTotalLast ? (val / counselorTotalLast) * 100 : 0);
    }, 0).toFixed(2);

    const totalSum = counselors.reduce((acc, c) => acc + totalSuccess[c], 0);
    const avgTotal = (totalSum / allMonths.length).toFixed(2);
    html += `<td>${ratioSum}%</td><td>${avgTotal}</td><td>${totalSum}</td></tr>`;

    // ⬇ 전월대비 증감건수
    html += `<tr class="pmcount" style=""><td>전월대비 증감건수</td>`;
    allMonths.forEach((m, i) => {
        if (i === 0) {
            html += '<td>-</td>';
        } else {
            const prev = counselors.reduce((acc, c) => acc + ((monthSuccess[c] && monthSuccess[c][allMonths[i - 1]]) || 0), 0);
            const curr = counselors.reduce((acc, c) => acc + ((monthSuccess[c] && monthSuccess[c][m]) || 0), 0);
            const diff = curr - prev;
            const color = diff > 0 ? '#264272' : diff < 0 ? '#F99C11' : 'black';
            const symbol = diff > 0 ? '▲ ' : diff < 0 ? '▼ ' : '';
            html += `<td><span style="color:${color};">${symbol}${Math.abs(diff)}</span></td>`;
        }
    });
    html += `<td></td><td></td><td></td></tr>`;

    // ⬇ 전월대비 증감률
    html += `<tr style="background-color:#f0f8ff;"><td>전월대비 증감률(%)</td>`;
    allMonths.forEach((m, i) => {
        if (i === 0) {
            html += '<td>-</td>';
        } else {
            const prev = counselors.reduce((acc, c) => acc + ((monthSuccess[c] && monthSuccess[c][allMonths[i - 1]]) || 0), 0);
            const curr = counselors.reduce((acc, c) => acc + ((monthSuccess[c] && monthSuccess[c][m]) || 0), 0);
            if (prev === 0) {
                html += '<td>-</td>';
            } else {
                const rate = (((curr - prev) / prev) * 100).toFixed(2);
                const color = rate > 0 ? '#264272' : rate < 0 ? '#F99C11' : 'black';
                const symbol = rate > 0 ? '▲ ' : rate < 0 ? '▼ ' : '';
                html += `<td><span style="color:${color};">${symbol}${Math.abs(rate)}%</span></td>`;
            }
        }
    });
    html += `<td></td><td></td><td></td></tr>`;

    $('#admissionStatsTable').html(html);
}

function renderAdmissionTypeTable(data) {
	const selectedCounselor = $('#counselorFilter').val();

	const normalizedData = data.map(d => ({
		...d,
		type: (d.type && d.type.trim()) ? d.type.trim() : '미지정'
	}));

	// 상담자 select 구성
	if ($('#counselorFilter').children().length === 0) {
		const counselors = [...new Set(normalizedData.map(row => row.counselor).filter(Boolean))].sort();
		let options = '<option value="">전체 상담자</option>';
		counselors.forEach(c => {
			options += `<option value="${c}">${c}</option>`;
		});
		$('#counselorFilter').html(options);
	}

	const filtered = selectedCounselor
		? normalizedData.filter(row => row.counselor === selectedCounselor)
		: normalizedData;

	const types = [...new Set(filtered.map(d => d.type))];
	const months = [...new Set(filtered.map(d => d.month))].sort();
	const formattedMonths = months.map(m => m.replace('-', '. '));
	const lastMonthKey = months[months.length - 1];
	const formattedLastMonth = lastMonthKey.replace('-', '. ');

	const typeMonthCount = {};
	const totalPerMonth = {};
	const totalPerMonthAll = {};

	// 전체 총합 계산 (전월대비용)
	normalizedData.forEach(d => {
		const month = d.month;
		const count = d.count || 0;
		totalPerMonthAll[month] = (totalPerMonthAll[month] || 0) + count;
	});

	// 필터된 상담자 기준 집계
	filtered.forEach(d => {
		const { type, month, count = 0 } = d;
		if (!typeMonthCount[type]) typeMonthCount[type] = {};
		typeMonthCount[type][month] = (typeMonthCount[type][month] || 0) + count;
		totalPerMonth[month] = (totalPerMonth[month] || 0) + count;
	});

	// ⬇ 테이블 그리기
	let html = `<thead><tr><th>연계유형</th>`;
	formattedMonths.forEach(m => html += `<th>${m}</th>`);
	html += `<th>입원유형별<br>입원연계비율</th><th>월평균 건수</th><th>총 건수</th></tr></thead><tbody>`;

	const totalAllLastMonth = totalPerMonth[lastMonthKey] || 0;

	const ratioSumList = [];

	types.forEach(type => {
		const rowData = typeMonthCount[type] || {};
		let sum = 0;
		html += `<tr><td>${type}</td>`;
		months.forEach(m => {
			const cnt = rowData[m] || 0;
			sum += cnt;
			html += `<td>${cnt}</td>`;
		});
		const avg = (sum / months.length).toFixed(2);
		const ratio = totalAllLastMonth
			? (((rowData[lastMonthKey] || 0) / totalAllLastMonth) * 100).toFixed(2)
			: '-';

		if (ratio !== '-') ratioSumList.push(parseFloat(ratio));

		html += `<td>${ratio !== '-' ? ratio + '%' : '-'}</td><td>${avg}</td><td>${sum}</td></tr>`;
	});

	// 총건수 행
	html += `<tr class="total"><td>총 건수</td>`;
	let grandTotal = 0;
	months.forEach(m => {
		const sum = types.reduce((acc, t) => acc + (typeMonthCount[t]?.[m] || 0), 0);
		html += `<td>${sum}</td>`;
		grandTotal += sum;
	});

	const totalRatioSum = ratioSumList.reduce((a, b) => a + b, 0).toFixed(2);

	html += `<td>${totalRatioSum}%</td>`;
	html += `<td>${(grandTotal / months.length).toFixed(2)}</td>`;
	html += `<td>${grandTotal}</td></tr>`;

	// 전월대비 증감건수
	html += `<tr class="pmcount" style=""><td>전월대비 증감건수</td>`;
	months.forEach((m, i) => {
	    if (i === 0) {
	        html += '<td>-</td>';
	    } else {
	        // 🚨 totalPerMonthAll 대신 totalPerMonth 사용
	        const prev = totalPerMonth[months[i - 1]] || 0;
	        const curr = totalPerMonth[m] || 0;
	        const diff = curr - prev;
	        const color = diff > 0 ? '#264272' : diff < 0 ? '#F99C11' : 'black';
	        const symbol = diff > 0 ? '▲ ' : diff < 0 ? '▼ ' : '';
	        html += `<td><span style="color:${color};">${symbol}${Math.abs(diff)}</span></td>`;
	    }
	});
	html += `<td></td><td></td><td></td></tr>`;


	// 전월대비 증감률
	html += `<tr style="background-color:#f0f8ff;"><td>전월대비 증감률(%)</td>`;
	months.forEach((m, i) => {
		if (i === 0) {
			html += '<td>-</td>';
		} else {
			// 🚨 totalPerMonthAll 대신 totalPerMonth 사용
			const prev = totalPerMonth[months[i - 1]] || 0;
			const curr = totalPerMonth[m] || 0;
			if (prev === 0) {
				html += '<td>-</td>';
			} else {
				const rate = (((curr - prev) / prev) * 100).toFixed(2);
				const color = rate > 0 ? '#264272' : rate < 0 ? '#F99C11' : 'black';
				const symbol = rate > 0 ? '▲ ' : rate < 0 ? '▼ ' : '';
				html += `<td><span style="color:${color};">${symbol}${Math.abs(rate)}%</span></td>`;
			}
		}
	});
	html += `<td></td><td></td><td></td></tr>`;

	html += `</tbody>`;
	$('#admissionTypeTable').html(html);
}
// counselor select 동적 구성

function populateCounselorSelect(data) {
	const $filter = $('#counselorFilter');
	if ($filter.children().length <= 1) {  // 최초 한 번만 생성
		const counselors = [...new Set(data.map(d => d.counselor).filter(Boolean))].sort();
		counselors.forEach(c => {
			$filter.append(`<option value="${c}">${c}</option>`);
		});
	}
}
function renderAdmissionSuccessTypeTable(successData, typeData) {
	const selectedCounselor = $('#counselorFilter').val();

	// 🔹 type이 없으면 '미지정'으로 치환
	const normalizedData = successData.map(d => ({
		...d,
		type: (d.type && d.type.trim()) ? d.type.trim() : '미지정'
	}));

	const filtered = selectedCounselor
		? normalizedData.filter(row => row.counselor === selectedCounselor)
		: normalizedData;

	const types = [...new Set(filtered.map(d => d.type))];
	const months = [...new Set(filtered.map(d => d.month))].sort();

	const typeMonthCount = {};
	const totalPerMonth = {};
	const totalPerMonthAll = {};

	// 전체 기준 월별 총합 (증감용)
	normalizedData.forEach(d => {
		const { month, count = 0 } = d;
		totalPerMonthAll[month] = (totalPerMonthAll[month] || 0) + count;
	});

	// 필터 기준 집계
	filtered.forEach(d => {
		const { type, month, count = 0 } = d;
		if (!typeMonthCount[type]) typeMonthCount[type] = {};
		typeMonthCount[type][month] = (typeMonthCount[type][month] || 0) + count;
		totalPerMonth[month] = (totalPerMonth[month] || 0) + count;
	});

	const lastMonthStr = months[months.length - 1] || '';
	const formattedLastMonth = lastMonthStr.replace('-', '. ');

	// 📌 테이블 렌더링
	let html = '<thead><tr><th>연계유형</th>';
	months.forEach(m => html += `<th>${m.replace('-', '. ')}</th>`);
	html += `<th>미입원사유별<br>비율</th><th>월평균 건수</th><th>총 건수</th></tr></thead><tbody>`;

	const lastMonth = months[months.length - 1];
	const totalAllLastMonth = totalPerMonth[lastMonth] || 0;
	const ratioSumList = [];

	types.forEach(type => {
		const rowData = typeMonthCount[type] || {};
		let sum = 0;

		html += `<tr><td>${type}</td>`;
		months.forEach(m => {
			const cnt = rowData[m] || 0;
			sum += cnt;
			html += `<td>${cnt}</td>`;
		});

		const avg = (sum / months.length).toFixed(2);
		const ratio = totalAllLastMonth
			? (((rowData[lastMonth] || 0) / totalAllLastMonth) * 100).toFixed(2)
			: '-';

		if (ratio !== '-') ratioSumList.push(parseFloat(ratio));

		html += `<td>${ratio !== '-' ? ratio + '%' : '-'}</td><td>${avg}</td><td>${sum}</td></tr>`;
	});

	// ✅ 총 건수 행 추가
	html += `<tr class="total"><td>총 건수</td>`;
	let grandTotal = 0;
	months.forEach(m => {
		const total = types.reduce((acc, t) => acc + (typeMonthCount[t]?.[m] || 0), 0);
		html += `<td>${total}</td>`;
		grandTotal += total;
	});

	const totalRatioSum = ratioSumList.reduce((a, b) => a + b, 0).toFixed(2);
	html += `<td>${totalRatioSum}%</td>`;
	html += `<td>${(grandTotal / months.length).toFixed(2)}</td>`;
	html += `<td>${grandTotal}</td></tr>`;

	// 📌 증감건수
	html += `<tr class="pmcount" style=""><td>전월대비 증감건수</td>`;
	months.forEach((m, i) => {
		if (i === 0) {
			html += '<td>-</td>';
		} else {
			const prev = totalPerMonthAll[months[i - 1]] || 0;
			const curr = totalPerMonthAll[m] || 0;
			const diff = curr - prev;
			const color = diff > 0 ? '#264272' : diff < 0 ? '#F99C11' : 'black';
			const symbol = diff > 0 ? '▲ ' : diff < 0 ? '▼ ' : '';
			html += `<td><span style="color:${color};">${symbol}${Math.abs(diff)}</span></td>`;
		}
	});
	html += '<td></td><td></td><td></td></tr>';

	// 📌 증감률
	html += '<tr style="background-color:#f0f8ff;"><td>전월대비 증감률(%)</td>';
	months.forEach((m, i) => {
		if (i === 0) {
			html += '<td>-</td>';
		} else {
			const prev = totalPerMonthAll[months[i - 1]] || 0;
			const curr = totalPerMonthAll[m] || 0;
			if (prev === 0) {
				html += '<td>-</td>';
			} else {
				const rate = (((curr - prev) / prev) * 100).toFixed(2);
				const color = rate > 0 ? '#264272' : rate < 0 ? '#F99C11' : 'black';
				const symbol = rate > 0 ? '▲ ' : rate < 0 ? '▼ ' : '';
				html += `<td><span style="color:${color};">${symbol}${Math.abs(rate)}%</span></td>`;
			}
		}
	});
	html += '<td></td><td></td><td></td></tr>';

	html += '</tbody>';
	$('#admissionSuccessTypeTable').html(html);
}


function renderNonAdmissionReasonTable(data) {
	if (!Array.isArray(data) || data.length === 0) {
		$('#nonAdmissionReasonTable').html('<thead><tr><th>미입원 사유</th><th>데이터가 없습니다.</th></tr></thead>');
		return;
	}

	const normalized = data.map(d => ({
		month: d.month,
		type: (d.type && d.type.trim()) ? d.type.trim() : '미지정',
		count: d.count || 0
	}));

	const months = [...new Set(normalized.map(d => d.month))].sort();
	const types = [...new Set(normalized.map(d => d.type))].sort();
	const lastMonth = months[months.length - 1];

	const statsMap = {};
	const lastMonthTotalMap = {};
	const totalPerMonth = {};

	types.forEach(type => {
		statsMap[type] = {};
		months.forEach(month => {
			statsMap[type][month] = 0;
		});
	});

	months.forEach(month => {
		lastMonthTotalMap[month] = 0;
		totalPerMonth[month] = 0;
	});

	normalized.forEach(row => {
		const { type, month, count } = row;
		statsMap[type][month] = count;
		lastMonthTotalMap[month] += count;
		totalPerMonth[month] += count;
	});

	let html = '<thead><tr><th>미입원 사유</th>';
	months.forEach(month => {
		html += `<th>${month.replace('-', '.')}</th>`;
	});
	html += `<th>미입원사유별<br>비율</th><th>월평균 건수</th><th>총 건수</th></tr></thead><tbody>`;

	const lastMonthTotal = lastMonthTotalMap[lastMonth];
	let ratioSum = 0;

	types.forEach(type => {
		let sum = 0;
		html += `<tr><td>${type}</td>`;

		months.forEach(month => {
			const count = statsMap[type][month];
			sum += count;
			html += `<td>${count}</td>`;
		});

		const avg = (sum / months.length).toFixed(2);
		const lastCount = statsMap[type][lastMonth];
		const ratio = lastMonthTotal > 0 ? ((lastCount / lastMonthTotal) * 100).toFixed(2) : '-';

		if (ratio !== '-') ratioSum += parseFloat(ratio);

		html += `<td>${ratio !== '-' ? ratio + '%' : '-'}</td><td>${avg}</td><td>${sum}</td></tr>`;
	});

	let totalRow = '<tr class="total"><td>총 건수</td>';
	let grandTotal = 0;

	months.forEach(month => {
		const monthlyTotal = totalPerMonth[month];
		totalRow += `<td>${monthlyTotal}</td>`;
		grandTotal += monthlyTotal;
	});

	const totalAvg = (grandTotal / months.length).toFixed(2);
	totalRow += `<td>${ratioSum.toFixed(2)}%</td><td>${totalAvg}</td><td>${grandTotal}</td></tr>`;

	html += totalRow + '</tbody>';
	$('#nonAdmissionReasonTable').html(html);
}

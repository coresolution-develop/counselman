<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta charset="UTF-8">
<title>문자관리 : SOSYGE EASY-CRM</title>
<!-- SNS 공유시 썸네일 표시(보통 헤드에 들어감) -->
<meta property="og:title" content="상담관리시스템">	<!-- 공유시 사이트 이름 -->
<meta property="og:type" content="website">
<meta property="og:url" content="http://csm.sosyge.net/csm/login">	<!-- 공유시 입력될 URL -->
<meta property="og:image" content="/resources/icon/ev/core_icon_big.png">	<!-- 공유시 노출 이미지 (최적 사이즈 : 800 x 400) -->
<meta property="og:description" content="병원상담관리시스템">	<!-- 공유시 보여질 사이트 간략 소개 -->

<link rel="shortcut icon" href="/resources/favicon/favicon.ico">
<link rel="apple-touch-icon" sizes="180x180" href="/resources/favicon/favicon.ico">
<link rel="icon" sizes="192x192" href="/resources/favicon/favicon.ico">
<script src="https://code.jquery.com/jquery-3.6.0.js"></script>
<link rel="stylesheet" href="/resources/css/reset.css">
<link rel="stylesheet" href="/resources/css/csm/core/admin/admin.css">
<link rel="stylesheet" href="/resources/css/csm/core/admin/style.css">
<link rel="stylesheet" href="/resources/css/csm/core/admin/smssetting.css">
<link rel="stylesheet" href="/resources/css/csm/core/popup/popup.css">
<link rel="stylesheet" href="/resources/css/csm/Include/spinner.css">
<link rel="stylesheet" href="/resources/css/csm/Include/button.css">
<link href="/resources/css/csm/Include/modal.css" rel="stylesheet">
<style type="text/css">
.bill.hidden {
	display: none;
}
.popup {
	width: 100%;
	height: 70%; 

}
.templateFirstInsert {
    border: 1px solid #ddd;
    padding: 5px;
    border-radius: 5px;
    transition: background-color 0.3s ease, color 0.3s ease; /* Smooth transition */
}

.templateFirstInsert:hover {
    background-color: #2f425a;; /* Change to black background */
    color: #fff; /* Optional: Change text color to white for contrast */
   	border: #2f425a;
}
.grid-row4 {
	grid-template-columns: 200px 1fr 1fr;
}
</style>
</head>
<body>
    <!-- 인클루드 헤더 -->
    <jsp:include page="/WEB-INF/views/Include/csm/csm_Head.jsp" />
    <jsp:include page="/WEB-INF/views/Include/csm/csm_Cate.jsp" />
	<div class="main">

		<!-- 중앙 콘텐츠 -->
		<div class="section">
			<div class="content-top" style="height: 0;">
				<div class="section-top">
					<div class="content-inner">
						<div class="inner-top">
							<div class="inner-section">
								<div class="inner-area">
									<span style="cursor:pointer; color: #06603A; font-weight: 500; font-size: 12px;" onclick="location.href='/csm/core/setting'"></span>
								</div>
							</div>
						</div>
					</div>
					
				</div>
			</div>
			<div class="content-mid">
				<div class="section-mid">
					<div class="smsTemplateDiv" style="width: 100%;" id="myTable">
						<div class="sms-history-grid4">
							<div class="grid-header4" style="grid-template-columns: 200px 1fr 1fr; height: 100%; align-items: center;">
								<div style="height: 100%; display: flex; justify-content: center; align-items: center;">기관명</div>
								<div style="display: flex; flex-direction: column; border-right: 1px solid #D2CFDA; border-left: 1px solid #D2CFDA;">
									<div style="padding-bottom: 10px;">단가</div>
									<div style="display: flex; width: 100%; justify-content: space-evenly;">
										<div style="width: 100%;">SMS</div>
										<div style="width: 100%;">LMS</div>
										<div style="width: 100%;">MMS</div>
									</div>
								</div>
								<div style="display: flex; flex-direction: column;">
									<div style="padding-bottom: 10px;">이달의 사용량</div>
									<div style="display: flex; width: 100%; justify-content: space-evenly;">
										<div style="width: 100%;">SMS</div>
										<div style="width: 100%;">LMS</div>
										<div style="width: 100%;">MMS</div>
										<div style="width: 100%;">총 사용량</div>
									</div>
								</div>
							</div>
						</div>
						<c:set var="instCount" value="0"/>
						<div class="grid-body4">
							<c:choose>
								<c:when test="${empty list }">
									<div class="grid-row4">
										<div>기관 없습니다</div>
									</div>
								</c:when>
								<c:otherwise>
									<c:forEach items="${list}" var="l">
									    <c:if test="${l.id_col_03 ne 'core'}">
									        <c:set var="usage" value="${countAll[l.id_col_03]}" />
									        <c:set var="monthly" value="${thisMonthUsage[l.id_col_03]}" />
									
									        <!-- 전체 사용 건수 -->
									        <c:set var="sms" value="${not empty usage.sms ? usage.sms : 0}" />
									        <c:set var="lms" value="${not empty usage.lms ? usage.lms : 0}" />
									        <c:set var="mms" value="${not empty usage.mms ? usage.mms : 0}" />
									
									        <!-- 이번달 사용 건수 -->
									        <c:set var="smsMonth" value="${not empty monthly.sms ? monthly.sms : 0}" />
									        <c:set var="lmsMonth" value="${not empty monthly.lms ? monthly.lms : 0}" />
									        <c:set var="mmsMonth" value="${not empty monthly.mms ? monthly.mms : 0}" />
									
									        <!-- 단가 -->
									        <c:set var="smsPrice" value="${l.sms_price}" />
									        <c:set var="lmsPrice" value="${l.lms_price}" />
									        <c:set var="mmsPrice" value="${l.mms_price}" />
									
									        <!-- 총 사용 금액 -->
									        <c:set var="total" value="${sms * smsPrice + lms * lmsPrice + mms * mmsPrice}" />
									
									        <!-- 이번달 사용 금액 -->
									        <c:set var="monthTotal" value="${smsMonth * smsPrice + lmsMonth * lmsPrice + mmsMonth * mmsPrice}" />
									
									        <div class="grid-row4 open-bill"
									             data-template-idx="${l.id_col_01}"
									             data-inst-code="${l.id_col_03}"
									             data-sms="${l.sms_price}"
									             data-lms="${l.lms_price}"
									             data-mms="${l.mms_price}">
									
									            <!-- 기관명 -->
									            <div>${l.id_col_02}</div>
									
									            <!-- 단가 -->
									            <div style="display: flex; width: 100%; justify-content: space-evenly;">
									                <div style="width: 100%;">${smsPrice}원</div>
									                <div style="width: 100%;">${lmsPrice}원</div>
									                <div style="width: 100%;">${mmsPrice}원</div>
									            </div>
									
									            <!-- 이달 사용량 -->
									            <div style="display: flex; width: 100%; justify-content: space-evenly;">
									                <div style="width: 100%;">${smsMonth}건</div>
									                <div style="width: 100%;">${lmsMonth}건</div>
									                <div style="width: 100%;">${mmsMonth}건</div>
									                <div style="width: 100%;"><fmt:formatNumber value="${monthTotal}" type="number" />원</div>
									            </div>
									        </div>
									
									        <c:set var="instCount" value="${instCount + 1}" />
									        
									    </c:if>
									</c:forEach>
								</c:otherwise>
							</c:choose>
						</div>
						<!-- 기관 리스트 렌더링 후, 하단에 고정된 청구서 div 1개 -->
						<div class="bill hidden" id="bill-template">
						    <div class="bill-wrapper">
						        <div class="bill-area1">
						            <span class="title-label" style="margin: 5px 0;">청구서 조회</span>
						            <div>
						                <select class="monthSelector" id="monthSelectorGlobal">
						                    <!-- 동적으로 기관 클릭 시 채워짐 -->
						                </select>
						            </div>
						        </div>
						        <div class="bill-area3">
						            <div class="bill-contents-header">
						                <div><span>사용년월</span></div>
						                <div><span>SMS건 수</span></div>
						                <div><span>단가</span></div>
						                <div><span>LMS건 수</span></div>
						                <div><span>단가</span></div>
						                <div><span>MMS건 수</span></div>
						                <div><span>단가</span></div>
						                <div><span>합계(부가세 별도)</span></div>
						            </div>
						            <div class="bill-contents-body monthlyDetailContainer" id="monthlyDetailContainerGlobal">
						                <!-- AJAX 데이터 표시 영역 -->
						            </div>
						        </div>
						    </div>
						</div>
						
						<!-- 청구서 출력 영역 (여기에 복제된 청구서가 들어감) -->
						<div id="bill-output-container"></div>
					</div>
				</div>
				<div class="section-bot">
					<div>
						<div class="search-status-area">
							<div>
								<span>검색수 : <span class="count">${instCount }건</span></span>
							</div>
							<div class="status-area">
								<div class="section-btn">
									<div class="btn counsel-btn delBtn" id="modi-price">단가수정</div>
									<div class="section-btn btn newBtn pop-btn counsel-btn" id="new-price">단가등록</div>
								</div>
							</div>
						</div>
					</div>
				</div>
			</div>
			<div class="content-bot">
			</div>
		</div>
	</div>

	<!-- 모달 -->
	<div class="popup-wrap" id="popup">
		<div class="popup">
			<div class="popup-head">
				<span class="head-title" id="head-title">템플릿 추가</span>
			</div>
			<form action="/csm/core/setting/template" method="post" >
				<div class="popup-body">
					<div class="body-content">
						<div class="body-contentbox">
							<div class="category-section">
						        <div id="" class="category-container">
							        <div class="category-header">
								        <h3>대분류</h3>
										<div class="settingDiv">
											<img class="search-text" id="setting-main" src="/resources/icon/ev/setting_off.png">
										</div>
							        </div>
						            
						            <div class="category-container-wrap">
							            <div id="mainCategoryDiv">
							                <c:forEach var="category1Data" items="${categoryData}">
												<label class="custom-radio">
													<input type="radio" data-category-type="parent" name="mainCategory" value="${category1Data.category1.cc_col_01}" onclick="getSubCategories('${category1Data.category1.cc_col_01}', this)">
													<span class="custom-radio-mark"></span>
													${category1Data.category1.cc_col_02}
												</label>
							                </c:forEach>
							            </div>
							            <div id="main" class="cateButton">
			    							<button type="button" class="btn orderBtn" id="save-order-main" style="display: none;" >순서저장</button>
								            <button type="button" class="btn newBtn" id="new-maincategory" data-category-type="parent" >대분류 추가</button>
								            <button type="button" class="btn delBtn" id="del-maincategory" data-category-type="parent" >대분류 삭제</button>
							            </div>
						            </div>
						        </div>
								<div id="" class="category-container">
									<div class="category-header">
										<h3>소분류</h3>
							            <div class="settingDiv">
											<img class="search-text" id="setting-sub" src="/resources/icon/ev/setting_off.png">
										</div>
									</div>
						            <div class="category-container-wrap">
							            <div id="subCategoryDiv"></div>
							            <div id="sub" class="cateButton">
							            	<button type="button" class="btn orderBtn" id="save-order-sub" style="display: none;">순서저장</button>
								            <button type="button" class="btn newBtn" id="new-subcategory" data-category-type="child" disabled>소분류 추가</button>
								            <button type="button" class="btn delBtn" id="del-subcategory" data-category-type="child" disabled>소분류 삭제</button>
							            </div>
						            </div>
								</div>
								<div id="" class="category-container">
									<div class="category-header">
										<h3>옵션</h3>
							            <div class="settingDiv">
											<img class="search-text" id="setting-option" src="/resources/icon/ev/setting_off.png">
										</div>
									</div>
						            <div class="category-container-wrap">
							            <div id="optionCategoryDiv"></div>
							            <div id="option" class="cateButton">
							            	<button type="button" class="btn orderBtn" id="save-order-option" style="display: none;">순서저장</button>
								            <button type="button" class="btn newBtn" id="new-optioncategory" data-category-type="option" disabled>옵션 추가</button>
								            <button type="button" class="btn delBtn" id="del-optioncategory" data-category-type="option" disabled>옵션 삭제</button>
							            </div>
						            </div>
								</div>
							</div>
						</div>
						
					</div>
				</div>
				<div class="popup-foot">
					<span class="pop-btn close" id="close">취소</span> 
					<span class="pop-btn confirm" id="save">저장</span>
				</div>
			</form>
		</div>
	</div>
		
	<div class="modal3 normal">
		<div class="modal_body3">
			<div>
				<img class="menu_icon" src="${pageContext.request.contextPath}/resources/img/alert_img.png">
				<div class="menu_msg3">text</div>
				<div style="display: flex; justify-content: center; padding-bottom: 15px;">
					<div style="width: 300px;">
						<div style="display: flex; justify-content: center; align-items: center; margin-bottom: 5px;">
							<div style="margin-right: 5px; width: 35px;">SMS</div>
							<input type="text"style="margin-bottom: 0px; height: 40px; font-size: 12px;" class="modal-input" id="smspriceAll" name="smspriceAll" placeholder="SMS 가격">
						</div>
						<div style="display: flex; justify-content: center; align-items: center; margin-bottom: 5px;">
							<div style="margin-right: 5px; width: 35px;">LMS</div>
							<input type="text"style="margin-bottom: 0px; height: 40px; font-size: 12px;" class="modal-input" id="lmspriceAll" name="lmspriceAll" placeholder="LMS 가격">
						</div>
						<div style="display: flex; justify-content: center; align-items: center; margin-bottom: 5px;">	
							<div style="margin-right: 5px; width: 35px;">MMS</div>
							<input type="text"style="margin-bottom: 0px; height: 40px; font-size: 12px;" class="modal-input" id="mmspriceAll" name="mmspriceAll" placeholder="MMS 가격">
						</div>
					</div>
				</div>
				
				<div class="modal_footer3">
					<div class="btn modal_btn2 listBtn" id="priceAllConfirmBtn">
						확인
					</div>
					<div class="btn pink_btn2 listBtn" onclick="closePopup3()">
						취소
					</div>
				</div>
			</div>
		</div>
	</div>
	<!-- 분류 추가 모달 -->
	<div class="modal4 normal">
		<div class="modal_body4">
			<div>
				<img class="menu_icon" src="${pageContext.request.contextPath}/resources/img/alert_img.png">
				<div class="menu_msg4">새로운 분류를 입력해주세요.</div>
				<input type="text" class="modal-input" id="cc_col_02" name="cc_col_02" placeholder="분류 이름 입력">
				<div id="checkboxContainer">
	                <label class="custom-checkbox">
				        <input type="checkbox" id="isCheckbox" onclick="toggleCheckboxRadio('checkbox')">
				        <span class="custom-checkbox-mark"></span>
				        Checkbox
				    </label><br>
				
				    <label class="custom-checkbox">
				        <input type="checkbox" id="isRadio" onclick="toggleCheckboxRadio('radio')">
				        <span class="custom-checkbox-mark"></span>
				        Radio Box
				    </label><br>
				
				    <label class="custom-checkbox">
				        <input type="checkbox" id="isSelectbox" onclick="toggleSelectOptions()">
				        <span class="custom-checkbox-mark"></span>
				        Select Box
				    </label><br>
				
				    <label class="custom-checkbox">
				        <input type="checkbox" id="isTextbox">
				        <span class="custom-checkbox-mark"></span>
				        Text Box
				    </label><br>
	            </div>
	
	            <!-- Select box options (shown only if Select Box is checked) -->
	            <div id="selectOptions" style="display: none;">
	                <input type="text" id="selectboxOptions" placeholder="선택박스 옵션(콤마로 구분)">
	            </div>
				<div class="modal_footer4">
					<div class="btn modal_btn2 listBtn" id ="addbtn">
						확인
					</div>
					<div class="btn pink_btn2 listBtn" onclick="closePopup4()">
						취소
					</div>
				</div>
			</div>
		</div>
	</div>
	<div class="modal5 normal">
		<div class="modal_body5">
			<div>
				<img class="menu_icon" src="${pageContext.request.contextPath}/resources/img/alert_img.png">
				<div class="menu_msg5">text</div>
				<div style="display: flex; justify-content: center; padding-bottom: 15px;">
					<div style="width: 300px;">
						<div style="display: flex; justify-content: center; align-items: center; margin-bottom: 5px;">
							<div style="margin-right: 5px; width: 35px;">SMS</div>
							<input type="text"style="margin-bottom: 0px; height: 40px; font-size: 12px;" class="modal-input" id="smsprice" name="smsprice" placeholder="SMS 가격">
						</div>
						<div style="display: flex; justify-content: center; align-items: center; margin-bottom: 5px;">
							<div style="margin-right: 5px; width: 35px;">LMS</div>
							<input type="text"style="margin-bottom: 0px; height: 40px; font-size: 12px;" class="modal-input" id="lmsprice" name="lmsprice" placeholder="LMS 가격">
						</div>
						<div style="display: flex; justify-content: center; align-items: center; margin-bottom: 5px;">	
							<div style="margin-right: 5px; width: 35px;">MMS</div>
							<input type="text"style="margin-bottom: 0px; height: 40px; font-size: 12px;" class="modal-input" id="mmsprice" name="mmsprice" placeholder="MMS 가격">
						</div>
					</div>
				</div>
				<div class="modal_footer5">
					<div class="btn modal_btn2 listBtn" id="priceConfirmBtn">
						확인
					</div>
					<div class="btn pink_btn2 listBtn" onclick="closePopup5()">
						취소
					</div>
				</div>
			</div>
		</div>
	</div>
	<div id="spinner-overlay" style="display: none;">
		<div class="spinner-container">
			<div class="spinner"></div>
			<p>잠시만 기다려주세요</p>
		</div>
	</div>
	
<script src="/resources/js/csm/core/admin/smssetting.js"></script>
</body>
<script>

$('.menu3', $('.nav_section')).addClass('active');

let selectedInst = null;

$(document).ready(function () {
    $('.open-bill').on('click', function () {
        const month = $(this).val();
        selectedInst = $(this).data('inst-code');

        console.log('선택된 월:', month); // <-- 여기서 찍히는 값이 정확해야 함
        // 이전에 열려있는 bill 제거
        $('.bill.dynamic').remove();

        // 템플릿 복제 → 고유 ID 부여
        const $template = $('#bill-template').clone().removeAttr('id');
        $template.addClass('dynamic').removeClass('hidden');
        const $selector = $template.find('.monthSelector');
        const $container = $template.find('.monthlyDetailContainer');

        // 1. 셀렉트박스 초기화
        $selector.empty().append(`<option value="all">전체기간</option>`);

        // 2. AJAX로 월 리스트 가져오기
        $.ajax({
            url: '/csm/adminMonthList',
            method: 'GET',
            data: { inst: selectedInst },
            success: function (months) {
                months.forEach(month => {
                    $selector.append(`<option value="\${month}">\${month}</option>`);
                });

                // 기본 전체기간 데이터 로딩
                loadBillData(selectedInst, 'all', $container);
            }
        });

        // 셀렉트박스 변경 시 재조회
        $selector.on('change', function () {
            const month = $(this).val();
            loadBillData(selectedInst, month, $container);
        });

        // 3. 화면에 출력
        $('#bill-output-container').append($template);
    });

    function loadBillData(inst, month, $container) {
    	console.log('선택 기관:', inst);
    	console.log('선택 월:', month);
        $.ajax({
            url: '/csm/adminMonthlyUsage',
            method: 'GET',
            data: { inst: inst, month: month },
            success: function (data) {
                let html = '';
                if (Array.isArray(data)) {
                    data.forEach(item => html += renderRow(item));
                } else {
                    html = renderRow(data);
                }
                $container.html(html);
            },
            error: function () {
                $container.html('<div>조회 실패</div>');
            }
        });
    }

    function renderRow(data) {
        return `
            <div class="bill-contents-row">
                <div><span>\${data.month}</span></div>
                <div><span>\${data.sms}</span></div>
                <div><span>\${data.smsPrice}원</span></div>
                <div><span>\${data.lms}</span></div>
                <div><span>\${data.lmsPrice}원</span></div>
                <div><span>\${data.mms}</span></div>
                <div><span>\${data.mmsPrice}원</span></div>
                <div><span>\${Number(data.total).toLocaleString()}원</span></div>
            </div>
        `;
    }
});
</script>
</html>
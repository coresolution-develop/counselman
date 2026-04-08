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
<title>상담일지관리 : SOSYGE EASY-CRM</title>
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
<link rel="stylesheet" href="/resources/css/csm/core/admin/setting.css">
<link rel="stylesheet" href="/resources/css/csm/core/popup/popup.css">
<link rel="stylesheet" href="/resources/css/csm/Include/spinner.css">
<link rel="stylesheet" href="/resources/css/csm/Include/button.css">
<link href="/resources/css/csm/Include/modal.css" rel="stylesheet">
<style type="text/css">
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
</style>
</head>
<body>
    <!-- 인클루드 헤더 -->
    <jsp:include page="/WEB-INF/views/Include/csm/csm_Head.jsp" />
    <jsp:include page="/WEB-INF/views/Include/csm/csm_Cate.jsp" />
	<div class="main">

		<!-- 중앙 콘텐츠 -->
		<div class="section">
			<div class="content-top">
				<div class="section-top">
					<div class="content-inner">
						<div class="inner-top">
							<div class="inner-section">
								<div class="inner-area">
									<span style="cursor:pointer; color: #06603A; font-weight: 500; font-size: 12px;" onclick="location.href='/csm/core/setting'">템플릿 관리</span>
									| 
									<span style="cursor:pointer; margin-left: 5px;" onclick="location.href='/csm/core/categorysetting'" >상담일지양식 관리</span>
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
							<div class="grid-header4">
								<div>템플릿명</div>
								<div>비고</div>
								<div>상태</div>
							</div>
						</div>
						<c:set var="instCount" value="0"/>
						<div class="grid-body4">
							<c:choose>
								<c:when test="${empty template }">
									<div class="grid-row4">
										<div colspan="3">템플릿이 없습니다. 생성하시겠습니까?<br><br><span class="templateFirstInsert">생성하기 +</span></div>
									</div>
								</c:when>
								<c:otherwise>
								<c:forEach items="${template }" var="t">
								<div class="grid-row4" data-template-idx="${t.idx }">
									<div>${t.name }</div>
									<div>${t.description }</div>
									<div>
										<c:choose>
											<c:when test="${t.del_yn eq 'Y' }">
												비활성화
											</c:when>
											<c:otherwise>
												활성화
											</c:otherwise>
										</c:choose>
									</div>
								</div>	
								<c:set var="instCount" value="${instCount + 1}"/>
							</c:forEach>
								</c:otherwise>
							</c:choose>
							
							
						</div>
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
									<div class="btn counsel-btn" id="user-del">탬플릿삭제</div>
									<div class="btn counsel-btn delBtn" id="user-modi">수정</div>
									<c:choose>
										<c:when test="${empty template }">
										
										</c:when>
										<c:otherwise>
											<div class="section-btn btn newBtn pop-btn counsel-btn" id="user-new">템플릿등록</div>
										</c:otherwise>
									</c:choose>
									
								</div>
							</div>
						</div>
					</div>
				</div>
			</div>
			<div class="content-bot">
			</div>
		</div>

<!-- 		<!-- 우측 내비 --> 
<!-- 		<div class="nav-right"> -->
<!-- 			<div style="height: 100%"> -->
<!-- 				<div style="height: 50%; border: 1px solid #ededed;"> -->
<!-- 					<div style="height: 35px;">사용중지자 목록</div> -->
<!-- 					<div style="height: calc(100% - 35px);"> -->
<!-- 						<table> -->
<!-- 							<thead> -->
<!-- 								<tr> -->
<!-- 									<th>사용자명</th> -->
<!-- 									<th>소속기관</th> -->
<!-- 								</tr> -->
<!-- 							</thead> -->
<!-- 							<tbody> -->
<!-- 								<tr> -->
<!-- 									<td></td> -->
<!-- 									<td></td> -->
<!-- 								</tr> -->
<!-- 							</tbody> -->
<!-- 						</table> -->
<!-- 						<div class="stop-btn">사용중지 해제</div> -->
<!-- 					</div> -->
<!-- 				</div> -->
<!-- 				<div style="height: 50%;"> -->
<!-- 					<div>일자별 접속기록</div> -->
<!-- 					<div style="display: flex;"> -->
<!-- 						<span>접속일</span><input type="date"> -->
<!-- 					</div> -->
<!-- 					<div> -->
<!-- 						<table> -->
<!-- 							<thead> -->
<!-- 								<tr> -->
<!-- 									<th>접속시간</th> -->
<!-- 									<th>접속자명</th> -->
<!-- 								</tr> -->
<!-- 							</thead> -->
<!-- 							<tbody> -->
<!-- 								<tr> -->
<!-- 									<td></td> -->
<!-- 									<td></td> -->
<!-- 								</tr> -->
<!-- 							</tbody> -->
<!-- 						</table> -->
<!-- 					</div> -->
<!-- 				</div> -->
<!-- 			</div> -->
<!-- 		</div> -->

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
				<input type="text"style="margin-bottom: 10px;" class="modal-input" id="templateName" name="templateName" placeholder="템플릿 이름 입력">
				<input type="text" class="modal-input" id="templateDescription" name="templateDescription" placeholder="템플릿 설명 입력">
				<div class="modal_footer3">
					<div class="btn modal_btn2 listBtn" id="TemplateConfirmBtn">
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
				<input type="text"style="margin-bottom: 10px;" class="modal-input" id="templateNameModi" name="templateNameModi" placeholder="템플릿 이름 입력">
				<input type="text" class="modal-input" id="templateDescriptionModi" name="templateDescriptionModi" placeholder="템플릿 설명 입력">
				<div class="modal_footer5">
					<div class="btn modal_btn2 listBtn" id="TemplateModifyBtn">
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
	
<script src="/resources/js/csm/core/admin/setting.js"></script>
</body>
<script>

$('.menu2', $('.nav_section')).addClass('active');
</script>
</html>
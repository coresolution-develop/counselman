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
<title>기관/사용자등록 : SOSYGE EASY-CRM</title>
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
<script src="/resources/js/csm/core/admin/newuser.js"></script>
<link rel="stylesheet" href="/resources/css/reset.css">
<link rel="stylesheet" href="/resources/css/csm/core/admin/admin.css">
<link rel="stylesheet" href="/resources/css/csm/core/admin/style.css">
<link rel="stylesheet" href="/resources/css/csm/core/popup/popup.css">
<style type="text/css">
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
									<span style="cursor:pointer; " onclick="location.href='/csm/core/admin'">기관</span>
									| 
									<span style="cursor:pointer; color: #06603A; font-weight: 500; margin: 5px; font-size: 12px;" onclick="location.href='/csm/core/user'">관리자</span>
								</div>
							</div>
						</div>
					</div>
					
				</div>
			</div>
			<div class="content-mid">
				<div class="section-mid">
					<div class="smsTemplateDiv" style="width: 100%;" id="myTable">
						<div class="sms-history-grid3">
							<div class="grid-header3">
								<div>관리자명</div>
								<div>기관코드</div>
								<div>기관명</div>
								<div>이메일</div>
								<div>연락처</div>
							</div>
						</div>
						<c:set var="instCount" value="0"/>
						<div class="grid-body3">
							<c:forEach items="${user }" var="u">
								<div class="grid-row3">
									<div>${u.us_col_01 }</div>
									<div>${u.us_col_02 }</div>
									<div>${u.us_col_03 }</div>
									<div>${u.us_col_06 }</div>
									<div>${u.us_col_05 }
										<input type="hidden" name="id" id="id" value="${u.id}">
										<input type="hidden" name="inst" id="inst" value="${u.us_col_02 }">
										<input type="hidden" name="userid" id="userid" value="${u.us_col_01 }">
									</div>
								</div>
								<c:set var="instCount" value="${instCount + 1}"/>
							</c:forEach>
							
						</div>
					</div>
				</div>
				<div class="section-bot">
					<div>
						<div class="search-status-area">
							<div>
								<span>검색수: ${instCount }건</span>
							</div>
							<div class="status-area">
								<div class="section-btn">
									<div class="btn delBtn btnw100 listBtn2" id="user-del">관리자삭제</div>
<!-- 									<div id="user-modi">수정</div> -->
									<div class="btn newBtn btnw100 listBtn2" id="user-new" onclick="openUserPopup()">관리자등록</div>
								</div>
							</div>
						</div>
					</div>
				</div>
			</div>
<!-- 			<div class="content-bot"> -->
<!-- 				<div> -->
<!-- 					<div> -->
<!-- 						<div class="log-area"> -->
<!-- 							<span>활동기록</span> -->
<!-- 							<input type="date"> -->
<!-- 							<span>~</span> -->
<!-- 							<input type="date"> -->
<!-- 						</div> -->
<!-- 						<div class="section-mid"> -->
<!-- 							<div class="user-list" > -->
<!-- 								<div> -->
<!-- 									<div class="grid-header2"> -->
<!-- 										<div>일자</div> -->
<!-- 										<div>시간</div> -->
<!-- 										<div>활동내역</div> -->
<!-- 									</div> -->
<!-- 								</div> -->
<!-- 							</div> -->
<!-- 						</div> -->
<!-- 					</div> -->
<!-- 				</div> -->
<!-- 			</div> -->
		</div>

		<!-- 우측 내비 -->
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
				<span class="head-title" id="head-title">사용자 추가</span>
			</div>
			<form action="/csm/core/inst/post" method="post" >
				<div class="popup-body">
					<div class="body-content">
						<!--           <div class="body-titlebox"> -->
						<!--             <h1>Confirm Modal</h1> -->
						<!--           </div> -->
						<div class="body-contentbox">
								<div>
									<div>
										<span class="content1">사용자명 :</span> <input type="text" id="us_col_02" name="us_col_02"> <span>(로그인 아이디)</span>
									</div>
									<div>
										<span class="content1">비밀번호 :</span> <input type="password" id="us_col_03" name="us_col_03" autocomplete="off"> <span>(신규 비밀번호)</span>
									</div>
									<div>
										<span class="content1">비번확인 :</span> <input type="password" id="us_col_03_h" name="id_col_03_h" autocomplete="off"> <span>(비밀번호 확인)</span>
									</div>
									<div>
										<span class="content1">소속기관 :</span> 
										<select name="us_col_05" id="us_col_05">
											<option>선택하세요</option>
											<c:forEach items="${ list}" var="l">
												<option value="${l.id_col_02 }" data-col-03="${l.id_col_03 }">${l.id_col_02 }</option>
											</c:forEach>
											
										</select>
									</div>
									<div>
										<span class="content1">사용권한 :</span>
										<select name="us_col_08" id="us_col_08">
											<option value="">선택하세요</option>
											<option value="1">1</option>
											<option value="2">2</option>
											<option value="3">3</option>
											<option value="4">4</option>
										</select>
									</div>
									<div>
										<span class="content1">사용상태 :</span>
										<select name="us_col_07" id="us_col_07">
											<option>선택하세요</option>
											<option value="y">y</option>
											<option value="n">n</option>
										</select>
									</div>
									<div>
										<span class="content1">비   고 :</span> <input type="text" id="us_col_06" name="us_col_06">
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
</body>
<script>
$('.menu1', $('.nav_section')).addClass('active');
</script>
</html>
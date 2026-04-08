<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>기관수정 : SOSYGE EASY-CRM</title>
<!-- SNS 공유시 썸네일 표시(보통 헤드에 들어감) -->
<meta property="og:title" content="상담관리시스템">	<!-- 공유시 사이트 이름 -->
<meta property="og:type" content="website">
<meta property="og:url" content="http://csm.sosyge.net/csm/login">	<!-- 공유시 입력될 URL -->
<meta property="og:image" content="/resources/icon/ev/core_icon_big.png">	<!-- 공유시 노출 이미지 (최적 사이즈 : 800 x 400) -->
<meta property="og:description" content="병원상담관리시스템">	<!-- 공유시 보여질 사이트 간략 소개 -->

<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="shortcut icon" href="${pageContext.request.contextPath}/resources/favicon/favicon.ico">
<link rel="apple-touch-icon" sizes="180x180" href="${pageContext.request.contextPath}/resources/favicon/favicon.ico">
<link rel="icon" sizes="192x192" href="${pageContext.request.contextPath}/resources/favicon/favicon.ico">
<link rel="stylesheet" href="/resources/css/csm/admin/newuser.css">
	<link rel="stylesheet" href="/resources/css/csm/Include/spinner.css">
<link rel="stylesheet" href="/resources/css/csm/Include/modal.css">
<script src="https://code.jquery.com/jquery-3.6.0.js"></script>
<script src="/resources/js/csm/core/admin/modifyinstPopup.js"></script>
</head>
<body>
	<form action="/csm/core/inst/post" method="post"> 
		<div class="user-header">
			<h3>새로운 기관 정보를 입력해 주세요.</h3>
		</div>
		<label>
			기관명
			<font color="red">*</font>
		</label>
		<input type="text" class="user-area" id="id_col_02" name="id_col_02" value="${instdata.id_col_02 }" placeholder="" required="required" />
		<label>
			기관코드
			<font color="red">*</font>
		</label>
		<input type="text" class="user-area" id="id_col_03" name="id_col_03" value="${instdata.id_col_03 }"placeholder="" required="required" disabled="disabled"/>
		<label>
			사업자등록번호
			<font color="red">*</font>
		</label>
		<input type="text" class="user-area" id="id_col_09" name="id_col_09" value="${instdata.id_col_09 }" placeholder="" required="required" />
		<label>
			연락처(1)
			<font color="red">*</font>
		</label>
		<input type="text" class="user-area" id="id_col_06" name="id_col_06" value="${instdata.id_col_06 }" placeholder="" required="required" />
		<label>
			연락처(2)
		</label>
		<input type="text" class="user-area" id="id_col_07" name="id_col_07" value="${instdata.id_col_07 }" placeholder=""/>
		<label>
			주소
			<font color="red">*</font>
		</label>
		<input type="text" class="user-area" id="id_col_08" name="id_col_08" value="${instdata.id_col_08 }" placeholder="" required="required" />
		<label>
			비고
		</label>
		<input type="text" class="user-area" id="id_col_05" name="id_col_05" value="${instdata.id_col_05 }" placeholder=""/>
		<div class="btn-area">
			<button class="blue-btn" id="submit-btn" type="button" style="">등록</button>
			<button class="gray-btn" type="button" onclick="window.close()">취소</button>
		</div>
		<input type="hidden" name="id_col_01" id="id_col_01" value="${instdata.id_col_01 }">
	</form>
	<div id="spinner-overlay" style="display: none;">
		<div class="spinner-container">
			<div class="spinner"></div>
			<p>잠시만 기다려주세요</p>
		</div>
	</div>
	<div class="modal normal">
        <div class="modal_body">
            <div>
                <img class="menu_icon" src="${pageContext.request.contextPath}/resources/img/alert_img.png">
                <div class="menu_msg"></div>
                <div class="modal_footer">
                    <div class="btn modal_btn" onclick="closePopup()" id="confirmBtn2">
                        확인
                    </div>
                </div>
            </div>
        </div>
    </div>
</body>
</html>
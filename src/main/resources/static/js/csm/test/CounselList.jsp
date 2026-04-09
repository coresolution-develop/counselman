<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<!-- Web App으로 선언하여 브라우저의 UI ( URL 바 ) 를 안 보이도록 할 수 있다. -->
<meta name="apple-mobile-web-app-capable" content="yes" />
<meta name="viewport" content="user-scalable=no">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
<link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/resources/css/css.css">
<link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/resources/css/CounselListcss.css?after">
<title>전자문서 검색</title>

<style>
.white-link {
	color: #fff;
}


게시판 페이징 색상 파란색에서 회색으로 변경 -->.pagination>li>a, .pagination>li>span {
	color: black !Important;
}

.page-link {
	Color: #000;
	background-color: #fff;
	border: 1px solid #ccc;
}

.page-item.active .page-link {
	z-index: 1;
	Color: #555;
	font-weight: bold;
	/* background-color: #f1f1f1; */
	border-color: #ccc;
}

.page-link:focus, .page-link:hover {
	Color: #000;
	background-color: #fafafa;
	border-color: #ccc;
}
</style>
</head>
<body class="normal" style="font-size: 14pt; ">
<%@ include file="../Include/Header9.jsp" %>
<section class="normal">
	<div class="container">
	<form action="
	<c:choose>
		<c:when test="${keyword1 == '' and keyword2 == '' and keyword3 == ''}">
		${pageContext.request.contextPath}/c/CounselList.do
		</c:when>
		<c:otherwise>
		${pageContext.request.contextPath}/c/CounselListSearch.do
		</c:otherwise>
	</c:choose>
	">
	<div class="flex flex-center " style="text-align: center; height: 88px; background-color: #dadada; font-size:20pt; color:#2f2f2f">
		<div class="dv_tx" style="width:95px;">환자명</div><input class="ip_tx" type="text" name="keyword1" id="keyword1" onkeyup="keyword1text()"/>
		<div class="dv_tx" style="width:145px;">보호자명</div><input class="ip_tx" type="text" name="keyword2" id="keyword2" onkeyup="keyword2text()"/>
		<div class="dv_tx" style="width:145px;">전화번호</div><input class="ip_tx ip_pn" type="text" name="keyword3" id="keyword3" onkeyup="keyword3text()"/>
		<input class="ip_sb" style="cursor: pointer;" type="submit" value="검색"/>&nbsp;
		<a class="ip_sb" href="${pageContext.request.contextPath}/c/Written.do" style="cursor: pointer;     display: flex;
    justify-content: center;
    align-items: center;" >등록</a>
	</div>
	<div style="border-bottom: 1px solid #ddd"></div>
	<div id="list">
	<table style=" width: 100%; text-align: center; font-size: 18pt; border-collapse: collapse;">
		<tr class="" style="height: 70px; background-color: #03a9d0; border: 1px solid #03a9d0;color:#ffffff;">
			<td>작성일</td>
			<td>차트번호</td>
			<td>환자명</td>
			<td>신청자</td>
			<td>주보호자</td>
			<td>부보호자</td>
			<td>종류</td>
			<td>상태</td>
			<td></td>
		</tr>
		<c:choose>
			<c:when test="${keyword == 1}">
				<c:forEach items="${cslist}" var="list">
				<tr class="light list-tr" style="height: 78px; border-bottom: 1px solid #e9e9e9; color: #000000;">
					<td>${list.cs_data_31}</td>
					<td>${list.cs_data_02}</td>
					<td>${list.cs_data_01}</td>
					<td>${list.cs_data_29}</td>
					<td>${list.cs_data_07}</td>
					<td>${list.cs_data_12}</td>
					<td>입원서약서</td>
					<td>
						<c:choose>
							<c:when test="${list.cs_data_30 == 1}">
								임시저장
							</c:when>
							<c:otherwise>
								등록완료
							</c:otherwise>
						</c:choose>
					</td>
					<td>
						<div style="display: flex; align-items: center; vertical-align: middle; justify-content: center;;">
							<c:choose>
								<c:when test="${list.cs_data_30 == 1}">
									<div class="save flex flex-center" style="cursor: pointer;" onclick="location.href='${pageContext.request.contextPath}/c/WrittenModify.do/${list.cs_idx}'">서명</div>
								</c:when>
								<c:otherwise>
									<div class="sb flex flex-center" style="cursor: pointer; margin-right: 5px;" onclick="window.open('${pageContext.request.contextPath}/c/WrittenView.do/${list.cs_idx}')">보기</div>
									<div class="sb flex flex-center" style="cursor: pointer; margin-left: 5px;">
									<a download="${list.cs_idx}" href="${pageContext.request.contextPath}/c/WrittenView.do/${list.cs_idx}" >
									<img src="${pageContext.request.contextPath}/resources/icon/dwonload_icon_w.png" style="width: 50px; vertical-align: middle;">
									</a>
									</div>
								</c:otherwise>
							</c:choose>
							</div>
					</td>
				</tr>
				<c:set value="1" var="keyword"/>
				</c:forEach>
			</c:when>
			<c:otherwise>
				<c:forEach items="${matchingList}" var="list">
				<tr class="light list-tr" style="height: 78px; border-bottom: 1px solid #e9e9e9; color: #000000; <c:if test ="${keyword == 1}">display:none;</c:if>" >
					<td>${list.cs_data_31}</td>
					<td>${list.cs_data_02}</td>
					<td>${list.cs_data_01}</td>
					<td>${list.cs_data_29}</td>
					<td>${list.cs_data_07}</td>
					<td>${list.cs_data_12}</td>
					<td>입원서약서</td>
					<td>
						<c:choose>
							<c:when test="${list.cs_data_30 == 1}">
								임시저장
							</c:when>
							<c:otherwise>
								등록완료
							</c:otherwise>
						</c:choose>
					</td>
					<td>
						<c:choose>
							<c:when test="${list.cs_data_30 == 1}">
								<div class="save flex flex-center" style="cursor: pointer;" onclick="location.href='${pageContext.request.contextPath}/c/WrittenModify.do/${list.cs_idx}'">서명</div>
							</c:when>
							<c:otherwise>
								<div class="sb flex flex-center" style="cursor: pointer;" onclick="location.href='${pageContext.request.contextPath}/c/WrittenView.do/${list.cs_idx}'">보기</div>
							</c:otherwise>
						</c:choose>
					</td>
				</tr>
				</c:forEach>
			</c:otherwise>
		</c:choose>
		
	</table>
	
	</div>
	<c:if test="${keyword ==1 }">
	<div class="example" style="display: block; text-align: center;">
			<nav aria-label="..." style="display: flex; height: 45px;
    justify-content: center;">

				<ul class="pagination justify-content-center" style="display: flex; justify-content: center; align-items: center;"id="pageInfo">
					<!-- 처음페이지로 이동하기 -->
					<c:choose>
						<c:when test="${pageMaker.cri.page != 1}">
							<li class="page-item"><a class="page-link" style="padding: 5px;"
								href="${pageContext.request.contextPath}/c/CounselList.do?page=${pageMaker.startPage - pageMaker.startPage}&perPageNum=${pageMaker.cri.perPageNum}&type=${type}&keyword1=${keyword1}">&laquo;</a>
							</li>
						</c:when>
					</c:choose>
					<!-- 이전페이지로 이동하기 -->
					<c:choose>
						<c:when test="${pageMaker.prev == true}">
							<li class="page-item"><a class="page-link" style="padding: 5px;"
								href="${pageContext.request.contextPath}/c/CounselList.do?page=${pageMaker.startPage - 1}&type=${type}&keyword1=${keyword1}">Previous</a>
							</li>
						</c:when>
					</c:choose>
					<!-- 리스트 페이징 -->
					<c:forEach var="num" begin="${pageMaker.startPage}"
						end="${pageMaker.endPage}">
						<c:choose>
							<c:when test="${num == pageMaker.cri.page}">
								<li class="page-item active"><a class="page-link" style="padding: 5px;"
									href="${pageContext.request.contextPath}/c/CounselList.do?page=${num}&type=${type}&keyword1=${keyword1}">${num}</a>
								</li>
							</c:when>
							<c:otherwise>
								<li class="page-item"><a class="page-link" style="padding: 5px;"
									href="${pageContext.request.contextPath}/c/CounselList.do?page=${num}&type=${type}&keyword1=${keyword1}">${num}</a>
								</li>
							</c:otherwise>
						</c:choose>
					</c:forEach>
					<!-- 다음 페이지로 이동하기 -->
					<c:choose>
						<c:when test="${pageMaker.next == true}">
							<li class="page-item"><a class="page-link" style="padding: 5px;"
								href="${pageContext.request.contextPath}/c/CounselList.do?page=${pageMaker.endPage + 1}&type=${type}&keyword1=${keyword1}">Next</a>
							</li>
						</c:when>
					</c:choose>

					<!-- 마지막페이지로 이동하기 -->
					<c:choose>
						<c:when test="${pageMaker.cri.page < pageMaker.endPage}">
							<li class="page-item"><a class="page-link" style="padding: 5px;"
								href="${pageContext.request.contextPath}/c/CounselList.do?page=${pageMaker.endPage}&perPageNum=${pageMaker.cri.perPageNum}&type=${type}&keyword1=${keyword1}">&raquo;</a>
							</li>
						</c:when>

					</c:choose>
				</ul>


			</nav>
		</div>
	
	</c:if>
	
	</form>
	</div>
</section>	
</body>
<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
<script>
$(document).ready(function(){
	var deviceWidth = $(window).width();
	var deviceHeight = $(window).height();
	var he = $('.container').height();
	var he2 = $('section').height();
	if (deviceWidth < 768) {
	  $('.dv_tx').css('font-size', '14pt');
	} else if (deviceWidth < 992) {
	  $('.dv_tx').css('font-size', '16pt');
	} else if (deviceWidth < 1200) {
	  $('.dv_tx').css('font-size', '18pt');
	} else {
	  $('.dv_tx').css('font-size', '20pt');
	}
	
	if(he < 800){
		$('.container').css({
			"height" : "calc(100vh - 45px)"
			
		});
		console.log("여기 실행됨");
	}
	console.log(deviceHeight);
	console.log(he);
	console.log(he2);
});
</script>
<script>
function keyword1text(){
	let value = $('#keyword1').val();
	console.log(value);
}
function keyword2text(){
	let value = $('#keyword2').val();
	console.log(value);
}
function keyword3text(){
	let value = $('#keyword3').val();
	console.log(value);
}
</script>
</html>
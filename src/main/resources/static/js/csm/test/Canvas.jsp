<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="user-scalable=no">
<title>canvas</title>
<style>
canvas{
	background-color: #eee;
}

.modal {
	position: fixed;
	top: 0;
	left: 0;
	
	bottom: 0;
	
	width: 100vw;
	height: 100vh;

	display: none;

	background-color: rgba(51, 51, 51, 0.7);
	/* 애니메이션 효과 */
	/* transform: translateY(100%); */
	/* transition: transform 1s ease-in-out; */
}

.modal.show {
	display: block;
	transform: translateY(0);
	transition: transform 1s ease-in-out;
}

.modal_body {
	display: flex;
    justify-content: center;
	position: absolute;
	top: 45%;
	left: 50%;

	/* width: 680px; */
	width: 900px;
	height: 700px;
	/* height: 60vh; */

	/* padding: 35px; */

	text-align: center;

	background-color: rgb(255, 255, 255);
	/* border-radius: 20px; */
	box-shadow: 0 2px 3px 0 rgba(34, 36, 38, 0.15);
	/* 애니메이션 효과 */
	transform: translateY(0);
	transform: translate(-50%, -50%); 
}
.text{
	font-size: 18pt;
    margin-top: 45px;
    margin-bottom: 45px;
}
.modal_insert{
	display: flex; 
	justify-content: center; 
	align-items: center; 
	background-color: #92D3EB; 
	color: #ffffff; 
	height: 40px; 
	width: 70px;
}
.modal_cancel{
	display: flex;
	justify-content: center;
	align-items: center;
	background-color: #ddd;
	color: #ffffff;
	height: 40px;
	width: 70px;
}
</style>
<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
<!-- 스크립트 부분 -->

</head>
<body>
<form>
<div class="modal normal">
	<div class="modal_body">
		<div>
			<div class="text">아래 공간에 서명을 하신 후 확인을 누르세요</div>
			<input type="hidden" name="image-data" id="image-data">
			<canvas class="canvas" id="canvas" width="600" height="400">
			이 브라우저는 캔버스를 지원하지 않습니다.
			</canvas>
			<div class="flex flex-center">
				<div class="modal_insert" id="modal_insert" onclick="end()">
				확인
				</div>&emsp;
				<div class="modal_cancel" id="modal_cancel" onclick="closePopup();">
				취소
				</div>
			</div>
		</div>
	</div>
</div>
<div style="display: flex; justify-content: space-between;">
    <div style="display: flex;">
        <div style="display: flex; justify-content: center; align-items: center; background-color: #ddd; color: #ffffff; height: 40px; width: 70px;">임시저장</div>
    	&emsp;
        <div style="display: flex; justify-content: center; align-items: center; background-color: #92D3EB; color: #ffffff; height: 40px; width: 70px;">등록</div>
    </div>
    <div style="display: flex; justify-content: flex-end;">
        <div style="display: flex; justify-content: center; align-items: center; background-color: #EBAA92; color: #ffffff; height: 40px; width: 70px;" id="end">서명</div>
    </div>
</div>
</form>
<script type="text/javascript">



const body = document.querySelector('body');
const modal = document.querySelector('.modal');
const btnOpenPopup = document.querySelector('#end');

btnOpenPopup.addEventListener('click', () => {
	
	modal.classList.toggle('show');

	if (modal.classList.contains('show')) {
		body.style.overflow = 'hidden';
	}
});

modal.addEventListener('click', (event) => {
	
	
	if (event.target === modal) {
		// 모달 바깥 영역을 클릭했을 때
		const confirmed = window.confirm('등록하시겠습니까?');
		
			closePopupAndRedirect();
		
	}
});

function end(){
	
	closePopupAndRedirect();
}
function closePopupAndRedirect() {
	modal.classList.toggle('show');
	if (!modal.classList.contains('show')) {
		body.style.overflow = 'auto';
	}
	
	$("#frm").attr("action","${pageContext.request.contextPath}/h/end.do/${detail.m_name}").submit();
	//window.location.href = '${pageContext.request.contextPath}/h/end.do/${sessionScope.midx}';
	
}
function closePopup(){
	modal.classList.toggle('show');
}



//canvas 엘리먼트를 가져옵니다
var canvas = document.getElementById("canvas");
//canvas 엘리먼트의 context를 가져옵니다.
var ctx = canvas.getContext("2d");

//이전 좌표값을 저장할 변수를 초기화합니다.
var lastX;
var lastY;

let drawing = false;	// true일 때만 그리기
function draw(curX, curY) {
    ctx.beginPath();
    ctx.moveTo(startX, startY);
    ctx.lineTo(curX, curY);
    ctx.stroke();
}
canvas.addEventListener("touchmove", touchMove, false);
canvas.addEventListener("touchstart", touchStart, false);
canvas.addEventListener("touchend", touchEnd, false);

function getTouchPos(e) {
	const canvasRect = canvas.getBoundingClientRect();
	// getBoundingClientRect() 메소드를 사용하여 캔버스 엘리먼트의 위치 정보를 얻고, 
	// e.touches에서 받은 clientX와 clientY 값을 기준으로 좌표 값을 계산합니다. 그 후, 
	// x와 y 값을 반환합니다.
    return {
    	x: e.touches[0].clientX - canvasRect.left,
        y: e.touches[0].clientY - canvasRect.top
    }
}

function touchStart(e) {
    e.preventDefault();
    drawing = true;
    const { x, y } = getTouchPos(e);
    startX = x;
    startY = y;
}
function touchMove(e) {
    if(!drawing) return;
    const { x, y } = getTouchPos(e);
    draw(x, y);
    startX = x;
    startY = y;
}
function touchEnd(e) {
    if(!drawing) return;
    // 점을 찍을 경우 위해 마지막에 점을 찍는다.
    // touchEnd 이벤트의 경우 위치정보가 없어서 startX, startY를 가져와서 점을 찍는다.
    ctx.beginPath();
    ctx.arc(startX, startY, ctx.lineWidth/2, 0, 2*Math.PI);
    ctx.fillStyle = ctx.strokeStyle;
    ctx.fill();
    drawing = false;
}

function onClear() {
    canvas = document.getElementById('canvas');
    context.save();
	context.setTransform(1, 0, 0, 1, 0, 0);
	context.clearRect(0, 0, canvas.width, canvas.height);
	context.restore();
}
</script>
</body>
</html>
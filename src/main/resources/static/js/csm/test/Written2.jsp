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
<meta name="viewport" content="user-scalable=yes">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
<link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/resources/css/css.css">
<link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/resources/css/Writtencss.css?after">
<title>입원 서약서</title>

<style>
  @supports (-webkit-appearance: none) or (-moz-appearance: none) {
    .checkbox-wrapper-13 input[type=checkbox] {
      --active: #275EFE;
      --active-inner: #fff;
      --focus: 2px rgba(39, 94, 254, .3);
      --border: #BBC1E1;
      --border-hover: #275EFE;
      --background: #fff;
      --disabled: #F6F8FF;
      --disabled-inner: #E1E6F9;
      -webkit-appearance: none;
      -moz-appearance: none;
      height: 21px;
      outline: none;
      display: inline-block;
      vertical-align: top;
      position: relative;
      margin: 0;
      cursor: pointer;
      border: 1px solid var(--bc, var(--border));
      background: var(--b, var(--background));
      transition: background 0.3s, border-color 0.3s, box-shadow 0.2s;
    }
    .checkbox-wrapper-13 input[type=checkbox]:after {
      content: "";
      display: block;
      left: 0;
      top: 0;
      position: absolute;
      transition: transform var(--d-t, 0.3s) var(--d-t-e, ease), opacity var(--d-o, 0.2s);
    }
    .checkbox-wrapper-13 input[type=checkbox]:checked {
      --b: var(--active);
      --bc: var(--active);
      --d-o: .3s;
      --d-t: .6s;
      --d-t-e: cubic-bezier(.2, .85, .32, 1.2);
    }
    .checkbox-wrapper-13 input[type=checkbox]:disabled {
      --b: var(--disabled);
      cursor: not-allowed;
      opacity: 0.9;
    }
    .checkbox-wrapper-13 input[type=checkbox]:disabled:checked {
      --b: var(--disabled-inner);
      --bc: var(--border);
    }
    .checkbox-wrapper-13 input[type=checkbox]:disabled + label {
      cursor: not-allowed;
    }
    .checkbox-wrapper-13 input[type=checkbox]:hover:not(:checked):not(:disabled) {
      --bc: var(--border-hover);
    }
    .checkbox-wrapper-13 input[type=checkbox]:focus {
      box-shadow: 0 0 0 var(--focus);
    }
    .checkbox-wrapper-13 input[type=checkbox]:not(.switch) {
      width: 21px;
    }
    .checkbox-wrapper-13 input[type=checkbox]:not(.switch):after {
      opacity: var(--o, 0);
    }
    .checkbox-wrapper-13 input[type=checkbox]:not(.switch):checked {
      --o: 1;
    }
    .checkbox-wrapper-13 input[type=checkbox] + label {
      display: inline-block;
      vertical-align: middle;
      cursor: pointer;
      margin-left: 4px;
    }

    .checkbox-wrapper-13 input[type=checkbox]:not(.switch) {
      border-radius: 7px;
    }
    .checkbox-wrapper-13 input[type=checkbox]:not(.switch):after {
      width: 5px;
      height: 9px;
      border: 2px solid var(--active-inner);
      border-top: 0;
      border-left: 0;
      left: 7px;
      top: 4px;
      transform: rotate(var(--r, 20deg));
    }
    .checkbox-wrapper-13 input[type=checkbox]:not(.switch):checked {
      --r: 43deg;
    }
  }

  .checkbox-wrapper-13 * {
    box-sizing: inherit;
  }
  .checkbox-wrapper-13 *:before,
  .checkbox-wrapper-13 *:after {
    box-sizing: inherit;
   
  }
  span {
      line-height: 30px;
      margin-bottom: 5px;
  }
  .info td {
  	border: 1px solid #dadada;
  }
</style>
</head>
<body style="min-width: 1280px;">
<%@ include file="../Include/Header10.jsp" %>
<form id="frm" name="frm" method="post" action="${pageContext.request.contextPath}/c/WrittenAction2.do">
<section style="background-repeat: no-repeat;
	background-position: center 0;
	background-image: url('${pageContext.request.contextPath}/resources/img/background4.png');
	background-size: 1246px;
	min-width: 1280px;
    margin: 0 auto; 
	margin-bottom: 111px;">
<div id="capture_area" style="">
<div>
<input type="hidden" id="imgData" name="imgData">
<h1 style="text-align: center; font-size: 30pt; padding-top: 70px;">입 원 서 약 서</h1>

<div class="bold" style="font-size: 16pt; color:#303030; display: flex; align-items: start; margin: 0 auto; width: 966px;">※ 환자의 인적사항</div>
<div style="text-align: center; margin-bottom: 20px;">
<table class="info" border=1 style="border-collapse: collapse; text-align: center; width: 966px; margin-left: auto; margin-right: auto;" >
	<tr>
		<td class="normal" width="170px" height="56px">성명</td>
		<td class="light" width="313px" onclick="this.querySelector('input').focus();" style="text-align: left;">
		<input style= "margin-left: 33px;" type="text" name="cs_data_01" value="${param1}"></td>
		<td class="normal" width="170px" height="56px">차트번호</td>
		<td onclick="this.querySelector('input').focus();" class="light" colspan="2" width="313px" style="text-align: left;">
		<input style="margin-left: 33px;" name="cs_data_02" type="text" value="${param4}"></td>
	</tr>
	<tr>
		<td class="normal" height="56px">입원병실</td>
		<td onclick="this.querySelector('input').focus();" class="light" style="text-align: left;">
		<input style= "margin-left: 33px;" type="text" name="cs_data_03" value="${param5}"></td>
		<td class="normal" height="56px">성별 </td>
		<td onclick="this.querySelector('input').checked = true;" class="light" style="cursor: pointer;">
		<input type="radio" name="cs_data_04" value="남성" style="cursor: pointer;" id="genderM" <c:if test="${param2 eq '남성' }">checked</c:if>><label for="genderM" style="cursor: pointer;">남</label></td>
		<td onclick="this.querySelector('input').checked = true;" class="light" style="cursor: pointer;">
		<input type="radio" name="cs_data_04" value="여성" id="genderF"<c:if test="${param2 eq '여성' }">checked</c:if>><label for="genderF" style="cursor: pointer;">여</label></td>
	</tr>
	<tr>
		<td class="normal" height="56px">생년월일</td>
		<td class="light" onclick="this.querySelector('input').focus();" style="text-align: left;">
		<input style= "margin-left: 33px;" type="text" name="cs_data_05" value="${param3 }"></td>
		<td class="normal" height="56px">전화</td>
		<td style="text-align: left;" class="light" colspan="2" onclick="this.querySelector('input').focus();">
		<input style="margin-left: 33px;" type="text" name="cs_data_06" value="${param6 }"></td>
	</tr>
</table>
</div>
<div class="light" style="font-size:14pt; color:#222222; width:966px; display: flex; align-items: start; flex-direction: column; margin: 0 auto;">
<span> 
&nbsp;본인(환자의 주보호자)은 귀 의료기관에서 제시한 제반 규칙을 준수함은 물론, 치료와 퇴원 등 의사 및 간호사(또는 직원)의 정당한 지시에 따르며, 아래의 내용을 읽고 서약 및 동의합니다.
</span>
<span class="" style="color:#f87b0c;">
1. 입원 기간 중 예기치 않은 사고(골절, 타박상, 개방성 상처 등)나 응급상황 시 본원에서 치료할 수 없는 상태이거나 의료진 판단으로 응급처치 가능한 병원으로 전원을 요구할 수 있으며 또한 환자 및 보호자가 원할 경우  담당의사와 상의 후 타 병원으로 전원 할 수 있습니다.
</span>
<span class="" style="color:#f87b0c;">
2. 노인은 골다공증, 피부의 약화로 쉽게 골절 또는 멍이 들 수 있으므로 의료기관의 정당한 진료지침이나 교육에 반하는 무단 외출・외박 등으로 인하여 발생하는 환자의 손해에 대한 책임은 원칙적으로 모두 환자에게 있습니다.
</span>
<span>
3. 진료 상 발생하는 모든 문제에 대하여 분쟁이 생겼을 때에는 『의료사고 피해구제 및 의료분쟁 조정 등에 관한 법률』에 의한 한국 의료분쟁조정중재원에 그 조정을 신청할 수 있음에 동의합니다.
</span>
<span>
4. 입원기간 동안 발생하는 진료비는 귀 의료기관에서 정하는 납부기한 내에 납부(연대보증인이 있는 경우에는 환자와 연대보증인이 연대하여 납부)하겠으며, 정당한 이유 없이 체납될 때에는 채권확보를 위한 법적조치에 이의가 없고, 만일 본건을 기초로 의료분쟁 등으로 소송을 제기할 경우 관할법원의 민사소송법에 따릅니다.
</span>
<span>
5. 입원기간 중에 환자 및 보호자가 귀 의료기관의 비품이나 기물을 고의 또는 과실로 파괴, 망실, 훼손한 때에는 이를 변상(현물, 현금)합니다.
</span>
<span class="" style="color:#f87b0c;">
6. 입원기간 중 환자 또는 보호자 등이 소지 중인 현금, 기타 귀중품 및 개인소지품(완전틀니, 부분틀니 포함, 안경, 보청기등)은 귀 의료기관이 지정한 보관 장소가 있는 경우에는 보관 장소에 보관하고, 보관 장소가 따로 없는 경우에는 귀 의료기관이 지정한 직원에게 보관을 의뢰합니다. 이를 이행치 아니하여 분실 및 훼손되어 발생한 손해에 대하여는 귀 의료기관은 책임이 없습니다.
</span>
<span>
7. 개인정보 수집 및 활용 동의
</span>
<span>  본원은 진료 등을 위해 아래와 같은 최소한의 개인정보를 수집함. 진료를 위한 필요정보는 의료법에 따라 별도의 동의 없이 수집되며, 동의를 하지 않더라도 진료에는 불이익이 없음.
</span>
<span> (1) 개인정보 수집항목 : (필수항목) 성명, 주소, 전화번호, 주민등록번호, 보험정보</span>
<span style="margin-bottom: 10px;">&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&nbsp;&nbsp;&nbsp;(선택항목) 이메일, 문자메세지 서비스 수신 동의여부 </span>
<span style="margin-bottom: 10px;"> (2) 개인정보 수집방법 : 진료 목적은 별도로 받지 않으며, 진료목적 외는 서면으로 수집</span>
<span style="margin-bottom: 10px;"> (3) 개인정보의 수집 및 이용목적 : 진단/검진 예약, 조회 및 진료를 위한 본인 확인 절차 등</span>
<span style="margin-bottom: 20px;">
 (4) 개인정보의 보유 및 이용기간 : 개인정보의 수집목적 또는 제공받은 목적이 달성될 때 파기</span>
 
<span class="bold" style="color:#303030; font-size: 16pt;">※ 환자본인, 주보호자 및 부보호자에 대한 안내</span>

<span>
1. 주보호자는 환자의 입원과 전원, 퇴원 등의 절차상 동의인 이며, 환자 상태의 급격한 변화, 낙상 등의 안전사고, 사망 등 환자입원생활에 관련된 사항에 대해 <b class="bold">일차적 연락대상</b>이며 타보호자는 <b>상담이 제한</b>됩니다. 주보호자 변경 시에는  주보호자변경요청서를 통해서만 가능합니다.</span>
<span>
2. 주보호자 및 부보호자는 환자의 입원비용과 기타 제반 비용 발생 시 매월 <b>정산의 책임</b>을 지게 되며 
</span>
<span style="margin-bottom: 10px;">
(보증채무최고액:30,000,000원 보증기간:3년), 2개월 미납시 본원은 퇴원권유 할 수 있습니다.
</span>
<span>
3. 주보호자는 환자의 입원기록 외 사본 발급 및 제증명 발급의 주체가 되며, 수혈동의서, 신체 보호대 동의서, 심폐소생술거부동의서, 낙상관련설명안내서, 병원비 등의 규정상 동의절차가 필요한 경우 <b>서명 대상자</b>가 됩니다.
</span>
<span>
4. 입원생활에 관련 법적 분쟁 발생 시 원칙적으로 환자 본인이 의료기관의 소송 상대방이 되며, 불가피할 경우 주보호자가 <b>법적 대리인</b>이 됩니다.
</span>
</div>
<br>

<div style="text-align: center; margin-bottom: 27px;">
<table style="border-collapse: collapse; text-align: center; width: 966px; margin-left: auto; margin-right: auto;
 border: 1px solid #c7c7c7;" >
	<tr style="height: 56px;">
		<td class="normal" rowspan="3" style="width:65px; text-align: center; background-color:#fafafa; border: 1px solid #c7c7c7;">
		주<br>보<br>호<br>자</td>
		<td class="normal" style=" position: relative; width: 170px; border: 1px solid #dadada; border-top: inherit;">성 명</td> 
		<td style="width:350px; text-align: left; border: 1px solid #dadada; border-top: inherit;" onclick="this.querySelector('input').focus();">
		<input class="light" name="cs_data_07" style="margin-left: 33px; width:250px;" value="${param4 }" type="text"/></td>
		<td onclick="this.querySelector('input').focus();" style="border: 1px solid #dadada; border-top: inherit;" class="normal">(관계 :
		<input class="light" name="cs_data_08" style="width : 175px;" type="text"/>)</td>
		<td style="width: 100px; border: 1px solid #dadada; border-top: inherit; border-right: inherit; position: relative;">
		<div class="normal" style="display: inline-block; position: relative; z-index: 1;" id="end1">(서   명)</div>
		<img style="position: absolute; z-index: 0; width: 100px; top: -20px; right: 0px;" id="canvasImg1" src="" alt="">
		</td>
	</tr>
	<tr style="height: 56px;">
		<td style="border: 1px solid #dadada;" class="normal">주 소</td>
		<td colspan="4" onclick="this.querySelector('input').focus();" style="text-align: left; border: 1px solid #dadada; border-right: inherit;">
		<input class="light" name="cs_data_09" style="margin-left: 33px; width:460px;" type="text"/></td>
	</tr>
	<tr style="height: 56px;">
		<td style="border: 1px solid #dadada; border-left: inherit; border-bottom: inherit;" class="normal">휴대폰 번호</td>
		<td onclick="this.querySelector('input').focus();" style="text-align: left;">
		<input class="light" name="cs_data_10" type="text" style="margin-left: 33px;" value="${param5}"/></td>
		<td colspan="2" style="text-align: right;">
		<!-- 
		<input name="cs_data_11" value="주보호자 비용안내" type="checkbox" id="cs_data_11" class="normal" style="cursor: pointer;"/>
		<div style="margin-right: 21px; display: inline-block;"><label for="cs_data_11"style="cursor: pointer;"> 비용안내</label></div>
		 -->
		<div class="checkbox-wrapper-13" style="margin-right: 21px;">
		  <input id="cs_data_11" id="cs_data_11" type="checkbox" name="cs_data_11" class="normal" value="주보호자 비용안내">
		  <label for="cs_data_11" for="cs_data_11">비용안내</label>
		</div>
		</td>
		
		
		
		
		
		
	</tr>
</table>
</div>

<div style="text-align: center; margin-bottom: 27px;">
<table class="normal" style="border-collapse: collapse; text-align: center; width: 966px; margin-left: auto; margin-right: auto;
 border: 1px solid #c7c7c7;" >
	<tr style="height: 56px;">
		<td rowspan="3" style="width:65px; background-color:#fafafa; text-align: center; border: 1px solid #c7c7c7;">부<br>보<br>호<br>자</td>
		<td style=" position: relative; width: 170px; border: 1px solid #dadada; border-top: inherit;">성 명</td> 
		<td style="width: 350px; text-align: left; border: 1px solid #dadada; border-top: inherit;" onclick="this.querySelector('input').focus();">
		<input class="light" name="cs_data_12" style="margin-left: 33px; width:250px;" type="text"/></td>
		<td onclick="this.querySelector('input').focus();" style="border: 1px solid #dadada; border-top: inherit;">(관계 :
		<input class="light" name="cs_data_13" style="width:175px;" type="text"/>)</td>
		<td style="width: 100px; border: 1px solid #dadada; border-top: inherit; border-right: inherit; position: relative;">
		<div style="display: inline-block; position: relative; z-index: 1;" id="end2">(서   명)</div>
		<img style="position: absolute; z-index: 0; width: 100px; top: -20px; right: 0px;" id="canvasImg2" src="" alt="">
		</td>
	</tr>
	 
	<tr style="height: 56px;" class="normal">
		<td style="border: 1px solid #dadada;">주 소 </td>
		<td colspan="4" onclick="this.querySelector('input').focus();" style="text-align: left; border: 1px solid #dadada; border-right: inherit;">
		<input class="light" name="cs_data_14" style="margin-left: 33px; width:460px;" type="text"/></td>
	</tr>
	<tr style="height: 56px;" class="normal">
		<td style="border: 1px solid #dadada; border-left: inherit; border-bottom: inherit;">휴대폰 번호</td>
		<td onclick="this.querySelector('input').focus();" style="text-align: left;">
		<input class="light" name="cs_data_15" type="text" style="margin-left: 33px;"/></td>
		<td colspan="2" style="text-align: right;">
		<!-- 
		<input name="cs_data_16" id="cs_data_16" value="부보호자 비용안내" type="checkbox"style="cursor: pointer;"/>
		<div style="margin-right: 21px; display: inline-block;"><label for="cs_data_16" style="cursor: pointer;"> 비용안내</label></div>
		 -->
		<div class="checkbox-wrapper-13" style="margin-right: 21px;">
		  <input id="cs_data_16" id="cs_data_16" type="checkbox" name="cs_data_16" class="normal" value="부보호자 비용안내">
		  <label for="cs_data_16" for="cs_data_16">비용안내</label>
		</div>
		
		</td>
	</tr>
</table>
</div>
<div style="text-align: center; margin-bottom: 27px;">
<div style="margin-bottom: 27px;">
<table border=1 style="font-size:14pt; border-collapse: collapse; text-align: center; width: 966px; margin-left: auto; margin-right: auto; 
border: 1px solid #c7c7c7;" >
	<tr style="background-color: #fafafa;">
		<td colspan="3" style="height: 56px; text-align: left;" class="normal"><div style="margin-left: 21px;">환자가 아닌 보호자의 동의사유</div></td>
	</tr>
	<tr style="border-bottom: none; background-color: #ffffff;">
		<td style="border-right: none; border-bottom: none; height: 56px; text-align: left; width:585px;">
		<!-- 
		<input style="margin-left: 21px; cursor: pointer;" name="cs_data_17" id="cs_data_17" value="환자의 신체적 정신적 장애로 의사결정 불가" type="checkbox"/>
		<label for="cs_data_17" style="cursor: pointer;"> 환자의 신체적 정신적 장애로 의사결정 불가</label>
		 -->
		<div class="checkbox-wrapper-13" style="">
		  <input style="margin-left: 21px;" id="cs_data_17" type="checkbox" name="cs_data_17" class="normal" value="환자의 신체적 정신적 장애로 의사결정 불가">
		  <label for="cs_data_17">환자의 신체적 정신적 장애로 의사결정 불가</label>
		</div>
		</td>
		<td style="text-align: center; border-bottom: none; border-left:none; border-right: none;">
		<!-- 
		<input name="cs_data_18" value="환자위임" id="cs_data_18" type="checkbox" style="cursor: pointer;"/>
		<label for="cs_data_18" style="cursor: pointer;"> 환자위임</label>
		 -->
		<div class="checkbox-wrapper-13" style="">
		  <input style="" id="cs_data_18" type="checkbox" name="cs_data_18" class="normal" value="환자위임">
		  <label for="cs_data_18">환자위임</label>
		</div>
		
		</td>
		<td style="text-align: center; border-bottom: none; border-left: none; text-align: right;">
		<!-- 
		<div style="margin-right: 21px;"><label for="cs_data_19" style="cursor: pointer;">
		<input name="cs_data_19" id="cs_data_19"value="응급 상황" type="checkbox" style="cursor: pointer;"/>
		 응급 상황</label></div>
		  -->
		<div class="checkbox-wrapper-13" style="margin-right: 21px;">
		  <input style="" id="cs_data_19" type="checkbox" name="cs_data_19" class="normal" value="응급 상황">
		  <label for="cs_data_19">응급 상황</label>
		</div>
		 </td>
	</tr>
	
	<tr style="background-color: #ffffff; border-top:none;">
		<td style="border-right: none; border-top:none; height: 56px; text-align: left;">
		<!-- 
		<label for="cs_data_20" style="cursor: pointer;"><input style="margin-left:21px; cursor: pointer;" name="cs_data_20" id="cs_data_20" value="내용 설명 시 환자의 심신에 중대한 영향 우려" type="checkbox"/>
		 내용 설명 시 환자의 심신에 중대한 영향 우려</label>
		  -->
		<div class="checkbox-wrapper-13" style="margin-left:21px;">
		  <input style="" id="cs_data_20" type="checkbox" name="cs_data_20" class="normal" value="내용 설명 시 환자의 심신에 중대한 영향 우려">
		  <label for="cs_data_20">내용 설명 시 환자의 심신에 중대한 영향 우려</label>
		</div>
		 </td>
		<td style="border-right: none; border-top:none; border-left: none;">
		 <!-- 
		<div style=""><label for="cs_data_21" style="cursor: pointer;">
		<input name="cs_data_21" id="cs_data_21" value="미성년자" type="checkbox" style="cursor: pointer;"/> 미성년자</label></div>
		 -->
		<div class="checkbox-wrapper-13" style="">
		  <input style="" id="cs_data_21" type="checkbox" name="cs_data_21" class="normal" value="미성년자">
		  <label for="cs_data_21">미성년자</label>
		</div>
		</td>
		<td style="border-left: none; border-top:none; text-align: center;">
		</td>
	</tr>
</table>
</div>

<div style="text-align: center; margin-bottom: 27px;">
<table style="font-size:14pt; border-collapse: collapse; text-align: center; width: 966px; margin-left: auto;
 margin-right: auto; border: 1px solid #c7c7c7;" >
	<tr style="height: 56px; background-color: #fafafa;" class="normal">
		<td colspan="3" style="text-align: left; border-bottom: 1px solid #c7c7c7;">
		<!-- 
		<label for="cs_data_22" style="cursor: pointer;">
		<input style="font-size:14pt; margin-left: 21px; cursor: pointer;" name="cs_data_22" id="cs_data_22" value="상급병실" type="checkbox" onchange="toggleCheckbox()"/>
		 상급병실(특실, 1인실, 2인실)의 이용 시 병실차액이 발생할 수 있습니다.</label>
		  -->
		<div class="checkbox-wrapper-13" style="" onchange="toggleCheckbox()">
		  <input style="font-size:14pt; margin-left: 21px;" id="cs_data_22" type="checkbox" name="cs_data_22" class="normal" value="상급병실">
		  <label for="cs_data_22">상급병실(특실, 1인실, 2인실)의 이용 시 병실차액이 발생할 수 있습니다.</label>
		</div>
		 
		</td>
	</tr>
	<tr style="height: 56px; border-bottom: 1px solid #dadada; background-color: #ffffff;">
		<td style="text-align: center; width: 170px; border-right: 1px solid #dadada;">병실</td>
		<td style="text-align: center; width: 30%;" onclick="this.querySelector('input').focus();">
		<input name="cs_data_23" style="width:190px; text-align: right;" type="text" disabled="disabled"/> 호</td>
		<td style="text-align: right; color:#222222;" class="light">
		
		<div style="margin-right: 21px; display: flex; justify-content: right;">
		<!-- 
		<label for="cs_data_24" style="cursor: pointer;">
		<input style="cursor: pointer;" name="cs_data_24" id="cs_data_24" value="특실" type="checkbox" disabled="disabled"/> 특실</label>&emsp;&emsp;
		 -->
		<div class="checkbox-wrapper-13" style="">
		  <input style="" id="cs_data_24" type="checkbox" name="cs_data_24" class="normal" value="특실" disabled="disabled">
		  <label for="cs_data_24">특실</label>&emsp;&emsp;
		</div>
		<!-- 
		<label for="cs_data_25" style="cursor: pointer;">
		<input style="cursor: pointer;" name="cs_data_25" id="cs_data_25" value="1인실" type="checkbox" disabled="disabled"/> 1인실</label>&emsp;&emsp;
		 -->
		<div class="checkbox-wrapper-13" style="">
		  <input style="" id="cs_data_25" type="checkbox" name="cs_data_25" class="normal" value="1인실" disabled="disabled">
		  <label for="cs_data_25">1인실</label>&emsp;&emsp;
		</div>
		<!-- 
		<label for="cs_data_26" style="cursor: pointer;">
		<input style="cursor: pointer;" name="cs_data_26" id="cs_data_26" value="2인실" type="checkbox" disabled="disabled"/> 2인실</label>
		 -->
		<div class="checkbox-wrapper-13" style="">
		  <input style="" id="cs_data_26" type="checkbox" name="cs_data_26" class="normal" value="2인실" disabled="disabled">
		  <label for="cs_data_26">2인실</label>
		</div>
		</div>
		
		</td>
	</tr>
	<tr style="height: 56px;  border-bottom: 1px solid #dadada; background-color: #ffffff; color:#222222;">
		<td style="text-align: center; border-right: 1px solid #dadada;">비용</td><td class="light" style="text-align: right;"
		 colspan="2" class="light">1일당 : <input name="cs_data_27" id="cs_data_24" type="text" disabled="disabled"/></td>
	</tr>
	<tr style="height: 56px; background-color: #ffffff;">
		<td colspan="3" style="text-align: left;">
		<div style="margin-left: 21px;">상급병실 사용에 관련한 차액발생부분 설명을 듣고 동의함.</div>
		</td>
	</tr>
</table>
</div>
</div>


<div style="position: relative;">
	<div class="normal" style="text-align: right; position: relative; z-index: 1; color: #222222; width: 966px; height: 42px; margin: 0 auto;
	display: flex; justify-content: flex-end; align-items: center;">
		신청인  (  관계  : <input style="width:175px;" name="cs_data_28" type="text"/>) :
		<input name="cs_data_29" style="width:100px;" type="text"/>&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;
		<div style="display: inline-block; position: relative;" id="end">
		( 서&emsp;<img style="position: absolute; z-index: -1; left: -16px; top: -43px; width: 120px;" id="canvasImg" src="" alt="">명 )</div	>
	</div>
</div>
<br><br>
</div>
</div>
<br><br><br><br><br><br><br>

<!-- 첫번째 서명 -->
<div class="modal1 normal">
	<div class="modal_body1">
		<div style="position: relative;">
			<div><img style="position: absolute; right: -30px; top: 20px;" 
			src="${pageContext.request.contextPath}/resources/icon/del.png" onclick="cancle1();"></div>
			<div class="text">아래 공간에 서명을 하신 후 확인을 누르세요</div>
			<input type="hidden" name="image-data" id="image-data">
			<canvas class="canvas1" id="canvas1" width="785px" height="735px">
			이 브라우저는 캔버스를 지원하지 않습니다.
			</canvas>
			<div class="flex flex-center">
				<div style="margin-right: 11px;" class="modal_insert1" id="modal_insert1" onclick="end1();">
				확인
				</div>
				<div style="margin-right: 11px;" class="modal_cancel1" id="modal_cancel1" onclick="cancle1();">
				취소
				</div>
				<div class="modal_clear1" id="modal_clear1" onclick="onClear1();">
				지우기
				</div>
			</div>
		</div>
	</div>
</div>
<!-- 두번째 서명 -->
<div class="modal2 normal">
	<div class="modal_body2">
		<div style="position: relative;">
			<div><img style="position: absolute; right: -30px; top: 20px;" 
			src="${pageContext.request.contextPath}/resources/icon/del.png" onclick="cancle2();"></div>
			<div class="text normal">아래 공간에 서명을 하신 후 확인을 누르세요</div>
			<input type="hidden" name="image-data" id="image-data">
			<canvas class="canvas2" id="canvas2" width="785px" height="735px">
			이 브라우저는 캔버스를 지원하지 않습니다.
			</canvas>
			<div class="flex flex-center">
				<div style="margin-right: 11px;" class="modal_insert2" id="modal_insert2" onclick="end2();">
				확인
				</div>
				<div style="margin-right: 11px;" class="modal_cancel2" id="modal_cancel2" onclick="cancle2();">
				취소
				</div>
				<div class="modal_clear2" id="modal_clear2" onclick="onClear2();">
				지우기
				</div>
				
			</div>
		</div>
	</div>
</div>

<!-- 마지막 서명 -->
<div class="modal normal">
	<div class="modal_body">
		<div style="position: relative;">
			<div><img style="position: absolute; right: -30px; top: 20px;" 
			src="${pageContext.request.contextPath}/resources/icon/del.png" onclick="cancle();"></div>
			<div class="text">아래 공간에 서명을 하신 후 확인을 누르세요</div>
			<input type="hidden" name="image-data" id="image-data">
			<canvas class="canvas" id="canvas" width="785px" height="735px">
			이 브라우저는 캔버스를 지원하지 않습니다.
			</canvas>
			<div class="flex flex-center">
				<div style="margin-right: 11px;" class="modal_insert" id="modal_insert" onclick="end();">
				확인
				</div>
				<div style="margin-right: 11px;" class="modal_cancel" id="modal_cancel" onclick="cancle();">
				취소
				</div>
				<div class="modal_clear" id="modal_clear" onclick="onClear();">
				지우기
				</div>
				
			</div>
		</div>
	</div>
</div>


<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
<script src="${pageContext.request.contextPath}/resources/js/html2canvas.min.js"></script>
<script>

// 첫번째 서명 모달
const body = document.querySelector('body');
const modal1 = document.querySelector('.modal1');
const btnOpenPopup1 = document.querySelector('#end1');

btnOpenPopup1.addEventListener('touchstart', () => {
	
	modal1.classList.toggle('show');

	if (modal1.classList.contains('show')) {
		body.style.overflow = 'hidden';
	}
});
btnOpenPopup1.addEventListener('click', () => {
	
	alert('모바일로 접속해주세요.')
});
modal1.addEventListener('click', (event) => {
	
	
	if (event.target === modal1) {
		// 모달 바깥 영역을 클릭했을 때
		
			closePopupAndRedirect1();
		
	}
});

function end1(){
	
	closePopup1();
}

function closePopupAndRedirect1() {
	modal1.classList.toggle('show');
	if (!modal1.classList.contains('show')) {
		body.style.overflow = 'auto';
	}
	var canvasValue1 = canvas1.toDataURL();
	console.log(canvasValue1);
	const canvasImg1 = document.getElementById('canvasImg1');
	canvasImg1.src = canvasValue1;
	
}
function closePopup1(){
	modal1.classList.toggle('show');
	if (!modal1.classList.contains('show')) {
		body.style.overflow = 'auto';
	}
	var canvasValue1 = canvas1.toDataURL();
	console.log(canvasValue1);
	const canvasImg1 = document.getElementById('canvasImg1');
	canvasImg1.src = canvasValue1;
}

</script>

<script>
// 두번째 서명 모달
// const body = document.querySelector('body');
const modal2 = document.querySelector('.modal2');
const btnOpenPopup2 = document.querySelector('#end2');

btnOpenPopup2.addEventListener('touchstart', () => {
	
	modal2.classList.toggle('show');

	if (modal2.classList.contains('show')) {
		body.style.overflow = 'hidden';
	}
});

btnOpenPopup2.addEventListener('click', () => {
	
	alert('모바일로 접속해주세요.')
});

modal2.addEventListener('touchstart', (event) => {
	
	
	if (event.target === modal2) {
		// 모달 바깥 영역을 클릭했을 때
		
			closePopupAndRedirect2();
		
	}
});

function end2(){
	
	closePopup2();
}

function closePopupAndRedirect2() {
	modal2.classList.toggle('show');
	if (!modal2.classList.contains('show')) {
		body.style.overflow = 'auto';
	}
	var canvasValue2 = canvas2.toDataURL();
	console.log(canvasValue2);
	const canvasImg2 = document.getElementById('canvasImg2');
	canvasImg2.src = canvasValue2;
	
}
function closePopup2(){
	modal2.classList.toggle('show');
	if (!modal2.classList.contains('show')) {
		body.style.overflow = 'auto';
	}
	var canvasValue2 = canvas2.toDataURL();
	console.log(canvasValue2);
	const canvasImg2 = document.getElementById('canvasImg2');
	canvasImg2.src = canvasValue2;
}

</script>

<script>
// 마지막 서명 모달
// const body = document.querySelector('body');
const modal = document.querySelector('.modal');
const btnOpenPopup = document.querySelector('#end');

btnOpenPopup.addEventListener('touchstart', () => {
	
	modal.classList.toggle('show');

	if (modal.classList.contains('show')) {
		body.style.overflow = 'hidden';
	}
});
btnOpenPopup.addEventListener('click', () => {
	
	alert('모바일로 접속해주세요.')
});


modal.addEventListener('touchstart', (event) => {
	
	
	if (event.target === modal) {
		// 모달 바깥 영역을 클릭했을 때
		
			closePopupAndRedirect();
		
	}
});

function end(){
	
	closePopup();
}

function closePopupAndRedirect() {
	modal.classList.toggle('show');
	if (!modal.classList.contains('show')) {
		body.style.overflow = 'auto';
	}
	var canvasValue = canvas.toDataURL();
	console.log(canvasValue);
	const canvasImg = document.getElementById('canvasImg');
	canvasImg.src = canvasValue;
	
}
function closePopup(){
	modal.classList.toggle('show');
	if (!modal.classList.contains('show')) {
		body.style.overflow = 'auto';
	}
	var canvasValue = canvas.toDataURL();
	console.log(canvasValue);
	const canvasImg = document.getElementById('canvasImg');
	canvasImg.src = canvasValue;
}

</script>
<script>
// 첫번째 서명
//canvas 엘리먼트를 가져옵니다
var canvas1 = document.getElementById("canvas1");
//canvas 엘리먼트의 context를 가져옵니다.
var ctx1 = canvas1.getContext("2d");

//이전 좌표값을 저장할 변수를 초기화합니다.
var lastX1;
var lastY1;
ctx1.lineWidth = 5;

let drawing1 = false;	// true일 때만 그리기
function draw1(curX1, curY1) {
    ctx1.beginPath();
    ctx1.moveTo(startX1, startY1);
    ctx1.lineTo(curX1, curY1);
    ctx1.stroke();
}

canvas1.addEventListener("touchmove", touchMove, false);
canvas1.addEventListener("touchstart", touchStart, false);
canvas1.addEventListener("touchend", touchEnd, false);

function getTouchPos1(e) {
	const canvasRect1 = canvas1.getBoundingClientRect();
	// getBoundingClientRect() 메소드를 사용하여 캔버스 엘리먼트의 위치 정보를 얻고, 
	// e.touches에서 받은 clientX와 clientY 값을 기준으로 좌표 값을 계산합니다. 그 후, 
	// x와 y 값을 반환합니다.
    return {
    	x: e.touches[0].clientX - canvasRect1.left,
        y: e.touches[0].clientY - canvasRect1.top
    }
}

function touchStart(e) {
    e.preventDefault();
    drawing1 = true;
    const { x, y } = getTouchPos1(e);
    startX1 = x;
    startY1 = y;
}
function touchMove(e) {
    if(!drawing1) return;
    const { x, y } = getTouchPos1(e);
    draw1(x, y);
    startX1 = x;
    startY1 = y;
}
function touchEnd(e) {
    if(!drawing1) return;
    // 점을 찍을 경우 위해 마지막에 점을 찍는다.
    // touchEnd 이벤트의 경우 위치정보가 없어서 startX, startY를 가져와서 점을 찍는다.
    ctx1.beginPath();
    
    ctx1.arc(startX1, startY1, ctx1.lineWidth/2, 0, 2*Math.PI);
    ctx1.fillStyle = ctx1.strokeStyle;
    ctx1.fill();
    drawing1 = false;
}

function onClear1() {
    canvas1 = document.getElementById('canvas1');
    ctx1.save();
    ctx1.setTransform(1, 0, 0, 1, 0, 0);
	ctx1.clearRect(0, 0, canvas1.width, canvas1.height);
	ctx1.restore();
}
function cancle1(){
//	ctx1.clearRect(0, 0, canvas1.width, canvas1.height);
	modal1.classList.toggle('show');
	if (!modal1.classList.contains('show')) {
		body.style.overflow = 'auto';
	}
//	const canvasImg1 = document.getElementById('canvasImg1');
//	canvasImg1.src = "";
}


</script>

<script>
// 두번째 서명
//canvas 엘리먼트를 가져옵니다
var canvas2 = document.getElementById("canvas2");
//canvas 엘리먼트의 context를 가져옵니다.
var ctx2 = canvas2.getContext("2d");

//이전 좌표값을 저장할 변수를 초기화합니다.
var lastX2;
var lastY2;
ctx2.lineWidth = 5;

let drawing2 = false;	// true일 때만 그리기
function draw2(curX2, curY2) {
    ctx2.beginPath();
    ctx2.moveTo(startX2, startY2);
    ctx2.lineTo(curX2, curY2);
    ctx2.stroke();
}

canvas2.addEventListener("touchmove", touchMove, false);
canvas2.addEventListener("touchstart", touchStart, false);
canvas2.addEventListener("touchend", touchEnd, false);

function getTouchPos2(e) {
	const canvasRect2 = canvas2.getBoundingClientRect();
	// getBoundingClientRect() 메소드를 사용하여 캔버스 엘리먼트의 위치 정보를 얻고, 
	// e.touches에서 받은 clientX와 clientY 값을 기준으로 좌표 값을 계산합니다. 그 후, 
	// x와 y 값을 반환합니다.
    return {
    	x: e.touches[0].clientX - canvasRect2.left,
        y: e.touches[0].clientY - canvasRect2.top
    }
}

function touchStart(e) {
    e.preventDefault();
    drawing2 = true;
    const { x, y } = getTouchPos2(e);
    startX2 = x;
    startY2 = y;
}
function touchMove(e) {
    if(!drawing2) return;
    const { x, y } = getTouchPos2(e);
    draw2(x, y);
    startX2 = x;
    startY2 = y;
}
function touchEnd(e) {
    if(!drawing2) return;
    // 점을 찍을 경우 위해 마지막에 점을 찍는다.
    // touchEnd 이벤트의 경우 위치정보가 없어서 startX, startY를 가져와서 점을 찍는다.
    ctx2.beginPath();
    
    ctx2.arc(startX2, startY2, ctx2.lineWidth/2, 0, 2*Math.PI);
    ctx2.fillStyle = ctx1.strokeStyle;
    ctx2.fill();
    drawing2 = false;
}

function onClear2() {
    canvas2 = document.getElementById('canvas2');
    ctx2.save();
    ctx2.setTransform(1, 0, 0, 1, 0, 0);
	ctx2.clearRect(0, 0, canvas2.width, canvas2.height);
	ctx2.restore();
}
function cancle2(){
	ctx2.clearRect(0, 0, canvas2.width, canvas2.height);
	modal2.classList.toggle('show');
	if (!modal2.classList.contains('show')) {
		body.style.overflow = 'auto';
	}
	const canvasImg2 = document.getElementById('canvasImg2');
	canvasImg2.src = "";
}


</script>

<script>
// 마지막 서명
//canvas 엘리먼트를 가져옵니다
var canvas = document.getElementById("canvas");
//canvas 엘리먼트의 context를 가져옵니다.
var ctx = canvas.getContext("2d");

//이전 좌표값을 저장할 변수를 초기화합니다.
var lastX;
var lastY;
ctx.lineWidth = 5;

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
    ctx.save();
    ctx.setTransform(1, 0, 0, 1, 0, 0);
	ctx.clearRect(0, 0, canvas.width, canvas.height);
	ctx.restore();
}
function cancle(){
	ctx.clearRect(0, 0, canvas.width, canvas.height);
	modal.classList.toggle('show');
	if (!modal.classList.contains('show')) {
		body.style.overflow = 'auto';
	}
	const canvasImg = document.getElementById('canvasImg');
	canvasImg.src = "";
}


</script>

<script>

// 웹페이지 이미지로 파일 저장
$(document).ready(function() {

	var cs_data_01 = $('input[name=cs_data_01]').val();
	var cs_data_02 = $('input[name=cs_data_02]').val();
	var cs_data_03 = $('input[name=cs_data_03]').val();
	var cs_data_04 = $('input[name=cs_data_04]').val();
	var cs_data_05 = $('input[name=cs_data_05]').val();
	var cs_data_06 = $('input[name=cs_data_06]').val();
	var cs_data_07 = $('input[name=cs_data_07]').val();
	var cs_data_08 = $('input[name=cs_data_08]').val();
	var cs_data_09 = $('input[name=cs_data_09]').val();
	var cs_data_10 = $('input[name=cs_data_10]').val();
	
	if($('input[name=cs_data_11]').checked){
		var cs_data_11 = $('input[name=cs_data_11]').val();
	}else{
		var cs_data_11 = '';
	}
	
	var cs_data_12 = $('input[name=cs_data_12]').val();
	var cs_data_13 = $('input[name=cs_data_13]').val();
	var cs_data_14 = $('input[name=cs_data_14]').val();
	var cs_data_15 = $('input[name=cs_data_15]').val();
	if($('input[name=cs_data_16]').checked){
		var cs_data_16 = $('input[name=cs_data_16]').val();
	}else{
		var cs_data_16 = '';
	}
	
	
	var cs_data_17 = $('input[name=cs_data_17]').val();
	var cs_data_18 = $('input[name=cs_data_18]').val();
	var cs_data_19 = $('input[name=cs_data_19]').val();
	var cs_data_20 = $('input[name=cs_data_20]').val();
	var cs_data_21 = $('input[name=cs_data_21]').val();
	var cs_data_22 = $('input[name=cs_data_22]').val();
	var cs_data_23 = $('input[name=cs_data_23]').val();
	var cs_data_24 = $('input[name=cs_data_24]').val();
	var cs_data_25 = $('input[name=cs_data_25]').val();
	var cs_data_26 = $('input[name=cs_data_26]').val();
	var cs_data_27 = $('input[name=cs_data_27]').val();
	var cs_data_28 = $('input[name=cs_data_28]').val();
	var cs_data_29 = $('input[name=cs_data_29]').val();
	
	
	$(function(){
		$(".btn_download").click(function(e){
			html2canvas(document.getElementById("capture_area")).then(function(canvas)
				{
					var el = document.createElement("a")
					el.href = canvas.toDataURL("image/png")
					
					// 이미지 url
					var image = canvas.toDataURL("image/png");
					image = image.replace("data:image/png;base64,", "");
					console.log(image);
					
					$.ajax({
					     url: "${pageContext.request.contextPath}/saveImage",
					     method: "POST",
					     data: { image: image },
					     success: function() {
					       alert("이미지가 성공적으로 등록되었습니다.");
					     },
					     error: function() {
					       alert("이미지 등록에 실패했습니다.");
					     }
					   });
					
					el.click()
				});
		});
	});
});
</script>
</section>
<div style="background-color: #ffffff; position:fixed; bottom:0; width: 100%; z-index: 1;">
<div style=" border-top: 1px solid #ddd;"></div>
<footer class="normal" style="width: 1087px; margin: 0 auto; padding-top: 20px;">
	<div style="display: flex; justify-content: space-between; margin-left: 33px;">
	    <div style="display: flex;">
	    	<input style="display: flex; justify-content: center; align-items: center; background-color: #7b7b7b; color: #ffffff;
	    	 height: 70px; width: 200px; border-radius: 5px; font-size: 18pt; margin-right: 22px; cursor:pointer; border:none;" id="btn_save" 
	    	 class="btn_save" type="button" value="임시저장" >
	    	
	    	 <!-- 
	        <div style="display: flex; justify-content: center; align-items: center; background-color: #03a9d0; color: #ffffff; 
	        border-radius: 5px; height: 70px; width: 200px; font-size: 18pt;" id="btn_download" class="btn_download">등록</div>
	         -->
	    </div>
		<!-- 등록버튼	    
		<div style="display: flex; justify-content: flex-end;">
			<div style="display: flex; justify-content: center; align-items: center; background-color: #EBAA92; color: #ffffff; height: 40px; width: 70px;" id="end">서명</div>
	    </div>
		-->
	</div>
	<div style="margin-bottom: 20px;"></div>
</footer>
</div>
</form>
</body>
<script>
function toggleCheckbox() {
	var checkbox22 = document.getElementsByName("cs_data_22")[0];
	var checkbox23 = document.getElementsByName("cs_data_23")[0];
	var checkbox24 = document.getElementsByName("cs_data_24")[0];
	var checkbox25 = document.getElementsByName("cs_data_25")[0];
	var checkbox26 = document.getElementsByName("cs_data_26")[0];
	var checkbox27 = document.getElementsByName("cs_data_27")[0];
  
	if (checkbox22.checked) {
		checkbox23.disabled = false;
		checkbox24.disabled = false;
		checkbox25.disabled = false;
		checkbox26.disabled = false;
		checkbox27.disabled = false;
    
	} else {
		checkbox23.disabled = true;
		checkbox24.disabled = true;
		checkbox25.disabled = true;
		checkbox26.disabled = true;
		checkbox27.disabled = true;
		
		$("#cs_data_24").prop("checked", false);
		$("#cs_data_25").prop("checked", false);
		$("#cs_data_26").prop("checked", false);
		
	}
}
$("#btn_save").click(function () {
	console.log("클릭");
	var frm = $("#frm");
	var confirmation = confirm("임시저장을 하시겠습니까?");
	if (confirmation) {
		frm.submit();
	}
});
</script>
</html>
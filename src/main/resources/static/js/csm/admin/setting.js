

let Id;
let SubId;
let OptionId;
let Type = '';
const orderSaveTimers = {};

(() => {
	const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
	const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || '';
	if (!csrfToken || !csrfHeader) return;

	if (typeof $ !== 'undefined' && $.ajaxSetup) {
		$.ajaxSetup({
			beforeSend: function(xhr) {
				xhr.setRequestHeader(csrfHeader, csrfToken);
			}
		});
	}

	if (typeof window.fetch === 'function') {
		const originalFetch = window.fetch.bind(window);
		window.fetch = function(input, init = {}) {
			const nextInit = { ...init };
			const headers = new Headers(nextInit.headers || {});
			if (!headers.has(csrfHeader)) {
				headers.set(csrfHeader, csrfToken);
			}
			nextInit.headers = headers;
			if (!nextInit.credentials) {
				nextInit.credentials = 'same-origin';
			}
			return originalFetch(input, nextInit);
		};
	}
})();

const APP_CTX = window.location.pathname.startsWith('/csm') ? '/csm' : '';
const ICON_BASE = `${APP_CTX}/icon/ev`;

function updateSelectedCategoryStyles() {
	['mainCategoryDiv', 'subCategoryDiv', 'optionCategoryDiv'].forEach((containerId) => {
		const container = document.getElementById(containerId);
		if (!container) return;
		container.querySelectorAll('.custom-radio').forEach((label) => {
			const input = label.querySelector('input[type="radio"]');
			label.classList.toggle('is-selected', Boolean(input && input.checked));
		});
	});
}

function isEnabledFlag(value) {
	return value === 1 || value === '1' || value === true;
}

function buildSubcategoryInputTypes(category = {}) {
	const types = [];
	if (isEnabledFlag(category.cc_col_04)) types.push('checkbox');
	if (isEnabledFlag(category.cc_col_05)) types.push('radio');
	if (isEnabledFlag(category.cc_col_07)) types.push('select');
	if (isEnabledFlag(category.cc_col_06)) types.push('text');
	return types.join(',');
}

function getLabelTextFromCategory(labelElement) {
	if (!labelElement) return '';
	const text = Array.from(labelElement.childNodes)
		.filter((node) => node.nodeType === Node.TEXT_NODE)
		.map((node) => node.textContent.replace(/\s+/g, ' ').trim())
		.filter(Boolean)
		.join(' ')
		.trim();
	return text;
}

function formatInputTypeChips(inputTypeText = '') {
	const map = {
		checkbox: '체크박스',
		radio: '라디오박스',
		select: '셀렉트박스',
		text: '텍스트박스'
	};
	return inputTypeText
		.split(',')
		.map((type) => type.trim())
		.filter(Boolean)
		.map((type) => `<span class="modal2-chip">${map[type] || type}</span>`)
		.join('');
}

//function getSubCategories(parentId, element) {
//	console.log("Category ID:", parentId);
//    const categoryType = element.getAttribute('data-category-type');
//    console.log("Category Type:", categoryType);
//    getSubCategories = categoryType;
//    Id = parentId;
//    Type = categoryType;
//    
//	$('#subCategoryDiv').html('');
//	$('#optionCategoryDiv').html('');
//    $.ajax({
//        url: '/csm/getSubcategories',
//        data: { categoryId: parentId },
//        success: function(data) {
//            var html = '';
//            $.each(data, function (index, category) {
//		    html += `
//		        <label class="custom-radio" data-category-type="sub">
//		            <input 
//		                type="radio" 
//		                data-category-type="child" 
//		                name="subCategory" 
//		                value="${category.cc_col_01}" 
//		                onclick="getOptions(${category.cc_col_01}, this)"
//		                >
//		            <span class="custom-radio-mark"></span>
//		            ${category.cc_col_02}
//		            <svg xmlns="http://www.w3.org/2000/svg"
//						width="16"
//						height="16"
//						fill="currentColor"
//						class="bi bi-pen"
//						viewBox="0 0 16 16"
//						value="${category.cc_col_01}">
//						  <path d="m13.498.795.149-.149a1.207 1.207 0 1 1 1.707 1.708l-.149.148a1.5 1.5 0 0 1-.059 2.059L4.854 14.854a.5.5 0 0 1-.233.131l-4 1a.5.5 0 0 1-.606-.606l1-4a.5.5 0 0 1 .131-.232l9.642-9.642a.5.5 0 0 0-.642.056L6.854 4.854a.5.5 0 1 1-.708-.708L9.44.854A1.5 1.5 0 0 1 11.5.796a1.5 1.5 0 0 1 1.998-.001m-.644.766a.5.5 0 0 0-.707 0L1.95 11.756l-.764 3.057 3.057-.764L14.44 3.854a.5.5 0 0 0 0-.708z"/>
//						</svg>
//		        </label>
//		    	`;
//			});
//            $('#subCategoryDiv').html(html);
//        }
//    });
//}

function OptionSelect(id, element) {
	console.log("Option Id : " + id);
	const categoryType =  element.getAttribute('data-category-type');
	console.log("Option Type : " + categoryType);
	
	OptionId = id;
	
}
//DeleteCategory 함수 호출 시 categoryTypePost와 hiddenId를 사용
function DeleteCategory() {
    if (categoryTypePost && hiddenId) {
        deleteCategoryHandler();
    } else {
        console.log("카테고리 정보가 누락되었습니다.");
    }
}

function toggleCheckboxRadio(type) {
	if (type === 'checkbox') {
        document.getElementById("isRadio").checked = false;
    } else if (type === 'radio') {
        document.getElementById("isCheckbox").checked = false;
    }
}

function toggleSelectOptions() {
    const selectOptionsDiv = document.getElementById("selectOptions");
    const isSelectboxChecked = document.getElementById("isSelectbox").checked;
    selectOptionsDiv.style.display = isSelectboxChecked ? "block" : "none";
}
// 템플릿 불러오기
	$('#templateSelect').click(function() {
//		console.log("asd");

		const modalContent = $('#templateModalContent'); // 모달 내부 콘텐츠 영역
    	modalContent.empty(); // 이전 콘텐츠 초기화
		$('#del_icon').css('display', 'flex');	
    	
    	// 서버에서 템플릿 목록 불러오기
	    $.ajax({
	        url: '/csm/setting/templates', // 템플릿 데이터를 제공하는 API
	        type: 'GET',
	        dataType: 'json',
	        success: function(data) {
				let html = '';
	            if (data && data.length > 0) {
	                html += '<ul id="templateList" style="padding: 20px;">';
	                data.forEach(template => {
	                    html += `<li>
	                                <label class="template-option">
	                                    <input type="radio" name="template" value="${template.idx}">
	                                    <span class="checkmark"></span>
	                                    ${template.name}
	                                </label>
	                             </li>`;
	                             
	                });
	                html += '</ul>';
	            } else {
	                html = '<p>No templates available.</p>';
	            }
	            modalContent.html(html);
	            
	            $('input[name="template"]').change(function() {
	                const selectedTemplateIdx = $(this).val();
	                $('#templateConfirm').attr("onclick", `templatepreview('${selectedTemplateIdx}')`);
	            });
	        },
	        error: function(error) {
	            console.error("Error fetching templates:", error);
	            modalContent.html('<p>Error fetching templates. Please try again.</p>');
	        }
	    });
    	
		templateModalOpen();
	});
function templatepreview(templateIdx) {
	console.log(templateIdx);
	console.log('템플릿 불러오기 미리보기');
	choiceTemplate = templateIdx;
	spinner();
	$.ajax({
		url: '/csm/setting/getTemplate/'+templateIdx,
		type: 'GET',
	    dataType: 'json',
		success: function(data) {
			const { mainCategories, subCategoryMap, optionMap } = data;
            console.log('Category Data:', mainCategories);
            console.log('SubCategory Map:', subCategoryMap);
            console.log('Option Map:', optionMap);
            $('.templateModal_footer').css('display', 'none');
			$('#del_icon').css('display', 'flex');	
            if (data) {
                const modalContent = $('#templateModalContent');
                hideSpinner();
                let html = `
                <div class="section-content">
                    <div class="content-body">
                        <div class="contentbox">
                            <div class="content-inner">
                                <div class="inner-left">
                                    <div class="left-content">
	                                    <div class="left-header">
	                                        <span>환자상세정보</span>
	                                    </div>
                                        <div class="left-content-1">
                                            <div class="form-row">
                                                <div class="field-group">
                                                    <label for="cs_col_01">환자명</label>
                                                    <input class="input-name" type="text" name="cs_col_01" id="cs_col_01" value="">
                                                </div>
                                                <div class="field-group">
                                                    <label for="cs_col_02">성별</label>
                                                    <select name="cs_col_02" id="cs_col_02">
                                                        <option value="">선택하세요</option>
                                                        <option value="남성">남성</option>
                                                        <option value="여성">여성</option>
                                                    </select>
                                                </div>
	                                            <div class="field-group">
													<label for="cs_col_07">현재계신곳</label>
													<select name="cs_col_07" id="cs_col_07">
														<option value="">선택하세요</option>
														<option value="자택">자택</option>
													    <option value="요양원">요양원</option>
													    <option value="요양병원">요양병원</option>
													    <option value="급성기병원">급성기병원</option>
													    <option value="기타">기타</option>
													</select>
													<input type="text" id="cs_col_07_text" name="cs_col_07_text" value="" style="display: none;" />
												</div>
                                                <div class="field-group">
													<label for="cs_col_05">보혐유형</label>
													<select name="cs_col_05" id="cs_col_05">
														<option value="">선택하세요</option>
														<option value="건강보험">건강보험</option>
														<option value="건강보험(차상위1종)">건강보험(차상위1종)</option>
														<option value="건강보험(차상위2종)">건강보험(차상위2종)</option>
														<option value="건강보혐(차상위2종장애)">건강보혐(차상위2종장애)</option>
														<option value="의료급여1종">의료급여1종</option>
														<option value="의료급여2종">의료급여2종</option>
														<option value="의료급여2종장애">의료급여2종장애</option>
														<option value="자보">자보</option>
														<option value="산재">산재</option>
													</select>
												</div>
												<div class="field-group checkbox-group">
													<input type="checkbox" id="cs_col_06" name="cs_col_06" value="실손보험">
													<label for="cs_col_06">실손보험</label>
												</div>
                                            </div>
											<div class="form-row">
                                                <div class="field-group">
                                                    <label for="cs_col_03">생년월일</label>
                                                    <input class="purchase_date" name="cs_col_03" id="cs_col_03" value="" type="date" placeholder="년도-월-일"  maxlength="10"/>
                                                </div>
                                                <div class="field-group">
                                                    <label for="cs_col_04">나이(만)</label>
                                                    <input class="input-age" type="text" name="cs_col_04" id="cs_col_04" value="" readonly="readonly">
                                                </div>
												<div class="field-group">
            										<label for="cs_col_08">상담유입경로</label>
													<select name="cs_col_08" id="cs_col_08">
														<option value="">선택하세요</option>
														<option value="인터넷">인터넷</option>
							                            <option value="대외협력">대외협력</option>
							                            <option value="협력병원">협력병원</option>
														<option value="직원유치">직원유치</option>
														<option value="입원환자소개">입원환자소개</option>
														<option value="재입원">재입원</option>
														<option value="기타">기타</option>
													</select>
													<input type="text" id="cs_col_08_text" name="cs_col_08_text" style="display: none;" />
												</div>
												<div class="field-group">
            										<label for="cs_col_09">잠재고객</label>
													<select name="cs_col_09" id="cs_col_09">
														<option value="">선택하세요</option>
							                            <option value="A">A</option>
							                            <option value="B">B</option>
							                            <option value="C">C</option>
							                        </select>
						                        </div>
					                        	<div class="field-group checkbox-group">
													<input type="checkbox" id="cs_col_10" name="cs_col_10" value="BC" >
													<label for="cs_col_10">BC</label>
												</div>
											</div>
                                        </div>
                                        <div class="category-wrapper" style="overflow-y: auto; height: calc(100% - 120px);">
	                                        <div class="category-container setting-popup" id="category-container">
	                                        
	                                        </div>
											<div class="left-content-2" style="">
												<div class="left-container">
													<div class="left-custom">
														<div class="left-customname">현상태</div>
														<textarea  class="left-textarea" name="cs_col_11" id="cs_col_11"></textarea>
													</div>
												</div>
											</div>
                                        </div>
									</div>
								</div>
								
								<div class="inner-right">
									<div class="right-header">상담기록</div>
									<div class="right-content" style="flex-direction: column; display: flex; height: calc(100% - 104px);">
										<div class="right-content-1">
										    <div class="counsel-form">
									            <div class="form-section">
									                <div id="guardianContainer">
									                    <div class="guardian-entry">
									                        <div class="form-group">
									                            <label class="protector_name_text" for="protector_name">보호자명</label>
									                            <input class="protector_name" type="text" name="cs_col_13[]" >
									                        </div>
									                        <div class="form-group">
									                            <label class="protector_connection_text" for="protector_connection">관계</label>
									                            <input class="protector_connection" type="text" name="cs_col_14[]" >
									                        </div>
									                        <div class="form-group guardian-phone">
									                            <label class="protector_phone_text" for="protector_phone">연락처</label>
									                            <div class="input-with-icon">
										                            <input class="protector_phone" type="text" name="cs_col_15[]" pattern="^\+?[0-9\-]{7,15}$" maxlength="13" placeholder="" title="유효한 연락처를 입력해주세요.">
										                            <img class="sms_icon" style="" src="/csm/icon/ev/sender-icon.png">
										                       	</div>
									                        </div>
											                <div>
												                <button type="button" class="removeGuardian" onclick="removeGuardian(this)"></button>
												                <button type="button" class="addGuardianButton" onclick="addGuardian()"></button>
											                </div>
									                    </div>
								                        
									                </div>
									            </div>
									        </div>
										    
										    <!--  -->
										</div>
										<div class="right-content-2" style="calc(100% - 84px);>
											<div id="counselContainer" class="counsel-container">
												<div class="counsel-entry" style="display: flex;">
													<div class="form-group">
														<label for="cs_col_16">상담일자</label>
														<input class="purchase_date" name="cs_col_16" id="cs_col_16" type="text" placeholder="년도-월-일" maxlength="10"/>
													</div>
													<div class="form-group">
														<img id="form-reset" style="width: 15px; cursor: pointer; position: absolute; top: 20px; right: 0;" src="/csm/icon/ev/refresh.png">
														<label for="cs_col_17">상담자</label>
														<input type="text" name="cs_col_17" id="cs_col_17" >
													</div>
													<div class="form-group">
														<label for="">방법</label>
														<select name="cs_col_18" id="cs_col_18">
															<option value="">선택하세요</option>
															<option value="전화">전화</option>
															<option value="방문">방문</option>
														</select>
													</div>
													<div class="form-group">
														<label for="cs_col_19">결과</label>
														<select name="cs_col_19" id="cs_col_19">
															<option value="">선택하세요</option>
															<option value="입원완료">입원완료</option>
															<option value="입원예약">입원예약</option>
															<option value="입원안함">입원안함</option>
														</select>
													</div>
												</div>
												<div id="reservationFields" style="display: none;">
											        <div class="form-group">
											            <label for="cs_col_21">입원예정일</label>
											            <input class="purchase_date" name="cs_col_21" id="cs_col_21" type="date" placeholder="년도-월-일" maxlength="10"/>
											        </div>
											        <div class="form-group">
														<label for="cs_col_21_time">예정시간</label>
														<input class="purchase_time" type="text" name="cs_col_21_time" id="cs_col_21_time" placeholder="HH:mm">
													</div>
											        <div class="form-group">
											            <label for="cs_col_21_room">예상 호실</label>
											            <input type="text" name="cs_col_38" id="cs_col_38">
											        </div>
											    </div>
											    <div id="noAdmissionReason" style="display: none;">
											        <div class="form-group">
											            <label for="cs_col_20">미입원사유</label>
											            <select name="cs_col_20" id="cs_col_20">
															<option value="">선택하세요</option>
															<option value="병원쇼핑">병원쇼핑</option>
															<option value="간병비">간병비</option>
															<option value="병실차액">병실차액</option>
															<option value="타병원입원">타병원입원</option>
															<option value="입원불허">입원불허</option>
															<option value="기타">기타</option>
														</select>
											        </div>
											    </div>
											</div>
											<div class="right-container" style="height: auto;">
												<div class="right-custom">
													<div class="right-customname">상담내용</div>
													<textarea style="height: 200px;" class="right-textarea" name="cs_col_32" id="cs_col_32"></textarea>
												</div>
											</div>
											<div class="right-content-4">
												<div class="counsel-log">
													<div class="logs-header" style="display: flex; justify-content: space-around;">
														<div class="date">상담일자</div>
														<div class="method">방법</div>
														<div class="result">결과</div>
														<div class="counselor">상담자</div>
													</div>
													
												</div>
											</div>
										</div>
										
										<div class="right-content-5" style="position: absolute; width: calc(100% - 14px); margin: 0px; padding: 10px 18px;">
											<button style="width: 100%; font-size: 1.2rem; color: #fff;" class="pop-btn" style="font-size:15px; color: #fff; width: 100%;" id="templateconfirm">템플릿 선택</button>
										</div>
									</div>
								</div>
							</div>
						</div>
					</div>
				</div>
                </div>`;
                modalContent.html(html);
				renderCategories(data); // Render the categories
                // Open the modal
                $('#templateModal').addClass('show');
            }
        },
        error: function(error) {
            console.error("Error fetching template details:", error);
            alert("Failed to fetch template details. Please try again.");
        }
	});
}
$('#del_icon').click(function() {
	templateModalClose();
    $('.templateModal_footer').css('display', 'flex');
})
$('#templatePreview').click(function(templateIdx) {
	choiceTemplate = templateIdx;
	console.log("템플릿 미리보기");
	spinner();
	$.ajax({
		url: '/csm/setting/TemplatePreview',
		type: 'GET',
	    dataType: 'json',
		success: function(data) {
			hideSpinner();
			console.log('Response data:', data);
			// 잘못된 변수 제거 및 실제 데이터 사용
            const { categoryData, fieldOptionsMapping, fieldTypeMapping } = data;
            console.log('Category Data:', categoryData);
            console.log('Field Options Mapping:', fieldOptionsMapping);
            console.log('Field Type Mapping:', fieldTypeMapping);
	            $('.templateModal_footer').css('display', 'none');
            if (data) {
				$('#template_header').html("미리보기");
				$('#back-icon').css('display','none');
				$('#del_icon').css('display', 'flex');
                const modalContent = $('#templateModalContent');
                let html = `
                <div class="section-content">
                    <div class="content-body">
                        <div class="contentbox">
                            <div class="content-inner">
                                <div class="inner-left">
                                    <div class="left-header">
                                        <span>환자상세정보</span>
                                    </div>
                                    <div class="left-content">
                                        <div class="left-content-1">
                                            <div class="form-row">
                                                <div class="field-group">
                                                    <label for="cs_col_01">환자명</label>
                                                    <input class="input-name" type="text" name="cs_col_01" id="cs_col_01" value="">
                                                </div>
                                                <div class="field-group">
                                                    <label for="cs_col_02">성별</label>
                                                    <select name="cs_col_02" id="cs_col_02">
                                                        <option value="">선택하세요</option>
                                                        <option value="남성">남성</option>
                                                        <option value="여성">여성</option>
                                                    </select>
                                                </div>
	                                            <div class="field-group">
													<label for="cs_col_07">현재계신곳</label>
													<select name="cs_col_07" id="cs_col_07">
														<option value="">선택하세요</option>
														<option value="자택">자택</option>
													    <option value="요양원">요양원</option>
													    <option value="요양병원">요양병원</option>
													    <option value="급성기병원">급성기병원</option>
													    <option value="기타">기타</option>
													</select>
													<input type="text" id="cs_col_07_text" name="cs_col_07_text" value="" style="display: none;" />
												</div>
                                                <div class="field-group">
													<label for="cs_col_05">보혐유형</label>
													<select name="cs_col_05" id="cs_col_05">
														<option value="">선택하세요</option>
														<option value="건강보험">건강보험</option>
														<option value="건강보험(차상위1종)">건강보험(차상위1종)</option>
														<option value="건강보험(차상위2종)">건강보험(차상위2종)</option>
														<option value="건강보혐(차상위2종장애)">건강보혐(차상위2종장애)</option>
														<option value="의료급여1종">의료급여1종</option>
														<option value="의료급여2종">의료급여2종</option>
														<option value="의료급여2종장애">의료급여2종장애</option>
														<option value="자보">자보</option>
														<option value="산재">산재</option>
													</select>
												</div>
												<div class="field-group checkbox-group">
													<input type="checkbox" id="cs_col_06" name="cs_col_06" value="실손보험">
													<label for="cs_col_06">실손보험</label>
												</div>
                                            </div>
											<div class="form-row">
                                                <div class="field-group">
                                                    <label for="cs_col_03">생년월일</label>
                                                    <input class="purchase_date" name="cs_col_03" id="cs_col_03" value="" type="text" placeholder="년도-월-일"  maxlength="10"/>
                                                </div>
                                                <div class="field-group">
                                                    <label for="cs_col_04">나이(만)</label>
                                                    <input class="input-age" type="text" name="cs_col_04" id="cs_col_04" value="" readonly="readonly">
                                                </div>
												<div class="field-group">
            										<label for="cs_col_08">상담유입경로</label>
													<select name="cs_col_08" id="cs_col_08">
														<option value="">선택하세요</option>
														<option value="인터넷">인터넷</option>
							                            <option value="대외협력">대외협력</option>
							                            <option value="협력병원">협력병원</option>
														<option value="직원유치">직원유치</option>
														<option value="입원환자소개">입원환자소개</option>
														<option value="재입원">재입원</option>
														<option value="기타">기타</option>
													</select>
													<input type="text" id="cs_col_08_text" name="cs_col_08_text" style="display: none;" />
												</div>
												<div class="field-group">
            										<label for="cs_col_09">잠재고객</label>
													<select name="cs_col_09" id="cs_col_09">
														<option value="">선택하세요</option>
							                            <option value="A">A</option>
							                            <option value="B">B</option>
							                            <option value="C">C</option>
							                        </select>
						                        </div>
					                        	<div class="field-group checkbox-group">
													<input type="checkbox" id="cs_col_10" name="cs_col_10" value="BC" >
													<label for="cs_col_10">BC</label>
												</div>
											</div>
                                        </div>
                                        <div class="category-wrapper" style="overflow-y: auto; height: calc(100% - 120px);">
	                                        <div class="category-container setting-popup" id="category-container">
	                                        
	                                        </div>
											<div class="left-content-2" style="">
												<div class="left-container">
													<div class="left-custom">
														<div class="left-customname">현상태</div>
														<textarea  class="left-textarea" name="cs_col_11" id="cs_col_11"></textarea>
													</div>
												</div>
											</div>
                                        </div>
                                        
									</div>
								</div>
								
								<div class="inner-right">
									<div class="right-header">상담기록</div>
									<div class="right-content">
										<div class="right-content-1">
										    <div class="counsel-form">
									            <div class="form-section">
									                <div id="guardianContainer">
									                    <div class="guardian-entry">
									                        <div class="form-group">
									                            <label class="protector_name_text" for="protector_name">보호자명</label>
									                            <input class="protector_name" type="text" name="cs_col_13[]" >
									                        </div>
									                        <div class="form-group">
									                            <label class="protector_connection_text" for="protector_connection">관계</label>
									                            <input class="protector_connection" type="text" name="cs_col_14[]" >
									                        </div>
									                        <div class="form-group guardian-phone">
									                            <label class="protector_phone_text" for="protector_phone">연락처</label>
									                            <div class="input-with-icon">
										                            <input class="protector_phone" type="text" name="cs_col_15[]" pattern="^\+?[0-9\-]{7,15}$" maxlength="13" placeholder="숫자만 입력해 주세요." title="유효한 연락처를 입력해주세요.">
										                            <img class="sms_icon" style="" src="/csm/icon/ev/sender-icon.png">
										                       	</div>
									                        </div>
									                        <div>
												                <button type="button" class="removeGuardian" onclick="removeGuardian(this)"></button>
												                <button type="button" class="addGuardianButton" onclick="addGuardian()"></button>
											                </div>
									                    </div>
									                </div>
									            </div>
									        </div>
										    
										    <!--  -->
										</div>
										<div class="right-content-2">
											<div id="counselContainer" class="counsel-container">
												<div class="counsel-entry" style="display: flex;">
													<div class="form-group">
														<label for="cs_col_16">상담일자</label>
														<input class="purchase_date" name="cs_col_16" id="cs_col_16" type="text" placeholder="년도-월-일" maxlength="10"/>
													</div>
													<div class="form-group">
														<img id="form-reset" style="width: 15px; cursor: pointer; position: absolute; top: 20px; right: 0;" src="/csm/icon/ev/refresh.png">
														<label for="cs_col_17">상담자</label>
														<input type="text" name="cs_col_17" id="cs_col_17" >
													</div>
													<div class="form-group">
														<label for="">방법</label>
														<select name="cs_col_18" id="cs_col_18">
															<option value="">선택하세요</option>
															<option value="전화">전화</option>
															<option value="방문">방문</option>
														</select>
													</div>
													<div class="form-group">
														<label for="cs_col_19">결과</label>
														<select name="cs_col_19" id="cs_col_19">
															<option value="">선택하세요</option>
															<option value="입원완료">입원완료</option>
															<option value="입원예약">입원예약</option>
															<option value="입원안함">입원안함</option>
														</select>
													</div>
												</div>
												<div id="reservationFields" style="display: none;">
											        <div class="form-group">
											            <label for="cs_col_21">입원예정일</label>
											            <input class="purchase_date" name="cs_col_21" id="cs_col_21" type="date" placeholder="년도-월-일" maxlength="10"/>
											        </div>
											        <div class="form-group">
														<label for="cs_col_21_time">예정시간</label>
														<input class="purchase_time" type="text" name="cs_col_21_time" id="cs_col_21_time" placeholder="HH:mm">
													</div>
											        <div class="form-group">
											            <label for="cs_col_21_room">예상 호실</label>
											            <input type="text" name="cs_col_38" id="cs_col_38">
											        </div>
											    </div>
											    <div id="noAdmissionReason" style="display: none;">
											        <div class="form-group">
											            <label for="cs_col_20">미입원사유</label>
											            <select name="cs_col_20" id="cs_col_20">
															<option value="">선택하세요</option>
															<option value="병원쇼핑">병원쇼핑</option>
															<option value="간병비">간병비</option>
															<option value="병실차액">병실차액</option>
															<option value="타병원입원">타병원입원</option>
															<option value="입원불허">입원불허</option>
															<option value="기타">기타</option>
														</select>
											        </div>
											    </div>
											</div>
											<div class="right-container">
												<div class="right-custom">
													<div class="right-customname">상담내용</div>
													<textarea style="height: 200px;" class="right-textarea" name="cs_col_32" id="cs_col_32"></textarea>
												</div>
											</div>
											<div class="right-content-4">
												<div class="counsel-log">
													<div class="logs-header" style="display: flex; justify-content: space-around;">
														<div class="date">상담일자</div>
														<div class="method">방법</div>
														<div class="result">결과</div>
														<div class="counselor">상담자</div>
													</div>
												</div>
											</div>
										</div>
										
									</div>
								</div>
							</div>
						</div>
					</div>
				</div>
                </div>`;
                
				templateModalOpen();
                modalContent.html(html);
				previewrenderCategories(data); // Render the categories
            }
        },
        error: function(error) {
            console.error("Error fetching template details:", error);
            alert("Failed to fetch template details. Please try again.");
        }
	});
});
function previewrenderCategories(data) {
    console.log("Received data:", data);

    const categoryContainer = $('#category-container');
    categoryContainer.empty();

    const { categoryData, fieldOptionsMapping, fieldTypeMapping, csData = { entries: [], cs_col_11: '' } } = data;
    let html = '';

    console.log("Main Categories:", categoryData);

    categoryData.forEach((mainCategory, mainIndex) => {
        const mainIdx = mainCategory.category1.cc_col_01;
        const mainName = mainCategory.category1.cc_col_02;

        html += `
            <div class="category1-wrapper">
                <div class="category1">${mainName}</div>
                <div class="subcategory-list">
        `;

        console.log("Subcategories for", mainName, ":", mainCategory.subcategories);

        mainCategory.subcategories.forEach(subCategoryData => {
            const subCategory = subCategoryData.category2;
            const subIdx = subCategory.cc_col_01;
            const subName = subCategory.cc_col_02;
            const fieldKey = `field_${mainIdx}_${subIdx}`;
            const subcategoryDisplayName = (mainName === subName) ? '' : subName;
            const options = fieldOptionsMapping[fieldKey] || [];
            const fieldType = fieldTypeMapping[fieldKey];

            html += '<div class="input-wrapper">';

            console.log(`Field ${fieldKey}: Type=${fieldType}, Options=`, options);

            switch (fieldType) {
                case 'checkbox_only':
                    let isCheckedCheckboxOnly = '';
                    csData.entries.forEach(entry => {
                        if (entry.subcategory_id === subIdx && entry.value === subName) {
                            isCheckedCheckboxOnly = 'checked="checked"';
                        }
                    });
                    html += `
                        <input type="checkbox" name="${fieldKey}" id="${fieldKey}" value="${subcategoryDisplayName}" ${isCheckedCheckboxOnly}>
                        <label for="${fieldKey}">${subcategoryDisplayName || ''}</label>
                    `;
                    break;

                case 'radio_only':
                    let isCheckedRadioOnly = '';
                    csData.entries.forEach(entry => {
                        if (entry.value === subName) {
                            isCheckedRadioOnly = 'checked="checked"';
                        }
                    });
                    html += `
                        <input type="radio" name="field_${mainIdx}" id="${fieldKey}" value="${subName}_${subIdx}" ${isCheckedRadioOnly}>
                        <label for="${fieldKey}">${subcategoryDisplayName || ''}</label>
                    `;
                    break;

                case 'select_only':
                    let selectedOptionSelect = '';
                    csData.entries.forEach(entry => {
                        if (entry.subcategory_id === subIdx) {
                            selectedOptionSelect = entry.value ? entry.value.trim() : '';
                        }
                    });
                    html += `
                        ${subcategoryDisplayName || ''}
                        <select name="${fieldKey}_select">
                            <option value="">선택하세요</option>
                    `;
                    options.forEach(option => {
                        const optionValue = Array.isArray(option.cc_col_03) ? (option.cc_col_03[0] || '').trim() : (option.cc_col_03 || '').trim();
                        html += `
                            <option value="${optionValue}" ${optionValue === selectedOptionSelect ? 'selected="selected"' : ''}>
                                ${optionValue}
                            </option>
                        `;
                    });
                    html += `</select>`;
                    break;

                case 'text_only':
                    let textOnlyValue = '';
                    csData.entries.forEach(entry => {
                        if (entry.subcategory_id === subIdx) {
                            textOnlyValue = entry.value || '';
                        }
                    });
                    html += `
                        ${subcategoryDisplayName || ''}
                        <input type="text" name="${fieldKey}" value="${textOnlyValue}">
                    `;
                    break;

                case 'checkbox_select':
                    let isCheckedCheckboxSelect = false;
                    let selectedOptionCheckboxSelect = '';
                    csData.entries.forEach(entry => {
                        if (entry.subcategory_id === subIdx) {
                            const splitValues = entry.value ? entry.value.split(',') : [];
                            if (splitValues[0]) isCheckedCheckboxSelect = true;
                            if (splitValues[1]) selectedOptionCheckboxSelect = splitValues[1].trim();
                        }
                    });
                    html += `
                        <input type="checkbox" name="${fieldKey}" id="${fieldKey}" value="${subcategoryDisplayName}" ${isCheckedCheckboxSelect ? 'checked="checked"' : ''}>
                        <label for="${fieldKey}">${subcategoryDisplayName || ''}</label>
                    </div>
                    <div class="input-wrapper">
                        <select name="${fieldKey}_select">
                            <option value="">선택하세요</option>
                    `;
                    options.forEach(option => {
                        const optionValue = Array.isArray(option.cc_col_03) ? (option.cc_col_03[0] || '').trim() : (option.cc_col_03 || '').trim();
                        html += `
                            <option value="${optionValue}" ${optionValue === selectedOptionCheckboxSelect ? 'selected="selected"' : ''}>
                                ${optionValue}
                            </option>
                        `;
                    });
                    html += `</select>`;
                    break;

                case 'checkbox_text':
                    let isCheckedCheckboxText = false;
                    let textValueCheckboxText = '';
                    csData.entries.forEach(entry => {
                        if (entry.subcategory_id === subIdx) {
                            const splitValues = entry.value ? entry.value.split('|') : [];
                            if (splitValues[0]) isCheckedCheckboxText = true;
                            if (splitValues[1]) textValueCheckboxText = splitValues[1].trim();
                        }
                    });
                    html += `
                        <input type="checkbox" name="${fieldKey}" id="${fieldKey}" value="${subcategoryDisplayName}" ${isCheckedCheckboxText ? 'checked="checked"' : ''}>
                        <label for="${fieldKey}">${subcategoryDisplayName || ''}</label>
                    </div>
                    <div class="input-wrapper">
                        <input type="text" name="${fieldKey}_details" value="${textValueCheckboxText}">
                    `;
                    break;

                default:
                    html += `<label>${subcategoryDisplayName || 'Unknown Field Type'}</label>`;
                    break;
            }

            html += `</div>`; // input-wrapper 닫기
        });

        html += `
                </div> <!-- subcategory-list 닫기 -->
            </div> <!-- category1-wrapper 닫기 -->
        `;
    });

    categoryContainer.html(html);
}

function renderCategories(data) {
    console.log("Received data:", data);

    const categoryContainer = $('#category-container');
    categoryContainer.empty();

    // csData가 없어도 기본값 설정 (필요 시 서버에서 추가)
	const { mainCategories, subCategoryMap, fieldOptionsMapping, fieldTypeMapping } = data;
    let html = '';

    console.log("Main Categories:", mainCategories);

    mainCategories.forEach((mainCategory) => {
	        const mainIdx = mainCategory.idx;
	        const subCategories = subCategoryMap[mainIdx] || [];
	
	        console.log(`🔍 mainIdx: ${mainIdx}, subCategories:`, subCategories);
	
	        if (!subCategories || subCategories.length === 0) {
	            console.warn(`⚠️ No subcategories found for main category ${mainIdx}`);
	        }
	
	        html += `
	        <div class="category1-wrapper">
	            <div class="category1">${mainCategory.main_col_01}</div>
	            <div class="subcategory-list">`;
	
	        subCategories.forEach((subCategory) => {
	            const subIdx = subCategory.idx;
	            const fieldKey = `field_${mainIdx}_${subIdx}`;
	            const fieldType = fieldTypeMapping[fieldKey] || "unknown";
	            const options = fieldOptionsMapping[fieldKey] || []; // select, checkbox_select 옵션
	            console.log(`🛠️ Rendering subcategory ${subIdx} with fieldType: ${fieldType}`);
	
	            let subcategoryHtml = "";
	
	            switch (fieldType) {
	                case "checkbox_only":
	                case "radio_only":
	                    subcategoryHtml += `
	                    <div class="input-wrapper">
	                        <input type="${fieldType === "checkbox_only" ? "checkbox" : "radio"}" 
	                               name="${fieldKey}" id="${fieldKey}" value="${subCategory.sub_col_01}">
	                        <label for="${fieldKey}">${subCategory.sub_col_01}</label>
	                    </div>`;
	                    break;
	
	                case "select_only":
	                case "text_only":
	                    subcategoryHtml += `<div class="input-wrapper">
	                        <label>${subCategory.sub_col_01}</label>`;
	                    if (fieldType === "select_only") {
	                        subcategoryHtml += `<select name="${fieldKey}">
	                            <option value="">선택하세요</option>`;
	                        options.forEach(option => {
	                            subcategoryHtml += `<option value="${option.option_col_01}">${option.option_col_01}</option>`;
	                        });
	                        subcategoryHtml += `</select>`;
	                    } else if (fieldType === "text_only") {
	                        subcategoryHtml += `<input type="text" name="${fieldKey}" placeholder="입력하세요">`;
	                    }
	                    subcategoryHtml += `</div>`;
	                    break;
	
	                case "checkbox_text":
	                case "radio_text":
	                    subcategoryHtml += `
	                    <div class="input-wrapper">
	                        <input type="${fieldType === "checkbox_text" ? "checkbox" : "radio"}" 
	                               name="${fieldKey}" id="${fieldKey}" value="${subCategory.sub_col_01}" 
	                               onclick="toggleTextInput('${fieldKey}')">
	                        <label for="${fieldKey}">${subCategory.sub_col_01}</label>
	                    </div>
	                    <div class="input-wrapper">
	                        <input type="text" name="${fieldKey}_details" id="${fieldKey}_details" 
	                               placeholder="입력하세요" style="">
	                    </div>`;
	                    break;
	
	                case "checkbox_select":
	                case "radio_select":
	                    subcategoryHtml += `
	                    <div class="input-wrapper">
	                        <input type="${fieldType === "checkbox_select" ? "checkbox" : "radio"}" 
	                               name="field_${mainIdx}" id="${fieldKey}" value="${subCategory.sub_col_01}">
	                        <label for="${fieldKey}">${subCategory.sub_col_01}</label>
	                    </div>
	                    <div class="input-wrapper">
	                        <select name="${fieldKey}_select">
	                            <option value="">선택하세요</option>`;
	                    options.forEach(option => {
	                        subcategoryHtml += `<option value="${option.option_col_01}">${option.option_col_01}</option>`;
	                    });
	                    subcategoryHtml += `</select></div>`;
	                    break;
	
	                case "checkbox_select_text":
	                case "radio_select_text":
	                    subcategoryHtml += `
	                    <div class="input-wrapper">
	                        <input type="${fieldType === "checkbox_select_text" ? "checkbox" : "radio"}" 
	                               name="${fieldKey}" id="${fieldKey}" value="${subCategory.sub_col_01}" 
	                               onclick="toggleTextInput('${fieldKey}')">
	                        <label for="${fieldKey}">${subCategory.sub_col_01}</label>
	                    </div>
	                    <div class="input-wrapper">
	                        <select name="${fieldKey}_select">
	                            <option value="">선택하세요</option>`;
	                    options.forEach(option => {
	                        subcategoryHtml += `<option value="${option.option_col_01}">${option.option_col_01}</option>`;
	                    });
	                    subcategoryHtml += `</select></div>
	                    <div class="input-wrapper">
	                        <input type="text" name="${fieldKey}_details" id="${fieldKey}_details" 
	                               placeholder="입력하세요" style="">
	                    </div>`;
	                    break;
	
	                default:
	                    console.warn(`⚠️ Unknown fieldType: ${fieldType}`);
	                    break;
	            }
	
	            html += subcategoryHtml;
	        });

         html += `</div></div>`; // subcategory-list 및 category1-wrapper 종료
    });

    categoryContainer.html(html);
}

function back() {
    const templateSelectButton = $('#templateSelect');
    if (templateSelectButton.length > 0) {
        templateSelectButton.click(); // Trigger the click event
        $('.templateModal_footer').css('display', 'flex');
		$('#del_icon').css('display', 'flex');	
    } else {
        console.error('Template select button not found.');
    }
}


function templateChoice() {
	let text = '템플릿 저장 시 기존 상담 데이터와 포맷이 맞지 않을 수 있습니다. \n템플릿을 선택하시겠습니까?'
	let fontsize = '14px';
	text = text.replace(/\n/g, '<br>');
	let buttonAction = function () {
//        console.log("Template has been selected!");
		templatePost();
    };
	openPopupParameter(text, fontsize, buttonAction);
	
}

$('#closeTemplateModal').click(function() {
    templateModalClose();
});
function templatePost() {
	console.log('선택된 템플릿 : '+choiceTemplate);
	// 템플릿을 선택했으니 기존의 상담카테고리가 있다면 삭제 후 생성
	closePopup();
	spinner();
	$.ajax({
		url: 'setting/postTemplate/'+choiceTemplate,
		type: 'post',
		dataType: 'json',
		data: {
			idx : choiceTemplate
		},
		success: function(response) {
			console.log('성공', response);
			window.location.reload();
		},
		error: function(error) {
			console.error('Error :', error);
		}
	});
}

function ModifyMainCategory(event, type, id, currentName = '', inputType = '') {
    console.log(`Editing category with ID: ${id}, TYPE: ${type}`);
    let text = '';
    if(type == 'main') {
		text = '상담항목을';
	} else if (type == 'sub') {
		text = '설정값을';
	} else {
		text = 'SelectBox설정값을';
	}
    showModifyCategoryModal(type, id, `수정할 ${text} 입력해주세요.`, currentName, inputType);
}

function showModifyCategoryModal(type, id, message, currentName = '', inputType = ''){
    const modal2 = $('.modal2');
    $('.menu_msg2').text(message);
    $('#cc_col_02').val(currentName || '');
    
    $('#ModifyBtn').attr('data-type', type);
    $('#ModifyBtn').attr('data-id', id);

    const fieldLabel = $('#modal2FieldLabel');
    const metaWrap = $('#modal2MetaWrap');
    const meta = $('#modal2Meta');
    const typeNotice = $('#modal2TypeNotice');
    const context = $('#modal2Context');

    if (type === 'main') {
		$('.modal2-title').text('상담항목 수정');
		fieldLabel.text('상담항목명');
		$('#cc_col_02').attr('placeholder', '상담항목 이름 입력');
		metaWrap.hide();
    } else if (type === 'sub') {
		$('.modal2-title').text('설정값 수정');
		fieldLabel.text('설정값명');
		$('#cc_col_02').attr('placeholder', '설정값 이름 입력');
		const chips = formatInputTypeChips(inputType);
		meta.html(chips || '<span class="modal2-chip modal2-chip-muted">유형 정보 없음</span>');
		typeNotice.show();
		metaWrap.show();
    } else {
		$('.modal2-title').text('Select Box 설정값 수정');
		fieldLabel.text('Select Box 설정값');
		$('#cc_col_02').attr('placeholder', 'Select Box 설정값 입력');
		metaWrap.hide();
    }

    if (context.length > 0) {
		context.text(message);
    }
    
    modal2.addClass('show');
    $('body').css('overflow', 'hidden');
    setTimeout(() => {
		const input = document.getElementById('cc_col_02');
		if (input) {
			input.focus();
			input.select();
		}
	}, 0);
	
}
function templateModalOpen() {
	const modal = document.getElementById('templateModal');
	modal.classList.add('show');
}
function templateModalClose() {
	const modal = document.getElementById('templateModal');
	modal.classList.remove('show');
}
function openPopupParameter(text, fontsize, buttonAction) {
	var modal = document.querySelector('.modal');
	var body = document.querySelector('body');
	var modal_msg = document.querySelector('.menu_msg');
	var modal_footer = document.querySelector('.modal_footer');
	var btn = modal_footer.querySelector('.btn');
	btn.onclick = null;
	btn.onclick = buttonAction;
	
	modal_msg.innerHTML = text;
	modal_msg.style.fontSize = fontsize;
	
	modal.classList.toggle('show');
    if (modal.classList.contains('show')) {
        body.style.overflow = 'hidden';
    }
}
function openPopup() {
	var modal = document.querySelector('.modal');
	var body = document.querySelector('body');
	var modal_msg = document.querySelector('.menu_msg');
	modal_msg.textContent='';
	modal.classList.toggle('show');
    if (modal.classList.contains('show')) {
        body.style.overflow = 'hidden';
    }
}
function openPopup2() {
	var modal2 = document.querySelector('.modal2');
	var body = document.querySelector('body');
	modal2.classList.toggle('show');
    if (modal2.classList.contains('show')) {
        body.style.overflow = 'hidden';
    }
}
function openPopup3() {
	var modal3 = document.querySelector('.modal3');
	var body = document.querySelector('body');
	modal3.classList.toggle('show');
    if (modal3.classList.contains('show')) {
        body.style.overflow = 'hidden';
    }
}
function openPopup4() {
	var modal4 = document.querySelector('.modal4');
	var body = document.querySelector('body');
	modal4.classList.toggle('show');
    if (modal4.classList.contains('show')) {
        body.style.overflow = 'hidden';
    }
}
function closePopup(){
	var modal = document.querySelector('.modal');
	var body = document.querySelector('body');
	modal.classList.toggle('show');
	if (!modal.classList.contains('show')) {
		body.style.overflow = 'auto';
	}
}

function closePopup2(){
	var modal2 = document.querySelector('.modal2');
	var body = document.querySelector('body');
	modal2.classList.toggle('show');
	if (!modal2.classList.contains('show')) {
		body.style.overflow = 'auto';
	}
}

function closePopup3() {
    var modal3 = document.querySelector('.modal3');
    var body = document.querySelector('body');
    modal3.classList.toggle('show');
    if (!modal3.classList.contains('show')) {
        body.style.overflow = 'auto';
    }
}

function closePopup4() {
    var modal4 = document.querySelector('.modal4');
    var body = document.querySelector('body');
    modal4.classList.toggle('show');
    if (!modal4.classList.contains('show')) {
        body.style.overflow = 'auto';
    }
}	
// spinner
function spinner() {
	const spinner = document.getElementById('spinner-overlay');
	if (spinner) {
		spinner.classList.add('show'); // Adds CSS transition
		spinner.style.display = 'flex'; // Ensure it's visible
	}
//		$("#popup").css('display', 'flex').hide().fadeIn();
}
// 스피너를 감추는 함수
function hideSpinner() {
    const spinner = document.getElementById('spinner-overlay');
    if (spinner) {
        spinner.style.display = 'none'; // 스피너를 화면에서 감춤
        // spinner.classList.remove('show');
    }
    // $("#popup").fadeOut(); 
    // jQuery로 팝업을 닫고 싶다면 주석 해제 후 사용
}
$(document).on("click", 'input[name="subCategory"]', function() {
	if (typeof getOptions === "function") {
        console.log("✅ getOptions is already defined.");
	} else {
		window.getOptions = function(subCategoryId, element) {
			console.log("Category ID:", subCategoryId);
		    const categoryType = element.getAttribute('data-category-type');
		    console.log("Category Type:", categoryType);
			$('#optionCategoryDiv').html('');
			SubId = subCategoryId;
		    Type = categoryType;
		    
		    $.ajax({
		        url: '/csm/getOptions',
		        data: { categoryId: subCategoryId },
		        success: function(data) {
		            var html = '';
		            console.log(data);
		            if (data == '') {
						console.log('no');
						html = 'SelectBox가 아닙니다.';
						$('#new-optioncategory').prop('disabled', true);;
					}
		            $.each(data, function (index, option) {
				    // cc_col_03이 List<String>일 경우, 각 옵션을 개별 라디오 버튼으로 생성
				    if (Array.isArray(option.cc_col_03)) {
						$('#new-optioncategory').prop('disabled', false);
				        $.each(option.cc_col_03, function (i, opt) {
				            html += `
				                <label class="custom-radio" data-category-type="option">
				                    <input 
				                        type="radio" 
				                        data-category-type="option" 
				                        name="optionCategory" 
				                        value="${option.cc_col_01}" 
				                        onclick="OptionSelect(${option.cc_col_01}, this)">
				                    <span class="custom-radio-mark"></span>
				                    ${opt.trim()}
				                    <img src="/csm/icon/ev/pen-icon.png"
									width="16"
									height="16"
									fill="currentColor"
									class="bi bi-pen"
									viewBox="0 0 16 16"
									value="${option.cc_col_01}">
				                </label>
				            `;
				        });
				    } else if (typeof option.cc_col_03 === 'string') {
				        // cc_col_03이 문자열인 경우 (혹시 모를 유연성 처리)
				        var options = option.cc_col_03.split(",");
				        $.each(options, function (i, opt) {
				            html += `
				                <label class="custom-radio">
				                    <input 
				                        type="radio" 
				                        data-category-type="option" 
				                        name="optionCategory" 
				                        value="${option.cc_col_01}" 
				                        onclick="OptionSelect(${option.cc_col_01}, this)">
				                    <span class="custom-radio-mark"></span>
				                    ${opt.trim()}
				                    <img src="/csm/icon/ev/pen-icon.png"
									width="16"
									height="16"
									fill="currentColor"
									class="bi bi-pen"
									viewBox="0 0 16 16"
									value="${option.cc_col_01}">
				                </label>
				            `;
				        });
				    }
				});
		            $('#optionCategoryDiv').html(html);
					updateSelectedCategoryStyles();
		        }
		    });
			
		}
	}
})
$(document).on("click", 'input[name="mainCategory"]', function() {
    let categoryId = $(this).val();
    console.log("Category ID:", categoryId);
    
    console.log("✅ DOMContentLoaded 이벤트 발생! getSubCategories 등록됨.");
});
window.getSubCategories = function(categoryId, element) {
    console.log("✅ getSubCategories 호출됨:", categoryId, element);

    if (!element) {
        console.error("❌ Element is null or undefined.");
        return;
    }
    if (typeof getSubCategories === "function") {
        console.log("✅ getSubCategories is already defined.");
    } else {
        console.log("⚠️ getSubCategories was undefined. Defining it now...");
        
		window.getSubCategories = function(categoryId, element) {
		    console.log("getSubCategories 호출됨:", categoryId, element);
		//    console.log("Category ID:", parentId);
		    const categoryType = element.getAttribute('data-category-type');
		    console.log("Category Type:", categoryType);
		    getSubCategories = categoryType;
		    Id = categoryId;
		    Type = categoryType;
		    
			$('#subCategoryDiv').html('');
			$('#optionCategoryDiv').html('');
		    $.ajax({
		        url: '/csm/getSubcategories',
		        data: { categoryId: categoryId },
		        success: function(data) {
		            var html = '';
			            $.each(data, function (index, category) {
			            	const inputType = buildSubcategoryInputTypes(category);
					    html += `
					        <label class="custom-radio" data-category-type="sub" data-input-type="${inputType}">
					            <input 
					                type="radio" 
				                data-category-type="child" 
				                name="subCategory" 
				                value="${category.cc_col_01}" 
				                onclick="getOptions(${category.cc_col_01}, this)"
				                >
				            <span class="custom-radio-mark"></span>
				            ${category.cc_col_02}
				            <img src="/csm/icon/ev/pen-icon.png"
								width="16"
								height="16"
								fill="currentColor"
								class="bi bi-pen"
								viewBox="0 0 16 16"
								value="${category.cc_col_01}">
				        </label>
				    	`;
					});
		            $('#subCategoryDiv').html(html);
					updateSelectedCategoryStyles();
		        }
		    });
		    // 기존 getSubCategories 함수 로직 유지
		};
    }
    const categoryType = element.getAttribute('data-category-type');
    console.log("Category Type:", categoryType);

    // ✅ Clear previous subcategories
    $('#subCategoryDiv').html('');
    $('#optionCategoryDiv').html('');

    // ✅ AJAX 요청
    $.ajax({
        url: '/csm/getSubcategories',
        data: { categoryId: categoryId },
        success: function(data) {
            var html = '';
	            $.each(data, function (index, category) {
	            	const inputType = buildSubcategoryInputTypes(category);
	                html += `
	                    <label class="custom-radio" data-category-type="sub" data-input-type="${inputType}">
	                        <input type="radio"
                            data-category-type="child"
                            name="subCategory"
                            value="${category.cc_col_01}"
                            onclick="window.getOptions(${category.cc_col_01}, this)">
                        <span class="custom-radio-mark"></span>
                        ${category.cc_col_02}
                        <img src="/csm/icon/ev/pen-icon.png"
								width="16"
								height="16"
								fill="currentColor"
								class="bi bi-pen"
								viewBox="0 0 16 16"
								value="${category.cc_col_01}">
                    </label>
                `;
            });
            $('#subCategoryDiv').html(html);
			updateSelectedCategoryStyles();
        },
        error: function(xhr, status, error) {
            console.error("❌ AJAX 오류:", error);
        }
    });
};

let categoryId = "";
// ✅ Event Listener for Click on `mainCategory`
$(document).on("click", 'input[name="mainCategory"]', function() {
    let selectedcategoryId = $(this).val();
	categoryId = selectedcategoryId;
    console.log("Category ID:", categoryId);
    $('#new-maincategory').attr("data-id", categoryId);
    $('#del-maincategory').attr("data-id", categoryId);
    // ✅ Ensure `getSubCategories` exists before calling
    if (typeof window.getSubCategories === "function") {
        let clickedElement = $(this).get(0); // Convert jQuery object to DOM element
        window.getSubCategories(categoryId, clickedElement);
    } else {
        console.error("❌ getSubCategories is not defined at runtime!");
    }
});
// 돔 DOM 시작
$(document).ready(function() {

    let subcategoryDataList = []; // 소분류 데이터 리스트
    let hiddenId = ""; // 대분류 ID
	let categoryTypePost = "";
	let selectedCategoryName = '';
	let selectedInputTypes = '';
	let selectedCategory = '';
	let selectedCategoryId = '';
	let currentCategoryType = null;
	var settingMain = document.getElementById('setting-main');
	var settingSub = document.getElementById('setting-sub');
	var settingOption = document.getElementById('setting-option');
	let previousModalContent = '';
	let choiceTemplate = '';
	
	$('.menu4', $('.nav_section')).addClass('active');
	$(document).on('change', 'input[name="mainCategory"]', function() {
        var categoryId = $(this).val();
//         loadSubCategories(categoryId);
        $('#subCategoryDiv').html('');
        $('#optionCategoryDiv').html('');
        $('#new-subcategory').prop('disabled', false);
        $('#del-subcategory').prop('disabled', false);
        $('#new-optioncategory').prop('disabled', true);
        $('#del-optioncategory').prop('disabled', true);
    });


	$(document).on('change', 'input[name="subCategory"]', function() {
        var subCategoryId = $(this).val();
//         loadSubSubCategories(subCategoryId);
        $('#optionCategoryDiv').html('');
        $('#new-optioncategory').prop('disabled', false);
        $('#del-optioncategory').prop('disabled', false);
    });

	$(document).on('change', 'input[name="mainCategory"], input[name="subCategory"], input[name="optionCategory"]', function() {
		updateSelectedCategoryStyles();
	});

	document.getElementById("back-icon").addEventListener("click", back);
    settingMain.addEventListener('mouseenter', function() {
        this.src = `${ICON_BASE}/fix-icon.png`;
    });

    settingMain.addEventListener('mouseleave', function() {
        this.src = `${ICON_BASE}/fix-icon.png`;
    });
    
    settingSub.addEventListener('mouseenter', function() {
        this.src = `${ICON_BASE}/fix-icon.png`;
    });

    settingSub.addEventListener('mouseleave', function() {
        this.src = `${ICON_BASE}/fix-icon.png`;
    });
    
    settingOption.addEventListener('mouseenter', function() {
        this.src = `${ICON_BASE}/fix-icon.png`;
    });

    settingOption.addEventListener('mouseleave', function() {
        this.src = `${ICON_BASE}/fix-icon.png`;
    });
	document.querySelectorAll('.selectable-cell').forEach(cell => {
	    cell.addEventListener('click', function() {
	        const categoryType = this.dataset.categoryType;
	        const categoryId = this.dataset.categoryId;
	        const parentId = this.dataset.parentId; // 소분류에만 존재
	        const inputTypes = this.dataset.inputType;
	        selectedCategoryId = categoryId;
	
			// 선택된 카테고리 이름 설정
	        selectedCategoryName = this.textContent.trim();
	        
	        // 선택된 입력 타입 설정
	        selectedInputTypes = inputTypes;
	        
	        if (categoryType === 'parent') {
	            console.log('대분류 선택:', categoryId);
	            // 대분류 처리 로직
	            categoryTypePost = categoryType;
	            hiddenId = categoryId;
	        } else if (categoryType === 'child') {
	            console.log('소분류 선택:', categoryId, '부모:', parentId, '입력 타입: ', inputTypes);
	            // 소분류 처리 로직
	            categoryTypePost = categoryType;
	            hiddenId = categoryId;
	        }
	
	        // 선택된 셀 스타일 변경
	        document.querySelectorAll('.selectable-cell').forEach(c => c.classList.remove('selected-cell'));
	        this.classList.add('selected-cell');
	        console.log("카테고리 타입: "+categoryTypePost);
            console.log("hiddenId set to:", hiddenId);
            console.log(selectedCategoryName);
	    });
	});
	// 전역에서 접근 가능하도록 설정
	window.showAddCategoryModal = showAddCategoryModal;
	window.showDeleteConfirmation = showDeleteConfirmation;
	window.deleteCategory = deleteCategory;

	function initializeOrderEdit() {
		console.log('initializeOrderEdit 실행됨');
	    initSortable('mainCategoryDiv', 'main');
	    initSortable('subCategoryDiv', 'sub');
	    initSortable('optionCategoryDiv', 'option');

		['main', 'sub', 'option'].forEach((type) => {
			const settingIcon = document.getElementById(`setting-${type}`);
			const settingWrapper = settingIcon ? settingIcon.closest('.settingDiv') : null;
			if (settingWrapper) {
				settingWrapper.style.display = 'none';
			}
		});

		updateSelectedCategoryStyles();
	}

	function initSortable(containerId, type) {
	    const container = document.getElementById(containerId);
	    if (!container) return;
	    const existing = Sortable.get(container);
	    if (existing) {
			existing.destroy();
		}
		new Sortable(container, {
			animation: 150,
			draggable: '.custom-radio',
			handle: '.custom-radio-mark',
			disabled: false,
			ghostClass: 'drag-ghost',
			chosenClass: 'drag-chosen',
			onEnd: function(evt) {
				if (evt && evt.oldIndex === evt.newIndex) return;
				scheduleAutoSaveOrder(type);
			}
		});
	}

	function scheduleAutoSaveOrder(type) {
		if (orderSaveTimers[type]) {
			clearTimeout(orderSaveTimers[type]);
		}
		orderSaveTimers[type] = setTimeout(() => {
			window.saveOrder(type, { auto: true });
		}, 200);
	}
	
	// ✅ 빈 함수로 미리 정의하여 오류 방지
	window.getOptions = function() {
	    console.warn("⚠️ getOptions is called before definition!");
	};
	// ✅ SubCategory 클릭 이벤트 (자동 호출 보장)
	$(document).off("click", 'input[name="subCategory"]').on("click", 'input[name="subCategory"]', function () {
	    let categoryId = $(this).val();
	    console.log("✅ SubCategory 클릭됨:", categoryId);
	
	    if (typeof window.getOptions === "function") {
	        window.getOptions(categoryId, this);
	    } else {
	        console.error("getOptions is not defined");
	    }
	});
	// ✅ Define `getOptions` Properly Before It’s Used
    window.getOptions = function(subCategoryId, element) {
        console.log("🟢 getOptions 실행됨:", subCategoryId, element);

        const categoryType = element.getAttribute('data-category-type');
        console.log("Category Type:", categoryType);

        $('#optionCategoryDiv').html('');
        SubId = subCategoryId;
        Type = categoryType;

        // ✅ Update button data-id
        $('#new-subcategory').attr("data-id", subCategoryId);
        $('#del-subcategory').attr("data-id", subCategoryId);
		$('#new-optioncategory').attr("data-id", subCategoryId);
        // ✅ Perform AJAX request
        $.ajax({
            url: '/csm/getOptions',
            data: { categoryId: subCategoryId },
            success: function(data) {
                var html = '';
                console.log("🔹 Received Data:", data);

                if (!data || data.length === 0) {
                    console.log("SelectBox 설정값 데이터가 없습니다.");
                    html = '<p>SelecrBox 설정값이 아닙니다.</p>';
                    $('#new-optioncategory').prop('disabled', true);
                } else {
                    $('#new-optioncategory').prop('disabled', false);

                    $.each(data, function(index, option) {
                        let optionsArray = Array.isArray(option.cc_col_03) 
                            ? option.cc_col_03 
                            : option.cc_col_03.split(",");

                        $.each(optionsArray, function(i, opt) {
                            html += `
                                <label class="custom-radio" data-category-type="option">
                                    <input 
                                        type="radio" 
                                        data-category-type="option" 
                                        name="optionCategory" 
                                        value="${option.cc_col_01}" 
                                        onclick="OptionSelect(${option.cc_col_01}, this)">
                                    <span class="custom-radio-mark"></span>
                                    ${opt.trim()}
                                    <img src="/csm/icon/ev/pen-icon.png"
											width="16"
											height="16"
											fill="currentColor"
											class="bi bi-pen"
											viewBox="0 0 16 16"
											value="${option.cc_col_01}">
                                </label>`;
                        });
                    });
                }

                $('#optionCategoryDiv').html(html);
				updateSelectedCategoryStyles();
            }
        });
    };

    // ✅ Event Listener for SubCategory Clicks
    $(document).on("click", 'input[name="subCategory"]', function() {
        let subCategoryId = $(this).val();
        let clickedElement = $(this).get(0);
        
        console.log("Category ID:", subCategoryId);

        // ✅ No need to check or redefine `getOptions`, it's already globally defined
        console.log("🚀 Calling getOptions:", subCategoryId);
        window.getOptions(subCategoryId, clickedElement);
    });
	window.saveOrder = function (type, options = {}) {
		const auto = options.auto === true;
		const originalType = type;
	    let categoryId = null;
	    let subcategoryId = null;
	    let containerId;

	    // Map incorrect type values to the correct ones and set the original container ID
	    switch (type) {
	        case 'main':
	            containerId = 'mainCategoryDiv';
	            type = 'parent';
	            break;
	        case 'sub':
	            containerId = 'subCategoryDiv';
	            type = 'child';
	            break;
	        case 'option':
	            containerId = 'optionCategoryDiv';
	            type = 'select';
	            break;
	        default:
	            console.error(`Unknown type: ${type}`);
	            return;
	    }
	
	    // Get the selected main category ID for 'child' and 'select' types
	    if (type === 'child' || type === 'select') {
	        const checkedMainCategory = document.querySelector('#mainCategoryDiv input[name="mainCategory"]:checked');
	        if (checkedMainCategory) {
	            categoryId = checkedMainCategory.value; // Main category ID
	        } else {
	        	if (!auto) {
		            alert('Please select a main category.');
	        	}
	            return;
	        }
	    }
	
	    // Get the selected subcategory ID for 'select' type
	    if (type === 'select') {
	        const checkedSubCategory = document.querySelector('#subCategoryDiv input[name="subCategory"]:checked');
	        if (checkedSubCategory) {
	            subcategoryId = checkedSubCategory.value; // Subcategory ID
	        } else {
	        	if (!auto) {
		            alert('Please select a subcategory.');
	        	}
	            return;
	        }
	    }
	
	    // Get container based on the original ID
	    const container = document.getElementById(containerId);
	    if (!container) {
	        console.error(`Container with ID ${containerId} not found.`);
	        return;
	    }
	
	    // Collect order data for the specified type
	    const newOrder = Array.from(container.querySelectorAll('.custom-radio')).map((item, index) => {
	        const inputValue = item.querySelector('input').value;
	
	        // For 'select' type, inputValue represents the Option ID
	        // For 'parent' and 'child' types, inputValue represents Category or Subcategory ID
	        return {
	            categoryId: type === 'parent' ? inputValue : categoryId,
	            subcategoryId: type === 'child' ? inputValue : (type === 'select' ? subcategoryId : null),
	            itemId: type === 'select' ? inputValue : null, // Option ID for 'select' type
	            order: index + 1,
	            type
	        };
	    });
	
	    console.log(newOrder); // For debugging
	    if (!auto) {
			spinner();
	    }
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
        const headers = {};
        if (csrfToken) {
            headers[csrfHeader] = csrfToken;
        }
	    // Send data to the server
	    $.ajax({
		    url: '/csm/setting/saveCategoryOrder',
		    type: 'POST',
		    contentType: 'application/json',
            headers: headers,
		    data: JSON.stringify(newOrder),
		    success: function (data) {
		    	if (auto) {
			        console.log(`${originalType} order auto-saved successfully:`, data);
		    	} else {
			        console.log(`${type} order saved successfully:`, data);
		        	window.location.reload();
		    	}
		    },
		    error: function (xhr, status, error) {
		        console.error(`Error saving ${type} order:`, xhr.responseText || error);
		    },
		    complete: function () {
		    	if (!auto) {
					hideSpinner();
		    	}
		    }
		});
		// 템플릿 체크박스
		const templateOptions = document.querySelectorAll('.template-option input');
	
	    templateOptions.forEach(option => {
	        option.addEventListener('change', function () {
	            // Remove the selected class from all options
	            document.querySelectorAll('.template-option').forEach(item => item.classList.remove('selected'));
	
	            // Add the selected class to the parent label of the selected radio
	            if (this.checked) {
	                this.closest('.template-option').classList.add('selected');
	            }
	        });
	    });
	};

	initializeOrderEdit();
	
	document.getElementById("addbtn").addEventListener("click", addCategory);
	
	
	$(document).on('click', '[data-action]', function(e) {
	    e.preventDefault();
	    var action = $(this).data('action');
	    
	    switch(action) {
	        case 'delete-category':
	            deleteCategoryHandler();
	            break;
	        case 'modify-category-farent':
				modifyCategoryHandlerParent();
				break;
	        case 'modify-category-child':
				modifyCategoryHandlerChild();
				break;
	        case 'add-category':
	            addCategoryHandler();
	            break;
	        case 'add-subcategory':
	            SaveSubCategoryHandler();
	            break;
            case 'category-turn-modify':
				categoryTurnHandler();
				break;
	    }
	});
	// 분류 추가 모달 열기 함수
	function showAddCategoryModal(element) {
		currentCategoryType = element.getAttribute("data-category-type");
	    var modal4 = document.querySelector('.modal4');
	    var body = document.querySelector('body');
	    const menuMsg4 = document.querySelector(".menu_msg4");
	    const modal4FieldLabel = document.querySelector("#modal4FieldLabel");
	    const checkboxOptionsDiv = document.querySelector("#selectOptions");
	    const checkboxContainer = document.querySelector("#checkboxContainer");
	    const CategoryInput = document.querySelector("#addCategoryName");
	    const OptionInput = document.querySelector('#selectboxOptions');
	    const inputTargetId = currentCategoryType === "option" ? "selectboxOptions" : "addCategoryName";
	    if (modal4FieldLabel) {
	        modal4FieldLabel.setAttribute('for', inputTargetId);
	    }
	    // 모달 메시지 설정
    	menuMsg4.innerText = "새로운 " + (currentCategoryType === "parent" ? "상담항목" : currentCategoryType === "child" ? "설정값" : "Select Box 설정값") + "을 입력해주세요.";
		CategoryInput.value = '';
		OptionInput.value = '';
	    // Show/Hide elements based on category type
	    if (currentCategoryType === "parent") {
	        if (modal4FieldLabel) modal4FieldLabel.innerText = "상담항목명";
	        if (CategoryInput) CategoryInput.placeholder = "분류 이름 입력";
	        if (checkboxContainer) checkboxContainer.style.display = "none";
	        if (checkboxOptionsDiv) checkboxOptionsDiv.style.display = "none";
	        if (CategoryInput) CategoryInput.style.display = "inline-block"; 
	    } else if (currentCategoryType === "child") {
	        if (modal4FieldLabel) modal4FieldLabel.innerText = "설정값명";
	        if (CategoryInput) CategoryInput.placeholder = "설정값 이름 입력";
	        if (checkboxContainer) checkboxContainer.style.display = "flex";
	        if (checkboxOptionsDiv) checkboxOptionsDiv.style.display = "none";
	        if (CategoryInput) CategoryInput.style.display = "inline-block"; 
	    } else if (currentCategoryType === "option") {
	        if (modal4FieldLabel) modal4FieldLabel.innerText = "Select Box 설정값";
	        if (OptionInput) OptionInput.placeholder = "옵션 입력 (콤마로 구분)";
	        if (checkboxContainer) checkboxContainer.style.display = "none";  // Hide checkboxes for options
	        if (checkboxOptionsDiv) checkboxOptionsDiv.style.display = "block"; // Show options input
	        if (CategoryInput) CategoryInput.style.display = "none"; 
	    }

	
	    // Show modal
	    modal4.classList.toggle('show');
	    if (modal4.classList.contains('show')) {
	        body.style.overflow = 'hidden';
	    }
	}
	
	
	// 대분류 추가 API (ajax)
//	function addCategoryHandler() {
//		spinner();
//		$.ajax({
//            url: 'csm/setting/category1',
//            type: 'post',
//            dataType: 'json',
//            data: {
//                'cc_col_02': $('#cc_col_02').val(),
//            },
//            success: function(response) {
//                console.log('Success:', response);
//            	$(".modal4").removeClass("show"); // 모달 창 닫기
//                window.location.reload();
//            },
//            error: function(error) {
//                console.log('Error:', error);
//            }
//        });
//	}
	// 카테고리 수정 API
	$('#modify-category').click(function() {
			console.log("modify - click");
			if(hiddenId == '') {
				alert('상담항목 혹은 설정값를 선택해 주세요.');
				return;
			}
			$("#popup").css('display', 'flex').hide().fadeIn();
		if (categoryTypePost == 'parent'){
			$('.head-title').text('상담항목 수정');
			$('.category1').addClass('off');
	        $('.category2').addClass('off');
	        $('.category3').addClass('off');
        	$('.category4').addClass('off');
        	$('.category5').removeClass('off');
        	$('.switch_content').addClass('off');
	        $('.message').addClass('off');
	        
			// 대분류 수정 UI
	        let formHtml = `
	                <span class="content5">상담항목명 :</span>
	                <input type="text" id="cc_col_02" name="cc_col_02" value="${selectedCategoryName}">
	        `;
	        $('#ModifyContainer').html(formHtml);
	        
	        // 저장 버튼 변경
	        $('.confirm').attr('data-action', 'modify-category-farent').text('Modify');
		} else {
			$('.head-title').text('설정값 수정');
	        $('.category1').addClass('off');
	        $('.category2').addClass('off');
	        $('.category3').addClass('off');
        	$('.category4').addClass('off');
        	$('.category5').removeClass('off');
        	$('.switch_content').addClass('off');
	        $('.message').addClass('off');
	        
	        // 소분류 수정 UI
	        let formHtml = `
	            <span class="content5 category1name">설정값명 :</span>
	            <input type="text" id="subcategoryName" value="${selectedCategoryName}" placeholder="설정값 입력"><br>
	        `;
	        
	        // 입력 타입에 따른 체크박스 생성
	        const inputTypes = selectedInputTypes.split('');
	        inputTypes.forEach(type => {
	            switch(type) {
	                case 'checkbox':
	                    formHtml += '<input type="checkbox" id="subcategory-checkbox" checked> 체크박스<br>';
	                    break;
	                case 'radio':
	                    formHtml += '<input type="checkbox" id="subcategory-radio" checked> 라디오 버튼<br>';
	                    break;
	                case 'text':
	                    formHtml += '<input type="checkbox" id="subcategory-text" checked> 텍스트 입력<br>';
	                    break;
	                case 'select':
	                    formHtml += '<input type="checkbox" id="subcategory-select" checked> 셀렉트 박스<br>';
	                    formHtml += '<textarea id="subcategory-options" placeholder="옵션을 쉼표로 구분하여 입력"></textarea><br>';
	                    break;
	            }
	        });
	
	        $('#ModifyContainer').html(formHtml);
	        
	        // 저장 버튼 변경
	        $('.confirm').attr('data-action', 'modify-category-child').text('Modify');
		}
	});
	function modifyCategoryHandlerParent() {
		const updatedData = {
	        cc_col_01: selectedCategoryId,
	        cc_col_02: $('#cc_col_02').val(),
	        type: categoryTypePost
	    };
	
		spinner();
	    $.ajax({
	        url: '/csm/setting/categoryModify',
	        type: 'POST',
	        data: JSON.stringify(updatedData),
	        contentType: 'application/json',
	        success: function(response) {
	            console.log('대분류 업데이트 성공:', response);
	            $("#popup").fadeOut();
	            // 필요한 경우 페이지 새로고침 또는 UI 업데이트
	            window.location.reload();
	        },
	        error: function(error) {
	            console.error('대분류 업데이트 실패:', error);
	        }
	    });
	}
	function modifyCategoryHandlerChild() {
		const updatedData = {
	        cc_col_01: categoryId,
	        cc_col_02: $('#subcategoryName').val(),
//	        cc_col_04: $('#subcategory-checkbox').is(':checked'),
//	        cc_col_05: $('#subcategory-radio').is(':checked'),
//	        cc_col_06: $('#subcategory-text').is(':checked'),
//	        cc_col_07: $('#subcategory-select').is(':checked'),
//	        options: $('#subcategory-options').val(),
	        type: categoryTypePost
	    };
		spinner();
	    $.ajax({
	        url: '/csm/setting/categoryModify',
	        type: 'POST',
	        data: JSON.stringify(updatedData),
	        contentType: 'application/json',
	        success: function(response) {
	            console.log('소분류 업데이트 성공:', response);
//	            $("#popup").fadeOut();
	            // 필요한 경우 페이지 새로고침 또는 UI 업데이트
	            window.location.reload();
	        },
	        error: function(error) {
	            console.error('소분류 업데이트 실패:', error);
	        }
	    });
	}
	
	// 선택삭제 모달창 오픈
	function showDeleteConfirmation(element) {
	    var modal3 = document.querySelector('.modal3');
	    var body = document.querySelector('body');
	    var menuMsg3 = document.querySelector('.menu_msg3');
	    
	    // data-category-type과 value를 가져와 설정
	    categoryTypePost = element.getAttribute('data-category-type'); // 예: 'parent'
	    hiddenId = element.getAttribute('data-id'); // 선택된 카테고리의 ID 값
	    
	    // 메시지 설정
		// categoryTypePost 값에 따른 메시지 설정
	    if (categoryTypePost === 'parent') {
	        menuMsg3.innerText = "선택하신 상담항목를 삭제하시겠습니까?\n하위 설정값까지 모두 삭제됩니다.";
	    } else if (categoryTypePost === 'child') {
	        menuMsg3.innerText = "선택하신 설정값를 삭제하시겠습니까?";
	    } else if (categoryTypePost === 'option') {
	        menuMsg3.innerText = "선택하신 Select Box 설정값을 삭제하시겠습니까?";
	    }
	    modal3.classList.toggle('show');
	    if (modal3.classList.contains('show')) {
	        body.style.overflow = 'hidden';
	    }
	}

	// 카테고리 삭제 API (ajax)
	function deleteCategory() {
        spinner();
        let deleteId; // ID to send in the delete request

	    // Set deleteId based on category type
	    if (categoryTypePost === 'parent') {
	        deleteId = hiddenId;
	    } else if (categoryTypePost === 'child') {
	        deleteId = hiddenId;
	    } else if (categoryTypePost === 'option') {
	        deleteId = OptionId;
	    }
	    console.log("hiddenvalue "+hiddenId);
    	console.log("카테고리 타입: ", categoryTypePost, "카테고리 ID: ", deleteId);
	    
        $.ajax({
            url: '/csm/setting/categoryDelete',
            type: 'POST',
			contentType: "application/json",
            data: JSON.stringify({
                'id': parseInt(deleteId),
                'type': categoryTypePost,
            }),
            success: function(response) {
                console.log('Success:', response);
                console.log('삭제 성공:', response);
            	$(".modal3").removeClass("show"); // 모달 창 닫기
                window.location.reload();
            },
            error: function(error) {
                console.log('Error:', error);
            }
        });
    }
    
    // 대분류가 선택되었을 때 hiddenId 설정 (예시)
    $('#new-category2').click(function() {
        console.log("new-category2 clicked, hiddenId set to:", hiddenId); // 확인
        // 대분류가 선택되지 않았다면 실행X
        if(hiddenId == '') {
			alert('상담항목을 선택해 주세요.');
			return;
		}
        $('.category1name').text(selectedCategoryName + '에 설정값을 추가합니다.');
        $('.category2').removeClass('off');
        $('.category1').addClass('off');
        $('.category3').addClass('off');
        $('.category4').addClass('off');
        $('.head-title').text('설정값 추가');
        $('#newElementsContainer').empty();	
        $("#popup").css('display', 'flex').hide().fadeIn();
        $(".confirm").attr('data-action', 'add-subcategory').text('SAVE');
    });

    // 소분류 입력 필드 추가 버튼 클릭 시
    $('#addSubcategoryEntry').click(function() {
        let subcategoryCount = $('.input-group').length + 1;
        let newEntry = `
            <div class="input-group mb-3">
                <input type="text" name="subcategoryName" placeholder="설정값 이름 입력" required>
                <!-- 입력 타입 라디오버튼 (radio와 checkbox 배타적으로 선택) -->
                <input type="radio" id="radio-${subcategoryCount}" name="inputType-${subcategoryCount}" value="radio">
                <label for="radio-${subcategoryCount}">라디오박스</label>
                <input type="radio" id="checkbox-${subcategoryCount}" name="inputType-${subcategoryCount}" value="checkbox">
                <label for="checkbox-${subcategoryCount}">체크박스</label>
                <!-- 텍스트박스와 셀렉트박스는 체크박스로 설정 -->
                <input type="checkbox" id="textbox-${subcategoryCount}" name="inputType-textbox-${subcategoryCount}" value="textbox">
                <label for="textbox-${subcategoryCount}">텍스트박스</label>
                <input type="checkbox" id="selectbox-${subcategoryCount}" name="inputType-selectbox-${subcategoryCount}" value="selectbox">
                <label for="selectbox-${subcategoryCount}">셀렉트박스</label>
                <!-- 옵션 입력 -->
                <div class="options-container" style="display:none;">
                    <input type="text" class="optionInput" placeholder="Select Box 설정값 입력">
                    <button type="button" class="addOption">Select Box 설정값 추가</button>
                    <ul class="optionsList"></ul>
                </div>
                <!-- 제거 버튼 -->
                <button type="button" class="remove-element">제거</button>
            </div>
        `;
        $('#newElementsContainer').append(newEntry);
    });

    // 옵션 추가 버튼 클릭 시 (이벤트 위임)
    $(document).on('click', '.addOption', function() {
        let option = $(this).siblings('.optionInput').val().trim();
        if (option) {
            $(this).siblings('.optionsList').append('<li>' + option + '</li>');
            $(this).siblings('.optionInput').val('');
        }
    });

    // 입력 타입 변경 시 옵션 입력란 표시/숨김 및 배타성 보장
    $(document).on('change', 'input[type="radio"], input[type="checkbox"]', function() {
        let parentGroup = $(this).closest('.input-group');

        // 셀렉트박스가 선택된 경우 옵션 입력란 표시
        if ($(this).attr('value') === 'selectbox' && $(this).is(':checked')) {
            parentGroup.find('.options-container').show();
        } else if ($(this).attr('value') === 'selectbox') {
            parentGroup.find('.options-container').hide();
            parentGroup.find('.optionsList').empty();
        }

        // 라디오박스 선택 시 텍스트박스 선택 해제
        if ($(this).attr('value') === 'radio' && $(this).is(':checked')) {
            parentGroup.find('input[type="checkbox"][value="textbox"]').prop('checked', false);
        }

        // 체크박스 선택 시 라디오박스 선택 해제
        if ($(this).attr('value') === 'checkbox' && $(this).is(':checked')) {
            parentGroup.find('input[type="radio"][value="radio"]').prop('checked', false);
        }
    });

    // 소분류 제거 버튼 클릭 시
    $(document).on('click', '.remove-element', function() {
        $(this).closest('.input-group').remove();
    });

    // 소분류 생성 버튼 클릭 시
	function SaveSubCategoryHandler() {
		if (!hiddenId || hiddenId <= 0) {
			console.log(hiddenId);
	        alert("먼저 상담항목을 선택해주세요.");
	        return;
	    }
			subcategoryDataList = []; // 리스트 초기화
	    $('.input-group').each(function() {
	        let subcategoryName = $(this).find('input[name="subcategoryName"]').val().trim();
	        let inputTypeRadio = $(this).find('input[type="radio"]:checked').val();
	        let isRadio = inputTypeRadio === 'radio' ? 1 : 0; // Convert to 1/0
	        let isCheckbox = inputTypeRadio === 'checkbox' ? 1 : 0; // Convert to 1/0
	        let isTextbox = $(this).find('input[type="checkbox"][value="textbox"]').is(':checked') ? 1 : 0; // Convert to 1/0
	        let isSelectbox = $(this).find('input[type="checkbox"][value="selectbox"]').is(':checked') ? 1 : 0; // Convert to 1/0
	
	        let options = [];
	        if (isSelectbox === 1) {
	            $(this).find('.optionsList li').each(function() {
	                let optionText = $(this).text().trim();
	                if (optionText !== "") {
	                    options.push(optionText); // 빈 옵션 제외
	                }
	            });
	        }
	
	        // 유효성 검사
	        if (!subcategoryName) {
	            alert("설정값 이름을 입력해주세요.");
	            return false; // 반복문 중단
	        }
	        // 라디오와 텍스트박스는 동시에 선택할 수 없도록
	        if (isRadio === 1 && isTextbox === 1) {
	            alert("설정값 '" + subcategoryName + "'에서 라디오박스와 텍스트박스는 동시에 선택할 수 없습니다.");
	            return false;
	        }
	
	        // 셀렉트박스 선택 시 옵션 필수
	        if (isSelectbox === 1 && options.length === 0) {
	            alert("설정값 '" + subcategoryName + "'에서 셀렉트박스를 선택한 경우 옵션을 최소 하나 이상 입력해야 합니다.");
	            return false;
	        }
	
	        let subcategoryData = {
	            name: subcategoryName,
	            hiddenid: hiddenId, // 대분류 ID는 별도로 설정 필요
	            checkbox: isCheckbox,  // Convert to 1/0
	            radio: isRadio,        // Convert to 1/0
	            textbox: isTextbox,    // Convert to 1/0
	            selectbox: isSelectbox, // Convert to 1/0
	            options: options
	        };
	        subcategoryDataList.push(subcategoryData);
	    });
	
	    console.log("subcategoryDataList length:", subcategoryDataList.length);
	    console.log("subcategoryDataList:", subcategoryDataList);
        // 소분류 데이터가 비어있지 않은 경우 AJAX 요청
        
        if (subcategoryDataList.length > 0) {
			spinner();
            $.ajax({
                url: '/csm/setting/category2',
                type: 'POST',
                data: JSON.stringify(subcategoryDataList),
                contentType: 'application/json',
                success: function(response) {
                    console.log('소분류 추가 성공:', response);
                    console.log('소분류 추가 성공:', subcategoryDataList);
                    $("#popup").fadeOut();
                    window.location.reload();
                },
                error: function(error) {
                    console.log('소분류 추가 실패:', error);
                    alert("소분류 추가에 실패했습니다.");
                }
            });

            // 입력 필드 초기화
            $('#newElementsContainer').empty();
        } else {
            alert("추가할 소분류 데이터가 없습니다.");
        }
	}
   
		
		
        
	
	// 카테고리 순서 편집 API
	$('#category-turn').click(function() {
		console.log('카테고리 순서 편집');
		
        $("#popup").css('display', 'flex').hide().fadeIn();
        updateModalContentBaseOnSwitch();
        $('.switch_content').removeClass('off');
        
        $('.confirm').attr('data-action', 'category-turn-modify').text('순서저장');
        
	});
	
	$('#switch').change(function() {
		updateModalContentBaseOnSwitch();
	});
	
	function updateModalContentBaseOnSwitch() {
		const isSubcategory = $('#switch').is(':checked');
		console.log('Switch checked (소분류 편집): ', isSubcategory);
        
         if (isSubcategory) {
	        // If switch is checked, handle subcategory ordering
	        $('.head-title').text('설정값 순서 편집');
	        $('.category1').addClass('off'); // Hide main category
	        $('.category3').addClass('off'); // Hide main category sorting
	        $('.category4').removeClass('off'); // Show subcategory section
       	 	$('.switch_content').removeClass('off');
			$('#mainCategorySelection').removeClass('off');
			
			loadMajorCategories();
	    } else {
	        // If switch is not checked, handle main category ordering
	        $('.head-title').text('상담항목 순서 편집');
	        $('.category1').addClass('off'); // Hide main category input
	        $('.category2').addClass('off'); // Hide subcategory input
	        $('.category3').removeClass('off'); // Show main category sorting
	        $('.category4').addClass('off');	// Hide subcategory section
	        
	        // Initialize sortable for main category list
	        const categoryList = document.getElementById('categoryList');
	        if (categoryList) {
	            new Sortable(categoryList, {
	                animation: 150,
	                onEnd: function(evt) {
	                    console.log("대분류 순서 재정렬됨.", evt);
	                }
	            });
	        }
	    }
	}
	
	function loadMajorCategories() {
		fetch('getMajorCategories')
			.then(response => {
	            if (!response.ok) {
	                throw new Error('Network response was not ok');
	            }
	            return response.json(); // Parse JSON from the response
	        })
			.then(data => {
				if (!data || data.length === 0) {
	                console.error('No major categories received');
	                return;
	            }
				const majorCategoryDropdown = document.getElementById('mainCategorySelection');
            majorCategoryDropdown.innerHTML = ''; // Clear the existing options
            
            // Loop through each category and add it to the dropdown
            const optionchecked = document.createElement('option');
            optionchecked.value = '';
            optionchecked.textContent = '선택하세요';
            optionchecked.disabled = true;  // Disable the option initially
            optionchecked.selected = true;  // Set as selected
            majorCategoryDropdown.appendChild(optionchecked);
            
            data.forEach(function(category) {
                const option = document.createElement('option');
                option.value = category.cc_col_01; // Use cc_col_01 for ID
                option.textContent = category.cc_col_02; // Use cc_col_02 for name
                majorCategoryDropdown.appendChild(option);
            });

            // Add event listener for selecting the major category
            majorCategoryDropdown.addEventListener('change', function() {
//                const selectedCategoryId = this.value;
//                loadSubcategories(selectedCategoryId);
				if (this.value !== '') {
                    // Disable "Select" option after any other selection
                    optionchecked.disabled = true;
                }
                // Load subcategories based on the selected category
                loadSubcategories(this.value);
            });
        })
        .catch(error => {
            console.error('Error fetching major categories:', error);
        });
	}
	
	function loadSubcategories(majorCategoryId) {
	    $.ajax({
	        url: `/csm/getSubcategories?categoryId=${majorCategoryId}`,
	        type: 'GET',
	        dataType: 'json',
	        success: function (data) {
	            const subcategoryList = $('#subcategoryList');
	            subcategoryList.empty(); // Clear the existing subcategories
	            
	            // Loop through each subcategory and add it to the list
	            data.forEach(function (subcategory) {
	                const li = $(`
	                    <li class="sortable-subcategory-item" 
	                        data-category-id="${subcategory.cc_col_03}" 
	                        data-subcategory-id="${subcategory.cc_col_01}" 
	                        data-category-type="child">
	                        ${subcategory.cc_col_02}
	                        <img src="/csm/icon/ev/pen-icon.png"
								width="16"
								height="16"
								fill="currentColor"
								class="bi bi-pen"
								viewBox="0 0 16 16"
								value="${subcategory.idx}">
	                    </li>
	                `);
	                subcategoryList.append(li);
	            });
	
	            // Initialize sortable for the subcategory list
	            new Sortable(document.getElementById('subcategoryList'), {
	                animation: 150,
	                onEnd: function (evt) {
	                    console.log("소분류 순서 재정렬됨.", evt);
	                }
	            });
	        },
	        error: function (error) {
	            console.error('Failed to load subcategories:', error);
	        }
	    });
	}
	function categoryTurnHandler() {
		console.log('categoryTurnHandler called');  // 로그 추가
    	const isSubcategory = $('#switch').is(':checked'); // 스위치가 체크되었는지 확인 (소분류 모드)
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
    
	    let newOrder;
	
	    if (isSubcategory) {
	        // 소분류 순서 변경
	        newOrder = Array.from(document.querySelectorAll('.sortable-subcategory-item')).map(function(item, index) {
	            const categoryId = item.getAttribute('data-category-id');
	            const categoryType = item.getAttribute('data-category-type');
	            const subcategoryId = item.getAttribute('data-subcategory-id');
	            
	            return {
	                subcategoryId: subcategoryId,
	                categoryId: categoryId,
	                type: categoryType,
	                order: index + 1
	            };
	        });
			spinner();
	        // 소분류 순서 저장 API 호출
	        fetch('/csm/setting/saveCategoryOrder', {
	            method: 'POST',
	            headers: {
	                'Content-Type': 'application/json',
                    [csrfHeader]: csrfToken
	            },
	            body: JSON.stringify(newOrder)
	        }).then(response => response.json())
	            .then(data => {
            		$("#popup").fadeOut();
	                
	                console.log('Saved subcategory order: ', data);
	                
//    				window.location.reload();
	            });
	
	    } else {
	        // 대분류 순서 변경
	        newOrder = Array.from(document.querySelectorAll('.sortable-item')).map(function(item, index) {
	            const categoryId = item.getAttribute('data-category-id');
	            const categoryType = item.getAttribute('data-category-type'); // "parent", "child", or "select"
	
	            return {
	                categoryId: categoryId,
	                type: categoryType,  // Type to distinguish between parent, child, and select box
	                order: index + 1
	            };
	        });
	
            spinner();
	        // 대분류 순서 저장 API 호출
	        fetch('/csm/setting/saveCategoryOrder', {
	            method: 'POST',
	            headers: {
	                'Content-Type': 'application/json',
                    [csrfHeader]: csrfToken
	            },
	            body: JSON.stringify(newOrder)
	        }).then(response => {
		        if (!response.ok) {
		            throw new Error('Network response was not ok');
		        }
		        return response.text();  // response.json() 대신 text() 사용
		    })
            .then(data => {
            	$("#popup").fadeOut();
            	
                console.log("Saved category order:", data);
                
//    			window.location.reload();
                
            });
	    }
	}
	// 대분류 순서 편집용 Sortable 적용
    const categoryList = document.getElementById('categoryList');
    if (categoryList) {
        new Sortable(categoryList, {
            animation: 150,
            onEnd: function(evt) {
				console.log("Category reordered from", evt.oldIndex, "to", evt.newIndex);
            
	            // 서버에 순서 저장
	            const newOrder = Array.from(categoryList.querySelectorAll('.sortable-item')).map(function(item, index) {
	                return {
	                    categoryId: item.getAttribute('data-category-id'),
	                    order: index + 1
	                };
	            });
	            console.log(newOrder);
            }
        });
    }

    // 소분류 순서 편집용 Sortable 적용
    const subcategoryList = document.getElementById('subcategoryList');
    if (subcategoryList) {
        new Sortable(subcategoryList, {
            animation: 150,
            onEnd: function(evt) {
                console.log("Subcategory reordered");
            }
        });
    }
    
    
    
    
    // 대분류 클릭 시 소분류 가져오기
    document.querySelectorAll('.sortable-item').forEach(function(categoryItem) {
        categoryItem.addEventListener('click', function() {
            const categoryId = categoryItem.getAttribute('data-category-id');
            
            // AJAX를 통해 해당 대분류의 소분류를 가져오는 로직
            fetch(`/csm/getSubcategories?categoryId=${categoryId}`)
                .then(response => response.json())
                .then(data => {
                    const subcategoryList = document.getElementById('subCategoryDiv');
                    subcategoryList.innerHTML = '';
					var html = '';
                    data.subcategories.forEach(function (subcategory) {
					    html += `
					        <label class="custom-radio" data-category-type="sub">
					            <input 
					                type="radio" 
					                name="subCategory" 
					                value="${subcategory.id}" 
					                onclick="loadSubSubCategories(${category.id})">
					            <span class="custom-radio-mark"></span>
					            ${subcategory.name}
					            <img src="/csm/icon/ev/pen-icon.png"
								width="16"
								height="16"
								fill="currentColor"
								class="bi bi-pen"
								viewBox="0 0 16 16"
								value="${subcategory.idx}">
								  
					        </label><br>
					    `;
					});
		            $('#subCategoryDiv').html(html);
					updateSelectedCategoryStyles();
                    // 소분류 편집 모달 열기
                    document.getElementById('subcategory-popup').style.display = 'block';
                });
        });
    });

//    // 대분류 순서 저장 버튼
//    document.getElementById('save-category-order').addEventListener('click', function() {
//        
//    });

    // 소분류 순서 저장 버튼
//    document.getElementById('save-subcategory-order').addEventListener('click', function() {
//        const newOrder = Array.from(document.querySelectorAll('.sortable-subcategory-item')).map(function(item, index) {
//            return {
//                subcategoryId: item.getAttribute('data-subcategory-id'),
//                order: index + 1
//            };
//        });
//
//        // AJAX를 통해 서버로 새로운 순서 저장
//        fetch('/saveSubcategoryOrder', {
//            method: 'POST',
//            headers: {
//                'Content-Type': 'application/json'
//            },
//            body: JSON.stringify(newOrder)
//        }).then(response => response.json())
//          .then(data => {
//              console.log("Saved subcategory order:", data);
//          });
//    });
    
    
	
	
    // 팝업 닫기 버튼 클릭 시
    $('#close').click(function() {
        $("#popup").fadeOut();
    });
    
    
	
	
	$(document).on('click', '#templateconfirm', function () {
		console.log("asdasd");
		templateChoice()
	});
	
	document.getElementById("mainCategoryDiv").addEventListener("mouseover", (event) => {
		// Check if the hovered element is or contains a `.custom-radio` element
		const customRadio = event.target.closest(".custom-radio");
		if (customRadio) {
			const penIcon = customRadio.querySelector(".bi-pen");
			if (penIcon) {
				penIcon.style.display = "inline"; // Show the `.bi-pen` element
			}
		}
	});
	
	document.getElementById("mainCategoryDiv").addEventListener("mouseout", (event) => {
		// Check if the hovered element is or contains a `.custom-radio` element
		const customRadio = event.target.closest(".custom-radio");
		if (customRadio) {
			const penIcon = customRadio.querySelector(".bi-pen");
			if (penIcon) {
				penIcon.style.display = "none"; // Hide the `.bi-pen` element
			}
		}
	});

	// 서브 카테고리 수정 이벤트 처리
	document.getElementById("subCategoryDiv").addEventListener("mouseover", (event) => {
		// Check if the hovered element is or contains a `.custom-radio` element
		const customRadio = event.target.closest(".custom-radio");
		if (customRadio) {
			const penIcon = customRadio.querySelector(".bi-pen");
			if (penIcon) {
				penIcon.style.display = "inline"; // Show the `.bi-pen` element
			}
		}
	});
	
	document.getElementById("subCategoryDiv").addEventListener("mouseout", (event) => {
		// Check if the hovered element is or contains a `.custom-radio` element
		const customRadio = event.target.closest(".custom-radio");
		if (customRadio) {
			const penIcon = customRadio.querySelector(".bi-pen");
			if (penIcon) {
				penIcon.style.display = "none"; // Hide the `.bi-pen` element
			}
		}
	});
    // 옵션 카테고리 수정 이벤트 처리
	document.getElementById("optionCategoryDiv").addEventListener("mouseover", (event) => {
		// Check if the hovered element is or contains a `.custom-radio` element
		const customRadio = event.target.closest(".custom-radio");
		if (customRadio) {
			const penIcon = customRadio.querySelector(".bi-pen");
			if (penIcon) {
				penIcon.style.display = "inline"; // Show the `.bi-pen` element
			}
		}
	});
	
	document.getElementById("optionCategoryDiv").addEventListener("mouseout", (event) => {
		// Check if the hovered element is or contains a `.custom-radio` element
		const customRadio = event.target.closest(".custom-radio");
		if (customRadio) {
			const penIcon = customRadio.querySelector(".bi-pen");
			if (penIcon) {
				penIcon.style.display = "none"; // Hide the `.bi-pen` element
			}
		}
	});
	// 통합된 svg 상위 요소 이벤트 중지
	document.addEventListener('click', (event) => {
	    const penIcon = event.target.closest('.bi-pen');
	    if (penIcon) {
	        event.stopPropagation(); // Stop event bubbling
	        event.preventDefault(); // Prevent default behavior
	
	        // 데이터 타입 및 ID 가져오기
	        const categoryIdx = penIcon.getAttribute('value');
	        const parentLabel = penIcon.closest('.custom-radio');
	        const categoryType = parentLabel ? parentLabel.getAttribute('data-category-type') : null;
	        const currentName = getLabelTextFromCategory(parentLabel);
	        const inputType = parentLabel ? (parentLabel.getAttribute('data-input-type') || '') : '';
	
	        if (categoryType) {
	            console.log(`Editing category with ID: ${categoryIdx}, TYPE: ${categoryType}`);
	            ModifyMainCategory(event, categoryType, categoryIdx, currentName, inputType);
	        } else {
	            console.warn('Category type not found.');
	        }
	    }
	});
	
	// 분류,옵션명 수정 API
	document.getElementById('ModifyBtn').addEventListener('click', function () {
		const type = this.getAttribute('data-type'); // Retrieve the type
	    const id = parseInt(this.getAttribute('data-id'), 10);     // Retrieve the id
	    const inputValue = document.getElementById('cc_col_02').value; // Get user input
	
	    if (!inputValue) {
	        alert('값을 입력하세요!');
	        return;
	    }
	
	    console.log(`Modifying ${type} category with ID: ${id}, New Value: ${inputValue}`);
	    // Perform AJAX or further logic with type, id, and inputValue
	    const data = {
	        type: type,
	        id: id,
	        inputValue: inputValue
	    };
	    closePopup2();
	   	spinner();
	    $.ajax({
			url: '/csm/setting/modifyCategory',
			type: 'post',
			dataType: 'json',
			contentType: 'application/json',
			data: JSON.stringify(data),
			success: function (response) {
				console.log('수정완료');
				console.log(response);
				window.location.reload();
			},
			error: function (error) {
				console.error("Error Modify Category : ", error);
			}
		})
	});
    restoreCheckboxState();
    
    
    
	// ✅ 체크박스 상태 저장 (LocalStorage 활용)
	function saveCheckboxState() {
	    let checkboxState = {};
	    $(".category-checkbox").each(function() {
	        checkboxState[$(this).attr("id")] = $(this).is(":checked");
	    });
	    localStorage.setItem("checkboxState", JSON.stringify(checkboxState));
	}
	
	// ✅ 체크박스 상태 복원
	function restoreCheckboxState() {
	    let checkboxState = localStorage.getItem("checkboxState");
	    if (checkboxState) {
	        checkboxState = JSON.parse(checkboxState);
	        $(".category-checkbox").each(function() {
	            let id = $(this).attr("id");
	            if (checkboxState[id]) {
	                $(this).prop("checked", true);
	            }
	        });
	    }
	}
	
	// ✅ 동적으로 카테고리 리스트 업데이트
	function updateCategoryList(response, currentCategoryType) {
	    console.log("Updating category list for:", currentCategoryType);
	    console.log("✅ Full response:", response);
	
	    // ⚠️ `response.data`가 존재하지 않거나 유효하지 않은 경우 처리
	    if (!response.data) {
	        console.warn("⚠️ No valid data received.");
	        return;
	    }
	
	    // ✅ 데이터가 배열인지 확인
	    if (Array.isArray(response.data)) {
	        console.log("📌 response.data is an array.");
	    } else {
	        console.log("📌 response.data is an object.");
	    }
	
	    let categoryHTML = '';
	
	    if (currentCategoryType === "parent") {
	        // 🚀 대분류 추가 UI 업데이트
	        categoryHTML = `
	        	<label class="custom-radio" data-category-type="main">
	                <input 
	                    type="radio" checked
	                    data-category-type="main" 
	                    name="mainCategory" 
	                    value="${response.data.cc_col_01}">
	                <span class="custom-radio-mark"></span>
	                ${response.data.cc_col_02}
	                <img src="/csm/icon/ev/pen-icon.png"
					width="16"
					height="16"
					fill="currentColor"
					class="bi bi-pen"
					viewBox="0 0 16 16"
					value="${response.data.cc_col_01}">
					  
	            </label>
	        `;
	        $("#mainCategoryDiv").prepend(categoryHTML);
			updateSelectedCategoryStyles();
			// ✅ 동적으로 추가된 요소에도 클릭 이벤트 적용 (jQuery의 `$(document).on()` 활용)
		    $(document).off("click", 'input[name="mainCategory"]').on("click", 'input[name="mainCategory"]', function () {
	            let categoryId = $(this).val();
	            console.log("Category ID:", categoryId);
	
	            if (typeof window.getSubCategories === "function") {
	                window.getSubCategories(categoryId, this);
	
	                // ✅ Enable buttons after selection
	                $('#new-subcategory').prop('disabled', false);
	                $('#del-subcategory').prop('disabled', false);
	            } else {
	                console.error("getSubCategories is not defined");
	            }
	        });
		    // 🚀 추가된 라디오 버튼을 자동 클릭 (최초로 추가된 요소)
		    setTimeout(() => {
		        let newCategoryInput = $(`input[name="mainCategory"][value="${response.data.cc_col_01}"]`);
		        if (newCategoryInput.length > 0) {
		            console.log("✅ 자동 클릭 실행:", response.data.cc_col_01);
		            newCategoryInput.trigger("click");
		        } else {
		            console.error("❌ 자동 클릭할 요소를 찾을 수 없음.");
		        }
		    }, 100);
	    } else if (currentCategoryType === "child") {
			console.log("📌 Adding new subcategory:", response.data);

	        if (!response.data.cc_col_01 || !response.data.cc_col_02) {
	            console.error("❌ Invalid data structure:", response.data);
	            return;
	        }
	        // 🚀 소분류 추가 UI 업데이트
	        categoryHTML = `
	        	<label class="custom-radio" data-category-type="sub" data-input-type="${buildSubcategoryInputTypes(response.data)}">
	        		<input
	        			type="radio" checked
	        			data-category-type="sub"
	        			name="subCategory"
	        			value="${response.data.cc_col_01}">
	        		<span class="custom-radio-mark"></span>
	        		${response.data.cc_col_02}
	        		<img src="/csm/icon/ev/pen-icon.png"
					width="16"
					height="16"
					fill="currentColor"
					class="bi bi-pen"
					viewBox="0 0 16 16"
					value="${response.data.cc_col_01}">
					  
	        	</label>
	        `;
	        $("#subCategoryDiv").prepend(categoryHTML);
			updateSelectedCategoryStyles();
			
		    setTimeout(() => {
	            let newSubCategoryInput = $(`input[name="subCategory"][value="${response.data.cc_col_01}"]`);
	            if (newSubCategoryInput.length > 0) {
	                console.log("✅ Auto-click executed (subCategory):", response.data.cc_col_01);
	                newSubCategoryInput.trigger("click");
	            } else {
	                console.error("❌ Could not find subCategory element for auto-click.");
	            }
	        }, 200);
	    } else if (currentCategoryType === "option") {
			console.log("📌 Adding new options:", response.data);
	
	        // ✅ 옵션이 배열인지 확인
	        if (!Array.isArray(response.data)) {
	            console.error("❌ response.data is not an array. Skipping option update.");
	            return;
	        }
	        // ✅ 기존 옵션 목록 초기화
	        $("#optionCategoryDiv").empty();
	
	        // ✅ 옵션 추가 UI 업데이트
	        response.data.forEach(option => {
	            if (!option.cc_col_03 || !Array.isArray(option.cc_col_03) || option.cc_col_03.length === 0) {
	                console.warn("⚠️ Invalid option data:", option);
	                return;
	            }
	
	            let optionText = option.cc_col_03[0]; // ✅ 옵션 텍스트 추출
	
	            categoryHTML += `
	                <label class="custom-radio option-item">
	                    <input type="radio" name="optionCategory" value="${option.cc_col_01}">
	                    <span class="custom-radio-mark"></span>
	                    ${optionText}  <!-- ✅ 옵션 텍스트 추가 -->
	                    <img src="/csm/icon/ev/pen-icon.png" width="16" height="16" class="bi bi-pen" value="${option.cc_col_01}">
	                </label>
	            `;
	        });
	
	        // ✅ 옵션 목록 업데이트
	        $("#optionCategoryDiv").html(categoryHTML);
	
	        console.log("✅ Option categories updated:", response.data);
	        $('#selectboxOptions').val('');
	    }
	}
	
	// 추가 확인 버튼 클릭 시 호출할 함수
	function addCategory() {
	    const categoryName = $("#addCategoryName").val(); // 입력된 카테고리 이름
		console.log("Category Name:", categoryName); // 값이 제대로 출력되는지 확인
	    // 카테고리 유형에 따른 URL 및 데이터 설정
	    let url = '';
	    let data = {};
	
	    if (currentCategoryType === "parent") {
	        url = '/csm/setting/category1';  // 대분류 추가 API
	        data = { cc_col_02: categoryName };
	        
	        if(!categoryName || categoryName.trim() === '') {
				alert("상담항목을 입력해주세요.")
				return;
			} 
	        
	    } else if (currentCategoryType === "child") {
	        url = '/csm/setting/category2';  // 소분류 추가 API
	        const mainCategoryId = $('input[name="mainCategory"]:checked').val();
	        id = mainCategoryId;
	        if (!mainCategoryId) {
	            alert("상담항목을 먼저 선택하세요.");
	            return;
	        }
	        
	        // Gather subcategory-specific fields
	        const isCheckbox = $("#isCheckbox").is(":checked");
	        const isRadio = $("#isRadio").is(":checked");
	        const isSelectbox = $("#isSelectbox").is(":checked");
	        const isTextbox = $("#isTextbox").is(":checked");
			// Process selectbox options if enabled
			if (!isCheckbox && !isRadio && !isSelectbox && !isTextbox) {
				alert('구분을 선택해주셔야 합니다.');
				return;
			}
	        let selectboxOptions = [];
	        if (isSelectbox) {
	            selectboxOptions = $("#selectboxOptions")
	                .val()
	                .split(",")
	                .map(option => option.trim()) // Trim each option
	                .filter(option => option !== ""); // Remove any empty options
	        }
	        data = {
	            cc_col_02: categoryName,
	            hiddenid: mainCategoryId, // Ensure hiddenId is passed as an integer
	            checkbox: isCheckbox,
	            radio: isRadio,
	            textbox: isTextbox,
	            selectbox: isSelectbox,
	            options: selectboxOptions // Send options as an array
	        };
	    } else if (currentCategoryType === "option") {
	        url = '/csm/setting/category3';  // 옵션 추가 API
	        const subCategoryId = $('input[name="subCategory"]:checked').val();
	        SubId = subCategoryId;
	        if (!subCategoryId) {
	            alert("설정값를 먼저 선택하세요.");
	            return;
	        }
	        let selectedText = $('input[name="subCategory"]:checked').closest("label").contents().filter(function() {
			    return this.nodeType === 3;
			}).text().trim();
			
			console.log("선택된 라디오 버튼의 텍스트:", selectedText);
	        const selectboxOptions = $("#selectboxOptions")
	            .val()
	            .split(",")
	            .map(option => option.trim())
	            .filter(option => option !== "");
	        if (!selectboxOptions.length) {
	            alert("Select Box 설정값을 입력하세요.");
	            return;
	        }
	        data = {
	            cc_col_02: selectedText,
	            cc_col_04: subCategoryId,
	            cc_col_03: selectboxOptions
	        };
	        console.log(categoryName);
	        console.log(subCategoryId);
	        console.log(selectboxOptions);
	        
	    }
		spinner();
	    // AJAX 요청을 통해 카테고리 추가
	    $.ajax({
	        url: url,
	        type: 'POST',
	        dataType: 'json',
	        contentType: 'application/json',
	        data: JSON.stringify(data),
	        success: function(response) {
	            console.log("Success:", response);
	            closePopup4();// 모달 닫기
	        //a    window.location.reload(); // 페이지 새로고침
	
				// 🚀 페이지 새로고침 대신 동적으로 UI 업데이트
	            updateCategoryList(response, currentCategoryType);
	            console.log("동적 업데이트 카테고리 타입", currentCategoryType);
				// 체크박스 상태 유지
	            saveCheckboxState();
	            hideSpinner();
	        },
	        error: function(error) {
	            console.log("Error:", error);
	        }
	    });
	} 
});

// `dom` 돔  끝

'use strict';

let assessmentDetails=null;
let assessmentRequest=0;
const assessmentMaxFiles=10, assessmentMaxBytes=5*1024*1024;

function assessmentButton(scoreId) {
  return `<button type="button" class="text-button" data-assessment="${scoreId}">جزئیات و پیوست‌ها</button>`;
}
function bindAssessmentButtons(container) {
  container.querySelectorAll('[data-assessment]').forEach(button=>button.onclick=()=>openAssessmentDetails(Number(button.dataset.assessment)));
}
function assessmentScoreAction(employeeId,factorId) {
  const score=state.data.scores.find(row=>row.employeeId===employeeId&&row.factorId===factorId);
  return score?assessmentButton(score.id):'<span class="muted">ابتدا نمره را ذخیره کنید.</span>';
}
function assessmentFileSize(bytes) {
  return bytes>=1024*1024?`${number(bytes/(1024*1024))} مگابایت`:`${number(Math.max(1,Math.ceil(bytes/1024)))} کیلوبایت`;
}
function assessmentError(message) {
  $('assessment-error').textContent=message;$('assessment-error').hidden=!message;
}
function assessmentChanged() {
  const details=assessmentDetails;
  return details&&(details.files.length||details.removed.size||['strengths','weaknesses','notes'].some(key=>$(`assessment-${key}`).value!==(details.score[key]||'')));
}
function closeAssessmentDetails() {
  if(state.busy)return;
  if(assessmentChanged()&&!window.confirm('تغییرات ارزیابی هنوز ذخیره نشده‌اند. تغییرات کنار گذاشته شوند؟'))return;
  $('assessment-dialog').close();
}
async function openAssessmentDetails(scoreId) {
  if(state.busy)return;
  if(!can('scores'))return deniedAction();
  const request=++assessmentRequest;
  assessmentDetails=null;assessmentError('');
  $('assessment-content').hidden=true;$('assessment-loading').hidden=false;
  $('assessment-save').hidden=true;$('assessment-summary').textContent='';
  $('assessment-dialog').showModal();
  try {
    const data=await api(`scores/${scoreId}/details`);
    if(!$('assessment-dialog').open||request!==assessmentRequest)return;
    assessmentDetails={...data,files:[],removed:new Set()};
    const score=data.score,editable=can('scores','edit');
    $('assessment-summary').textContent=`${label('employees',score.employeeId)} · ${label('factors',score.factorId)} · نمره ${number(score.score)} از ۱۰۰ · ${date(score.assessedOn)}`;
    for(const key of ['strengths','weaknesses','notes']) {
      $(`assessment-${key}`).value=score[key]||'';$(`assessment-${key}`).readOnly=!editable;
      $(`assessment-${key}`).removeAttribute('aria-invalid');$(`assessment-${key}-error`).textContent='';
    }
    $('assessment-files').value='';$('assessment-upload').hidden=!editable;
    $('assessment-save').hidden=!editable;$('assessment-save').disabled=false;
    $('assessment-content').hidden=false;renderAssessmentFiles();
  } catch(error) { if(request===assessmentRequest)assessmentError(error.message); }
  finally { if(request===assessmentRequest)$('assessment-loading').hidden=true; }
}
function renderAssessmentFiles() {
  const details=assessmentDetails;if(!details)return;
  const editable=can('scores','edit');
  const existing=details.attachments.map(file=>{
    const removed=details.removed.has(file.id);
    return `<li class="${removed?'assessment-file-removed':''}"><div><a href="/api/scores/${details.score.id}/attachments/${file.id}" download>${escapeHtml(file.fileName)}</a><small>${assessmentFileSize(file.fileSize)}${removed?' · پس از ذخیره حذف می‌شود':''}</small></div>${editable?`<button type="button" class="text-button ${removed?'':'danger-text'}" data-remove-attachment="${file.id}">${removed?'بازگرداندن':'حذف'}</button>`:''}</li>`;
  });
  const pending=details.files.map((file,index)=>`<li><div><span>${escapeHtml(file.name)}</span><small>${assessmentFileSize(file.size)} · آمادهٔ بارگذاری</small></div><button type="button" class="text-button danger-text" data-remove-pending="${index}">برداشتن</button></li>`);
  $('assessment-attachments').innerHTML=existing.length+pending.length?`<ul class="assessment-files">${existing.concat(pending).join('')}</ul>`:'<p class="muted">هنوز پیوستی ثبت نشده است.</p>';
  $('assessment-file-count').textContent=`${number(details.attachments.length-details.removed.size+details.files.length)} از ${number(assessmentMaxFiles)} پیوست`;
  $('assessment-attachments').querySelectorAll('[data-remove-attachment]').forEach(button=>button.onclick=()=>{
    if(state.busy)return;
    const id=Number(button.dataset.removeAttachment);
    if(details.removed.has(id)) {
      if(details.attachments.length-details.removed.size+details.files.length>=assessmentMaxFiles){assessmentError('ابتدا یکی از فایل‌های جدید را بردارید؛ حداکثر ۱۰ پیوست مجاز است.');return;}
      details.removed.delete(id);
    }else details.removed.add(id);
    assessmentError('');renderAssessmentFiles();
  });
  $('assessment-attachments').querySelectorAll('[data-remove-pending]').forEach(button=>button.onclick=()=>{
    if(state.busy)return;details.files.splice(Number(button.dataset.removePending),1);assessmentError('');renderAssessmentFiles();
  });
}
function selectAssessmentFiles() {
  const files=Array.from($('assessment-files').files);$('assessment-files').value='';
  if(state.busy||!assessmentDetails||!can('scores','edit'))return;
  if(assessmentDetails.attachments.length-assessmentDetails.removed.size+assessmentDetails.files.length+files.length>assessmentMaxFiles){assessmentError('برای هر ارزیابی حداکثر ۱۰ پیوست مجاز است.');return;}
  const invalid=files.find(file=>file.size===0||file.size>assessmentMaxBytes||file.name.length>255);
  if(invalid){assessmentError(`فایل «${invalid.name}» باید غیرخالی، حداکثر ۵ مگابایت و دارای نام حداکثر ۲۵۵ کاراکتری باشد.`);return;}
  assessmentDetails.files.push(...files);assessmentError('');renderAssessmentFiles();
}
async function saveAssessmentDetails(event) {
  event.preventDefault();if(state.busy||!assessmentDetails)return;
  if(!can('scores','edit'))return deniedAction();
  assessmentError('');
  const details=assessmentDetails,payload={removedAttachmentIds:[...details.removed]};let invalid=false;
  for(const key of ['strengths','weaknesses','notes']) {
    const input=$(`assessment-${key}`),value=input.value;
    input.removeAttribute('aria-invalid');$(`assessment-${key}-error`).textContent='';
    if(value.length>input.maxLength){input.setAttribute('aria-invalid','true');$(`assessment-${key}-error`).textContent='طول متن بیشتر از حد مجاز است.';invalid=true;}
    payload[key]=value;
  }
  if(invalid){$('assessment-form').querySelector('[aria-invalid]')?.focus();return;}
  const body=new FormData();body.append('details',new Blob([JSON.stringify(payload)],{type:'application/json'}));
  details.files.forEach(file=>body.append('files',file));
  state.busy=true;$('assessment-save').textContent='در حال ذخیره…';
  $('assessment-form').querySelectorAll('input,textarea,button').forEach(input=>input.disabled=true);
  try {
    const saved=await auth.json(`/api/scores/${details.score.id}/details`,{method:'PUT',body});
    state.data.scores=state.data.scores.map(score=>score.id===saved.score.id?saved.score:score);
    assessmentDetails=null;$('assessment-dialog').close();render();toast('جزئیات ارزیابی و پیوست‌ها ذخیره شدند.');
  } catch(error) {
    assessmentError(error.message);
    for(const [key,message] of Object.entries(error.fields||{})) {
      const input=$(`assessment-${key}`);if(!input)continue;
      input.setAttribute('aria-invalid','true');$(`assessment-${key}-error`).textContent=message;
    }
  } finally {
    state.busy=false;$('assessment-save').textContent='ذخیره جزئیات و پیوست‌ها';
    $('assessment-form').querySelectorAll('input,textarea,button').forEach(input=>input.disabled=false);
    $('assessment-form').querySelector('[aria-invalid]')?.focus();
  }
}
$('assessment-form').addEventListener('submit',saveAssessmentDetails);
$('assessment-files').addEventListener('change',selectAssessmentFiles);
document.querySelectorAll('[data-assessment-close]').forEach(button=>button.onclick=closeAssessmentDetails);
$('assessment-dialog').addEventListener('cancel',event=>{event.preventDefault();closeAssessmentDetails();});
$('assessment-dialog').addEventListener('close',()=>{assessmentRequest++;assessmentDetails=null;$('assessment-files').value='';});

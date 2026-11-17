'use strict';

let trainingFiles=null,trainingFilesRequest=0;
const trainingMaxFiles=10,trainingMaxBytes=5*1024*1024;

function trainingRecordButton(id) {
  return `<button type="button" class="text-button" data-training-record="${id}">مشاهده و پیوست‌ها</button>`;
}
function bindTrainingRecordButtons(container) {
  container.querySelectorAll('[data-training-record]').forEach(button=>button.onclick=()=>{
    const record=state.data.trainings.find(row=>row.id===Number(button.dataset.trainingRecord));
    if(record)openForm(record,'trainings');
  });
}
function trainingFileError(message) {
  $('training-files-error').textContent=message;$('training-files-error').hidden=!message;
}
function trainingFileSize(bytes) {
  return bytes>=1024*1024?`${number(bytes/(1024*1024))} مگابایت`:`${number(Math.max(1,Math.ceil(bytes/1024)))} کیلوبایت`;
}
async function prepareTrainingFiles(record,readOnly) {
  const request=++trainingFilesRequest;
  trainingFiles={id:record?.id??null,readOnly,attachments:[],pending:[],removed:new Set(),loading:Boolean(record),failed:false};
  $('training-files-section').hidden=false;$('training-upload').hidden=readOnly;$('training-files').value='';
  trainingFileError('');$('training-files-retry').hidden=true;renderTrainingFiles();
  if(!record)return;
  $('save-button').disabled=true;
  try {
    const attachments=await api(`trainings/${record.id}/attachments`);
    if(request!==trainingFilesRequest)return;
    trainingFiles.attachments=attachments;
  } catch(error) {
    if(request!==trainingFilesRequest)return;
    trainingFiles.failed=true;trainingFileError(error.message);$('training-files-retry').hidden=false;
    $('training-files-retry').onclick=()=>prepareTrainingFiles(record,readOnly);
  } finally {
    if(request===trainingFilesRequest) {
      trainingFiles.loading=false;$('save-button').disabled=trainingFiles.failed;renderTrainingFiles();
    }
  }
}
function resetTrainingFiles() {
  trainingFilesRequest++;trainingFiles=null;$('training-files-section').hidden=true;$('training-files').value='';
}
function trainingRemainingCount() {
  return trainingFiles.attachments.length-trainingFiles.removed.size+trainingFiles.pending.length;
}
function renderTrainingFiles() {
  const files=trainingFiles;if(!files)return;
  $('training-files').disabled=state.busy||files.loading||files.failed;
  $('training-file-count').textContent=files.loading?'در حال دریافت پیوست‌ها…':`${number(trainingRemainingCount())} از ۱۰ پیوست`;
  if(files.loading){$('training-attachments').replaceChildren();return;}
  const existing=files.attachments.map(file=>{
    const removed=files.removed.has(file.id);
    return `<li class="${removed?'training-file-removed':''}"><div><a href="/api/trainings/${files.id}/attachments/${file.id}" download>${escapeHtml(file.fileName)}</a><small>${trainingFileSize(file.fileSize)}${removed?' · پس از ذخیره حذف می‌شود':''}</small></div>${files.readOnly?'':`<button type="button" class="text-button ${removed?'':'danger-text'}" data-training-remove="${file.id}">${removed?'بازگرداندن':'حذف'}</button>`}</li>`;
  });
  const pending=files.pending.map((file,index)=>`<li><div><span>${escapeHtml(file.name)}</span><small>${trainingFileSize(file.size)} · آمادهٔ بارگذاری</small></div><button type="button" class="text-button danger-text" data-training-pending="${index}">برداشتن</button></li>`);
  $('training-attachments').innerHTML=existing.length+pending.length?`<ul class="training-files">${existing.concat(pending).join('')}</ul>`:'<p class="muted">پیوستی انتخاب یا ثبت نشده است.</p>';
  $('training-attachments').querySelectorAll('[data-training-remove]').forEach(button=>button.onclick=()=>{
    if(state.busy)return;
    const id=Number(button.dataset.trainingRemove);
    if(files.removed.has(id)) {
      if(trainingRemainingCount()>=trainingMaxFiles){trainingFileError('ابتدا یکی از فایل‌های جدید را بردارید؛ حداکثر ۱۰ پیوست مجاز است.');return;}
      files.removed.delete(id);
    }else files.removed.add(id);
    trainingFileError('');renderTrainingFiles();
  });
  $('training-attachments').querySelectorAll('[data-training-pending]').forEach(button=>button.onclick=()=>{
    if(state.busy)return;files.pending.splice(Number(button.dataset.trainingPending),1);trainingFileError('');renderTrainingFiles();
  });
}
function trainingCanDiscard() {
  return !state.busy&&(!trainingFiles||(!trainingFiles.pending.length&&!trainingFiles.removed.size)||window.confirm('تغییرات پیوست‌ها ذخیره نشده‌اند. فرم بسته شود؟'));
}
async function saveTrainingRecord(payload) {
  const files=trainingFiles;
  if(!files||files.loading||files.failed||files.readOnly)return;
  if(!can('trainings',files.id?'edit':'create'))return deniedAction();
  if(payload.startDate&&payload.completionDate&&payload.startDate>payload.completionDate) {
    fieldError('startDate','تاریخ شروع نمی‌تواند بعد از تاریخ پایان باشد.');$('field-startDate').focus();return;
  }
  const body=new FormData();
  body.append('details',new Blob([JSON.stringify({training:payload,removedAttachmentIds:[...files.removed]})],{type:'application/json'}));
  files.pending.forEach(file=>body.append('files',file));
  state.busy=true;trainingFileError('');$('save-button').textContent='در حال ذخیره…';
  $('record-form').querySelectorAll('input,select,textarea,button').forEach(element=>element.disabled=true);
  try {
    await auth.json(`/api/trainings${files.id?`/${files.id}`:''}`,{method:files.id?'PUT':'POST',body});
    $('record-dialog').close();toast('سابقهٔ آموزشی و پیوست‌ها ذخیره شدند.');
    try { await loadData();render(); } catch { showLoadError('سابقه ذخیره شد، اما فهرست تازه دریافت نشد. صفحه را تازه‌سازی کنید.'); }
  } catch(error) {
    $('form-error').textContent=error.message;$('form-error').hidden=false;
    for(const [name,message] of Object.entries(error.fields||{}))fieldError(name.replace(/^training\./,''),message);
  } finally {
    state.busy=false;$('save-button').textContent='ذخیره اطلاعات';
    $('record-form').querySelectorAll('input,select,textarea,button').forEach(element=>element.disabled=false);
    $('record-form').querySelector('[aria-invalid]')?.focus();
  }
}
$('training-files').addEventListener('change',()=>{
  const selected=Array.from($('training-files').files);$('training-files').value='';
  if(state.busy||!trainingFiles||trainingFiles.readOnly||trainingFiles.loading||trainingFiles.failed)return;
  if(trainingRemainingCount()+selected.length>trainingMaxFiles){trainingFileError('برای هر سابقه حداکثر ۱۰ پیوست مجاز است.');return;}
  const invalid=selected.find(file=>!file.size||file.size>trainingMaxBytes||file.name.length>255);
  if(invalid){trainingFileError(`فایل «${invalid.name}» باید غیرخالی، حداکثر ۵ مگابایت و دارای نام حداکثر ۲۵۵ کاراکتری باشد.`);return;}
  trainingFiles.pending.push(...selected);trainingFileError('');renderTrainingFiles();
});
$('record-dialog').addEventListener('close',resetTrainingFiles);

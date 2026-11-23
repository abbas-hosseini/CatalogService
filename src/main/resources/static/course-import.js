'use strict';

let courseImportReport=null, courseImportFile=null;

function resetCourseImport() {
  courseImportReport=null;courseImportFile=null;
  $('course-import-results').replaceChildren();
  $('course-import-status').textContent='';
  $('course-import-error').hidden=true;
  $('course-import-errors-only').checked=false;
  $('course-import-filter-label').hidden=true;
  $('course-import-save').disabled=true;
}

function renderCourseImport() {
  const report=courseImportReport;
  if(!report)return;
  $('course-import-status').textContent=`${number(report.rows.length)} ردیف: ${number(report.validCount)} معتبر، ${number(report.errorCount)} دارای خطا. ${report.message}`;
  $('course-import-filter-label').hidden=report.errorCount===0;
  const rows=report.rows.filter(row=>!$('course-import-errors-only').checked||row.errors.length);
  const fields=[['code','کد دوره'],['title','عنوان دوره'],['provider','برگزارکننده'],['durationHours','مدت دوره (ساعت)'],['description','توضیحات']];
  $('course-import-results').innerHTML=`<div class="data-panel table-wrap" tabindex="0" role="region" aria-label="پیش‌نمایش دوره‌ها"><table><caption class="muted">شمارهٔ ردیف مطابق اکسل است. وجود خطا مانع ثبت کل فایل می‌شود.</caption><thead><tr><th scope="col">ردیف</th><th scope="col">وضعیت</th>${fields.map(([,label])=>`<th scope="col">${label}</th>`).join('')}</tr></thead><tbody>${rows.map(row=>{
    const values={...row.course,durationHours:row.durationHours};
    return `<tr class="${row.errors.length?'import-invalid':''}"><td>${number(row.rowNumber)}</td><td class="import-row-status">${row.errors.length?`<ul>${row.errors.map(error=>`<li>${escapeHtml(error)}</li>`).join('')}</ul>`:'<span class="badge">آمادهٔ ثبت</span>'}</td>${fields.map(([name])=>`<td class="import-description"><span${name==='code'?' class="code"':''}>${escapeHtml(values[name]??'—')}</span></td>`).join('')}</tr>`;
  }).join('')}</tbody></table></div>`;
}

async function uploadCourseFile(file, commit) {
  const data=new FormData();data.append('file',file);
  let response;
  try { response=await auth.request(`/api/courses/import${commit?'':'/preview'}`,{method:'POST',body:data,headers:{Accept:'application/json'}}); }
  catch { throw new Error(commit?'ارتباط قطع شد و نتیجهٔ ثبت مشخص نیست. فهرست دوره‌ها را تازه‌سازی کنید و سپس فایل را دوباره بررسی کنید.':'ارتباط با سرور برقرار نشد. دوباره تلاش کنید.'); }
  const report=await response.json().catch(()=>({message:'پاسخ سرور قابل خواندن نیست. دوباره فایل را بررسی کنید.'}));
  if(!response.ok&&!(response.status===422&&Array.isArray(report.rows)))throw new Error(report.message||'بررسی فایل انجام نشد.');
  if(!Array.isArray(report.rows))throw new Error(report.message||'پاسخ سرور معتبر نیست.');
  return report;
}

async function processCourseImport(commit) {
  if(state.busy)return;
  if(!can('courses','import'))return deniedAction();
  const file=commit?courseImportFile:$('course-import-file').files[0];
  if(commit&&(!courseImportReport||courseImportReport.errorCount||courseImportReport.importedCount||!file))return;
  $('course-import-error').hidden=true;
  if(!file||! /\.(xlsx|xls)$/i.test(file.name)||file.size>5*1024*1024){
    $('course-import-error').textContent='یک فایل xlsx یا xls با حجم حداکثر ۵ مگابایت انتخاب کنید.';
    $('course-import-error').hidden=false;return;
  }
  state.busy=true;
  $('course-import-file').disabled=true;
  $('course-import-preview').disabled=true;
  $('course-import-save').disabled=true;
  $('course-import-status').textContent=commit?'در حال ثبت دوره‌ها…':'در حال بررسی فایل…';
  try {
    courseImportReport=await uploadCourseFile(file,commit);
    courseImportFile=file;
    renderCourseImport();
    if(commit&&courseImportReport.importedCount>0){
      const imported=courseImportReport.importedCount;
      $('course-import-dialog').close();
      toast(`${number(imported)} دوره با موفقیت ثبت شد.`);
      state.search='';state.filter='';state.page=1;
      try{await loadData();render();}catch{showLoadError('دوره‌ها ثبت شدند، اما دریافت فهرست تازه انجام نشد. صفحه را تازه‌سازی کنید.');}
    }
  }catch(error){
    courseImportReport=null;courseImportFile=null;
    $('course-import-results').replaceChildren();
    $('course-import-status').textContent='';
    $('course-import-filter-label').hidden=true;
    $('course-import-error').textContent=error.message;
    $('course-import-error').hidden=false;
  }finally{
    state.busy=false;
    $('course-import-file').disabled=false;
    $('course-import-preview').disabled=false;
    $('course-import-save').disabled=!courseImportReport||courseImportReport.errorCount>0||courseImportReport.importedCount>0;
  }
}

$('import-courses-button').onclick=()=>{
  if(state.busy)return;
  if(!can('courses','import'))return deniedAction();
  resetCourseImport();$('course-import-file').value='';$('course-import-dialog').showModal();
};
$('course-import-file').addEventListener('change',resetCourseImport);
$('course-import-errors-only').addEventListener('change',renderCourseImport);
$('course-import-preview').onclick=()=>processCourseImport(false);
$('course-import-save').onclick=()=>processCourseImport(true);
$('course-import-dialog').addEventListener('close',()=>{resetCourseImport();$('course-import-file').value='';});

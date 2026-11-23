'use strict';

let jobImportReport=null, jobImportFile=null;

function resetJobImport() {
  jobImportReport=null;jobImportFile=null;
  $('job-import-results').replaceChildren();
  $('job-import-status').textContent='';
  $('job-import-error').hidden=true;
  $('job-import-errors-only').checked=false;
  $('job-import-filter-label').hidden=true;
  $('job-import-save').disabled=true;
}

function renderJobImport() {
  const report=jobImportReport;
  if(!report)return;
  $('job-import-status').textContent=`${number(report.rows.length)} ردیف: ${number(report.validCount)} معتبر، ${number(report.errorCount)} دارای خطا. ${report.message}`;
  $('job-import-filter-label').hidden=report.errorCount===0;
  const rows=report.rows.filter(row=>!$('job-import-errors-only').checked||row.errors.length);
  const fields=[['code','کد شغل'],['title','عنوان شغل'],['company','شرکت'],['parentCode','کد شغل بالادست'],['description','توضیحات']];
  $('job-import-results').innerHTML=`<div class="data-panel table-wrap" tabindex="0" role="region" aria-label="پیش‌نمایش مشاغل"><table><caption class="muted">شمارهٔ ردیف مطابق اکسل است. وجود خطا مانع ثبت کل فایل می‌شود.</caption><thead><tr><th scope="col">ردیف</th><th scope="col">وضعیت</th>${fields.map(([,label])=>`<th scope="col">${label}</th>`).join('')}</tr></thead><tbody>${rows.map(row=>{
    const values={...row.job,company:row.company,parentCode:row.parentCode};
    return `<tr class="${row.errors.length?'import-invalid':''}"><td>${number(row.rowNumber)}</td><td class="import-row-status">${row.errors.length?`<ul>${row.errors.map(error=>`<li>${escapeHtml(error)}</li>`).join('')}</ul>`:'<span class="badge">آمادهٔ ثبت</span>'}</td>${fields.map(([name])=>`<td class="import-description"><span${['code','parentCode'].includes(name)?' class="code"':''}>${escapeHtml(values[name]??'—')}</span></td>`).join('')}</tr>`;
  }).join('')}</tbody></table></div>`;
}

async function uploadJobFile(file, commit) {
  const data=new FormData();data.append('file',file);
  let response;
  try { response=await auth.request(`/api/jobs/import${commit?'':'/preview'}`,{method:'POST',body:data,headers:{Accept:'application/json'}}); }
  catch { throw new Error(commit?'ارتباط قطع شد و نتیجهٔ ثبت مشخص نیست. فهرست مشاغل را تازه‌سازی کنید و سپس فایل را دوباره بررسی کنید.':'ارتباط با سرور برقرار نشد. دوباره تلاش کنید.'); }
  const report=await response.json().catch(()=>({message:'پاسخ سرور قابل خواندن نیست. دوباره فایل را بررسی کنید.'}));
  if(!response.ok&&!(response.status===422&&Array.isArray(report.rows)))throw new Error(report.message||'بررسی فایل انجام نشد.');
  if(!Array.isArray(report.rows))throw new Error(report.message||'پاسخ سرور معتبر نیست.');
  return report;
}

async function processJobImport(commit) {
  if(state.busy)return;
  if(!can('jobs','import'))return deniedAction();
  const file=commit?jobImportFile:$('job-import-file').files[0];
  if(commit&&(!jobImportReport||jobImportReport.errorCount||jobImportReport.importedCount||!file))return;
  $('job-import-error').hidden=true;
  if(!file||! /\.(xlsx|xls)$/i.test(file.name)||file.size>5*1024*1024){
    $('job-import-error').textContent='یک فایل xlsx یا xls با حجم حداکثر ۵ مگابایت انتخاب کنید.';
    $('job-import-error').hidden=false;return;
  }
  state.busy=true;
  $('job-import-file').disabled=true;
  $('job-import-preview').disabled=true;
  $('job-import-save').disabled=true;
  $('job-import-status').textContent=commit?'در حال ثبت مشاغل…':'در حال بررسی فایل…';
  try {
    jobImportReport=await uploadJobFile(file,commit);
    jobImportFile=file;
    renderJobImport();
    if(commit&&jobImportReport.importedCount>0){
      const imported=jobImportReport.importedCount;
      $('job-import-dialog').close();
      toast(`${number(imported)} شغل با موفقیت ثبت شد.`);
      state.search='';state.filter='';state.page=1;
      try{await loadData();render();}catch{showLoadError('مشاغل ثبت شدند، اما دریافت فهرست تازه انجام نشد. صفحه را تازه‌سازی کنید.');}
    }
  }catch(error){
    jobImportReport=null;jobImportFile=null;
    $('job-import-results').replaceChildren();
    $('job-import-status').textContent='';
    $('job-import-filter-label').hidden=true;
    $('job-import-error').textContent=error.message;
    $('job-import-error').hidden=false;
  }finally{
    state.busy=false;
    $('job-import-file').disabled=false;
    $('job-import-preview').disabled=false;
    $('job-import-save').disabled=!jobImportReport||jobImportReport.errorCount>0||jobImportReport.importedCount>0;
  }
}

$('import-jobs-button').onclick=()=>{
  if(state.busy)return;
  if(!can('jobs','import'))return deniedAction();
  resetJobImport();$('job-import-file').value='';$('job-import-dialog').showModal();
};
$('job-import-file').addEventListener('change',resetJobImport);
$('job-import-errors-only').addEventListener('change',renderJobImport);
$('job-import-preview').onclick=()=>processJobImport(false);
$('job-import-save').onclick=()=>processJobImport(true);
$('job-import-dialog').addEventListener('close',()=>{resetJobImport();$('job-import-file').value='';});

'use strict';

let employeeImportReport=null, employeeImportFile=null;

function resetEmployeeImport() {
  employeeImportReport=null;employeeImportFile=null;
  $('employee-import-results').replaceChildren();
  $('employee-import-status').textContent='';
  $('employee-import-error').hidden=true;
  $('employee-import-errors-only').checked=false;
  $('employee-import-filter-label').hidden=true;
  $('employee-import-save').disabled=true;
}

function renderEmployeeImport() {
  const report=employeeImportReport;
  if(!report)return;
  $('employee-import-status').textContent=`${number(report.rows.length)} ردیف: ${number(report.validCount)} معتبر، ${number(report.errorCount)} دارای خطا. ${report.message}`;
  $('employee-import-filter-label').hidden=report.errorCount===0;
  const rows=report.rows.filter(row=>!$('employee-import-errors-only').checked||row.errors.length);
  $('employee-import-results').innerHTML=`<div class="data-panel table-wrap"><table><caption class="muted">پیش‌نمایش اطلاعات فایل؛ شمارهٔ ردیف مطابق اکسل است.</caption><thead><tr><th scope="col">ردیف</th><th scope="col">کد پرسنلی</th><th scope="col">نام و نام خانوادگی</th><th scope="col">مدرک تحصیلی</th><th scope="col">تاریخ شروع به کار</th><th scope="col">توضیحات</th><th scope="col">وضعیت</th></tr></thead><tbody>${rows.map(row=>{
    const employee=row.employee;
    return `<tr class="${row.errors.length?'import-invalid':''}"><td>${number(row.rowNumber)}</td><td><span class="code">${escapeHtml(employee.personnelCode)}</span></td><td>${escapeHtml(`${employee.firstName||''} ${employee.lastName||''}`)}</td><td>${escapeHtml(educationLevels[employee.educationLevel]||employee.educationLevel||'ثبت نشده')}</td><td>${jalaliDateCell(employee.startDate)}</td><td class="import-description">${escapeHtml(employee.description||'—')}</td><td class="import-row-status">${row.errors.length?`<ul>${row.errors.map(error=>`<li>${escapeHtml(error)}</li>`).join('')}</ul>`:'<span class="badge">آمادهٔ ثبت</span>'}</td></tr>`;
  }).join('')}</tbody></table></div>`;
}

async function uploadEmployeeFile(file, commit) {
  const data=new FormData();data.append('file',file);
  let response;
  try { response=await auth.request(`/api/employees/import${commit?'':'/preview'}`,{method:'POST',body:data,headers:{Accept:'application/json'}}); }
  catch { throw new Error(commit?'ارتباط قطع شد و نتیجهٔ ثبت مشخص نیست. فهرست کارمندان را تازه‌سازی کنید و سپس فایل را دوباره بررسی کنید.':'ارتباط با سرور برقرار نشد. دوباره تلاش کنید.'); }
  const report=await response.json().catch(()=>({message:'پاسخ سرور قابل خواندن نیست. دوباره فایل را بررسی کنید.'}));
  if(!response.ok&&!(response.status===422&&Array.isArray(report.rows)))throw new Error(report.message||'بررسی فایل انجام نشد.');
  if(!Array.isArray(report.rows))throw new Error(report.message||'پاسخ سرور معتبر نیست.');
  return report;
}

async function processEmployeeImport(commit) {
  if(state.busy)return;
  const file=commit?employeeImportFile:$('employee-import-file').files[0];
  if(commit&&(!employeeImportReport||employeeImportReport.errorCount||!file))return;
  $('employee-import-error').hidden=true;
  if(!file||! /\.(xlsx|xls)$/i.test(file.name)||file.size>5*1024*1024){
    $('employee-import-error').textContent='یک فایل xlsx یا xls با حجم حداکثر ۵ مگابایت انتخاب کنید.';
    $('employee-import-error').hidden=false;return;
  }
  state.busy=true;
  $('employee-import-file').disabled=true;
  $('employee-import-preview').disabled=true;
  $('employee-import-save').disabled=true;
  $('employee-import-status').textContent=commit?'در حال ثبت کارمندان…':'در حال بررسی فایل…';
  try {
    employeeImportReport=await uploadEmployeeFile(file,commit);
    employeeImportFile=file;
    renderEmployeeImport();
    if(commit&&employeeImportReport.importedCount>0){
      const imported=employeeImportReport.importedCount;
      $('employee-import-dialog').close();
      toast(`${number(imported)} کارمند با موفقیت ثبت شد.`);
      state.search='';state.page=1;
      try{await loadData();render();}catch{showLoadError('کارمندان ثبت شدند، اما دریافت فهرست تازه انجام نشد. صفحه را تازه‌سازی کنید.');}
    }
  }catch(error){
    employeeImportReport=null;employeeImportFile=null;
    $('employee-import-results').replaceChildren();
    $('employee-import-status').textContent='';
    $('employee-import-filter-label').hidden=true;
    $('employee-import-error').textContent=error.message;
    $('employee-import-error').hidden=false;
  }finally{
    state.busy=false;
    $('employee-import-file').disabled=false;
    $('employee-import-preview').disabled=false;
    $('employee-import-save').disabled=!employeeImportReport||employeeImportReport.errorCount>0||employeeImportReport.importedCount>0;
  }
}

$('import-employees-button').onclick=()=>{
  if(state.busy)return;
  resetEmployeeImport();$('employee-import-file').value='';$('employee-import-dialog').showModal();
};
$('employee-import-file').addEventListener('change',resetEmployeeImport);
$('employee-import-errors-only').addEventListener('change',renderEmployeeImport);
$('employee-import-preview').onclick=()=>processEmployeeImport(false);
$('employee-import-save').onclick=()=>processEmployeeImport(true);
$('employee-import-dialog').addEventListener('close',()=>{resetEmployeeImport();$('employee-import-file').value='';});

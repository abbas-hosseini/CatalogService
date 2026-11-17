'use strict';

const employeeSearchPage={title:'جست‌وجوی کارکنان',description:'معیارهای موردنیاز شغل یا تخصص را انتخاب کنید و پروندهٔ افراد مطابق با آن‌ها را بررسی کنید.'};
const employeeSearchState={filters:{},response:null,busy:false,requestId:0};

function employeeSearchFields(){return [
  {name:'specialityId',label:'تخصص',ref:'specialities'},
  {name:'organizationalLevelId',label:'سطح سازمانی موردنیاز',ref:'organizational-levels'},
  {name:'gender',label:'جنسیت',choices:employeeGenders},
  {name:'ageMin',label:'حداقل سن',type:'integer',min:0,max:150},
  {name:'ageMax',label:'حداکثر سن',type:'integer',min:0,max:150},
  {name:'experienceMin',label:'حداقل سابقه',type:'decimal',min:0,max:300},
  {name:'experienceMax',label:'حداکثر سابقه',type:'decimal',min:0,max:300},
  {name:'marineExperienceMin',label:'حداقل سابقه دریایی (سال)',type:'decimal',min:0,max:100},
  {name:'educationLevel',label:'مقطع تحصیلی',choices:educationLevels},
  {name:'degree',label:'مدرک تحصیلی / رشته',type:'text'},
  {name:'assessmentScoreMin',label:'حداقل برآورد موفقیت',type:'decimal',min:0},
  {name:'assessmentScoreMax',label:'حداکثر برآورد موفقیت',type:'decimal',min:0}
];}

function invalidateEmployeeSearch(){
  employeeSearchState.requestId++;employeeSearchState.response=null;employeeSearchState.busy=false;
}

function renderEmployeeSearch(){
  const fields=employeeSearchFields(),filters=employeeSearchState.filters;
  const control=name=>{
    const f=fields.find(field=>field.name===name),value=filters[name]??'';
    const attrs=`id="candidate-${name}" name="${name}" aria-describedby="candidate-error-${name}"`;
    let input;
    if(f.ref||f.choices){
      const choices=f.ref?referenceRows(f.ref).map(row=>[String(row.id),`${row.title}${f.ref==='organizational-levels'?` — ${row.level==null?'عدد سطح تعیین نشده':`سطح ${number(row.level)}`}`:''}`]):Object.entries(f.choices);
      input=`<select ${attrs}><option value="">همه</option>${choices.map(([id,title])=>`<option value="${escapeHtml(id)}" ${String(value)===id?'selected':''}>${escapeHtml(title)}</option>`).join('')}</select>`;
    }else input=`<input ${attrs} type="text" ${f.type==='text'?'maxlength="200" placeholder="مثلاً مهندسی دریا"':`inputmode="${f.type==='integer'?'numeric':'decimal'}" dir="ltr" maxlength="${name.startsWith('assessmentScore')?103:12}" placeholder="بدون محدودیت"`} value="${escapeHtml(value)}">`;
    return `<div class="field"><label for="candidate-${name}">${f.label}</label>${input}<span class="field-error" id="candidate-error-${name}"></span></div>`;
  };
  const range=(title,first,last,hint)=>`<fieldset class="candidate-filter"><legend>${title}</legend><div class="candidate-range">${control(first)}${control(last)}</div><p class="field-help">${hint}</p></fieldset>`;
  $('page-content').innerHTML=`<form id="employee-search-form" class="data-panel candidate-search-form">
    <p class="candidate-intro">همهٔ فیلترها اختیاری‌اند و هم‌زمان اعمال می‌شوند. با خالی گذاشتن فیلترها، همهٔ کارکنان نمایش داده می‌شوند.</p>
    <div id="candidate-error" class="alert error" role="alert" hidden></div>
    <fieldset id="candidate-controls" class="candidate-controls" ${employeeSearchState.busy?'disabled':''}>
      <div class="candidate-filter-grid">
        <fieldset class="candidate-filter"><legend>تخصص و سطح سازمانی</legend>${control('specialityId')}${control('organizationalLevelId')}<p class="field-help">هم‌تراز یا یک رده پایین‌تر؛ مثلاً برای سطح ۳، سطوح ۳ و ۲ بررسی می‌شوند.</p></fieldset>
        ${range('بازهٔ سن','ageMin','ageMax','سال کامل، بر اساس تاریخ تولد تا امروز؛ دو سر بازه محاسبه می‌شوند.')}
        ${range('بازهٔ سابقهٔ محاسبه‌شده','experienceMin','experienceMax','سال کامل از شروع به کار + ۱٫۵ × سابقهٔ دریایی. برای محاسبه، هر دو مقدار باید ثبت شده باشند.')}
        <fieldset class="candidate-filter"><legend>اطلاعات فردی و دریایی</legend>${control('gender')}${control('marineExperienceMin')}</fieldset>
        <fieldset class="candidate-filter"><legend>تحصیلات</legend>${control('educationLevel')}${control('degree')}<p class="field-help">رشته در مقطع انتخاب‌شده جست‌وجو می‌شود؛ بدون انتخاب مقطع، همهٔ رشته‌های ثبت‌شده بررسی می‌شوند.</p></fieldset>
        ${range('بازهٔ برآورد موفقیت','assessmentScoreMin','assessmentScoreMax','همان نمرهٔ ارزیابی محاسبه‌شده است؛ سقف ۱۰۰ ندارد.')}
      </div>
      <div class="candidate-actions"><button type="submit" class="button primary" id="candidate-submit">${icon('search')}جست‌وجو</button><button type="button" class="button secondary" id="candidate-reset">پاک کردن فیلترها</button></div>
    </fieldset>
  </form><section id="candidate-results" class="candidate-results" aria-label="نتایج جست‌وجوی کارکنان" aria-live="polite"></section>`;
  $('employee-search-form').addEventListener('submit',event=>{event.preventDefault();searchEmployees();});
  $('candidate-controls').addEventListener('input',()=>{
    for(const field of fields)employeeSearchState.filters[field.name]=$(`candidate-${field.name}`).value;
    employeeSearchState.response=null;renderEmployeeSearchResults();
  });
  $('candidate-reset').onclick=()=>{employeeSearchState.filters={};invalidateEmployeeSearch();renderEmployeeSearch();$('candidate-specialityId').focus();};
  renderEmployeeSearchResults();
}

function candidateFieldError(name,message){
  const input=$(`candidate-${name}`);if(!input)return;
  input.setAttribute('aria-invalid','true');$(`candidate-error-${name}`).textContent=message;
}

async function searchEmployees(page=1){
  if(employeeSearchState.busy||!can('employee-search'))return;
  $('candidate-error').hidden=true;
  $('employee-search-form').querySelectorAll('[aria-invalid]').forEach(input=>input.removeAttribute('aria-invalid'));
  $('employee-search-form').querySelectorAll('.field-error').forEach(error=>error.textContent='');
  const parameters=new URLSearchParams();let invalid=false;
  for(const field of employeeSearchFields()){
    const raw=$(`candidate-${field.name}`).value.trim();employeeSearchState.filters[field.name]=raw;
    if(!raw)continue;
    let value=raw;
    if(['integer','decimal'].includes(field.type)){
      value=normalizeDigits(raw);const n=Number(value);
      const valid=field.type==='integer'?/^\d+$/.test(value):/^\d+(\.\d{1,2})?$/.test(value);
      if(!valid||!Number.isFinite(n)||n<field.min||(field.max!=null&&n>field.max)){
        candidateFieldError(field.name,field.type==='integer'?'عدد صحیح بین ۰ تا ۱۵۰ وارد کنید.':field.max!=null?`عدد بین ۰ تا ${number(field.max)} با حداکثر دو رقم اعشار وارد کنید.`:'عدد صفر یا بزرگ‌تر با حداکثر دو رقم اعشار وارد کنید.');invalid=true;
      }
    }
    parameters.set(field.name,value);
  }
  for(const prefix of ['age','experience','assessmentScore']){
    const min=parameters.get(prefix+'Min'),max=parameters.get(prefix+'Max');
    if(min!=null&&max!=null&&Number(min)>Number(max)){
      candidateFieldError(prefix+'Max','حداکثر نباید از حداقل کمتر باشد.');invalid=true;
    }
  }
  if(invalid){employeeSearchState.response=null;renderEmployeeSearchResults();$('employee-search-form').querySelector('[aria-invalid]')?.focus();return;}
  parameters.set('page',page);parameters.set('pageSize','20');
  const requestId=++employeeSearchState.requestId;
  employeeSearchState.busy=true;employeeSearchState.response=null;$('candidate-controls').disabled=true;renderEmployeeSearchResults();
  try{
    const response=await api(`employees/search?${parameters}`);
    if(requestId!==employeeSearchState.requestId)return;
    employeeSearchState.response=response;
  }catch(error){
    if(requestId!==employeeSearchState.requestId)return;
    if(state.section==='employee-search'){
      $('candidate-error').textContent=error.message;$('candidate-error').hidden=false;
      for(const [name,message] of Object.entries(error.fields||{}))candidateFieldError(name,message);
    }
  }finally{
    if(requestId===employeeSearchState.requestId){
      employeeSearchState.busy=false;
      if(state.section==='employee-search'){$('candidate-controls').disabled=false;renderEmployeeSearchResults();}
    }
  }
}

function renderEmployeeSearchResults(){
  const panel=$('candidate-results');if(!panel)return;
  if(employeeSearchState.busy){panel.innerHTML='<div class="loading" role="status">در حال جست‌وجو…</div>';return;}
  const result=employeeSearchState.response;
  if(!result){panel.innerHTML='<div class="candidate-prompt">معیارها را تعیین کنید و دکمهٔ «جست‌وجو» را بزنید.</div>';return;}
  const summary=`<div class="section-heading"><h2>نتایج جست‌وجو</h2><span role="status">${number(result.total)} نفر · محاسبه تا ${jalaliDate(result.asOfDate)}</span></div>`;
  if(!result.rows.length){panel.innerHTML=summary+'<div class="data-panel empty-state"><h3>فردی مطابق این معیارها پیدا نشد</h3><p>فیلترها یا بازه‌ها را تغییر دهید. اطلاعات خالی با شرط‌های انتخاب‌شده تطبیق داده نمی‌شوند.</p></div>';return;}
  const pages=Math.max(1,Math.ceil(result.total/result.pageSize));
  const columns=[['personnelCode','کد پرسنلی'],['fullName','نام و نام خانوادگی'],['specialityId','تخصص','specialities'],['grade','سطح سازمانی','organizational-levels'],['age','سن (سال)'],['experienceYears','سابقهٔ محاسبه‌شده (سال)'],['gender','جنسیت'],['marineExperience','سابقه دریایی'],['educationLevel','مقطع تحصیلی'],['degrees','مدرک تحصیلی / رشته'],['assessmentScore','برآورد موفقیت']];
  const value=(column,row)=>{
    const key=column[0];
    if(['age','experienceYears'].includes(key))return row[key]==null?'—':number(row[key]);
    if(key==='degrees')return `<span class="candidate-degree">${['bachelorMajor','masterMajor','doctoralMajor'].filter(name=>row[name]).map(name=>`${{bachelorMajor:'کارشناسی',masterMajor:'ارشد',doctoralMajor:'دکتری'}[name]}: ${escapeHtml(row[name])}`).join('<br>')||'—'}</span>`;
    return cell(sections.employees,column,row);
  };
  panel.innerHTML=summary+`<div class="data-panel"><div class="table-wrap" tabindex="0" role="region" aria-label="جدول نتیجه‌ها"><table><thead><tr>${columns.map(column=>`<th scope="col">${column[1]}</th>`).join('')}<th scope="col">پرونده</th></tr></thead><tbody>${result.rows.map(row=>`<tr>${columns.map(column=>`<td>${value(column,row)}</td>`).join('')}<td><button type="button" class="text-button" data-candidate-profile="${row.id}">مشاهدهٔ پرونده</button></td></tr>`).join('')}</tbody></table></div><div class="pagination"><span>صفحهٔ ${number(result.page)} از ${number(pages)}</span><div class="pagination-buttons"><button type="button" class="button secondary" id="candidate-prev" ${result.page<=1?'disabled':''}>قبلی</button><button type="button" class="button secondary" id="candidate-next" ${result.page>=pages?'disabled':''}>بعدی</button></div></div></div>`;
  $('candidate-prev').onclick=()=>searchEmployees(result.page-1);$('candidate-next').onclick=()=>searchEmployees(result.page+1);
  panel.querySelectorAll('[data-candidate-profile]').forEach(button=>button.onclick=()=>{
    const row=result.rows.find(employee=>employee.id===Number(button.dataset.candidateProfile));if(!row)return;
    const existing=state.data.employees.findIndex(employee=>employee.id===row.id);
    if(existing>=0)state.data.employees[existing]=row;else state.data.employees.push(row);
    openProfile(row.id);
  });
}

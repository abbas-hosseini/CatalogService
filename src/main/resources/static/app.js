'use strict';

const paths = {
  dashboard: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  factors: '<path d="m12 3 3 5 6 1-4 5 1 7-6-3-6 3 1-7-4-5 6-1z"/>',
  jobs: '<rect x="8" y="3" width="8" height="5" rx="1"/><rect x="2" y="16" width="6" height="5" rx="1"/><rect x="16" y="16" width="6" height="5" rx="1"/><path d="M12 8v4M5 16v-4h14v4"/>',
  employees: '<circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 5a3 3 0 0 1 0 6m3 10v-3a6 6 0 0 0-2-4"/>',
  courses: '<path d="m2 8 10-5 10 5-10 5zM6 10v7c4 3 8 3 12 0v-7M22 8v8"/>',
  trainings: '<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 7h8M8 11h4m-3 5 2 2 4-4"/>',
  scores: '<path d="M4 21V13h4v8m2 0V8h4v13m2 0V3h4v18M2 21h20"/>',
  requirements: '<path d="M9 4H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2h-4"/><rect x="9" y="2" width="6" height="5" rx="1"/><path d="m8 14 3 3 5-6"/>',
  plus: '<path d="M12 5v14M5 12h14"/>', search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
  arrow: '<path d="M20 12H4m6-6-6 6 6 6"/>', info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7v1"/>',
  folder: '<path d="M3 7V5a2 2 0 0 1 2-2h5l2 3h7a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>'
};
const icon = name => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.folder}</svg>`;
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const number = value => new Intl.NumberFormat('fa-IR', {maximumFractionDigits:2}).format(value);
const date = value => value ? new Intl.DateTimeFormat('fa-IR', {year:'numeric',month:'short',day:'numeric'}).format(new Date(`${value}T12:00:00`)) : '—';
const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; };
const normalizeDigits = value => String(value).replace(/[۰-۹]/g,c=>c.charCodeAt(0)-1776).replace(/[٠-٩]/g,c=>c.charCodeAt(0)-1632).replace(/٫/g,'.');
const normalizeSearch = value => normalizeDigits(value).replace(/ي/g,'ی').replace(/ك/g,'ک').toLowerCase();
const field = (name,label,type='text',extra={}) => ({name,label,type,...extra});
const codeField = field('code','کد','text',{required:true,max:40,placeholder:'مثلاً C-001'});
const titleField = field('title','عنوان','text',{required:true,max:160});
const descriptionField = field('description','توضیحات','textarea',{max:2000});
const notesField = field('notes','یادداشت','textarea',{max:2000});
const refField = (name,label,ref,required=true) => field(name,label,'select',{ref,required});
const scoreField = (name,label,required=true) => field(name,label,'decimal',{required,min:0,max:100,placeholder:'عددی بین ۰ تا ۱۰۰'});
const dateField = (name,label) => field(name,label,'date',{required:true});

const sections = {
  factors: {title:'عوامل ارزشیابی',singular:'عامل ارزشیابی',description:'شایستگی‌ها و شاخص‌های ارزیابی را در یک ساختار سلسله‌مراتبی تعریف کنید.',tree:true,fields:[codeField,titleField,refField('parentId','عامل بالادست','factors',false),descriptionField],columns:[['code','کد عامل'],['title','عنوان عامل'],['parentId','عامل بالادست','factors'],['description','توضیحات']]},
  jobs: {title:'ساختار مشاغل',singular:'شغل',description:'مشاغل سازمان و ارتباط هر شغل با شغل بالادست را مدیریت کنید.',tree:true,fields:[codeField,titleField,refField('parentId','شغل بالادست','jobs',false),descriptionField],columns:[['code','کد شغل'],['title','عنوان شغل'],['parentId','شغل بالادست','jobs'],['description','توضیحات']]},
  employees: {title:'کارمندان',singular:'کارمند',description:'مشخصات کارکنان، شغل فعلی و پرونده شایستگی و آموزش آن‌ها را ثبت کنید.',fields:[field('personnelCode','کد پرسنلی','text',{required:true,max:40}),field('firstName','نام','text',{required:true,max:80}),field('lastName','نام خانوادگی','text',{required:true,max:80}),refField('jobId','شغل فعلی','jobs',false),descriptionField],columns:[['personnelCode','کد پرسنلی'],['fullName','نام و نام خانوادگی'],['jobId','شغل فعلی','jobs']]},
  courses: {title:'دوره‌های آموزشی',singular:'دوره آموزشی',description:'فهرست دوره‌ها، برگزارکنندگان و مدت آموزش را تعریف کنید.',fields:[codeField,titleField,field('provider','برگزارکننده','text',{max:160}),field('durationHours','مدت دوره (ساعت)','decimal',{required:true,min:0.01,max:10000}),descriptionField],columns:[['code','کد دوره'],['title','عنوان دوره'],['provider','برگزارکننده'],['durationHours','مدت (ساعت)']]},
  trainings: {title:'سوابق آموزشی',singular:'سابقه آموزشی',description:'دوره‌های گذرانده‌شده را همراه با تاریخ پایان و گواهی در پرونده کارمند ثبت کنید.',filter:'employeeId',filterRef:'employees',fields:[refField('employeeId','کارمند','employees'),refField('courseId','دوره آموزشی','courses'),dateField('completionDate','تاریخ پایان (میلادی)'),scoreField('score','نمره دوره',false),field('certificateNumber','شماره گواهی','text',{max:100}),notesField],columns:[['employeeId','کارمند','employees'],['courseId','دوره آموزشی','courses'],['completionDate','تاریخ پایان'],['score','نمره'],['certificateNumber','شماره گواهی']]},
  scores: {title:'نمرات شایستگی',singular:'نمره شایستگی',description:'نمره جاری هر کارمند را برای عوامل ارزشیابی، به‌صورت دستی از ۱۰۰ ثبت کنید.',filter:'employeeId',filterRef:'employees',fields:[refField('employeeId','کارمند','employees'),refField('factorId','عامل ارزشیابی','factors'),scoreField('score','نمره شایستگی'),dateField('assessedOn','تاریخ ارزشیابی (میلادی)'),notesField],columns:[['employeeId','کارمند','employees'],['factorId','عامل ارزشیابی','factors'],['score','نمره از ۱۰۰'],['assessedOn','تاریخ ارزشیابی']]},
  requirements: {title:'شرایط احراز شغل',singular:'شرط احراز',fields:[refField('factorId','عامل ارزشیابی','factors'),scoreField('minimumScore','حداقل نمره لازم'),notesField],columns:[['factorId','عامل ارزشیابی','factors'],['minimumScore','حداقل نمره از ۱۰۰'],['notes','یادداشت']]}
};
const state = {section:'dashboard',data:{},search:'',filter:'',page:1,pageSize:12,editing:null,deleting:null,jobId:null,ready:false,busy:false};
const $ = id => document.getElementById(id);
let toastTimer;

function toast(message) {
  $('toast').textContent=message; $('toast').hidden=false;
  clearTimeout(toastTimer); toastTimer=setTimeout(()=>$('toast').hidden=true,4500);
}
function label(resource,id) {
  const r=(state.data[resource]||[]).find(x=>x.id===Number(id));
  return r ? (resource==='employees' ? `${r.firstName} ${r.lastName}` : r.title) : '';
}
function recordLabel(section,r) {
  return section==='employees' ? `${r.firstName} ${r.lastName}` : r.title || `${label('employees',r.employeeId)||label('jobs',r.jobId)} / ${label('factors',r.factorId)||label('courses',r.courseId)}`;
}
async function api(resource, options={}) {
  let response;
  try { response=await fetch(`/api/${resource}`,{headers:{'Content-Type':'application/json'},...options}); }
  catch { throw new Error('ارتباط با سرور برقرار نشد. اتصال را بررسی و دوباره تلاش کنید.'); }
  if(response.status===204) return null;
  const data=await response.json().catch(()=>({message:'پاسخ سرور قابل خواندن نیست.'}));
  if(!response.ok) { const error=new Error(data.message||'ذخیره اطلاعات انجام نشد. دوباره تلاش کنید.'); error.fields=data.fields; throw error; }
  return data;
}
async function loadData() {
  const entries=await Promise.all(Object.keys(sections).map(async key=>[key,await api(key)]));
  state.data=Object.fromEntries(entries); state.ready=true; $('load-error').hidden=true;
}
function renderNavigation() {
  const link=(key,title)=>`<a href="#${key}" class="nav-link ${state.section===key?'active':''}" ${state.section===key?'aria-current="page"':''}>${icon(key)}<span>${title}</span></a>`;
  $('navigation').innerHTML=link('dashboard','نمای کلی')+'<div class="nav-heading">اطلاعات پایه</div>'+['factors','jobs','employees','courses'].map(k=>link(k,sections[k].title)).join('')+'<div class="nav-heading">پرونده کارکنان</div>'+['scores','trainings'].map(k=>link(k,sections[k].title)).join('');
}
function render() {
  if(state.ready && $('job-dialog').open) renderJobRequirements();
  renderNavigation();
  const config=sections[state.section];
  $('page-title').textContent=config?.title||'نمای کلی'; $('breadcrumb').textContent=config?.title||'نمای کلی';
  $('page-description').textContent=config?.description||'اطلاعات کارکنان و شرایط احراز مشاغل، در یک فضای یکپارچه.';
  $('eyebrow').textContent=config?'مدیریت اطلاعات سازمان':'سرمایه انسانی سازمان';
  $('add-button').hidden=!config||!state.ready;
  if(config) $('add-button').innerHTML=icon('plus')+`ثبت ${config.singular}`;
  if(!state.ready) {
    $('page-content').innerHTML='<div class="loading">دریافت اطلاعات انجام نشد. <button class="button secondary" id="retry-load">تلاش دوباره</button></div>';
    $('retry-load').onclick=initialize; return;
  }
  if(!config) return renderDashboard();
  $('page-content').innerHTML=`<section class="data-panel" aria-label="فهرست ${config.title}"><div class="toolbar"><label class="search-box">${icon('search')}<input id="search" type="search" placeholder="جست‌وجو در ${config.title}…" aria-label="جست‌وجو در ${config.title}" value="${escapeHtml(state.search)}"></label>${config.filter?`<select id="record-filter" aria-label="فیلتر ${config.filterRef==='employees'?'کارمند':'شغل'}"><option value="">${config.filterRef==='employees'?'همه کارمندان':'همه مشاغل'}</option>${options(config.filterRef,state.filter)}</select>`:''}<span class="record-count" id="record-count"></span></div><div id="table-content"></div></section>${config.tree?`<div class="guide-strip">${icon('info')}هر مورد می‌تواند چند زیرمجموعه داشته باشد. با خالی گذاشتن «بالادست»، یک مورد در سطح اصلی بسازید.</div>`:state.section==='scores'?`<div class="guide-strip">${icon('info')}برای هر کارمند و عامل، یک نمره جاری نگهداری می‌شود. برای تغییر نمره از «ویرایش» استفاده کنید.</div>`:''}`;
  $('search').addEventListener('input',e=>{state.search=e.target.value;state.page=1;renderTable();});
  $('record-filter')?.addEventListener('change',e=>{state.filter=e.target.value;state.page=1;renderTable();});
  renderTable();
}
function renderDashboard() {
  const count=key=>state.data[key].length;
  const steps=[
    ['factors','۰۱','تعریف عوامل و مشاغل','شاخص‌های ارزشیابی و جایگاه مشاغل را در ساختار سازمان مشخص کنید.','تعریف عوامل ارزشیابی'],
    ['employees','۰۲','تشکیل پرونده کارکنان','کارمندان را ثبت کنید و سوابق آموزشی آن‌ها را به پرونده اضافه کنید.','ثبت اطلاعات کارکنان'],
    ['jobs','۰۳','تکمیل شرایط احراز مشاغل','در فهرست مشاغل، شرایط احراز هر شغل و حداقل نمره عوامل آن را ثبت کنید.','مشاهده مشاغل و شرایط احراز']
  ];
  $('page-content').innerHTML=`<section class="hero"><div class="hero-copy"><div class="hero-label">شناخت امروز، توسعه فردا</div><h2>شایستگی‌ها را بشناسید؛<br>مسیر رشد را روشن کنید.</h2><p>عوامل ارزشیابی، ساختار مشاغل و پرونده کارکنان را تکمیل کنید تا پایه‌ای دقیق برای بررسی شرایط احراز داشته باشید.</p><a class="button" href="#employees">مدیریت پرونده کارکنان ${icon('arrow')}</a></div><div class="hero-art" aria-hidden="true"><span class="orbit"></span><span class="orbit inner"></span>${icon('factors')}</div></section><div class="stats-grid">${[['employees','کارمند'],['jobs','شغل سازمانی'],['factors','عامل ارزشیابی'],['courses','دوره آموزشی']].map(([key,title])=>`<a class="stat-card" href="#${key}"><div><div class="stat-label">${title}</div><div class="stat-value">${number(count(key))}</div></div><span class="stat-icon">${icon(key)}</span></a>`).join('')}</div><div class="section-heading"><h3>تکمیل اطلاعات، قدم به قدم</h3><span>از تعریف ساختار تا ثبت شایستگی</span></div><div class="setup-grid">${steps.map(([key,n,title,desc,action])=>`<article class="setup-card"><span class="step-number">${n}</span><h3>${title}</h3><p>${desc}</p><a class="text-button" href="#${key}">${action} ${icon('arrow')}</a></article>`).join('')}</div><div class="guide-strip">${icon('info')}<span>${number(count('scores'))} نمره شایستگی، ${number(count('requirements'))} شرط احراز و ${number(count('trainings'))} سابقه آموزشی ثبت شده است.</span></div>`;
}
function treeRows(resource) {
  const all=[...(state.data[resource]||[])].sort((a,b)=>a.title.localeCompare(b.title,'fa'));
  const result=[],visited=new Set(),children=new Map();
  for(const row of all) { const key=row.parentId??null;if(!children.has(key))children.set(key,[]);children.get(key).push(row); }
  const walk=(row,depth)=>{if(visited.has(row.id))return;visited.add(row.id);result.push({...row,depth});for(const child of children.get(row.id)||[])walk(child,depth+1);};
  for(const row of children.get(null)||[])walk(row,0);
  for(const row of all)if(!visited.has(row.id))walk(row,0);
  return result;
}
function cell(config,column,row) {
  const [key,,ref]=column,value=row[key];
  if(ref) { const text=label(ref,value);return text?escapeHtml(text):`<span class="badge gray">${key==='jobId'?'بدون شغل':key==='parentId'?'سطح اصلی':'—'}</span>`; }
  if(key==='fullName')return escapeHtml(`${row.firstName} ${row.lastName}`);
  if(key==='score'||key==='minimumScore')return value==null?'<span class="muted">—</span>':`<span class="score-cell"><strong>${number(value)}</strong><span class="score-track"><span class="score-fill" style="--score:${Number(value)}%"></span></span></span>`;
  if(key==='assessedOn'||key==='completionDate')return date(value);
  if(key==='durationHours')return number(value);
  if(key==='code'||key==='personnelCode')return `<span class="code">${escapeHtml(value)}</span>`;
  if(config.tree&&key==='title')return `<span class="tree-name" style="--depth:${row.depth||0}">${row.depth?'<span class="tree-line"></span>':''}${icon('folder')}${escapeHtml(value)}</span>`;
  if(key==='description'||key==='notes')return `<span class="muted" title="${escapeHtml(value)}">${escapeHtml((value||'—').slice(0,55))}${value?.length>55?'…':''}</span>`;
  return escapeHtml(value||'—');
}
function renderTable() {
  const key=state.section,config=sections[key];
  let rows=config.tree?treeRows(key):state.data[key];
  if(state.filter)rows=rows.filter(r=>String(r[config.filter])===state.filter);
  if(state.search.trim()) {
    const query=normalizeSearch(state.search.trim());
    rows=rows.filter(r=>normalizeSearch(config.columns.map(([k,,ref])=>ref?label(ref,r[k]):k==='fullName'?`${r.firstName} ${r.lastName}`:r[k]).join(' ')).includes(query));
  }
  $('record-count').textContent=`${number(rows.length)} رکورد`;
  if(!rows.length) {
    const filtered=Boolean(state.search||state.filter);
    $('table-content').innerHTML=`<div class="empty-state"><div class="empty-symbol">${icon(key)}</div><h3>${filtered?'نتیجه‌ای پیدا نشد':`هنوز ${config.singular} ثبت نشده است`}</h3><p>${filtered?'عبارت جست‌وجو یا فیلتر انتخاب‌شده را تغییر دهید.':`برای شروع، نخستین ${config.singular} را به سامانه اضافه کنید.`}</p><button class="button ${filtered?'secondary':'primary'}" id="empty-action">${filtered?'پاک کردن فیلترها':icon('plus')+`ثبت ${config.singular}`}</button></div>`;
    $('empty-action').onclick=()=>{if(filtered){state.search='';state.filter='';render();}else openForm();};return;
  }
  const pages=Math.ceil(rows.length/state.pageSize);state.page=Math.min(state.page,pages);
  const visible=rows.slice((state.page-1)*state.pageSize,state.page*state.pageSize);
  $('table-content').innerHTML=`<div class="table-wrap"><table><thead><tr>${config.columns.map(c=>`<th scope="col">${c[1]}</th>`).join('')}<th scope="col">عملیات</th></tr></thead><tbody>${visible.map(row=>`<tr>${config.columns.map(c=>`<td${c[0]==='title'||c[0]==='fullName'?' class="title-cell"':''}>${cell(config,c,row)}</td>`).join('')}<td><div class="row-actions">${key==='employees'?`<button class="text-button" data-profile="${row.id}">پرونده</button>`:''}${key==='jobs'?`<button class="text-button" data-job-requirements="${row.id}" aria-label="شرایط احراز ${escapeHtml(row.title)}">شرایط احراز</button>`:''}<button class="text-button" data-edit="${row.id}" aria-label="ویرایش ${escapeHtml(recordLabel(key,row))}">ویرایش</button><button class="text-button danger-text" data-delete="${row.id}" aria-label="حذف ${escapeHtml(recordLabel(key,row))}">حذف</button></div></td></tr>`).join('')}</tbody></table></div><div class="pagination"><span>صفحه ${number(state.page)} از ${number(pages)}</span><div class="pagination-buttons"><button class="button secondary" id="previous-page" ${state.page===1?'disabled':''}>قبلی</button><button class="button secondary" id="next-page" ${state.page===pages?'disabled':''}>بعدی</button></div></div>`;
  $('previous-page').onclick=()=>{state.page--;renderTable();};$('next-page').onclick=()=>{state.page++;renderTable();};
  $('table-content').querySelectorAll('[data-edit]').forEach(b=>b.onclick=()=>openForm(state.data[key].find(r=>r.id===Number(b.dataset.edit))));
  $('table-content').querySelectorAll('[data-delete]').forEach(b=>b.onclick=()=>openDelete(state.data[key].find(r=>r.id===Number(b.dataset.delete))));
  $('table-content').querySelectorAll('[data-profile]').forEach(b=>b.onclick=()=>openProfile(Number(b.dataset.profile)));
  $('table-content').querySelectorAll('[data-job-requirements]').forEach(b=>b.onclick=()=>openJobRequirements(Number(b.dataset.jobRequirements)));
}
function excludedDescendants(resource,id) {
  const excluded=new Set(id?[id]:[]);let changed=true;
  while(changed){changed=false;for(const r of state.data[resource]||[])if(excluded.has(r.parentId)&&!excluded.has(r.id)){excluded.add(r.id);changed=true;}}
  return excluded;
}
function options(resource,selected,excluded=new Set()) {
  const rows=sections[resource].tree?treeRows(resource):[...state.data[resource]].sort((a,b)=>recordLabel(resource,a).localeCompare(recordLabel(resource,b),'fa'));
  return rows.filter(r=>!excluded.has(r.id)).map(r=>`<option value="${r.id}" ${String(r.id)===String(selected)?'selected':''}>${escapeHtml(`${'— '.repeat(r.depth||0)}${recordLabel(resource,r)} (${r.code||r.personnelCode})`)}</option>`).join('');
}
function openForm(record=null,section=state.section,prefill={}) {
  if(state.busy)return;
  const jobId=section==='requirements' ? record?.jobId??prefill.jobId : null;
  if(section==='requirements' && !state.data.jobs.some(job=>job.id===jobId)) {
    toast('ابتدا شغل موردنظر را از فهرست مشاغل انتخاب کنید.');return;
  }
  const config=sections[section];state.editing={section,id:record?.id??null,jobId};
  const values=record||{...prefill};
  if(!record&&section===state.section&&config.filter&&state.filter&&!values[config.filter])values[config.filter]=Number(state.filter);
  $('dialog-title').textContent=`${record?'ویرایش':'ثبت'} ${config.singular}`;
  $('form-context').hidden=section!=='requirements';
  $('form-context').textContent=section==='requirements'?`شغل: ${label('jobs',jobId)}`:'';
  $('form-error').hidden=true;
  $('form-hint').textContent='فیلدهای ستاره‌دار الزامی هستند.'+(config.fields.some(f=>f.type==='date')?' تاریخ را به میلادی وارد کنید؛ در فهرست، تاریخ شمسی نمایش داده می‌شود.':'');
  $('form-fields').innerHTML=config.fields.map(f=>{
    const val=values[f.name]??(f.type==='date'?today():'');
    const attrs=`id="field-${f.name}" name="${f.name}" ${f.required?'required':''} aria-describedby="error-${f.name}"`;
    let input;
    if(f.type==='select')input=`<select ${attrs}><option value="">${f.required?'انتخاب کنید…':f.name==='parentId'?'بدون بالادست (سطح اصلی)':'بدون شغل'}</option>${options(f.ref,val,f.name==='parentId'?excludedDescendants(section,record?.id):new Set())}</select>`;
    else if(f.type==='textarea')input=`<textarea ${attrs} maxlength="${f.max}">${escapeHtml(val)}</textarea>`;
    else if(f.type==='decimal')input=`<input ${attrs} type="text" inputmode="decimal" dir="ltr" value="${escapeHtml(val)}" placeholder="${escapeHtml(f.placeholder||'مثلاً ۸')}" maxlength="12">`;
    else input=`<input ${attrs} type="${f.type}" value="${escapeHtml(val)}" ${f.max?`maxlength="${f.max}"`:''} ${f.type==='date'?`max="${today()}" dir="ltr"`:''} placeholder="${escapeHtml(f.placeholder||'')}">`;
    const needsReference=f.type==='select'&&f.required&&!state.data[f.ref].length;
    return `<div class="field ${f.type==='textarea'?'full':''}"><label for="field-${f.name}">${f.label}${f.required?'<span class="required">*</span>':''}</label>${input}${needsReference?`<span class="field-help">ابتدا در بخش «${sections[f.ref].title}» اطلاعات ثبت کنید.</span>`:''}<span class="field-error" id="error-${f.name}"></span></div>`;
  }).join('');
  $('save-button').textContent='ذخیره اطلاعات';$('save-button').disabled=false;$('record-dialog').showModal();
}
function fieldError(name,message) {
  const input=$(`field-${name}`);if(!input)return;
  input.setAttribute('aria-invalid','true');$(`error-${name}`).textContent=message;
}
async function saveForm(event) {
  event.preventDefault();if(state.busy)return;
  $('form-error').hidden=true;
  $('record-form').querySelectorAll('[aria-invalid]').forEach(i=>i.removeAttribute('aria-invalid'));
  $('record-form').querySelectorAll('.field-error').forEach(i=>i.textContent='');
  const {section,id,jobId}=state.editing,payload=section==='requirements'?{jobId}:{};let invalid=false;
  for(const f of sections[section].fields){
    const value=$(`field-${f.name}`).value.trim();
    if(f.required&&!value){fieldError(f.name,'تکمیل این فیلد الزامی است.');invalid=true;}
    if(!value)payload[f.name]=null;
    else if(f.type==='select')payload[f.name]=Number(value);
    else if(f.type==='decimal'){
      const normalized=normalizeDigits(value),n=Number(normalized);
      if(!/^\d+(\.\d{1,2})?$/.test(normalized)||!Number.isFinite(n)||n<f.min||n>f.max){fieldError(f.name,`عددی بین ${number(f.min)} و ${number(f.max)} با حداکثر دو رقم اعشار وارد کنید.`);invalid=true;}
      payload[f.name]=n;
    }else payload[f.name]=value;
  }
  if(invalid){$('record-form').querySelector('[aria-invalid]')?.focus();return;}
  state.busy=true;$('save-button').disabled=true;$('save-button').textContent='در حال ذخیره…';
  try{
    await api(section+(id?`/${id}`:''),{method:id?'PUT':'POST',body:JSON.stringify(payload)});
    $('record-dialog').close();toast('اطلاعات با موفقیت ذخیره شد.');
    try{await loadData();render();}catch{showLoadError('اطلاعات ذخیره شد، اما دریافت فهرست تازه انجام نشد. صفحه را دوباره بارگذاری کنید.');}
  }catch(error){
    $('form-error').textContent=error.message;$('form-error').hidden=false;
    for(const [name,message]of Object.entries(error.fields||{}))fieldError(name,message);
    $('record-form').querySelector('[aria-invalid]')?.focus();
  }finally{state.busy=false;$('save-button').disabled=false;$('save-button').textContent='ذخیره اطلاعات';}
}
function openDelete(record,section=state.section){
  if(state.busy)return;
  state.deleting={section,id:record.id};
  $('delete-description').textContent=`«${recordLabel(section,record)}» حذف شود؟`;
  $('delete-error').hidden=true;$('delete-dialog').showModal();
}
async function confirmDelete(){
  if(state.busy)return;state.busy=true;$('confirm-delete').disabled=true;
  const {section,id}=state.deleting;
  try{
    await api(`${section}/${id}`,{method:'DELETE'});$('delete-dialog').close();toast('رکورد حذف شد.');
    try{await loadData();render();}catch{showLoadError('رکورد حذف شد، اما دریافت فهرست تازه انجام نشد. صفحه را دوباره بارگذاری کنید.');}
  }catch(error){$('delete-error').textContent=error.message;$('delete-error').hidden=false;}
  finally{state.busy=false;$('confirm-delete').disabled=false;}
}
function openProfile(id){
  const employee=state.data.employees.find(e=>e.id===id);$('profile-title').textContent=`${employee.firstName} ${employee.lastName}`;
  const miniTable=key=>{
    const config=sections[key],rows=state.data[key].filter(r=>r.employeeId===id),columns=config.columns.filter(c=>c[0]!=='employeeId');
    return rows.length?`<div class="data-panel table-wrap"><table><thead><tr>${columns.map(c=>`<th>${c[1]}</th>`).join('')}</tr></thead><tbody>${rows.map(r=>`<tr>${columns.map(c=>`<td>${cell(config,c,r)}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`:'<p class="muted">هنوز اطلاعاتی ثبت نشده است.</p>';
  };
  $('profile-content').innerHTML=`<div class="profile-summary"><div><small>کد پرسنلی</small>${escapeHtml(employee.personnelCode)}</div><div><small>شغل فعلی</small>${escapeHtml(label('jobs',employee.jobId)||'بدون شغل')}</div><div><small>سوابق آموزشی</small>${number(state.data.trainings.filter(t=>t.employeeId===id).length)} دوره</div></div>${employee.description?`<p class="muted profile-note">${escapeHtml(employee.description)}</p>`:''}${['scores','trainings'].map(key=>`<section class="profile-section"><div class="section-heading"><h3>${sections[key].title}</h3><button class="button secondary" data-profile-add="${key}">${icon('plus')}ثبت ${sections[key].singular}</button></div>${miniTable(key)}</section>`).join('')}`;
  $('profile-content').querySelectorAll('[data-profile-add]').forEach(b=>b.onclick=()=>{$('profile-dialog').close();openForm(null,b.dataset.profileAdd,{employeeId:id});});
  $('profile-dialog').showModal();
}
function openJobRequirements(id){
  if(state.busy)return;
  state.jobId=id;
  if(!renderJobRequirements())return;
  $('job-dialog').showModal();
}
function renderJobRequirements(){
  const job=state.data.jobs.find(row=>row.id===state.jobId);
  if(!job){$('job-dialog').close();state.jobId=null;return false;}
  const config=sections.requirements,rows=state.data.requirements.filter(row=>row.jobId===job.id);
  $('job-title').textContent=job.title;
  $('job-error').hidden=true;
  const table=rows.length
    ? `<div class="data-panel table-wrap"><table><thead><tr>${config.columns.map(column=>`<th scope="col">${column[1]}</th>`).join('')}<th scope="col">عملیات</th></tr></thead><tbody>${rows.map(row=>`<tr>${config.columns.map(column=>`<td>${cell(config,column,row)}</td>`).join('')}<td><div class="row-actions"><button class="text-button" data-requirement-edit="${row.id}" aria-label="ویرایش شرط ${escapeHtml(label('factors',row.factorId))}">ویرایش</button><button class="text-button danger-text" data-requirement-delete="${row.id}" aria-label="حذف شرط ${escapeHtml(label('factors',row.factorId))}">حذف</button></div></td></tr>`).join('')}</tbody></table></div>`
    : `<div class="empty-state"><div class="empty-symbol">${icon('requirements')}</div><h3>هنوز شرط احرازی برای این شغل ثبت نشده است</h3><p>با «ثبت شرط احراز»، عامل ارزشیابی و حداقل نمره لازم را مشخص کنید.</p></div>`;
  $('job-content').innerHTML=`<div class="profile-summary"><div><small>کد شغل</small><span class="code">${escapeHtml(job.code)}</span></div><div><small>شغل بالادست</small>${escapeHtml(label('jobs',job.parentId)||'سطح اصلی')}</div><div><small>شرایط احراز</small>${number(rows.length)} عامل</div></div><section class="profile-section"><div class="section-heading"><h3>عوامل و حداقل نمرات لازم</h3><button type="button" class="button primary" id="add-job-requirement">${icon('plus')}ثبت شرط احراز</button></div>${table}</section>`;
  $('add-job-requirement').onclick=()=>openForm(null,'requirements',{jobId:job.id});
  $('job-content').querySelectorAll('[data-requirement-edit]').forEach(button=>button.onclick=()=>openForm(rows.find(row=>row.id===Number(button.dataset.requirementEdit)),'requirements'));
  $('job-content').querySelectorAll('[data-requirement-delete]').forEach(button=>button.onclick=()=>openDelete(rows.find(row=>row.id===Number(button.dataset.requirementDelete)),'requirements'));
  return true;
}
function showLoadError(message){
  $('load-error').textContent=message;$('load-error').hidden=false;
  if($('job-dialog').open){$('job-error').textContent=message;$('job-error').hidden=false;}
}
function navigate(){
  let key=location.hash.slice(1);
  if(key==='requirements'){key='jobs';history.replaceState(null,'','#jobs');}
  state.section=Object.hasOwn(sections,key)?key:'dashboard';
  state.search='';state.filter='';state.page=1;render();
}
async function initialize(){try{await loadData();navigate();}catch(error){showLoadError(error.message);render();}}
$('today').textContent=new Intl.DateTimeFormat('fa-IR',{weekday:'long',day:'numeric',month:'long',year:'numeric'}).format(new Date());
$('add-button').onclick=()=>openForm();$('record-form').addEventListener('submit',saveForm);$('confirm-delete').onclick=confirmDelete;
document.querySelectorAll('.close-dialog').forEach(b=>b.onclick=()=>{if(!state.busy)$('record-dialog').close();});
document.querySelectorAll('[data-close]').forEach(b=>b.onclick=()=>{if(!state.busy)$(b.dataset.close).close();});
document.querySelectorAll('dialog').forEach(d=>d.addEventListener('cancel',e=>{if(state.busy)e.preventDefault();}));
window.addEventListener('hashchange',navigate);
initialize();

'use strict';

const paths = {
  companies: '<path d="M4 21V3h12v18M16 9h4v12M2 21h20M8 7h4M8 11h4M8 15h4M9 21v-3h2v3"/>',
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
const jalaliDate = value => value ? String(value).replace(/-/g,'/').replace(/[0-9]/g,d=>'۰۱۲۳۴۵۶۷۸۹'[d]) : '—';
const jalaliDateCell = value => `<span class="jalali-date" dir="ltr">${escapeHtml(jalaliDate(value))}</span>`;
const normalizeSearch = value => normalizeDigits(value).replace(/ي/g,'ی').replace(/ك/g,'ک').toLowerCase();
const field = (name,label,type='text',extra={}) => ({name,label,type,...extra});
const codeField = field('code','کد','text',{required:true,max:40,placeholder:'مثلاً C-001'});
const titleField = field('title','عنوان','text',{required:true,max:160});
const descriptionField = field('description','توضیحات','textarea',{max:2000});
const notesField = field('notes','یادداشت','textarea',{max:2000});
const refField = (name,label,ref,required=true) => field(name,label,'select',{ref,required});
const scoreField = (name,label,required=true) => field(name,label,'decimal',{required,min:0,max:100,placeholder:'عددی بین ۰ تا ۱۰۰'});
const dateField = (name,label) => field(name,label,'date',{required:true});
const educationLevels = {
  BELOW_DIPLOMA:'زیر دیپلم', DIPLOMA:'دیپلم', ASSOCIATE:'فوق دیپلم', BACHELOR:'لیسانس', MASTER:'فوق لیسانس', DOCTORATE:'دکتری'
};
const educationLabel = value => Object.hasOwn(educationLevels,value) ? educationLevels[value] : 'ثبت نشده';

const sections = {
  factors: {title:'عوامل ارزشیابی',singular:'عامل ارزشیابی',description:'شایستگی‌ها و شاخص‌های ارزیابی را در یک ساختار سلسله‌مراتبی تعریف کنید.',tree:true,fields:[codeField,titleField,refField('parentId','عامل بالادست','factors',false),descriptionField],columns:[['code','کد عامل'],['title','عنوان عامل'],['parentId','عامل بالادست','factors'],['description','توضیحات']]},
  jobs: {title:'ساختار مشاغل',singular:'شغل',description:'مشاغل هر شرکت و ارتباط آن‌ها با شغل بالادست در همان شرکت را مدیریت کنید.',tree:true,filter:'companyId',filterRef:'companies',fields:[refField('companyId','شرکت','companies'),codeField,titleField,refField('parentId','شغل بالادست','jobs',false),descriptionField],columns:[['code','کد شغل'],['title','عنوان شغل'],['companyId','شرکت','companies'],['parentId','شغل بالادست','jobs'],['description','توضیحات']]},
  companies: {title:'شرکت‌ها',singular:'شرکت',description:'شرکت‌ها را تعریف کنید و سمت کارکنان در هر شرکت را از پروندهٔ کارمند ثبت کنید.',fields:[codeField,field('title','نام شرکت','text',{required:true,max:160}),descriptionField],columns:[['code','کد شرکت'],['title','نام شرکت'],['description','توضیحات']]},
  employees: {title:'کارمندان',singular:'کارمند',description:'مشخصات کارکنان را ثبت کنید؛ سمت‌های هر فرد در شرکت‌ها، شایستگی‌ها و آموزش‌ها در پروندهٔ او قرار دارند.',fields:[field('personnelCode','کد پرسنلی','text',{required:true,max:40}),field('firstName','نام','text',{required:true,max:80}),field('lastName','نام خانوادگی','text',{required:true,max:80}),field('educationLevel','مدرک تحصیلی','choice',{choices:educationLevels}),field('startDate','تاریخ شروع به کار (شمسی)','jalali'),descriptionField],columns:[['personnelCode','کد پرسنلی'],['fullName','نام و نام خانوادگی'],['positions','شرکت و سمت'],['educationLevel','مدرک تحصیلی'],['startDate','تاریخ شروع به کار (شمسی)']]},
  positions: {title:'سمت‌ها در شرکت‌ها',singular:'سمت',fields:[refField('companyId','شرکت','companies'),refField('jobId','سمت / شغل','jobs'),notesField],columns:[['companyId','شرکت','companies'],['jobId','سمت / شغل','jobs'],['notes','یادداشت']]},
  courses: {title:'دوره‌های آموزشی',singular:'دوره آموزشی',description:'فهرست دوره‌ها، برگزارکنندگان و مدت آموزش را تعریف کنید.',fields:[codeField,titleField,field('provider','برگزارکننده','text',{max:160}),field('durationHours','مدت دوره (ساعت)','decimal',{required:true,min:0.01,max:10000}),descriptionField],columns:[['code','کد دوره'],['title','عنوان دوره'],['provider','برگزارکننده'],['durationHours','مدت (ساعت)']]},
  trainings: {title:'سوابق آموزشی',singular:'سابقه آموزشی',description:'دوره‌های گذرانده‌شده را همراه با تاریخ پایان و گواهی در پرونده کارمند ثبت کنید.',filter:'employeeId',filterRef:'employees',fields:[refField('employeeId','کارمند','employees'),refField('courseId','دوره آموزشی','courses'),dateField('completionDate','تاریخ پایان (میلادی)'),scoreField('score','نمره دوره',false),field('certificateNumber','شماره گواهی','text',{max:100}),notesField],columns:[['employeeId','کارمند','employees'],['courseId','دوره آموزشی','courses'],['completionDate','تاریخ پایان'],['score','نمره'],['certificateNumber','شماره گواهی']]},
  scores: {title:'نمرات شایستگی',singular:'نمره شایستگی',description:'نمره جاری هر کارمند را برای عوامل ارزشیابی، به‌صورت دستی از ۱۰۰ ثبت کنید.',filter:'employeeId',filterRef:'employees',fields:[refField('employeeId','کارمند','employees'),refField('factorId','عامل ارزشیابی','factors'),scoreField('score','نمره شایستگی'),dateField('assessedOn','تاریخ ارزشیابی (میلادی)'),notesField],columns:[['employeeId','کارمند','employees'],['factorId','عامل ارزشیابی','factors'],['score','نمره از ۱۰۰'],['assessedOn','تاریخ ارزشیابی']]},
  requirements: {title:'شرایط احراز شغل',singular:'شرط احراز',fields:[refField('factorId','عامل ارزشیابی','factors'),scoreField('minimumScore','حداقل نمره لازم'),notesField],columns:[['factorId','عامل ارزشیابی','factors'],['minimumScore','حداقل نمره از ۱۰۰'],['notes','یادداشت']]}
};
const state = {section:'dashboard',data:{},search:'',filter:'',page:1,pageSize:12,editing:null,deleting:null,jobId:null,profileId:null,scoreSheet:null,ready:false,busy:false};
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
  if(section==='positions')return `${companyLabel(r.companyId)} / ${label('jobs',r.jobId)}`;
  return section==='employees' ? `${r.firstName} ${r.lastName}` : r.title || `${label('employees',r.employeeId)||label('jobs',r.jobId)} / ${label('factors',r.factorId)||label('courses',r.courseId)}`;
}
const companyLabel = id => label('companies',id)||'شرکت مشخص نشده';
const employeePositions = id => (state.data.positions||[]).filter(row=>row.employeeId===id);
const positionLabel = row => `${companyLabel(row.companyId)} — ${label('jobs',row.jobId)}`;
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
  $('navigation').innerHTML=link('dashboard','نمای کلی')+'<div class="nav-heading">اطلاعات پایه</div>'+['companies','factors','jobs','employees','courses'].map(k=>link(k,sections[k].title)).join('')+'<div class="nav-heading">پرونده کارکنان</div>'+['scores','trainings'].map(k=>link(k,sections[k].title)).join('');
}
function render() {
  if(state.ready && $('job-dialog').open) renderJobRequirements();
  if(state.ready && $('profile-dialog').open) openProfile(state.profileId);
  renderNavigation();
  const config=sections[state.section];
  $('page-title').textContent=config?.title||'نمای کلی'; $('breadcrumb').textContent=config?.title||'نمای کلی';
  $('page-description').textContent=config?.description||'اطلاعات کارکنان و شرایط احراز مشاغل، در یک فضای یکپارچه.';
  $('eyebrow').textContent=config?'مدیریت اطلاعات سازمان':'سرمایه انسانی سازمان';
  $('add-button').hidden=!config||!state.ready;
  $('import-employees-button').hidden=state.section!=='employees'||!state.ready;
  if(config) $('add-button').innerHTML=icon('plus')+(state.section==='scores'?'ورود جدولی نمرات':`ثبت ${config.singular}`);
  if(!state.ready) {
    $('page-content').innerHTML='<div class="loading">دریافت اطلاعات انجام نشد. <button class="button secondary" id="retry-load">تلاش دوباره</button></div>';
    $('retry-load').onclick=initialize; return;
  }
  if(!config) return renderDashboard();
  const filterTitle=config.filterRef==='companies'?'شرکت':config.filterRef==='employees'?'کارمند':'شغل';
  const filterAll=config.filterRef==='companies'?'همه شرکت‌ها':config.filterRef==='employees'?'همه کارمندان':'همه مشاغل';
  const pendingJobs=state.section==='jobs'&&state.data.jobs.some(job=>job.companyId==null);
  $('page-content').innerHTML=`${pendingJobs?`<div class="guide-strip">${icon('info')}شرکت بعضی مشاغل قبلی مشخص نشده است. با «ویرایش»، شرکت آن‌ها را از بالاترین شغل به زیرمجموعه‌ها تعیین کنید.</div>`:''}<section class="data-panel" aria-label="فهرست ${config.title}"><div class="toolbar"><label class="search-box">${icon('search')}<input id="search" type="search" placeholder="جست‌وجو در ${config.title}…" aria-label="جست‌وجو در ${config.title}" value="${escapeHtml(state.search)}"></label>${config.filter?`<select id="record-filter" aria-label="فیلتر ${filterTitle}"><option value="">${filterAll}</option>${options(config.filterRef,state.filter)}${pendingJobs?`<option value="unassigned" ${state.filter==='unassigned'?'selected':''}>شرکت مشخص نشده</option>`:''}</select>`:''}<span class="record-count" id="record-count"></span></div><div id="table-content"></div></section>${config.tree?`<div class="guide-strip">${icon('info')}هر مورد می‌تواند چند زیرمجموعه داشته باشد. با خالی گذاشتن «بالادست»، یک مورد در سطح اصلی بسازید.${state.section==='jobs'?' شغل و بالادست آن باید متعلق به یک شرکت باشند.':''}</div>`:state.section==='scores'?`<div class="guide-strip">${icon('info')}برای هر کارمند و عامل، یک نمره جاری نگهداری می‌شود. برای تغییر نمره از «ویرایش» استفاده کنید.</div>`:''}`;
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
  const all=[...(state.data[resource]||[])].sort((a,b)=>(resource==='jobs'?companyLabel(a.companyId).localeCompare(companyLabel(b.companyId),'fa'):0)||a.title.localeCompare(b.title,'fa'));
  const result=[],visited=new Set(),children=new Map();
  for(const row of all) { const key=row.parentId??null;if(!children.has(key))children.set(key,[]);children.get(key).push(row); }
  const walk=(row,depth)=>{if(visited.has(row.id))return;visited.add(row.id);result.push({...row,depth});for(const child of children.get(row.id)||[])walk(child,depth+1);};
  for(const row of children.get(null)||[])walk(row,0);
  for(const row of all)if(!visited.has(row.id))walk(row,0);
  return result;
}
function cell(config,column,row) {
  const [key,,ref]=column,value=row[key];
  if(key==='positions'){
    const positions=employeePositions(row.id);
    return positions.length?`<div class="employee-position-list">${positions.map(position=>`<span>${escapeHtml(positionLabel(position))}</span>`).join('')}</div>`:'<span class="badge gray">بدون سمت</span>';
  }
  if(key==='companyId'&&value==null)return '<span class="badge gray">شرکت مشخص نشده</span>';
  if(ref) { const text=label(ref,value);return text?escapeHtml(text):`<span class="badge gray">${key==='jobId'?'بدون شغل':key==='parentId'?'سطح اصلی':'—'}</span>`; }
  if(key==='fullName')return escapeHtml(`${row.firstName} ${row.lastName}`);
  if(key==='score'||key==='minimumScore')return value==null?'<span class="muted">—</span>':`<span class="score-cell"><strong>${number(value)}</strong><span class="score-track"><span class="score-fill" style="--score:${Number(value)}%"></span></span></span>`;
  if(key==='startDate')return jalaliDateCell(value);
  if(key==='assessedOn'||key==='completionDate')return date(value);
  if(key==='educationLevel')return escapeHtml(educationLabel(value));
  if(key==='durationHours')return number(value);
  if(key==='code'||key==='personnelCode')return `<span class="code">${escapeHtml(value)}</span>`;
  if(config.tree&&key==='title')return `<span class="tree-name" style="--depth:${row.depth||0}">${row.depth?'<span class="tree-line"></span>':''}${icon('folder')}${escapeHtml(value)}</span>`;
  if(key==='description'||key==='notes')return `<span class="muted" title="${escapeHtml(value)}">${escapeHtml((value||'—').slice(0,55))}${value?.length>55?'…':''}</span>`;
  return escapeHtml(value||'—');
}
function renderTable() {
  const key=state.section,config=sections[key];
  let rows=config.tree?treeRows(key):state.data[key];
  if(state.filter)rows=rows.filter(r=>state.filter==='unassigned'?r[config.filter]==null:String(r[config.filter])===state.filter);
  if(state.search.trim()) {
    const query=normalizeSearch(state.search.trim());
    rows=rows.filter(r=>normalizeSearch(config.columns.map(([k,,ref])=>ref?label(ref,r[k]):k==='positions'?employeePositions(r.id).map(positionLabel).join(' '):k==='fullName'?`${r.firstName} ${r.lastName}`:k==='educationLevel'?educationLabel(r[k]):k==='startDate'?`${r[k]||''} ${jalaliDate(r[k])}`:r[k]).join(' ')).includes(query));
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
function options(resource,selected,excluded=new Set(),include=()=>true) {
  const rows=sections[resource].tree?treeRows(resource):[...state.data[resource]].sort((a,b)=>recordLabel(resource,a).localeCompare(recordLabel(resource,b),'fa'));
  return rows.filter(r=>!excluded.has(r.id)&&include(r)).map(r=>`<option value="${r.id}" ${String(r.id)===String(selected)?'selected':''}>${escapeHtml(`${'— '.repeat(r.depth||0)}${recordLabel(resource,r)} (${r.code||r.personnelCode})`)}</option>`).join('');
}
function openForm(record=null,section=state.section,prefill={}) {
  if(state.busy)return;
  if(section==='scores')return openScoreSheet(record?.employeeId??prefill.employeeId??(state.section==='scores'&&state.filter?Number(state.filter):null));
  const jobId=section==='requirements' ? record?.jobId??prefill.jobId : null;
  const employeeId=section==='positions' ? record?.employeeId??prefill.employeeId : null;
  if(section==='positions' && !state.data.employees.some(employee=>employee.id===employeeId)) {
    toast('ابتدا پروندهٔ کارمند موردنظر را باز کنید.');return;
  }
  if(section==='requirements' && !state.data.jobs.some(job=>job.id===jobId)) {
    toast('ابتدا شغل موردنظر را از فهرست مشاغل انتخاب کنید.');return;
  }
  const config=sections[section];state.editing={section,id:record?.id??null,jobId,employeeId};
  const values=record||{...prefill};
  if(!record&&section===state.section&&config.filter&&state.filter&&state.filter!=='unassigned'&&!values[config.filter])values[config.filter]=Number(state.filter);
  $('dialog-title').textContent=`${record?'ویرایش':'ثبت'} ${config.singular}`;
  $('form-context').hidden=!['requirements','positions'].includes(section);
  $('form-context').textContent=section==='requirements'?`شغل: ${label('jobs',jobId)}`:section==='positions'?`کارمند: ${label('employees',employeeId)}`:'';
  $('form-error').hidden=true;
  $('form-hint').textContent='فیلدهای ستاره‌دار الزامی هستند.'+(config.fields.some(f=>f.type==='date')?' تاریخ را به میلادی وارد کنید؛ در فهرست، تاریخ شمسی نمایش داده می‌شود.':'');
  if(section==='employees')$('form-hint').textContent+=' سمت‌ها در شرکت‌ها را پس از ذخیره، از پروندهٔ کارمند ثبت و ویرایش کنید.';
  if(section==='jobs')$('form-hint').textContent+=' شرکت را انتخاب کنید؛ شغل بالادست باید از همان شرکت باشد.';
  if(section==='jobs'&&record&&record.companyId==null&&record.parentId!=null)$('form-hint').textContent+=' برای حفظ سلسله‌مراتب، ابتدا شرکت شغل بالادست را مشخص کنید.';
  if(section==='positions')$('form-hint').textContent+=' پس از انتخاب شرکت، فقط مشاغل همان شرکت قابل انتخاب‌اند.';
  $('form-fields').innerHTML=config.fields.map(f=>{
    const val=values[f.name]??(f.type==='date'&&f.required?today():'');
    const attrs=`id="field-${f.name}" name="${f.name}" ${f.required?'required':''} aria-describedby="error-${f.name}${f.type==='jalali'?` help-${f.name}`:''}"`;
    let input;
    if(f.type==='select')input=`<select ${attrs}><option value="">${f.required?'انتخاب کنید…':f.name==='parentId'?'بدون بالادست (سطح اصلی)':'بدون شغل'}</option>${options(f.ref,val,f.name==='parentId'?excludedDescendants(section,record?.id):new Set())}</select>`;
    else if(f.type==='choice')input=`<select ${attrs}><option value="">ثبت نشده</option>${Object.entries(f.choices).map(([value,title])=>`<option value="${escapeHtml(value)}" ${value===val?'selected':''}>${escapeHtml(title)}</option>`).join('')}</select>`;
    else if(f.type==='textarea')input=`<textarea ${attrs} maxlength="${f.max}">${escapeHtml(val)}</textarea>`;
    else if(f.type==='jalali')input=`<input ${attrs} type="text" dir="ltr" value="${escapeHtml(val?jalaliDate(val):'')}" placeholder="۱۴۰۳/۰۱/۰۱" maxlength="10"><span class="field-help" id="help-${f.name}">سال/ماه/روز شمسی؛ ارقام فارسی یا انگلیسی پذیرفته می‌شوند.</span>`;
    else if(f.type==='decimal')input=`<input ${attrs} type="text" inputmode="decimal" dir="ltr" value="${escapeHtml(val)}" placeholder="${escapeHtml(f.placeholder||'مثلاً ۸')}" maxlength="12">`;
    else input=`<input ${attrs} type="${f.type}" value="${escapeHtml(val)}" ${f.max?`maxlength="${f.max}"`:''} ${f.type==='date'?`${f.allowFuture?'':`max="${today()}"`} dir="ltr"`:''} placeholder="${escapeHtml(f.placeholder||'')}">`;
    const needsReference=f.type==='select'&&f.required&&!state.data[f.ref].length;
    return `<div class="field ${f.type==='textarea'?'full':''}"><label for="field-${f.name}">${f.label}${f.required?'<span class="required">*</span>':''}</label>${input}${needsReference?`<span class="field-help">ابتدا در بخش «${sections[f.ref].title}» اطلاعات ثبت کنید.</span>`:''}<span class="field-error" id="error-${f.name}"></span></div>`;
  }).join('');
  if(section==='jobs'||section==='positions'){
    const target=section==='jobs'?'parentId':'jobId';
    const updateJobs=()=>{
      const companyId=Number($('field-companyId').value)||null,select=$(`field-${target}`),selected=select.value;
      const excluded=section==='jobs'?excludedDescendants('jobs',record?.id):new Set();
      select.innerHTML=`<option value="">${!companyId?'ابتدا شرکت را انتخاب کنید':target==='parentId'?'بدون بالادست (سطح اصلی)':'انتخاب شغل…'}</option>`+options('jobs',selected,excluded,job=>companyId!=null&&job.companyId===companyId);
      // Retain an existing parent visibly instead of silently detaching a legacy hierarchy.
      if(target==='parentId'&&selected&&!Array.from(select.options).some(option=>option.value===selected)){
        select.innerHTML+=`<option value="${escapeHtml(selected)}" selected>${escapeHtml(label('jobs',selected))} — شرکت نامشخص یا متفاوت؛ ابتدا بالادست را اصلاح کنید</option>`;
      }
      select.disabled=!companyId;
    };
    $('field-companyId').addEventListener('change',updateJobs);updateJobs();
  }
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
  const {section,id,jobId,employeeId}=state.editing,payload=section==='requirements'?{jobId}:section==='positions'?{employeeId}:{};let invalid=false;
  for(const f of sections[section].fields){
    const value=$(`field-${f.name}`).value.trim();
    if(f.required&&!value){fieldError(f.name,'تکمیل این فیلد الزامی است.');invalid=true;}
    if(!value)payload[f.name]=null;
    else if(f.type==='select')payload[f.name]=Number(value);
    else if(f.type==='jalali'){
      const parts=normalizeDigits(value).replace(/\//g,'-').match(/^([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})$/);
      if(!parts||Number(parts[1])<1||Number(parts[1])>1999||Number(parts[2])<1||Number(parts[2])>12||Number(parts[3])<1||Number(parts[3])>(Number(parts[2])<=6?31:30)){
        fieldError(f.name,'تاریخ شمسی را به صورت ۱۴۰۳/۰۱/۰۱ وارد کنید.');invalid=true;
      }else payload[f.name]=`${parts[1]}-${parts[2].padStart(2,'0')}-${parts[3].padStart(2,'0')}`;
    }
    else if(f.type==='decimal'){
      const normalized=normalizeDigits(value),n=Number(normalized);
      if(!/^\d+(\.\d{1,2})?$/.test(normalized)||!Number.isFinite(n)||n<f.min||n>f.max){fieldError(f.name,`عددی بین ${number(f.min)} و ${number(f.max)} با حداکثر دو رقم اعشار وارد کنید.`);invalid=true;}
      payload[f.name]=n;
    }else payload[f.name]=value;
  }
  if(invalid){$('record-form').querySelector('[aria-invalid]')?.focus();return;}
  if(section==='jobs'&&payload.parentId&&state.data.jobs.find(job=>job.id===payload.parentId)?.companyId!==payload.companyId){
    fieldError('parentId','شغل بالادست باید متعلق به همین شرکت باشد. ابتدا شرکت بالادست را مشخص کنید یا بالادست دیگری انتخاب کنید.');$('field-parentId').focus();return;
  }
  if(section==='positions'&&state.data.jobs.find(job=>job.id===payload.jobId)?.companyId!==payload.companyId){
    fieldError('jobId','یک شغل از شرکت انتخاب‌شده تعیین کنید.');$('field-jobId').focus();return;
  }
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
  const employee=state.data.employees.find(e=>e.id===id);
  if(!employee){$('profile-dialog').close();return;}
  state.profileId=id;$('profile-title').textContent=`${employee.firstName} ${employee.lastName}`;
  const positions=employeePositions(id),positionColumns=sections.positions.columns;
  const positionTable=positions.length?`<div class="data-panel table-wrap"><table><thead><tr>${positionColumns.map(c=>`<th scope="col">${c[1]}</th>`).join('')}<th scope="col">عملیات</th></tr></thead><tbody>${positions.map(row=>`<tr>${positionColumns.map(c=>`<td>${cell(sections.positions,c,row)}</td>`).join('')}<td><div class="row-actions"><button class="text-button" data-position-edit="${row.id}" aria-label="ویرایش سمت ${escapeHtml(positionLabel(row))}">${row.companyId==null?'تعیین شرکت':'ویرایش'}</button><button class="text-button danger-text" data-position-delete="${row.id}" aria-label="حذف سمت ${escapeHtml(positionLabel(row))}">حذف</button></div></td></tr>`).join('')}</tbody></table></div>`:'<p class="muted">هنوز سمتی ثبت نشده است. شرکت و شغل این کارمند را با «ثبت سمت» مشخص کنید.</p>';
  const miniTable=key=>{
    const config=sections[key],rows=state.data[key].filter(r=>r.employeeId===id),columns=config.columns.filter(c=>c[0]!=='employeeId');
    return rows.length?`<div class="data-panel table-wrap"><table><thead><tr>${columns.map(c=>`<th>${c[1]}</th>`).join('')}</tr></thead><tbody>${rows.map(r=>`<tr>${columns.map(c=>`<td>${cell(config,c,r)}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`:'<p class="muted">هنوز اطلاعاتی ثبت نشده است.</p>';
  };
  $('profile-content').innerHTML=`<div class="profile-summary"><div><small>کد پرسنلی</small>${escapeHtml(employee.personnelCode)}</div><div><small>سمت‌ها</small>${number(positions.length)} سمت</div><div><small>مدرک تحصیلی</small>${escapeHtml(educationLabel(employee.educationLevel))}</div><div><small>تاریخ شروع به کار (شمسی)</small>${jalaliDateCell(employee.startDate)}</div><div><small>سوابق آموزشی</small>${number(state.data.trainings.filter(t=>t.employeeId===id).length)} دوره</div></div>${employee.description?`<p class="muted profile-note">${escapeHtml(employee.description)}</p>`:''}<section class="profile-section"><div class="section-heading"><h3>سمت‌ها در شرکت‌ها</h3><button type="button" class="button secondary" id="add-employee-position">${icon('plus')}ثبت سمت</button></div>${positions.some(row=>row.companyId==null)?'<p class="form-hint">برای سمت‌های قبلی که شرکت آن‌ها مشخص نیست، از «تعیین شرکت» استفاده کنید.</p>':''}${positionTable}</section>${['scores','trainings'].map(key=>`<section class="profile-section"><div class="section-heading"><h3>${sections[key].title}</h3><button class="button secondary" data-profile-add="${key}">${icon('plus')}ثبت ${sections[key].singular}</button></div>${miniTable(key)}</section>`).join('')}`;
  $('add-employee-position').onclick=()=>openForm(null,'positions',{employeeId:id});
  $('profile-content').querySelectorAll('[data-position-edit]').forEach(b=>b.onclick=()=>openForm(positions.find(row=>row.id===Number(b.dataset.positionEdit)),'positions'));
  $('profile-content').querySelectorAll('[data-position-delete]').forEach(b=>b.onclick=()=>openDelete(positions.find(row=>row.id===Number(b.dataset.positionDelete)),'positions'));
  $('profile-content').querySelectorAll('[data-profile-add]').forEach(b=>{
    if(b.dataset.profileAdd==='scores'){
      b.textContent='ورود و ویرایش نمرات';b.onclick=()=>openScoreSheet(id,true);
    }else b.onclick=()=>{$('profile-dialog').close();openForm(null,b.dataset.profileAdd,{employeeId:id});};
  });
  if(!$('profile-dialog').open)$('profile-dialog').showModal();
}

function openScoreSheet(employeeId=null,locked=false){
  if(state.busy)return;
  state.scoreSheet={employeeId:null,locked,rows:[]};
  $('score-sheet-employee').innerHTML='<option value="">انتخاب کارمند…</option>'+options('employees',employeeId);
  $('score-sheet-form').querySelectorAll('input,select,button').forEach(input=>input.disabled=false);
  $('score-sheet-employee').disabled=locked;
  selectScoreSheetEmployee(employeeId);
  $('score-sheet-dialog').showModal();
}
function selectScoreSheetEmployee(employeeId){
  const sheet=state.scoreSheet;
  sheet.employeeId=state.data.employees.some(row=>row.id===Number(employeeId))?Number(employeeId):null;
  $('score-sheet-employee').value=sheet.employeeId??'';
  const scores=new Map(state.data.scores.filter(row=>row.employeeId===sheet.employeeId).map(row=>[row.factorId,row.score]));
  sheet.rows=sheet.employeeId?treeRows('factors').map(factor=>({...factor,original:scores.get(factor.id)??null,value:String(scores.get(factor.id)??'')})):[];
  $('score-sheet-error').hidden=true;$('score-sheet-search').value='';
  $('score-sheet-search').disabled=!sheet.rows.length;
  if(!sheet.rows.length){
    const message=!state.data.employees.length?'ابتدا یک کارمند ثبت کنید.':!sheet.employeeId?'برای مشاهدهٔ همهٔ شایستگی‌ها، کارمند را انتخاب کنید.':'هنوز عامل ارزشیابی تعریف نشده است. ابتدا عوامل را ثبت کنید.';
    $('score-sheet-table').innerHTML=`<div class="empty-state"><p>${message}</p></div>`;
  }else{
    $('score-sheet-table').innerHTML=`<div class="table-wrap"><table class="score-sheet-table"><thead><tr><th scope="col">کد</th><th scope="col">شایستگی</th><th scope="col">نمره از ۱۰۰</th></tr></thead><tbody>${sheet.rows.map(row=>`<tr data-score-row="${row.id}"><td><span class="code">${escapeHtml(row.code)}</span></td><td><label class="tree-name" style="--depth:${row.depth}" for="sheet-score-${row.id}">${row.depth?'<span class="tree-line"></span>':''}${escapeHtml(row.title)}</label></td><td><div class="field"><input id="sheet-score-${row.id}" data-score-factor="${row.id}" type="text" inputmode="decimal" dir="ltr" maxlength="12" autocomplete="off" value="${escapeHtml(row.value)}" placeholder="—" aria-label="نمره ${escapeHtml(row.title)} (${escapeHtml(row.code)})" aria-describedby="score-sheet-hint sheet-error-${row.id}"><span class="field-error" id="sheet-error-${row.id}"></span></div></td></tr>`).join('')}</tbody></table></div><p id="score-sheet-no-results" class="score-sheet-note" hidden>شایستگی مطابق جست‌وجو پیدا نشد.</p>`;
    $('score-sheet-table').querySelectorAll('[data-score-factor]').forEach(input=>{
      const row=sheet.rows.find(item=>item.id===Number(input.dataset.scoreFactor));
      input.addEventListener('input',()=>{
        row.value=input.value;input.removeAttribute('aria-invalid');$(`sheet-error-${row.id}`).textContent='';
        input.closest('tr').classList.toggle('score-changed',scoreRowChanged(row));updateScoreSheetStatus();
      });
      input.addEventListener('keydown',event=>{
        if(event.isComposing||!['Enter','ArrowDown','ArrowUp'].includes(event.key))return;
        event.preventDefault();
        const inputs=[...$('score-sheet-table').querySelectorAll('tr:not([hidden]) [data-score-factor]')];
        const offset=event.key==='ArrowUp'||(event.key==='Enter'&&event.shiftKey)?-1:1;
        const next=inputs[inputs.indexOf(input)+offset];
        if(next){next.focus();next.select();}else if(offset>0)$('score-sheet-save').focus();
      });
    });
  }
  updateScoreSheetStatus();
}
function scoreRowChanged(row){
  const value=normalizeDigits(row.value).trim();
  if(value!==''&&!/^\d+(\.\d{1,2})?$/.test(value))return true;
  return value===''?row.original!==null:row.original===null||Number(value)!==row.original;
}
function updateScoreSheetStatus(){
  const sheet=state.scoreSheet;if(!sheet)return;
  const filled=sheet.rows.filter(row=>row.value.trim()!=='').length,changed=sheet.rows.filter(scoreRowChanged).length;
  $('score-sheet-progress').textContent=sheet.employeeId?`${number(filled)} از ${number(sheet.rows.length)} شایستگی دارای نمره${changed?` · ${number(changed)} تغییر ذخیره‌نشده`:''}`:'';
  $('score-sheet-save').disabled=state.busy||!sheet.employeeId||!changed;
  $('score-sheet-save').textContent=state.busy?'در حال ذخیره…':'ذخیره نمرات';
}
function filterScoreSheet(){
  const query=normalizeSearch($('score-sheet-search').value.trim());let visible=0;
  for(const row of state.scoreSheet?.rows||[]){
    const element=$(`sheet-score-${row.id}`).closest('tr');
    element.hidden=!normalizeSearch(`${row.code} ${row.title}`).includes(query);
    if(!element.hidden)visible++;
  }
  if($('score-sheet-no-results'))$('score-sheet-no-results').hidden=visible>0;
}
function scoreSheetCanDiscard(){
  return !state.busy&&(!state.scoreSheet?.rows.some(scoreRowChanged)||window.confirm('تغییرات نمرات هنوز ذخیره نشده‌اند. تغییرات کنار گذاشته شوند؟'));
}
function scoreSheetFieldError(factorId,message){
  const input=$(`sheet-score-${factorId}`);if(!input)return;
  input.setAttribute('aria-invalid','true');$(`sheet-error-${factorId}`).textContent=message;
}
function focusScoreSheetError(){
  const input=$('score-sheet-form').querySelector('[aria-invalid]');
  if(input){$('score-sheet-search').value='';filterScoreSheet();input.focus();}
}
async function saveScoreSheet(event){
  event.preventDefault();if(state.busy||!state.scoreSheet?.employeeId)return;
  const sheet=state.scoreSheet,changes=sheet.rows.filter(scoreRowChanged);let invalid=false;
  $('score-sheet-error').hidden=true;
  $('score-sheet-form').querySelectorAll('[aria-invalid]').forEach(input=>input.removeAttribute('aria-invalid'));
  $('score-sheet-form').querySelectorAll('.field-error').forEach(element=>element.textContent='');
  // Validate all rows, including rows hidden by the search filter.
  for(const row of sheet.rows){
    const value=normalizeDigits(row.value).trim();
    if(value!==''&&(!/^\d+(\.\d{1,2})?$/.test(value)||!Number.isFinite(Number(value))||Number(value)>100)){
      scoreSheetFieldError(row.id,'عددی بین ۰ تا ۱۰۰ با حداکثر دو رقم اعشار وارد کنید.');invalid=true;
    }
  }
  if(invalid){focusScoreSheetError();return;}
  if(!changes.length)return;
  const scores=changes.map(row=>({factorId:row.id,score:row.value.trim()===''?null:Number(normalizeDigits(row.value).trim())}));
  state.busy=true;
  $('score-sheet-form').querySelectorAll('input,select,button').forEach(input=>input.disabled=true);updateScoreSheetStatus();
  try{
    const saved=await api(`employees/${sheet.employeeId}/scores`,{method:'PUT',body:JSON.stringify({scores})});
    state.data.scores=[...state.data.scores.filter(row=>row.employeeId!==sheet.employeeId),...saved].sort((a,b)=>b.id-a.id);
    state.scoreSheet=null;$('score-sheet-dialog').close();render();toast('نمرات شایستگی با موفقیت ذخیره شد.');
  }catch(error){
    $('score-sheet-error').textContent=error.message;$('score-sheet-error').hidden=false;
    for(const [key,message]of Object.entries(error.fields||{})){
      const index=key.match(/^scores\[(\d+)\]/)?.[1];
      if(index!==undefined&&changes[index])scoreSheetFieldError(changes[index].id,message);
    }
  }finally{
    state.busy=false;
    if(state.scoreSheet){
      $('score-sheet-form').querySelectorAll('input,select,button').forEach(input=>input.disabled=false);
      $('score-sheet-employee').disabled=sheet.locked;updateScoreSheetStatus();focusScoreSheetError();
    }
  }
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
  $('job-content').innerHTML=`<div class="profile-summary"><div><small>کد شغل</small><span class="code">${escapeHtml(job.code)}</span></div><div><small>شرکت</small>${escapeHtml(companyLabel(job.companyId))}</div><div><small>شغل بالادست</small>${escapeHtml(label('jobs',job.parentId)||'سطح اصلی')}</div><div><small>شرایط احراز</small>${number(rows.length)} عامل</div></div><section class="profile-section"><div class="section-heading"><h3>عوامل و حداقل نمرات لازم</h3><button type="button" class="button primary" id="add-job-requirement">${icon('plus')}ثبت شرط احراز</button></div>${table}</section>`;
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
  if(key==='positions'){key='employees';history.replaceState(null,'','#employees');}
  state.section=Object.hasOwn(sections,key)?key:'dashboard';
  state.search='';state.filter='';state.page=1;render();
}
async function initialize(){try{await loadData();navigate();}catch(error){showLoadError(error.message);render();}}
$('today').textContent=new Intl.DateTimeFormat('fa-IR',{weekday:'long',day:'numeric',month:'long',year:'numeric'}).format(new Date());
$('add-button').onclick=()=>openForm();$('record-form').addEventListener('submit',saveForm);$('confirm-delete').onclick=confirmDelete;
$('score-sheet-form').addEventListener('submit',saveScoreSheet);
$('score-sheet-search').addEventListener('input',filterScoreSheet);
$('score-sheet-employee').addEventListener('change',event=>{
  if(!scoreSheetCanDiscard()){$('score-sheet-employee').value=state.scoreSheet.employeeId??'';return;}
  selectScoreSheetEmployee(Number(event.target.value)||null);
});
$('score-sheet-dialog').addEventListener('close',()=>{if(!$('score-sheet-dialog').open)state.scoreSheet=null;});
document.querySelectorAll('.close-dialog').forEach(b=>b.onclick=()=>{if(!state.busy)$('record-dialog').close();});
document.querySelectorAll('[data-close]').forEach(b=>b.onclick=()=>{
  if(state.busy||(b.dataset.close==='score-sheet-dialog'&&!scoreSheetCanDiscard()))return;
  $(b.dataset.close).close();
});
document.querySelectorAll('dialog').forEach(d=>d.addEventListener('cancel',e=>{
  if(state.busy||(d.id==='score-sheet-dialog'&&!scoreSheetCanDiscard()))e.preventDefault();
}));
window.addEventListener('hashchange',navigate);
initialize();

'use strict';

const accessElement = id => document.getElementById(id);
const accessEscape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const accessNumber = value => new Intl.NumberFormat('fa-IR').format(value);
const accessActions = {view:'مشاهده',create:'ثبت',edit:'ویرایش',delete:'حذف',import:'ورود اکسل',manage:'مدیریت'};
const management = {tab:'roles',roles:[],users:[],groups:[],role:null,user:null,deleting:null,busy:false};

function accessMessage(id, message) { accessElement(id).textContent=message;accessElement(id).hidden=false; }
function accessBusy(busy) {
  management.busy=busy;
  document.querySelectorAll('#role-form button,#user-form button,#role-delete-dialog button,#access-add').forEach(button=>button.disabled=busy);
}
async function loadManagement() {
  accessElement('access-error').hidden=true;
  const mine=await auth.json('/api/access/me');
  if(!mine.permissions.includes('security.manage')) {
    accessElement('access-panel').hidden=true;
    throw new Error('دسترسی مدیریت کاربران و نقش‌ها برای حساب شما فعال نیست. به سامانه بازگردید.');
  }
  [management.roles,management.users,management.groups]=await Promise.all(['roles','users','permissions'].map(key=>auth.json(`/api/admin/${key}`)));
  accessElement('access-loading').hidden=true;accessElement('access-panel').hidden=false;renderManagement();
}
function renderManagement() {
  const roles=management.tab==='roles';
  for(const key of ['roles','users']) {
    const button=accessElement(`${key}-tab`),active=management.tab===key;
    button.className=`button ${active?'primary':'secondary'}`;button.setAttribute('aria-pressed',String(active));
  }
  accessElement('access-list-title').textContent=roles?'نقش‌ها و مجوزها':'کاربران';
  accessElement('access-add').textContent=roles?'ایجاد نقش':'ایجاد کاربر';
  accessElement('access-list').innerHTML=roles?`<table><thead><tr><th>نام نقش</th><th>توضیحات</th><th>تعداد مجوز</th><th>کاربران</th><th>عملیات</th></tr></thead><tbody>${management.roles.map(role=>`<tr><td><strong>${accessEscape(role.name)}</strong>${role.builtIn?'<span class="badge">سیستمی</span>':''}</td><td>${accessEscape(role.description||'—')}</td><td>${accessNumber(role.permissions.length)}</td><td>${accessNumber(role.userCount)}</td><td><div class="row-actions"><button class="text-button" data-role-edit="${role.id}">${role.builtIn?'مشاهده مجوزها':'ویرایش و مجوزها'}</button>${role.builtIn?'':`<button class="text-button danger-text" data-role-delete="${role.id}">حذف</button>`}</div></td></tr>`).join('')}</tbody></table>`:
    `<table><thead><tr><th>نام کاربری</th><th>نام نمایشی</th><th>ایمیل</th><th>وضعیت</th><th>نقش‌ها</th><th>عملیات</th></tr></thead><tbody>${management.users.map(user=>`<tr><td><span dir="ltr">${accessEscape(user.username)}</span></td><td>${accessEscape(user.displayName)}</td><td>${accessEscape(user.email||'—')}</td><td><span class="badge ${user.enabled?'':'gray'}">${user.enabled?'فعال':'غیرفعال'}</span></td><td>${user.roleIds.length?user.roleIds.map(id=>`<span class="badge gray">${accessEscape(management.roles.find(role=>role.id===id)?.name||'—')}</span>`).join(''):'بدون نقش'}</td><td><button class="text-button" data-user-edit="${user.id}">ویرایش و نقش‌ها</button></td></tr>`).join('')}</tbody></table>`;
  document.querySelectorAll('[data-role-edit]').forEach(button=>button.onclick=()=>openRole(management.roles.find(role=>role.id===Number(button.dataset.roleEdit))));
  document.querySelectorAll('[data-user-edit]').forEach(button=>button.onclick=()=>openUser(management.users.find(user=>user.id===Number(button.dataset.userEdit))));
  document.querySelectorAll('[data-role-delete]').forEach(button=>button.onclick=()=>{
    if(management.busy)return;
    management.deleting=management.roles.find(role=>role.id===Number(button.dataset.roleDelete));
    accessElement('role-delete-error').hidden=true;
    accessElement('role-delete-description').textContent=`نقش «${management.deleting.name}» حذف شود؟ نقشِ دارای کاربر قابل حذف نیست.`;
    accessElement('role-delete-dialog').showModal();
  });
}
function openRole(role=null) {
  if(management.busy)return;management.role=role;
  accessElement('role-form').reset();accessElement('role-error').hidden=true;
  accessElement('role-title').textContent=role?.builtIn?'مجوزهای مدیر سامانه':role?'ویرایش نقش':'ایجاد نقش';
  accessElement('role-name').value=role?.name||'';accessElement('role-description').value=role?.description||'';
  accessElement('role-name').disabled=Boolean(role?.builtIn);accessElement('role-description').disabled=Boolean(role?.builtIn);
  accessElement('role-save').hidden=Boolean(role?.builtIn);
  accessElement('permission-matrix').innerHTML=`<table class="permission-table"><thead><tr><th scope="col">بخش برنامه</th>${Object.values(accessActions).map(title=>`<th scope="col">${title}</th>`).join('')}</tr></thead><tbody>${management.groups.map(group=>`<tr><th scope="row">${accessEscape(group.title)}</th>${Object.entries(accessActions).map(([action,title])=>{
    const permission=`${group.resource}.${action}`;
    return `<td>${group.actions.includes(action)?`<label><input type="checkbox" data-permission="${permission}" aria-label="${title} ${accessEscape(group.title)}" ${role?.permissions.includes(permission)?'checked':''} ${role?.builtIn?'disabled':''}></label>`:'—'}</td>`;
  }).join('')}</tr>`).join('')}</tbody></table>`;
  accessElement('permission-matrix').querySelectorAll('input').forEach(input=>input.addEventListener('change',()=>{
    const [resource,action]=input.dataset.permission.split('.');
    if(input.checked&&action!=='view')setPermission(`${resource}.view`,true);
    if(!input.checked&&action==='view')document.querySelectorAll('[data-permission]').forEach(box=>{if(box.dataset.permission.startsWith(resource+'.'))box.checked=false;});
    // If a parent view is explicitly cleared, clear the dependent section as well.
    if(!input.checked&&action==='view'&&['jobs','employees'].includes(resource)){
      const child=resource==='jobs'?'requirements':'positions';
      document.querySelectorAll('[data-permission]').forEach(box=>{if(box.dataset.permission.startsWith(child+'.'))box.checked=false;});
    }
    if(document.querySelector('[data-permission="requirements.view"]')?.checked)setPermission('jobs.view',true);
    if(document.querySelector('[data-permission="positions.view"]')?.checked)setPermission('employees.view',true);
  }));
  accessElement('role-dialog').showModal();
}
function setPermission(permission,checked){const box=document.querySelector(`[data-permission="${permission}"]`);if(box)box.checked=checked;}
function openUser(user=null) {
  if(management.busy)return;management.user=user;
  accessElement('user-form').reset();accessElement('user-error').hidden=true;
  accessElement('user-title').textContent=user?'ویرایش کاربر و نقش‌ها':'ایجاد کاربر';
  accessElement('user-username').value=user?.username||'';accessElement('user-username').disabled=Boolean(user);
  accessElement('user-display-name').value=user?.displayName||'';accessElement('user-email').value=user?.email||'';
  accessElement('user-enabled').value=String(user?.enabled??true);
  accessElement('new-user-passwords').hidden=Boolean(user);
  for(const id of ['user-password','user-confirmation']){accessElement(id).disabled=Boolean(user);accessElement(id).required=!user;}
  accessElement('user-role-options').innerHTML=management.roles.map(role=>`<label class="role-option"><input type="checkbox" value="${role.id}" ${user?.roleIds.includes(role.id)?'checked':''}><span>${accessEscape(role.name)}</span></label>`).join('');
  accessElement('user-dialog').showModal();
}
async function saveManagement(url,method,body,dialogId,message){
  await auth.json(url,{method,headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  accessElement(dialogId).close();accessMessage('access-success',message);
  try{await loadManagement();}catch(error){accessMessage('access-error',error.message);}
}
accessElement('role-form').addEventListener('submit',async event=>{
  event.preventDefault();if(management.busy||management.role?.builtIn)return;
  accessElement('role-error').hidden=true;accessBusy(true);
  try{
    const id=management.role?.id;
    await saveManagement(`/api/admin/roles${id?`/${id}`:''}`,id?'PUT':'POST',{
      name:accessElement('role-name').value.trim(),description:accessElement('role-description').value.trim(),
      permissions:[...document.querySelectorAll('[data-permission]:checked')].map(box=>box.dataset.permission)
    },'role-dialog','نقش ذخیره شد. تغییر مجوزها در درخواست بعدی کاربران اعمال می‌شود.');
  }catch(error){accessMessage('role-error',error.message);}finally{accessBusy(false);}
});
accessElement('user-form').addEventListener('submit',async event=>{
  event.preventDefault();if(management.busy)return;
  accessElement('user-error').hidden=true;
  const id=management.user?.id,username=accessElement('user-username').value.trim().toLowerCase();
  const body={displayName:accessElement('user-display-name').value.trim(),email:accessElement('user-email').value.trim(),
    enabled:accessElement('user-enabled').value==='true',roleIds:[...document.querySelectorAll('#user-role-options input:checked')].map(box=>Number(box.value))};
  if(!id){
    Object.assign(body,{username,password:accessElement('user-password').value,confirmation:accessElement('user-confirmation').value});
    if(!auth.passwordChecks(body.password,username).every(Boolean)){accessMessage('user-error','رمز عبور باید تمام شرایط درج‌شده در فرم را داشته باشد.');return;}
    if(body.password!==body.confirmation){accessMessage('user-error','رمز عبور و تکرار آن یکسان نیستند.');return;}
  }
  accessBusy(true);
  try{await saveManagement(`/api/admin/users${id?`/${id}`:''}`,id?'PUT':'POST',body,'user-dialog','کاربر و نقش‌های او ذخیره شدند.');}
  catch(error){accessMessage('user-error',error.message);}finally{accessBusy(false);}
});
accessElement('role-delete-confirm').onclick=async()=>{
  if(management.busy||!management.deleting)return;accessBusy(true);accessElement('role-delete-error').hidden=true;
  try{await auth.json(`/api/admin/roles/${management.deleting.id}`,{method:'DELETE'});accessElement('role-delete-dialog').close();accessMessage('access-success','نقش حذف شد.');await loadManagement();}
  catch(error){accessMessage(accessElement('role-delete-dialog').open?'role-delete-error':'access-error',error.message);}finally{accessBusy(false);}
};
for(const key of ['roles','users'])accessElement(`${key}-tab`).onclick=()=>{if(!management.busy){management.tab=key;renderManagement();}};
accessElement('access-add').onclick=()=>management.tab==='roles'?openRole():openUser();
document.querySelectorAll('[data-access-close]').forEach(button=>button.onclick=()=>{if(!management.busy)accessElement(button.dataset.accessClose).close();});
document.querySelectorAll('dialog').forEach(dialog=>dialog.addEventListener('cancel',event=>{if(management.busy)event.preventDefault();}));
accessElement('user-dialog').addEventListener('close',()=>{accessElement('user-password').value='';accessElement('user-confirmation').value='';});
loadManagement().catch(error=>{accessElement('access-loading').hidden=true;accessMessage('access-error',error.message);});

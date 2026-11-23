'use strict';
const accountElement=id=>document.getElementById(id);
let accountBusy=false,accountLoaded=false;
let savedAccountTheme='classic',selectedAccountTheme='classic';
function accountMessage(message,error=false){const element=accountElement(error?'account-error':'account-success');element.textContent=message;element.hidden=false;}
function accountSetBusy(busy){
  accountBusy=busy;['account-save','password-save','account-logout'].forEach(id=>accountElement(id).disabled=busy||!accountLoaded);
  accountElement('theme-fieldset').disabled=busy||!accountLoaded;
  ['theme-save','theme-reset'].forEach(id=>accountElement(id).disabled=busy||!accountLoaded||selectedAccountTheme===savedAccountTheme);
}
function selectAccountTheme(theme) {
  selectedAccountTheme=appTheme.apply(theme);
  document.querySelectorAll('input[name="theme"]').forEach(input=>input.checked=input.value===selectedAccountTheme);
  accountElement('theme-current').textContent=`ذخیره‌شده: ${appTheme.names[savedAccountTheme]}`;
  accountElement('theme-status').textContent=selectedAccountTheme===savedAccountTheme?
    `تم «${appTheme.names[savedAccountTheme]}» برای حساب شما ذخیره شده است.`:
    `پیش‌نمایش «${appTheme.names[selectedAccountTheme]}»؛ برای اعمال در ورودهای بعدی، «ذخیره ظاهر» را بزنید.`;
  accountSetBusy(accountBusy);
}
async function loadAccount(){
  try{const user=await auth.json('/api/account');accountElement('account-username').value=user.username;accountElement('password-username').value=user.username;accountElement('display-name').value=user.displayName;accountElement('account-email').value=user.email||'';savedAccountTheme=appTheme.normalize(user.theme);accountLoaded=true;selectAccountTheme(savedAccountTheme);accountSetBusy(false);}
  catch(error){accountMessage(error.message,true);}
}
accountElement('theme-fieldset').addEventListener('change',event=>{
  if(accountBusy||!accountLoaded||event.target.name!=='theme')return;
  accountElement('theme-error').hidden=true;selectAccountTheme(event.target.value);
});
accountElement('theme-reset').onclick=()=>{
  if(accountBusy||!accountLoaded)return;
  accountElement('theme-error').hidden=true;selectAccountTheme(savedAccountTheme);
};
accountElement('theme-form').addEventListener('submit',async event=>{
  event.preventDefault();if(accountBusy||!accountLoaded||selectedAccountTheme===savedAccountTheme)return;
  accountSetBusy(true);accountElement('theme-error').hidden=true;accountElement('theme-status').textContent='در حال ذخیرهٔ ظاهر برنامه…';
  try{
    const user=await auth.json('/api/account/preferences',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({theme:selectedAccountTheme})});
    savedAccountTheme=appTheme.normalize(user.theme);selectAccountTheme(savedAccountTheme);
  }catch(error){
    selectAccountTheme(selectedAccountTheme);accountElement('theme-error').textContent=error.message;accountElement('theme-error').hidden=false;
  }finally{accountSetBusy(false);}
});
accountElement('account-form').addEventListener('submit',async event=>{
  event.preventDefault();if(accountBusy)return;accountSetBusy(true);accountElement('account-error').hidden=true;accountElement('account-success').hidden=true;
  try{await auth.json('/api/account',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({displayName:accountElement('display-name').value,email:accountElement('account-email').value})});accountMessage('پروفایل ذخیره شد.');}
  catch(error){accountMessage(error.message,true);}finally{accountSetBusy(false);}
});
accountElement('new-password').addEventListener('input',()=>{
  const checks=auth.passwordChecks(accountElement('new-password').value,accountElement('account-username').value);
  document.querySelectorAll('#password-rules li').forEach((element,index)=>element.classList.toggle('met',checks[index]));
});
accountElement('password-form').addEventListener('submit',async event=>{
  event.preventDefault();if(accountBusy)return;accountElement('account-error').hidden=true;accountElement('account-success').hidden=true;
  const newPassword=accountElement('new-password').value,confirmation=accountElement('confirm-password').value;
  if(!auth.passwordChecks(newPassword,accountElement('account-username').value).every(Boolean)){accountMessage('رمز جدید باید همهٔ شرایط نمایش‌داده‌شده را داشته باشد.',true);return;}
  if(newPassword!==confirmation){accountMessage('رمز جدید و تکرار آن یکسان نیستند.',true);return;}
  accountSetBusy(true);
  try{await auth.json('/api/account/password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({currentPassword:accountElement('current-password').value,newPassword,confirmation})});event.target.reset();location.replace('/login.html?changed');}
  catch(error){accountMessage(error.message,true);}finally{accountElement('current-password').value='';accountSetBusy(false);}
});
accountElement('account-logout').onclick=async()=>{if(accountBusy)return;accountSetBusy(true);try{await auth.logout();}catch(error){accountMessage(error.message,true);accountSetBusy(false);}};
loadAccount();

'use strict';
const loginElement=id=>document.getElementById(id);
let captchaUrl,loginBusy=false;
function loginError(message){loginElement('login-error').textContent=message;loginElement('login-error').hidden=false;}
async function refreshCaptcha(){
  loginElement('login-submit').disabled=true;loginElement('refresh-captcha').disabled=true;loginElement('captcha').value='';
  try{
    await auth.csrf();
    const response=await auth.request('/api/auth/captcha');
    if(!response.ok){const body=await response.json();throw new Error(body.message||'دریافت تصویر انجام نشد.');}
    if(captchaUrl)URL.revokeObjectURL(captchaUrl);
    captchaUrl=URL.createObjectURL(await response.blob());loginElement('captcha-image').src=captchaUrl;
    loginElement('login-submit').disabled=false;
  }catch(error){loginError(error.message);}
  finally{loginElement('refresh-captcha').disabled=false;}
}
loginElement('refresh-captcha').onclick=()=>{if(!loginBusy){loginElement('login-error').hidden=true;refreshCaptcha();}};
loginElement('toggle-password').onclick=()=>{
  const visible=loginElement('password').type==='password';loginElement('password').type=visible?'text':'password';
  loginElement('toggle-password').textContent=visible?'پنهان کردن':'نمایش رمز';loginElement('toggle-password').setAttribute('aria-pressed',String(visible));
};
loginElement('login-form').addEventListener('submit',async event=>{
  event.preventDefault();if(loginBusy)return;loginBusy=true;
  loginElement('login-error').hidden=true;loginElement('login-submit').disabled=true;loginElement('refresh-captcha').disabled=true;
  loginElement('login-submit').textContent='در حال ورود…';
  try{
    await auth.json('/api/auth/login',{method:'POST',body:new URLSearchParams(new FormData(event.target))});
    loginElement('password').value='';location.replace('/');
  }catch(error){loginError(error.message);loginElement('password').value='';await refreshCaptcha();loginElement('password').focus();}
  finally{loginBusy=false;loginElement('login-submit').textContent='ورود';}
});
const loginParams=new URLSearchParams(location.search);
const loginNotice=loginParams.has('changed')?'رمز عبور تغییر کرد؛ با رمز جدید وارد شوید.':loginParams.has('logout')?'از حساب کاربری خارج شدید.':loginParams.has('expired')?'نشست شما پایان یافته است؛ دوباره وارد شوید.':'';
if(loginNotice){loginElement('login-notice').textContent=loginNotice;loginElement('login-notice').hidden=false;}
refreshCaptcha();

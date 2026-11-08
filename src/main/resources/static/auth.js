'use strict';

const auth = (() => {
  let csrfPromise;
  async function csrf() {
    if(!csrfPromise)csrfPromise=fetch('/api/auth/csrf',{credentials:'same-origin',cache:'no-store'})
      .then(response=>{if(!response.ok)throw new Error('دریافت اطلاعات امنیتی انجام نشد. صفحه را تازه‌سازی کنید.');return response.json();})
      .catch(error=>{csrfPromise=null;throw error;});
    return csrfPromise;
  }
  async function request(url,options={}) {
    const headers=new Headers(options.headers||{}),method=(options.method||'GET').toUpperCase();
    if(!['GET','HEAD','OPTIONS'].includes(method)){
      const token=await csrf();headers.set(token.headerName,token.token);
    }
    let response;
    try{response=await fetch(url,{...options,headers,credentials:'same-origin',cache:'no-store'});}
    catch{throw new Error('ارتباط با سرور برقرار نشد. اتصال را بررسی کنید.');}
    if(response.status===403)csrfPromise=null;
    if(response.status===401&&url!=='/api/auth/login'){
      location.replace('/login.html?expired');throw new Error('نشست شما پایان یافته است. دوباره وارد شوید.');
    }
    return response;
  }
  async function json(url,options={}) {
    const response=await request(url,options);
    if(response.status===204)return null;
    const body=await response.json().catch(()=>({message:'پاسخ سرور قابل خواندن نیست.'}));
    if(!response.ok){const error=new Error(body.message||'انجام درخواست ممکن نشد.');error.fields=body.fields;throw error;}
    return body;
  }
  async function logout() {
    await json('/api/auth/logout',{method:'POST'});
    location.replace('/login.html?logout');
  }
  function passwordChecks(value,username) {
    const lower=value.toLowerCase();
    return [value.length>=15&&value.length<=128,/[A-Z]/.test(value),/[a-z]/.test(value),/[0-9]/.test(value),
      /[^\p{L}\p{N}\s]/u.test(value),!/[\u0000-\u001f\u007f-\u009f]/.test(value)&&
      !(username.length>=3&&lower.includes(username.toLowerCase()))&&!['password','qwerty','123456','letmein'].some(word=>lower.includes(word))];
  }
  return {csrf,request,json,logout,passwordChecks};
})();

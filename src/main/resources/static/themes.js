'use strict';

// Preferences come from the signed-in account, never from another user's browser cache.
const appTheme = (() => {
  const names = Object.freeze({classic:'سبز کلاسیک',ocean:'آبی اقیانوس',violet:'بنفش آرام',dark:'شب'});
  function normalize(theme) { return Object.hasOwn(names,theme)?theme:'classic'; }
  function apply(theme) {
    theme=normalize(theme);
    document.documentElement.dataset.theme=theme;
    return theme;
  }
  return Object.freeze({names,normalize,apply});
})();

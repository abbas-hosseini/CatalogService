'use strict';

const employeeGenders = {MALE:'مرد',FEMALE:'زن'};
// Keep the exact backend codes; display names do not change persisted values.
// Persian rank titles will be supplied by the user later.
const employeeMarineRanks = Object.fromEntries([
  'DRYASALAR','AMIRNEMAT','DRYADAR','DRYANAVARD','NAKHDA_BANI','NAKHDA_DOUM','NAKHDA_SOOM',
  'NAKHDA_CHAHARUM','NAKHDA_PANJUM','NAKHDA_SHESHUM','NAKHDA_HAFTUM','NAKHDA_HASHTUM',
  'NAKHDA_NOHUM','NAKHDA_DAHUM','NAKHDA_YAZDAHUM','NAKHDA_DOVAZHDAHUM','NAKHDA_SEYAZDAHUM'
].map(code=>[code,code]));

function employeeFormFields() {
  return [
    field('personnelCode','کد پرسنلی','text',{required:true,max:40,group:'مشخصات فردی'}),
    field('firstName','نام','text',{required:true,max:80}),
    field('lastName','نام خانوادگی','text',{required:true,max:80}),
    field('gender','جنسیت','choice',{choices:employeeGenders}),
    field('birthDate','تاریخ تولد (شمسی)','jalali'),
    field('startDate','تاریخ شروع به کار (شمسی)','jalali',{group:'اطلاعات شغلی و دریایی'}),
    field('hireDate','تاریخ استخدام (شمسی)','jalali'),
    field('speciality','تخصص','text',{max:200}),
    field('marineExperience','سابقه دریایی (سال)','decimal',{min:0,max:100,placeholder:'مثلاً ۱۲٫۵'}),
    field('lastMarineRank','آخرین درجه دریایی','choice',{choices:employeeMarineRanks}),
    field('educationLevel','مدرک تحصیلی','choice',{choices:educationLevels,group:'تحصیلات'}),
    field('grade','مقطع تحصیلی','decimal',{min:0,max:100,placeholder:'۰ تا ۱۰۰'}),
    field('bachelorMajor','رشته کارشناسی','text',{max:200}),
    field('bachelorUniversity','دانشگاه کارشناسی','text',{max:200}),
    field('masterMajor','رشته کارشناسی ارشد','text',{max:200}),
    field('masterUniversity','دانشگاه کارشناسی ارشد','text',{max:200}),
    field('doctoralMajor','رشته دکتری','text',{max:200}),
    field('doctoralUniversity','دانشگاه دکتری','text',{max:200}),
    {...descriptionField,group:'توضیحات'}
  ];
}

function employeeValue(field, value) {
  if(value==null||value==='')return 'ثبت نشده';
  if(field.type==='choice')return field.choices[value]||value;
  if(field.type==='jalali')return jalaliDate(value);
  if(field.type==='decimal')return number(value);
  return String(value);
}
function employeeSearchText(employee) {
  return sections.employees.fields.map(field=>`${employee[field.name]??''} ${employeeValue(field,employee[field.name])}`).join(' ');
}
function renderEmployeeDetails(employee) {
  const groups=[];
  for(const field of sections.employees.fields) {
    if(field.group)groups.push({title:field.group,fields:[]});
    groups[groups.length-1].fields.push(field);
  }
  return groups.map(group=>`<section class="profile-section employee-detail-section"><h3>${escapeHtml(group.title)}</h3><dl class="employee-details">${group.fields.map(field=>`<div${field.type==='textarea'?' class="employee-detail-full"':''}><dt>${escapeHtml(field.label)}</dt><dd>${field.type==='jalali'&&employee[field.name]?jalaliDateCell(employee[field.name]):escapeHtml(employeeValue(field,employee[field.name]))}</dd></div>`).join('')}</dl></section>`).join('');
}

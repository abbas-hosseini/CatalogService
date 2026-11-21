'use strict';

const employeeGenders = {MALE:'مرد',FEMALE:'زن'};
// Keep the exact backend codes; display names do not change persisted values.
// Persian rank titles will be supplied by the user later.
const employeeMarineRanks = {
  MASTER:'Master',
  CHIEF_ENGINEER:'Chief Engineer',
  CHIEF_OFFICER:'Chief Officer',
  SECOND_ENGINEER:'2nd Engineer',
  THIRD_OFFICER:'3rd Officer',
  THIRD_ENGINEER:'3rd Engineer',
  FOURTH_ENGINEER:'4th Engineer',
  FIFTH_ENGINEER:'5th Engineer',
  ELECTRONIC_OFFICER:'Electronic Officer',
  RADIO_OFFICER:'Radio Officer'
};

function employeeFormFields() {
  return [
    field('personnelCode','کد پرسنلی','text',{required:true,max:40,group:'مشخصات فردی'}),
    field('firstName','نام','text',{required:true,max:80}),
    field('lastName','نام خانوادگی','text',{required:true,max:80}),
    field('gender','جنسیت','choice',{choices:employeeGenders}),
    field('birthDate','تاریخ تولد (شمسی)','jalali'),
    field('startDate','تاریخ شروع به کار (شمسی)','jalali',{group:'اطلاعات شغلی و دریایی'}),
    field('hireDate','تاریخ استخدام (شمسی)','jalali'),
    refField('specialityId','تخصص','specialities',false),
    refField('grade','سطح سازمانی','organizational-levels',false),
    field('marineExperience','سابقه دریایی (سال)','decimal',{min:0,max:100,placeholder:'مثلاً ۱۲٫۵'}),
    field('lastMarineRank','آخرین درجه دریایی','choice',{choices:employeeMarineRanks}),
    field('educationLevel','مدرک تحصیلی','choice',{choices:educationLevels,group:'تحصیلات'}),
    field('bachelorMajor','رشته کارشناسی','text',{max:200}),
    field('bachelorUniversity','دانشگاه کارشناسی','text',{max:200}),
    field('masterMajor','رشته کارشناسی ارشد','text',{max:200}),
    field('masterUniversity','دانشگاه کارشناسی ارشد','text',{max:200}),
    field('doctoralMajor','رشته دکتری','text',{max:200}),
    field('doctoralUniversity','دانشگاه دکتری','text',{max:200}),
    {...descriptionField,group:'توضیحات'},
    field('assessmentScore','نمره ارزیابی','computed',{group:'ارزیابی',help:'این نمره توسط سیستم از نمرهٔ ارزیابی و سوابق آموزشی محاسبه می‌شود و قابل ویرایش نیست.'})
  ];
}

function employeeValue(field, value) {
  if(field.type==='computed')return value==null?'ارزیابی ثبت نشده':number(value);
  if(value==null||value==='')return 'ثبت نشده';
  if(field.type==='choice')return field.choices[value]||value;
  if(field.type==='select')return label(field.ref,value)||'گزینه نامشخص';
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

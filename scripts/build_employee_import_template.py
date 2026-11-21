"""Build the blank, Persian employee import template shipped with the application."""
from pathlib import Path
from openpyxl import Workbook
from openpyxl.comments import Comment
from openpyxl.styles import Alignment, Font, PatternFill, Border, Side
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.workbook.defined_name import DefinedName
from openpyxl.utils import get_column_letter

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/static/templates/employees-import.xlsx"
HEADERS = ["کد پرسنلی", "نام", "نام خانوادگی", "مدرک تحصیلی", "تاریخ شروع به کار (شمسی)", "توضیحات"]
EDUCATION = ["زیر دیپلم", "دیپلم", "فوق دیپلم", "لیسانس", "فوق لیسانس", "دکتری"]
DATE_HELP = "اختیاری؛ متن شمسی با قالب سال/ماه/روز و ارقام فارسی، عربی یا انگلیسی. از تاریخ عددی Excel استفاده نکنید."
NUMBER_HELP = "اختیاری؛ عدد بین ۰ و ۱۰۰ با حداکثر دو رقم اعشار. صفر نیز پذیرفته می‌شود."
TEXT_HELP = "اختیاری، حداکثر ۲۰۰ کاراکتر."
NEW_FIELDS = [
    ("تاریخ تولد (شمسی)", 28, "۱۳۷۰/۰۱/۰۲", DATE_HELP),
    ("جنسیت", 18, "زن", "اختیاری؛ مرد یا زن را از فهرست انتخاب کنید. کدهای MALE و FEMALE نیز پذیرفته می‌شوند."),
    ("تاریخ استخدام (شمسی)", 30, "۱۳۹۹/۱۲/۳۰", DATE_HELP),
    ("سابقه دریایی (سال)", 26, "۱۲٫۵", NUMBER_HELP),
    ("تخصص", 38, "کد — عنوان تخصص", "اختیاری؛ گزینه‌ای از جدول تخصص‌ها انتخاب کنید. کد یا عنوان دقیق نیز پذیرفته می‌شود. پس از تغییر اطلاعات پایه قالب تازه دریافت کنید."),
    ("آخرین درجه دریایی", 32, "MASTER", "اختیاری؛ کد را از فهرست انتخاب کنید. عنوان فارسی درجه‌ها هنوز تعیین نشده است."),
    ("سطح سازمانی", 38, "کد — عنوان سطح", "اختیاری؛ گزینه‌ای از جدول سطوح سازمانی انتخاب کنید. کد یا عنوان دقیق نیز پذیرفته می‌شود. این فیلد مستقل از مدرک تحصیلی است."),
    ("رشته کارشناسی", 30, "مهندسی دریا", TEXT_HELP),
    ("دانشگاه کارشناسی", 34, "دانشگاه اول", TEXT_HELP),
    ("رشته کارشناسی ارشد", 32, "مدیریت", TEXT_HELP),
    ("دانشگاه کارشناسی ارشد", 36, "دانشگاه دوم", TEXT_HELP),
    ("رشته دکتری", 30, "حمل و نقل", TEXT_HELP),
    ("دانشگاه دکتری", 34, "دانشگاه سوم", TEXT_HELP),
]
HEADERS += [item[0] for item in NEW_FIELDS]
RANKS = ["MASTER", "CHIEF_ENGINEER", "CHIEF_OFFICER", "SECOND_ENGINEER", "THIRD_OFFICER",
         "THIRD_ENGINEER", "FOURTH_ENGINEER", "FIFTH_ENGINEER", "ELECTRONIC_OFFICER", "RADIO_OFFICER"]


workbook = Workbook()
sheet = workbook.active
sheet.title = "کارمندان"
guide = workbook.create_sheet("راهنما")
options = workbook.create_sheet("گزینه‌ها")
options.sheet_state = "hidden"
options.append(["کد درجه دریایی"])
for rank in RANKS:
    options.append([rank])
# Excel list validation has a 255-character inline limit; use a named range for ranks.
workbook.defined_names.add(DefinedName("MarineRanks", attr_text=f"'گزینه‌ها'!$A$2:$A${len(RANKS) + 1}"))
for ws in workbook:
    ws.sheet_view.rightToLeft = True
    ws.sheet_view.showGridLines = False
    ws.freeze_panes = "A2"
    ws.sheet_properties.pageSetUpPr.fitToPage = True
    ws.page_setup.orientation = "landscape"
    ws.page_setup.paperSize = ws.PAPERSIZE_A4
    ws.page_setup.fitToWidth = 1
    ws.page_setup.fitToHeight = 1
    ws.print_options.horizontalCentered = True

sheet.append(HEADERS)
sheet.freeze_panes = "D2"
for index, width in enumerate([20, 22, 25, 24, 32, 50] + [item[1] for item in NEW_FIELDS], 1):
    col = get_column_letter(index)
    sheet.column_dimensions[col].width = width
    sheet.column_dimensions[col].number_format = "0.00" if col == "J" else "@"
for row in sheet.iter_rows(min_row=2, max_row=21, max_col=len(HEADERS)):
    for cell in row:
        cell.number_format = sheet.column_dimensions[cell.column_letter].number_format
        cell.font = Font(name="Tahoma", size=11, color="243D47")
        cell.alignment = Alignment(horizontal="right", vertical="center", readingOrder=2)
        cell.fill = PatternFill("solid", fgColor="F0F7F5" if cell.row % 2 == 0 else "FFFFFF")
    sheet.row_dimensions[row[0].row].height = 25
for col in "ABC":
    sheet[f"{col}1"].comment = Comment("این ستون الزامی است.", "سامانه شایستگی")
sheet["A2"].comment = Comment("کد را به‌صورت متن وارد کنید تا صفرهای ابتدایی حفظ شوند؛ مثال در برگه راهنماست.", "سامانه شایستگی")
sheet["E2"].comment = Comment("تاریخ شمسی متنی؛ مانند ۱۴۰۳/۰۱/۰۱. از تاریخ عددی Excel استفاده نکنید.", "سامانه شایستگی")
for index, item in enumerate(NEW_FIELDS, 7):
    sheet.cell(1, index).comment = Comment(item[3], "سامانه شایستگی")
validation = DataValidation(type="list", formula1='"' + ",".join(EDUCATION) + '"', allow_blank=True)
validation.errorTitle = "مدرک تحصیلی نامعتبر"
validation.error = "از گزینه‌های فهرست انتخاب کنید."
validation.showErrorMessage = True
validation.showDropDown = False
sheet.add_data_validation(validation)
validation.add("D2:D1001")
for col, formula in [("H", '"مرد,زن"'), ("L", "=MarineRanks")]:
    validation = DataValidation(type="list", formula1=formula, allow_blank=True)
    validation.errorTitle = "مقدار نامعتبر"
    validation.error = "از گزینه‌های فهرست انتخاب کنید."
    validation.showErrorMessage = True
    validation.showDropDown = False
    sheet.add_data_validation(validation)
    validation.add(f"{col}2:{col}1001")
for col in ("J",):
    validation = DataValidation(type="decimal", operator="between", formula1=0, formula2=100, allow_blank=True)
    validation.errorTitle = "عدد نامعتبر"
    validation.error = "عدد باید بین ۰ و ۱۰۰ باشد."
    validation.promptTitle = "ورود عدد"
    validation.prompt = "حداکثر دو رقم اعشار؛ صفر مجاز است."
    validation.showErrorMessage = True
    validation.showInputMessage = True
    sheet.add_data_validation(validation)
    validation.add(f"{col}2:{col}1001")
sheet.auto_filter.ref = "A1:S21"
sheet.print_title_rows = "1:1"
sheet.print_area = "A1:S21"

guide.append(["ستون / موضوع", "نمونه", "راهنما"])
for row in [
    ["روش استفاده", "فقط برگهٔ اول", "اطلاعات را از ردیف دوم برگهٔ کارمندان وارد کنید؛ این برگه فقط راهنماست و وارد سامانه نمی‌شود."],
    ["کد پرسنلی", "00123", "الزامی، یکتا و حداکثر ۴۰ کاراکتر؛ قالب Text صفرهای ابتدایی را حفظ می‌کند."],
    ["نام", "علی", "الزامی، حداکثر ۸۰ کاراکتر."],
    ["نام خانوادگی", "رضایی", "الزامی، حداکثر ۸۰ کاراکتر."],
    ["مدرک تحصیلی", "لیسانس", "اختیاری: " + "، ".join(EDUCATION)],
    ["تاریخ شروع به کار", "۱۴۰۳/۰۱/۰۱", "اختیاری؛ متن شمسی با قالب سال/ماه/روز. ارقام فارسی و انگلیسی پذیرفته می‌شوند."],
    ["توضیحات", "", "اختیاری، حداکثر ۲۰۰۰ کاراکتر."],
    *[[label, example, help_text] for label, width, example, help_text in NEW_FIELDS],
    ["قالب‌های قبلی", "بررسی اطلاعات پایه", "فقط کد پرسنلی، نام و نام خانوادگی الزامی‌اند. تخصص و سطح سازمانی باید در اطلاعات پایه تعریف شده باشند؛ مقدار ناشناخته خطا دارد."],
    ["پیش‌نمایش و ثبت", "ورود از اکسل", "پس از بارگذاری، بررسی فایل و پیش‌نمایش را بزنید؛ فقط پس از رفع همهٔ خطاها ثبت را تأیید کنید."],
    ["کدهای تکراری", "بدون بازنویسی", "کدهای موجود در سامانه و تکراری در فایل خطا دارند. هیچ کارمند قبلی ویرایش نمی‌شود."],
    ["حدود فایل", "۱۰۰۰ ردیف / ۵ مگابایت", "پسوند xlsx یا xls، فایل بدون رمز و سلول‌ها بدون فرمول باشند."],
    ["شرکت و سمت", "پروندهٔ کارمند", "پس از ثبت کارمند، شرکت و سمت‌های او را از پرونده وارد کنید."],
]:
    guide.append(row)
for col, width in zip("ABC", [32, 28, 80]):
    guide.column_dimensions[col].width = width
for row in guide.iter_rows(min_row=2):
    guide.row_dimensions[row[0].row].height = 46
    for cell in row:
        cell.number_format = "@"
        cell.font = Font(name="Tahoma", size=11, color="243D47")
        cell.alignment = Alignment(horizontal="right", vertical="center", wrap_text=True, readingOrder=2)
        cell.fill = PatternFill("solid", fgColor="F0F7F5" if cell.row % 2 == 0 else "FFFFFF")
guide.print_area = f"A1:C{guide.max_row}"
guide.print_title_rows = "1:1"
guide.page_setup.fitToHeight = 0
for ws in workbook:
    ws.row_dimensions[1].height = 34
    for cell in ws[1]:
        cell.font = Font(name="Tahoma", size=11, bold=True, color="FFFFFF")
        cell.fill = PatternFill("solid", fgColor="175F5C")
        cell.alignment = Alignment(horizontal="right", vertical="center", readingOrder=2)
        cell.border = Border(bottom=Side(style="thin", color="A3C9C3"))
workbook.properties.title = "قالب ورود کارمندان"
workbook.properties.creator = "سامانه شایستگی"
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
workbook.save(OUTPUT)
print(OUTPUT)

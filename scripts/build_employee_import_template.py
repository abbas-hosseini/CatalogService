"""Build the blank, Persian employee import template shipped with the application."""
from pathlib import Path
from openpyxl import Workbook
from openpyxl.comments import Comment
from openpyxl.styles import Alignment, Font, PatternFill, Border, Side
from openpyxl.worksheet.datavalidation import DataValidation

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/static/templates/employees-import.xlsx"
HEADERS = ["کد پرسنلی", "نام", "نام خانوادگی", "مدرک تحصیلی", "تاریخ شروع به کار (شمسی)", "توضیحات"]
EDUCATION = ["زیر دیپلم", "دیپلم", "فوق دیپلم", "لیسانس", "فوق لیسانس", "دکتری"]

workbook = Workbook()
sheet = workbook.active
sheet.title = "کارمندان"
guide = workbook.create_sheet("راهنما")
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
for col, width in zip("ABCDEF", [20, 22, 25, 24, 32, 50]):
    sheet.column_dimensions[col].width = width
    sheet.column_dimensions[col].number_format = "@"
for row in sheet.iter_rows(min_row=2, max_row=21, max_col=6):
    for cell in row:
        cell.number_format = "@"
        cell.font = Font(name="Tahoma", size=11, color="243D47")
        cell.alignment = Alignment(horizontal="right", vertical="center", readingOrder=2)
        cell.fill = PatternFill("solid", fgColor="F0F7F5" if cell.row % 2 == 0 else "FFFFFF")
    sheet.row_dimensions[row[0].row].height = 25
for col in "ABC":
    sheet[f"{col}1"].comment = Comment("این ستون الزامی است.", "سامانه شایستگی")
sheet["A2"].comment = Comment("کد را به‌صورت متن وارد کنید تا صفرهای ابتدایی حفظ شوند؛ مثال در برگه راهنماست.", "سامانه شایستگی")
sheet["E2"].comment = Comment("تاریخ شمسی متنی؛ مانند ۱۴۰۳/۰۱/۰۱. از تاریخ عددی Excel استفاده نکنید.", "سامانه شایستگی")
validation = DataValidation(type="list", formula1='"' + ",".join(EDUCATION) + '"', allow_blank=True)
validation.errorTitle = "مدرک تحصیلی نامعتبر"
validation.error = "از گزینه‌های فهرست انتخاب کنید."
validation.showErrorMessage = True
validation.showDropDown = False
sheet.add_data_validation(validation)
validation.add("D2:D1001")
sheet.auto_filter.ref = "A1:F21"
sheet.print_title_rows = "1:1"
sheet.print_area = "A1:F21"

guide.append(["ستون / موضوع", "نمونه", "راهنما"])
for row in [
    ["روش استفاده", "فقط برگهٔ اول", "اطلاعات را از ردیف دوم برگهٔ کارمندان وارد کنید؛ این برگه فقط راهنماست و وارد سامانه نمی‌شود."],
    ["کد پرسنلی", "00123", "الزامی، یکتا و حداکثر ۴۰ کاراکتر؛ قالب Text صفرهای ابتدایی را حفظ می‌کند."],
    ["نام", "علی", "الزامی، حداکثر ۸۰ کاراکتر."],
    ["نام خانوادگی", "رضایی", "الزامی، حداکثر ۸۰ کاراکتر."],
    ["مدرک تحصیلی", "لیسانس", "اختیاری: " + "، ".join(EDUCATION)],
    ["تاریخ شروع به کار", "۱۴۰۳/۰۱/۰۱", "اختیاری؛ متن شمسی با قالب سال/ماه/روز. ارقام فارسی و انگلیسی پذیرفته می‌شوند."],
    ["توضیحات", "", "اختیاری، حداکثر ۲۰۰۰ کاراکتر."],
    ["پیش‌نمایش و ثبت", "ورود از اکسل", "پس از بارگذاری، بررسی فایل و پیش‌نمایش را بزنید؛ فقط پس از رفع همهٔ خطاها ثبت را تأیید کنید."],
    ["کدهای تکراری", "بدون بازنویسی", "کدهای موجود در سامانه و تکراری در فایل خطا دارند. هیچ کارمند قبلی ویرایش نمی‌شود."],
    ["حدود فایل", "۱۰۰۰ ردیف / ۵ مگابایت", "پسوند xlsx یا xls، فایل بدون رمز و سلول‌ها بدون فرمول باشند."],
    ["شرکت و سمت", "پروندهٔ کارمند", "پس از ثبت کارمند، شرکت و سمت‌های او را از پرونده وارد کنید."],
]:
    guide.append(row)
for col, width in zip("ABC", [25, 28, 80]):
    guide.column_dimensions[col].width = width
for row in guide.iter_rows(min_row=2):
    guide.row_dimensions[row[0].row].height = 46
    for cell in row:
        cell.number_format = "@"
        cell.font = Font(name="Tahoma", size=11, color="243D47")
        cell.alignment = Alignment(horizontal="right", vertical="center", wrap_text=True, readingOrder=2)
        cell.fill = PatternFill("solid", fgColor="F0F7F5" if cell.row % 2 == 0 else "FFFFFF")
guide.print_area = f"A1:C{guide.max_row}"
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

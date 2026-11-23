"""Build the Persian job import template; live choices are filled at download time."""
from pathlib import Path
from openpyxl import Workbook
from openpyxl.comments import Comment
from openpyxl.styles import Alignment, Font, PatternFill, Border, Side
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.workbook.defined_name import DefinedName

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/static/templates/jobs-import.xlsx"
book = Workbook()
data = book.active
data.title = "مشاغل"
guide = book.create_sheet("راهنما")
companies = book.create_sheet("شرکت‌ها")
jobs = book.create_sheet("مشاغل موجود")
data.append(["کد شغل", "عنوان شغل", "شرکت", "کد شغل بالادست", "توضیحات"])
guide.append(["موضوع", "راهنما", "نمونه"])
for row in [
    ("روش استفاده", "قالب را از برنامه دانلود کنید؛ شرکت‌ها و مشاغل موجود به‌روز می‌شوند. اطلاعات جدید را از ردیف دوم برگهٔ «مشاغل» وارد کنید.", ""),
    ("کد شغل", "الزامی؛ حداکثر ۴۰ نویسه. در کل سامانه یکتا است. کد موجود ویرایش نمی‌شود. برای حفظ صفرهای ابتدا، قالب Text را حفظ کنید.", "001"),
    ("عنوان شغل", "الزامی؛ حداکثر ۱۶۰ نویسه.", "مدیر عملیات"),
    ("شرکت", "الزامی؛ از فهرست انتخاب کنید. کد یا عنوان دقیق شرکت نیز پذیرفته می‌شود. شرکت باید قبلاً در سامانه تعریف شده باشد.", "C-01 — شرکت نمونه"),
    ("کد شغل بالادست", "اختیاری؛ کد شغلی در همان شرکت، از سامانه یا همین فایل. ترتیب ردیف‌ها مهم نیست. برای شغل اصلی خالی بگذارید.", "001"),
    ("توضیحات", "اختیاری؛ حداکثر ۲۰۰۰ نویسه. فرمول اکسل مجاز نیست.", "مسئول هماهنگی عملیات"),
    ("ساختار سازمانی", "زیرمجموعه می‌تواند پیش از بالادست بیاید. بالادست باید شرکت یکسان داشته باشد. خودارجاعی و چرخه مجاز نیست.", "002 ← 001"),
    ("بررسی و ثبت", "پس از بارگذاری، پیش‌نمایش را بررسی کنید. وجود هر خطا مانع ثبت کل فایل می‌شود. ثبت نهایی فایل را دوباره اعتبارسنجی می‌کند.", ""),
    ("محدودیت فایل", "فقط برگهٔ اول خوانده می‌شود؛ برگه‌های راهنما و فهرست‌ها وارد نمی‌شوند. حداکثر ۱۰۰۰ ردیف، ۵ مگابایت؛ xlsx یا xls.", ""),
    ("نمونهٔ شغل اصلی", "نمونه را فقط با شرکت معتبر خود در برگهٔ مشاغل وارد کنید؛ دادهٔ نمونه در فایل ورودی قرار داده نشده است.", "001 | مدیر عملیات | C-01"),
    ("نمونهٔ زیرمجموعه", "کد بالادست 001 است؛ هر دو شغل متعلق به C-01 هستند.", "002 | کارشناس عملیات | C-01 | 001"),
]:
    guide.append(row)
companies.append(["کد شرکت", "نام شرکت", "گزینهٔ قابل انتخاب"])
jobs.append(["کد شغل", "عنوان شغل", "شرکت", "کد شغل بالادست"])
for ws, widths in [(data,[20,32,42,25,50]), (guide,[25,85,52]), (companies,[20,42,60]), (jobs,[20,38,50,25])]:
    ws.sheet_view.rightToLeft = True
    ws.sheet_view.showGridLines = False
    ws.freeze_panes = "A2"
    for i, width in enumerate(widths, 1):
        ws.column_dimensions[ws.cell(1,i).column_letter].width = width
        ws.column_dimensions[ws.cell(1,i).column_letter].number_format = "@"
    for row in ws.iter_rows(min_row=1, max_row=ws.max_row, max_col=len(widths)):
        for cell in row:
            cell.font = Font(name="Tahoma", size=11, color="243D47")
            cell.alignment = Alignment(horizontal="right", vertical="center", wrap_text=True, readingOrder=2)
            cell.number_format = "@"
        ws.row_dimensions[row[0].row].height = 54 if ws == guide else 28
    for cell in ws[1]:
        cell.font = Font(name="Tahoma", size=11, bold=True, color="FFFFFF")
        cell.fill = PatternFill("solid", fgColor="123D46")
        cell.border = Border(bottom=Side(style="thin",color="1E7B78"))
    ws.row_dimensions[1].height = 32
    ws.sheet_properties.pageSetUpPr.fitToPage = True
    ws.page_setup.orientation = "landscape"
    ws.page_setup.paperSize = ws.PAPERSIZE_A4
    ws.page_setup.fitToWidth = 1
    ws.page_setup.fitToHeight = 0
    ws.print_title_rows = "1:1"

for ws, last in [(data,21),(companies,2),(jobs,2)]:
    for row in ws.iter_rows(min_row=2, max_row=last, max_col=ws.max_column):
        for cell in row:
            cell.font = Font(name="Tahoma", size=11, color="243D47")
            cell.alignment = Alignment(horizontal="right", vertical="center", wrap_text=True, readingOrder=2)
            cell.number_format = "@"
            cell.fill = PatternFill("solid",fgColor="F0F7F5" if cell.row%2==0 else "FFFFFF")
        ws.row_dimensions[row[0].row].height = 28
data.auto_filter.ref = "A1:E1001"
for column, hint in {"A":"الزامی؛ کد شغل یکتا، حداکثر ۴۰ نویسه، با قالب Text.","B":"الزامی؛ حداکثر ۱۶۰ نویسه.","C":"الزامی؛ شرکت تعریف‌شده در سامانه.","D":"اختیاری؛ کد شغل بالادست در همان شرکت، از سامانه یا همین فایل.","E":"اختیاری؛ حداکثر ۲۰۰۰ نویسه."}.items():
    data[f"{column}1"].comment = Comment(hint,"سامانه شایستگی")
book.defined_names.add(DefinedName("JobCompanies",attr_text="'شرکت‌ها'!$C$2:$C$2"))
validation = DataValidation(type="list",formula1="=JobCompanies",allow_blank=False)
validation.showDropDown = False
validation.showErrorMessage = True
validation.errorTitle = "شرکت نامعتبر"
validation.error = "یک شرکت موجود انتخاب کنید؛ در صورت نیاز قالب تازه دریافت کنید."
data.add_data_validation(validation)
validation.add("C2:C1001")
OUTPUT.parent.mkdir(parents=True,exist_ok=True)
book.save(OUTPUT)
print(OUTPUT)

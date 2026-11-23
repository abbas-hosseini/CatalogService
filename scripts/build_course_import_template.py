"""Build the Persian course import workbook, with empty input rows and a separate guide."""
from pathlib import Path
from openpyxl import Workbook
from openpyxl.comments import Comment
from openpyxl.styles import Alignment, Font, PatternFill, Border, Side
from openpyxl.worksheet.datavalidation import DataValidation

OUTPUT = Path(__file__).resolve().parents[1] / 'src/main/resources/static/templates/courses-import.xlsx'
book = Workbook()
data = book.active
data.title = 'دوره‌ها'
guide = book.create_sheet('راهنما')
data.append(['کد دوره', 'عنوان دوره', 'برگزارکننده', 'مدت دوره (ساعت)', 'توضیحات'])
guide.append(['موضوع', 'راهنما', 'نمونه'])
for row in [
    ('روش استفاده', 'اطلاعات دوره‌های جدید را از ردیف دوم برگهٔ «دوره‌ها» وارد کنید.', ''),
    ('کد دوره', 'الزامی؛ یکتا و حداکثر ۴۰ نویسه. قالب Text را برای حفظ صفرهای ابتدایی نگه دارید.', '001'),
    ('عنوان دوره', 'الزامی؛ حداکثر ۱۶۰ نویسه.', 'ایمنی دریایی'),
    ('برگزارکننده', 'اختیاری؛ حداکثر ۱۶۰ نویسه.', 'مرکز آموزش'),
    ('مدت دوره (ساعت)', 'الزامی؛ بیشتر از صفر تا ۱۰۰۰۰ ساعت؛ حداکثر دو رقم اعشار. عدد فارسی یا انگلیسی پذیرفته می‌شود.', '12.5'),
    ('قالب مدت', 'عدد ساده با ممیز نقطه یا ٫ وارد کنید. تاریخ، ساعت، درصد و جداکنندهٔ هزارگان مجاز نیست.', '۱۲٫۵'),
    ('توضیحات', 'اختیاری؛ حداکثر ۲۰۰۰ نویسه.', 'دورهٔ مقدماتی'),
    ('مقادیر مستقیم', 'فرمول و خطای اکسل پذیرفته نیست. از Paste Values برای چسباندن نتیجهٔ فرمول استفاده کنید.', ''),
    ('بررسی و ثبت', 'پیش‌نمایش را بررسی کنید. وجود هر خطا مانع ثبت کل فایل می‌شود؛ سپس ثبت را تأیید کنید.', ''),
    ('کدهای تکراری', 'کد تکراری در فایل یا سامانه پذیرفته نیست. اطلاعات دوره‌های قبلی ویرایش نمی‌شود.', ''),
    ('محدودیت فایل', 'فقط برگهٔ اول؛ حداکثر ۱۰۰۰ ردیف اطلاعات و ۵ مگابایت. فرمت xlsx یا xls.', ''),
    ('نمونهٔ یک ردیف', 'نمونه فقط در راهنما است و به‌طور خودکار وارد سامانه نمی‌شود.', '001 | ایمنی دریایی | مرکز آموزش | 12.5'),
]:
    guide.append(row)

for ws, widths in [(data,[20,36,32,24,48]), (guide,[26,78,52])]:
    ws.sheet_view.rightToLeft = True
    ws.sheet_view.showGridLines = False
    ws.freeze_panes = 'A2'
    for i, width in enumerate(widths,1):
        ws.column_dimensions[ws.cell(1,i).column_letter].width = width
        ws.column_dimensions[ws.cell(1,i).column_letter].number_format = '@'
    for row in ws.iter_rows(min_row=1,max_row=21 if ws==data else ws.max_row,max_col=len(widths)):
        for cell in row:
            cell.font = Font(name='Tahoma',size=11,color='243D47')
            cell.alignment = Alignment(horizontal='right',vertical='center',wrap_text=True,readingOrder=2)
            cell.number_format = '@'
            cell.fill = PatternFill('solid',fgColor='F0F7F5' if cell.row%2==0 else 'FFFFFF')
        ws.row_dimensions[row[0].row].height = 48 if ws==guide else 28
    for cell in ws[1]:
        cell.font = Font(name='Tahoma',size=11,bold=True,color='FFFFFF')
        cell.fill = PatternFill('solid',fgColor='123D46')
        cell.border = Border(bottom=Side(style='thin',color='1E7B78'))
    ws.row_dimensions[1].height = 32
    ws.sheet_properties.pageSetUpPr.fitToPage = True
    ws.page_setup.orientation = 'landscape'
    ws.page_setup.paperSize = ws.PAPERSIZE_A4
    ws.page_setup.fitToWidth = 1
    ws.page_setup.fitToHeight = 0
    ws.print_title_rows = '1:1'

data.column_dimensions['D'].number_format = '0.##'
for row in range(2,22):
    data.cell(row,4).number_format = '0.##'
data.auto_filter.ref = 'A1:E1001'
for column, hint in {'A':'الزامی؛ کد یکتا، حداکثر ۴۰ نویسه، با قالب Text.', 'B':'الزامی؛ حداکثر ۱۶۰ نویسه.',
                     'C':'اختیاری؛ حداکثر ۱۶۰ نویسه.', 'D':'الزامی؛ بیشتر از صفر تا ۱۰۰۰۰ ساعت، حداکثر دو رقم اعشار.',
                     'E':'اختیاری؛ حداکثر ۲۰۰۰ نویسه.'}.items():
    data[f'{column}1'].comment = Comment(hint,'سامانه شایستگی')
validation = DataValidation(type='decimal',operator='between',formula1='0.01',formula2='10000',allow_blank=False)
validation.showErrorMessage = True
validation.errorTitle = 'مدت نامعتبر'
validation.error = 'عدد بین ۰٫۰۱ و ۱۰۰۰۰ ساعت وارد کنید؛ حداکثر دو رقم اعشار.'
data.add_data_validation(validation)
validation.add('D2:D1001')
OUTPUT.parent.mkdir(parents=True,exist_ok=True)
book.save(OUTPUT)
print(OUTPUT)

package ru.bmstu.schedule.pdfgen;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.itextpdf.layout.property.TextAlignment;
import ru.bmstu.schedule.entity.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class PDFUtils {

    private static final String FREE_SANS_FONT_PATH = "/font/FreeSans.ttf";
    private static final float[] TABLE_COLUMN_WIDTHS = {64, 208, 208};
    private static final float TABLE_WIDTH = 480;
    private static final float TABLE_MARGIN_TOP = 10;
    private static final float TABLE_MARGIN_BOTTOM = 10;
    private static final int DAYS_PER_PAGE = 3;
    private static final int NUMBER_OF_ITEMS = 7;
    private static PdfFont font;

    static {
        try {
            font = PdfFontFactory.createFont(FREE_SANS_FONT_PATH, "Cp1251", true);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }


    public static void exportToPdf(List<ClassTime> classTimes, StudyGroup group, String filePath) throws FileNotFoundException {
        File outFile = new File(filePath);

        System.out.println("directory exists");
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(outFile));
        Document doc = new Document(pdfDoc);
        doc.setTextAlignment(TextAlignment.CENTER);

        String docTitle = String.format("Расписание %s", cipherOf(group));
        doc.add(docHeaderParagraph(docTitle));

        int noOfWeak = 0;

        List<ScheduleDay> dayList = new ArrayList<>(group.getScheduleDays());

        final List<String> WEEK_ORDER = Arrays.asList("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ");

        dayList.sort((d1, d2) -> {
            int d1Idx = WEEK_ORDER.indexOf(d1.getDayOfWeek().getShortName().trim());
            int d2Idx = WEEK_ORDER.indexOf(d2.getDayOfWeek().getShortName().trim());

            return d1Idx - d2Idx;
        });

        System.out.println(dayList);

        for (ScheduleDay day : dayList) {
            if (noOfWeak == DAYS_PER_PAGE)
                doc.add(new AreaBreak());
            appendScheduleDay(classTimes, doc, day);
            noOfWeak++;
        }

        doc.close();

    }

    private static void appendScheduleDay(List<ClassTime> classTimes, Document doc, ScheduleDay scheduleDay) {
        Table table = new Table(TABLE_COLUMN_WIDTHS);
        table.setWidth(TABLE_WIDTH);
        table.setHorizontalAlignment(HorizontalAlignment.CENTER);

        table.setMarginTop(TABLE_MARGIN_TOP);
        table.setMarginBottom(TABLE_MARGIN_BOTTOM);

        table.addHeaderCell(cellHeaderParagraph("Время"));
        table.addHeaderCell(cellHeaderParagraph("ЧС"));
        table.addHeaderCell(cellHeaderParagraph("ЗН"));

        String parityType;

        List<ScheduleItem> items = new ArrayList<>(scheduleDay.getScheduleItems());
        items.sort(Comparator.comparing(c -> c.getClassTime().getStartsAt()));

        for (int i = 0; i < NUMBER_OF_ITEMS; i++) {
            ClassTime ct = classTimes.get(i);
            if (ct == null)
                continue;

            table.addCell(cellParagraph(ct.toString()));
            if (i < items.size()) {
                ScheduleItem item = items.get(i);

                List<ScheduleItemParity> parities = new ArrayList<>(item.getScheduleItemParities());
                parities.sort((p1, p2) -> {
                    if (p1.getDayParity().equals("ЧС") && p2.getDayParity().equals("ЗН"))
                        return 1;
                    else if (p1.getDayParity().equals("ЗН") && p2.getDayParity().equals("ЧС"))
                        return -1;

                    return 0;
                });

                System.out.println(item);

                if (parities.size() == 1) {
                    ScheduleItemParity parity = parities.get(0);
                    System.out.println("\t" + parity.toString());

                    parityType = parity.getDayParity().trim();

                    if (parityType.equals("ЧС/ЗН")) {
                        table.addCell(mergedCell(1, 2, readableItemParity(parity)));
                    } else if (parityType.equals("ЧС")) {
                        table.addCell(cellParagraph(readableItemParity(parity)));
                        table.addCell(emptyParagraph());
                    } else if (parityType.equals("ЗН")) {
                        table.addCell(emptyParagraph());
                        table.addCell(cellParagraph(readableItemParity(parity)));
                    }
                } else if (parities.size() == 2) {

                    ScheduleItemParity parity1 = parities.get(0), parity2 = parities.get(1);

                    System.out.println("\t" + parity1.toString());
                    System.out.println("\t" + parity2.toString());

                    if (parity1.getDayParity().trim().equals("ЧС")) {
                        table.addCell(cellParagraph(readableItemParity(parity1)));
                        table.addCell(cellParagraph(readableItemParity(parity2)));
                    } else if (parity2.getDayParity().trim().equals("ЧС")) {
                        table.addCell(cellParagraph(readableItemParity(parity2)));
                        table.addCell(cellParagraph(readableItemParity(parity1)));
                    }
                } else {
                    table.addCell(mergedCell(1, 2, ""));
                }

                table.startNewRow();
                System.out.println();
            } else {
                table.addCell(mergedCell(1, 2, ""));
                if (i < NUMBER_OF_ITEMS - 1) {
                    table.startNewRow();
                }
            }
        }

        doc.add(dayHeaderParagraph(scheduleDay.getDayOfWeek().getName()));
        doc.add(table);

    }

    private static String readableItemParity(ScheduleItemParity itemParity) {
        StringBuilder builder = new StringBuilder();
        ClassType ct = itemParity.getClassType();
        Classroom cr = itemParity.getClassroom();
        LecturerSubject lecturerSubject = itemParity.getLecturerSubject();

        Subject subj = lecturerSubject.getDepartmentSubject().getSubject();
        Lecturer lec = lecturerSubject.getLecturer();

        if (ct != null) {
            builder.append("(")
                    .append(ct.getName(), 0, 3)
                    .append(")  ");
        }
        if (subj != null) {
            builder.append(subj.getName())
                    .append("  ");
        }

        if (cr != null) {
            builder.append(cr.getRoomNumber())
                    .append("  ");
        }


        builder.append(lec.getLastName().equals("[UNKNOWN]") ? "" : lec.getInitials());

        return builder.toString();
    }

    private static String cipherOf(StudyGroup group) {
        Calendar calendar = group.getCalendar();
        EduDegree degree = calendar
                .getDepartmentSpecialization()
                .getSpecialization()
                .getSpeciality()
                .getDegree();
        char degreeLetter = degree.getName().toUpperCase().charAt(0);
        return String.format(
                "%s-%d%d%s",
                calendar.getDepartmentSpecialization().getDepartment().getCipher(),
                group.getTerm().getNumber(),
                group.getNumber(),
                (degreeLetter == 'Б' ? "" : degreeLetter)
        );
    }

    private static Paragraph docHeaderParagraph(String text) {
        return new Paragraph(text)
                .setFont(font)
                .setFontSize(22)
                .setBold()
                .setMarginBottom(15);
    }

    private static Paragraph cellHeaderParagraph(String text) {
        return new Paragraph(text).setFont(font).setItalic();
    }

    private static Paragraph dayHeaderParagraph(String text) {
        return new Paragraph(text)
                .setFontSize(18)
                .setFont(font)
                .setBold();
    }

    private static Paragraph cellParagraph(String text) {
        return new Paragraph(text)
                .setFont(font)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER);
    }

    private static Cell mergedCell(int rowspan, int colspan, String text) {
        return new Cell(rowspan, colspan).add(cellParagraph(text));
    }

    private static Paragraph emptyParagraph() {
        return new Paragraph("");
    }

}

package ru.subsidy.docproc.text;

/**
 * Текст страницы файла.
 *
 * @param page номер страницы с 1
 * @param text текст
 * @param source TEXT_LAYER, OCR, OFFICE или NONE (текста нет — только картинка)
 */
public record PageText(int page, String text, String source) {

    /** Текстовый слой PDF. */
    public static final String TEXT_LAYER = "TEXT_LAYER";
    /** Распознано OCR. */
    public static final String OCR = "OCR";
    /** DOCX/XLSX. */
    public static final String OFFICE = "OFFICE";
    /** Текста нет. */
    public static final String NONE = "NONE";

    /**
     * Есть ли на странице текст.
     *
     * @return true, если текст не пустой
     */
    public boolean hasText() {
        return !NONE.equals(source) && text != null && !text.isBlank();
    }
}

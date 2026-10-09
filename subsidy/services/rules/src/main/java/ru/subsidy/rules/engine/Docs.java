package ru.subsidy.rules.engine;

import java.util.Map;

/**
 * Виды документов и имена полей (docproc doc-types/registry.json) и их названия для текстов.
 */
public final class Docs {

    /** Заявление. */
    public static final String APPLICATION = "APPLICATION";
    /** Выписка ЕГРЮЛ. */
    public static final String EGRUL = "EGRUL_EXTRACT";
    /** Справка ФНС. */
    public static final String TAX = "TAX_CERTIFICATE";
    /** Отчётность. */
    public static final String FIN = "FIN_STATEMENTS";
    /** Смета. */
    public static final String ESTIMATE = "COST_ESTIMATE";
    /** Договор. */
    public static final String CONTRACT = "CONTRACT";
    /** Счёт. */
    public static final String INVOICE = "INVOICE";
    /** Платёжное поручение. */
    public static final String PAYMENT = "PAYMENT_ORDER";
    /** Прочее. */
    public static final String OTHER = "OTHER";

    /** ИНН. */
    public static final String INN = "inn";
    /** ОГРН. */
    public static final String OGRN = "ogrn";
    /** Наименование. */
    public static final String ORG_NAME = "orgName";
    /** Дата. */
    public static final String DATE = "date";
    /** Сумма. */
    public static final String AMOUNT = "amount";
    /** Итог. */
    public static final String TOTAL = "total";
    /** Позиции. */
    public static final String ITEMS = "items";
    /** ИНН покупателя. */
    public static final String BUYER_INN = "buyerInn";
    /** ИНН продавца. */
    public static final String SELLER_INN = "sellerInn";
    /** ИНН плательщика. */
    public static final String PAYER_INN = "payerInn";
    /** ИНН получателя. */
    public static final String PAYEE_INN = "payeeInn";
    /** БИК. */
    public static final String BIK = "bik";
    /** НДС. */
    public static final String VAT = "vatAmount";
    /** Запрошенная сумма. */
    public static final String REQUESTED = "requestedAmount";
    /** Сумма субсидии в смете. */
    public static final String SUBSIDY = "subsidyAmount";

    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry(APPLICATION, "Заявление"),
            Map.entry(EGRUL, "Выписка ЕГРЮЛ"),
            Map.entry(TAX, "Справка ФНС"),
            Map.entry(FIN, "Отчётность"),
            Map.entry(ESTIMATE, "Смета"),
            Map.entry(CONTRACT, "Договор"),
            Map.entry(INVOICE, "Счёт"),
            Map.entry(PAYMENT, "Платёжное поручение"),
            Map.entry(OTHER, "Прочий документ"));

    private Docs() {
    }

    /**
     * Название вида для текста специалисту.
     *
     * @param type код вида
     * @return «Счёт», «Выписка ЕГРЮЛ», …
     */
    public static String title(String type) {
        return TITLES.getOrDefault(type, type == null ? "Документ" : type);
    }

    /**
     * Только цифры.
     *
     * @param value строка
     * @return цифры
     */
    public static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}

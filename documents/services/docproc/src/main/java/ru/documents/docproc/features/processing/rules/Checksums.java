package ru.documents.docproc.features.processing.rules;

/**
 * Контрольные суммы номеров (docs/DOC-TYPES.md «Алгоритмы проверок»).
 */
public final class Checksums {

    private static final int[] INN_11 = {7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] INN_12 = {3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] MRZ_WEIGHTS = {7, 3, 1};

    private Checksums() {
    }

    /**
     * СНИЛС: 11 цифр, контрольное число по алгоритму ПФР.
     *
     * @param digits только цифры
     * @return верно
     */
    public static boolean snils(String digits) {
        if (digits == null || !digits.matches("\\d{11}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (digits.charAt(i) - '0') * (9 - i);
        }
        int check;
        if (sum < 100) {
            check = sum;
        } else if (sum == 100 || sum == 101) {
            check = 0;
        } else {
            check = sum % 101;
            if (check == 100) {
                check = 0;
            }
        }
        return check == Integer.parseInt(digits.substring(9));
    }

    /**
     * ИНН физического лица: 12 цифр, два контрольных разряда.
     *
     * @param digits только цифры
     * @return верно
     */
    public static boolean innPerson(String digits) {
        if (digits == null || !digits.matches("\\d{12}")) {
            return false;
        }
        return weighted(digits, INN_11) == digits.charAt(10) - '0' && weighted(digits, INN_12) == digits.charAt(11) - '0';
    }

    private static int weighted(String d, int[] w) {
        int s = 0;
        for (int i = 0; i < w.length; i++) {
            s += (d.charAt(i) - '0') * w[i];
        }
        return s % 11 % 10;
    }

    /**
     * Контрольная цифра MRZ (ICAO 9303, веса 7-3-1).
     *
     * @param s поле MRZ
     * @return цифра
     */
    public static int mrz(String s) {
        int sum = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int v;
            if (Character.isDigit(c)) {
                v = c - '0';
            } else if (c >= 'A' && c <= 'Z') {
                v = c - 'A' + 10;
            } else {
                v = 0;
            }
            sum += v * MRZ_WEIGHTS[i % 3];
        }
        return sum % 10;
    }
}

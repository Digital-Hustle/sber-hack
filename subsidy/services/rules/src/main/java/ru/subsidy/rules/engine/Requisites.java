package ru.subsidy.rules.engine;

/**
 * Контрольные суммы реквизитов (docs/DOC-TYPES.md §Контрольные суммы).
 */
public final class Requisites {

    private static final int[] INN10 = {2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] INN12_11 = {7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] INN12_12 = {3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] ACCOUNT = {7, 1, 3};
    private static final int MOD11 = 11;
    private static final int MOD10 = 10;
    private static final int MOD13 = 13;
    private static final int INN_LEGAL = 10;
    private static final int INN_PERSON = 12;
    private static final int OGRN = 13;
    private static final int OGRNIP = 15;
    private static final int BIK = 9;
    private static final int ACCOUNT_LEN = 20;
    private static final int BIK_TAIL = 3;

    private Requisites() {
    }

    /**
     * ИНН юрлица (10) или физлица/ИП (12).
     *
     * @param inn цифры
     * @return true, если контрольные разряды сходятся
     */
    public static boolean inn(String inn) {
        if (!digits(inn) || inn.length() != INN_LEGAL && inn.length() != INN_PERSON) {
            return false;
        }
        if (inn.length() == INN_LEGAL) {
            return control(inn, INN10) == digit(inn, INN_LEGAL - 1);
        }
        return control(inn, INN12_11) == digit(inn, INN_PERSON - 2)
                && control(inn, INN12_12) == digit(inn, INN_PERSON - 1);
    }

    /**
     * ОГРН (13) или ОГРНИП (15).
     *
     * @param ogrn цифры
     * @return true, если контрольная цифра сходится
     */
    public static boolean ogrn(String ogrn) {
        if (!digits(ogrn) || ogrn.length() != OGRN && ogrn.length() != OGRNIP) {
            return false;
        }
        long body = Long.parseLong(ogrn.substring(0, ogrn.length() - 1));
        long mod = ogrn.length() == OGRN ? body % MOD11 : body % MOD13;
        return mod % MOD10 == digit(ogrn, ogrn.length() - 1);
    }

    /**
     * КПП: NNNNPPXXX.
     *
     * @param kpp строка
     * @return true, если формат верный
     */
    public static boolean kpp(String kpp) {
        return kpp != null && kpp.matches("\\d{4}[0-9A-Z]{2}\\d{3}");
    }

    /**
     * БИК: 9 цифр, начинается с 04.
     *
     * @param bik строка
     * @return true, если формат верный
     */
    public static boolean bik(String bik) {
        return digits(bik) && bik.length() == BIK && bik.startsWith("04");
    }

    /**
     * Расчётный счёт по ключу ЦБ вместе с БИК.
     *
     * @param account 20 цифр
     * @param bik БИК
     * @return true, если ключ сходится
     */
    public static boolean account(String account, String bik) {
        if (!digits(account) || account.length() != ACCOUNT_LEN || !bik(bik)) {
            return false;
        }
        String full = bik.substring(BIK - BIK_TAIL) + account;
        int sum = 0;
        for (int idx = 0; idx < full.length(); idx++) {
            sum += (full.charAt(idx) - '0') * ACCOUNT[idx % ACCOUNT.length] % MOD10;
        }
        return sum % MOD10 == 0;
    }

    private static int control(String value, int[] weights) {
        int sum = 0;
        for (int idx = 0; idx < weights.length; idx++) {
            sum += weights[idx] * digit(value, idx);
        }
        return sum % MOD11 % MOD10;
    }

    private static int digit(String value, int idx) {
        return value.charAt(idx) - '0';
    }

    private static boolean digits(String value) {
        return value != null && !value.isEmpty() && value.chars().allMatch(Character::isDigit);
    }
}

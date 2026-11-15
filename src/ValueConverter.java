import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Преобразует текстовое значение из запроса в нужный Java-тип.
 * Здесь же проверяется, подходит ли значение под тип столбца.
 */
public class ValueConverter {
    /** Литерал из запроса -> Java-значение нужного типа. */
    public static Object convert(String literal, DataType type, String columnName) throws DbException {
        boolean quoted = literal.startsWith("'");

        // NULL пишется без кавычек
        if (!quoted && literal.equalsIgnoreCase("NULL")) {
            return null;
        }

        // убираем кавычки у строковых литералов
        String text = literal;
        if (quoted) {
            text = literal.substring(1, literal.length() - 1);
        }

        if (type == DataType.INT) {
            if (quoted) {
                throw new DbException("Столбец " + columnName + " (INT): число пишется без кавычек");
            }
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw new DbException("Столбец " + columnName + " (INT): '" + text + "' не целое число");
            }
        }

        if (type == DataType.DOUBLE) {
            if (quoted) {
                throw new DbException("Столбец " + columnName + " (DOUBLE): число пишется без кавычек");
            }
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                throw new DbException("Столбец " + columnName + " (DOUBLE): '" + text + "' не число");
            }
        }

        if (type == DataType.STRING) {
            if (!quoted) {
                throw new DbException("Столбец " + columnName + " (STRING): строку нужно писать в одинарных кавычках");
            }
            return text;
        }

        if (type == DataType.DATE) {
            if (!quoted) {
                throw new DbException("Столбец " + columnName + " (DATE): дату нужно писать в кавычках, например '2026-11-15'");
            }
            try {
                return LocalDate.parse(text);
            } catch (DateTimeParseException e) {
                throw new DbException("Столбец " + columnName + " (DATE): '" + text + "' не дата вида ГГГГ-ММ-ДД");
            }
        }

        // BOOLEAN
        if (quoted) {
            throw new DbException("Столбец " + columnName + " (BOOLEAN): используйте TRUE или FALSE без кавычек");
        }
        if (text.equalsIgnoreCase("TRUE")) {
            return Boolean.TRUE;
        }
        if (text.equalsIgnoreCase("FALSE")) {
            return Boolean.FALSE;
        }
        throw new DbException("Столбец " + columnName + " (BOOLEAN): ожидалось TRUE или FALSE, получено " + text);
    }
}

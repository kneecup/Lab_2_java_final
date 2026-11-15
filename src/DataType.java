/**
 * Типы данных, которые поддерживает СУБД.
 */
public enum DataType {
    INT,
    DOUBLE,
    STRING,
    DATE,
    BOOLEAN;

    /**
     * Преобразует слово из запроса в тип данных.
     */
    public static DataType fromWord(String word) throws DbException {
        for (DataType type : DataType.values()) {
            if (type.name().equalsIgnoreCase(word)) {
                return type;
            }
        }
        throw new DbException("Неизвестный тип данных: " + word
                + " (доступны: INT, DOUBLE, STRING, DATE, BOOLEAN)");
    }
}

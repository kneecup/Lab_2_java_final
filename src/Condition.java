/**
 * Условие WHERE вида "столбец оператор значение".
 * Умеет проверять, подходит ли строка под условие.
 */
public class Condition {

    private final int columnIndex;
    private final String operator;  // = != < > <= >=
    private final Object value;

    /** Условие: столбец operator значение. */
    public Condition(int columnIndex, String operator, Object value) {
        this.columnIndex = columnIndex;
        this.operator = operator;
        this.value = value;
    }

    /** Номер столбца. */
    public int getColumnIndex() {
        return columnIndex;
    }

    /** Оператор сравнения. */
    public String getOperator() {
        return operator;
    }

    /** Значение для сравнения. */
    public Object getValue() {
        return value;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    /** Подходит ли строка под условие. */
    public boolean matches(Row row) {
        Object cell = row.get(columnIndex);
        if (cell == null || value == null) {
            return false;
        }

        // compareTo есть у Integer, Double, String, LocalDate, Boolean
        int result = ((Comparable) cell).compareTo(value);

        if (operator.equals("=")) {
            return result == 0;
        }
        if (operator.equals("!=")) {
            return result != 0;
        }
        if (operator.equals("<")) {
            return result < 0;
        }
        if (operator.equals(">")) {
            return result > 0;
        }
        if (operator.equals("<=")) {
            return result <= 0;
        }
        // остаётся ">="
        return result >= 0;
    }
}

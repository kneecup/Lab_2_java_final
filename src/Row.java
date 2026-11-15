import java.io.Serializable;

/**
 * Одна строка таблицы. Значения лежат в массиве
 * в том же порядке, что и столбцы.
 */
public class Row implements Serializable {

    private static final long serialVersionUID = 1L;

    // значения ячеек (null = NULL)
    @SuppressWarnings("serial")
    private final Object[] values;

    /** Строка с значениями в порядке столбцов. */
    public Row(Object[] values) {
        this.values = values;
    }

    /** Значение ячейки. */
    public Object get(int index) {
        return values[index];
    }

    /** Записать значение ячейки. */
    public void set(int index, Object value) {
        values[index] = value;
    }

    // возвращаем копию, чтобы снаружи нельзя было испортить строку
    /** Копия массива значений. */
    public Object[] copyValues() {
        return values.clone();
    }
}

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Индекс по одному столбцу.
 * Внутри TreeMap: значение -> список строк с этим значением.
 * Поиск по индексу O(log n), без индекса был бы полный перебор O(n).
 * Индекс сериализуется вместе с таблицей.
 */
public class Index implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final int columnIndex;
    private final TreeMap<Object, ArrayList<Row>> tree = new TreeMap<>();

    /** Индекс по столбцу columnIndex. */
    public Index(String name, int columnIndex) {
        this.name = name;
        this.columnIndex = columnIndex;
    }

    /** Имя индекса. */
    public String getName() {
        return name;
    }

    /** Номер индексируемого столбца. */
    public int getColumnIndex() {
        return columnIndex;
    }

    // --- изменение индекса ---
    /** Добавить строку в индекс. */
    public void add(Row row) {
        Object key = row.get(columnIndex);
        if (key == null) {
            return;
        }
        ArrayList<Row> list = tree.get(key);
        if (list == null) {
            list = new ArrayList<>();
            tree.put(key, list);
        }
        list.add(row);
    }
    /** Убрать строку из индекса (до изменения значения). */
    public void remove(Row row) {
        Object key = row.get(columnIndex);
        if (key == null) {
            return;
        }
        ArrayList<Row> list = tree.get(key);
        if (list == null) {
            return;
        }
        list.remove(row);
        if (list.isEmpty()) {
            tree.remove(key);
        }
    }

    // --- поиск ---

    /** Есть ли ключ в индексе. */
    public boolean containsKey(Object key) {
        return tree.containsKey(key);
    }
    /** Поиск по оператору = < > <= >=. */
    public List<Row> find(String operator, Object value) {
        List<Row> result = new ArrayList<>();

        if (operator.equals("=")) {
            ArrayList<Row> list = tree.get(value);
            if (list != null) {
                result.addAll(list);
            }
            return result;
        }

        // для остальных операторов берём нужную часть дерева
        Map<Object, ArrayList<Row>> part;
        if (operator.equals(">")) {
            part = tree.tailMap(value, false);
        } else if (operator.equals(">=")) {
            part = tree.tailMap(value, true);
        } else if (operator.equals("<")) {
            part = tree.headMap(value, false);
        } else {
            // "<="
            part = tree.headMap(value, true);
        }
        for (ArrayList<Row> list : part.values()) {
            result.addAll(list);
        }
        return result;
    }
}

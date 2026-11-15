import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Таблица: столбцы, строки, индексы.
 * Ввод-вывод только в Main. Внешние ключи проверяет Database.
 */
public class Table implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final ArrayList<Column> columns = new ArrayList<>();
    private ArrayList<Row> rows = new ArrayList<>();
    private final ArrayList<Index> indexes = new ArrayList<>();
    private int primaryKeyIndex = -1;

    /** Создаёт таблицу и индекс по первичному ключу. */
    public Table(String name, List<Column> columnsList) throws DbException {
        this.name = name;
        for (int i = 0; i < columnsList.size(); i++) {
            Column column = columnsList.get(i);
            for (Column other : columns) {
                if (other.name.equals(column.name)) {
                    throw new DbException("Столбец " + column.name + " указан дважды");
                }
            }
            columns.add(column);
            if (column.primaryKey) {
                if (primaryKeyIndex != -1) {
                    throw new DbException("В таблице может быть только один первичный ключ");
                }
                primaryKeyIndex = i;
            }
        }
        if (primaryKeyIndex == -1) {
            throw new DbException("В таблице должен быть первичный ключ (PRIMARY KEY)");
        }
        // индекс по PK для проверки уникальности
        indexes.add(new Index("pk_" + name, primaryKeyIndex));
    }

    /** Имя таблицы. */
    public String getName() {
        return name;
    }

    /** Список столбцов. */
    public List<Column> getColumns() {
        return java.util.Collections.unmodifiableList(columns);
    }

    /** Количество строк. */
    public int getRowCount() {
        return rows.size();
    }

    /** Номер столбца первичного ключа. */
    public int getPrimaryKeyIndex() {
        return primaryKeyIndex;
    }

    /** Номер столбца по имени. */
    public int getColumnIndex(String columnName) throws DbException {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name.equals(columnName)) {
                return i;
            }
        }
        throw new DbException("В таблице " + name + " нет столбца " + columnName);
    }

    /** Индекс по столбцу или null. */
    public Index findIndex(int columnIndex) {
        for (Index index : indexes) {
            if (index.getColumnIndex() == columnIndex) {
                return index;
            }
        }
        return null;
    }

    /** Есть ли значение в столбце. */
    public boolean hasValue(int columnIndex, Object value) {
        Index index = findIndex(columnIndex);
        if (index != null) {
            return index.containsKey(value);
        }
        for (Row row : rows) {
            if (value.equals(row.get(columnIndex))) {
                return true;
            }
        }
        return false;
    }

    // условие WHERE из текста запроса
    /** Условие WHERE из текста запроса. */
    public Condition makeCondition(String columnName, String operator, String literal) throws DbException {
        int index = getColumnIndex(columnName);
        Object value = ValueConverter.convert(literal, columns.get(index).type, columnName);
        if (value == null) {
            throw new DbException("Сравнение с NULL не поддерживается");
        }
        return new Condition(index, operator, value);
    }

    // собирает значения строки для INSERT
    /** Значения строки для INSERT (с приведением типов). */
    public Object[] buildRowValues(List<String> columnNames, List<String> literals) throws DbException {
        Object[] values = new Object[columns.size()];

        if (columnNames.isEmpty()) {
            if (literals.size() != columns.size()) {
                throw new DbException("Ожидалось значений: " + columns.size()
                        + ", получено: " + literals.size());
            }
            for (int i = 0; i < columns.size(); i++) {
                values[i] = ValueConverter.convert(literals.get(i), columns.get(i).type, columns.get(i).name);
            }
            return values;
        }

        if (columnNames.size() != literals.size()) {
            throw new DbException("Количество столбцов (" + columnNames.size()
                    + ") не равно количеству значений (" + literals.size() + ")");
        }
        for (int i = 0; i < columnNames.size(); i++) {
            int index = getColumnIndex(columnNames.get(i));
            values[index] = ValueConverter.convert(literals.get(i), columns.get(index).type, columns.get(index).name);
        }
        return values;
    }

    // --- вставка ---
    /** Вставка строки с проверкой NOT NULL и уникальности PK. */
    public Row insert(Object[] values) throws DbException {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).notNull && values[i] == null) {
                throw new DbException("Столбец " + columns.get(i).name + " не может быть NULL");
            }
        }
        Object key = values[primaryKeyIndex];
        if (findIndex(primaryKeyIndex).containsKey(key)) {
            throw new DbException("Нарушена уникальность первичного ключа: "
                    + columns.get(primaryKeyIndex).name + " = " + key + " уже есть");
        }
        Row row = new Row(values);
        rows.add(row);
        addToIndexes(row);
        return row;
    }

    /** Удаление конкретной строки (для отката INSERT). */
    public void removeRow(Row row) {
        removeFromIndexes(row);
        rows.remove(row);
    }

    // --- выборка (с индексом или без) ---
    /** Выборка по условию; useIndex — использовать индекс, если есть. */
    public List<Row> select(Condition condition, boolean useIndex) {
        if (condition == null) {
            return new ArrayList<>(rows);
        }
        if (useIndex && !condition.getOperator().equals("!=")) {
            Index index = findIndex(condition.getColumnIndex());
            if (index != null) {
                return index.find(condition.getOperator(), condition.getValue());
            }
        }
        // полный перебор
        List<Row> result = new ArrayList<>();
        for (Row row : rows) {
            if (condition.matches(row)) {
                result.add(row);
            }
        }
        return result;
    }

    // --- обновление ---
    /** Обновление строк по условию. */
    public int update(List<Integer> columnIndexes, List<Object> newValues, Condition condition) throws DbException {
        List<Row> found = select(condition, true);

        // сначала все проверки
        for (int i = 0; i < columnIndexes.size(); i++) {
            int colIndex = columnIndexes.get(i);
            Object newValue = newValues.get(i);

            if (columns.get(colIndex).notNull && newValue == null) {
                throw new DbException("Столбец " + columns.get(colIndex).name + " не может быть NULL");
            }
            if (colIndex == primaryKeyIndex) {
                if (found.size() > 1) {
                    throw new DbException("Нельзя присвоить один первичный ключ нескольким строкам");
                }
                if (found.size() == 1) {
                    Object oldKey = found.get(0).get(primaryKeyIndex);
                    if (!newValue.equals(oldKey) && findIndex(primaryKeyIndex).containsKey(newValue)) {
                        throw new DbException("Нарушена уникальность первичного ключа: " + newValue + " уже есть");
                    }
                }
            }
        }

        for (Row row : found) {
            removeFromIndexes(row);
            for (int i = 0; i < columnIndexes.size(); i++) {
                row.set(columnIndexes.get(i), newValues.get(i));
            }
            addToIndexes(row);
        }
        return found.size();
    }

    // --- удаление ---
    /** Удаление строк по условию. */
    public int delete(Condition condition) {
        ArrayList<Row> kept = new ArrayList<>();
        int deleted = 0;
        for (Row row : rows) {
            if (condition == null || condition.matches(row)) {
                removeFromIndexes(row);
                deleted++;
            } else {
                kept.add(row);
            }
        }
        rows = kept;
        return deleted;
    }

    /** Создание индекса по столбцу. */
    public void createIndex(String indexName, String columnName) throws DbException {
        int colIndex = getColumnIndex(columnName);
        for (Index index : indexes) {
            if (index.getName().equals(indexName)) {
                throw new DbException("Индекс " + indexName + " уже существует");
            }
        }
        if (findIndex(colIndex) != null) {
            throw new DbException("Для столбца " + columnName + " индекс уже есть");
        }
        Index newIndex = new Index(indexName, colIndex);
        for (Row row : rows) {
            newIndex.add(row);
        }
        indexes.add(newIndex);
    }

    private void addToIndexes(Row row) {
        for (Index index : indexes) {
            index.add(row);
        }
    }

    private void removeFromIndexes(Row row) {
        for (Index index : indexes) {
            index.remove(row);
        }
    }
}

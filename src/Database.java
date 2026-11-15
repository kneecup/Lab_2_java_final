import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * База данных: таблицы, сохранение на диск, выполнение запросов.
 * Проверяет внешние ключи. В консоль ничего не пишет.
 */
public class Database {

    private final Map<String, Table> tables = new LinkedHashMap<>();
    private final File folder;
    private final List<String> warnings = new ArrayList<>();

    /** Открывает базу: создаёт папку и загружает .tbl файлы. */
    public Database(String folderName) throws DbException {
        folder = new File(folderName);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new DbException("Не удалось создать папку данных: " + folderName);
        }
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            // недописанные .tmp после сбоя
            if (file.getName().endsWith(".tmp")) {
                file.delete();
                warnings.add("Удалён недописанный временный файл " + file.getName());
            } else if (file.getName().endsWith(".tbl")) {
                loadTable(file);
            }
        }
    }

    private void loadTable(File file) {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            Table table = (Table) in.readObject();
            tables.put(table.getName(), table);
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            warnings.add("Файл " + file.getName() + " повреждён и пропущен (" + e.getMessage() + ")");
        }
    }

    /** Предупреждения при загрузке (повреждённые файлы и т.п.). */
    public List<String> getWarnings() {
        return warnings;
    }

    // запись через временный файл, потом атомарная замена
    /** Сохранение таблицы через временный файл. */
    public void saveTable(Table table) throws DbException {
        File tmpFile = new File(folder, table.getName() + ".tbl.tmp");
        File realFile = new File(folder, table.getName() + ".tbl");
        try {
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(tmpFile))) {
                out.writeObject(table);
            }
            Files.move(tmpFile.toPath(), realFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DbException("Не удалось сохранить таблицу " + table.getName() + ": " + e.getMessage(), e);
        }
    }

    /** Таблица по имени. */
    public Table getTable(String name) throws DbException {
        Table table = tables.get(name);
        if (table == null) {
            throw new DbException("Таблица " + name + " не найдена");
        }
        return table;
    }

    /** true, если таблиц нет. */
    public boolean isEmpty() {
        return tables.isEmpty();
    }

    // таблицы предметной области при первом запуске
    /** Регистрация уже созданной таблицы и сохранение на диск. */
    public void registerTable(Table table) throws DbException {
        if (tables.containsKey(table.getName())) {
            throw new DbException("Таблица " + table.getName() + " уже существует");
        }
        tables.put(table.getName(), table);
        saveTable(table);
    }

    /** Создаёт donors и recipients при первом запуске. */
    public void createDefaultTables() throws DbException {
        java.util.List<Column> donorCols = new java.util.ArrayList<>();
        donorCols.add(new Column("donor_id", DataType.INT, true, true, null, null));
        donorCols.add(new Column("full_name", DataType.STRING, false, true, null, null));
        donorCols.add(new Column("blood_type", DataType.STRING, false, true, null, null));
        donorCols.add(new Column("weight_kg", DataType.DOUBLE, false, false, null, null));
        Table donors = new Table("donors", donorCols);
        tables.put("donors", donors);
        saveTable(donors);

        java.util.List<Column> recipientCols = new java.util.ArrayList<>();
        recipientCols.add(new Column("recipient_id", DataType.INT, true, true, null, null));
        recipientCols.add(new Column("full_name", DataType.STRING, false, true, null, null));
        recipientCols.add(new Column("blood_type", DataType.STRING, false, true, null, null));
        recipientCols.add(new Column("donor_id", DataType.INT, false, false, "donors", "donor_id"));
        recipientCols.add(new Column("request_date", DataType.DATE, false, true, null, null));
        recipientCols.add(new Column("urgent", DataType.BOOLEAN, false, true, null, null));
        Table recipients = new Table("recipients", recipientCols);
        tables.put("recipients", recipients);
        saveTable(recipients);
    }

    /** Выполнение разобранного запроса. */
    public QueryResult execute(Query query) throws DbException {
        switch (query.type) {
            case CREATE_INDEX:
                return createIndex(query);
            case INSERT:
                return insert(query);
            case SELECT:
                return select(query);
            case UPDATE:
                return update(query);
            case DELETE:
                return delete(query);
            default:
                return showTables();
        }
    }
    private QueryResult createIndex(Query query) throws DbException {
        Table table = getTable(query.tableName);
        table.createIndex(query.indexName, query.columnNames.get(0));
        saveTable(table);
        return message("Индекс " + query.indexName + " создан");
    }

    // SHOW TABLES: одна строка на столбец, чтобы вывод был узким и не переносился в консоли
    private QueryResult showTables() {
        QueryResult result = new QueryResult();
        result.hasTable = true;
        result.columnNames.add("table");
        result.columnNames.add("column");
        result.columnNames.add("type");
        result.columnNames.add("key");

        StringBuilder counts = new StringBuilder();
        for (Table table : tables.values()) {
            for (Column column : table.getColumns()) {
                String key = "";
                if (column.primaryKey) {
                    key = "PK";
                }
                if (column.refTable != null) {
                    key = "FK -> " + column.refTable + "." + column.refColumn;
                }
                result.rows.add(new Object[]{table.getName(), column.name, column.type, key});
            }
            if (counts.length() > 0) {
                counts.append(", ");
            }
            counts.append(table.getName()).append(": ").append(table.getRowCount());
        }
        result.message = "Таблиц: " + tables.size() + " (строк: " + counts + ")";
        return result;
    }

    // INSERT: при ошибке откатываем уже добавленные строки
    private QueryResult insert(Query query) throws DbException {
        Table table = getTable(query.tableName);
        List<Row> added = new ArrayList<>();
        try {
            for (List<String> literals : query.valuesList) {
                Object[] values = table.buildRowValues(query.columnNames, literals);
                checkForeignKeys(table, values);
                added.add(table.insert(values));
            }
        } catch (DbException e) {
            for (Row row : added) {
                table.removeRow(row);
            }
            throw e;
        }
        saveTable(table);
        return message("Добавлено строк: " + added.size());
    }

    private void checkForeignKeys(Table table, Object[] values) throws DbException {
        for (int i = 0; i < table.getColumns().size(); i++) {
            Column column = table.getColumns().get(i);
            if (column.refTable != null && values[i] != null) {
                checkReferenceExists(column, values[i]);
            }
        }
    }

    private void checkReferenceExists(Column column, Object value) throws DbException {
        Table refTable = getTable(column.refTable);
        int refIndex = refTable.getColumnIndex(column.refColumn);
        if (!refTable.hasValue(refIndex, value)) {
            throw new DbException("Нарушен внешний ключ: в " + column.refTable + "."
                    + column.refColumn + " нет значения " + value);
        }
    }

    private QueryResult select(Query query) throws DbException {
        Table table = getTable(query.tableName);
        Condition condition = makeCondition(table, query);

        List<Integer> columnIndexes = new ArrayList<>();
        if (query.selectAll) {
            for (int i = 0; i < table.getColumns().size(); i++) {
                columnIndexes.add(i);
            }
        } else {
            for (String name : query.columnNames) {
                columnIndexes.add(table.getColumnIndex(name));
            }
        }

        QueryResult result = new QueryResult();
        result.hasTable = true;
        for (int index : columnIndexes) {
            result.columnNames.add(table.getColumns().get(index).name);
        }

        boolean usedIndex = condition != null && !condition.getOperator().equals("!=")
                && table.findIndex(condition.getColumnIndex()) != null;

        List<Row> found = table.select(condition, true);
        for (Row row : found) {
            Object[] line = new Object[columnIndexes.size()];
            for (int i = 0; i < columnIndexes.size(); i++) {
                line[i] = row.get(columnIndexes.get(i));
            }
            result.rows.add(line);
        }
        result.message = "Найдено строк: " + found.size() + (usedIndex ? " (использован индекс)" : "");
        return result;
    }

    private QueryResult update(Query query) throws DbException {
        Table table = getTable(query.tableName);
        Condition condition = makeCondition(table, query);

        List<Integer> columnIndexes = new ArrayList<>();
        List<Object> newValues = new ArrayList<>();
        for (int i = 0; i < query.setColumns.size(); i++) {
            int index = table.getColumnIndex(query.setColumns.get(i));
            Column column = table.getColumns().get(index);
            columnIndexes.add(index);
            newValues.add(ValueConverter.convert(query.setValues.get(i), column.type, column.name));
        }

        List<Row> affected = table.select(condition, true);
        for (int i = 0; i < columnIndexes.size(); i++) {
            int index = columnIndexes.get(i);
            Column column = table.getColumns().get(index);
            Object newValue = newValues.get(i);

            if (column.refTable != null && newValue != null && !affected.isEmpty()) {
                checkReferenceExists(column, newValue);
            }
            for (Row row : affected) {
                Object oldValue = row.get(index);
                if (!Objects.equals(oldValue, newValue) && oldValue != null && isReferenced(table, index, oldValue)) {
                    throw new DbException("Нельзя изменить " + table.getName() + "." + column.name
                            + " = " + oldValue + ": на это значение ссылаются другие таблицы");
                }
            }
        }

        int count = table.update(columnIndexes, newValues, condition);
        saveTable(table);
        return message("Обновлено строк: " + count);
    }

    private QueryResult delete(Query query) throws DbException {
        Table table = getTable(query.tableName);
        Condition condition = makeCondition(table, query);

        List<Row> affected = table.select(condition, true);
        for (Row row : affected) {
            for (int i = 0; i < table.getColumns().size(); i++) {
                Object value = row.get(i);
                if (value != null && isReferenced(table, i, value)) {
                    throw new DbException("Нельзя удалить: " + table.getName() + "."
                            + table.getColumns().get(i).name + " = " + value
                            + " используется в другой таблице");
                }
            }
        }

        int count = table.delete(condition);
        saveTable(table);
        return message("Удалено строк: " + count);
    }

    // есть ли ссылка на это значение из другой таблицы
    private boolean isReferenced(Table table, int columnIndex, Object value) {
        String columnName = table.getColumns().get(columnIndex).name;
        for (Table other : tables.values()) {
            for (int i = 0; i < other.getColumns().size(); i++) {
                Column column = other.getColumns().get(i);
                boolean pointsHere = table.getName().equals(column.refTable)
                        && columnName.equals(column.refColumn);
                if (pointsHere && other.hasValue(i, value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Condition makeCondition(Table table, Query query) throws DbException {
        if (query.whereColumn == null) {
            return null;
        }
        return table.makeCondition(query.whereColumn, query.whereOperator, query.whereValue);
    }

    private QueryResult message(String text) {
        QueryResult result = new QueryResult();
        result.message = text;
        return result;
    }
}

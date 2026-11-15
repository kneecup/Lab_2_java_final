import java.util.ArrayList;
import java.util.List;

/**
 * Разобранный SQL-запрос.
 * Парсер заполняет поля, Database выполняет.
 * Логики и ввода-вывода здесь нет.
 */
public class Query {

    public enum Type {
        CREATE_INDEX, INSERT, SELECT, UPDATE, DELETE, SHOW_TABLES
    }

    public Type type;
    public String tableName;

    // CREATE INDEX
    public String indexName;

    // INSERT / SELECT
    public List<String> columnNames = new ArrayList<>();
    public boolean selectAll;
    public List<List<String>> valuesList = new ArrayList<>();

    // UPDATE SET
    public List<String> setColumns = new ArrayList<>();
    public List<String> setValues = new ArrayList<>();

    // WHERE (null, если WHERE нет)
    public String whereColumn;
    public String whereOperator;
    public String whereValue;
}

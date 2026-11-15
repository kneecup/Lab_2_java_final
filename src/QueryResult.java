import java.util.ArrayList;
import java.util.List;

/**
 * Результат выполнения запроса.
 * Сообщение + (для SELECT) таблица значений.
 * Печатает их только Main.
 */
public class QueryResult {

    public String message = "";
    public List<String> columnNames = new ArrayList<>();
    public List<Object[]> rows = new ArrayList<>();
    public boolean hasTable;
}

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

/**
 * Консольный интерфейс СУБД.
 * Только здесь есть ввод с клавиатуры и вывод на экран.
 */
public class Main {

    private static final String DATA_FOLDER = "data";

    /** Точка входа: открывает базу и цикл команд. */
    public static void main(String[] args) {

    // для отображения русского языка в Windows
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }

        Database database;

        try {
            database = new Database(DATA_FOLDER);
        } catch (DbException e) {
            System.out.println("Ошибка запуска: " + e.getMessage());
            return;
        }
        for (String warning : database.getWarnings()) {
            System.out.println("Предупреждение: " + warning);
        }

        if (database.isEmpty()) {
            System.out.println("База пуста, создаю таблицы donors и recipients...");
            try {
                database.createDefaultTables();
            } catch (DbException e) {
                System.out.println("Ошибка создания таблиц: " + e.getMessage());
                return;
            }
        }

        System.out.println("СУБД запущена. Введите help для справки, exit для выхода.");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("sql> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            }
            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                break;
            }
            if (line.equalsIgnoreCase("help")) {
                printHelp();
            } else if (line.toLowerCase().startsWith("run ")) {
                runScript(database, line.substring(4).trim());
            } else {
                runQuery(database, line);
            }
        }
        System.out.println("Данные сохранены. До свидания!");
    }

    // выполнение одного запроса (ошибки не роняют программу)
    private static void runQuery(Database database, String sql) {
        try {
            SqlParser parser = new SqlParser();
            Query query = parser.parse(sql);
            QueryResult result = database.execute(query);
            printResult(result);
        } catch (DbException e) {
            System.out.println("Ошибка: " + e.getMessage());
        } catch (RuntimeException e) {
            System.out.println("Внутренняя ошибка: " + e);
        }
    }

    private static void runScript(Database database, String fileName) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(fileName), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("Ошибка: не удалось прочитать файл " + fileName);
            return;
        }
        for (String line : lines) {
            String sql = line.trim();
            if (sql.isEmpty() || sql.startsWith("--")) {
                continue;
            }
            System.out.println("sql> " + sql);
            runQuery(database, sql);
        }
    }

    private static void printResult(QueryResult result) {
        if (result.hasTable) {
            printTable(result);
        }
        System.out.println(result.message);
    }

    private static void printTable(QueryResult result) {
        int count = result.columnNames.size();

        int[] width = new int[count];
        for (int i = 0; i < count; i++) {
            width[i] = result.columnNames.get(i).length();
        }
        for (Object[] row : result.rows) {
            for (int i = 0; i < count; i++) {
                width[i] = Math.max(width[i], toText(row[i]).length());
            }
        }

        StringBuilder line = new StringBuilder("+");
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < width[i] + 2; j++) {
                line.append("-");
            }
            line.append("+");
        }

        System.out.println(line);
        System.out.println(makeRow(result.columnNames.toArray(), width));
        System.out.println(line);
        for (Object[] row : result.rows) {
            System.out.println(makeRow(row, width));
        }
        System.out.println(line);
    }

    private static String makeRow(Object[] cells, int[] width) {
        StringBuilder text = new StringBuilder("|");
        for (int i = 0; i < cells.length; i++) {
            String cell = toText(cells[i]);
            text.append(" ").append(cell);
            for (int j = cell.length(); j < width[i]; j++) {
                text.append(" ");
            }
            text.append(" |");
        }
        return text.toString();
    }

    private static String toText(Object value) {
        if (value == null) {
            return "NULL";
        }
        return value.toString();
    }

    private static void printHelp() {
        System.out.println("Команды:");
        System.out.println("  CREATE INDEX idx_name ON donors (full_name);");
        System.out.println("  INSERT INTO donors VALUES (1, 'Иванов И.И.', 'A+', 80.5);");
        System.out.println("  INSERT INTO donors (donor_id, full_name, blood_type) VALUES (2, 'Петров П.П.', 'B+');");
        System.out.println("  INSERT INTO donors VALUES (3, 'A', 'O+', 70.0), (4, 'B', 'AB-', 65.2);");
        System.out.println("  SELECT * FROM donors;");
        System.out.println("  SELECT full_name, weight_kg FROM donors WHERE weight_kg > 70;");
        System.out.println("  UPDATE donors SET weight_kg = 82 WHERE donor_id = 1;");
        System.out.println("  DELETE FROM donors WHERE blood_type = 'A+';");
        System.out.println("  SHOW TABLES;");
        System.out.println("  RUN файл.sql   - выполнить запросы из файла");
        System.out.println("  EXIT           - выход");
        System.out.println("Правила: строки и даты в одинарных кавычках ('2026-11-15'), TRUE/FALSE и числа без кавычек.");
        System.out.println("Операторы WHERE: = != < > <= >=");
    }
}

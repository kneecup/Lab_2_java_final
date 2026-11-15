import java.io.File;
import java.nio.file.Files;
import java.util.Random;

/**
 * Тест производительности: сравнивает скорость SELECT без индекса и с индексом.
 * Запуск: java -cp out PerformanceTest
 * Работает во временной папке, основную базу не трогает.
 */
public class PerformanceTest {

    // сколько раз повторяем запрос, чтобы усреднить время
    private static final int REPEATS = 200;

    private static final String[] BLOOD_TYPES = {
            "O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-"
    };

    /** Замер SELECT с индексом и без. */
    public static void main(String[] args) throws Exception {
        // размеры таблиц для замеров
        int[] sizes = {1000, 10000, 50000, 100000};

        System.out.println("Запросов в каждом замере: " + REPEATS);
        System.out.println("Время одного запроса в микросекундах (мкс)");
        System.out.println();
        System.out.println("N строк | Запрос                         | Без индекса | С индексом | Ускорение | Найдено");
        System.out.println("--------+--------------------------------+-------------+------------+-----------+--------");

        // небольшой прогрев JVM
        runForSize(2000, false);

        for (int size : sizes) {
            runForSize(size, true);
        }
    }

    private static void runForSize(int size, boolean print) throws Exception {
        File folder = Files.createTempDirectory("perf_db").toFile();
        try {
            Database database = new Database(folder.getPath());
            SqlParser parser = new SqlParser();

            // создаём таблицу donors
            java.util.List<Column> cols = new java.util.ArrayList<>();
            cols.add(new Column("donor_id", DataType.INT, true, true, null, null));
            cols.add(new Column("full_name", DataType.STRING, false, true, null, null));
            cols.add(new Column("blood_type", DataType.STRING, false, true, null, null));
            cols.add(new Column("weight_kg", DataType.DOUBLE, false, false, null, null));
            Table table = new Table("donors", cols);
            database.registerTable(table);
            Random random = new Random(42);
            for (int i = 1; i <= size; i++) {
                String bloodType = BLOOD_TYPES[random.nextInt(BLOOD_TYPES.length)];
                double weight = 50 + random.nextInt(600) / 10.0;
                table.insert(new Object[]{i, "Donor " + i, bloodType, weight});
            }

            // строим индексы
            database.execute(parser.parse("CREATE INDEX idx_donors_name ON donors (full_name)"));
            database.execute(parser.parse("CREATE INDEX idx_donors_blood ON donors (blood_type)"));
            database.execute(parser.parse("CREATE INDEX idx_donors_weight ON donors (weight_kg)"));

            int middle = size / 2;
            if (print) {
                measure(table, size, "full_name = 'Donor " + middle + "'",
                        table.makeCondition("full_name", "=", "'Donor " + middle + "'"));
                measure(table, size, "weight_kg > 109.5 (редкие)",
                        table.makeCondition("weight_kg", ">", "109.5"));
                measure(table, size, "blood_type = 'A+' (примерно 1/8)",
                        table.makeCondition("blood_type", "=", "'A+'"));
            }
        } finally {
            // чистим временную папку
            deleteFolder(folder);
        }
    }

    private static void measure(Table table, int size, String queryText, Condition condition)
            throws Exception {
        // без индекса
        long start = System.nanoTime();
        int foundWithout = 0;
        for (int i = 0; i < REPEATS; i++) {
            foundWithout = table.select(condition, false).size();
        }
        long timeWithout = (System.nanoTime() - start) / REPEATS / 1000;

        // с индексом
        start = System.nanoTime();
        int foundWith = 0;
        for (int i = 0; i < REPEATS; i++) {
            foundWith = table.select(condition, true).size();
        }
        long timeWith = (System.nanoTime() - start) / REPEATS / 1000;

        double speedup = timeWith > 0 ? (double) timeWithout / timeWith : 0;

        System.out.printf("%7d | %-30s | %11d | %10d | %8.1f× | %6d%n",
                size, queryText, timeWithout, timeWith, speedup, foundWith);

        // на всякий случай проверяем, что количество строк совпадает
        if (foundWithout != foundWith) {
            System.out.println("  !!! количество строк не совпало: " + foundWithout + " vs " + foundWith);
        }
    }

    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        folder.delete();
    }
}

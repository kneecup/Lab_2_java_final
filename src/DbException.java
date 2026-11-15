/**
 * Исключение СУБД. Выбрасывается при ошибке пользователя или данных
 * (неверный запрос, неверный тип, нарушение ключа и т.д.).
 * Main ловит его и показывает сообщение, программа не падает.
 */
public class DbException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Ошибка с текстом для пользователя. */
    public DbException(String message) {
        super(message);
    }

    /** Ошибка с причиной (например, IOException). */
    public DbException(String message, Throwable cause) {
        super(message, cause);
    }
}

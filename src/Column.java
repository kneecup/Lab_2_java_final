import java.io.Serializable;

/**
 * Описание одного столбца таблицы (имя, тип, ограничения).
 */
public class Column implements Serializable {

    private static final long serialVersionUID = 1L;

    public final String name;
    public final DataType type;
    public final boolean primaryKey;
    public final boolean notNull;
    public final String refTable;   // таблица, на которую ссылается FK (или null)
    public final String refColumn;  // столбец FK (или null)

    /** Описание столбца (имя, тип, PK, NOT NULL, внешний ключ). */
    public Column(String name, DataType type, boolean primaryKey, boolean notNull,
                  String refTable, String refColumn) {
        this.name = name;
        this.type = type;
        this.primaryKey = primaryKey;
        // первичный ключ всегда NOT NULL
        this.notNull = notNull || primaryKey;
        this.refTable = refTable;
        this.refColumn = refColumn;
    }
}

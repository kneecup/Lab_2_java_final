/**
 * Разбор SQL-подобных запросов.
 * Сначала токены, потом заполнение Query.
 */
import java.util.ArrayList;
import java.util.List;
public class SqlParser {
    private List<String> tokens;
    private int pos;

    // токенизация
    private List<String> tokenize(String sql) throws DbException {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '\'') {
                // строка в кавычках — читаем до закрывающей
                int end = sql.indexOf('\'', i + 1);
                if (end == -1) {
                    throw new DbException("Не закрыта кавычка в запросе");
                }
                result.add(sql.substring(i, end + 1));
                i = end + 1;
            } else if (c == '<' || c == '>' || c == '!') {
                // операторы сравнения
                if (i + 1 < sql.length() && sql.charAt(i + 1) == '=') {
                    result.add("" + c + "=");
                    i += 2;
                } else if (c == '<' && i + 1 < sql.length() && sql.charAt(i + 1) == '>') {
                    result.add("!=");
                    i += 2;
                } else if (c == '!') {
                    throw new DbException("Неизвестный символ '!' (используйте !=)");
                } else {
                    result.add("" + c);
                    i++;
                }
            } else if (isSymbol(c)) {
                result.add("" + c);
                i++;
            } else {
                // обычное слово или число
                int start = i;
                while (i < sql.length() && !Character.isWhitespace(sql.charAt(i))
                        && !isSymbol(sql.charAt(i)) && sql.charAt(i) != '\''
                        && sql.charAt(i) != '<' && sql.charAt(i) != '>' && sql.charAt(i) != '!') {
                    i++;
                }
                result.add(sql.substring(start, i));
            }
        }
        return result;
    }
    private boolean isSymbol(char c) {
        return c == '(' || c == ')' || c == ',' || c == '*' || c == '=' || c == ';';
    }

    // чтение токенов
    private boolean atEnd() {
        return pos >= tokens.size();
    }
    private String peek() {
        if (atEnd()) {
            return "";
        }
        return tokens.get(pos);
    }
    private String next() throws DbException {
        if (atEnd()) {
            throw new DbException("Запрос закончился раньше времени");
        }
        pos++;
        return tokens.get(pos - 1);
    }
    private void expect(String word) throws DbException {
        String token = next();
        if (!token.equalsIgnoreCase(word)) {
            throw new DbException("Ожидалось '" + word + "', а найдено '" + token + "'");
        }
    }
    private boolean isNext(String word) {
        return !atEnd() && peek().equalsIgnoreCase(word);
    }
    private String readName() throws DbException {
        String token = next();
        boolean bad = token.startsWith("'") || token.length() == 1 && isSymbol(token.charAt(0))
                || token.equals("<") || token.equals(">") || token.equals("<=")
                || token.equals(">=") || token.equals("!=");
        if (bad) {
            throw new DbException("Ожидалось имя, а найдено '" + token + "'");
        }
        return token.toLowerCase();
    }
    private String readValue() throws DbException {
        String token = next();
        boolean bad = token.length() == 1 && isSymbol(token.charAt(0))
                || token.equals("<") || token.equals(">") || token.equals("<=")
                || token.equals(">=") || token.equals("!=");
        if (bad) {
            throw new DbException("Ожидалось значение, а найдено '" + token + "'");
        }
        return token;
    }

    // главный метод разбора =====
    /** Разбирает текст запроса в объект Query. */
    public Query parse(String sql) throws DbException {
        tokens = tokenize(sql);
        pos = 0;

        // убираем ';' в конце
        while (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals(";")) {
            tokens.remove(tokens.size() - 1);
        }
        if (tokens.isEmpty()) {
            throw new DbException("Пустой запрос");
        }

        Query query = new Query();
        String first = next().toUpperCase();

        if (first.equals("CREATE")) {
            // только CREATE INDEX (таблицы создаются при старте программы)
            parseCreateIndex(query);
        } else if (first.equals("INSERT")) {
            parseInsert(query);
        } else if (first.equals("SELECT")) {
            parseSelect(query);
        } else if (first.equals("UPDATE")) {
            parseUpdate(query);
        } else if (first.equals("DELETE")) {
            parseDelete(query);
        } else if (first.equals("SHOW")) {
            expect("TABLES");
            query.type = Query.Type.SHOW_TABLES;
        } else {
            throw new DbException("Неизвестная команда: " + first);
        }

        // после разбора не должно остаться лишних слов
        if (!atEnd()) {
            throw new DbException("Лишний текст в конце запроса: " + peek());
        }
        return query;
    }

    // разбор отдельных видов запросов =====
    private void parseCreateIndex(Query query) throws DbException {
        expect("INDEX");
        query.type = Query.Type.CREATE_INDEX;
        query.indexName = readName();
        expect("ON");
        query.tableName = readName();
        expect("(");
        query.columnNames.add(readName());
        expect(")");
    }
    private void parseInsert(Query query) throws DbException {
        expect("INTO");
        query.type = Query.Type.INSERT;
        query.tableName = readName();

        // необязательный список столбцов
        if (isNext("(")) {
            next();
            while (true) {
                query.columnNames.add(readName());
                String separator = next();
                if (separator.equals(")")) {
                    break;
                }
                if (!separator.equals(",")) {
                    throw new DbException("В списке столбцов ожидалась ',' или ')'");
                }
            }
        }

        expect("VALUES");

        // один или несколько наборов значений
        while (true) {
            expect("(");
            List<String> oneRow = new ArrayList<>();
            while (true) {
                oneRow.add(readValue());
                String separator = next();
                if (separator.equals(")")) {
                    break;
                }
                if (!separator.equals(",")) {
                    throw new DbException("В списке значений ожидалась ',' или ')'");
                }
            }
            query.valuesList.add(oneRow);

            if (isNext(",")) {
                next(); // есть ещё один набор значений
            } else {
                break;
            }
        }
    }
    private void parseSelect(Query query) throws DbException {
        query.type = Query.Type.SELECT;
        if (isNext("*")) {
            next();
            query.selectAll = true;
        } else {
            while (true) {
                query.columnNames.add(readName());
                if (isNext(",")) {
                    next();
                } else {
                    break;
                }
            }
        }
        expect("FROM");
        query.tableName = readName();
        parseWhere(query);
    }
    private void parseUpdate(Query query) throws DbException {
        query.type = Query.Type.UPDATE;
        query.tableName = readName();
        expect("SET");
        while (true) {
            query.setColumns.add(readName());
            expect("=");
            query.setValues.add(readValue());
            if (isNext(",")) {
                next();
            } else {
                break;
            }
        }
        parseWhere(query);
    }
    private void parseDelete(Query query) throws DbException {
        expect("FROM");
        query.type = Query.Type.DELETE;
        query.tableName = readName();
        parseWhere(query);
    }
    private void parseWhere(Query query) throws DbException {
        if (!isNext("WHERE")) {
            return;
        }
        next();
        query.whereColumn = readName();
        String operator = next();
        boolean good = operator.equals("=") || operator.equals("!=") || operator.equals("<")
                || operator.equals(">") || operator.equals("<=") || operator.equals(">=");
        if (!good) {
            throw new DbException("Неверный оператор сравнения: " + operator);
        }
        query.whereOperator = operator;
        query.whereValue = readValue();
    }
}

package DB;

import java.io.Serializable;
import java.util.Hashtable;

public class Tuple implements Serializable {

    private Hashtable<String, Object> data;

    private String primaryKey;

    public Tuple(Hashtable<String, Object> data,String primaryKey) {
        this.data = data;
        this.primaryKey = primaryKey;
    }

    public Hashtable<String, Object> getData() {
        return data;
    }

    public Object getPrimaryKeyValue() {
        return data.get(primaryKey);
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        for (String key : data.keySet()) {
            result.append(data.get(key)).append(",");
        }
        result.deleteCharAt(result.length()-1);
        return result.toString();
    }

}

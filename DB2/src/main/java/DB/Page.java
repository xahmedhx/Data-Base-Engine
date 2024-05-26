package DB;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

public class Page implements Serializable {

    private Vector<Tuple> rows;
    private int index;
    private String fileName;

    private int maxSize = DBApp.pageSize;

    public Page(int index, String tableName) {
        this.index = index;
        rows = new Vector<Tuple>();
        this.createFile(index, tableName);
    }

    public int getIndex(){
        return index;
    }

    public boolean isFull() {
        return rows.size() >= maxSize;
    }

    public String getFilename() {
        return fileName;
    }

    public void createFile(int index, String tableName) {
        this.fileName = tableName + index + ".ser";
        try {
            FileOutputStream fileOut = new FileOutputStream(fileName, false);
            fileOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Vector<Tuple> getRows() {
        return rows;
    }

    public Tuple getLastTuple() {
        return rows.get(rows.size() - 1);
    }

    public Tuple removeLastTuple() {
        return rows.remove(rows.size() - 1);
    }

    public void addTuple(Tuple tuple, Hashtable<String,String> attr) throws DBAppException {
        for (Map.Entry<String, Object> entry : tuple.getData().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if(value == null)
                throw new DBAppException("Column " + key + " cannot be null");
            if(!value.getClass().getName().equals(attr.get(key)))
                throw new DBAppException("Column " + key + " is of type " + attr.get(key) + " but value is of type " + value.getClass().getName());
        }
        if (rows.size() >= maxSize)
            throw new DBAppException("Page is full");
        int index = findIndexToInsert(tuple.getPrimaryKeyValue());
        if (index == -1)
            throw new DBAppException("Primary key already exists");
        rows.add(index, tuple);
    }

    public HashMap<String,Object> update(Object primaryKeyVal, Hashtable<String, Object> values, Hashtable<String, String> attributes) throws DBAppException {
        int index = binarySearch(primaryKeyVal);
        if(index == -1){
            throw new DBAppException("Primary key not found");
        }
        Tuple tuple = rows.get(index);
        HashMap<String,Object> data = new HashMap<>(tuple.getData());
        //iterate over the attributes and check if the value is of the same type
        for(Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value != null && !value.getClass().getName().equals(attributes.get(key))) {
                throw new DBAppException("Tuple's data type doesn't match the column's data type");
            }
            tuple.getData().put(key, value);
        }
        return data;
    }

    public Tuple delete(Object primaryKey) throws DBAppException {
        int index = binarySearch(primaryKey);
        if(index == -1){
            throw new DBAppException("Primary key not found");
        }
        return rows.remove(index);
    }

    public int compareValues(Object value1, Object value2) {
        if (value1 instanceof Integer) {
            return ((Integer) value1).compareTo((Integer) value2);
        } else if (value1 instanceof Double) {
            return ((Double) value1).compareTo((Double) value2);
        } else if (value1 instanceof String) {
            return ((String) value1).compareTo((String) value2);
        } else if (value1 instanceof Boolean) {
            return ((Boolean) value1).compareTo((Boolean) value2);
        } else {
            return 0;
        }
    }

    public int binarySearch(Object key) {
        int low = 0;
        int high = rows.size() - 1;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            int cmp = compareValues(rows.get(mid).getPrimaryKeyValue(), key);
            if (cmp == 0) {
                return mid;
            } else if (cmp < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return -1;
    }

    public int findIndexToInsert(Object key) {
        int low = 0;
        int high = rows.size() - 1;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            int cmp = compareValues(rows.get(mid).getPrimaryKeyValue(), key);
            if (cmp == 0) {
                return -1;
            } else if (cmp < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Tuple row : rows)
            result.append(row).append("\n");
        return result.toString();
    }

    public Object[] getMinMax(String columnName) {
        Object[] minMax = new Object[2];
        minMax[0] = rows.get(0).getData().get(columnName);
        minMax[1] = rows.get(rows.size() - 1).getData().get(columnName);
        return minMax;
    }
}

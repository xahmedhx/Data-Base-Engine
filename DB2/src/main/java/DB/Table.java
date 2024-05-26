package DB;

import BTree.BTree;
import BTree.Pointer;

import java.io.*;
import java.util.*;

public class Table implements Serializable {

    private final String strTableName;
    private final String filename;
    private ArrayList<String> pageNames;

    private List<Object[]> minMaxValues;

    private Hashtable<String, String> attributes = new Hashtable<>();

    private String primaryKey;

    private List<String> indexNames;
    private List<String> colBTrees;

    public Table(String strTableName, Hashtable<String, String> htblColNameType, String primaryKey) {
        this.strTableName = strTableName;
        this.filename = strTableName + ".ser";
        this.pageNames = new ArrayList<>();
        this.attributes = htblColNameType;
        this.primaryKey = primaryKey;
        minMaxValues = new ArrayList<>();
        indexNames = new ArrayList<>();
        colBTrees = new ArrayList<>();
        createFile();
    }

    public String getName() {
        return this.strTableName;
    }

    public ArrayList<String> getPageNames() {
        return pageNames;
    }

    public String getFilename() {
        return filename;
    }

    public void createFile() {
        try {
            FileOutputStream fileOut = new FileOutputStream(filename, false);
            fileOut.close();
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    public List<Page> getPages() {
        List<Page> pages = new ArrayList<>();
        for (String pageName : pageNames) {
            pages.add(deserializePage(pageName));
        }
        return pages;
    }

    public List<BTree<?,?>> getBTrees(){
        List<BTree<?,?>> bTrees = new ArrayList<BTree<?, ?>>();
        for (String col : colBTrees){
            bTrees.add(deserializeBTree(col+"Index.ser"));
        }
        return bTrees;
    }

    public BTree<?,?> getBTree(String col){
        List<BTree<?,?>> bTrees = getBTrees();
        for (BTree<?,?> bTree:bTrees){
            if(Objects.equals(bTree.getColName(), col)){
                return bTree;
            }
        }
        return null;
    }

    public Hashtable<String,String> getAttributes(){
        return attributes;
    }

    private boolean checkColumnsExist(Hashtable<String,Object> htblColNameValue){
        for (String entry: htblColNameValue.keySet()){
            if(!attributes.containsKey(entry)){
                return false;
            }
        }
        return true;
    }

    public void insert(Hashtable<String, Object> values) throws DBAppException {
        if (values.size()!= attributes.size()){
            throw new DBAppException("Columns not matched");
        }
        if(!values.containsKey(primaryKey)){
            throw new DBAppException("Primary key is not found");
        }
        if(!checkColumnsExist(values)){
            throw new DBAppException("Tuple contains columns that aren't in the table");
        }
        Tuple tuple = new Tuple(values, primaryKey);
        if (pageNames.isEmpty()) {
            Page page = new Page(0, strTableName);
            page.addTuple(tuple, attributes);
            pageNames.add(page.getFilename());
            minMaxValues.add(page.getMinMax(primaryKey));
            serializePage(page.getFilename(), page);
            DBApp.serializeTable(this.filename, this);
            return;
        }
        int index = findPageIndexToInsert(tuple.getPrimaryKeyValue());
        index = Integer.parseInt(pageNames.get(index).substring(strTableName.length(),pageNames.get(index).length()-4));
        shiftTupleToOtherPage(index, values);
        System.out.println("inserting "+values+" into page "+index);
    }

    public void shiftTupleToOtherPage(int index, Hashtable<String, Object> values) throws DBAppException {
        Tuple tuple = new Tuple(values, primaryKey);
        List<BTree<?,?>> bTrees = getBTrees();
        if(index >= pageNames.size()){
            Page page = new Page(pageNames.size(), strTableName);
            page.addTuple(tuple,attributes);
            pageNames.add(page.getFilename());
            minMaxValues.add(page.getMinMax(primaryKey));
            serializePage(page.getFilename(), page);
            for (BTree<?,?> bTree: bTrees){
                String colName = bTree.getColName();
                switch (attributes.get(colName)){
                    case "java.lang.String" ->  ((BTree<String,String>) bTree).insert((String) values.get(colName), index + "-" + values.get(primaryKey));
                    case "java.lang.Integer" -> ((BTree<Integer,String>) bTree).insert((Integer) values.get(colName), index + "-" + values.get(primaryKey));
                    case "java.lang.Double" -> ((BTree<Double,String>) bTree).insert((Double) values.get(colName), index + "-" + values.get(primaryKey));
                }
                serializeBTree(colName+"Index.ser",bTree);
            }
            return;
        }
        Page page = deserializePage(pageNames.get(index));
        if (page.isFull()) {
            Tuple removedtuple = page.removeLastTuple();
            minMaxValues.set(index, page.getMinMax(primaryKey));
            shiftTupleToOtherPage(index + 1, removedtuple.getData());
        }
        page.addTuple(tuple, attributes);
        minMaxValues.set(index,page.getMinMax(primaryKey));
        serializePage(page.getFilename(), page);
        for (BTree<?,?> bTree: bTrees){
            String colName = bTree.getColName();
            switch (attributes.get(colName)){
                case "java.lang.String" ->  ((BTree<String,String>) bTree).insert((String) values.get(colName), index + "-" + values.get(primaryKey));
                case "java.lang.Integer" -> ((BTree<Integer,String>) bTree).insert((Integer) values.get(colName), index + "-" + values.get(primaryKey));
                case "java.lang.Double" -> ((BTree<Double,String>) bTree).insert((Double) values.get(colName), index + "-" + values.get(primaryKey));
            }
            serializeBTree(colName+"Index.ser",bTree);
        }

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

    public int findPageIndexToInsert(Object primaryKeyValue) {
        int low = 0;
        int high = pageNames.size() - 1;
        while (low < high) {
            int mid = (low + high) / 2;
            int minCmp = compareValues(minMaxValues.get(mid)[0], primaryKeyValue);
            int maxCmp = compareValues(minMaxValues.get(mid)[1], primaryKeyValue);
            if (minCmp >= 0 && maxCmp <= 0) {
                return mid;
            } else if (minCmp > 0) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return low;
    }

    public void update(String strKey, Hashtable<String, Object> values) throws DBAppException {
        if(values.containsKey(primaryKey)){
            throw new DBAppException("Primary key cannot be updated");
        }
        if(!checkColumnsExist(values)){
            throw new DBAppException("Tuple contains columns that aren't in the table");
        }
        Object primaryKeyVal = switch (attributes.get(primaryKey)){
            case "java.lang.String" -> strKey;
            case "java.lang.Integer" -> Integer.parseInt(strKey);
            case "java.lang.Double" -> Double.parseDouble(strKey);
            default -> throw new DBAppException();
        };
        int index = findPageIndexToInsert(primaryKeyVal);
        index = Integer.parseInt(pageNames.get(index).substring(strTableName.length(),pageNames.get(index).length()-4));
        Page page = deserializePage(pageNames.get(index));
        minMaxValues.set(index, page.getMinMax(primaryKey));
        HashMap<String,Object> data = page.update(primaryKeyVal, values, attributes);
        serializePage(page.getFilename(), page);
        System.out.println("updating "+primaryKey+" with "+values+" in page "+index);
        List<BTree<?,?>> bTrees = getBTrees();

        for (BTree<?,?> bTree: bTrees){
            String colName = bTree.getColName();
            if(!values.containsKey(colName)){
                continue;
            }
            switch (attributes.get(colName)){
                case "java.lang.String" ->  {
                    ((BTree<String,String>) bTree).delete((String) data.get(colName), index + "-" + primaryKeyVal);
                    ((BTree<String,String>) bTree).insert((String) values.get(colName), index + "-" + primaryKeyVal);
                }
                case "java.lang.Integer" -> {
                    ((BTree<Integer,String>) bTree).delete((Integer) data.get(colName), index + "-" + primaryKeyVal);
                    ((BTree<Integer,String>) bTree).insert((Integer) values.get(colName), index + "-" + primaryKeyVal);
                }
                case "java.lang.Double" -> {
                    ((BTree<Double,String>) bTree).delete((Double) data.get(colName), index + "-" + primaryKeyVal);
                    ((BTree<Double,String>) bTree).insert((Double) values.get(colName), index + "-" + primaryKeyVal);
                }
            }
            serializeBTree(colName+"Index.ser",bTree);

        }

    }

    public void deleteData(Hashtable<String,Object> values) throws DBAppException {
        if(values.isEmpty()){
            File file = new File("src");
            for (File f : Objects.requireNonNull(file.listFiles())) {
                if(f.isFile() && f.getName().startsWith(strTableName)) {
                    f.delete();
                }
            }
            pageNames.clear();
            minMaxValues.clear();
            return;
        }
        if(values.size()>attributes.size()){
            throw new DBAppException("The Tuple has more columns than the table's columns");
        }
        for(Map.Entry<String, Object> entry: values.entrySet()){
            if(!attributes.containsKey(entry.getKey())){
                throw new DBAppException("The Tuple contains come columns that aren't in the table");
            }
            if(!attributes.get(entry.getKey()).equals(entry.getValue().getClass().getName())){
                throw new DBAppException("Tuple's data type doesn't match the column's data type");
            }
        }
        ArrayList<HashMap<Integer,Object>> results = new ArrayList<>();
        List<Page> pages = getPages();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            ArrayList<HashMap<Integer,Object>> satisfyingTuples = findTuplesForQuery(entry, pages);
            if (satisfyingTuples.isEmpty()) {
                return;
            }
            if (results.isEmpty()) {
                results = satisfyingTuples;
            } else {
                results = intersect(results, satisfyingTuples);
            }
        }
        for (HashMap<Integer, Object> result : results) {
            int pageToDeleteFrom = (int) result.keySet().toArray()[0];
            deleteTuple(result.values().toArray()[0], pages.get(pageNames.indexOf(strTableName + pageToDeleteFrom + ".ser")), pageToDeleteFrom);
        }
    }

    private ArrayList<HashMap<Integer,Object>> findTuplesForQuery(Map.Entry<String,Object> entry, List<Page> pages) {
        ArrayList<HashMap<Integer,Object>> satisfyingTuples = new ArrayList<>();
        for (Page page : pages) {
            for (Tuple tuple : page.getRows()) {
                if (tuple.getData().get(entry.getKey()).equals(entry.getValue())) {
                    HashMap<Integer, Object> tupleData = new HashMap<>();
                    tupleData.put(page.getIndex(), tuple.getPrimaryKeyValue());
                    satisfyingTuples.add(tupleData);
                }
            }
        }
        return satisfyingTuples;
    }

    private ArrayList<HashMap<Integer,Object>> intersect(ArrayList<HashMap<Integer,Object>> list1, ArrayList<HashMap<Integer,Object>> list2){
        ArrayList<HashMap<Integer,Object>> result = new ArrayList<>();
        for (HashMap<Integer, Object> map1 : list1) {
            if (list2.contains(map1)) {
                result.add(map1);
            }
        }
        return result;
    }

    private void deleteTuple(Object primaryKeyVal,Page page, int index) throws DBAppException {
        index = pageNames.indexOf(strTableName+index+".ser");
        Vector<Tuple> rows = page.getRows();
        Tuple row = null;
        if(rows.size() > 1){
            row = page.delete(primaryKeyVal);
            serializePage(strTableName+page.getIndex()+".ser",page);
            minMaxValues.set(index,page.getMinMax(primaryKey));
        }else{
            int cmpResult = compareValues(rows.get(0).getPrimaryKeyValue(), primaryKeyVal);
            if(rows.size() != 0 && cmpResult == 0){
                row = page.delete(primaryKeyVal);
                File file = new File(strTableName + index +".ser");
                pageNames.remove(strTableName+index+".ser");
                file.delete();
                minMaxValues.remove(index);
            }
        }
        System.out.println("deleting "+primaryKeyVal+" from page "+index);

        List<BTree<?,?>> bTrees = getBTrees();
        for (BTree<?,?> bTree: bTrees){
            String colName = bTree.getColName();
            switch (attributes.get(colName)){
                case "java.lang.String" -> ((BTree<String,String>) bTree).delete((String) row.getData().get(colName), index + "-" + row.getData().get(primaryKey));
                case "java.lang.Integer" -> ((BTree<Integer,String>) bTree).delete((Integer) row.getData().get(colName), index + "-" + row.getData().get(primaryKey));
                case "java.lang.Double" -> ((BTree<Double,String>) bTree).delete((Double) row.getData().get(colName), index + "-" + row.getData().get(primaryKey));
            }
            serializeBTree(colName+"Index.ser",bTree);
        }
    }

    public void createIndex(String colName, String indexName) throws DBAppException {
        if(indexNames.contains(indexName)){
            throw new DBAppException("The index was already created on one of the columns");
        }
        if(attributes.get(colName) == null){
            throw new DBAppException("Wrong column name");
        }

        List<Page> pages = getPages();
        BTree<?,String> index = null;
        switch (attributes.get(colName)) {
            case "java.lang.String" -> {
                Vector<Pointer<String, String>> data = new Vector<>();
                for (Page page: pages){
                    for (Tuple tuple : page.getRows()){
                        data.add(new Pointer<>((String) tuple.getData().get(colName), page.getIndex() + "-" + tuple.getPrimaryKeyValue()));
                    }
                }
                data.sort((o1, o2) -> {
                    String s1 = String.valueOf(o1.key());
                    String s2 = String.valueOf(o2.key());
                    return s1.compareTo(s2);
                });
                BTree<String, String> bTree = new BTree<String, String>(indexName, colName);
                for (Pointer<String, String> datum : data) {
                    bTree.insert(datum.key(), datum.value());
                }
                index = bTree;
            }
            case "java.lang.Integer" -> {
                Vector<Pointer<Integer, String>> data = new Vector<>();
                for (Page page: pages){
                    for (Tuple tuple : page.getRows()){
                        data.add(new Pointer<>((Integer) tuple.getData().get(colName), page.getIndex() + "-" + tuple.getPrimaryKeyValue()));
                    }
                }
                data.sort((o1, o2) -> {
                    Integer i1 = o1.key();
                    Integer i2 = o2.key();
                    return i1.compareTo(i2);
                });
                BTree<Integer, String> bTree = new BTree<Integer, String>(indexName, colName);
                for (Pointer<Integer, String> datum : data) {
                    bTree.insert(datum.key(), datum.value());
                }
                index = bTree;
            }
            case "java.lang.Double" -> {
                Vector<Pointer<Double, String>> data = new Vector<>();
                for (Page page: pages){
                    for (Tuple tuple : page.getRows()){
                        data.add(new Pointer<>((Double) tuple.getData().get(colName), page.getIndex() + "-" + tuple.getPrimaryKeyValue()));
                    }
                }
                data.sort((o1, o2) -> {
                    Double d1 = o1.key();
                    Double d2 = o2.key();
                    return d1.compareTo(d2);
                });
                BTree<Double, String> bTree = new BTree<Double, String>(indexName, colName);
                for (Pointer<Double, String> datum : data) {
                    bTree.insert(datum.key(), datum.value());
                }
                index = bTree;
            }
        }
        if(!colBTrees.contains(colName)) {
            serializeBTree(colName+"Index.ser",index);
            indexNames.add(indexName);
            colBTrees.add(colName);
        }
    }

    public Iterator<Tuple> selectFromTable(SQLTerm[] arrSQLTerms, String[]  strarrOperators) {
        List<SQLTerm> sqlTerms = new ArrayList<>(Arrays.asList(arrSQLTerms));
        List<String> operations = new ArrayList<>(Arrays.asList(strarrOperators));

        //Result for each query
        List<HashMap<Object,Tuple>> results = new ArrayList<>();
        List<Page> pages = getPages();
        if (sqlTerms.isEmpty()) {
            Collection<Tuple> result = new ArrayList<>();
            for (Page page : pages) {
                result.addAll(page.getRows());
            }
            return result.iterator();
        }
        for (SQLTerm sqlTerm : sqlTerms) {
            results.add(computeSQLTerm(sqlTerm,pages));
        }
        while(!operations.isEmpty()){
            HashMap<Object, Tuple> data = new HashMap<>();
            switch (operations.get(0).toUpperCase()){
                case "AND" -> data = getIntersectionHashMaps(results.get(0), results.get(1));
                case "OR" -> data = getUnionHashMaps(results.get(0), results.get(1));
                case "XOR" -> data = getXORHashMaps(results.get(0), results.get(1));
            }
            operations.remove(0);
            results.remove(0);
            results.remove(0);
            results.add(0,data);
        }
        Collection<Tuple> result = results.get(0).values();
        return result.iterator();
    }

    public HashMap<Object,Tuple> getIntersectionHashMaps(HashMap<Object, Tuple> hashMap1, HashMap<Object,Tuple> hashMap2){
        HashMap<Object, Tuple> result = new HashMap<>();
        if(hashMap1.size()<hashMap2.size()){
            for(Map.Entry<Object,Tuple> entry : hashMap1.entrySet()){
                if(hashMap2.containsKey(entry.getKey())){
                    result.put(entry.getKey(),entry.getValue());
                }
            }
        }else {
            for(Map.Entry<Object,Tuple> entry : hashMap2.entrySet()){
                if(hashMap1.containsKey(entry.getKey())){
                    result.put(entry.getKey(),entry.getValue());
                }
            }
        }
        return result;
    }

    public HashMap<Object,Tuple> getUnionHashMaps(HashMap<Object, Tuple> hashMap1, HashMap<Object,Tuple> hashMap2){
        HashMap<Object,Tuple> hashMap = new HashMap<>(hashMap1);
        hashMap.putAll(hashMap2);
        return hashMap;
    }

    public HashMap<Object, Tuple> getXORHashMaps(HashMap<Object, Tuple> hashMap1, HashMap<Object,Tuple> hashMap2){
        HashMap<Object, Tuple> xor = getUnionHashMaps(hashMap1,hashMap2);
        HashMap<Object, Tuple> intersection = getIntersectionHashMaps(hashMap1,hashMap2);
        for(Object key : intersection.keySet()){
            xor.remove(key);
        }
        return xor;
    }

    public HashMap<Object,Tuple> computeSQLTerm(SQLTerm sqlTerm,List<Page> pages) {
        HashMap<Object, Tuple> dataSet = new HashMap<>();
        if(colBTrees.contains(sqlTerm._strColumnName)){
            //Index format: key:value in column , value: page number-primary key as a string
            String type = attributes.get(primaryKey);
            switch (attributes.get(sqlTerm._strColumnName)){
                case "java.lang.String" -> {
                    BTree<String,String> bTree = (BTree<String,String>) getBTree(sqlTerm._strColumnName);
                    LinkedList<Pointer<String,String>> list = bTree.computeOperator(String.valueOf(sqlTerm._objValue),sqlTerm._strOperator);
                    for(Pointer<String, String> pointer : list){
                        int pageNum = Integer.parseInt(pointer.value().split("-")[0]);
                        pageNum = pageNames.indexOf(strTableName + pageNum + ".ser");
                        Object primaryKey = getParsedPrimaryKey(type,pointer.value().split("-")[1]);
                        Page page = pages.get(pageNum);
                        Tuple tuple = page.getRows().get(page.binarySearch(primaryKey));
                        dataSet.put(primaryKey,tuple);
                    }
                    return dataSet;
                }
                case "java.lang.Integer" -> {
                    BTree<Integer,String> bTree = (BTree<Integer,String>) getBTree(sqlTerm._strColumnName);
                    LinkedList<Pointer<Integer,String>> list = bTree.computeOperator((int) sqlTerm._objValue,sqlTerm._strOperator);
                    for(Pointer<Integer, String> pointer : list){
                        int pageNum = Integer.parseInt(pointer.value().split("-")[0]);
                        pageNum = pageNames.indexOf(strTableName + pageNum + ".ser");
                        Object primaryKey = getParsedPrimaryKey(type,pointer.value().split("-")[1]);
                        Page page = pages.get(pageNum);
                        Tuple tuple = page.getRows().get(page.binarySearch(primaryKey));
                        dataSet.put(primaryKey,tuple);
                    }
                    return dataSet;
                }
                case "java.lang.Double" -> {
                    BTree<Double,String> bTree = (BTree<Double,String>) getBTree(sqlTerm._strColumnName);
                    LinkedList<Pointer<Double,String>> list = bTree.computeOperator((double) sqlTerm._objValue,sqlTerm._strOperator);
                    for(Pointer<Double, String> pointer : list){
                        int pageNum = Integer.parseInt(pointer.value().split("-")[0]);
                        pageNum = pageNames.indexOf(strTableName + pageNum + ".ser");
                        Object primaryKey = getParsedPrimaryKey(type,pointer.value().split("-")[1]);
                        Page page = pages.get(pageNum);
                        Tuple tuple = page.getRows().get(page.binarySearch(primaryKey));
                        dataSet.put(primaryKey,tuple);
                    }
                    return dataSet;
                }

            }
        }else{
            return linearSearch(sqlTerm._strColumnName,sqlTerm._strOperator,sqlTerm._objValue,pages);
        }
        return null;
    }

    public Object getParsedPrimaryKey(String type,Object val){
        return switch (type){
            case "java.lang.String" -> String.valueOf(val);
            case "java.lang.Integer" -> Integer.parseInt((String) val);
            case "java.lang.Double" -> Double.parseDouble((String) val);
            default -> null;
        };
    }

    private HashMap<Object,Tuple> linearSearch(String colName,String operator,Object value,List<Page> pages){
        HashMap<Object, Tuple> tupleMap = new HashMap<>();
        for (Page page : pages) {
            for (Tuple tuple : page.getRows()) {
                switch (operator) {
                    case ">" -> {
                        if (compareValues(tuple.getData().get(colName), value) > 0) {
                            tupleMap.put(tuple.getPrimaryKeyValue(), tuple);
                        }
                    }
                    case ">=" -> {
                        if (compareValues(tuple.getData().get(colName), value) >= 0) {
                            tupleMap.put(tuple.getPrimaryKeyValue(), tuple);
                        }
                    }
                    case "<" -> {
                        if (compareValues(tuple.getData().get(colName), value) < 0) {
                            tupleMap.put(tuple.getPrimaryKeyValue(), tuple);
                        }
                    }
                    case "<=" -> {
                        if (compareValues(tuple.getData().get(colName), value) <= 0) {
                            tupleMap.put(tuple.getPrimaryKeyValue(), tuple);
                        }
                    }
                    case "!=" -> {
                        if (compareValues(tuple.getData().get(colName), value) != 0) {
                            tupleMap.put(tuple.getPrimaryKeyValue(), tuple);
                        }
                    }
                    case "=" -> {
                        if (compareValues(tuple.getData().get(colName), value) == 0) {
                            tupleMap.put(tuple.getPrimaryKeyValue(), tuple);
                        }
                    }
                }
            }
        }
        return tupleMap;
    }

    public void serializePage(String filename, Page page) {
        try (FileOutputStream fileOut = new FileOutputStream(filename);
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(page);
        } catch (IOException ex) {
            System.out.println("An error occurred while serializing data to " + filename);
            ex.printStackTrace();
        }
    }

    public static Page deserializePage(String filename) {
        Page page = null;
        try (FileInputStream fileIn = new FileInputStream(filename);
             ObjectInputStream in = new ObjectInputStream(fileIn)) {
            page = (Page) in.readObject();
        } catch (IOException ex) {
            System.out.println("An error occurred while reading from " + filename);
            ex.printStackTrace();
        } catch (ClassNotFoundException ex) {
            System.out.println("Page class not found for " + filename);
            ex.printStackTrace();
        }

        return page;
    }


    private static void serializeBTree(String filename, BTree bTree) {
        try (FileOutputStream fileOut = new FileOutputStream(filename);
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(bTree);
            System.out.printf("Serialized data is saved in %s\n", filename);
        } catch (IOException ex) {
            System.out.println("An error occurred while serializing the BPTree to " + filename);
            ex.printStackTrace();
        }
    }

    public static BTree deserializeBTree(String filename) {
        BTree bTree = null;

        try (FileInputStream fileIn = new FileInputStream(filename);
             ObjectInputStream in = new ObjectInputStream(fileIn)) {
            bTree = (BTree) in.readObject();
        } catch (IOException ex) {
            System.out.println("An error occurred while reading the BPTree from " + filename);
            ex.printStackTrace();
        } catch (ClassNotFoundException ex) {
            System.out.println("BPTree class not found for " + filename);
            ex.printStackTrace();
        }

        return bTree;
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Table Name: ").append(strTableName).append("\n");
        List<Page> pages = getPages();
        for (Page page : pages) {
            result.append("Page Num: ").append(pages.indexOf(page)).append("\n");
            result.append(page).append("\n");
        }
        return result.toString();
    }
}

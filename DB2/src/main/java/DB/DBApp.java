package DB;

import BTree.BTree;

import java.io.BufferedReader;
import java.io.File;
import java.util.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class DBApp {
	ArrayList<String> tableNames = new ArrayList<String>();

	public static int pageSize = readConfig("MaximumRowsCountinPage");

	public DBApp() {
		init();
	}

	public void init() {
		File metadata = new File("metadata.csv");
		try{
			metadata.createNewFile();
			Scanner scanner = new Scanner(metadata);
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				String[] values = line.split(",");
				tableNames.add(values[0]);
			}
		}catch (IOException e){
			e.printStackTrace();
		}

	}

	public static int readConfig(String property){
		Properties properties = new Properties();
		try {
			ClassLoader classloader = Thread.currentThread().getContextClassLoader();
			InputStream fileInputStream = classloader.getResourceAsStream("DBApp.config");
			properties.load(fileInputStream);
			assert fileInputStream != null;
			fileInputStream.close();

			return Integer.parseInt(properties.getProperty(property));

		} catch (IOException e) {
			e.printStackTrace();
		}
		return 2;
	}

	public void createTable(String strTableName, String strClusteringKeyColumn,
							Hashtable<String, String> htblColNameType) throws DBAppException {
		legalType(htblColNameType);
		legalName(strTableName);
		String primaryKey = writeMetadataCSV(strTableName, strClusteringKeyColumn, htblColNameType);
		Table t = new Table(strTableName, htblColNameType, primaryKey);
		this.serializeTable(t.getFilename(), t);
		tableNames.add(strTableName);
	}

	// checks that it's one of the 3 data types
	private void legalType(Hashtable<String, String> htblColNameType) throws DBAppException {
		Set<String> keys = htblColNameType.keySet();

		ArrayList<String> legalTypes = new ArrayList<>(Arrays.asList(
				"java.lang.Integer",
				"java.lang.String",
				"java.lang.Double"
		));

		for (String key : keys) {
			if (!legalTypes.contains(htblColNameType.get(key))) {
				throw new DBAppException("The type of " + key + " is invalid.\n" +
						"It's of type: " + htblColNameType.get(key) + "\n" +
						"Must be one of the following types:\n" +
						"java.lang.Integer\n" +
						"java.lang.String\n" +
						"java.lang.Double");
			}
		}
	}

	private void legalName(String strTableName) throws DBAppException {
		boolean tableFound = false;
		String filePath = "metadata.csv";

		try (BufferedReader bufferedReader = new BufferedReader(new FileReader(filePath))) {
			String currentLine;

			while ((currentLine = bufferedReader.readLine()) != null) {
				String[] data = currentLine.split(",");
				if (data[0].equals(strTableName)) {
					tableFound = true;
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (tableFound) {
			throw new DBAppException("You cannot create a table with name: " + strTableName + " because it already exists.");
		}
	}

	private String writeMetadataCSV(String strTableName, String strClusteringKeyColumn, Hashtable<String, String> htblColNameType) throws DBAppException {
		String metadataPath = "metadata.csv";
		String primaryKey = "";
		try (FileWriter fileWriter = new FileWriter(metadataPath, true)) {
			BufferedReader bufferedReader = new BufferedReader(new FileReader(metadataPath));
			bufferedReader.close();

			Set<String> keys = htblColNameType.keySet();
			for (String key : keys) {
				writeMetadataEntry(fileWriter, strTableName, key, htblColNameType.get(key), key.equals(strClusteringKeyColumn));
				if(key.equals(strClusteringKeyColumn)){
					primaryKey = key;
				}
			}
			if(primaryKey.equals("")){
				throw new DBAppException("The clustering key column does not exist in the table.");
			}

			System.out.println("CSV file was updated successfully !!!");
		} catch (IOException e) {
			System.out.println("Error in CsvFileWriter !!!");
			e.printStackTrace();
		}
		return primaryKey;
	}

	private void writeMetadataEntry(FileWriter fileWriter, String tableName, String columnName, String columnType, boolean isClusteringKey) throws IOException {
		fileWriter.append(tableName).append(",");
		fileWriter.append(columnName).append(",");
		fileWriter.append(columnType).append(",");
		fileWriter.append(isClusteringKey ? "true," : "false,");
		fileWriter.append("null,");
		fileWriter.append("null\n");
	}


	private void updateMetadataCSV(String strTableName, String colName, String indexName) throws DBAppException {
		String metadataPath = "metadata.csv";
		List<String> lines = new ArrayList<>();

		try (BufferedReader bufferedReader = new BufferedReader(new FileReader(metadataPath))) {
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				String[] values = line.split(",");
				if (values.length > 1 && values[0].equals(strTableName) && values[1].equals(colName)) {
					StringBuilder updatedLine = new StringBuilder();
					updatedLine.append(strTableName).append(",");
					updatedLine.append(colName).append(",");
					updatedLine.append(values[2]).append(",");
					updatedLine.append(values[3]).append(",");
					updatedLine.append("BTree,");
					updatedLine.append(indexName+"\n");
					lines.add(updatedLine.toString());
				} else {
					lines.add(line + "\n");
				}
			}
		} catch (IOException e) {
			throw new DBAppException("Error reading CSV file."+ e.getMessage());
		}

		try (FileWriter fileWriter = new FileWriter(metadataPath)) {
			for (String l : lines) {
				fileWriter.write(l);
			}
		} catch (IOException e) {
			throw new DBAppException("Error reading CSV file."+ e.getMessage());
		}
	}

	public void createIndex(String   strTableName,
							String   strColName,
							String   strIndexName) throws DBAppException {
		Table table = deserializeTable(strTableName + ".ser");
		table.createIndex(strColName,strIndexName);
		serializeTable(table.getFilename(), table);
		updateMetadataCSV(strTableName,strColName,strIndexName);
	}

	public void insertIntoTable(String strTableName,
								Hashtable<String,Object>  htblColNameValue) throws DBAppException {
		Table table = deserializeTable(strTableName+".ser");
		assert table != null;
		table.insert(htblColNameValue);
		serializeTable(table.getFilename(), table);
	}

	public void updateTable(String strTableName,
							String strClusteringKeyValue,
							Hashtable<String,Object> htblColNameValue   )  throws DBAppException {

		Table table = deserializeTable(strTableName+".ser");
		assert table != null;
		table.update(strClusteringKeyValue,htblColNameValue);
		serializeTable(table.getFilename(), table);
	}

	public void deleteFromTable(String strTableName,
								Hashtable<String,Object> htblColNameValue) throws DBAppException {
		Table table = deserializeTable(strTableName+".ser");
		assert table != null;
		table.deleteData(htblColNameValue);
		serializeTable(table.getFilename(), table);
	}

	public Iterator selectFromTable(SQLTerm[] arrSQLTerms,
									String[]  strarrOperators) throws DBAppException {
		if(arrSQLTerms.length!=strarrOperators.length+1){
			throw new DBAppException("Num of operators must be = SQLTerms -1");
		}
		String tableName = arrSQLTerms[0]._strTableName;
		Table table = deserializeTable(tableName+".ser");
		assert table != null;
		if (arrSQLTerms.length == 1 && arrSQLTerms[0]._strColumnName == null && arrSQLTerms[0]._strOperator == null && arrSQLTerms[0]._objValue == null) {
			return table.selectFromTable(new SQLTerm[0],strarrOperators);
		}
		//Edge case checks
		for (SQLTerm arrSQLTerm : arrSQLTerms) {
			if (!Objects.equals(arrSQLTerm._strTableName, tableName)) {
				throw new DBAppException("One of the SQLTerms isn't on the same table");
			}
			if (!table.getAttributes().containsKey(arrSQLTerm._strColumnName)) {
				throw new DBAppException("The Table doesn't contain a " + arrSQLTerm._strColumnName + " column");
			}
			if (!arrSQLTerm._objValue.getClass().getName().equals(table.getAttributes().get(arrSQLTerm._strColumnName))) {
				throw new DBAppException("Class of the object for the operation doesn't match the column class");
			}
			if (!Arrays.asList("<", "<=", ">", ">=", "!=", "=").contains(arrSQLTerm._strOperator)) {
				throw new DBAppException("The only supported operators are <,<=,>,>=,!=,=");
			}
		}
		for (String operator : strarrOperators){
			if (!Arrays.asList("AND","OR","XOR").contains(operator.toUpperCase())) {
				throw new DBAppException("The only supported array operators are AND,OR,XOR");
			}
		}
		return table.selectFromTable(arrSQLTerms,strarrOperators);
	}

	public static void serializeTable(String filename, Table table) {
		try (FileOutputStream fileOut = new FileOutputStream(filename)){
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(table);
		} catch (IOException ex) {
			System.out.println("An error occurred while serializing data to " + filename);
			ex.printStackTrace();
		}
	}

	public static Table deserializeTable(String filename) {
		try (FileInputStream fileIn = new FileInputStream(filename)) {
			ObjectInputStream in = new ObjectInputStream(fileIn);
			return (Table) in.readObject();
		} catch (IOException ex) {
			System.out.println("An error occurred while reading from " + filename);
			ex.printStackTrace();
		} catch (ClassNotFoundException ex) {
			System.out.println("Table class not found for " + filename);
			ex.printStackTrace();
		}
		return null;
	}

	// ///////// END SERIALIZATION METHODS//////////////////////////////
	public static void main(String[] args) throws DBAppException {
		String strTableName1 = "Shop";
		DBApp dbApp = new DBApp( );
		Hashtable htblColNameType1 = new Hashtable( );
		htblColNameType1.put("Branch_id", "java.lang.Integer");
		htblColNameType1.put("B_Address", "java.lang.String");
//		dbApp.createTable( strTableName1, "Branch_id", htblColNameType1);

		String strTableName2 = "City";
		Hashtable htblColNameType2 = new Hashtable( );
		htblColNameType2.put("Name", "java.lang.String");
		htblColNameType2.put("Population", "java.lang.Integer");
//		dbApp.createTable( strTableName2, "Name", htblColNameType2);

		String strTableName3 = "Student";
		Hashtable htblColNameType3 = new Hashtable( );
		htblColNameType3.put("SID", "java.lang.Integer");
		htblColNameType3.put("Sname", "java.lang.String");
		htblColNameType3.put("SGPA", "java.lang.Double");
		htblColNameType3.put("Major", "java.lang.String");
//		dbApp.createTable( strTableName3, "SID", htblColNameType3);
//		dbApp.createIndex(strTableName3, "SID", "idIndex");
//		dbApp.createIndex(strTableName3, "Sname", "nameIndex");


////// First Check

		Hashtable htblColNameValue = new Hashtable( );
//		htblColNameValue.put("Name", new Integer( 2343432 ));
//		htblColNameValue.put("Population", new Integer(12000000));
//		dbApp.insertIntoTable( strTableName2 , htblColNameValue );

//		htblColNameValue.clear( );
//		htblColNameValue.put("Name", new String("Naples"));
//		dbApp.insertIntoTable( strTableName2 , htblColNameValue );

////// exception here

//		htblColNameValue.clear( );
//		htblColNameValue.put("Name", new String("Norway"));
//		htblColNameValue.put("Population", new Integer(5000000));
//		dbApp.deleteFromTable( strTableName2 , htblColNameValue );
//
//		htblColNameValue.clear( );
//		htblColNameValue.put("Name", new String("Norway"));
//		htblColNameValue.put("Population", new Integer(12000000));
//		dbApp.insertIntoTable( strTableName2 , htblColNameValue );
//
/////// print pages here
//
		htblColNameValue.clear( );
		htblColNameValue.put("SID", new Integer( 4 ));
//		htblColNameValue.put("Sname", new String("Mahy" ) );
//		htblColNameValue.put("SGPA", new Double( 2.0 ) );
//		htblColNameValue.put("Major", new String ( "CS" ) );
		dbApp.deleteFromTable( strTableName3 ,  htblColNameValue );
//
///////// create page 1
//
//		htblColNameValue.clear( );
//		htblColNameValue.put("SID", new Integer( 9 ));
//		htblColNameValue.put("Sname", new String( "Hussien" ) );
//		htblColNameValue.put("SGPA", new Double( 1.5 ) );
//		htblColNameValue.put("Major", new String ( "CS" ) );
//		dbApp.insertIntoTable( strTableName3 , htblColNameValue );
//
//		htblColNameValue.clear( );
//		htblColNameValue.put("SID", new Integer( 17 ));
//		htblColNameValue.put("Sname", new String( "Hoda" ) );
//		htblColNameValue.put("SGPA", new Double( 0.8 ) );
//		htblColNameValue.put("Major", new String ( "CS" ) );
//		dbApp.insertIntoTable( strTableName3 , htblColNameValue );
//
//		htblColNameValue.clear( );
//		htblColNameValue.put("SID", new Integer( 19 ));
//		htblColNameValue.put("Sname", new String( "Ola" ) );
//		htblColNameValue.put("SGPA", new Double( 3.1 ) );
//		htblColNameValue.put("Major", new String ( "CS" ) );
//		dbApp.insertIntoTable( strTableName3 , htblColNameValue );
//
//////// new page without shifting
//
//		htblColNameValue.clear( );
//		htblColNameValue.put("SID", new Integer( 5 ));
//		htblColNameValue.put("Sname", new String("Samy" ) );
//		htblColNameValue.put("SGPA", new Double( 2.0 ) );
//		htblColNameValue.put("Major", new String ( "CS" ) );
//		dbApp.insertIntoTable( strTableName3 , htblColNameValue );
//
//////// shifting here
//
//		htblColNameValue.clear( );
//		htblColNameValue.put("Population", new Integer(6000000));
//		dbApp.updateTable( strTableName2 , "Naples" , htblColNameValue );
//
//
//		htblColNameValue.clear( );
//		htblColNameValue.put("Population", new String( "True" ));
//		dbApp.updateTable( strTableName2 , "Naples" , htblColNameValue );
//
//////// exception here
//
//		htblColNameValue.clear( );
//		htblColNameValue.put("Sname", new String("Hany" ) );
//		htblColNameValue.put("SGPA", new Double( 0.79 ) );
//		dbApp.updateTable( strTableName3 , "17" , htblColNameValue );
//
//		htblColNameValue.clear( );
//		htblColNameValue.put("Sname", new String("Hany" ) );
//		htblColNameValue.put("SGPA", new Double( 0.79 ) );
//		dbApp.updateTable( strTableName3 , "117" , htblColNameValue );
//
//////// does nothing


		SQLTerm[] arrSQLTerms;
		arrSQLTerms = new SQLTerm[1];
		arrSQLTerms[0] = new SQLTerm();
//		arrSQLTerms[1] = new SQLTerm();
		arrSQLTerms[0]._strTableName = "Student";
//		arrSQLTerms[0]._strColumnName= "SID";
//		arrSQLTerms[0]._strOperator = ">";
//		arrSQLTerms[0]._objValue = 1;
//		arrSQLTerms[1]._strTableName = "Student";
//		arrSQLTerms[1]._strColumnName= "Sname";
//		arrSQLTerms[1]._strOperator = "=";
//		arrSQLTerms[1]._objValue = new String("Hany");
		String[]strarrOperators = new String[0];
//		strarrOperators[0] = "AND";
// select * from Student where name = “John Noor” or gpa = 1.5;
		Iterator resultSet = dbApp.selectFromTable(arrSQLTerms , strarrOperators);
		while(resultSet.hasNext()){
			System.out.println(resultSet.next());
		}
	}
}



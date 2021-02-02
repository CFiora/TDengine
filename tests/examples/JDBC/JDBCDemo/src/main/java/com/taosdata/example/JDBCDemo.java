package com.taosdata.example;

import java.sql.*;
import java.util.Properties;

class multiThreadingClass extends Thread
{
    public int id;
    public int from, to;
    public int numOfTb;
    public Connection connection;
    public String dbName, stbName, tbName;

    public void run()
    {
        System.out.println("ID: " + id + " from: " + from + " to: " + to);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            System.out.println("Thread " + id + " interrupted.");
        }

        for (int i = from; i < to; i++) {
            for (int j = 0; j < numOfTb; j++) {
                if (j % 1000 == 0) {
                    try {
                        System.out.print(id + "s.");
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        System.out.println("Thread " + id + " interrupted.");
                    }
                }
                final String sql = "select last_row(humidity) from " + dbName + "." + tbName + i + "_" + j;
//		        System.out.println(sql);
        	    executeQuery(sql);
	        }
	    }
    }

    private void printSql(String sql, boolean succeed, long cost) {
        System.out.println("[ " + (succeed ? "OK" : "ERROR!") + " ] time cost: " + cost + " ms, execute statement ====> " + sql);
    }

    private void printResult(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        while (resultSet.next()) {
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                String columnLabel = metaData.getColumnLabel(i);
                String value = resultSet.getString(i);
                System.out.printf("%s: %s\t", columnLabel, value);
            }
            System.out.println();
        }
    }

    private void executeQuery(String sql) {
        try (Statement statement = connection.createStatement()) {
            long start = System.currentTimeMillis();
            ResultSet resultSet = statement.executeQuery(sql);
            long end = System.currentTimeMillis();
//            printSql(sql, true, (end - start));
//            printResult(resultSet);
            resultSet.close();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

public class JDBCDemo {
    private static String host;
    private static String driverType = "jni";
    private static final String dbName = "test";
    private static final String stbName = "weather";
    private static final String tbName = "subweather";
    public Connection connection;
    private static int numOfSTb = 300000;
    private static int numOfTb = 3;
    private static int numOfThreads = 1;


    public static void main(String[] args) {

        for (int i = 0; i < args.length; i++) {
            if ("-host".equalsIgnoreCase(args[i]) && i < args.length - 1)
                host = args[++i];
            if ("-T".equalsIgnoreCase(args[i]) && i < args.length - 1) {
                numOfThreads = Integer.parseInt(args[++i]);
            }
            if ("-driverType".equalsIgnoreCase(args[i]) && i < args.length - 1) {
                driverType = args[++i];
                if (!"jni".equalsIgnoreCase(driverType) && !"restful".equalsIgnoreCase(driverType))
                    printHelp();
            }
        }

        if (host == null) {
            printHelp();
        }

        System.out.println("Number of Threads: " + numOfThreads);

        JDBCDemo demo = new JDBCDemo();
        demo.init();
//        demo.dropDatabase();
//        demo.createDatabase();
        demo.useDatabase();
//        demo.dropTable();
//        demo.createSTable();
//        demo.createTable();
//        demo.insert();
        demo.selectMultiThreading();
//        demo.select();
//        demo.dropTable();
        demo.close();
    }

    private void init() {
        // get connection
        try {
            String url = "jdbc:TAOS://" + host + ":6030/?user=root&password=taosdata";
            if (driverType.equals("restful")) {
                Class.forName("com.taosdata.jdbc.rs.RestfulDriver");
                url = "jdbc:TAOS-RS://" + host + ":6041/?user=root&password=taosdata";
            } else {
                Class.forName("com.taosdata.jdbc.TSDBDriver");
            }
            Properties properties = new Properties();
            properties.setProperty("charset", "UTF-8");
            properties.setProperty("locale", "en_US.UTF-8");
            properties.setProperty("timezone", "UTC-8");
            System.out.println("get connection starting...");
            connection = DriverManager.getConnection(url, properties);
            if (connection != null)
                System.out.println("[ OK ] Connection established.");
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    private void dropDatabase() {
        String sql = "DROP database if exists " + dbName;
        exuete(sql);
    }

    private void createDatabase() {
        String sql = "create database if not exists " + dbName;
        exuete(sql);
    }

    private void useDatabase() {
        String sql = "use " + dbName;
        exuete(sql);
    }

    private void selectMultiThreading() {
        int a = numOfSTb / numOfThreads;

        if (a < 1) {
            numOfThreads = numOfSTb;
            a = 1;
        }

        int b = 0;
        if (numOfThreads != 0) {
            b = numOfSTb % numOfThreads;
        }

        multiThreadingClass instance[] = new multiThreadingClass[numOfThreads];

        int last = 0;
        for (int i = 0; i < numOfThreads; i ++) {
            instance[i] = new multiThreadingClass();
            instance[i].id = i;
            instance[i].from = last;
            if (i < b) {
                instance[i].to = last + a;
            } else {
                instance[i].to = last + a - 1;
            }

            last = instance[i].to + 1;
            instance[i].numOfTb = numOfTb;
            instance[i].connection = connection;
            instance[i].dbName = dbName;
            instance[i].tbName = tbName;

            instance[i].start();
        }

        for (int i = 0; i < numOfThreads; i ++) {
            try {
                instance[i].join();
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }
    }

    private void select() {
        for (int i = 0; i < numOfSTb; i++) {
            for (int j = 0; j < numOfTb; j++) {
                final String sql = "select last_row(humidity) from " + dbName + "." + tbName + i + "_" + j;
//		        System.out.println(sql);
        	    executeQuery(sql);
	        }
	    }
    }

    private void executeQuery(String sql) {
        try (Statement statement = connection.createStatement()) {
            long start = System.currentTimeMillis();
            ResultSet resultSet = statement.executeQuery(sql);
            long end = System.currentTimeMillis();
            printSql(sql, true, (end - start));
//            printResult(resultSet);
            resultSet.close();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void printResult(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        while (resultSet.next()) {
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                String columnLabel = metaData.getColumnLabel(i);
                String value = resultSet.getString(i);
                System.out.printf("%s: %s\t", columnLabel, value);
            }
            System.out.println();
        }
    }

    private void insert() {
	for (int i = 0; i < numOfSTb; i++) {
	    for (int j = 0; j < numOfTb; j++) {
        	final String sql = "INSERT INTO " + dbName + "." + tbName + i + "_" + j + " (ts, temperature, humidity, name) values(now, 20.5, 34, \"" + i + "\")";
		System.out.println(sql);
        	exuete(sql);
	    }
	}
    }

    private void createSTable() {
	for (int i = 0; i < numOfSTb; i ++) {
        	final String sql = "create table if not exists " + dbName + "." + stbName + i + " (ts timestamp, temperature float, humidity int, name BINARY(" + (i % 73+10) + ")) TAGS (tag1 INT)";
		System.out.println(sql);
        	exuete(sql);
	}
    }

    private void createTable() {
	for (int i = 0; i < numOfSTb; i ++) {
	    for (int j = 0; j < numOfTb; j++) {
        	final String sql = "create table if not exists " + dbName + "." + tbName + i + "_" + j + " USING " + stbName + i + " TAGS(" + j + ")";
		System.out.println(sql);
        	exuete(sql);
	    }
	}
    }

    private void printSql(String sql, boolean succeed, long cost) {
        System.out.print(".");
//        System.out.println("[ " + (succeed ? "OK" : "ERROR!") + " ] time cost: " + cost + " ms, execute statement ====> " + sql);
    }

    private void exuete(String sql) {
        try (Statement statement = connection.createStatement()) {
            long start = System.currentTimeMillis();
            boolean execute = statement.execute(sql);
            long end = System.currentTimeMillis();
            printSql(sql, execute, (end - start));
        } catch (SQLException e) {
            e.printStackTrace();

        }
    }

    private void close() {
        try {
            if (connection != null) {
                this.connection.close();
                System.out.println("connection closed.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void dropTable() {
        final String sql = "drop table if exists " + dbName + "." + tbName + "";
        exuete(sql);
    }

    private static void printHelp() {
        System.out.println("Usage: java -jar JdbcDemo.jar -host <hostname> -driverType <jni|restful>");
        System.exit(0);
    }


}

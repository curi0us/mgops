package com.savemgo.mgo1;

import org.apache.commons.dbcp.ConnectionFactory;
import org.apache.commons.dbcp.DriverManagerConnectionFactory;
import org.apache.commons.dbcp.PoolableConnectionFactory;
import org.apache.commons.dbcp.PoolingDataSource;
import org.apache.commons.pool.impl.GenericObjectPool;

import org.apache.log4j.Logger; 

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


public class DBPool {
	protected static final String DRIVER   = "com.mysql.jdbc.Driver";
	public static String URL;
	public static String USER;
	public static String PASSWORD;
	public static int POOL_SIZE;

	private Connection connection   = null;
	private GenericObjectPool connectionPool = null;
	
	private static DataSource ds = null;
	private static DBPool dbp = null;

	public DataSource setUp() throws Exception {
		Class.forName(DBPool.DRIVER).newInstance(); //Load driver
		//Create pool
		connectionPool = new GenericObjectPool();
        connectionPool.setMaxActive(DBPool.POOL_SIZE);
        //Create factor
        ConnectionFactory cf = new DriverManagerConnectionFactory(DBPool.URL,DBPool.USER,DBPool.PASSWORD);
        //Create PoolableConnectionFactory
        PoolableConnectionFactory pcf =
                new PoolableConnectionFactory(cf, connectionPool,
                        null, null, false, true);
        return new PoolingDataSource(connectionPool);
	}
	
	public GenericObjectPool getConnectionPool() {
        return connectionPool;
    }
    
    public static Connection getConnection() throws Exception {
		//Initalize connection pool if not already done
		if(DBPool.ds==null){
			DBPool.dbp = new DBPool();
			DBPool.ds  = DBPool.dbp.setUp();
		}
		return DBPool.ds.getConnection();
	}
	
	public static int getRowCount(ResultSet rs) {
		int rows = 0;
		int start;

		try {
			start = rs.getRow();
			if (rs.last()) {
				rows = rs.getRow();
				// Restore pointer to original position
				if (start == 0)
					rs.beforeFirst();
				else if (start != rows)
					rs.absolute(start);
			}
		} catch (SQLException e) {
			return 0;
		}
		return rows;
	}
	public static void displayStats() {
		try {
			GenericObjectPool pool = DBPool.dbp.getConnectionPool();
			if(DBPool.ds==null) {
				System.out.printf("\n==DBCP Stats==\n\tNot yet initalized\n");
			} else {
				System.out.printf("\n==DBCP Stats==\nMaxActive:\t%d\nActive:\t%d\nIdle:\t%d\nRemaining:\t%d\n", pool.getMaxActive(),pool.getNumActive(),pool.getNumIdle(),(pool.getMaxActive()-pool.getNumActive()));
			}
		} catch(Exception e) {}
	}


}
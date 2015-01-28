package com.savemgo.mgo1;
import com.savemgo.mgo1.DBPool;


import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.net.InetSocketAddress;
import java.net.InetAddress;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.FileAppender;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.mina.transport.socket.nio.NioDatagramAcceptor;
import org.apache.mina.transport.socket.DatagramSessionConfig;

import com.savemgo.mgo1.mina.PacketCodecFactory;
import com.savemgo.mgo1.mina.ServerHandler;
import com.savemgo.mgo1.mina.DNSHandler;

public class GameServer {
	private static final Logger logger = Logger.getLogger(ServerHandler.class);

	public static void main(String[] args) {
		//Determine config file to load
		String sConfigFile = null;
		if(args.length == 0) {
			sConfigFile = new String("mgo1.config");
		} else sConfigFile = args[0];

		//Attempt to load property file
		InputStream is = null;
		Properties p = new Properties();
		try {
			is = new FileInputStream(sConfigFile);
		} catch(FileNotFoundException e) {
			System.out.printf("Configuration file '%s' not found.\n",sConfigFile);
			createConfig(sConfigFile);
			//check if it was created successfully
			try {
				is = new FileInputStream(sConfigFile);
			} catch(FileNotFoundException ee) {
				//Couldn't create file, exit application
				System.out.println("Configuration file was not created successfully.\n");
				System.exit(-1);
			}
		}

		try {p.load(is);} //Load properties file
		catch(Exception e) { 
			System.out.println("Could not load properties file.");
			System.exit(-1);
		}
		System.out.printf("Loaded configuration from '%s'\n", sConfigFile);
		//Setup Databpase connection pool
		DBPool.URL       = p.getProperty("DB_URL");
		DBPool.USER      = p.getProperty("DB_USER");
		DBPool.PASSWORD  = p.getProperty("DB_PASS");
		DBPool.POOL_SIZE = Integer.parseInt(p.getProperty("DB_POOL"));

		System.out.printf("Configuring Logging information.\n");
		//Setup Log4j 
		logger.getRootLogger().getLoggerRepository().resetConfiguration();
		ConsoleAppender console = new ConsoleAppender();
		String PATTERN = "[%d{HH:mm:ss}] %m%n";
		console.setLayout(new PatternLayout(PATTERN));
		console.setThreshold(Level.DEBUG);
		console.activateOptions();
		logger.getRootLogger().addAppender(console);

		//Setup File Appender
		FileAppender fileLog = new FileAppender(); //create appender
		fileLog.setFile("./logs/errors.txt");
		fileLog.setLayout(new PatternLayout(PATTERN));
		fileLog.setThreshold(Level.ERROR);
		fileLog.activateOptions();
		logger.getRootLogger().addAppender(fileLog);


		String[] aLobbies = p.getProperty("LOBBIES").split(":");

		Connection con    = null;
		try {
			con = DBPool.getConnection();
		} catch(Exception e) {
			logger.error(e.getMessage());
			logger.error("Unable to establish connection to database");
			System.exit(-1);
		}
		logger.info(String.format("Established connection to database."));

		for(String v:aLobbies) {
			if(v.equals("")) {
				logger.info("No lobbies to start up.");
				break;
			}
			try {
				logger.info(String.format("Querying for lobby %s", v));
				PreparedStatement stmt = con.prepareStatement("SELECT id,name,ip,port FROM lobbies WHERE id=?");
				stmt.setString(1, v);
				ResultSet rs = stmt.executeQuery();
				if(DBPool.getRowCount(rs)==0) {
					throw new Exception(String.format("Lobby %s not found.",v));
				} else {
					//Display the server information being added
					rs.first();
					System.out.printf("Starting %s @ %s:%d\n", rs.getString("name"), rs.getString("ip"), rs.getInt("port"));

					//Establish handler classes
					PacketHandler packetHandler = new PacketHandler();
					ServerHandler serverHandler = new ServerHandler(packetHandler);

					//Create socket and bind
					NioSocketAcceptor acceptor = new NioSocketAcceptor();
					acceptor.getFilterChain().addLast("protocol", new ProtocolCodecFilter(new PacketCodecFactory()));
					acceptor.setHandler(serverHandler);
					acceptor.setReuseAddress(true);
					
					SocketSessionConfig scfg = acceptor.getSessionConfig();
					scfg.setReadBufferSize(1024);
					
					//Try binding to socket, retry every 5seconds until success
					boolean hadError;
					InetSocketAddress isa = null;
					do {
						hadError = false;
						try {
							isa = new InetSocketAddress(InetAddress.getByName(rs.getString("ip")), rs.getInt("port"));
							acceptor.bind(isa);
						} catch(Exception e) {
							//Couldn't bind the socket, might be inuse already
							logger.info("Unable to bind, trying again in five seconds.");
							hadError = true;
							try { Thread.sleep(5000);}
							catch(Exception ex) { /* Do Nothing */ }
						}
					} while(hadError);
					logger.info(String.format("%s successfully started.",rs.getString("name")));
					//Added Lobby to lobby listing
					Globals.lobbies.put(isa.toString(), rs.getInt("id"));

					//Reset database for this lobby
					try {
						//Delete any persistent games
						stmt = con.prepareStatement("DELETE FROM games WHERE lobby=?");
						stmt.setString(1, v);
						stmt.executeUpdate();
						//Reset the player count
						stmt = con.prepareStatement("UPDATE lobbies SET players=0 WHERE id=?");
						stmt.setString(1, v);
						stmt.executeUpdate();
						//Update users table to remove any players idled in here
						stmt = con.prepareStatement("UPDATE users SET lobby_id=0 WHERE lobby_id=?");
						stmt.setString(1, v);
						stmt.executeUpdate();
					} catch(Exception e) {
						logger.error(e.getMessage());
						logger.error("Lobby initialization queries failed.");
					}


				}
			} catch(Exception e) {
				logger.error(e.getMessage());
				logger.error(String.format("Unable to launch lobby with id: %s", v));
			}

		}
		try{ con.close(); }catch(Exception ee) {}
		//Launch DNS if desired
		if(p.getProperty("DNS").equals("yes")) {
			logger.info("Starting DNS.");
			NioDatagramAcceptor acceptor = new NioDatagramAcceptor();
			acceptor.setHandler(new DNSHandler(p.getProperty("DNS_HTTP"),p.getProperty("DNS_STUN"),p.getProperty("DNS_AUTH"),p.getProperty("DNS_MGO2")));

			DatagramSessionConfig dcfg = acceptor.getSessionConfig();
			dcfg.setReuseAddress(true);

			boolean hadError;
			do {
				hadError = false;
				try {
					acceptor.bind(new InetSocketAddress(Integer.parseInt(p.getProperty("DNS_PORT"))));
				} catch(Exception e) {
					//Couldn't bind the socket, might be inuse already
					logger.error(e.getMessage());
					logger.error("Unable to bind DNS, trying again in five seconds.");
					hadError = true;
					try { Thread.sleep(5000);}
					catch(Exception ex) { /* Do Nothing */ }
				}
			} while(hadError);
			logger.info(String.format("DNS successfully started on port %s.",p.getProperty("DNS_PORT")));
		} else {
			logger.info("Not running DNS.");
		}
		logger.info("SaveMGO MGO1 Server started.");
	}

	//Simple class that prompts the user for input and returns their input
	protected static String prompt(String sPrompt) {
		Scanner stdin = new Scanner(System.in);
		System.out.printf("%s",sPrompt);
		return stdin.nextLine();
	}

	//Creates the properties file
	protected static void createConfig(String sFile) {
		Connection con = null;	
		System.out.printf("Creating configuration file...\n");

		//Database Information
		String sDBHost = prompt("Database host:       \t");
		String sDBPort = prompt("Database port: [3306]\t");
		String sDB     = prompt("Database name:       \t");
		String sDBUser = prompt("Database user:       \t");
		String sDBPass = prompt("Database pass:       \t");
		int iPool      = Integer.parseInt(prompt("Database pool size:  \t"));
		
		//Setup DB pool to see that the connection works
		DBPool.URL      = String.format("jdbc:mysql://%s:%s/%s", sDBHost,sDBPort,sDB);
		DBPool.USER     = sDBUser;
		DBPool.PASSWORD = sDBPass;
		DBPool.POOL_SIZE = iPool;
		//Check if connection works
		try { con = DBPool.getConnection();	}
		catch(Exception e) {
			System.out.printf("Unable to connect to database.");
			System.exit(-1);
		}

		//DNS Information if desired
		String sDNS   = "";
		int iDNSPort  = 0;
		String sDNSIP = "";		
		String sDNS_HTTP = "";
		String sDNS_STUN = "";
		String sDNS_AUTH = "";
		while(!(sDNS.equals("yes")||sDNS.equals("no"))) {
			sDNS = prompt("Run DNS (yes|no):    \t").toLowerCase();
		}
		if(sDNS.equals("yes")) {
			iDNSPort = Integer.parseInt(prompt("DNS Port:\t"));
			sDNS_HTTP = prompt("IP for HTTP Server:\t");
			sDNS_STUN = prompt("IP for STUN Server:\t");
			sDNS_AUTH = prompt("IP for ATUH Server:\t");
		}

		//Get Lobby Information
		int iLobbyCount = Integer.parseInt(prompt("Number of lobbies to run: "));
		//Store ip/port information
		int[] iLobbies = new int[iLobbyCount];
		for(int i=0;i<iLobbyCount;i++) {
			System.out.printf("[%d] ", i+1);
			int iLobby = Integer.parseInt(prompt("Lobby Id from Database: "));
			try {
				//Query for desired lobby make sure it is in the database
				PreparedStatement stmt = con.prepareStatement("SELECT id,type,name,ip,port FROM lobbies WHERE id=?");
				stmt.setInt(1, iLobby);
				ResultSet rs = stmt.executeQuery();
				if(DBPool.getRowCount(rs)==0) {
					throw new Exception("Lobby not found.");
				} else {
					//Display the server information being added
					rs.first();
					System.out.printf("%s @ %s:%d\n", rs.getString("name"), rs.getString("ip"), rs.getInt("port"));
					iLobbies[i] = iLobby;

				}
			} catch(Exception e) {
				System.out.println(e.getMessage());
				i--; //Backtrack since this was unsuccessful
			}
		}
		try{ con.close(); }catch(Exception ee) {}

		//Create a tokenized string
		StringBuilder sLobbies = new StringBuilder();
		for(int v : iLobbies){
			if(sLobbies.toString().equals("")) sLobbies.append(v);
			else sLobbies.append(":").append(v);
		}


		//Start saving the properties file
		Properties p = new Properties();
		//Database Config
		p.setProperty("DB_URL", String.format("jdbc:mysql://%s:%s/%s", sDBHost,sDBPort,sDB));
		p.setProperty("DB_USER", sDBUser);
		p.setProperty("DB_PASS", sDBPass);
		p.setProperty("DB_POOL", String.format("%d",iPool));
		//DNS Information
		p.setProperty("DNS", sDNS);
		p.setProperty("DNS_PORT", String.format("%d",iDNSPort));
		p.setProperty("DNS_HTTP", sDNS_HTTP);
		p.setProperty("DNS_STUN", sDNS_STUN);
		p.setProperty("DNS_AUTH", sDNS_AUTH);
		//Lobbies
		p.setProperty("LOBBIES",sLobbies.toString());
		//Save config file
		try { p.store(new FileOutputStream(sFile),null); }
		catch(Exception e) {}

	}

}

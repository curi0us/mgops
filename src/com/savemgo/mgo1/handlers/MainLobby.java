package com.savemgo.mgo1.handlers;

import java.util.LinkedList;
import java.util.Queue;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.buffer.IoBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharsetDecoder;

import com.savemgo.mgo1.Packet;
import com.savemgo.mgo1.PacketHandler;
import com.savemgo.mgo1.DBPool;
import com.savemgo.mgo1.MGOException;
import com.savemgo.mgo1.Globals;

public class MainLobby {
	/**
	 * Generates the Lobby List packets and adds them to the queue
	 */
	public static void handleGetLobbyListing(IoSession session,Packet pktIn, Queue<Packet> queue, Connection conn) throws MGOException {
		Logger logger = Logger.getLogger(MainLobby.class);
		CharsetEncoder ce = Charset.forName("ISO-8859-1").newEncoder();
		int rows = 0;
		IoBuffer bb = null;

		try {
			//Get connection and perform query
			PreparedStatement stmt = conn.prepareStatement("SELECT id,type,name,ip,port,players,trusted FROM lobbies");
			ResultSet rs = stmt.executeQuery();
			
			//Allocate buffer for packet payload
			rows = DBPool.getRowCount(rs);
			bb = IoBuffer.allocate(45 * rows);
			
			//Add lobbies to payload
			while (rs.next()) {
				bb.putUnsignedInt(rs.getInt("id"))
				  .putUnsignedInt(rs.getInt("type"))
				  .putString(rs.getString("name"),15,ce)
				  .put((byte)0x00)
				  .putString(rs.getString("ip"),15,ce)
				  .putUnsignedShort(rs.getInt("port"))
				  .putUnsignedShort(rs.getInt("players"))
				  .putUnsignedShort(rs.getInt("trusted"));
            }
		} catch(Exception e) {
			throw new MGOException(0x2003, 1);
		}
		//Add it to the queue
		queue.add(new Packet(0x2003, bb.array()));
	}
	/**
	 * Generates the news list packets and adds them to the queue
	 */
	public static void handleGetNewsListing(IoSession session,Packet pktIn, Queue<Packet> queue, Connection conn) throws MGOException {
		CharsetEncoder ce = Charset.forName("ISO-8859-1").newEncoder();
		//int rows = 0;
		IoBuffer bb = null;

		try {
			//Get connection and perform query
			PreparedStatement stmt = conn.prepareStatement("SELECT id,time,topic,message FROM news WHERE visible=1 ORDER BY id DESC LIMIT 5");
			ResultSet rs = stmt.executeQuery();

			//Add lobbies to payload
			while (rs.next()) {
				String msg = Globals.unescapeMySQLString("\"" + rs.getString("message") + "\"");
				msg = msg.substring(0, Math.min(msg.length(), 1023));
				bb = IoBuffer.allocate(89 + msg.length());
				bb.putUnsignedInt(rs.getInt("id"))
				  .putUnsigned(1)
				  .putString(rs.getString("time"),19, ce)
				  .putString(rs.getString("topic"), 64, ce)
				  .putString(msg, ce)
				  .put((byte)0x00);
				//Add this message to queue, each news message can be its own packet
				queue.add(new Packet(0x200A,bb.array()));
				bb.free();
            }
		} catch(Exception e) {
			throw new MGOException(0x200A, 1,e);
		}
	}
	
	/**
	 * Handles user disconnect
	 */
	public static void handleDisconnect(IoSession session, Packet pkt, Queue<Packet> queue, Connection con) throws MGOException {
			PreparedStatement stmt;
			ResultSet rs;
			//Grab necessary information about user
			int lobby = (Integer)session.getAttribute("lobby",0);
			int uid = (Integer)session.getAttribute("userid",0);
			try {
				if(uid >0) {
					//Grab users current session_id
					stmt = con.prepareStatement("SELECT session_id FROM users WHERE id=?");
					stmt.setInt(1,uid);
					rs = stmt.executeQuery();
					//Move cursor to first (and should be only) row
					if(!rs.first()) {
						//This shouldn't happen, but return here if it does isntead of an exception later.
						return;
					}
					String current_sessid = rs.getString("session_id");

					//Is this a disconnect from a Lobby?
					if(lobby > 0) {
						stmt = con.prepareStatement("UPDATE lobbies SET players = players-1 WHERE id=? and players > 0");
						stmt.setInt(1,lobby);
						stmt.executeUpdate();

						//Is this the disconnection of the users active session?
						if(current_sessid.equals((String)session.getAttribute("session_id",""))) {
							//Remove player from lobby
							stmt = con.prepareStatement("UPDATE users SET lobby_id=0 WHERE id=?");
							stmt.setInt(1,uid);
							stmt.executeUpdate();
							//Delete any game this player was host of
							stmt = con.prepareStatement("DELETE FROM games WHERE host_id=?");
							stmt.setInt(1,uid);
							stmt.executeUpdate();
							//Host should have already remvoed them
							stmt = con.prepareStatement("DELETE FROM players WHERE user_id=?");
							stmt.setLong(1,uid);
							stmt.executeUpdate();

						}
					}
				}
			} catch(Exception e) {
				throw new MGOException(0x0000,0,e);
			} finally {
				//Destroy session
				session.setAttribute("session_id", null);
				session.setAttribute("session_seq", 0);
				session.setAttribute("userid", null);
				session.close(true);//Close immediately
			}

	}
}
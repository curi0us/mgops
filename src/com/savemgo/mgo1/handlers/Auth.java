package com.savemgo.mgo1.handlers;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharsetDecoder;

import com.savemgo.mgo1.Packet;
import com.savemgo.mgo1.PacketHandler;
import com.savemgo.mgo1.DBPool;
import com.savemgo.mgo1.MGOException;
import com.savemgo.mgo1.Globals;

public class Auth {
	/**
	 * Handles login attempts
	 */
	public static void handleLogin(IoSession session,Packet pktIn, Queue<Packet> queue, Connection con) throws MGOException {
		IoBuffer iob = IoBuffer.wrap(pktIn.payload);
		IoBuffer bb = IoBuffer.allocate(20);

		CharsetDecoder d = Charset.forName("ISO-8859-1").newDecoder();
		String username = "";
		String encPasswordClient = "";
		String encPasswordServer = "";
		try {
			//Parse information from payload
			username = iob.getString(16, d);
			iob.position(16);
			byte[] encPassword = new byte[16];
			iob.get(encPassword, 0, 16);
			encPasswordClient = Globals.bytesToHex(encPassword);
			iob.free();
			//Check if this is a valid user
			PreparedStatement stmt = con.prepareStatement("SELECT id,username,lobby_id FROM users WHERE username=? AND password=?");
			stmt.setString(1, username);
			stmt.setString(2, encPasswordClient);
			ResultSet rs = stmt.executeQuery();
			
			//If we don't get a row, then this was a failed login
			if(DBPool.getRowCount(rs)!=1) {
				stmt = con.prepareStatement("SELECT id,username,lobby_id FROM users WHERE username=?");
				stmt.setString(1, username);
				rs = stmt.executeQuery();
				if(DBPool.getRowCount(rs)<=0) {
					throw new MGOException(0x3004,1, "Invalid user.");
				}

				con.close();
				throw new MGOException(0x3004,2, "Invalid credentials.");
			}
			rs.first();
			int userid = rs.getInt("id");
			int lobbyId = rs.getInt("lobby_id");
			//This is a valid login attempt
			byte[] sessionId = new byte[16];
			boolean exists = false;
			do {
				(new SecureRandom()).nextBytes(sessionId);
				stmt = con.prepareStatement("SELECT id FROM users WHERE session_id=?");
				stmt.setString(1, Globals.bytesToHex(sessionId));
				rs = stmt.executeQuery();
				if(DBPool.getRowCount(rs)!=0) exists = true;
			} while (exists);
			
			//Setup session attributes
			String sessionIdStr = Globals.bytesToHex(sessionId);
			session.setAttribute("session_id", sessionIdStr);
			session.setAttribute("userid", userid);

			if(lobbyId > 0) {
				//If user is still seen as in a lobby, remove them
				stmt = con.prepareStatement("DELETE FROM games WHERE host_id=?");
				stmt.setLong(1,userid);
				stmt.executeUpdate();

				stmt = con.prepareStatement("DELETE FROM players WHERE user_id=?");
				stmt.setLong(1,userid);
				stmt.executeUpdate();

				stmt = con.prepareStatement("UPDATE lobbies SET players=players-1 WHERE id=? and players > 0");
				stmt.setInt(1,lobbyId);
				stmt.executeUpdate();
				
				stmt = con.prepareStatement("UPDATE users SET lobby_id=0 WHERE id=?");
				stmt.setInt(1,userid);
				stmt.executeUpdate();
			}

			//Update Session id
			stmt = con.prepareStatement("UPDATE users SET session_id=? WHERE id=?");
			stmt.setString(1, sessionIdStr);
			stmt.setInt(2, userid);
			stmt.executeUpdate();

			//Send it out to wire
			bb.putUnsignedInt(0x0000).put(sessionId);
			queue.add(new Packet(0x3004, bb.array()));
		} catch(MGOException e) {
			throw e;
		} catch (Exception e) {
			bb.free();
			throw new MGOException(0x3004, 10, e);
		}
		bb.free();
	}
	/**
	 * Handles session id verification attempts
	 */
	public static void handleSessionCheck(IoSession session,Packet pktIn, Queue<Packet> queue, Connection con) throws MGOException {
		try {
			//Parse payload
			IoBuffer iob = IoBuffer.wrap(pktIn.payload);
			long userId = iob.getUnsignedInt();
			byte[] sessionIdBytes = new byte[16];
			iob.get(sessionIdBytes, 0, 16);
			iob.free();
			String sessionId = Globals.bytesToHex(sessionIdBytes);

			//Find user with this session
			PreparedStatement stmt = con.prepareStatement("SELECT id,lobby_id FROM users WHERE session_id=?");
			stmt.setString(1, sessionId);
			ResultSet rs = stmt.executeQuery();
			if(DBPool.getRowCount(rs)!=1) {
				throw new MGOException(0x3004,2, "Unknown session id: " +sessionId);
			}
			rs.first();
			int id = rs.getInt("id");
			int lobbyId = rs.getInt("lobby_id");

			//Setup session
			session.setAttribute("session_id", sessionId);
			session.setAttribute("userid", id);
			session.setAttribute("lobby",Globals.lobbyId);

			if(lobbyId > 0) {
				//If user is still seen as in a lobby, remove them
				stmt = con.prepareStatement("DELETE FROM games WHERE host_id=?");
				stmt.setLong(1,id);
				stmt.executeUpdate();

				stmt = con.prepareStatement("DELETE FROM players WHERE user_id=?");
				stmt.setLong(1,id);
				stmt.executeUpdate();

			}
			
			//Update Player Count
			stmt = con.prepareStatement("UPDATE lobbies SET players = players +1 WHERE id=?");
			stmt.setInt(1,Globals.lobbyId);
			stmt.executeUpdate();
			//Update user's lobby information
			stmt = con.prepareStatement("UPDATE users SET lobby_id=? WHERE id=?");
			stmt.setInt(1,Globals.lobbyId);
			stmt.setInt(2,id);//user id
			stmt.executeUpdate();
			

			iob = IoBuffer.allocate(36);
			iob.putInt(0);
			iob.put(sessionIdBytes);
			queue.add(new Packet(0x3004, iob.array()));
			iob.free();
		} catch (Exception e) {
			throw new MGOException(0x3004, 11, e);
		}
	}

	/**
	 * Handles splayer information requests
	 */
	public static void handlePlayerInfo(IoSession session,Packet pktIn, Queue<Packet> queue, Connection con) throws MGOException {
		try {
			IoBuffer iob = IoBuffer.allocate(24, false);
			CharsetEncoder ce = Charset.forName("ISO-8859-1").newEncoder();
			//TODO: WHERE id=? and session_id='' so deal with sessions properly
			PreparedStatement stmt = con.prepareStatement("SELECT id,displayname FROM users WHERE id=?");
			stmt.setInt(1, (Integer) session.getAttribute("userid"));
			ResultSet rs = stmt.executeQuery();
			rs.first();
			//Create payload
			iob.putInt(0);
			iob.putUnsignedInt(rs.getInt("id"));
			iob.putString(rs.getString("displayname"), 15, ce);
			iob.put((byte) 0x00);
			//Send down wire
			queue.add(new Packet(0x3041,iob.array()));
			iob.free();
		} catch (Exception e) {
			throw new MGOException(0x3041, 11, e);
		}
	}
}
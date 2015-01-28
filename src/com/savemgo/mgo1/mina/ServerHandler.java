package com.savemgo.mgo1.mina;

import java.util.Queue;
import java.util.LinkedList;
import java.sql.Connection;

import org.apache.log4j.Logger;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSessionConfig;

import com.savemgo.mgo1.PacketHandler;
import com.savemgo.mgo1.Packet;
import com.savemgo.mgo1.DBPool;
import com.savemgo.mgo1.handlers.MainLobby;

public class ServerHandler extends IoHandlerAdapter {

	private static Logger logger = Logger.getLogger(ServerHandler.class);
	
	public PacketHandler packetHandler = null;
	
	public ServerHandler(PacketHandler packetHandler) {
		this.packetHandler = packetHandler;
	}
	
	public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
		logger.error("Exception caught", cause);
		session.close(true);
	}
	
	public void messageReceived(IoSession session, Object message) throws Exception {
		packetHandler.handlePacket(session, (Packet) message);
	}

	public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
		//Close the session
		Connection con = DBPool.getConnection();
		try {
			MainLobby.handleDisconnect(session, new Packet(0x00), (Queue<Packet>) new LinkedList<Packet>(), con);
		} catch(Exception e) {
			throw e; //pass it along
		} finally {
			try{ con.close(); } catch(Exception ee){}
		}
		
	}
	public void sessionClosed(IoSession session) throws Exception {
		//Close the session
		Connection con = DBPool.getConnection();
		try {
			MainLobby.handleDisconnect(session, new Packet(0x00), (Queue<Packet>) new LinkedList<Packet>(), con);
		} catch(Exception e) {
			throw e; //pass it along
		} finally {
			try{ con.close(); } catch(Exception ee){}
		}
	}
	public void sessionOpened(IoSession session) throws Exception {
		//Set idle time
		IoSessionConfig ic= session.getConfig();
		ic.setIdleTime(IdleStatus.READER_IDLE, 900); //10Minutes
	}
	
}

package com.savemgo.mgo1;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.sql.Connection;
import java.sql.SQLException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.mina.core.session.IoSession;

import com.savemgo.mgo1.handlers.*;

public class PacketHandler {

	private static Logger logger = Logger.getLogger(PacketHandler.class);

	public PacketHandler() {
		super();
	}

	public boolean handlePacket(IoSession session, Packet pktIn) {
		this.printPacket(pktIn, true); // For debugging purposes
		Queue<Packet> queue = new LinkedList<Packet>(); // Output queue
		Connection con = null;

		//Setup Lobby Id
		InetSocketAddress isa = (InetSocketAddress) session.getServiceAddress();
		Globals.lobbyId = Globals.lobbies.get(isa.toString());

		try {
			//Check there is a valid session for events requiring a login
			if(pktIn.cmd > 0x4000 && !Globals.hasSession(session)) {
				throw new MGOException(pktIn.cmd+1, 999, "Invalid Session");
			}
			//Grab database connection
			con = DBPool.getConnection();
			// Packet Handlers
			switch (pktIn.cmd) {
			case 0x0005: // Client Ping
				queue.add(new Packet(0x0005));
				break;
			case 0x0003: // Client Disconnect
				//Handled by ServerHandler.sessionClosed()
				break;

			/** General Lobby Server Packets */
			case 0x2005: // Client Request Lobby Listing
				queue.add(new Packet(0x2002)); // Lobby listing Start
				MainLobby.handleGetLobbyListing(session, pktIn, queue, con);
				queue.add(new Packet(0x2004)); // Lobby listing EOF
				break;

			case 0x2008: // Client Request News Listing
				queue.add(new Packet(0x2009)); // News listing Start
				MainLobby.handleGetNewsListing(session, pktIn, queue, con);
				queue.add(new Packet(0x200b)); // News listing EOF
				break;

			/** Authentication Server packets */
			case 0x3001:// Client Requests hash salt
				queue.add(new Packet(0x3002, Globals.staticAuthSalt));
				break;
			case 0x3003: // Click Login or session verification
				if (pktIn.payload.length == 32) {
					Auth.handleLogin(session, pktIn, queue, con);
				} else {
					Auth.handleSessionCheck(session, pktIn, queue, con);
				}
				break;
			case 0x3040: // Client Player info request
				Auth.handlePlayerInfo(session, pktIn, queue, con);
				break;
			case 0x3042:
				queue.add(new Packet(0x3043, new byte[4]));
				queue.add(new Packet(0x3045, new byte[4]));
				break;

			/** Regional Lobby Player-related packets */
			case 0x4100: // Client requests User Settings & Lobby Stats
				LobbyPlayer.getUserSettings(session, pktIn, queue, con);
				LobbyPlayer.getLobbyStats(session, pktIn, queue, con);
				break;
			case 0x4102: // Client requests Personal Stats
				LobbyPlayer.getPersonalStats(session, pktIn, queue, con);
				break;
			case 0x4110: // Client sends updated User Settings
				LobbyPlayer.handleUserSettings(session, pktIn, queue, con);
				break;
			case 0x4500: // Client adds player to a friend/black list
				LobbyPlayer.addToList(session,pktIn,queue,con);
				break;
			case 0x4510: // Client removes player from friends/black list
				LobbyPlayer.removeFromList(session,pktIn,queue,con);
				break;
			case 0x4580: // Client requests friend/black list
				queue.add(new Packet(0x4581));
				LobbyPlayer.getFBList(session, pktIn, queue, con);
				queue.add(new Packet(0x4583));
				break;
			case 0x4600: // Client searches a player
				// TODO: Find this out
				break;
			case 0x4700:
				LobbyPlayer.handleConnectionInfo(session,pktIn,queue,con);
				break;

			/** Lobby Game related requests */
			case 0x4112: // Client requests "sorted" game list
				queue.add(new Packet(0x4113, new byte[4]));
				LobbyGame.getGameList(session, pktIn, queue, con, 0x4114);
				queue.add(new Packet(0x4115, new byte[4]));
				break;
			case 0x4300: // Client requests a game list
				queue.add(new Packet(0x4301, new byte[4]));
				LobbyGame.getGameList(session, pktIn, queue, con, 0x4302);
				queue.add(new Packet(0x4303, new byte[4]));
				break;
			case 0x4312: // Client requests game info
				LobbyGame.getGameInfo(session, pktIn, queue, con);
				break;
			case 0x4304: //Client requests last create game settings
				LobbyGame.getPastGameSettings(session, pktIn, queue, con);
				break;
			case 0x4310: // Client sends game settings to create a game
				LobbyGame.handleCreateGame(session,pktIn,queue,con);
				break;
			case 0x4316: // Client says its ready to host
				queue.add(new Packet(0x4317));
				break;
			case 0x4320: //Client requests host connection information
				LobbyGame.getHostInformation(session,pktIn,queue,con);
				break;
			case 0x4322:
				queue.add(new Packet(0x4323,new byte[4]));
				break;

				
			/** Regional Lobby Host Related Packets */
			case 0x4340: //Player attempting to join
				LobbyHost.handleJoinAttempt(session,pktIn,queue,con);
				break;
			case 0x4342: //Player just quit/dc'd
				LobbyHost.handlePlayerQuit(session,pktIn,queue,con);
				break;
			case 0x4344: //Host sends information about team player just joined
				LobbyHost.handleTeamJoin(session,pktIn,queue,con);
				break;
			case 0x4346: //Host kicked this user
				LobbyHost.handleKick(session,pktIn,queue,con);
				break;
			case 0x4380: //Host is quitting the game
				LobbyHost.handleHostQuit(session,pktIn,queue,con);
				break;

			case 0x4394:
				queue.add(new Packet(0x4395,new byte[0]));
				break;
			case 0x4390: //Host sends stats information
				LobbyHost.handleStats(session,pktIn,queue,con);
				break;
			case 0x4392:// Game tells us what round it is moving to(single byte)
				LobbyHost.handleNewRound(session,pktIn,queue,con);
				break;
			case 0x4398: //Host sends player ping information
				LobbyHost.handlePingInformation(session,pktIn,queue,con);
				break;
			default:
				logger.error(String.format(
						"Couldn't handle command 0x%02x (%d), not implemented",
						pktIn.cmd, pktIn.cmd));
				try { con.close(); } catch(Exception e) {}
				return false;
			}
		} catch(MGOException e) {			
			//Check if this exception is informational or has a real cause.
			if(e.hasCause()) {
//				e.getException().printStackTrace();
				if(e.getMessage()!=null) {
					logger.error(e.getMessage(), e.getException());
				} else {
					logger.error("MGOException", e.getException());
				}
			} else {
				if(e.getMessage()!=null) logger.info(e.getMessage());
			}
			//Send error out
			queue.add(new Packet(e.getCommand(), e.getPayload()));
		} catch(Exception e) {
			e.printStackTrace();

		} finally {
			//Done processing close the connection
			try{ con.close(); }catch(Exception ee) {}

		}
		// Loop over output queue and write to wire
		Iterator<Packet> iterator = queue.iterator();
		while (iterator.hasNext()) {
			Packet pktOut = (Packet) iterator.next();
			session.write(pktOut);
			this.printPacket(pktOut, false);
		}

		return true;
	}

	/**
	 * Prints the supplied packet, if LogLevel is DEBUG
	 */
	protected void printPacket(Packet pktIn, boolean recv) {
		if (logger.getEffectiveLevel() == Level.DEBUG) {
			logger.debug(String.format(((recv) ? "Received" : "Sending")
					+ " command 0x%02x (%d)", pktIn.cmd, pktIn.cmd));
			String tempString = "";
			int debugWidth = 16;
			if (pktIn.payload.length > 0) {
				for (int i = 0; i < pktIn.payload.length; i++) {
					tempString += String.format("%02x ", pktIn.payload[i]);
					if (i % debugWidth == debugWidth - 1)
						tempString += "\n";
				}
				logger.debug(String.format("\n\n%s\n\n", tempString));
			}
		}
	}

}
package com.savemgo.mgo1.handlers;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Queue;

import org.apache.log4j.Logger;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.util.Base64;

import com.savemgo.mgo1.DBPool;
import com.savemgo.mgo1.MGOException;
import com.savemgo.mgo1.Packet;

public class LobbyPlayer {

	private static final Logger logger = Logger.getLogger(LobbyPlayer.class);

	/**
	 * Gets the User Settings data from the database and adds packet to queue
	 */
	public static void getUserSettings(IoSession session, Packet pktIn,
			Queue<Packet> queue, Connection conn) throws MGOException {
		CharsetEncoder ce = Charset.forName("ISO-8859-1").newEncoder();

		long pid = (Integer) session.getAttribute("userid", -1);
		if (pid == -1) {
			throw new MGOException(0x4101, 1, "No User ID in IoSession.");
		}

		long flags;
		String displayName, emblemText;
		byte[] userSettings;

		try {
			// Prepare SQL statement and execute
			PreparedStatement stmt = conn
					.prepareStatement("SELECT displayname, flags, emblem_text, user_settings FROM users WHERE id=?");
			stmt.setLong(1, pid);
			ResultSet rs = stmt.executeQuery();
			rs.first();

			// Set Player ID, Display Name, Flags
			displayName = rs.getString("displayname");
			session.setAttribute("displayname", displayName); //Store the users displayname so we can use it in notices
			flags = rs.getLong("flags");
			emblemText = rs.getString("emblem_text");

			// Parse the User Settings, use defaults if an error occurs
			String encUserSettings = "";
			try {
				encUserSettings = rs.getString("user_settings");
			} catch (SQLException e) {
				encUserSettings = "EUAEBBIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
			}
			userSettings = Base64.decodeBase64(encUserSettings.getBytes());
		} catch (SQLException e1) {
			// Generic SQL error
			throw new MGOException(0x4101, 2, e1);
		}

		// Byte arrays which are unknown
		byte[] bytez1 = { (byte) 0x00, (byte) 0x29, (byte) 0x00, (byte) 0x06,
				(byte) 0x00, (byte) 0x04, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0xec,
				(byte) 0x45, (byte) 0xc0, (byte) 0xf6, (byte) 0x29,
				(byte) 0x45, (byte) 0xbd, (byte) 0x1d, (byte) 0x9f };

		byte[] bytez2 = { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,

				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
				(byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0x78,
				(byte) 0x00, (byte) 0x00, (byte) 0x0b, (byte) 0x9e };

		// Start Friends List
		ResultSet rs = null;
		PreparedStatement stmt = null;
		IoBuffer lists = IoBuffer.allocate(128 + 4); // +4 for last 4 bytes
		try {
			// Friends list
			stmt = conn
					.prepareStatement("SELECT user_id FROM lists WHERE type=0 AND owner_id=?");
			stmt.setLong(1, pid);
			rs = stmt.executeQuery();
			for (int i = 0; i < 16; i++) {
				if (rs.next()) {
					lists.putUnsignedInt((long) rs.getInt("user_id"));
				} else {
					lists.putUnsignedInt(0x00000000);
				}
			}

			// Black list
			stmt = conn
					.prepareStatement("SELECT user_id FROM lists WHERE type=1 AND owner_id=?");
			stmt.setLong(1, pid);
			rs = stmt.executeQuery();
			for (int i = 0; i < 16; i++) {
				if (rs.next()) {
					lists.putUnsignedInt((int) rs.getInt("user_id"));
				} else {
					lists.putUnsignedInt(0x00000000);
				}
			}
		} catch (SQLException e) {

		}
		lists.putUnsignedInt(0x0000341e);

		// Create payload
		IoBuffer iob = IoBuffer.allocate(552, false);
		iob.putUnsignedInt(pid);
		try {
			iob.putString(displayName, 15, ce);
		} catch (CharacterCodingException ex) {
			ex.printStackTrace();
		}
		iob.put((byte) 0).putUnsignedInt(flags);
		try {
			iob.putString(emblemText, 15, ce);
		} catch (CharacterCodingException ex) {
			ex.printStackTrace();
		}
		iob.put((byte) 0).put(bytez1).put(userSettings)
			.put(bytez2).put(lists.array());
		lists.free();
		byte[] payload = iob.array();
		iob.free();

		// Add it to the queue
		queue.add(new Packet(0x4101, payload));
	}

	/**
	 * Gets the Personal Stats and adds packet to queue
	 */
	public static void getPersonalStats(IoSession session, Packet pktIn,
			Queue<Packet> queue, Connection con) throws MGOException {
		CharsetEncoder ce = Charset.forName("ISO-8859-1").newEncoder();

		IoBuffer iob = IoBuffer.wrap(pktIn.payload);
		long userid = iob.getUnsignedInt();
		iob.free();

		String displayName = "SaveMGO.com";
		try {
			// Prepare SQL statement and execute
			PreparedStatement stmt = con
					.prepareStatement("SELECT displayname FROM users WHERE id=?");
			stmt.setLong(1, userid);
			ResultSet rs = stmt.executeQuery();
			rs.first();
			displayName = rs.getString("displayname");
		} catch (SQLException e) {
			throw new MGOException(0x4103, 1, e);
		}

		// Random data for now
		byte[] bytez1 = new byte[] {
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,
				(byte) 0x01,

				(byte) 0x00,
				(byte) 0x00,
				(byte) 0x05,
				(byte) 0x39, // Vs. Rating

				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,

				(byte) 0x00, (byte) 0x04, (byte) 0xc7, (byte) 0xf9, // All Rules
																	// Score
																	// Rank
				(byte) 0x02, (byte) 0x04, (byte) 0xc7, (byte) 0xfa, // All
																	// Rules,
																	// score
																	// Rank
																	// (This
																	// week)
				(byte) 0x00, (byte) 0x00, (byte) 0x7a, (byte) 0x69, // Vs.
																	// Rating
																	// Rank

		};

		iob = IoBuffer.allocate(236, false);
		iob = IoBuffer.allocate(24, false);
		iob.putUnsignedInt(0).putUnsignedInt(userid);
		try {
			iob.putString(displayName, 16, ce);
		} catch (CharacterCodingException ex) {
			ex.printStackTrace();
		}
		//iob.put(bytez1);

		byte[] payload = iob.array();
		iob.free();

		// Add it to the queue
		queue.add(new Packet(0x4103, payload));
	}

	/**
	 * Gets the Lobby Stats and adds packet to queue
	 */
	public static void getLobbyStats(IoSession session, Packet pktIn,
			Queue<Packet> queue, Connection con) throws MGOException {
		int userid = (Integer) session.getAttribute("userid", -1);
		if (userid == -1) {
			throw new MGOException(0x4104, 1, "No User ID for the IoSession.");
		}

		IoBuffer iob = null;
		byte[] payload1 = null, payload2 = null;
		try {
			// Prepare SQL statement and execute
			PreparedStatement stmt = con
					.prepareStatement("SELECT type_id, score_rank as rank,kills, deaths, kill_streak, death_streak, score_rank, stuns, stuns_received, teammate_kills, teammate_stuns, rounds_played,rounds_played_without_dying, head_shots, head_shots_received, team_wins, kills_with_scorpion, kills_with_knife, times_eaten, rolls, infrared_goggle_uses, play_time, vs_rating, vs_rating_rank,kerotans_placed_for_win,kerotans_placed,gakos_rescued,goals_reached_as_snake,points FROM stats WHERE user_id=? ORDER BY period_id, type_id");
			stmt.setLong(1, userid);
			ResultSet rs = stmt.executeQuery();
			int rows = DBPool.getRowCount(rs);
			if (rows == 0) {
				logger.debug("No stats found");
				// If no stats, empty payload (should check to see if we have
				// enough later)
				payload1 = new byte[604];
				payload2 = new byte[604];
				payload2[3] = (byte) 0x01;
			}

			iob = IoBuffer.allocate(604, false);
			iob.putUnsignedInt(0);

			int i = 0;
			while (rs.next()) {
				long kills = rs.getInt("kills");
				long deaths = rs.getInt("deaths");
				int  kill_streak = rs.getInt("kill_streak");
				int  death_streak = rs.getInt("death_streak");
				long scoreRank = rs.getInt("rank");
				long stuns = rs.getInt("stuns");
				long stunsReceived = rs.getInt("stuns_received");
				long teammateKills = rs.getInt("teammate_kills");
				long teammateStuns = rs.getInt("teammate_stuns");
				long roundsPlayed = rs.getInt("rounds_played");
				long roundsNoDeath = rs.getInt("rounds_played_without_dying");
				long headShots = rs.getInt("head_shots");
				long headShotsReceived = rs.getInt("head_shots_received");
				long teamWins = rs.getInt("team_wins");
				long killsWithScorpion = rs.getInt("kills_with_scorpion");
				long killsWithKnife = rs.getInt("kills_with_knife");
				long timesEaten = rs.getInt("times_eaten");
				long rolls = rs.getInt("rolls");
				long infraredGoggleUses = rs.getInt("infrared_goggle_uses");
				long playTime = rs.getInt("play_time");
				long vsRating = rs.getInt("vs_rating");
				long vsRatingRank = rs.getInt("vs_rating_rank");
				long keroForWin = rs.getInt("kerotans_placed_for_win");
				long keroPlaced = rs.getInt("kerotans_placed");
				long points = rs.getInt("points");
				if(scoreRank != 1) {
					if(scoreRank > 5) scoreRank += 200; //No Fox/FH
					else scoreRank += 30; //Only Top 5 get FOX
				}
				//Gakos Rescued = Kero for win AND kero placed = goals as snake
				switch(rs.getInt("type_id")) {
					case 2://res
						keroForWin = rs.getInt("gakos_rescued");
						break;
					case 4://sne
						keroPlaced = rs.getInt("goals_reached_as_snake");
						break;

				}
				iob.putUnsignedInt(kills).putUnsignedInt(deaths).putUnsignedShort(kill_streak).putUnsignedShort(death_streak)
				   .putUnsignedInt(stuns).putUnsignedInt(stunsReceived)
				   .putUnsignedInt(teammateKills).putUnsignedInt(teammateStuns).putUnsignedInt(0)
				   .putUnsignedInt(0).putUnsignedInt(0).putUnsignedInt(0).putUnsignedInt(roundsPlayed)
				   .putUnsignedInt(roundsNoDeath).putUnsignedInt(keroForWin).putUnsignedInt(keroPlaced).putUnsignedInt(0).putUnsignedInt(0)
				   .putUnsignedInt(0).putUnsignedInt(0).putUnsignedInt(headShots)
				   .putUnsignedInt(headShotsReceived).putUnsignedInt(teamWins)
				   .putUnsignedInt(killsWithScorpion).putUnsignedInt(killsWithKnife)
				   .putUnsignedInt(timesEaten).putUnsignedInt(rolls)
				   .putUnsignedInt(infraredGoggleUses).putUnsignedInt(playTime)
				   .putUnsignedInt(vsRating).putUnsignedInt(scoreRank);
				i++;
				if (i == 5) { // This is the end of the 1st payload
					logger.debug("End of payload 1");
					payload1 = iob.array();
					iob.free();
					iob = IoBuffer.allocate(604, false);
					iob.putUnsignedInt(1);
				} else if (i == 10) { // This is the end of the 2nd payload
					logger.debug("End of payload 2");
					payload2 = iob.array();
					iob.free();
				}
			}
			// No rows found
			if (i == 0) {
				for (int j = 0; j < 2; j++) {
					for (int k = 0; k < 5; k++) {
						stmt = con
								.prepareStatement("INSERT INTO `stats` (`id`, `user_id`, `period_id`, `type_id`, `vs_rating_rank`, `vs_rating`, `score_rank`, `rank_points`, `points`, `kills`, `deaths`, `kill_streak`, `death_streak`, `stuns`, `stuns_received`, `head_shots`, `head_shots_received`, `kills_with_scorpion`, `kills_with_knife`, `cqc_attacks`, `cqc_attacks_received`, `teammate_kills`, `teammate_stuns`, `radio_uses`, `text_chat_uses`, `times_eaten`, `rolls`, `infrared_goggle_uses`, `team_wins`, `goals_reached_as_snake`, `snake_frags`, `kerotans_placed`, `kerotans_placed_for_win`, `gakos_rescued`, `rounds_played`, `rounds_played_without_dying`, `play_time`) VALUES (NULL, ?, ?, ?, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);");
						stmt.setInt(1, userid);
						stmt.setInt(2, j); // period_id
						stmt.setInt(3, k); // type_id
						stmt.executeUpdate();
					}
				}
			}
		} catch (SQLException e) {
			if (iob != null)
				iob.free();
			throw new MGOException(0x4104, 1, e);
		}

		// Add it to the queue
		queue.add(new Packet(0x4104, payload1));
		queue.add(new Packet(0x4104, payload2));
	}

	/**
	 * Sets the database User Settings and adds packet to queue
	 */
	public static void handleUserSettings(IoSession session, Packet pktIn,
			Queue<Packet> queue, Connection con) throws MGOException {
		int res = 0;
		byte[] userSettings = pktIn.payload;
		try {
			String encUserSettings = new String(
					Base64.encodeBase64(userSettings));
			int pid = (Integer) session.getAttribute("userid", -1);

			PreparedStatement stmt = con
					.prepareStatement("UPDATE users SET user_settings=? WHERE id=?");
			stmt.setString(1, encUserSettings);
			stmt.setLong(2, pid);
			res = stmt.executeUpdate();
		} catch (SQLException e) {
			throw new MGOException(0x4111, 1, e);
		}
		if (res <= 0) {
			throw new MGOException(0x4111, 2,
					"SQL UPDATE did not affect any users.");
		}

		// Add the OK to the queue
		queue.add(new Packet(0x4111, new byte[4]));
	}

	public static void handleConnectionInfo(IoSession session, Packet pktIn,
			Queue<Packet> queue, Connection con) throws MGOException {
		try {
			// Parse connection information
			CharsetDecoder d = Charset.forName("ISO-8859-1").newDecoder();
			IoBuffer iob = IoBuffer.wrap(pktIn.payload);
			short remotePort = iob.getShort();
			String localAddr = iob.getString(16, d);
			short localPort = iob.getShort();
			// short unknown = iob.getShort(); //Not sure what this is, 01 b3
			int userid = (Integer) session.getAttribute("userid");

			// get remote IP
			InetSocketAddress socketAddress = (InetSocketAddress) session
					.getRemoteAddress();
			InetAddress inetAddress = socketAddress.getAddress();
			String remoteAddr = inetAddress.getHostAddress().toString();

			// Remove any existing connection information if any
			PreparedStatement stmt = con
					.prepareStatement("DELETE FROM `connections` WHERE user_id=?");
			stmt.setInt(1, userid);
			stmt.executeUpdate();

			// Add the connection information
			stmt = con
					.prepareStatement("INSERT INTO `connections`(`user_id`,`remote`,`remote_port`,`local`,`local_port`) VALUES(?,?,?,?,?)");
			stmt.setInt(1, userid);
			stmt.setString(2, remoteAddr);
			stmt.setInt(3, remotePort);
			stmt.setString(4, localAddr);
			stmt.setInt(5, localPort);
			stmt.executeUpdate();
			iob.free();

			// All is right in the world.
			queue.add(new Packet(0x4701, new byte[4]));
		} catch (Exception e) {
			throw new MGOException(0x4701, 1, e);
		}

	}

	public static void getFBList(IoSession session, Packet pktIn,
			Queue<Packet> queue, Connection con) throws MGOException {

		// Check this is a valid request
		if (pktIn.payload[0] > 1)
			throw new MGOException(0x4582, 1, "Unknown list request type.");

		// Start building payload
		CharsetEncoder ce = Charset.forName("ISO-8859-1").newEncoder();
		PreparedStatement stmt = null;
		ResultSet rs = null;
		IoBuffer iob = null;
		int count = 0;
		try {
			// Query list users
			stmt = con
					.prepareStatement("SELECT users.id, users.displayname, users.lobby_id, games.id as game_id ,games.name as game_name, lobbies.name as lobby_name FROM lists INNER JOIN users on users.id=lists.user_id LEFT JOIN players ON players.user_id=lists.user_id LEFT JOIN games ON games.id=players.game LEFT JOIN lobbies ON lobbies.id=users.lobby_id WHERE lists.owner_id=? AND lists.type=?");
			stmt.setInt(1, (Integer) session.getAttribute("userid"));// userid
			stmt.setInt(2, (int) pktIn.payload[0]);// list type
			rs = stmt.executeQuery();
			count = DBPool.getRowCount(rs);
			// allocate buffer
			iob = IoBuffer.allocate(58 * count);

			while (rs.next()) {
				iob.putUnsignedInt(rs.getInt("id"));
				iob.putString(rs.getString("displayname"), 16, ce);
				iob.putShort(rs.getShort("lobby_id"));

				String lobbyName = rs.getString("lobby_name");
				if (lobbyName != null)
					iob.putString(lobbyName, 16, ce);
				else
					iob.putString("", 16, ce);

				iob.putUnsignedInt(rs.getInt("game_id"));

				String gameName = rs.getString("game_name");
				if (gameName != null)
					iob.putString(gameName, 16, ce);
				else
					iob.putString("", 16, ce);

			}
			/*
			 * byte[] item = { //Player Id
			 * (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x08, //Player Name
			 * (byte)0x4d
			 * ,(byte)0x72,(byte)0x47,(byte)0x61,(byte)0x6d,(byte)0x65,
			 * (byte)0x32,(byte)0x30,
			 * (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00
			 * ,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,
			 * 
			 * //I suspect this is lobby type/id but I havn't gotten it to
			 * 'work' //But if lobby name = current lobby it doesn't check id
			 * //if its not the same it tries to change lobbies and fails
			 * (byte)0x00,(byte)0x00, //Lobby Name
			 * (byte)0x00,(byte)0x00,(byte)0x00
			 * ,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,
			 * (byte)0x00
			 * ,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,
			 * (byte)0x00,(byte)0x00,
			 * 
			 * //This is probably the game id, if > 0 it shows game name
			 * (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00, //Host name
			 * (byte)0x00
			 * ,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,
			 * (byte)0x00,(byte)0x00,
			 * (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00
			 * ,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,
			 * 
			 * 
			 * };
			 */
		} catch (Exception e) {
			throw new MGOException(0x4582, 10, e);
		}
		// NOTE: Max displayed is 16, so I take it there is a 16player limit on
		// lists
		queue.add(new Packet(0x4582, iob.array()));
		iob.free();
		// queue.add(new Packet(0x4582,"4582.txt"));
	}

	public static void removeFromList(IoSession session, Packet pktIn,
			Queue<Packet> queue, Connection con) throws MGOException {

		IoBuffer iob = IoBuffer.wrap(pktIn.payload);
		byte type = iob.get();
		long id = iob.getUnsignedInt();
		iob.free();
		if (type > 0)
			throw new MGOException(0x4512, 1, "Unknown list request type.");

		PreparedStatement stmt = null;
		try {
			stmt = con
					.prepareStatement("DELETE FROM lists WHERE owner_id=? AND type=? AND user_id=?");
			stmt.setInt(1, (Integer) session.getAttribute("userid"));
			stmt.setInt(2, (int) type);
			stmt.setLong(3, id);// id to remove
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new MGOException(0x4512, 10, e);
		}
		queue.add(new Packet(0x4512));

	}

	public static void addToList(IoSession session, Packet pktIn,
			Queue<Packet> queue, Connection con) throws MGOException {

		IoBuffer iob = IoBuffer.wrap(pktIn.payload);
		byte type = iob.get();
		long id = iob.getUnsignedInt();
		iob.free();
		if (type > 0) {
			throw new MGOException(0x4502, 1, "Unknown list request type.");
		}

		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			stmt = con.prepareStatement("SELECT COUNT(*) as cnt FROM lists WHERE type=? and owner_id=?");
			stmt.setInt(1,(int)type);
			stmt.setInt(2, (Integer) session.getAttribute("userid"));
			rs = stmt.executeQuery();
			rs.first();
			if(rs.getInt("cnt")>=16) {
				throw new MGOException(0x4502, 2, "Too many friends.");
			}

			stmt = con
					.prepareStatement("INSERT INTO lists(`type`,`owner_id`,`user_id`) VALUES(?,?,?)");
			stmt.setInt(1, (int) type);
			stmt.setInt(2, (Integer) session.getAttribute("userid"));			
			stmt.setLong(3, id);// id to add
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new MGOException(0x4502, 10, e);
		} 
		queue.add(new Packet(0x4502));
	}

}
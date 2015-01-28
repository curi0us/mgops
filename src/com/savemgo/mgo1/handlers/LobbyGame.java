package com.savemgo.mgo1.handlers;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Queue;

import org.apache.log4j.Logger;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.util.Base64;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSessionConfig;

import com.savemgo.mgo1.DBPool;
import com.savemgo.mgo1.Globals;
import com.savemgo.mgo1.MGOException;
import com.savemgo.mgo1.Packet;

public class LobbyGame {

	private static final Logger logger = Logger.getLogger(LobbyGame.class);

	/**
	 * Generates the game list using database data and adds the packet to queue
	 */
	public static void getGameList(IoSession session, Packet pktIn,
			Queue<Packet> queue, Connection con, int command)
			throws MGOException {
		CharsetEncoder ce = Charset.forName("ISO-8859-1").newEncoder();

		IoBuffer iob = null;
		byte[] payload = new byte[0];
		try {
			// Query all games for the info we need
			PreparedStatement stmt = con
					.prepareStatement("SELECT games.id, name, password, rule, map, players, max_players, vs_rating, vs_restriction_type, weapon_restriction, bitmask_options_one, bitmask_options_two, host_only, ping, fl.game as friend FROM games LEFT JOIN players fl ON fl.game=games.id AND fl.user_id IN (SELECT user_id from lists WHERE lists.owner_id=? and type=0) WHERE games.lobby=?");
			stmt.setInt(1, (Integer)session.getAttribute("userid"));
			stmt.setInt(2, Globals.lobbyId);
			ResultSet rs = stmt.executeQuery();
			int rows = DBPool.getRowCount(rs);
			iob = IoBuffer.allocate(45 * rows, false);

			while (rs.next()) {
				long id = rs.getInt("id");
				String name = rs.getString("name");
				String password = rs.getString("password");
				short rule = rs.getShort("rule");
				short map = rs.getShort("map");
				short players = rs.getShort("players");
				short maxPlayers = rs.getShort("max_players");
				long vsRating = rs.getLong("vs_rating");
				short vsRestrictionType = rs.getShort("vs_restriction_type");
				short weaponRestriction = rs.getShort("weapon_restriction");
				short bitOptionsOne = rs.getShort("bitmask_options_one");
				short bitOptionsTwo = rs.getShort("bitmask_options_two");
				short hostOnly = rs.getShort("host_only");
				long ping = rs.getLong("ping");
				int friend = rs.getInt("friend");

				byte[] unk = new byte[6];

				short locked = (password == "") ? (short) 0 : (short) 1;
				short flbl = (short) 0;
				if(friend > 0) flbl = (short)1;

				try {
					iob.putUnsignedInt(id).putString(name, 16, ce)
							.putUnsigned(locked).putUnsigned(hostOnly)
							.putUnsigned(rule).putUnsigned(map)
							.putUnsigned(weaponRestriction)
							.putUnsigned(maxPlayers).putUnsigned(bitOptionsOne)
							.putUnsigned(bitOptionsTwo).putUnsigned(players)
							.putUnsignedInt(ping).putUnsigned(flbl)
							.putUnsigned(vsRestrictionType)
							.putUnsignedInt(vsRating).put(unk);
				} catch (Exception e) {
					iob.free();
					throw new MGOException(command, 2, e);
				}

			}

			payload = iob.array();
		} catch (SQLException e) {
			throw new MGOException(command, 1, e);
		} finally {
			if (iob != null) {
				iob.free();
			}
		}

		// Add the packet to the queue
		queue.add(new Packet(command, payload));
	}

	/**
	 * Generates the game info using database data and adds the packet to queue
	 */
	public static void getGameInfo(IoSession session, Packet pktIn,
			Queue<Packet> queue, Connection con) throws MGOException {
		CharsetEncoder ce = Charset.forName("ISO-8859-1").newEncoder();

		IoBuffer iob = IoBuffer.wrap(pktIn.payload);
		long gameid = iob.getUnsignedInt();
		iob.free();

		byte[] payload = new byte[0];
		try {
			// Get the Game Info
			PreparedStatement stmt = con
					.prepareStatement("SELECT name, password, description, rule, map, players, max_players, vs_rating, vs_restriction_type, weapon_restriction, bitmask_options_one, bitmask_options_two, host_only, ping, misc_data FROM games WHERE id=? AND lobby=?");
			stmt.setLong(1, gameid);
			stmt.setInt(2, Globals.lobbyId);
			ResultSet rsg = stmt.executeQuery();
			try {
				rsg.first();
			} catch (Exception e) {
				throw new MGOException(0x4313, 5, "Could not find requested game.");
			}

			// Set our Game Info variables
			String name = rsg.getString("name");
			String password = rsg.getString("password");
			String description = rsg.getString("description");
			short rule = rsg.getShort("rule");
			short map = rsg.getShort("map");
			// short players = rsg.getShort("players");
			short maxPlayers = rsg.getShort("max_players");
			long vsRating = rsg.getLong("vs_rating");
			short vsRestrictionType = rsg.getShort("vs_restriction_type");
			short weaponRestriction = rsg.getShort("weapon_restriction");
			short bitOptionsOne = rsg.getShort("bitmask_options_one");
			short bitOptionsTwo = rsg.getShort("bitmask_options_two");
			short hostOnly = rsg.getShort("host_only");
			String encMisc = rsg.getString("misc_data");

			// Decrypt Misc data
			byte[] misc = Base64.decodeBase64(encMisc.getBytes());

			// Get the Player Info for this game
			stmt = con
					.prepareStatement("SELECT p.user_id, p.game_team,p.game_kills,p.game_deaths,p.game_score,p.game_seconds, p.game_ping, u.displayname FROM players p LEFT JOIN users u on u.id=p.user_id WHERE p.game=?");
			stmt.setLong(1, gameid);
			ResultSet rsp = stmt.executeQuery();
			short players = (short) DBPool.getRowCount(rsp);

			iob = IoBuffer.allocate(276 + (41 * players), false);

			try {
				iob.putUnsignedInt(0).putUnsignedInt(gameid)
						.putString(name, 16, ce)
						.putString(description, 128, ce).skip(2);
			} catch (CharacterCodingException ex) {
				throw new MGOException(0x4313, 2, ex);
			}

			iob.put(misc, 0, 30).skip(2).put(misc, 30, 2)
					.putUnsigned(weaponRestriction).putUnsigned(maxPlayers)
					.putUnsigned(players).skip(22)
					.putUnsigned(vsRestrictionType).putUnsignedInt(vsRating)
					.put(misc, 32, 40).skip(7).putUnsigned(bitOptionsOne)
					.putUnsigned(bitOptionsTwo).put(misc, 72, 5)
					.putUnsignedInt(80L);

			while (rsp.next()) {
				long userid = rsp.getLong("user_id");
				String displayName = rsp.getString("displayname");
				byte team = rsp.getByte("game_team");
				long kills = rsp.getLong("game_kills");
				long deaths = rsp.getLong("game_deaths");
				long score = rsp.getLong("game_score");
				long seconds = rsp.getLong("game_seconds");
				long ping = rsp.getLong("game_ping");

				try {
					iob.putUnsignedInt(userid).putString(displayName, 16, ce);
					iob.put(team).putUnsignedInt(kills);
					iob.putUnsignedInt(deaths).putUnsignedInt(score);
					iob.putUnsignedInt(seconds);
					iob.putUnsignedInt(ping);
				} catch (CharacterCodingException ex) {
					throw new MGOException(0x4313, 3, ex);
				}

			}

			payload = iob.array();
		} catch (SQLException e) {
			throw new MGOException(0x4313, 1, e);
		} finally {
			if (iob != null) {
				iob.free();
			}
		}

		// Add the packet to the queue
		queue.add(new Packet(0x4313, payload));
	}

	/**
	 * Handles creating a new game on the server
	 */
	public static void handleCreateGame(IoSession session, Packet pktIn,
			Queue<Packet> queue, Connection con) throws MGOException {
		// Just a quick check to make sure this is of sufficent size
		if (pktIn.payload.length < 260)
			throw new MGOException(0x4311, 1,
					"Invalid Create Game payload size.");

		int userid = (Integer) session.getAttribute("userid", -1);
		if (userid == -1) {
			throw new MGOException(0x4311, 15, "No User ID in IoSession.");
		}

		// Some initalization
		CharsetDecoder d = Charset.forName("ISO-8859-1").newDecoder();
		IoBuffer iob = IoBuffer.wrap(pktIn.payload);
		// Parse the payload
		String[] gameInfo = new String[3];
		boolean hostOnly = false;
		byte[] rules = new byte[15];
		byte[] maps = new byte[15];
		byte[] teams = new byte[2];
		short weapons = 0;
		short maxPlayers = 0;
		short vsRatingRestr = 0;
		long vsRating = 0L;
		byte[] ruleSettings = new byte[40];
		short[] bitOptions = new short[2];
		byte autoBalance = 0;
		int idleKickTime = 0;
		int teamKillKick = 0;

		// Parse Game Information
		try {
			gameInfo[0] = iob.getString(16, d); // Game name
			iob.position(16);
			gameInfo[2] = iob.getString(76, d); // Game description
			if (gameInfo[2].length() == 0)
				gameInfo[2] = "Foxhound";
			// Game Password if any
			iob.position(144);
			short p = iob.getUnsigned();
			if (p == 1)
				gameInfo[1] = iob.getString(16, d);
			else
				gameInfo[1] = "";
		} catch (CharacterCodingException e) {
			throw new MGOException(0x4311, 10, e);
		}

		// Parse host only flag
		iob.position(161);
		if (iob.get() == 1)
			hostOnly = true;

		// Parse match rule/map pairings
		iob.position(162);
		for (int i = 0; i < 15; i++) {
			rules[i] = iob.get();
			maps[i] = iob.get();
			// Check that Pairing is valid
			if (rules[i] > 4 || maps[i] > 0x0C) {
				throw new MGOException(0x4311, 2, "Invalid map or rule choice");
			}
			if (rules[i] == 4
					&& (maps[i] == 4 || maps[i] == 5 || maps[i] == 6
							|| maps[i] == 10 || maps[i] == 11)) {
				throw new MGOException(0x4311, 3,
						"Invalid map choice for sneaking game mode.");
			}
		}
		// Parse Uniform choices
		iob.position(194);
		teams[0] = iob.get();
		teams[1] = iob.get();
		if (teams[0] > 2 || teams[1] > 2) {
			throw new MGOException(0x4311, 4, "Invalid team uniform choice");
		}

		// Parse Weapon Restrictions
		weapons = iob.getUnsigned();
		if (weapons > 7) {
			throw new MGOException(0x4311, 5, "Invalid weapon restriction");
		}

		// Parse Max Players
		maxPlayers = iob.getUnsigned();
		if ((!hostOnly && maxPlayers > 8) || (hostOnly && maxPlayers > 9)) {
			throw new MGOException(0x4311, 5, "Max players too high.");
		}

		// Parse VS Rating Restriction Type
		iob.position(210);
		vsRatingRestr = iob.getUnsigned();
		if (vsRatingRestr > 2) {
			throw new MGOException(0x4311, 5,
					"Invalid VS Rating restriction type");
		}

		// Parse VS Rating standard
		vsRating = iob.getUnsignedInt();
		if (vsRating > 9900 || vsRating < 100) {
			throw new MGOException(0x4311, 5, "Invalid VS Rating standard.");
		}

		// Grab Rule settings not parsing
		iob.get(ruleSettings, 0, 40);

		// Grab last bits of information
		bitOptions[0] = iob.getUnsigned();
		bitOptions[1] = iob.getUnsigned();
		autoBalance = iob.get();
		idleKickTime = iob.getUnsignedShort();
		teamKillKick = iob.getUnsignedShort();
		iob.free();

		// Create our "misc" data block
		IoBuffer iob2 = IoBuffer.allocate(77, false);
		for (int i = 0; i < 15; i++) {
			iob2.put(rules[i]);
			iob2.put(maps[i]);
		}
		iob2.put(teams).put(ruleSettings).put(autoBalance)
				.putUnsignedShort(idleKickTime).putUnsignedShort(teamKillKick);
		byte[] miscBytes = iob2.array();
		iob2.free();
		String encMisc = new String(Base64.encodeBase64(miscBytes));

		// Save these create game settings for the future
		String encGameSettings = new String(Base64.encodeBase64(pktIn.payload));

		// Insert new game into database
		PreparedStatement stmt = null;
		ResultSet rs = null;
		int gameid = 0;
		try {
			stmt = con
					.prepareStatement(
							"INSERT INTO `games` (`lobby`, `name`, `password`, `description`, `rule`, `map`, `players`, `max_players`, `vs_rating`, `vs_restriction_type`, `weapon_restriction`, `bitmask_options_one`, `bitmask_options_two`, `host_only`, `ping`, `misc_data`,`host_id`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?);",
							Statement.RETURN_GENERATED_KEYS);
			stmt.setInt(1, Globals.lobbyId); // Lobby Id
			stmt.setString(2, gameInfo[0]); // Game Title
			stmt.setString(3, gameInfo[1]); // Game Password
			stmt.setString(4, gameInfo[2]); // Game Description
			stmt.setShort(5, rules[0]); // Current Rule
			stmt.setShort(6, maps[0]); // Current Map
			stmt.setShort(7, (short) 1); // Player Count
			stmt.setShort(8, maxPlayers); // Max Players
			stmt.setLong(9, vsRating); // Vs Rating
			stmt.setShort(10, vsRatingRestr); // Vs Rating Type
			stmt.setShort(11, weapons); // Weapon restrictions
			stmt.setShort(12, bitOptions[0]); // Bitfield options 1
			stmt.setShort(13, bitOptions[1]); // Bitfield options 2
			stmt.setShort(14, (short) (hostOnly ? 1 : 0)); // Vs Rating Type
			stmt.setLong(15, 0); // Ping
			stmt.setString(16, encMisc); // Misc data block, base64 encrypted
			stmt.setInt(17,userid);//Host id
			stmt.executeUpdate();

			rs = stmt.getGeneratedKeys();
			if (rs.next()) {
				gameid = rs.getInt(1);
			} else {
				throw new SQLException(
						"Game was not inserted, not new id generated.");
			}
			//Set rule so it doesn't need to be queried for
			session.setAttribute("host_rule", rules[0]);

		} catch (SQLException e) {
			throw new MGOException(0x4311, 11, e);
		}

		try {
			stmt = con
					.prepareStatement("UPDATE users SET game_settings=? WHERE id=?");
			stmt.setString(1, encGameSettings);
			stmt.setInt(2, userid);
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new MGOException(0x4311, 14, e);
		}

		try {
			stmt = con
					.prepareStatement("INSERT INTO `players` (`id`,`user_id`,`lobby`,`game`,`game_team`,`game_kills`,`game_deaths`,`game_score`,`game_seconds`,`game_ping`) VALUES(NULL,?,?,?,?,?,?,?,?,?)");
			stmt.setInt(1, userid);
			stmt.setInt(2, Globals.lobbyId); // Lobby Id
			stmt.setInt(3, gameid); // game Id
			stmt.setInt(4, 0); // team
			stmt.setInt(5, 0); // deaths
			stmt.setInt(6, 0); // kills
			stmt.setInt(7, 0); // score
			stmt.setInt(8, 0); // seconds
			stmt.setInt(9, 0); // ping
			stmt.executeUpdate();
		} catch (SQLException e) {
			try {
				// Cleanup from failed game creation
				stmt = con.prepareStatement("DELETE FROM games WHERE id=?");
				stmt.setInt(1, gameid);
				stmt.executeUpdate();
			} catch (SQLException ee) {
				throw new MGOException(0x4311, 12, e);
			}
			throw new MGOException(0x4311, 13, e);
		}

		queue.add(new Packet(0x4311));
		IoSessionConfig ic= session.getConfig();
		ic.setIdleTime(IdleStatus.READER_IDLE, 7200); //2Hour for hosts only
		Globals.sendEvent("notice",((String)session.getAttribute("displayname"))+" just hosted a game.");
	}

	/**
	 * Gets remote/locate connection information of the host
	 */
	public static void getHostInformation(IoSession session, Packet pktIn,
			Queue<Packet> queue, Connection con) throws MGOException {

		PreparedStatement stmt = null;
		ResultSet rs = null;
		String[] ips = new String[2];
		short[] ports = new short[2];

		// Get Host id
		IoBuffer iob = IoBuffer.wrap(pktIn.payload);
		long gameid = iob.getUnsignedInt();

		// Get Password if supplied
		if (pktIn.payload.length > 4) {
			String password;
			try {
				CharsetDecoder d = Charset.forName("ISO-8859-1").newDecoder();
				password = iob.getString(16, d);
				// Query to check if password is correct
				stmt = con
						.prepareStatement("SELECT id FROM games WHERE id=? AND lobby=? AND password=?");
				stmt.setLong(1, gameid);
				stmt.setInt(2, Globals.lobbyId);
				stmt.setString(3, password);
				rs = stmt.executeQuery();
				if (DBPool.getRowCount(rs) != 1) {
					throw new MGOException(0x4321, 1, "Wrong game password.");
				}
			} catch (CharacterCodingException e) {
				throw new MGOException(0x4321, 15, e);
			} catch (SQLException e) {
				throw new MGOException(0x4321, 16, e);
			}
		}

		iob.free();

		try {
			// Get Connection information
			stmt = con
					.prepareStatement("SELECT c.remote,c.local,c.remote_port,c.local_port FROM players INNER JOIN games ON games.players<games.max_players AND games.lobby=? INNER JOIN connections c ON c.user_id=players.user_id WHERE players.game=? ORDER BY players.id ASC LIMIT 1");
			stmt.setInt(1, Globals.lobbyId);
			stmt.setLong(2, gameid);
			rs = stmt.executeQuery();
			rs.first();
			// Setup vars
			ips[0] = rs.getString(1);
			ips[1] = rs.getString(2);
			ports[0] = rs.getShort("c.remote_port");
			ports[1] = rs.getShort("c.local_port");
		} catch (SQLException e) {
			throw new MGOException(0x4321, 10, e);
		}

		// Prepare payload
		CharsetEncoder ce = Charset.forName("ISO-8859-1").newEncoder();
		iob.free();
		iob = IoBuffer.allocate(40);
		try {
			iob.putUnsignedInt(0);
			iob.putString(ips[0], 16, ce);
			iob.putUnsignedShort(ports[0]);
			iob.putString(ips[1], 16, ce);
			iob.putUnsignedShort(ports[1]);
		} catch (CharacterCodingException e) {
			throw new MGOException(0x4321, 11, e);
		}
		queue.add(new Packet(0x4321, iob.array()));
		iob.free();
	}

	/**
	 * Gets the past create game settings
	 */
	public static void getPastGameSettings(IoSession session, Packet pktIn,
			Queue<Packet> queue, Connection con) throws MGOException {
		int userid = (Integer) session.getAttribute("userid", -1);
		if (userid == -1) {
			throw new MGOException(0x4311, 1, "No User ID in IoSession.");
		}

		String encGameSettings = null;
		PreparedStatement stmt = null;
		try {
			// Prepare the query and set the settings
			stmt = con
					.prepareStatement("SELECT `game_settings` FROM `users` WHERE `id`=? LIMIT 1");
			stmt.setInt(1, userid);
			ResultSet rs = stmt.executeQuery();

			if (rs.next()) {
				encGameSettings = rs.getString("game_settings");
			}
		} catch (SQLException e) {
			throw new MGOException(0x4311, 14, e);
		}

		// Decode the settings
		byte[] gameSettings = (encGameSettings != null) ? Base64
				.decodeBase64(encGameSettings.getBytes()) : new byte[0];

		// Create the payload
		IoBuffer iob = IoBuffer.allocate(266, false);
		iob.putUnsignedInt(0L).put(gameSettings);
		byte[] payload = iob.array();
		iob.free();
		
		// Send it off
		queue.add(new Packet(0x4305, payload));
	}

}
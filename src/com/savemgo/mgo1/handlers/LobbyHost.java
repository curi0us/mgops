package com.savemgo.mgo1.handlers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Queue;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.util.Base64;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSessionConfig;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;

import com.savemgo.mgo1.MGOException;
import com.savemgo.mgo1.Packet;
import com.savemgo.mgo1.Globals;

public class LobbyHost {
	/**
	 * Handles a players attempt to join a game
	 */
	public static void handleJoinAttempt(IoSession session, Packet pktIn, Queue<Packet> queue, Connection con) throws MGOException {
		IoBuffer iob = null;
		//Get Userid
		iob = IoBuffer.wrap(pktIn.payload);
		long uid = iob.getUnsignedInt();
		iob.free();

		ResultSet rs = null;
		PreparedStatement stmt = null;
		try {
			stmt = con.prepareStatement("SELECT game FROM players WHERE user_id=?");
			stmt.setInt(1,(Integer) session.getAttribute("userid"));
			rs = stmt.executeQuery();
			rs.first();
			int gameid = rs.getInt("game");
	
			stmt = con.prepareStatement("INSERT INTO `players` (`id`,`user_id`,`lobby`,`game`,`game_team`,`game_kills`,`game_deaths`,`game_score`,`game_seconds`,`game_ping`) VALUES(NULL,?,?,?,?,?,?,?,?,?)");
			stmt.setLong(1, uid); //PLayer who is joining
			stmt.setInt(2, Globals.lobbyId); // Lobby Id
			stmt.setInt(3, gameid); // game Id
			stmt.setInt(4, 0); // team
			stmt.setInt(5, 0); // deaths
			stmt.setInt(6, 0); // kills
			stmt.setInt(7, 0); // score
			stmt.setInt(8, 0); // seconds
			stmt.setInt(9, 0); // ping
			stmt.executeUpdate();
			
			stmt = con.prepareStatement("UPDATE games SET players=players+1 WHERE id=?");
			stmt.setInt(1,gameid);
			stmt.executeUpdate();

			//Query username for use in notice
			stmt = con.prepareStatement("SELECT displayname FROM users WHERE id=?");
			stmt.setLong(1,uid);
			rs = stmt.executeQuery();
			rs.first();
			Globals.sendEvent("notice",rs.getString("displayname")+" just joined a game.");
		} catch(SQLException e) {
			//Fail Silently, this bans users if they fail here.
		}

		//Create payload
		iob = IoBuffer.allocate(8);
		iob.putUnsignedInt(0);
		iob.putUnsignedInt(uid);
		queue.add(new Packet(0x4341,iob.array()));
		iob.free();

	}
	/**
	 * Handles a players attempt to join a team 
	 */
	public static void handleTeamJoin(IoSession session, Packet pktIn, Queue<Packet> queue, Connection con) throws MGOException {
		IoBuffer iob = null;
		//Get Userid
		iob = IoBuffer.wrap(pktIn.payload);
		long uid = iob.getUnsignedInt();
		byte team = iob.get();
		iob.free();
		
		//Update the players table with the players team
		ResultSet rs = null;
		PreparedStatement stmt = null;
		try {
			stmt = con.prepareStatement("UPDATE players SET game_team=? WHERE user_id=?");
			stmt.setInt(1,team);
			stmt.setLong(2,uid);
			stmt.executeUpdate();
		} catch(SQLException e) {
			//Fail silently, failing here causes problems quitting
		}

		//Create payload
		iob = IoBuffer.allocate(8);
		iob.putUnsignedInt(0);
		iob.putUnsignedInt(uid);
		queue.add(new Packet(0x4345,iob.array()));
		iob.free();

	}
	/**
	 * Handles a players attempt to quit the game
	 */
	public static void handlePlayerQuit(IoSession session, Packet pktIn, Queue<Packet> queue, Connection con) throws MGOException {
		IoBuffer iob = null;
		//Get Userid
		iob = IoBuffer.wrap(pktIn.payload);
		long uid = iob.getUnsignedInt();
		iob.free();

		//Update database
		ResultSet rs = null;
		PreparedStatement stmt = null;
		try {
			//Update player count
			stmt = con.prepareStatement("UPDATE games SET players=players-1 WHERE games.id=(SELECT game FROM players WHERE user_id=?)");
			stmt.setLong(1,uid);
			stmt.executeUpdate();

			//Delete player from list
			stmt = con.prepareStatement("DELETE FROM players WHERE user_id=?");
			stmt.setLong(1,uid);
			stmt.executeUpdate();
		} catch(SQLException e) {
			//Fail silently, failing here causes problems
		}


		//Create payload
		iob = IoBuffer.allocate(8);
		iob.putUnsignedInt(0);
		iob.putUnsignedInt(uid);
		queue.add(new Packet(0x4343,iob.array()));
		iob.free();

	}

	/**
	 * Handles host's attempt to quit game
	 */
	public static void handleHostQuit(IoSession session, Packet pktIn, Queue<Packet> queue, Connection con) throws MGOException {
		int uid = (Integer) session.getAttribute("userid");

		//Update database
		ResultSet rs = null;
		PreparedStatement stmt = null;
		try {
			//Delete game, due to table relationship with players; players will be deleted automatically
			stmt = con.prepareStatement("DELETE FROM games WHERE host_id=?");
			stmt.setInt(1,uid);
			stmt.executeUpdate();
		} catch(SQLException e) {
			//Fail silently, failing here causes problems
		}
		//Set idle time
		IoSessionConfig ic= session.getConfig();
		ic.setIdleTime(IdleStatus.READER_IDLE, 900); //10Minutes
		//Send reply
		queue.add(new Packet(0x4381,new byte[4]));

	}
	
	/**
	 * Handles player ping information
	 */
	public static void handlePingInformation(IoSession session, Packet pktIn, Queue<Packet> queue, Connection con) throws MGOException {
		IoBuffer iob = null;
		iob = IoBuffer.wrap(pktIn.payload);
		long gamePing = iob.getUnsignedInt();
		//Arrays for ID/Ping information
		long[] ids   = new long[9];
		long[] pings = new long[9];
		int count   = 0;
		
		//Loop until we've read all the information
		while(iob.hasRemaining()&&count<9) {
			ids[count] = iob.getUnsignedInt();
			pings[count] = iob.getUnsignedInt();
			count++;
		}
		
		//Update the database
		ResultSet rs = null;
		PreparedStatement stmt = null;
		try {
			//Update player pings
			for(int i=0;i<count;i++) {
				stmt = con.prepareStatement("UPDATE players SET game_ping=? WHERE user_id=?");
				stmt.setLong(1,pings[i]);
				stmt.setLong(2,ids[i]);
				stmt.executeUpdate();
			}
			//Update game ping
			stmt = con.prepareStatement("UPDATE games SET ping=? WHERE host_id=?");
			stmt.setLong(1, gamePing);
			stmt.setInt(2,(Integer)session.getAttribute("userid"));
			stmt.executeUpdate();
		} catch(SQLException e) {
			//Fail silent
		}

		//Send reply
		queue.add(new Packet(0x4399,new byte[0]));
	}
	
	/**
	 * Game is going to a new round, update map/rule
	 */
	public static void handleNewRound(IoSession session, Packet pktIn, Queue<Packet> queue, Connection con) throws MGOException {
		int uid = (Integer) session.getAttribute("userid");
		//Get round index
		IoBuffer iob = IoBuffer.wrap(pktIn.payload);
		byte newRound = iob.get();
		iob.free();

		//Update database
		ResultSet rs = null;
		PreparedStatement stmt = null;
		try {
			//Get Game id
			stmt = con.prepareStatement("SELECT id,misc_data FROM games WHERE host_id=?");
			stmt.setInt(1,uid);
			rs = stmt.executeQuery();
			rs.first();
			int gameid = rs.getInt("id");
			
			byte[] misc = Base64.decodeBase64(rs.getString("misc_data").getBytes());

			//Delete game, due to table relationship with players; players will be deleted automatically
			stmt = con.prepareStatement("UPDATE games SET map=?, rule=? WHERE id=?");
			stmt.setInt(1,misc[(newRound*2)+1]);
			stmt.setInt(2,misc[(newRound*2)]);
			stmt.setInt(3,gameid);
			stmt.executeUpdate();
			//Store rule in host's session so we don't have to query it at end of round
			session.setAttribute("host_rule", misc[(newRound*2)]);
		} catch(SQLException e) {
			//Fail silently, game doesn't care
		}

		//Send reply
		queue.add(new Packet(0x4393,new byte[4]));

	}

	/**
	 * Handles host kicking a user
	 */
	public static void handleKick(IoSession session, Packet pktIn, Queue<Packet> queue, Connection con) throws MGOException {
		int uid = (Integer) session.getAttribute("userid");
		IoBuffer iob = IoBuffer.wrap(pktIn.payload);
		long pid = iob.getUnsignedInt();
		iob.free();

		try {
			PreparedStatement stmt = con.prepareStatement("DELETE FROM players WHERE user_id=? AND game=(SELECT id FROM games WHERE host_id=?)");
			stmt.setInt(1,uid);
			stmt.setLong(2,pid);
			stmt.executeUpdate();
		} catch(SQLException e) {
			//Fail silent
		}

		iob = IoBuffer.allocate(8);
		iob.putInt(0);
		iob.putUnsignedInt(pid);
		//Send reply
		queue.add(new Packet(0x4347,iob.array()));
		iob.free();

	}

	/**
	 * Handles packet containing player stats
	 */
	public static void handleStats(IoSession session, Packet pktIn, Queue<Packet> queue, Connection con) throws MGOException {
		IoBuffer iob = null;
		ResultSet rs = null;
		PreparedStatement stmt = null;
		long host_id = (Integer)session.getAttribute("userid");
		byte host_rule = (Byte)session.getAttribute("host_rule");

		iob = IoBuffer.wrap(pktIn.payload);
		//Parse Stats into array
		long[] stats = new long[30];
		for(int i=0;i<30;i++) {
			stats[i] = iob.getUnsignedInt();
		}
		int[] streaks = new int[2];
		iob.position(12);
		streaks[0] = iob.getUnsignedShort(); //Kill Streak
		streaks[1] = iob.getUnsignedShort(); //Death streak

		try {

			String query = "UPDATE stats SET points=points+?, kills=kills+?, deaths=deaths+?, kill_streak=GREATEST(kill_streak,?), death_streak=GREATEST(death_streak,?),"
			              +" stuns=stuns+?, stuns_received=stuns_received+?, teammate_kills=teammate_kills+?, teammate_stuns=teammate_stuns+?,"
			              +" radio_uses=radio_uses+?, text_chat_uses=text_chat_uses+?, cqc_attacks=cqc_attacks+?,cqc_attacks_received=cqc_attacks_received+?,"
			              +" head_shots=head_shots+?, head_shots_received=head_shots_received+?, kills_with_scorpion=kills_with_scorpion+?,kills_with_knife=kills_with_knife+?,"
			              +" times_eaten=times_eaten+?, rolls=rolls+?, infrared_goggle_uses=infrared_goggle_uses+?,play_time=play_time+?,"
			              +" rounds_played=rounds_played+?, rounds_played_without_dying=rounds_played_without_dying+?,team_wins=team_wins+? ";
			switch(host_rule) {
				case 2://Rescue mode
					query += " ,gakos_rescued=gakos_rescued+?";
					break;	
				case 3://Capture
					query += " ,kerotans_placed_for_win=kerotans_placed_for_win+?, kerotans_placed=kerotans_placed+?";
					break;
				case 4://Sneaker
					query += " ,snake_frags=snake_frags+?,goals_reached_as_snake=goals_reached_as_snake+?";
					break;
			}
			query += " WHERE user_id=? AND type_id=?";

			stmt = con.prepareStatement(query);
			int qIndex=1;
			//Set values
			if((stats[7] >= 0x00FFFFFF)) stats[7] = 1; //Has happened one in SNE, not sure why
			stmt.setLong(qIndex++, stats[7]);   //Points, score?
			stmt.setLong(qIndex++, stats[1]);   //Kills
			stmt.setLong(qIndex++, stats[2]);   //Deaths
			stmt.setLong(qIndex++, streaks[0]); //Kill_streak
			stmt.setLong(qIndex++, streaks[1]); //Death_streak
			stmt.setLong(qIndex++, stats[4]);   //Stuns
			stmt.setLong(qIndex++, stats[5]);   //Stuns received
			stmt.setLong(qIndex++, stats[8]);   //Team Kills //9
			stmt.setLong(qIndex++, stats[9]);  //Team Stuns //10
			stmt.setLong(qIndex++, stats[16]);  //Radio uses
			stmt.setLong(qIndex++, stats[17]);  //Text Chat uses
			stmt.setLong(qIndex++, stats[18]);  //cqc_attacks
			stmt.setLong(qIndex++, stats[19]);  //cqc_attacks_received
			stmt.setLong(qIndex++, stats[20]);  //head_shots
			stmt.setLong(qIndex++, stats[21]);  //head_shots_received
			stmt.setLong(qIndex++, stats[23]);  //Kills with SCO
			stmt.setLong(qIndex++, stats[24]);  //Kills with Knife
			stmt.setLong(qIndex++, stats[25]);  //Times eaten
			stmt.setLong(qIndex++, stats[26]);  //Rolls
			stmt.setLong(qIndex++, stats[27]);  //NVG Usage
			stmt.setLong(qIndex++, stats[28]);  //Play Time
			stmt.setLong(qIndex++, stats[12]);  //Rounds played
			stmt.setLong(qIndex++, stats[13]);  //Rounds played without dying
			stmt.setLong(qIndex++, stats[22]);  //Team wins

			switch(host_rule) {
				case 2://Rescue mode
					stmt.setLong(qIndex++, stats[14]);   //Gakos Rescued
					break;	
				case 3://Capture
					stmt.setLong(qIndex++, stats[14]);   //Kero placed for win
					stmt.setLong(qIndex++, stats[15]);   //Keros places
					break;
				case 4://Sneaking
					stmt.setLong(qIndex++, stats[6]);   //Snake Frags
					stmt.setLong(qIndex++, stats[15]);   //Goals as Snake
					break;
			}

			//Where clause
			stmt.setLong(qIndex++, stats[0]);//Player id
			stmt.setByte(qIndex++, host_rule);//Game mode
			stmt.executeUpdate();

			//Update Players table
			stmt = con.prepareStatement("UPDATE players SET game_kills=game_kills+?,game_deaths=game_deaths+?,game_score=game_score+?, game_seconds=game_seconds+? WHERE user_id=?");
			stmt.setLong(1,stats[1]);//kills
			stmt.setLong(2,stats[2]);//deaths
			stmt.setLong(3,stats[7]);//score
			stmt.setLong(4,stats[28]);//seconds
			stmt.setLong(5,stats[0]);//Player id
			stmt.executeUpdate();

		} catch(SQLException e) {
			throw new MGOException(0x4391, 1,e);
		}

		queue.add(new Packet(0x4391,new byte[4]));


	}


}
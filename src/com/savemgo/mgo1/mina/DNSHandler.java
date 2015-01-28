package com.savemgo.mgo1.mina;

import java.util.Queue;
import java.util.LinkedList;
import java.sql.Connection;

import org.apache.log4j.Logger;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.core.buffer.IoBuffer;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;

import com.savemgo.mgo1.PacketHandler;
import com.savemgo.mgo1.Packet;
import com.savemgo.mgo1.DBPool;
import com.savemgo.mgo1.handlers.MainLobby;

public class DNSHandler extends IoHandlerAdapter {

	private static Logger logger = Logger.getLogger(ServerHandler.class);
	private static long[] ips = new long[4];
	public DNSHandler(String http,String stun, String auth, String mgo2) {
		//Transform IP strings to longs
		String[] tok = http.split("\\.");
		if(tok.length!=4) {
			logger.error(String.format("HTTP IP is not valid IPv4 -- %s",http));
		}
		ips[0] = Long.parseLong(String.format("%02x%02x%02x%02x", Integer.parseInt(tok[0]),Integer.parseInt(tok[1]),Integer.parseInt(tok[2]),Integer.parseInt(tok[3])),16);

		tok = stun.split("\\.");
		if(tok.length!=4) {
			logger.error(String.format("STUN IP is not valid IPv4 -- %s",stun));
		}
		ips[1] = Long.parseLong(String.format("%02x%02x%02x%02x", Integer.parseInt(tok[0]),Integer.parseInt(tok[1]),Integer.parseInt(tok[2]),Integer.parseInt(tok[3])),16);

		tok = auth.split("\\.");
		if(tok.length!=4) {
			logger.error(String.format("AUTH IP is not valid IPv4 -- %s",auth));
		}
		ips[2] = Long.parseLong(String.format("%02x%02x%02x%02x", Integer.parseInt(tok[0]),Integer.parseInt(tok[1]),Integer.parseInt(tok[2]),Integer.parseInt(tok[3])),16);

	}
	public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
		logger.error("Exception caught", cause);
	}
	
	public void messageReceived(IoSession session, Object message) throws Exception {
		IoBuffer iob = (IoBuffer) message;
		short id = iob.getShort(); //DNS Query ID
		short flags = iob.getShort(); //DNS Flags

		//If message is a response we don't care about it
		if(((int)flags & ((int)0x8000)) != 0x0000) {
			//logger.error("Recieved unsolicited DNS response.");
			return;
		}
		//If opcode is not 0(standard query) we will not handle it
		if(((int)flags & ((int)0x7800)) != 0x0000) {
			//logger.error("Invalid DNS opcode.");
			return;
		}
		short qCount = iob.getShort(); //Number of questions
		if(qCount != 1) {
			//logger.error("Too many questions in DNS, only support one at a time.");
			return;
		}
		iob.position(12); //Jump to end of DNS header

		//Start preparing response also
		IoBuffer out = IoBuffer.allocate(8);
		out.setAutoExpand(true);
		out.rewind();
		out.putShort(id);
		out.putShort((short)0x8180); //Flags
		out.putShort((short)0x0001); //One Question
		out.putShort((short)0x0001); //One Answer
		out.putShort((short)0x0000); //No recods
		out.putShort((short)0x0000); //No additional records

		StringBuilder sb = new StringBuilder("");
		byte count = iob.get();
		out.put(count);
		while(count != 0) {				
			for(int i=0;i<count;i++) {
				byte c = iob.get();
				sb.append(String.format("%c",(char)c));
				out.put(c);
			}
			count = iob.get();
			out.put(count);
			if(count != 0) sb.append(".");
		}
		short itype = iob.getShort();
		short itype2= iob.getShort();
		if(itype != 1 && itype != 28 && itype != 38) {
			//logger.error(String.format("Non-A type DNS query received. %d -> %s",itype, sb));
			return;
		}
		out.putShort(itype);
		out.putShort(itype2);
		out.putShort((short)0xc00c);
		out.putShort((short)0x0001);
		out.putShort((short)0x0001);
		logger.info(String.format("Query for %s", sb));

		out.putInt(0x00000060);//TTL
		out.putShort((short)0x0004); //4bytes lone

/*
   $serverip = '192.3.157.163';
    if(strpos($d,'savemgo')!==false||
       strpos($d,'web')!==false) return '64.111.125.108';
    if( strpos($d,'mgo')!==false||
        strpos($d,'mgs')!==false||
        strpos($d,'konami')!==false||
        strpos($d,'sony')!==false||
        strpos($d,'playstation')!==false) {
	echo "\t$serverip";
        return $serverip;
    } 
    */
    	String sDom = sb.toString().toLowerCase();
    	if(sDom.indexOf("savemgo") >= 0 || sDom.indexOf("mgs3sweb") >= 0) {
    		//Requesting web address (savemgo.com and mgs3sweb.konamionline.com)
    		out.putInt((int)ips[0]);
    	} else if(sDom.indexOf("stun") >= 0) {
    		//Request STUN server address mgs3sstun.konamionline.com mgo2stun*.konamionline.com
    		out.putInt((int)ips[1]);
    	} else if(sDom.indexOf("mgo") >= 0 || sDom.indexOf("mgs") >= 0 || sDom.indexOf("konami") >= 0 || 
			sDom.indexOf("sony") >= 0 || sDom.indexOf("playstation") >= 0 ||
			sDom.indexOf("mgo2web") >= 0 || sDom.indexOf("mgo2auth") >= 0 || sDom.indexOf("info.service") >= 0) {
    			// MGO2 web-related stuff and...
	    		//Requesting anything else possible related to MGO, includes DNAS, Gate servers, konami web stuff.
	    		//Give AUTH server
	    		out.putInt((int)ips[2]);
    	} else if (sDom.indexOf("mgo2gate") >= 0) {
    		//Request MGO2-related server address mgo2gate*.konamionline.com
    		out.putInt((int)ips[3]);
    	} else {
    		return; //Don't respond to these requests
    	}
		out.rewind();


		session.write((Object)out);

	}	
}

package com.savemgo.mgo1.mina;

import org.apache.log4j.Logger;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;

import com.savemgo.mgo1.Globals;
import com.savemgo.mgo1.Packet;

public class PacketDecoder extends CumulativeProtocolDecoder {
	
	private static Logger logger = Logger.getLogger(PacketDecoder.class);
	
	boolean checkComplete(int cmd, int len, int lengthPayload) {
		for (int i = 0; i < Globals.largePackets.length; i++) {
			if (cmd == Globals.largePackets[i] && len != (lengthPayload + 24)) {
				logger.debug("Waiting for other part of packet.");
				return false;
			}
		}
		return true;
	}
	
	protected boolean doDecode(IoSession session, IoBuffer buffer,
			ProtocolDecoderOutput out) throws Exception {
		try {
			int len = buffer.remaining();

			byte[] encrypted = new byte[len], decrypted = new byte[len];
			buffer.get(encrypted, 0, len);

			byte validityCheck = (byte)(encrypted[0] ^ Globals.xorKey[0]);
			if(!(validityCheck == 0x47 || validityCheck == 0x46 || validityCheck == 0x45 ||
				 validityCheck == 0x43 || validityCheck == 0x41 || validityCheck == 0x30 ||
				 validityCheck == 0x20 || validityCheck == 0x00)) {
				logger.error("Message failed validity check; unable to decode.");
				return false;
			} 

			decrypted = Globals.xor(encrypted);
			buffer.rewind();
			
			Packet pkt = new Packet();
			IoBuffer iob = IoBuffer.allocate(decrypted.length, false);
			iob.put(decrypted);
			
			final int lengthPayload = iob.position(2).getUnsignedShort(); // Length of the payload
			//System.out.printf("Payload is 0x%02x (%d) bytes vs buffer.remaining() which is %d bytes\n", lengthPayload, lengthPayload, len);
			iob.position(0);

			pkt.cmd = iob.getUnsignedShort();
			
			if (!checkComplete(pkt.cmd, len, lengthPayload))
				return false;
			
			buffer.position(len); // Must be called before we return any true
			
			if (len > 1000 || len < 24) {
				return true; // Most likely not related to MGO, ignore it
			}
			
			iob.skip(6 + Packet.MD5_SIZE);
			pkt.payload = new byte[lengthPayload];
			iob.get(pkt.payload, 0, lengthPayload);
					
			out.write(pkt);
		} catch (Exception e) {
			logger.error("Unable to decode packet");
		}
		return true;
	}
	
}

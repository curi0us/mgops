package com.savemgo.mgo1.mina;

import org.apache.log4j.Logger;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;

import com.savemgo.mgo1.Globals;
import com.savemgo.mgo1.Packet;

public class PacketEncoder implements ProtocolEncoder {
	
	private static Logger logger = Logger.getLogger(PacketEncoder.class);
	
	public void dispose(IoSession arg0) throws Exception {

	}

	public void encode(IoSession session, Object message, ProtocolEncoderOutput out)
			throws Exception {
		try {
			final Packet pkt = (Packet) message;
			
			int seq = (Integer) session.getAttribute("session_seq", 0) + 1;
			session.setAttribute("session_seq", seq);

			byte[] md5 = new byte[16], encrypted = null;
			
			IoBuffer iob = IoBuffer.allocate(Packet.HEADER_SIZE - Packet.MD5_SIZE + pkt.payload.length, false);
			iob.putUnsignedShort(pkt.cmd).putUnsignedShort(pkt.payload.length).putUnsignedInt(seq);
			if (pkt.payload.length > 0)
				iob.put(pkt.payload);
			md5 = Globals.md5(iob.array());
			iob.free();
			
			iob = IoBuffer.allocate(Packet.HEADER_SIZE + pkt.payload.length, false);
			iob.putUnsignedShort(pkt.cmd).putUnsignedShort(pkt.payload.length).putUnsignedInt(seq);
			iob.put(md5);
			if (pkt.payload.length > 0)
				iob.put(pkt.payload);
			encrypted = Globals.xor(iob.array());
			
			iob.clear(); // As long as we write the same length of data, this should be fine
			//iob.sweep(); // If only clearing proves to be an issue
			iob.put(encrypted);
			
			iob.flip();
			out.write(iob);
			iob.free();
		} catch (Exception e) {
			logger.error("Unable to encode packet", e);
		}
	}
	
}

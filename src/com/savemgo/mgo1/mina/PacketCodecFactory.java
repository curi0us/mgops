package com.savemgo.mgo1.mina;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;

public class PacketCodecFactory implements ProtocolCodecFactory {
	
    private PacketEncoder encoder;
    private PacketDecoder decoder;

    public PacketCodecFactory() {
    	encoder = new PacketEncoder();
    	decoder = new PacketDecoder();
    }

    public PacketEncoder getEncoder(IoSession ioSession) throws Exception {
        return encoder;
    }

    public PacketDecoder getDecoder(IoSession ioSession) throws Exception {
        return decoder;
    }
    
}

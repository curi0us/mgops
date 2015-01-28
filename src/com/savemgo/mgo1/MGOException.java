package com.savemgo.mgo1;

public class MGOException extends Exception{
	private int command = 0x00;
	private int code = 0x00;
	private Exception ex = null;
	public MGOException(int cmd, int c) {
		super();
		this.command = cmd;
		this.code = c;
	}
	public MGOException(int cmd, int c,String message) {
		super(message);
		this.command = cmd;
		this.code = c;
	}
	public MGOException(int cmd, int c,Exception cause) {
		super(cause.getMessage());
		this.command = cmd;
		this.code = c;
		this.ex = cause;
	}
	
	public int getCommand() {
		return this.command;
	}

	public int getCode() {
		return this.code;
	}
	public Exception getException() {
		return this.ex==null?null:this.ex;
	}
	public boolean hasCause() {
		return (this.ex==null)?false:true;	
	}
	
	public byte[] getPayload() {
		byte[] b = {(byte)0x05,(byte)0xf5,(byte)0xE1, (byte)this.code};
		return b;
	}

}
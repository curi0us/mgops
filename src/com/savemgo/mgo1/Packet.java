package com.savemgo.mgo1;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class Packet {

	public static final int HEADER_SIZE = 24, MD5_SIZE = 16, TYPE_IN = 0,
			TYPE_OUT = 1;

	public int cmd = 0xdead;
	public byte[] payload = new byte[0];

	public Packet() {

	}

	public Packet(int command) {
		this.cmd = command;
	}

	public Packet(int command, byte[] pl) {
		this.cmd = command;
		if (pl != null)
			this.payload = pl;
	}

	public Packet(int command, String file) {
		byte[] data = new byte[0];
		InputStream ios = null;
		try {
			File f = new File("./payloads/" + file);
			data = new byte[(int) f.length()];
			ios = new FileInputStream(f);
			if (ios.read(data) == -1) {
				throw new IOException(
						"EOF reached while trying to read the whole file");
			}
		} catch (FileNotFoundException ex) {
			System.out.println("Packet " + command + " payload file not found.");
		} catch (IOException ex) {
			System.out.println("Packet payload file IOException.");
		} finally {
			try {
				if (ios != null)
					ios.close();
			} catch (IOException e) {
			}
		}
		
		this.cmd = command;
		this.payload = data;
	}

}

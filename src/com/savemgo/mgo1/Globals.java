package com.savemgo.mgo1;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.HashMap;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import org.apache.mina.core.session.IoSession;

public class Globals {
	public static final int[] xorKey;
	public static int lobbyId;
	public static Map<String, Integer> lobbies = new HashMap<String, Integer>();
	public static final byte[] staticAuthSalt = {
		(byte) 0x84, (byte) 0xbd, (byte) 0xb8, (byte) 0xcf, (byte) 0xad, (byte) 0x46, (byte) 0xdd, (byte) 0x6e,
		(byte) 0x42, (byte) 0x4a, (byte) 0xe4, (byte) 0xd8, (byte) 0xd2, (byte) 0x6a, (byte) 0x12, (byte) 0xf3
	};


	/* Static initalizer */
	static{
		xorKey = new int[4];
		xorKey[0] = (int)0x5a;
		xorKey[1] = (int)0x70;
		xorKey[2] = (int)0x85;
		xorKey[3] = (int)0xaf;
	}

	/**
	 * Identifiers for any command that has a payload exceeding 232 bytes
	 * these payloads get split up so decoder needs to know to wait for rest
	 */
	public static final int[] largePackets = {
		0x4110, //CLIENT_OPTIONS_UPDATE
		0x4310, //CLIENT_GAMESETTINGS
		0x4390, //HOST_GameStats
	};

	/**
	 * Xor byte array with the xor key
	 */
	public static byte[] xor(byte[] in) {
		int length = in.length;
		for (int t = 0; t < length; t++)
			in[t] ^= Globals.xorKey[t & 3];
		return in;
	}

	/**
	 * Calculate MD5 of byte array
	 */
	public static byte[] md5(byte[] bytes) {
		try {
			if (bytes == null) {
				throw new IllegalArgumentException("Input cannot be null!");
			}
			MessageDigest md = MessageDigest.getInstance("MD5");
			return md.digest(bytes);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			System.out.println(e.getMessage());
		}
		return null;
	}

	/**
	 * Translate Bytes to Hex
	 */
	public static String bytesToHex(byte[] bytes) {
		char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for (int j = 0; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}
	
	/**
	 * Unescape any MySQL escape sequences. See MySQL language reference Chapter
	 * 6 at <a href="http://www.mysql.com/doc/">http://www.mysql.com/doc/</a>.
	 * This function will <strong>not</strong> work for other SQL-like dialects.
	 * 
	 * @param s
	 *            string to unescape, with the surrounding quotes.
	 * @return unescaped string, without the surrounding quotes.
	 * @exception IllegalArgumentException
	 *                if s is not a valid MySQL string.
	 */
	public static String unescapeMySQLString(String s)
			throws IllegalArgumentException {
		// note: the same buffer is used for both reading and writing
		// it works because the writer can never outrun the reader
		char chars[] = s.toCharArray();

		// the string must be quoted 'like this' or "like this"
		if (chars.length < 2 || chars[0] != chars[chars.length - 1]
				|| (chars[0] != '\'' && chars[0] != '"')) {
			throw new IllegalArgumentException("not a valid MySQL string: " + s);
		}

		// parse the string and decode the backslash sequences; in addition,
		// quotes can be escaped 'like this: ''', "like this: """, or 'like
		// this: "'
		int j = 1; // write position in the string (never exceeds read position)
		int f = 0; // state: 0 (normal), 1 (backslash), 2 (quote)
		for (int i = 1; i < chars.length - 1; i++) {
			if (f == 0) { // previous character was normal
				if (chars[i] == '\\') {
					f = 1; // backslash
				} else if (chars[i] == chars[0]) {
					f = 2; // quoting character
				} else {
					chars[j++] = chars[i];
				}
			} else if (f == 1) { // previous character was a backslash
				switch (chars[i]) {
				case '0':
					chars[j++] = '\0';
					break;
				case '\'':
					chars[j++] = '\'';
					break;
				case '"':
					chars[j++] = '"';
					break;
				case 'b':
					chars[j++] = '\b';
					break;
				case 'n':
					chars[j++] = '\n';
					break;
				case 'r':
					chars[j++] = '\r';
					break;
				case 't':
					chars[j++] = '\t';
					break;
				case 'z':
					chars[j++] = '\032';
					break;
				case '\\':
					chars[j++] = '\\';
					break;
				default:
					// if the character is not special, backslash disappears
					chars[j++] = chars[i];
					break;
				}
				f = 0;
			} else { // previous character was a quote
				// quoting characters must be doubled inside a string
				if (chars[i] != chars[0]) {
					throw new IllegalArgumentException(
							"not a valid MySQL string: " + s);
				}
				chars[j++] = chars[0];
				f = 0;
			}
		}
		// string contents cannot end with a special character
		if (f != 0) {
			throw new IllegalArgumentException("not a valid MySQL string: " + s);
		}

		// done
		return new String(chars, 1, j - 1);
	}

	public static boolean hasSession(IoSession session) {
		int userid = (Integer) session.getAttribute("userid",0);
		return  (userid > 0);
	}

	/**
	 * Trigger a socket.io event to be sent out to everyone on snake.savemgo.com
	 */
	public static void sendEvent(String action, String message) {
		try {
			URL oURL = new URL("http://rex.savemgo.com:5780/event?action="+URLEncoder.encode(action,"UTF-8")+"&message="+URLEncoder.encode(message,"UTF-8"));
			HttpURLConnection con = (HttpURLConnection) oURL.openConnection();
			con.setRequestMethod("GET");
			con.getResponseCode();
		} catch(Exception e) { /* Error doesn't matter, don't retry if error */ }

	}

}
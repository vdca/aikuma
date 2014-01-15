package org.lp20.aikuma.http;
import org.apache.http.conn.util.InetAddressUtils;
import org.lp20.aikuma.http.NanoHTTPD;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.net.NetworkInterface;
import java.net.InetAddress;

public class Server extends NanoHTTPD {
	private static String host_;    // hostname
	private static int port_ = -1;  // port number
	private static Server server_;  // singleton server object

	/**
	 * Protected constructor used by a factory function.
	 * 
	 * @param host hostname
	 * @param port port number
	 */
	protected Server(String host, int port) {
		super(host, port);
	}
	
	/**
	 * Factory function that returns a singleton Server object.
	 * Hostname and port must be set before this method is called.
	 * Use setHost() and setPort() to set hostname and port.
	 * 
	 * @return Server or null of server can't be created.
	 */
	public static Server getServer() {
		if (port_ < 0) {
			return null;
		}
		
		if (server_ == null) {
			server_ = new Server(host_, port_);
		}
		return server_;
	}
	
	/**
	 * Configure hostname.
	 * 
	 * @param host
	 */
	public static void setHost(String host) {
		host_ = host;
	}
	
	/**
	 * Configure port number.
	 * 
	 * @param port
	 */
	public static void setPort(int port) {
		port_ = port;
	}
	
	/**
	 * Destroy the Server singleton object.
	 */
	public static void destroyServer() {
		if (server_ != null) {
			server_.stop();
			server_ = null;
		}
	}
	
	/**
	 * 
	 * @return
	 */
	public static Map<String,String> getIpAddresses() {
		Enumeration<NetworkInterface> ifs;
		try {
			ifs = NetworkInterface.getNetworkInterfaces();
		}
		catch (java.net.SocketException e) {
			return null;
		}
		Map<String,String> ips = new HashMap<String,String>();
		while (ifs.hasMoreElements()) {
			NetworkInterface ff = ifs.nextElement();
			Enumeration<InetAddress> addrs = ff.getInetAddresses();
			while (addrs.hasMoreElements()) {
				InetAddress addr = addrs.nextElement();
				if (!addr.isLoopbackAddress()) {
					String ip = addr.getHostAddress();
					if (InetAddressUtils.isIPv4Address(ip)) {
						android.util.Log.e("http--", ff.getName());
						android.util.Log.e("http--", addr.getHostName());
						android.util.Log.e("http--", ip);
						ips.put(ff.getName(), ip);
					}
				}
			}
		}
		return ips;
	}

	@Override
	public void start() throws IOException {
		super.start();
	}
	
	@Override
	public Response serve(IHTTPSession session) {
		return new Response("hello");
	}
}

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;

/**
 * Class to function as both client and server, runs with multiple options.
 * @author zmm2962
 */
public class tsapp {
	
	/**
	 * Main function
	 * @param args Multiple, optional arguments. See documentation for details
	 */
	public static void main(String[] args) {
		
		boolean client = false, server = false, useUdp = true, useTcp = false, useUTCTime = false, setTime = false;
		String username = "-1", password = "-1", serverAddress = "0.0.0.0"; 
		int numQueries = 1, udpPort = -1, tcpPort = -1;
		long time = -1;
		
		//Read in the arguments
		for (int i = 0; i < args.length; i++) {
			switch(args[i]) {
			case "–c":
				client = true;
				break;
			case "–s":
				server = true;
				break;
			case "–u":
				break;
			case "–t":
				useTcp = true;
				useUdp = false;
				break;
			case "–z":
				useUTCTime = true;
				break;
			case "--user":
				username = args[++i];
				break;
			case "--pass":
				password = args[++i];
				break;
			case "–n":
				numQueries = Integer.parseInt(args[++i]);
				break;
			case "–T":
				time = Integer.parseInt(args[++i]);
				setTime = true;
				break;
			default:
				if (args[i].split("\\.").length == 4)
					serverAddress = args[i];
				break;
			}
		}
		
		//If server, read in both 
		if (server) {
			udpPort = Integer.parseInt(args[args.length-2]);
			tcpPort = Integer.parseInt(args[args.length-1]);
		//If client, read in either the UDP or TCP port
		} else {
			if (useUdp) {
				udpPort = Integer.parseInt(args[args.length-1]);
			} else {
				tcpPort = Integer.parseInt(args[args.length-1]);
			}
		}
		
		try {
//Start client code
			if (client) {
				InetAddress ipAddress = InetAddress.getByName(serverAddress);

				
				if (useUdp) {
					
					String message = "GET";
					if (setTime) 
						message = "SET " + time + " " + username + " " + password;
					
					InetSocketAddress destination = new InetSocketAddress(ipAddress, udpPort);

					DatagramChannel udpChannel = DatagramChannel.open();
					
					for (int i = 0; i < numQueries; i++) {
						ByteBuffer sendBuffer = ByteBuffer.allocate(100);
						ByteBuffer receiveBuffer = ByteBuffer.allocate(100);
						sendBuffer.clear();
						receiveBuffer.clear();
						sendBuffer.put(message.getBytes());
						sendBuffer.flip();
						long start = System.currentTimeMillis();
						udpChannel.send(sendBuffer, destination);
						udpChannel.receive(receiveBuffer);
						long finish = System.currentTimeMillis();
						printResponse(new String(receiveBuffer.array()), useUTCTime, finish-start);
					}
					udpChannel.close();	
					
				} else if (useTcp) {
					
					String message = "GET";
					if (setTime) 
						message = "SET " + time + " " + username + " " + password;
					
					InetSocketAddress destination = new InetSocketAddress(ipAddress, tcpPort);
					
					SocketChannel tcpChannel = SocketChannel.open();
					tcpChannel.connect(destination);
					
					for (int i = 0; i < numQueries; i++) {
						ByteBuffer sendBuffer = ByteBuffer.allocate(100);
						sendBuffer.clear();
						ByteBuffer receiveBuffer = ByteBuffer.allocate(100);
						receiveBuffer.clear();
						
						sendBuffer.put(message.getBytes());
						sendBuffer.flip();
						long start = System.currentTimeMillis();
						tcpChannel.write(sendBuffer);
						tcpChannel.read(receiveBuffer);
						long finish = System.currentTimeMillis();
						printResponse(new String(receiveBuffer.array()), useUTCTime, finish-start);
					}
					tcpChannel.close();
				}
				
//Start server code				
			} else if (server) {
				
				Selector selector = Selector.open();
				
				DatagramChannel udpChannel = DatagramChannel.open();
				ServerSocketChannel tcpMainChannel = ServerSocketChannel.open();
				
				udpChannel.configureBlocking(false);
				tcpMainChannel.configureBlocking(false);
				
				InetSocketAddress udpAddress = new InetSocketAddress(udpPort);
				udpChannel.bind(udpAddress);
				
				InetSocketAddress tcpAddress = new InetSocketAddress(tcpPort);
				tcpMainChannel.bind(tcpAddress);
				
				udpChannel.register(selector, SelectionKey.OP_READ);
				tcpMainChannel.register(selector, SelectionKey.OP_ACCEPT);
				
				while (true) {
					selector.select();
					Iterator<SelectionKey> keysIterator = selector.selectedKeys().iterator();
					while(keysIterator.hasNext()) {
						SelectionKey key = (SelectionKey) keysIterator.next();
						keysIterator.remove();
						Channel keyChannel = key.channel();
						
						if (keyChannel == udpChannel) {
	//Start UDP Server
							ByteBuffer receiveBuffer = ByteBuffer.allocate(100);
							receiveBuffer.clear();
							ByteBuffer sendBuffer = ByteBuffer.allocate(100);
							sendBuffer.clear();
							
							SocketAddress returnAddress = udpChannel.receive(receiveBuffer);
							
							String[] messageTokens = new String(receiveBuffer.array()).split(" ");
							
							//Determine whether the client is requesting or setting the time
							if (messageTokens[0].trim().equals("GET")) {
								sendBuffer.put(String.valueOf(time).getBytes());
							//Set message should have 4 parts, else error
							} else if (messageTokens[0].equals("SET") & messageTokens.length == 4) {
								//Make sure the credentials check out
								if (username.equals(messageTokens[2].trim()) & password.equals(messageTokens[3].trim())) {
									time = (Long.parseLong(messageTokens[1]));
									sendBuffer.put(String.valueOf(time).getBytes());
								} else {
									sendBuffer.put("Error: invalid credentials.".getBytes());
								}
							} else {
								sendBuffer.put("Error: bad message.".getBytes());
							}
							sendBuffer.flip();
							udpChannel.send(sendBuffer, returnAddress);
							
						} else if (keyChannel == tcpMainChannel) {
	//Start TCP Server
							SocketChannel tcpSocketChannel = tcpMainChannel.accept();
							ByteBuffer receiveBuffer = ByteBuffer.allocate(100);
							receiveBuffer.clear();
							ByteBuffer sendBuffer = ByteBuffer.allocate(100);
							sendBuffer.clear();
							tcpSocketChannel.read(receiveBuffer);
							String[] messageTokens = new String(receiveBuffer.array()).split(" ");
							
							//Determine whether the client is requesting the time, or setting the time
							if (messageTokens[0].equals("GET")) {
								sendBuffer.put(String.valueOf(time).getBytes());
							//Set message should have 4 parts, else error
							} else if (messageTokens[0].equals("SET")) {
								//Make sure the credentials check out
								if (username.equals(messageTokens[2].trim()) & password.equals(messageTokens[3].trim())) {
									time = Long.parseLong(messageTokens[1]);
									sendBuffer.put(String.valueOf(time).getBytes());
								} else {
									sendBuffer.put("Error: invalid credentials.".getBytes());
								}
							} else {
								sendBuffer.put("Error: invalid credentials.".getBytes());
							}
							sendBuffer.flip();
							tcpSocketChannel.write(sendBuffer);
						}
					}
				}
			} else {
				System.err.println("tsapp thinks it is neither a client or server, were flags set with em-dash?");
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
		
	}
	
	/**
	 * Prints the server response and the round-trip-time
	 * @param response String the server response
	 * @param useUTCTime boolean whether to print the time in UTC format
	 * @param rtt long the round-trip-time
	 */
	public static void printResponse(String response, boolean useUTCTime, long rtt) {
		String[] responseTokens = response.split(" ");
		
		//Error messages start with "Error:"
		if (responseTokens[0].trim().equals("Error:")) {
			System.err.println(response);
		//Format and print UTC time
		} else if (useUTCTime) {
			Date date = new Date(Long.parseLong(response.trim()) * 1000); 
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
			formatter.setTimeZone(TimeZone.getTimeZone("Z"));
			System.out.println("Server time: " + formatter.format(date));
		//Print unix time
		} else {
			System.out.println("Server time: " + response);
		}
		
		//Print the round-trip-time
		System.out.println("RTT To Server: " + rtt + " ms.");
	}
}
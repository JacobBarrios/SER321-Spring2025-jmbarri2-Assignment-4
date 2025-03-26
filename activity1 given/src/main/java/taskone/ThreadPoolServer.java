/**
 File: ThreadedServer.java
 Author: Jacob Barrios
 Description: Server class in package taskone.
 */

package taskone;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.json.JSONObject;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class: ThreadedServer
 * Description: ThreadedServer tasks.
 */
class ThreadPoolServer {
	static Socket conn;
	static Performer performer;
	static int maxUsers;
	static int totalUsers = 0;
	static boolean createConnection = true;
	
	public static void main(String[] args) throws Exception {
		int port = 0;
		StringList strings = new StringList();
		performer = new Performer(strings);
		
		if (args.length != 2) {
			// gradle runServer -Pport=8080 -q --console=plain
			System.out.println("Usage: gradle runServer -Pport=8080 -Pusers=5 -q --console=plain");
			System.exit(1);
		}
		
		try {
			port = Integer.parseInt(args[0]);
		} catch (NumberFormatException nfe) {
			System.out.println("[Port] must be an integer");
			System.exit(2);
		}
		try {
			maxUsers = Integer.parseInt(args[1]);
		} catch (NumberFormatException nfe) {
			System.out.println("[users] must be an integer");
			System.exit(2);
		}
		ServerSocket server = new ServerSocket(port);
		System.out.println("Server Started...");
		
		while (true) {
			conn = server.accept();
			if(totalUsers == maxUsers) {
				createConnection = false;
			}
			else {
				createConnection = true;
				totalUsers++;
			}
			System.out.println("[DEBUG] server accept connection");
			ThreadPoolHandler newThreadHandler = new ThreadPoolHandler(performer, conn, createConnection);
			
			newThreadHandler.start();
			
		}
	}
	
}

class ThreadPoolHandler extends Thread {
	private final Socket conn;
	private final Performer performer;
	private static final Lock mutex = new ReentrantLock();
	private final boolean createConnection;
	
	public ThreadPoolHandler(Performer performer, Socket conn, boolean createConnection) {
		this.performer = performer;
		this.conn = conn;
		this.createConnection = createConnection;
	}
	
	@Override
	public void run() {
		doPerform();
	}
	
	public void doPerform() {
		boolean quit = false;
		OutputStream out = null;
		InputStream in = null;
		try {
			out = conn.getOutputStream();
			in = conn.getInputStream();
			
			System.out.println("Server connected to client:");
			if(createConnection) {
				JSONObject startMessage = new JSONObject();
				startMessage.put("type", "start");
				byte[] startOutput = JsonUtils.toByteArray(startMessage);
				NetworkUtils.send(out, startOutput);
			}
			else {
				JSONObject cancelMessage = new JSONObject();
				cancelMessage.put("type", "cancel");
				byte[] startOutput = JsonUtils.toByteArray(cancelMessage);
				NetworkUtils.send(out, startOutput);
			}
			
			while (!quit) {
				JSONObject returnMessage;
				System.out.println("Accepting a Request...");
				byte[] messageBytes = NetworkUtils.receive(in);
				JSONObject message = JsonUtils.fromByteArray(messageBytes);
				
				int choice = message.getInt("selected");
				switch (choice) {
					case (1):
						String inStr = (String) message.get("data");
						mutex.lock();
						try {
							returnMessage = performer.add(inStr);
						}
						finally {
							mutex.unlock();
						}
						break;
					case (2):
						returnMessage = performer.display();
						break;
					case (3):
						returnMessage = performer.count();
						break;
					case (0):
						returnMessage = performer.quit();
						quit = true;
						break;
					default:
						returnMessage = performer.error("Invalid selection: " + choice
								+ " is not an option");
						break;
				}
				
				// we are converting the JSON object we have to a byte[]
				byte[] output = JsonUtils.toByteArray(returnMessage);
				NetworkUtils.send(out, output);
			}
			// close the resource
			System.out.println("close the resources of client ");
			ThreadPoolServer.totalUsers--;
			out.close();
			in.close();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
	
}

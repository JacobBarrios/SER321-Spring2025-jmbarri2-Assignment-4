/**
  File: Server.java
  Author: Student in Fall 2020B
  Description: Server class in package taskone.
*/

package taskone;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import org.json.JSONObject;

/**
 * Class: Server
 * Description: Server tasks.
 */
class Server {
    static Socket conn;
    static Performer performer;

    public static void main(String[] args) throws Exception {
        int port = 0;
        StringList strings = new StringList();
        performer = new Performer(strings);

        if (args.length != 1) {
            // gradle runServer -Pport=8080 -q --console=plain
            System.out.println("Usage: gradle runServer -Pport=8080 -q --console=plain");
            System.exit(1);
        }
        
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException nfe) {
            System.out.println("[Port] must be an integer");
            System.exit(2);
        }
        ServerSocket server = new ServerSocket(port);
        System.out.println("Server Started...");
        while (true) {
            System.out.println("Accepting a Request...");
            conn = server.accept();
            doPerform();

        }
    }

    public static void doPerform() {
        boolean quit = false;
        OutputStream out = null;
        InputStream in = null;
        try {
            out = conn.getOutputStream();
            in = conn.getInputStream();
            System.out.println("Server connected to client:");
            
            JSONObject startMessage = new JSONObject();
            startMessage.put("type", "start");
            byte[] startOutput = JsonUtils.toByteArray(startMessage);
            NetworkUtils.send(out, startOutput);
            
            while (!quit) {
                byte[] messageBytes = NetworkUtils.receive(in);
                JSONObject message = JsonUtils.fromByteArray(messageBytes);
                JSONObject returnMessage;

                int choice = message.getInt("selected");
                switch (choice) {
                    case (1):
                        String inStr = (String) message.get("data");
                        returnMessage = performer.add(inStr);
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
            out.close();
            in.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}

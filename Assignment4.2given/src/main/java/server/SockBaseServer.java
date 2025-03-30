package server;

import buffers.RequestProtos.*;
import buffers.ResponseProtos.*;

import client.Player;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.*;

class SockBaseServer extends Thread {
    static String logFilename = "logs.txt";
    static String leaderFilename = "leaderboard.json";

    // Please use these as given so it works with our test cases
    static String menuOptions = "\nWhat would you like to do? \n 1 - to see the leader board \n 2 - to enter a game \n 3 - quit the game";
    static String gameOptions = "\nChoose an action: \n (1-9) - Enter an int to specify the row you want to update \n c - Clear number \n r - New Board";
    
    InputStream in = null;
    OutputStream out = null;
    Socket clientSocket = null;
    private final int id; // client id
    Response response;

    Game game; // current game

    private boolean inGame = false; // a game was started (you can decide if you want this
    private String name; // player name
    private JSONArray leaderboardList;

    private int currentState = 1; // I used something like this to keep track of where I am in the game, you can decide if you want that as well

    private static boolean grading = true; // if the grading board should be used

    public SockBaseServer(Socket sock, Game game, int id, boolean grading) {
        this.clientSocket = sock;
        this.game = game;
        this.id = id;
        SockBaseServer.grading = grading;
        try {
            in = clientSocket.getInputStream();
            out = clientSocket.getOutputStream();
        } catch (Exception e){
            System.out.println("Error in constructor: " + e);
        }
    }
    
    public void run() {
		try {
			startGame();
		}
		catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

    /**
     * Received a request, starts to evaluate what it is and handles it, not complete
     */
    public void startGame() throws IOException {
        Path leaderJSONPath = Paths.get(leaderFilename);
        // Create leaderboard.json file
        if (!Files.exists(leaderJSONPath)) {
            try {
                // Create a new JSON file if it doesn't exist
                Files.createFile(leaderJSONPath);
                System.out.println("[DEBUG] File created: " + leaderJSONPath);
                
                // Create a sample empty JSONArray and write it to the file
                JSONArray emptyArray = new JSONArray();
                Files.write(leaderJSONPath, emptyArray.toString().getBytes());
                System.out.println("[DEBUG] Empty array: " + emptyArray.toString());
                System.out.println("[DEBUG] Initialized empty JSON array in the file.");
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        // Get the json list from the json file
        String jsonString = new String(Files.readAllBytes(Paths.get(leaderFilename)));
        leaderboardList = new JSONArray(jsonString);
        
        try {
            while (true) {
                // read the proto object and put into new object
                Request op = Request.parseDelimitedFrom(in);
                System.out.println("[DEBUG] Got request: " + op.toString());

                boolean quit = false;

                // should handle all the other request types here, my advice is to put them in methods similar to nameRequest()
                switch (op.getOperationType()) {
                    case NAME:
                        if (op.getName().isBlank()) {
                            response = error(1, "name");
                        } else {
                            response = nameRequest(op);
                        }
                        
                        break;
                    case LEADERBOARD:
                        response = leaderRequest();
                        
                        break;
                    case START:
                        response = startRequest(op);

                        break;
                    case UPDATE:
                        response = playRequest(op);
                        
                        break;
                    case CLEAR:
                        response = clearRequest(op);
                        
                        break;
                    case QUIT:
                        quit = true;
                        response = quit();
                        
                        break;
                    default:
                        response = error(2, op.getOperationType().name());
                        break;
                }
                
                response.writeDelimitedTo(out);

                if (quit) {
                    return;
                }
            }
        } catch (SocketException se) {
            System.out.println("[DEBUG] Client disconnected");
        } catch (Exception ex) {
            Response error = error(0, "Unexpected server error: " + ex.getMessage());
            error.writeDelimitedTo(out);
        }
        finally {
            System.out.println("[DEBUG] Client ID " + id + " disconnected");
            this.inGame = false;
            exitAndClose(in, out, clientSocket);
        }
    }

    void exitAndClose(InputStream in, OutputStream out, Socket serverSock) throws IOException {
        if (in != null)   in.close();
        if (out != null)  out.close();
        if (serverSock != null) serverSock.close();
    }

    /**
     * Handles the name request and returns the appropriate response
     * @return Request.Builder holding the reponse back to Client as specified in Protocol
     */
    private Response nameRequest(Request op) throws IOException {
        name = op.getName();

        writeToLog(name, Message.CONNECT);
        checkForPlayer(name);
        currentState = 2;

        System.out.println("[DEBUG] Got a connection and a name: " + name);
        return Response.newBuilder()
                .setResponseType(Response.ResponseType.GREETING)
                .setMessage("Hello " + name + " and welcome to a simple game of Sudoku.")
                .setMenuoptions(menuOptions)
                .setNext(currentState)
                .build();
    }
    
    private void checkForPlayer(String name) {
        Player client = new Player(name, 0, 1, 0);
        
        boolean found = false;
        for(int i = 0; i < leaderboardList.length(); i++) {
            JSONObject player = leaderboardList.getJSONObject(i);

            // Update client if client is already on the leaderboard
            if(player.getString("name").equals(client.getName())) {
                found = true;
                
                client.setWins(player.getInt("wins"));
                
                client.setLogins(player.getInt("logins"));
                client.increaseLogin();
                player.put("logins", client.getLogins());
                
                System.out.println("[DEBUG] Found player in leaderboard");
                
                break;
                
            }
        }
        
        if(!found) {
            JSONObject newPlayer = new JSONObject();
            newPlayer.put("name", client.getName());
            newPlayer.put("wins", client.getWins());
            newPlayer.put("logins", client.getLogins());
            newPlayer.put("points", client.getPoints());
            
            leaderboardList.put(newPlayer);
            
        }
        
        try (FileWriter fw = new FileWriter(leaderFilename)) {
            fw.write(leaderboardList.toString(4));
            System.out.println("[DEBUG] Login successfully updated " + leaderFilename);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        
    }
    
    private Response leaderRequest() throws IOException {
        System.out.println("[DEBUG] Got leaderboard request");
        
        Response.Builder newResponse = Response.newBuilder();
        
        for(int i = 0; i < leaderboardList.length(); i++) {
            JSONObject playerJSON = leaderboardList.getJSONObject(i);
            
            Entry player = Entry.newBuilder()
                    .setName(playerJSON.getString("name"))
                    .setPoints(playerJSON.getInt("points"))
                    .setLogins(playerJSON.getInt("logins"))
                    .build();
            
            newResponse.addLeader(player);
            
        }
        
        return newResponse
                .setResponseType(Response.ResponseType.LEADERBOARD)
                .setMenuoptions(menuOptions)
                .setNext(2)
                .build();
    }
    
    /**
     * Starts to handle start of a game after START request, is not complete of course, just shows how to get to the board
     */
    private Response startRequest(Request op) throws IOException {
        System.out.println("[DEBUG] start request");

        game.newGame(grading, op.getDifficulty()); // difficulty should be read from request!

        System.out.println("[DEBUG] Player Board: \n" + game.getDisplayBoard());
        System.out.println("[DEBUG] Solved Board: \n" + game.getSolvedBoard());
        
        return Response.newBuilder()
                .setResponseType(Response.ResponseType.START)
                .setBoard(game.getDisplayBoard())
                .setMessage("\n")
                .setMenuoptions(gameOptions)
                .setNext(3)
                .build();
    }
    
    private Response playRequest(Request op) throws IOException {
        int row = op.getRow();
        int column = op.getColumn();
        int value = op.getValue();
        int eval;
        Response.Builder response = Response.newBuilder();
        
        System.out.println("[DEBUG] play request");
        System.out.println("[DEBUG] Row: " + row);
        System.out.println("[DEBUG] Column: " + column);
        System.out.println("[DEBUG] Value: " + value);
        
        System.out.println("[DEBUG] Character at coordinates selected: " + game.getPlayerBoard()[row][column]);
        
        eval = game.updateBoard(row, column, value, 0);
        
        if (game.getWon()) {
            JSONObject winner;
            game.setPoints(20);
            
            for(int i = 0; i < leaderboardList.length(); i++) {
                if(leaderboardList.getJSONObject(i).getString("name").equals(name)) {
                    winner = leaderboardList.getJSONObject(i);
                    winner.put("points", winner.getInt("points") + game.getPoints());
                    winner.put("wins", winner.getInt("wins") + game.getPoints());
                }
                
            }
            
            System.out.println("[DEBUG] Client " + id + "Won the game");
            response = Response.newBuilder()
                    .setResponseType(Response.ResponseType.WON)
                    .setBoard(game.getDisplayBoard())
                    .setType(Response.EvalType.UPDATE)
                    .setMenuoptions(menuOptions)
                    .setMessage("You solved the current puzzle, good job!")
                    .setPoints(game.getPoints())
                    .setNext(2);
            
            game.setPoints(-game.getPoints());
        }
        else {
            if(eval == 0) {
                System.out.println("[DEBUG] Valid move");
                
                response =  Response.newBuilder()
                        .setResponseType(Response.ResponseType.PLAY)
                        .setBoard(game.getDisplayBoard())
                        .setType(Response.EvalType.UPDATE)
                        .setMenuoptions(gameOptions)
                        .setPoints(game.getPoints())
                        .setNext(3);
            }
            else if(eval == 1) {
                game.setPoints(-2);
                System.out.println("[DEBUG] Can't fill with number");
                
                response = Response.newBuilder()
                        .setResponseType(Response.ResponseType.PLAY)
                        .setBoard(game.getDisplayBoard())
                        .setType(Response.EvalType.PRESET_VALUE)
                        .setMenuoptions(gameOptions)
                        .setPoints(game.getPoints())
                        .setNext(3);
            }
            else if(eval == 2) {
                game.setPoints(-2);
                System.out.println("[DEBUG] Duplicate row");
                
                response = Response.newBuilder()
                        .setResponseType(Response.ResponseType.PLAY)
                        .setBoard(game.getDisplayBoard())
                        .setPoints(game.getPoints())
                        .setMenuoptions(gameOptions)
                        .setType(Response.EvalType.DUP_ROW)
                        .setNext(3);
            }
            else if(eval == 3) {
                game.setPoints(-2);
                System.out.println("[DEBUG] Duplicate column");
                
                response = Response.newBuilder()
                        .setResponseType(Response.ResponseType.PLAY)
                        .setBoard(game.getDisplayBoard())
                        .setPoints(game.getPoints())
                        .setMenuoptions(gameOptions)
                        .setType(Response.EvalType.DUP_COL)
                        .setNext(3);
            }
            else if(eval == 4) {
                game.setPoints(-2);
                System.out.println("[DEBUG] Duplicate grid");
                
                response = Response.newBuilder()
                        .setResponseType(Response.ResponseType.PLAY)
                        .setBoard(game.getDisplayBoard())
                        .setPoints(game.getPoints())
                        .setMenuoptions(gameOptions)
                        .setType(Response.EvalType.DUP_GRID)
                        .setNext(3);
            }
            
        }
        
        return response.build();
        
    }
    
    private Response clearRequest(Request op) throws IOException {
        int row = op.getRow();
        int column = op.getColumn();
        int value = op.getValue();
        
        Response.EvalType evalType = Response.EvalType.CLEAR_VALUE;
        
        game.updateBoard(row, column, 0, value);
        game.setPoints(-5);
        
        if(value == 1) {
            evalType = Response.EvalType.CLEAR_VALUE;
            
        }
        else if(value == 2) {
            evalType = Response.EvalType.CLEAR_ROW;
            
        }
        else if(value == 3) {
            evalType = Response.EvalType.CLEAR_COL;
            
        }
        else if(value == 4) {
            evalType = Response.EvalType.CLEAR_GRID;
            
        }
        else if(value == 5) {
            evalType = Response.EvalType.CLEAR_BOARD;
            
        }
        else if(value == 6) {
            evalType = Response.EvalType.RESET_BOARD;
            
        }
        
        return response = Response.newBuilder()
                .setResponseType(Response.ResponseType.PLAY)
                .setBoard(game.getDisplayBoard())
                .setPoints(game.getPoints())
                .setMenuoptions(gameOptions)
                .setNext(3)
                .setType(evalType)
                .build();
    }

    /**
     * Handles the quit request, might need adaptation
     * @return Request.Builder holding the reponse back to Client as specified in Protocol
     */
    private Response quit() throws IOException {
        this.inGame = false;
        return Response.newBuilder()
                .setResponseType(Response.ResponseType.BYE)
                .setMessage("Thank you for playing! goodbye.")
                .build();
    }

    /**
     * Start of handling errors, not fully done
     * @return Request.Builder holding the reponse back to Client as specified in Protocol
     */
    private Response error(int err, String field) throws IOException {
        String message = "";
        int type = err;
        Response.Builder response = Response.newBuilder();

        switch (err) {
            case 1:
                message = "\nError: required field missing or empty";
                break;
            case 2:
                message = "\nError: request not supported";
                break;
            default:
                message = "\nError: cannot process your request";
                type = 0;
                break;
        }

        response
                .setResponseType(Response.ResponseType.ERROR)
                .setErrorType(type)
                .setMessage(message)
                .setNext(currentState)
                .build();

        return response.build();
    }
    
    /**
     * Writing a new entry to our log
     * @param name - Name of the person logging in
     * @param message - type Message from Protobuf which is the message to be written in the log (e.g. Connect) 
     * @return String of the new hidden image
     */
    public void writeToLog(String name, Message message) {
        try {
            // read old log file
            Logs.Builder logs = readLogFile();

            Date date = java.util.Calendar.getInstance().getTime();

            // we are writing a new log entry to our log
            // add a new log entry to the log list of the Protobuf object
            logs.addLog(date + ": " +  name + " - " + message);

            // open log file
            FileOutputStream output = new FileOutputStream(logFilename);
            Logs logsObj = logs.build();

            // write to log file
            logsObj.writeTo(output);
        } catch(Exception e) {
            System.out.println("Issue while trying to save");
        }
    }

    /**
     * Reading the current log file
     * @return Logs.Builder a builder of a logs entry from protobuf
     */
    public Logs.Builder readLogFile() throws Exception {
        Logs.Builder logs = Logs.newBuilder();

        try {
            return logs.mergeFrom(new FileInputStream(logFilename));
        } catch (FileNotFoundException e) {
            System.out.println(logFilename + ": File not found.  Creating a new file.");
            return logs;
        }
    }
}

class ServerMain {
    public static void main (String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Expected arguments: <port(int)> <delay(int)>");
            System.exit(1);
        }
        int port = 8080; // default port
        boolean grading = Boolean.parseBoolean(args[1]);
        Socket clientSocket = null;
        ServerSocket socket = null;
        
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException nfe) {
            System.out.println("[Port|sleepDelay] must be an integer");
            System.exit(2);
        }
        try {
            socket = new ServerSocket(port);
            System.out.println("Server started..");
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
        int id = 1;
        while (true) {
            try {
                clientSocket = socket.accept();
                System.out.println("Attempting to connect to client-" + id);
                Game game = new Game();
                SockBaseServer server = new SockBaseServer(clientSocket, game, id++, grading);
                server.start();
            } catch (Exception e) {
                System.out.println("Error in accepting client connection.");
            }
        }
    }
    
}

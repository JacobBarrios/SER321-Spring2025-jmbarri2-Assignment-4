package server;

import buffers.RequestProtos.*;
import buffers.ResponseProtos.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;

class SockBaseServer extends Thread {
    static String logFilename = "logs.txt";

    // Please use these as given so it works with our test cases
    static String menuOptions = "\nWhat would you like to do? \n 1 - to see the leader board \n 2 - to enter a game \n 3 - quit the game";
    static String gameOptions = "\nChoose an action: \n (1-9) - Enter an int to specify the row you want to update \n c - Clear number \n r - New Board";


    ServerSocket serv = null;
    InputStream in = null;
    OutputStream out = null;
    Socket clientSocket = null;
    private final int id; // client id

    Game game; // current game

    private boolean inGame = false; // a game was started (you can decide if you want this
    private String name; // player name

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
        try {
            while (true) {
                // read the proto object and put into new object
                Request op = Request.parseDelimitedFrom(in);
                System.out.println("Got request: " + op.toString());
                Response response;

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
                        //TODO update leaderboard request
                        response = leaderRequest();
                        
                        break;
                    case START:
                        response = startRequest(op);

                        break;
                    case UPDATE:
                        //TODO if game not done send play, if won send won
                        response = playRequest(op);
                        
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
            System.out.println("Client disconnected");
        } catch (Exception ex) {
            Response error = error(0, "Unexpected server error: " + ex.getMessage());
            error.writeDelimitedTo(out);
        }
        finally {
            System.out.println("Client ID " + id + " disconnected");
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
        currentState = 2;

        System.out.println("Got a connection and a name: " + name);
        return Response.newBuilder()
                .setResponseType(Response.ResponseType.GREETING)
                .setMessage("Hello " + name + " and welcome to a simple game of Sudoku.")
                .setMenuoptions(menuOptions)
                .setNext(currentState)
                .build();
    }
    
    private Response leaderRequest() throws IOException {
        System.out.println("Got leaderboard request");
        
        currentState = 2;
        
        return Response.newBuilder()
                .setResponseType(Response.ResponseType.LEADERBOARD)
                .setMenuoptions(menuOptions)
                .setNext(currentState)
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
    
    //TODO implement play request
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
        
        if(game.getWon()) {
            System.out.println("[DEBUG] won the game");
            response = Response.newBuilder()
                    .setResponseType(Response.ResponseType.WON)
                    .setBoard(game.getDisplayBoard())
                    .setType(Response.EvalType.UPDATE)
                    .setMenuoptions(menuOptions)
                    .setMessage("You solved the current puzzle, good job!")
                    .setPoints(game.getPoints())
                    .setNext(2);
        }
        else {
            if(eval == 0) {
                game.setPoints(20);
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

package client;

import buffers.RequestProtos.*;
import buffers.ResponseProtos.*;

import java.io.*;
import java.net.Socket;

class SockBaseClient {
    public static void main (String[] args) throws Exception {
        Socket serverSock = null;
        OutputStream out = null;
        InputStream in = null;
        int i1=0, i2=0;
        int port = 8080; // default port

        // Make sure two arguments are given
        if (args.length != 2) {
            System.out.println("Expected arguments: <host(String)> <port(int)>");
            System.exit(1);
        }
        String host = args[0];
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException nfe) {
            System.out.println("[Port] must be integer");
            System.exit(2);
        }

        // Build the first request object just including your name
        Request op = nameRequest().build();
        Response response;
        
        try {
            // connect to the server
            serverSock = new Socket(host, port);

            // write to the server
            out = serverSock.getOutputStream();
            in = serverSock.getInputStream();

            op.writeDelimitedTo(out);

            while (true) {
                // read from the server
                response = Response.parseDelimitedFrom(in);
                System.out.println("[DEBUG] Got a response: " + response.toString());

                Request.Builder req = Request.newBuilder();
                
                switch (response.getResponseType()) {
                    case GREETING:
                        // Server saying hello to client
                        System.out.println(response.getMessage());
                        req = chooseMenuRequest(req, response);
                        
                        break;
                    case START:
                        // Goes to in game menu insert, clear, new board
                        req = chooseInGameMenuRequest(req, response);
                        
                        break;
                    case PLAY:
                        // Server said keep playing
                        req = playRequest(req, response);
                        
                        break;
                    case WON:
                        // Server said client won
                        System.out.println(response.getMessage());
                        req = chooseMenuRequest(req, response);
                        
                        break;
                    case LEADERBOARD:
                        // Server sends leaderboard
                        System.out.println("===== Leaderboard =====");
                        
                        // Iterate through the repeated field
                        for (Entry player : response.getLeaderList()) {
                            System.out.println("Name: " + player.getName());
                            System.out.println("Points: " + player.getPoints());
                            System.out.println("Logins: " + player.getLogins());
                            System.out.println("----------------------");
                        }
                        
                        req = chooseMenuRequest(req, response);
                        
                        break;
                    case BYE:
                        // Server said to quit
                        System.out.println("Quiting");
                        
                        return;
                    case ERROR:
                        // Server sent an error
                        System.out.println("Error: " + response.getMessage() + "Type: " + response.getErrorType());
                        if (response.getNext() == 1) {
                            req = nameRequest();
                        } else {
                            System.out.println("That error type is not handled yet");
                            req = nameRequest();
                        }
                        
                        break;
                }
                req.build().writeDelimitedTo(out);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            exitAndClose(in, out, serverSock);
        }
    }

    /**
     * handles building a simple name requests, asks the user for their name and builds the request
     * @return Request.Builder which holds all teh information for the NAME request
     */
    static Request.Builder nameRequest() throws IOException {
        System.out.println("Please provide your name for the server.");
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        String strToSend = stdin.readLine();

        return Request.newBuilder()
                .setOperationType(Request.OperationType.NAME)
                .setName(strToSend);
    }

    /**
     * Shows the main menu and lets the user choose a number, it builds the request for the next server call
     * @return Request.Builder which holds the information the server needs for a specific request
     */
    static Request.Builder chooseMenuRequest(Request.Builder req, Response response) throws IOException {
        while (true) {
            System.out.println(response.getMenuoptions());
            System.out.print("Enter a number 1-3: ");
            
            BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
            String menu_select = stdin.readLine();
            System.out.println("[DEBUG] Selected: " + menu_select);
            switch (menu_select) {
                // needs to include the other requests
                case "1":
                    System.out.println("[DEBUG] Chose leaderboard");
                    req.setOperationType(Request.OperationType.LEADERBOARD);
                    return req;
                case "2":
                    // Start a new game from main menu
                    System.out.println("[DEBUG] Chose start new game");
                    req.setOperationType(Request.OperationType.START)
                            .setDifficulty(getDifficulty());
                    return req;
                case "3":
                    System.out.println("[DEBUG] Chose quit");
                    req.setOperationType(Request.OperationType.QUIT);
                    return req;
                default:
                    System.out.println("\nNot a valid choice, please choose again");
                    break;
            }
        }
    }
    
    static int getDifficulty() throws IOException {
        System.out.println("Please provide difficulty level (1-20) for the game.");
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        int difficultyToSend = 1; // Default value
        boolean selecting = true;
        
        while (selecting) {
            try {
                String stringDifficulty = stdin.readLine();
                difficultyToSend = Integer.parseInt(stringDifficulty);
                
                if (difficultyToSend < 1 || difficultyToSend > 20) {
                    System.out.println("Please enter a difficulty between 1 and 20.");
                } else {
                    selecting = false;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Difficulty should be an integer.");
            }
        }
        
        return difficultyToSend;
    }
    
    static Request.Builder chooseInGameMenuRequest(Request.Builder req, Response response) throws IOException {
        int row;
        int column;
        int value;
        
        System.out.println(response.getBoard());
        System.out.println(response.getMenuoptions());
        
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        String menu_select = stdin.readLine();
        System.out.println("[DEBUG] Selected: " + menu_select);
        
        try {
            // Insert row
            row = Integer.parseInt(menu_select);
            
            // Insert column
            System.out.println("Enter column (1-9)");
            column = Integer.parseInt(stdin.readLine());
            
            // Insert value
            System.out.printf("What value do you want to insert at row: %d, column: %d?\n", row, column);
            value = Integer.parseInt(stdin.readLine());
            
            req.setOperationType(Request.OperationType.UPDATE)
                    .setRow(row - 1)
                    .setColumn(column - 1)
                    .setValue(value);
        }
        catch(NumberFormatException e) {
			switch(menu_select) {
                // If you want to clear
                case "c":
					System.out.println("[DEBUG] Clear board");
					try {
						int[] coordinates = boardSelectionClear();
						req.setOperationType(Request.OperationType.CLEAR)
								.setRow(coordinates[0])
								.setColumn(coordinates[1])
								.setValue(coordinates[2]);
					}
					catch(Exception ex) {
						throw new RuntimeException(ex);
					}
                    
                    break;
                // If you want to get a new board
                case "r":
					System.out.println("[DEBUG] New board");
					req.setOperationType(Request.OperationType.CLEAR)
							.setRow(-1)
							.setColumn(-1)
							.setValue(6);
				// If you want to exit
                case "exit":
                    req.setOperationType(Request.OperationType.QUIT);
			}
        }
        
        return req;
        
    }
    
    static Request.Builder playRequest(Request.Builder req, Response response) throws IOException {
        // Tell client the result of there request
        processEval(response);
        
        // Continue game
        return chooseInGameMenuRequest(req, response);
        
    }
    
    static void processEval(Response response) {
        if(response.getType() == Response.EvalType.UPDATE) {
            System.out.println("Number was filled successfully");
        }
        else if(response.getType() == Response.EvalType.PRESET_VALUE) {
            System.out.println("Couldn't fill in spot");
        }
        else if(response.getType() == Response.EvalType.DUP_ROW) {
            System.out.println("Number exists in row");
        }
        else if(response.getType() == Response.EvalType.DUP_COL) {
            System.out.println("Number exists in row");
        }
        else if(response.getType() == Response.EvalType.DUP_GRID) {
            System.out.println("Number exists in grid");
        }
        
        System.out.println("Current points: " + response.getPoints());
    
    }

    /**
     * Exits the connection
     */
    static void exitAndClose(InputStream in, OutputStream out, Socket serverSock) throws IOException {
        if (in != null)   in.close();
        if (out != null)  out.close();
        if (serverSock != null) serverSock.close();
        System.exit(0);
    }

    /**
     * Handles the clear menu logic when the user chooses that in Game menu. It returns the values exactly
     * as needed in the CLEAR request row int[0], column int[1], value int[3]
     */
    static int[] boardSelectionClear() throws Exception {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Choose what kind of clear by entering an integer (1 - 5)");
        System.out.print(" 1 - Clear value \n 2 - Clear row \n 3 - Clear column \n 4 - Clear Grid \n 5 - Clear Board \n");

        String selection = stdin.readLine();

        while (true) {
            if (selection.equalsIgnoreCase("exit")) {
                return new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            }
            try {
                int temp = Integer.parseInt(selection);

                if (temp < 1 || temp > 5) {
                    throw new NumberFormatException();
                }

                break;
            } catch (NumberFormatException nfe) {
                System.out.println("That's not an integer!");
                System.out.println("Choose what kind of clear by entering an integer (1 - 5)");
                System.out.print("1 - Clear value \n 2 - Clear row \n 3 - Clear column \n 4 - Clear Grid \n 5 - Clear Board \n");
            }
            selection = stdin.readLine();
        }

        int[] coordinates = new int[3];

        switch (selection) {
            case "1":
                // clear value, so array will have {row, col, 1}
                coordinates = boardSelectionClearValue();
                break;
            case "2":
                // clear row, so array will have {row, -1, 2}
                coordinates = boardSelectionClearRow();
                break;
            case "3":
                // clear col, so array will have {-1, col, 3}
                coordinates = boardSelectionClearCol();
                break;
            case "4":
                // clear grid, so array will have {gridNum, -1, 4}
                coordinates = boardSelectionClearGrid();
                break;
            case "5":
                // clear entire board, so array will have {-1, -1, 5}
                coordinates[0] = -1;
                coordinates[1] = -1;
                coordinates[2] = 5;
                break;
            default:
                break;
        }

        return coordinates;
    }

    static int[] boardSelectionClearValue() throws Exception {
        int[] coordinates = new int[3];

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Choose coordinates of the value you want to clear");
        System.out.print("Enter the row as an integer (1 - 9): ");
        String row = stdin.readLine();

        while (true) {
            if (row.equalsIgnoreCase("exit")) {
                return new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            }
            try {
                Integer.parseInt(row);
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("That's not an integer!");
                System.out.print("Enter the row as an integer (1 - 9): ");
            }
            row = stdin.readLine();
        }

        coordinates[0] = Integer.parseInt(row);

        System.out.print("Enter the column as an integer (1 - 9): ");
        String col = stdin.readLine();

        while (true) {
            if (col.equalsIgnoreCase("exit")) {
                return new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            }
            try {
                Integer.parseInt(col);
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("That's not an integer!");
                System.out.print("Enter the column as an integer (1 - 9): ");
            }
            col = stdin.readLine();
        }

        coordinates[1] = Integer.parseInt(col);
        coordinates[2] = 1;

        return coordinates;
    }

    static int[] boardSelectionClearRow() throws Exception {
        int[] coordinates = new int[3];

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Choose the row you want to clear");
        System.out.print("Enter the row as an integer (1 - 9): ");
        String row = stdin.readLine();

        while (true) {
            if (row.equalsIgnoreCase("exit")) {
                return new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            }
            try {
                Integer.parseInt(row);
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("That's not an integer!");
                System.out.print("Enter the row as an integer (1 - 9): ");
            }
            row = stdin.readLine();
        }

        coordinates[0] = Integer.parseInt(row);
        coordinates[1] = -1;
        coordinates[2] = 2;

        return coordinates;
    }

    static int[] boardSelectionClearCol() throws Exception {
        int[] coordinates = new int[3];

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Choose the column you want to clear");
        System.out.print("Enter the column as an integer (1 - 9): ");
        String col = stdin.readLine();

        while (true) {
            if (col.equalsIgnoreCase("exit")) {
                return new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            }
            try {
                Integer.parseInt(col);
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("That's not an integer!");
                System.out.print("Enter the column as an integer (1 - 9): ");
            }
            col = stdin.readLine();
        }

        coordinates[0] = -1;
        coordinates[1] = Integer.parseInt(col);
        coordinates[2] = 3;
        return coordinates;
    }

    static int[] boardSelectionClearGrid() throws Exception {
        int[] coordinates = new int[3];

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Choose area of the grid you want to clear");
        System.out.println(" 1 2 3 \n 4 5 6 \n 7 8 9 \n");
        System.out.print("Enter the grid as an integer (1 - 9): ");
        String grid = stdin.readLine();

        while (true) {
            if (grid.equalsIgnoreCase("exit")) {
                return new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            }
            try {
                Integer.parseInt(grid);
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("That's not an integer!");
                System.out.print("Enter the grid as an integer (1 - 9): ");
            }
            grid = stdin.readLine();
        }

        coordinates[0] = Integer.parseInt(grid);
        coordinates[1] = -1;
        coordinates[2] = 4;

        return coordinates;
    }
}

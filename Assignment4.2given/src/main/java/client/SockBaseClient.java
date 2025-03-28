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

        // Build the first request object just including the name
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
                System.out.println("Got a response: " + response.toString());

                Request.Builder req = Request.newBuilder();

                switch (response.getResponseType()) {
                    case GREETING:
                        System.out.println(response.getMessage());
                        req = chooseMenuRequest(req, response);
                        
                        break;
                    case START:
                        req = chooseInGameMenu(req, response);
                        
                        break;
                    case ERROR:
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
    
    static Request.Builder clearRequest(Request.Builder req) throws IOException {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        boolean selecting = true;
        String rowString = "-1";
        String columnString = "-1";
        int value = 0;
        
        while(selecting) {
            System.out.println("Do you want to clear\n1 - at an index\n2 - a row\n3 - a column\n4 - a grid\n5 - the board back to it's original state\nEnter a number 1-5:");
            String clearChoice = stdin.readLine();
            
            switch (clearChoice) {
                case "1":
                    System.out.println("Enter row (1-9)");
                    rowString = stdin.readLine();
                    System.out.println("Enter column (1-9)");
                    columnString = stdin.readLine();
                    value = 1;
                    selecting = false;
                    
                    break;
                case "2":
                    System.out.println("Enter row (1-9)");
                    rowString = stdin.readLine();
                    value = 2;
                    selecting = false;
                    
                    break;
                case "3":
                    System.out.println("Enter column (1-9)");
                    columnString = stdin.readLine();
                    value = 3;
                    selecting = false;
                    
                    break;
                case "4":
                    System.out.println("Enter index of grid");
                    rowString = stdin.readLine();
                    value = 4;
                    selecting = false;
                    
                    break;
                case "5":
                    System.out.println("Clearing board back to original");
                    value = 5;
                    selecting = false;
                    
                    break;
                default:
                    System.out.println("Please enter valid input");
            }
        }
        
        return req.setOperationType(Request.OperationType.CLEAR)
                .setRow(Integer.parseInt(rowString))
                .setColumn(Integer.parseInt(columnString))
                .setValue(value);
        
    }
    
    static Request.Builder chooseInGameMenu(Request.Builder req, Response response) throws IOException {
        int row;
        int column;
        int value;
        
        System.out.println(response.getBoard());
        System.out.println(response.getMenuoptions());
        
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        String menu_select = stdin.readLine();
        System.out.println("Selected: " + menu_select);
        
        try {
            row = Integer.parseInt(menu_select);
            
            System.out.println("Enter column (1-9)");
            column = Integer.parseInt(stdin.readLine());
            
            System.out.printf("What value do you want to insert at row: %d, column: %d?", row, column);
            value = Integer.parseInt(stdin.readLine());
            
            req.setOperationType(Request.OperationType.UPDATE)
                    .setRow(row)
                    .setColumn(column)
                    .setValue(value);
        }
        catch(NumberFormatException e) {
            if(menu_select.equals("c")) {
                System.out.println("Clear board");
                req = clearRequest(req);
            }
            else if(menu_select.equals("r")) {
                System.out.println("New board");
                req.setOperationType(Request.OperationType.CLEAR)
                        .setRow(-1)
                        .setColumn(-1)
                        .setValue(6);
            }
        }
        
        return req;
        
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
            System.out.println(menu_select);
            switch (menu_select) {
                // needs to include the other requests
                case "1":
                    req.setOperationType(Request.OperationType.LEADERBOARD);
                    return req;
                case "2":
                    req.setOperationType(Request.OperationType.START)
                            .setDifficulty(getDifficulty());
                    System.out.println(req.isInitialized());
                    return req;
                case "3":
                    req.setOperationType(Request.OperationType.QUIT);
                    return req;
                default:
                    System.out.println("\nNot a valid choice, please choose again");
                    break;
            }
        }
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

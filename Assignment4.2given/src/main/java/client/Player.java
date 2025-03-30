package client;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Class: Player 
 * Description: Class that represents a Player, I only used it in my Client 
 * to sort the LeaderBoard list
 * You can change this class, decide to use it or not to use it, up to you.
 */

public class Player implements Comparable<Player> {

    private int wins;
    private final String name;
    private int logins;
    private int points;

    // constructor, getters, setters
    public Player(String name, int wins, int logins, int points){
      this.wins = wins;
      this.name = name;
      this.logins = logins;
      this.points = points;
    }

    public int getWins(){
      return wins;
    }
    public void setWins(int wins){this.wins = wins;}
    public void increaseWin() {wins++;}
    
    public String getName() {return name;}
    
    public int getLogins() {return logins;}
    public void setLogins(int logins) {this.logins = logins;}
    public void increaseLogin() {this.logins++;}
    
    public int getPoints() {
        return points;
    }
    
    public void setPoints(int points) {
        this.points = points;
    }
    
    // override equals and hashCode
    @Override
    public int compareTo(Player player) {
        return player.getWins() - this.wins;
    }

    @Override
       public String toString() {
            return ("\n" +this.wins + ": " + this.name);
       }
}
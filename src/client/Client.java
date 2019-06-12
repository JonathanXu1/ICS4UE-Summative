package client;

import client.gameUi.BottomComponent;
import client.gameUi.DebugComponent;
import client.gameUi.GameComponent;
import client.gameUi.InventoryComponent;
import client.gameUi.MinimapComponent;
import client.gameUi.PauseComponent;
import client.map.*;
import client.sound.*;
import client.ui.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;

/*
Here is how the messages work.
First, the server must send a message to the client. The client immediately deciphers and sends it's own message, but it is
limited by waiting for the server to send a message again. However, in the menu this is irrelevant
If a server is trying to send two messages, then both can be recieved as the server is limiting here. Essentially, what will
occur is the client sending an output that does not reach anyone, which is perfectly fine.
 */

/**
 * Client.java
 * This is
 *
 * @author Will Jeong
 * @version 1.0
 * @since 2019-04-17
 */

public class Client extends JFrame implements WindowListener {
   private String thisClass = "Summoner";//Turn into an array or arraylist when people are able to select unique classes. Right now all are the same.
   //Finds memory usage before program starts
   Runtime runtime = Runtime.getRuntime();
   double maxMem = runtime.maxMemory();
   double usedMem;

   // Networking
   private Socket socket;
   private BufferedReader input;
   private PrintWriter output;
   private int connectionState = 0; //-1 means unable to connect, 0 means trying to connect, 1 means connected
   private String serverName;
   private String serverPassword;
   private boolean receivedOnce;//Determines if a message was received

   // Screen stuff
   private final int DESIRED_X = 1600;
   private final int DESIRED_Y = 900;
   private int[] xyAdjust = new int[2];
   private int MAX_Y, MAX_X;
   private double INTRO_SCALING;
   private int[] mouseState = new int[3];

   // Assets
   private soundEffectManager soundEffect = new soundEffectManager();
   private Clock time = new Clock(30);

   // Ui stuff
   private CustomMouseAdapter myMouseAdapter;
   private CustomKeyListener myKeyListener = new CustomKeyListener(this);
   private MenuPanel[] menuPanels = new MenuPanel[7];
   private IntermediatePanel intermediatePanel;
   private final String[] PANEL_NAMES = {"LOGIN_PANEL", "INTRO_PANEL", "MAIN_PANEL", "CREATE_PANEL", "JOIN_PANEL", "INSTRUCTION_PANEL", "WAITING_PANEL", "INTERMEDIATE_PANEL"};
   private CardLayout cardLayout = new CardLayout(5, 5);
   private JPanel mainContainer = new JPanel(cardLayout);
   private int currentPanel, nextPanel; // 0 by default
   private boolean keyPressed = false;
   private char lastKeyTyped;


   // Game states
   private ArrayList<User> onlineList = new ArrayList<User>();
   private Player[] players;
   private User myUser;
   private Player myPlayer;
   private String username, attemptedGameName, attemptedGamePassword;
   private boolean host, notifyReady, sendName, testGame, loading, logout, leaveGame, teamChosen, classChosen, gameBegin; // False by default
   private int[] errors = new int[4];
   private String errorMessages[] = {"Success", "This name is already taken", "Only letters and numbers are allowed", "This exceeds 15 characters", "This is blank", "Wrong username/password", "Game is full/has already begun", "Not enough players", "One team is empty", "Team is full", "Not all players have selected a team", "Not all players have selected a class"};
   private int myTeam;
   private String className;
   private int myPlayerID;
   private int frames, fps;

   // Game itself
   private FogMap fog;
   private Area darkFog;
   private Area lightFog;
   private ArrayList<Projectile> projectiles = new ArrayList<Projectile>();
   private ArrayList<AOE> aoes = new ArrayList<AOE>();
   private ArrayList<Player>[] teams = new ArrayList[2];
   private double mouseAngle;
   private int keyAngle;
   private boolean flashlightOn;
   private int MAP_WIDTH = 30000;
   private int MAP_HEIGHT = 20000;
   private boolean waitingForImage;
   private BufferedImage sheet;
   private boolean drawn = true;
   // Debugging
   private boolean testingBegin = false;
   //Graphics


   public Client() {
      super("Artifact of the Shadowmage");

      GraphicsEnvironment gEnv = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice screenDevice = gEnv.getDefaultScreenDevice();
      screenDevice.setFullScreenWindow(this);
      DisplayMode dm = new DisplayMode(DESIRED_X, DESIRED_Y, 32, 60);
      screenDevice.setDisplayMode(dm);
      validate();
      //Font set up
      try {
         GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
         ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, new File(".\\graphicFonts\\Quicksand-Regular.ttf")));
         ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, new File(".\\graphicFonts\\Quicksand-Bold.ttf")));
         ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, new File(".\\graphicFonts\\Quicksand-Light.ttf")));
         ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, new File(".\\graphicFonts\\Quicksand-Medium.ttf")));
         ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, new File(".\\graphicFonts\\Akura Popo.ttf")));
      } catch (IOException | FontFormatException e) {
         System.out.println("Font not available");
         e.printStackTrace();
      }

      // Display set up
      MAX_X = DESIRED_X;
      MAX_Y = DESIRED_Y;
      this.setSize(MAX_X, MAX_Y);
      this.setVisible(true);
      Dimension actualSize = this.getContentPane().getSize();
      MAX_X = actualSize.width;
      MAX_Y = actualSize.height;
      //System.out.println(MAX_X);
      //System.out.println(MAX_Y);
      this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      this.setLocationRelativeTo(null);
      this.setFocusable(true); //Necessary so that the buttons and stuff do not take over the focus
      //this.setExtendedState(JFrame.MAXIMIZED_BOTH);

      //Control set up (the mouse listeners are attached to the game panel)
      initializeScaling();
      this.addKeyListener(myKeyListener);
      this.setFocusTraversalKeysEnabled(false);
      int[] tempXy = {(int) (MAX_X / 2), (int) (MAX_Y / 2)};
      myMouseAdapter = new CustomMouseAdapter(this, tempXy);

      //Creating components
      MenuPanel.setParameters(MAX_X, MAX_Y, INTRO_SCALING, this);
      menuPanels[0] = new LoginPanel();
      menuPanels[1] = new IntroPanel();
      menuPanels[2] = new StartPanel();
      menuPanels[3] = new CreatePanel();
      menuPanels[4] = new JoinPanel();
      menuPanels[5] = new InstructionPanel();
      menuPanels[6] = new WaitingPanel();
      intermediatePanel = new IntermediatePanel(MAX_X, MAX_Y, this);
      //Adding to mainContainer cards
      mainContainer.setBackground(new Color(0, 0, 0));
      for (int i = 0; i < menuPanels.length; i++) {
         mainContainer.add(menuPanels[i], PANEL_NAMES[i]);
      }
      mainContainer.add(intermediatePanel, PANEL_NAMES[7]);
      this.add(mainContainer);
      cardLayout.show(mainContainer, PANEL_NAMES[0]);
      this.setVisible(true);//Must be called again so that it appears visible
      this.addKeyListener(myKeyListener);
      this.addWindowListener(this);

      // Setting up fog (should be moved soon TM)
      int[] xy = {300, 300};
      fog = new FogMap(xy, MAP_WIDTH, MAP_HEIGHT);
      // TODO: Set player spawn xy later

      //Variable set up
      teams[0] = new ArrayList<Player>();
      teams[1] = new ArrayList<Player>();
      Projectile.setXyAdjust(xyAdjust);
      AOE.setXyAdjust(xyAdjust);
      Status.setXyAdjust(xyAdjust);
   }

   public static void main(String[] args) {
      new Client().go();
   }

   public void go() {
      // Sets up frame rate timer
      new java.util.Timer().scheduleAtFixedRate(
              new java.util.TimerTask() {
                 @Override
                 public void run() {
                    fps = frames;
                    frames = 0;
                 }
              },
              1000,
              1000
      );

      while (true) {  //Main game loop
         if (time.getFramePassed()) {
            if (!gameBegin) {
               repaintPanels();
            } else {
               if (receivedOnce) {
                  repaintPanels();
               }
            }
         }
         if (connectionState < 1) {
            connect();
         } else {
            try {
               input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
               output = new PrintWriter(socket.getOutputStream());
            } catch (IOException e) {
               e.printStackTrace();
            }
            if (!gameBegin) {
               menuLogic();
            } else {
               //Finds memory usage after code execution
               usedMem = runtime.totalMemory() - runtime.freeMemory();
               gameLogic();
            }
         }
      }
   }

   public void menuLogic() {
      try {
         if (!waitingForImage) {
            if (input.ready()) {
               decipherMenuInput(input.readLine());
            }
         } else {
            waitForInput();
         }
         //Deal with output when going back through the menu
         if (logout) {
            output.println("B");//for back
            output.flush();
            username = null;
            logout = false;
         }
         if (leaveGame) {
            output.println("B");//for back
            output.flush();
            onlineList.clear();
            myUser.setTeam(9);
            leaveGame = false;
         }
         if (sendName) {
            sendName = false;
            if (username != null) {
               if (verifyString(username, 0)) {
                  output.println("U" + username);
                  output.flush();
                  waitForInput();
               }
               if (errors[0] != 0) {
                  menuPanels[currentPanel].setErrorUpdate("Error: " + errorMessages[errors[0]]);
                  soundEffect.playSound("error");
               }
            }
         }
         if (testGame) {
            testGame = false;
            boolean checkName = (verifyString(attemptedGameName, 1));
            boolean checkPass = (verifyString(attemptedGamePassword, 2));
            if ((checkName) && (checkPass)) {
               if (currentPanel == 3) {
                  output.println("C" + attemptedGameName + " " + attemptedGamePassword);
               } else {
                  output.println("J" + attemptedGameName + " " + attemptedGamePassword);
               }
               output.flush();
               waitForInput();
            } else {
               soundEffect.playSound("error");
               String totalErrorOutput = "";
               if ((errors[1] != 0)) {
                  totalErrorOutput += ("Name Error: " + errorMessages[errors[1]] + "_");
               }
               if ((errors[2] != 0)) {
                  totalErrorOutput += ("Password Error: " + errorMessages[errors[2]]);
               }
               menuPanels[currentPanel].setErrorUpdate(totalErrorOutput);
            }
         }
         if (notifyReady) {
            notifyReady = false;
            output.println("R");
            output.flush();

            waitForInput();
            if (errors[3] != 0) {
               menuPanels[currentPanel].setErrorUpdate("Error: " + errorMessages[errors[3]]);
               System.out.println("Error:" + errorMessages[errors[3]]);
               soundEffect.playSound("error");
            }
         }
         if (teamChosen) {
            teamChosen = false;
            output.println("E" + myTeam);//E for now, when testing is removed it will be T
            output.flush();
         }
         if (classChosen) {
            classChosen = true;
            output.println("Z" + className);//Refers to class chosen
            output.flush();
         }
         if (testingBegin) {
            username = Math.random() + "";
            myUser = new User(username);
            serverName = Integer.toString((int) (Math.random() * 10000));
            serverPassword = "0";
            System.out.println(serverName);
            output.println("T" + username + "," + serverName);//test
            output.flush();
            waitForInput();
            host = true;
            players = new Player[onlineList.size()];
            for (int i = 0; i < onlineList.size(); i++) {
               //TODO: Add class select here
               if (thisClass.equals("Archer") || thisClass.equals("Marksman") || thisClass.equals("SafeMarksman")) {
                  players[i] = new SafeMarksman(onlineList.get(i).getUsername());
               } else if (thisClass.equals("TimeMage")) {
                  players[i] = new TimeMage(onlineList.get(i).getUsername());
               } else if (thisClass.equals("Ghost")) {
                  players[i] = new Ghost(onlineList.get(i).getUsername());
               } else if (thisClass.equals("MobileSupport") || thisClass.equals("Support")) {
                  players[i] = new MobileSupport(onlineList.get(i).getUsername());
               } else if (thisClass.equals("Juggernaut")) {
                  players[i] = new Juggernaut(onlineList.get(i).getUsername());
               } else if (thisClass.equals("Summoner")) {
                  players[i] = new Summoner(onlineList.get(i).getUsername());
               } else {
                  players[i] = new SafeMarksman(onlineList.get(i).getUsername());
               }
               if (onlineList.get(i).getUsername().equals(myUser.getUsername())) {
                  myPlayer = players[i];
               }
               players[i].setTeam(onlineList.get(i).getTeam());
            }
            testingBegin = false;
            nextPanel = 6;
         }
         //TODO: Add class select here
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public void updateMouse(int[] state) {
      this.mouseState = state;
   }

   public void typeKey(char c) {
      keyPressed = true;
      lastKeyTyped = c;
      //System.out.println("type");
      if (currentPanel == 7) {
         if (c == 9) { // Tab key switches focus to game chat panel
            intermediatePanel.toggleMode();
         }
      }
   }

   public void gameLogic() {
      // TODO: Initialize map ONCE after game begin
      try {
         if (input.ready()) {
            decipherGameInput(input.readLine());
            if (drawn) {
               for (int i = 0; i < players.length; i++) {
                  if (players[i] != null) {
                     if (players[i].getTeam() == myTeam) {
                        fog.scout(players[i].getXy());
                     }
                  }
               }
               AffineTransform tx = new AffineTransform();
               tx.translate(xyAdjust[0], xyAdjust[1]);
          /*     darkFog = fog.getFog(2).createTransformedArea(tx);
               lightFog = fog.getExplored(2).createTransformedArea(tx);*/
               drawn = false;
            }
            // TODO: Update/improve when kameron is done
            int[] xyPos = new int[2]; //Scaled to the map
            xyPos[0] = myMouseAdapter.getDispXy()[0] + myPlayer.getXy()[0];
            xyPos[1] = myMouseAdapter.getDispXy()[1] + myPlayer.getXy()[1];
            mouseAngle = myMouseAdapter.getAngle();
            keyAngle = myKeyListener.getAngle();
            boolean[] spellsPressed = myKeyListener.getSpell();
            boolean[] leftRight = myMouseAdapter.getLeftRight();
            StringBuilder outputString = new StringBuilder();
            for (int i = 0; i < spellsPressed.length; i++) {
               if (spellsPressed[i]) {
                  outputString.append("S" + i + " ");
               }
            }
            if (keyAngle != -10) {
               outputString.append("M" + myPlayer.getDisp(keyAngle)[0] + "," + myPlayer.getDisp(keyAngle)[1] + " ");
            }
            if (mouseState[2] == 1) { // If mouse pressed
               if (leftRight[0]) {
                  outputString.append("A" + " ");
               }
               if (leftRight[1]) {
                  soundEffect.playSound("cow");
                  outputString.append("F" + " ");
               }
            }
            if (myKeyListener.getFlashlightOn()) {
               outputString.append("L" + mouseAngle + " ");
            }
            outputString.append("P" + xyPos[0] + "," + xyPos[1] + " ");
            outputString.append("R" + mouseAngle + " ");
            boolean walking = false;
            int positionIndex = -10;
            //Refreshes the players animation
            if (keyAngle != -10) {
               positionIndex = (int) Math.abs(2 - Math.ceil(keyAngle / 2.0)); //*4*,3, *2*,1,*0*,-1,*-2*,-3
               //2,1.5 1,0.5 0,-0.5 ,-1,-1.5, so rounding UP will give 2,1,0,-1
               //Adding one more gives 3,2,1,0, which refer to left, up,right,down
               walking = true;
            } else {
               if (mouseState[2] == 1) {
                  positionIndex = (int) Math.abs(2 - Math.ceil((int) (4 * (mouseAngle / Math.PI)) / 2.0));
               }
            }
            if (positionIndex != -10) {
               outputString.append("W" + positionIndex + " ");//TODO: make this event driven
            }
            if (!outputString.toString().trim().isEmpty()) {
               output.println(outputString.toString().trim());
               output.flush();
            }

            //Update positions:
         }
      } catch (
              IOException e) {
         e.printStackTrace();
      }
   }

   public void waitForInput() {
      boolean inputReady = false;
      try {
         while (!inputReady) {
            if (input.ready()) {
               inputReady = true;
               if (!gameBegin) {
                  if (!waitingForImage) {
                     decipherMenuInput(input.readLine().trim());
                  } else {
                     sheet = ImageIO.read(socket.getInputStream());
                     waitingForImage = false;
                     nextPanel = 7;//Sends to the game screen
                     gameBegin = true;
                  }
               }
            }
         }
      } catch (
              IOException e) {
         System.out.println("Lost connection");
      }

   }

   public boolean isParsable(char input) {
      try {
         int test = Integer.parseInt(input + "");
         return (true);
      } catch (NumberFormatException e) {
         return (false);
      }
   }

   public boolean verifyString(String testString, int errorIndex) {
      errors[errorIndex] = 0;
      if (testString.length() < 15) {
         if (testString.isEmpty()) {
            errors[errorIndex] = 4;
         } else {
            for (int i = 0; i < testString.length(); i++) {
               if (!letterOrNumber(testString.charAt(i))) {
                  errors[errorIndex] = 2;
               }
            }
         }
      } else {
         errors[errorIndex] = 3;
      }
      if (errors[errorIndex] == 0) {
         return true;
      } else {
         return false;
      }
   }

   public boolean letterOrNumber(char letter) {
      if (((letter >= 97) && (letter <= 122)) || ((letter >= 65) && (letter <= 90)) || ((letter >= 48) && (letter <= 57))) {
         return true;
      } else {
         return false;
      }
   }

   public void decipherMenuInput(String input) {
      System.out.println(input);
      if (!input.contains("END")) {
         char initializer = input.charAt(0);
         input = input.substring(1);
         if (isParsable(initializer)) {
            if (currentPanel == 0) {
               errors[0] = Integer.parseInt(initializer + "");
               if (initializer == '0') {
                  //Start the opening here
/*
                  cardLayout.show(mainContainer, PANEL_NAMES[1]);
                  ((IntroPanel) (menuPanels[1])).go();
                  try {
                     Thread.sleep(3000);
                  } catch (Exception E) {
                  }
*/
                  cardLayout.show(mainContainer, PANEL_NAMES[2]);
                  nextPanel = 2;
               } else {
                  username = null;
               }
            } else if ((currentPanel == 3) || (currentPanel == 4)) {
               errors[1] = Integer.parseInt(initializer + "");
            } else if (currentPanel == 6) {
               if (initializer == '0') {
                  loading = true;
               } else {
                  errors[3] = Integer.parseInt(initializer + input);
               }
            }
         } else if (initializer == 'A') {
            String[] allPlayers = input.split(" ", -1);
            myUser = new User(username);
            for (String aPlayer : allPlayers) {
               if ((testingBegin) && (myUser.getUsername().equals(aPlayer.substring(1)))) {
                  if (aPlayer.charAt(0) != '9') {
                     myUser.setTeam(Integer.parseInt(aPlayer.charAt(0) + ""));
                  }
                  onlineList.add(myUser);
               } else {
                  User tempUser = new User(aPlayer.substring(1));
                  if (aPlayer.charAt(0) != '9') {
                     tempUser.setTeam(Integer.parseInt(aPlayer.charAt(0) + ""));
                  }
                  onlineList.add(tempUser);
               }
            }
            nextPanel = 6;
            if (currentPanel == 3) {
               host = true;
            }
         } else if (initializer == 'N') {
            onlineList.add(new User(input));
         } else if (initializer == 'X') {
            for (int i = 0; i < onlineList.size(); i++) {
               if (onlineList.get(i).getUsername().equals(input)) {
                  onlineList.remove(i);
               }
            }
         } else if (initializer == 'B') {
            waitingForImage = true;
            players = new Player[onlineList.size()];
            input = input.trim();
            String[] classes = input.split(" ", -1);
            for (int i = 0; i < onlineList.size(); i++) {
               thisClass = classes[i];
               //TODO: Add class stuff here;
               if (thisClass.equals("Archer") || thisClass.equals("Marksman") || thisClass.equals("SafeMarksman")) {
                  players[i] = new SafeMarksman(onlineList.get(i).getUsername());
               } else if (thisClass.equals("TimeMage")) {
                  players[i] = new TimeMage(onlineList.get(i).getUsername());
               } else if (thisClass.equals("Ghost")) {
                  players[i] = new Ghost(onlineList.get(i).getUsername());
               } else if (thisClass.equals("MobileSupport") || thisClass.equals("Support")) {
                  players[i] = new MobileSupport(onlineList.get(i).getUsername());
               } else if (thisClass.equals("Juggernaut")) {
                  players[i] = new Juggernaut(onlineList.get(i).getUsername());
               } else if (thisClass.equals("Summoner")) {
                  players[i] = new Summoner(onlineList.get(i).getUsername());
               } else {//TESTING MODE ONLY
                  players[i] = new SafeMarksman(onlineList.get(i).getUsername());
               }
               if (onlineList.get(i).getUsername().equals(myUser.getUsername())) {
                  myPlayer = players[i];
                  myPlayerID = i;
               }
               try {
                  teams[0].add(players[i]);
                  teams[onlineList.get(i).getTeam()].add(players[i]);
                  players[i].setTeam(onlineList.get(i).getTeam());
               } catch (Exception e) {
                  teams[0].add(players[i]);
                  players[i].setTeam(0);
                  System.out.println("Testing mode error");
               }
            }
         } else if (initializer == 'P') { //Then leave the game
            onlineList.clear();
            nextPanel = 2;
         } else if (initializer == 'E') { //This is similar to when E was sent, it is for switching teams
            for (int i = 0; i < onlineList.size(); i++) {
               if (onlineList.get(i).getUsername().equals(input.substring(1))) {
                  onlineList.get(i).setTeam(Integer.parseInt(input.charAt(0) + ""));
               }
            }
         }
      }
   }

   public void decipherGameInput(String input) {
      if ((!input.contains("END")) && (!input.contains("FINAL"))) {
         if (!receivedOnce) {
            receivedOnce = true;
         }
         projectiles.clear();
         aoes.clear();
         System.out.println(input);
         String[] firstSplit = input.split(" ", -1);
         for (String firstInput : firstSplit) {
            char initializer = firstInput.charAt(0);
            firstInput = firstInput.substring(1);
            String[] secondSplit = firstInput.split(",", -1);
            if (secondSplit.length > 0) {
               if (initializer == 'P') {
                  updatePlayer(secondSplit);
               } else if (initializer == 'O') {
                  updateOthers(secondSplit);
               } else if (initializer == 'D') {
                  players[Integer.parseInt(secondSplit[0])] = null;
               } else if (initializer == 'R') {
                  projectiles.add(new Projectile(Integer.parseInt(secondSplit[0]), (int) (Integer.parseInt(secondSplit[1])), (int) (Integer.parseInt(secondSplit[2]))));
               } else if (initializer == 'E') {
                  int id = Integer.parseInt(secondSplit[0]);
                  if ((id != 4) && (id != 14)) {
                     aoes.add(new AOE(id, (int) (Integer.parseInt(secondSplit[1])), (int) (Integer.parseInt(secondSplit[2])), (int) (Integer.parseInt(secondSplit[3]))));
                  } else {
                     int[][] points = new int[2][4];
                     for (int m = 0; m < 2; m++) {
                        for (int n = 0; n < 4; n++) {
                           points[m][n] = (int) (Integer.parseInt(secondSplit[1 + m * 4 + n]));
                        }
                     }
                     if (id == 4) {
                        aoes.add(new TimeMageAOE(id, points));
                     } else {
                        aoes.add(new AutoAOE(id, points));
                     }
                  }
               } else if (initializer == 'S') {//Statuses now, use a different letter for spell using setspell//Set the spell of the appropriate player to the correct one using setSpell
                  int id = Integer.parseInt(secondSplit[0]);
                  Player player = players[Integer.parseInt(secondSplit[1])];
                  player.clearStatuses();
                  if (id == 2) {
                     player.addStatus(new GhostE(Integer.parseInt(secondSplit[2]), Integer.parseInt(secondSplit[3])));
                  } else if (id == 3) {
                     player.addStatus(new GhostPassive(Integer.parseInt(secondSplit[2])));
                  } else if (id == 0) {
                     player.addStatus(new DamageBuff());
                  } else if (id == 1) {
                     //player.addStatus(new Dead());
                     player.setDead(true);
                  } else if (id == 4) {
                     //player.addStatus(new Illuminated());//Talk with will
                  } else if (id == 5) {
                     //player.addStatus(new Invisible());
                     player.setInvisible(true);
                  } else if (id == 8) {
                     player.addStatus(new MSBuff());
                  } else if (id == 9) {
                     player.addStatus(new ReduceDamage());
                  } else if (id == 10) {
                     //player.addStatus(new Uncollidable());
                     player.setUncollidable(true);
                  } else if (id == 11) {
                     player.addStatus(new Unstoppable());
                  } else if (id == 12) {
                     player.addStatus(new Stun());
                  } else if (id == 13) {
                     player.addStatus(new Shielded());
                  }
               } else if (initializer == 'W') { //Walking
                  players[Integer.parseInt(secondSplit[0])].setMovementIndex(Integer.parseInt(secondSplit[1]), Boolean.parseBoolean(secondSplit[2]));
               } else if (initializer == 'L') {// Flash light
                  players[Integer.parseInt(secondSplit[0])].setFlashlightOn(true);//Resets the flashlight
                  for (int i = 2; i < Integer.parseInt(secondSplit[1]) * 2 + 2; i += 2) { //Parses all the points
                     players[Integer.parseInt(secondSplit[0])].setFlashlightPoint(Integer.parseInt(secondSplit[i]), Integer.parseInt(secondSplit[i + 1]));
                  }
               } else if (initializer == 'C') { //Message in
                  boolean isFriendly = false;
                  for (Player player : teams[myTeam]) { // Checks to see if username belongs to a player in 1st team
                     if (player.getUsername().equals(secondSplit[0])) {
                        isFriendly = true;
                     }
                  }
                  intermediatePanel.messageIn(secondSplit[0], secondSplit[1], isFriendly);
               }
            }
         }
      }
   }

   public void updatePlayer(String[] data) {
      int playerID = Integer.parseInt(data[0]);
      players[playerID].setXy(Integer.parseInt(data[1]), Integer.parseInt(data[2]));
      players[playerID].setHealth(Integer.parseInt(data[3]));
      players[playerID].setMaxHealth(Integer.parseInt(data[4]));
      players[playerID].setAttack(Integer.parseInt(data[5]));
      players[playerID].setMobility(Integer.parseInt(data[6]));
      players[playerID].setRange(Integer.parseInt(data[7]));
      players[playerID].setArtifact(Boolean.parseBoolean(data[8]));
      players[playerID].setGold(Integer.parseInt(data[9]));
      for (int j = 10; j < 13; j++) {
         players[playerID].setSpellPercent(Integer.parseInt(data[j]), j - 10);
      }
      players[playerID].setDamaged(Boolean.parseBoolean(data[13]));
      players[playerID].setIlluminated(Boolean.parseBoolean(data[14]));
      /*
      for (int j = 16; j < 16 + Integer.parseInt(data[15]); j++) {
         players[playerID].addStatus(Integer.parseInt(data[j]));
      }*/
      //Turn off flashlight
      players[playerID].setFlashlightOn(false);
   }

   public void updateOthers(String[] data) {
      int playerID = Integer.parseInt(data[0]);
      players[playerID].setFlashlightOn(false);
      players[playerID].setXy(Integer.parseInt(data[1]), Integer.parseInt(data[2]));
      players[playerID].setHealth(Integer.parseInt(data[3]));
      players[playerID].setMaxHealth(Integer.parseInt(data[4]));
      players[playerID].setArtifact(Boolean.parseBoolean(data[5]));
      players[playerID].setDamaged(Boolean.parseBoolean(data[6]));
      players[playerID].setIlluminated(Boolean.parseBoolean(data[7]));
      /*
      for (int j = 9; j < 9 + Integer.parseInt(data[8]); j++) {
         players[playerID].addStatus(Integer.parseInt(data[j]));
      }*/
   }

   public void repaintPanels() {
      if (currentPanel != nextPanel) {
         System.out.println("C" + currentPanel);
         System.out.println("V" + nextPanel);
         menuPanels[currentPanel].setErrorUpdate("");
         currentPanel = nextPanel;
         cardLayout.show(mainContainer, PANEL_NAMES[currentPanel]);
      }
      if (currentPanel != 7) {
         menuPanels[currentPanel].repaint();
      } else {
         intermediatePanel.repaintReal();
      }
   }

   public void connect() {
      try {
         socket = new Socket("localhost", 5002);//localhost
         System.out.println("Successfully connected");
         connectionState = 1;
      } catch (Exception e) {
         System.out.println("Unable to connect");
         connectionState = -1;
      }
   }

   public void initializeScaling() {
      int BG_Y = 1198;
      int BG_X = 1800;
      if ((1.0 * MAX_Y / MAX_X) > (1.0 * BG_Y / BG_X)) { //Fit bg to height
         INTRO_SCALING = 1.0 * MAX_Y / BG_Y;
      } else {
         INTRO_SCALING = 1.0 * MAX_X / BG_X;
      }
   }

   public void windowClosing(WindowEvent e) {
      dispose();
      try {
         output.println("X");
         output.flush();
         System.out.println("X");
      } catch (Exception E) {
         System.out.println("Not connected");
      }
      System.exit(0);
   }

   public void windowOpened(WindowEvent e) {
   }

   public void windowActivated(WindowEvent e) {
   }

   public void windowIconified(WindowEvent e) {
   }

   public void windowDeiconified(WindowEvent e) {
   }

   public void windowDeactivated(WindowEvent e) {
   }

   public void windowClosed(WindowEvent e) {
   }


   //Booleans to clients
   public void leaveGame() {
      leaveGame = true;
   }

   public void logout() {
      logout = true;
   }

   public void testingBegin() {
      testingBegin = true;
   }

   public void ready() {
      notifyReady = true;
   }

   //Tested input to clients
   public void testGame(String attemptedGameName, String attemptedGamePassword) {
      if (!testGame) {
         this.attemptedGameName = attemptedGameName;
         this.attemptedGamePassword = attemptedGamePassword;
         serverName = this.attemptedGameName;
         serverPassword = this.attemptedGamePassword;
         testGame = true;
      }
   }

   public void testName(String username) {
      if (!sendName) {
         this.username = username;
         sendName = true;
      }
   }

   //Sets the teams
   public void setTeam(int myTeam) {
      this.myTeam = myTeam;
      teamChosen = true;
   }


   public void setNextPanel(int nextPanel) {
      this.nextPanel = nextPanel;
   }

   public void setClassName(String className) {
      this.className = className;
      classChosen = true;
   }

   // Chat methods
   public void sendMessage(String message, int mode) {
      output.println("C" + mode + "," + message);
      output.flush();
   }

   //Info to panels
   public int getConnectionState() {
      return (connectionState);
   }

   public boolean getHost() {
      return (host);
   }

   public boolean getLoading() {
      return (loading);
   }

   public ArrayList<User> getOnlineList() {
      return (onlineList);
   }

   public int[] getMouseState() {
      return (mouseState);
   }

   public String getGameName() {
      return (serverName);
   }

   public String getGamePassword() {
      return (serverPassword);
   }

   public class GamePanel extends MenuPanel {//State=7
      private Graphics2D g2;
      private boolean generateGraphics = true;
      private int[] midXy = new int[2];
      private Rectangle drawArea;
      private final Font MAIN_FONT = super.getFont("main");
      //Game components
      private GameComponent[] allComponents;
      private PauseComponent pauseComponent;
      private int MAX_GAME_X, MAX_GAME_Y;
      private Area darkness;
      private boolean pause = false;

      public GamePanel() {
         this.setBackground(Color.black);
         this.setLayout(null); //Necessary so that the buttons can be placed in the correct location
         this.addMouseListener(myMouseAdapter);
         this.addMouseWheelListener(myMouseAdapter);
         this.addMouseMotionListener(myMouseAdapter);
         MAX_GAME_X = this.getWidth();
         MAX_GAME_Y = this.getHeight();
         GameComponent.initializeSize(MAX_GAME_X, MAX_GAME_Y);
         allComponents = new GameComponent[4];
         pauseComponent = new PauseComponent(800, 500, super.getClient());
         pauseComponent.setBounds(MAX_GAME_X / 2 - 400, MAX_GAME_Y / 2 - 250, 800, 500);
         this.add(pauseComponent);

         this.setDoubleBuffered(true);
         this.setVisible(true);
      }

      @Override
      public void paintComponent(Graphics g) {
         super.paintComponent(g);
         g2 = (Graphics2D) g;
         if ((currentPanel == 7) && (generateGraphics)) {
            allComponents[0] = new BottomComponent(myPlayer);
            allComponents[1] = new MinimapComponent(fog, players, myPlayerID);
            allComponents[2] = new InventoryComponent();
            allComponents[3] = new DebugComponent();
            midXy[0] = (MAX_X / 2);
            midXy[1] = (MAX_Y / 2);
            for (Player currentPlayer : players) {
               currentPlayer.setCenterXy(midXy);
            }
            g2.setFont(MAIN_FONT);
            generateGraphics = false;
            drawArea = new Rectangle(0, 0, MAX_X, MAX_Y);
            darkness = new Area(new Rectangle(0, 0, (MAX_GAME_X), (MAX_GAME_Y)));
         }
         if (drawArea != null) {
            resetXyAdjust();
            g2.clip(drawArea);
            g2.setFont(MAIN_FONT);

            //Map
            g2.drawImage(sheet, xyAdjust[0], xyAdjust[1], MAP_WIDTH, MAP_HEIGHT, null);
            g2.setColor(Color.black);
            //Game player
            resetXyAdjust();

            for (Player currentPlayer : players) {
               if (currentPlayer != null) {
                  currentPlayer.translateFlashlight(xyAdjust);
                  if (currentPlayer.getFlashlightOn()) {
                     darkness.subtract(new Area(currentPlayer.getFlashlightBeam()));
                  }
               }
            }

            for (int i = 0; i < aoes.size(); i++) {
               if (aoes.get(i).getID() == 0) {
                  darkness.subtract(aoes.get(i).getArea());
               }
            }
            int[] xP = {(int) (100), (int) (200), (int) (300), (int) (400), (int) (500)};
             int[] yP = {(int) (100), (int) (200), (int) (200), (int) (100), 0};
            Polygon test = new Polygon(xP, yP, 5);
            test.translate(xyAdjust[0], xyAdjust[1]);
            g2.setColor(Color.black);
            g2.fillPolygon(test);
            g2.fillRect((int) (300) + xyAdjust[0], (int) (300) + xyAdjust[1], (int) (100), (int) (100));

            g2.setColor(new Color(0, 0, 0, 150));
            g2.fill(darkness);
            for (Player currentPlayer : players) {
               if (currentPlayer != null) {
                  if ((currentPlayer.getTeam() == myTeam) || (currentPlayer.getIlluminated())) {
                     currentPlayer.draw(g2, myPlayer);
                  }
               }
            }

            // Updating fog
            resetXyAdjust();

            for (int i = 0; i < players.length; i++) {
               if (players[i] != null) {
                  if (players[i].getTeam() == myTeam) {
                     fog.scout(players[i].getXy());
                  }
               }
            }

            //Creating shapes

            //Draws fog
         /*   g2.setColor(Color.black); //Unexplored
            g2.fill(darkFog);
            g2.setColor(new Color(0, 0, 0, 128)); //Previously explored
            g2.fill(lightFog);
            drawn = true;*/
            // Draws projectiles and AOEs
            for (int i = 0; i < projectiles.size(); i++) {
               projectiles.get(i).draw(g2);
            }
            for (int i = 0; i < aoes.size(); i++) {
               aoes.get(i).draw(g2);
            }
            resetXyAdjust();
            for (int i = 0; i < players.length; i++) {
               for (int j = 0; j < players[i].getStatuses().size(); j++) {
                  players[i].getStatuses().get(j).draw(g2, players[i].getX(), players[i].getY(), j);
               }
            }
            //draw all components

            ((DebugComponent) (allComponents[3])).update(fps, mouseState, lastKeyTyped, usedMem, maxMem);
            if (keyPressed) {
               if (lastKeyTyped == 27) { // Esc key
                  pause = !pause;
                  pauseComponent.setVisible(pause);
                  if (pause) {
                     pauseComponent.requestFocus();
                     System.out.println("Pause");
                  }
               } else if (lastKeyTyped == 8) { // Back key
                  ((DebugComponent) (allComponents[3])).toggle();
                  System.out.println("Debug mode");
               } else if ((lastKeyTyped == 99) || (lastKeyTyped == 67)) { //C or c
                  ((InventoryComponent) (allComponents[2])).toggle();
               }
               keyPressed = false;
            }
            for (GameComponent gameComponent : allComponents) {
               gameComponent.draw(g2);
            }
         }
         darkness = new Area(new Rectangle(0, 0, (MAX_GAME_X), (MAX_GAME_Y)));
         frames++;
      }

      public void resetXyAdjust() {
         xyAdjust[0] = (int) (midXy[0] - myPlayer.getXy()[0]);
         xyAdjust[1] = (int) (midXy[1] - myPlayer.getXy()[1]);
      }
   }
}
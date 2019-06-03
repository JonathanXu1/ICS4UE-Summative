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
import javax.swing.JFrame;
import javax.swing.JPanel;
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
   // Networking
   private Socket socket;
   private BufferedReader input;
   private PrintWriter output;
   private int connectionState = 0; //-1 means unable to connect, 0 means trying to connect, 1 means connected

   // Screen stuff
   private final int DESIRED_Y = 500;
   private final int DESIRED_X = 950;
   private int MAX_Y, MAX_X;
   private double SCALING, introScaling;
   private int[] xyAdjust = new int[2];
   private int[] centerXy = new int[2];
   private int[] mouseState = new int[3];

   // Assets
   private soundEffectManager soundEffect = new soundEffectManager();
   private Clock time = new Clock(20);

   // Ui stuff
   private CustomMouseAdapter myMouseAdapter;
   private CustomKeyListener myKeyListener = new CustomKeyListener(this);
   private GeneralPanel[] allPanels = new GeneralPanel[8];
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
   private boolean host, notifyReady, sendName, testGame, loading, logout, leaveGame, teamChosen, gameBegin; // False by default
   private int[] errors = new int[3];
   private String errorMessages[] = {"Success", "This name is already taken", "Only letters and numbers are allowed", "This exceeds 15 characters", "This is blank", "Wrong username/password", "Game is full/has already begun"};
   private int myTeam; //TODO: make better way
   private int myPlayerID;
   private int frames, fps;

   // Game itself
   private FogMap fog;
   private ArrayList<Projectile> projectiles = new ArrayList<Projectile>();
   private ArrayList<AOE> aoes = new ArrayList<AOE>();

   // Debugging
   private boolean testingBegin = false;

   public Client() {
      super("Dark");

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
      MAX_X = (int) (GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().getWidth());
      MAX_Y = (int) (GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().getHeight());
      this.setSize(MAX_X, MAX_Y);
      this.setVisible(true);
      Dimension actualSize = this.getContentPane().getSize();
      MAX_X = actualSize.width;
      MAX_Y = actualSize.height;
      this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      this.setLocationRelativeTo(null);
      this.setFocusable(true); //Necessary so that the buttons and stuff do not take over the focus
      this.setExtendedState(JFrame.MAXIMIZED_BOTH);

      //Control set up (the mouse listeners are attached to the game panel)
      initializeScaling();
      this.addKeyListener(myKeyListener);
      int[] tempXy = {(int) (DESIRED_X * SCALING / 2), (int) (DESIRED_Y * SCALING / 2)};
      myMouseAdapter = new CustomMouseAdapter(this, SCALING, tempXy);

      //Creating components
      GeneralPanel.setParameters(MAX_X, MAX_Y, SCALING, introScaling, this);
      allPanels[0] = new LoginPanel();
      allPanels[1] = new IntroPanel();
      allPanels[2] = new StartPanel();
      allPanels[3] = new CreatePanel();
      allPanels[4] = new JoinPanel();
      allPanels[5] = new InstructionPanel();
      allPanels[6] = new WaitingPanel();
      allPanels[7] = new IntermediatePanel();
      //Adding to mainContainer cards
      mainContainer.setBackground(new Color(0, 0, 0));
      for (int i = 0; i < allPanels.length; i++) {
         mainContainer.add(allPanels[i], PANEL_NAMES[i]);
      }
      this.add(mainContainer);
      cardLayout.show(mainContainer, PANEL_NAMES[0]);
      this.setVisible(true);//Must be called again so that it appears visible
      this.addKeyListener(myKeyListener);
      this.addWindowListener(this);
      ((IntermediatePanel) (allPanels[7])).initializeSize(DESIRED_X, DESIRED_Y);

      // Setting up fog (should be moved soon TM)
      int[] xy = {300, 300};
      fog = new FogMap(xy, SCALING);
      // TODO: Set player spawn xy later
   }

   public static void main(String[] args) {
      new Client().go();
   }

   public void go() {
      while (true) {  //Main game loop
         if (time.getFramePassed()) {
            repaintPanels();
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
               gameLogic();
            }
         }
      }
   }

   public void menuLogic() {
      try {
         if (input.ready()) {
            decipherMenuInput(input.readLine());
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
                  allPanels[currentPanel].setErrorUpdate("Error: " + errorMessages[errors[0]]);
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
               allPanels[currentPanel].setErrorUpdate(totalErrorOutput);
            }
         }
         if (notifyReady) {
            notifyReady = false;
            output.println("R");
            output.flush();
            waitForInput();
         }
         if (teamChosen) {
            teamChosen = false;
            output.println("E" + myTeam);//E for now, when testing is removed it will be T
            output.flush();
         }
         if (testingBegin) {
            username = Math.random() + "";
            myUser = new User(username);
            output.println("T" + username);//test
            output.flush();
            waitForInput();
            host = true;
            players = new Player[onlineList.size()];
            for (int i = 0; i < onlineList.size(); i++) {
               //TODO: Add class select here
               players[i] = new SafeMarksman(onlineList.get(i).getUsername());
               if (onlineList.get(i).getUsername().equals(myUser.getUsername())) {
                  myPlayer = players[i];
               }
            }
            testingBegin = false;
            nextPanel = 6;
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public void updateMouse(int[] state) {
      this.mouseState = state;
   }
   public void typeKey(char c){
      keyPressed = true;
      lastKeyTyped = c;
   }

   public void gameLogic() {
      // TODO: Initialize map ONCE after game begin
      try {
         if (input.ready()) {
            decipherGameInput(input.readLine());
            int angleOfMovement = myKeyListener.getAngle();
            // TODO: Update/improve when kameron is done
            int[] xyPos = new int[2]; //Scaled to the map
            xyPos[0] = myMouseAdapter.getDispXy()[0] + myPlayer.getXy()[0];
            xyPos[1] = myMouseAdapter.getDispXy()[1] + myPlayer.getXy()[1];
            boolean[] spellsPressed = myKeyListener.getSpell();
            boolean[] leftRight = myMouseAdapter.getLeftRight();
            StringBuilder outputString = new StringBuilder();
            for (int i = 0; i < spellsPressed.length; i++) {
               if (spellsPressed[i]) {
                  outputString.append("S" + i);
               }
            }

            if ((spellsPressed[0]) || (spellsPressed[1]) || (spellsPressed[2])) {
               outputString.append(" "); //Add the separator
            }
            if (angleOfMovement != -10) {
               outputString.append("M" + myPlayer.getDisp(angleOfMovement)[0] + "," + myPlayer.getDisp(angleOfMovement)[1] + " ");
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
            outputString.append("P" + xyPos[0] + "," + xyPos[1] + " ");
            if (!outputString.toString().trim().isEmpty()) {
               output.println(outputString.toString().trim());
               output.flush();
            }
         }
      } catch (IOException e) {
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
                  decipherMenuInput(input.readLine().trim());
               } else {
                  decipherGameInput(input.readLine().trim());
               }
            }
         }
      } catch (IOException e) {
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
      char initializer = input.charAt(0);
      input = input.substring(1);
      if (isParsable(initializer)) {
         if (currentPanel == 0) {
            errors[0] = Integer.parseInt(initializer + "");
            if (initializer == '0') {
               //Start the opening here
/*
                  cardLayout.show(mainContainer, PANEL_NAMES[1]);
                  ((IntroPanel) (allPanels[1])).go();
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
               System.out.println("Starting Game");
               loading = true;
            } else {
               System.out.println("Unable to Start Game");
               soundEffect.playSound("error");
            }
         }
      } else if (initializer == 'A') {
         String[] allPlayers = input.split(" ", -1);
         myUser = new User(username);
         for (String aPlayer : allPlayers) {
            if ((testingBegin) && (myUser.getUsername().equals(aPlayer))) {
               onlineList.add(myUser);
            } else {
               onlineList.add(new User(aPlayer));
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
         players = new Player[onlineList.size()];
         for (int i = 0; i < onlineList.size(); i++) {
            players[i] = new SafeMarksman(onlineList.get(i).getUsername());
            if (onlineList.get(i).getUsername().equals(myUser.getUsername())) {
               myPlayer = players[i];
               myPlayerID = i;
            }
         }
         nextPanel = 7;//Sends to the game screen
         gameBegin = true;
      } else if (initializer == 'P') { //Then leave the game
         onlineList.clear();
         nextPanel = 2;
      }
   }

   public void decipherGameInput(String input) {
      projectiles.clear();
      aoes.clear();
      String[] firstSplit = input.split(" ", -1);
      for (String firstInput : firstSplit) {
         char initializer = firstInput.charAt(0);
         String[] secondSplit = firstInput.split(initializer + "", -1);
         for (String secondInput : secondSplit) {
            if (!secondInput.equals("")) {
               String[] thirdSplit = secondInput.split(",", -1);
               if (initializer == 'P') {
                  updatePlayer(thirdSplit);
               } else if (initializer == 'O') {
                  updateOthers(thirdSplit);
               } else if (initializer == 'D') {
                  players[Integer.parseInt(thirdSplit[0])] = null;
               } else if (initializer == 'R') {
                  projectiles.add(new Projectile(Integer.parseInt(thirdSplit[0]), (int) (Integer.parseInt(thirdSplit[1]) * SCALING + xyAdjust[0]), (int) (Integer.parseInt(thirdSplit[2]) * SCALING + xyAdjust[1])));
               } else if (initializer == 'E') {
                  aoes.add(new AOE(Integer.parseInt(thirdSplit[0]), (int) (Integer.parseInt(thirdSplit[1]) * SCALING + xyAdjust[0]), (int) (Integer.parseInt(thirdSplit[2]) * SCALING + xyAdjust[1]), (int) (Integer.parseInt(thirdSplit[3]) * SCALING)));
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
      players[playerID].setSpriteID(Integer.parseInt(data[10]));
      for (int j = 11; j < 14; j++) {
         players[playerID].setSpellPercent(Integer.parseInt(data[j]), j - 11);
      }
      players[playerID].setDamaged(Boolean.parseBoolean(data[14]));
      for (int j = 15; j < 15 + Integer.parseInt(data[15]); j++) {
         players[playerID].addStatus(Integer.parseInt(data[j]));
      }
   }

   public void updateOthers(String[] data) {
      int playerID = Integer.parseInt(data[0]);
      players[playerID].setXy(Integer.parseInt(data[1]), Integer.parseInt(data[2]));
      players[playerID].setHealth(Integer.parseInt(data[3]));
      players[playerID].setMaxHealth(Integer.parseInt(data[4]));
      players[playerID].setArtifact(Boolean.parseBoolean(data[5]));
      players[playerID].setSpriteID(Integer.parseInt(data[6]));
      players[playerID].setDamaged(Boolean.parseBoolean(data[7]));
      for (int j = 8; j < 8 + Integer.parseInt(data[8]); j++) {
         players[playerID].addStatus(Integer.parseInt(data[j]));
      }
   }

   public void repaintPanels() {
      if (currentPanel != nextPanel) {
         allPanels[currentPanel].setErrorUpdate("");
         currentPanel = nextPanel;
         cardLayout.show(mainContainer, PANEL_NAMES[currentPanel]);
      }
      if (currentPanel != 7) {
         allPanels[currentPanel].repaint();
      } else {
         ((IntermediatePanel) (allPanels[currentPanel])).repaintReal();
      }
      frames++;
   }

   public void connect() {
      try {
         socket = new Socket("localhost", 5001);
         System.out.println("Successfully connected");
         connectionState = 1;
      } catch (Exception e) {
         System.out.println("Unable to connect");
         connectionState = -1;
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

   public void initializeScaling() {
      if ((1.0 * MAX_Y / MAX_X) > (1.0 * DESIRED_Y / DESIRED_X)) { //
         SCALING = 1.0 * MAX_X / DESIRED_X;
      } else {
         SCALING = 1.0 * MAX_Y / DESIRED_Y;
      }
      int BG_Y = 1198;
      int BG_X = 1800;
      if ((1.0 * MAX_Y / MAX_X) > (1.0 * BG_Y / BG_X)) { //Fit bg to height
         introScaling = 1.0 * MAX_Y / BG_Y;
      } else {
         introScaling = 1.0 * MAX_X / BG_X;
      }
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


   /**
    * GamePanel.java
    * This is
    *
    * @author Will Jeong
    * @version 1.0
    * @since 2019-05-31
    */

   public class GamePanel extends GeneralPanel {//State=7
      private Graphics2D g2;
      private boolean generateGraphics = true;
      int[] midXy = new int[2];
      private Rectangle drawArea;
      private final Font MAIN_FONT = super.getFont("main");
      private BufferedImage sheet;
      private int[][] currentXy;
      //Game components
      private GameComponent[] allComponents = new GameComponent[5];
      private boolean menuCooldown  = true;

      public GamePanel() {
         this.setBackground(new Color(0, 0, 0));
         this.setLayout(null); //Necessary so that the buttons can be placed in the correct location
         this.setVisible(true);
         this.addMouseListener(myMouseAdapter);
         this.addMouseWheelListener(myMouseAdapter);
         this.addMouseMotionListener(myMouseAdapter);
         GameComponent.initializeSize(SCALING, DESIRED_X, DESIRED_Y);
         allComponents[0] = new PauseComponent();
         allComponents[1] = new BottomComponent();
         allComponents[2] = new MinimapComponent();
         allComponents[3] = new InventoryComponent();
         allComponents[4] = new DebugComponent();
         this.setDoubleBuffered(true);
      }

      @Override
      public void paintComponent(Graphics g) {

         if (players.length != 0) {
            currentXy = new int[players.length][2];
            for (int i = 0; i < players.length; i++) {
               if (players[i] != null) {
                  currentXy[i][0] = players[i].getXy()[0];
                  currentXy[i][1] = players[i].getXy()[1];
               }
            }
            xyAdjust[0] = (int) (centerXy[0] - currentXy[myPlayerID][0] * SCALING);
            xyAdjust[1] = (int) (centerXy[1] - currentXy[myPlayerID][1] * SCALING);
         }
         g2 = (Graphics2D) g;
         super.paintComponent(g2);
         if ((currentPanel == 7) && (generateGraphics)) {
            midXy[0] = (int) (DESIRED_X * SCALING / 2);
            midXy[1] = (int) (DESIRED_Y * SCALING / 2);
            for (Player currentPlayer : players) {
               currentPlayer.setScaling(SCALING);
               currentPlayer.setCenterXy(midXy);
            }
            g2.setFont(MAIN_FONT);
            generateGraphics = false;
            //Game set up
            centerXy[0] = (int) (DESIRED_X * SCALING / 2);
            centerXy[1] = (int) (DESIRED_Y * SCALING / 2);
            try {
               sheet = ImageIO.read(new File(".\\res\\Map.png"));
            } catch (IOException e) {
               System.out.println("Image not found");
            }
            drawArea = new Rectangle(0, 0, (int) (DESIRED_X * SCALING), (int) (DESIRED_Y * SCALING));
         }
         if (drawArea != null) {
            g2.clip(drawArea);
            g2.setFont(MAIN_FONT);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            //Map
            g2.drawImage(sheet, xyAdjust[0], xyAdjust[1], (int) (10000 * SCALING), (int) (10000 * SCALING), null);

            //Game player
            for (Player currentPlayer : players) {
               if (currentPlayer != null) {
                  currentPlayer.draw(g2, myPlayer.getXy());
               }
            }

            // Updating fog
            for (int i = 0; i < players.length; i++) {
               if (players[i] != null) {
                  // TODO: Separate by teams
                  fog.scout(currentXy[i]);
               }
            }

            //Creating shapes
            AffineTransform tx = new AffineTransform();
            tx.translate(xyAdjust[0], xyAdjust[1]);
            Area darkFog = fog.getFog().createTransformedArea(tx);
            Area lightFog = fog.getExplored().createTransformedArea(tx);

            //Draws fog
            g2.setColor(Color.black); //Unexplored
            g2.fill(darkFog);
            g2.setColor(new Color(0, 0, 0, 128)); //Previously explored
            g2.fill(lightFog);

            // Draws projectiles and AOEs
            for (int i = 0; i < projectiles.size(); i++) {
               projectiles.get(i).draw(g2);
            }
            for (int i = 0; i < aoes.size(); i++) {
               aoes.get(i).draw(g2);
            }
            if (keyPressed) {
               if(lastKeyTyped == 27){ // Esc key
                  ((PauseComponent) (allComponents[0])).toggle();
               } else if(lastKeyTyped == 8){ // Back key
                  ((DebugComponent) (allComponents[4])).toggle();
                  System.out.println("F1");
               }
               keyPressed = false;
            }
            ((BottomComponent) (allComponents[1])).setBothHealth(myPlayer.getHealth(), myPlayer.getMaxHealth());
            //draw all components
            for (GameComponent gameComponent : allComponents) {
               gameComponent.draw(g2);
            }
         }
         g2.dispose();
      }
   }
}

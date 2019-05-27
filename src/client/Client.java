package client;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
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
   private Socket socket;
   private BufferedReader input;
   private PrintWriter output;
   private String username;
   private boolean connected = false;
   private JPanel[] allPanels = new JPanel[8];
   private String[] panelNames = {"LOGIN_PANEL", "MAIN_PANEL", "CREATE_PANEL", "JOIN_PANEL", "WAITING_PANEL", "INTERMEDIATE_PANEL", "INSTRUCTION_PANEL","INTRO_PANEL"};
   private CustomMouseAdapter myMouseAdapter = new CustomMouseAdapter();
   private CustomKeyListener myKeyListener = new CustomKeyListener();
   private boolean sendName = false;
   private boolean testGame = false;
   private Font MAIN_FONT;
   //State legend:
   //0: Login panel, 1: Create/Join game, 2:Create gam   e, 3:Join game , 4:Waiting , 5:Game
   private int state = 0;
   private int newState = 0;
   private CardLayout cardLayout = new CardLayout(5, 5);
   private JPanel mainContainer = new JPanel(cardLayout);
   private String gameName;
   private String gamePassword;
   private String attemptedGameName;
   private String attemptedGamePassword;
   private boolean host = false;
   private boolean notifyReady = false;
   private ArrayList<Player> onlineList = new ArrayList<Player>();
   private GamePlayer[] gamePlayers;
   private Player myPlayer;
   private GamePlayer myGamePlayer;
   private boolean gameBegin;
   private String outputString;//This is what is outputted to the game
   private boolean loading = false;
   private int DESIRED_Y = 500;
   private int DESIRED_X = 950;
   private int MAX_Y;
   private int MAX_X;
   private double scaling;
   private Sector[][] sectors;
   private ArrayList<Integer> disconnectedPlayerID = new ArrayList<Integer>();
   private int[] errors = new int[3];
   private String errorMessages[] = {"Success", "This name is already taken", "Only letters and numbers are allowed", "This exceeds 15 characters", "This is blank", "Wrong username/password", "Game is full/has already begun"};
   private BufferedImage sheet;
   private boolean logout = false;
   private boolean leaveGame = false;

   public Client() {
      super("Dark");

      //Control set up (the mouse listeners are attached to the game panel)
      this.addKeyListener(myKeyListener);

      //Font set up
      try {
         GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
         ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, new File(".\\graphicFonts\\Quicksand-Regular.ttf")));
         ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, new File(".\\graphicFonts\\Quicksand-Bold.ttf")));
         ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, new File(".\\graphicFonts\\Quicksand-Light.ttf")));
         ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, new File(".\\graphicFonts\\Quicksand-Medium.ttf")));
      } catch (IOException | FontFormatException e) {
         System.out.print("Font not available");
      }


      //Basic set up
      MAX_X = (int) (GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().getWidth());
      MAX_Y = (int) (GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().getHeight());
      this.setSize(MAX_X, MAX_Y);
      this.setVisible(true);
      Dimension actualSize = this.getContentPane().getSize();
      this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      this.setLocationRelativeTo(null);
      this.setFocusable(true); //Necessary so that the buttons and stuff do not take over the focus
      this.setExtendedState(JFrame.MAXIMIZED_BOTH);
      //Creating components
      allPanels[5] = new IntermediatePanel();
      ((IntermediatePanel) (allPanels[5])).initializeScaling();//must be called before the rest of the fonts
      allPanels[0] = new LoginPanel();
      allPanels[1] = new MenuPanel();
      allPanels[2] = new CreatePanel();
      allPanels[3] = new JoinPanel();
      allPanels[4] = new WaitingPanel();
      allPanels[6] = new InstructionPanel();
      allPanels[7] = new IntroPanel();
      //Adding to mainContainer cards
      mainContainer.setBackground(new Color(0, 0, 0));
      for (int i = 0; i < allPanels.length; i++) {
         mainContainer.add(allPanels[i], panelNames[i]);
      }
      this.add(mainContainer);
      cardLayout.show(mainContainer, panelNames[0]);
      this.setVisible(true);//Must be called again so that it appears visible
      this.addKeyListener(myKeyListener);
      this.addWindowListener(this);
      ((IntermediatePanel) (allPanels[5])).initializeSize();
     // ((IntroPanel)(allPanels[7])).go();
   }

   public static void main(String[] args) {
      new Client().go();
   }

   public void go() {
      //Start the opening here

      connect();
      boolean inputReady = false;
      try {
         input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
         output = new PrintWriter(socket.getOutputStream());
         //Start with entering the name. This must be separated from the rest
         //Username successfully entered in
         while (connected) {
            //Otherwise, continue to send messages. The lines below are for when something is going to be sent
            if (!gameBegin) {
               //Recieves input if possible
               if (input.ready()) {
                  decipherInput(input.readLine());
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
                     if (errors[0] == 0) {
                        System.out.println("Valid username");
                     } else {
                        System.out.println("Error: " + errorMessages[errors[0]]);
                     }
                  }
               }
               if (testGame) {
                  testGame = false;
                  if ((verifyString(attemptedGameName, 1)) && (verifyString(attemptedGamePassword, 2))) {
                     if (state == 2) {
                        output.println("C" + attemptedGameName + " " + attemptedGamePassword);
                     } else {
                        output.println("J" + attemptedGameName + " " + attemptedGamePassword);
                     }
                     output.flush();
                     waitForInput();
                  }
                  System.out.println(errors[2] + " " + attemptedGameName + " " + attemptedGamePassword);
                  if ((errors[1] == 0) && (errors[2] == 0)) {
                     System.out.println("Valid game");
                  } else {
                     System.out.println("Error: " + errorMessages[errors[1]]);
                     System.out.println("Error: " + errorMessages[errors[2]]);
                  }
               }
               if (notifyReady) {
                  notifyReady = false;
                  output.println("R");
                  output.flush();
                  waitForInput();
               }
               repaintPanels();
            } else {
               if (input.ready()) {
                  decipherInput(input.readLine());//read input
                  //This is where everything is output. Output the key controls
                  //Always begin with clearing the outputString
                  //The output string contains all the information required for the server.
                  //I'm unsure if I should process some here, or just send all the raw data
                  //If the raw data was to be sent, the following should be sent: MAX_X/MAX_Y (only once),
                  //the x and y of the mouse, the relevant keyboard presses maybe? (not all)
                  outputString = "";
                  int angleOfMovement = myKeyListener.getAngle();
                  int[] xyPos = new int[2]; //Scaled to the map
                  if (myMouseAdapter.getPressed()) {
                     xyPos[0] = myMouseAdapter.getDispXy()[0] + myGamePlayer.getXy()[0];
                     xyPos[1] = myMouseAdapter.getDispXy()[1] + myGamePlayer.getXy()[1];
                  }
                  //Check to see if it can only reach within the boundaries of the JFrame. Make sure that this is true, otherwise you
                  //must add the mouse adapter to the JPanel.

                  boolean[] spellsPressed = myKeyListener.getSpell();
                  boolean[] leftRight = myMouseAdapter.getLeftRight();
                  StringBuilder outputString = new StringBuilder();
                  for (int i = 0; i < spellsPressed.length; i++) {
                     if (spellsPressed[i]) {
                        outputString.append("S" + i + "," + xyPos[0] + "," + xyPos[1]);
                     }
                  }
                  if ((spellsPressed[0]) || (spellsPressed[1]) || (spellsPressed[2])) {
                     outputString.append(" "); //Add the seperator
                  }
                  if (angleOfMovement != -10) {
                     outputString.append("M" + myGamePlayer.getDisp(angleOfMovement)[0] + "," + myGamePlayer.getDisp(angleOfMovement)[1]);
                  }
                  // outputString = angleOfMovement + " " + xyDisp[0] + " " + xyDisp[1] + " " + spellsPressed[0] + " " + spellsPressed[1] + " " + spellsPressed[2] + " " + leftRight[0] + " " + leftRight[1];//If it is -1, then the server will recognize to stop
                  if (!outputString.toString().isEmpty()) {
                     output.println(outputString);
                     output.flush();
                  }
                  repaintPanels();
               }
            }
         }
         //If a message is sent, wait until a response is received before doing anything
      } catch (IOException e) {
         System.out.println("Unable to read/write");
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

   public void waitForInput() {
      boolean inputReady = false;
      try {
         while (!inputReady) {
            if (input.ready()) {
               inputReady = true;
               decipherInput(input.readLine());
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

   public void decipherInput(String input) {
      //Hopefully, every message should have something
      //For the menu, numbers represent error/success, A represents add all (if you join),
      //N represents add one new player, and B represents begin the game
      //Remove the initializer
      input = input.trim();//in case something is wrong
      if (!gameBegin) {
         char initializer = input.charAt(0);
         input = input.substring(1);
         if (isParsable(initializer)) {
            if (state == 0) {
               errors[0] = Integer.parseInt(initializer + "");
               if (initializer == '0') {
                  newState = 1;
               } else {
                  username = null;
               }
            } else if ((state == 2) || (state == 3)) {
               if (initializer == '0') {
                  newState = 4;//Sends to a waiting room
                  gameName = attemptedGameName;
                  gamePassword = attemptedGamePassword;
                  myPlayer = new Player(username);//Sets the player
                  if (state == 2) {
                     host = true;
                     onlineList.add(myPlayer);
                  }
               }
               errors[1] = Integer.parseInt(initializer + "");
               /*
               else if (initializer == '1') {
                  if (state == 2) {
                     System.out.println("Game name in use");
                  } else {
                     System.out.println("Wrong username/password"); //Make this one print out state==5
                  }
               } else if (initializer == '2') {
                  System.out.println("Game is full");//Make this one print out state==6
               }
               */
            } else if (state == 4) {
               if (initializer == '0') {
                  System.out.println("Starting Game");
                  loading = true;
               } else {
                  System.out.println("Unable to Start Game");
               }
            }
         } else if (initializer == 'A') {
            String[] allPlayers = input.split(" ", -1);
            for (String aPlayer : allPlayers) {
               onlineList.add(new Player(aPlayer));
            }
         } else if (initializer == 'N') {
            onlineList.add(new Player(input));
         } else if (initializer == 'X') {
            for (int i = 0; i < onlineList.size(); i++) {
               if (onlineList.get(i).getUsername().equals(input)) {
                  System.out.println(onlineList.get(i).getUsername());
                  onlineList.remove(i);
               }
            }
         } else if (initializer == 'B') {
            gamePlayers = new GamePlayer[onlineList.size()];
            for (int i = 0; i < onlineList.size(); i++) {
               gamePlayers[i] = new TestClass(onlineList.get(i).getUsername());
               if (onlineList.get(i).getUsername().equals(myPlayer.getUsername())) {
                  myGamePlayer = gamePlayers[i];
               }
            }
            newState = 5;//Sends to the game screen
            gameBegin = true;
         } else if (initializer == 'P') { //Then leave the game
            onlineList.clear();
            newState = 1;
         }
      } else {
         String[] firstSplit = input.split(" ", -1);
         for (String firstInput : firstSplit) {
            char initializer = firstInput.charAt(0);
            String[] secondSplit = firstInput.split(initializer + "", -1);
            for (String secondInput : secondSplit) {
               if (!secondInput.equals("")) {
                  String[] thirdSplit = secondInput.split(",", -1);
                  if (initializer == 'P') {
                     //REPLACE THIS WITH A SET PLAYER METHOD.
                     int playerID = Integer.parseInt(thirdSplit[0]);
                     gamePlayers[playerID].setXy(Integer.parseInt(thirdSplit[1]), Integer.parseInt(thirdSplit[2]));
                     gamePlayers[playerID].setHealth(Integer.parseInt(thirdSplit[3]));
                     gamePlayers[playerID].setMaxHealth(Integer.parseInt(thirdSplit[4]));
                     gamePlayers[playerID].setAttack(Integer.parseInt(thirdSplit[5]));
                     gamePlayers[playerID].setMobility(Integer.parseInt(thirdSplit[6]));
                     gamePlayers[playerID].setRange(Integer.parseInt(thirdSplit[7]));
                     gamePlayers[playerID].setArtifact(Boolean.parseBoolean(thirdSplit[8]));
                     gamePlayers[playerID].setGold(Integer.parseInt(thirdSplit[9]));
                     gamePlayers[playerID].setSpriteID(Integer.parseInt(thirdSplit[10]));
                     for (int j = 11; j < 14; j++) {
                        gamePlayers[playerID].setSpellPercent(Integer.parseInt(thirdSplit[j]), j - 11);
                     }
                     gamePlayers[playerID].setDamaged(Boolean.parseBoolean(thirdSplit[14]));
                     for (int j = 15; j < 15 + Integer.parseInt(thirdSplit[15]); j++) {
                        gamePlayers[playerID].addStatus(Integer.parseInt(thirdSplit[j]));
                     }
                  } else if (initializer == 'O') {
                     //REPLACE THIS WITH A SET OTHERS METHOD.
                     int playerID = Integer.parseInt(thirdSplit[0]);
                     gamePlayers[playerID].setXy(Integer.parseInt(thirdSplit[1]), Integer.parseInt(thirdSplit[2]));
                     gamePlayers[playerID].setHealth(Integer.parseInt(thirdSplit[3]));
                     gamePlayers[playerID].setMaxHealth(Integer.parseInt(thirdSplit[4]));
                     gamePlayers[playerID].setArtifact(Boolean.parseBoolean(thirdSplit[5]));
                     gamePlayers[playerID].setSpriteID(Integer.parseInt(thirdSplit[6]));
                     gamePlayers[playerID].setDamaged(Boolean.parseBoolean(thirdSplit[7]));
                     for (int j = 8; j < 8 + Integer.parseInt(thirdSplit[8]); j++) {
                        gamePlayers[playerID].addStatus(Integer.parseInt(thirdSplit[j]));
                     }
                  } else if (initializer == 'X') {
                     gamePlayers[Integer.parseInt(thirdSplit[0])] = null;
                  }
               }
            }
         }
      }
   }

   public void repaintPanels() {
      if (state != newState) {
         state = newState;
         cardLayout.show(mainContainer, panelNames[state]);
      }
      if (state != 5) {
         allPanels[state].repaint();
      } else {
         ((IntermediatePanel) (allPanels[state])).repaintReal();
      }
   }

   public void connect() {
      try {
         socket = new Socket("localhost", 5001);
         System.out.println("Successfully connected");
         connected = true;
      } catch (Exception e) {
         System.out.println("Unable to connect");
      }
   }

   public void windowClosing(WindowEvent e) {
      output.println("X");
      output.flush();
      dispose();
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

   private class LoginPanel extends JPanel { //State=0
      private Graphics2D g2;
      private JTextField nameField = new JTextField(3);
      private CustomButton nameButton = new CustomButton("Click to test name");

      public LoginPanel() {
         //Setting up the size
         this.setPreferredSize(new Dimension(MAX_X, MAX_Y));
         //Basic username field
         nameButton.addActionListener((ActionEvent e) -> {
            if (!sendName) {
               if (!(nameField.getText().contains(" "))) {
                  username = nameField.getText();
                  sendName = true;
               } else {
                  System.out.println("Error: Spaces exist");
               }
            }
         });
         //sendName = true;
         nameField.setFont(MAIN_FONT);
         nameField.setBounds(MAX_X / 2 - (int) (37 * scaling), MAX_Y / 5, (int) (75 * scaling), (int) (15 * scaling));
         nameButton.setBounds(MAX_X / 2 - (int) (65 * scaling), MAX_Y * 3 / 10, (int) (130 * scaling), (int) (15 * scaling));
         this.add(nameField);
         this.add(nameButton);

         //Basic visuals
         this.setDoubleBuffered(true);
         this.setBackground(new Color(20, 20, 20));
         this.setLayout(null); //Necessary so that the buttons can be placed in the correct location
         this.setVisible(true);
      }

      @Override
      public void paintComponent(Graphics g) {
         g2 = (Graphics2D) g;
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         g2.setFont(MAIN_FONT);
         super.paintComponent(g);
         //this.requestFocusInWindow();// Removed, this interferes with the textboxes. See if this is truly necessary

         //Begin drawing

      }
   }

   private class MenuPanel extends JPanel {//State=1
      private Graphics2D g2;
      private CustomButton createButton = new CustomButton("Create game");
      private CustomButton joinButton = new CustomButton("Join game");
      private CustomButton instructionButton = new CustomButton("Instructions");
      private CustomButton backButton = new CustomButton("Back");

      public MenuPanel() {
         //Setting up the size
         this.setPreferredSize(new Dimension(MAX_X, MAX_Y));
         //Basic create and join server buttons
         createButton.addActionListener((ActionEvent e) -> {
            newState = 2;
         });
         createButton.setBounds(MAX_X / 2 - (int) (65 * scaling), (int) (MAX_Y * 3.0 / 10), (int) (130 * scaling), (int) (15 * scaling));
         this.add(createButton);

         joinButton.addActionListener((ActionEvent e) -> {
            newState = 3;
         });
         joinButton.setBounds(MAX_X / 2 - (int) (65 * scaling), (int) (MAX_Y * 4.5 / 10), (int) (130 * scaling), (int) (15 * scaling));
         this.add(joinButton);
         instructionButton.addActionListener((ActionEvent e) -> {
            newState = 6;//I added this later so I didn't want to move everything around
         });
         instructionButton.setBounds(MAX_X / 2 - (int) (65 * scaling), (int) (MAX_Y * 6.0 / 10), (int) (130 * scaling), (int) (15 * scaling));

         this.add(instructionButton);
         backButton.addActionListener((ActionEvent e) -> {
            newState = 0;
            logout = true;
         });
         backButton.setBounds(MAX_X / 2 - (int) (65 * scaling), (int) (MAX_Y * 7.5 / 10), (int) (130 * scaling), (int) (15 * scaling));
         this.add(backButton);

         //Basic visuals
         this.setDoubleBuffered(true);
         this.setBackground(new Color(20, 20, 20));
         this.setLayout(null); //Necessary so that the buttons can be placed in the correct location
         this.setVisible(true);
      }

      @Override
      public void paintComponent(Graphics g) {
         g2 = (Graphics2D) g;
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         g2.setFont(MAIN_FONT);
         super.paintComponent(g);
         //this.requestFocusInWindow(); Removed, this interferes with the textboxes. See if this is truly necessary
      }
   }

   private class CreatePanel extends JPanel { //State =2
      private Graphics2D g2;
      private boolean generateGraphics = true;
      private JTextField gameNameField = new JTextField(3);
      private JTextField gamePasswordField = new JTextField(3);
      private CustomButton testGameButton = new CustomButton("Confirm game");
      private CustomButton backButton = new CustomButton("Back");

      public CreatePanel() {
         //Setting up the size
         this.setPreferredSize(new Dimension(MAX_X, MAX_Y));
         //Basic create and join server buttons
         testGameButton.addActionListener((ActionEvent e) -> {
            if (!testGame) {
               attemptedGameName = gameNameField.getText();
               attemptedGamePassword = gamePasswordField.getText();
               testGame = true;
            }
         });
         testGameButton.setBounds(MAX_X / 2 - (int) (65 * scaling), MAX_Y * 4 / 10, (int) (130 * scaling), (int) (15 * scaling));
         this.add(testGameButton);
         gameNameField.setFont(MAIN_FONT);
         gameNameField.setBounds(MAX_X / 2 - (int) (37 * scaling), MAX_Y / 5, (int) (75 * scaling), (int) (15 * scaling));
         this.add(gameNameField);
         gamePasswordField.setFont(MAIN_FONT);
         gamePasswordField.setBounds(MAX_X / 2 - (int) (37 * scaling), MAX_Y * 3 / 10, (int) (75 * scaling), (int) (15 * scaling));
         this.add(gamePasswordField);
         backButton.addActionListener((ActionEvent e) -> {
            newState = 1;
         });
         backButton.setBounds(MAX_X / 2 - (int) (65 * scaling), MAX_Y * 7 / 10, (int) (130 * scaling), (int) (15 * scaling));
         this.add(backButton);
         //Basic visuals
         this.setDoubleBuffered(true);
         this.setBackground(new Color(20, 20, 20));
         this.setLayout(null); //Necessary so that the buttons can be placed in the correct location
         this.setVisible(true);
      }

      @Override
      public void paintComponent(Graphics g) {
         g2 = (Graphics2D) g;
         if (generateGraphics) {
            generateGraphics = false;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(MAIN_FONT);
         }

         //FOR NOW, ONLY TEMP
/*
         if (attemptedGameName != null) {
            if (!attemptedGameName.equals("w")) {
               attemptedGameName = "w";
               attemptedGamePassword = "w";
               testGame = true;
            }
         } else {
            attemptedGameName = "";
         }
*/
         super.paintComponent(g);
         //this.requestFocusInWindow(); Removed, this interferes with the textboxes. See if this is truly necessary
      }
   }

   private class JoinPanel extends JPanel { //State =3
      private Graphics2D g2;
      private JTextField gameNameTestField = new JTextField(3);
      private JTextField gamePasswordTestField = new JTextField(3);
      private CustomButton testGameButton = new CustomButton("Join game");
      private CustomButton backButton = new CustomButton("Back");

      public JoinPanel() {
         //Setting up the size
         this.setPreferredSize(new Dimension(MAX_X, MAX_Y));
         //Basic create and join server buttons
         testGameButton.addActionListener((ActionEvent e) -> {
            if (!testGame) {
               attemptedGameName = gameNameTestField.getText();
               attemptedGamePassword = gamePasswordTestField.getText();
               testGame = true;
            }
         });
         testGameButton.setBounds(MAX_X / 2 - (int) (65 * scaling), MAX_Y * 4 / 10, (int) (130 * scaling), (int) (15 * scaling));
         this.add(testGameButton);
         gameNameTestField.setFont(MAIN_FONT);
         gameNameTestField.setBounds(MAX_X / 2 - (int) (37 * scaling), MAX_Y / 5, (int) (75 * scaling), (int) (15 * scaling));
         this.add(gameNameTestField);
         gamePasswordTestField.setFont(MAIN_FONT);
         gamePasswordTestField.setBounds(MAX_X / 2 - (int) (37 * scaling), MAX_Y * 3 / 10, (int) (75 * scaling), (int) (15 * scaling));
         this.add(gamePasswordTestField);
         backButton.addActionListener((ActionEvent e) -> {
            newState = 1;
         });
         backButton.setBounds(MAX_X / 2 - (int) (65 * scaling), MAX_Y * 7 / 10, (int) (130 * scaling), (int) (15 * scaling));
         this.add(backButton);
         //Basic visuals
         this.setDoubleBuffered(true);
         this.setBackground(new Color(20, 20, 20));
         this.setLayout(null); //Necessary so that the buttons can be placed in the correct location
         this.setVisible(true);
      }

      @Override
      public void paintComponent(Graphics g) {
         g2 = (Graphics2D) g;
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         g2.setFont(MAIN_FONT);
         super.paintComponent(g);
         //this.requestFocusInWindow(); Removed, this interferes with the textboxes. See if this is truly necessary

         //FOR NOW, ONLY TEMP
/*
         if (attemptedGameName != null) {
            if (!attemptedGameName.equals("w")) {
               attemptedGameName = "w";
               attemptedGamePassword = "w";
               testGame = true;
            }
         } else {
            attemptedGameName = "";
         }
*/
      }
   }

   private class InstructionPanel extends JPanel { //State=6
      private Graphics2D g2;
      private CustomButton backButton = new CustomButton("Back");


      public InstructionPanel() {
         //Setting up the size
         this.setPreferredSize(new Dimension(MAX_X, MAX_Y));
         //Setting up buttons
         backButton.addActionListener((ActionEvent e) -> {
            newState = 1;
            leaveGame = true;
         });
         backButton.setBounds(MAX_X / 2 - (int) (65 * scaling), MAX_Y * 7 / 10, (int) (130 * scaling), (int) (15 * scaling));
         this.add(backButton);

         //Basic visuals
         this.setDoubleBuffered(true);
         this.setBackground(new Color(150, 150, 150));
         this.setLayout(null); //Necessary so that the buttons can be placed in the correct location
         this.setVisible(true);
      }

      @Override
      public void paintComponent(Graphics g) {
         g2 = (Graphics2D) g;
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         g2.setFont(MAIN_FONT);
         super.paintComponent(g);

      }
   }

   private class WaitingPanel extends JPanel { //State=4
      private Graphics2D g2;
      private boolean buttonAdd = true;
      private boolean buttonRemove = true;
      private CustomButton readyGameButton = new CustomButton("Begin game");
      private CustomButton backButton = new CustomButton("Back");


      public WaitingPanel() {
         //Setting up the size
         this.setPreferredSize(new Dimension(MAX_X, MAX_Y));
         //Setting up buttons
         readyGameButton.setBounds(MAX_X / 2 - (int) (65 * scaling), MAX_Y * 4 / 10, (int) (130 * scaling), (int) (15 * scaling));
         readyGameButton.addActionListener((ActionEvent e) -> {
            notifyReady = true;
         });

         backButton.addActionListener((ActionEvent e) -> {
            newState = 1;
            leaveGame = true;
         });
         backButton.setBounds(MAX_X / 2 - (int) (65 * scaling), MAX_Y * 7 / 10, (int) (130 * scaling), (int) (15 * scaling));
         this.add(backButton);

         //Basic visuals
         this.setDoubleBuffered(true);
         this.setBackground(new Color(70, 70, 70));
         this.setLayout(null); //Necessary so that the buttons can be placed in the correct location
         this.setVisible(true);
      }

      @Override
      public void paintComponent(Graphics g) {
         g2 = (Graphics2D) g;
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         g2.setFont(MAIN_FONT);
         super.paintComponent(g);
         //this.requestFocusInWindow(); Removed, this interferes with the textboxes. See if this is truly necessary
         //if host==true, then display the ready button
         g2.setColor(Color.white);
         if ((host) && (buttonAdd)) {
            this.add(readyGameButton);
            buttonAdd = false;
         }
         for (int i = 0; i < onlineList.size(); i++) {
            g2.drawString(onlineList.get(i).getUsername(), 0, 40 * (i + 1));
         }
         if (loading) {
            if (buttonRemove) {
               this.remove(readyGameButton);
               buttonRemove = false;
            }
            g2.drawString("LOADING", MAX_X / 2, MAX_Y / 2);
         }
      }
   }

   private class IntermediatePanel extends JPanel { //State=5 (intermediate)=
      private GamePanel gamePanel;
      private boolean begin = true;

      public IntermediatePanel() {
         //Scaling is a factor which reduces the MAX_X/MAX_Y so that it eventually fits
         //Setting up the size
         this.setPreferredSize(new Dimension(MAX_X, MAX_Y));
         //Basic visuals
         this.setDoubleBuffered(true);
         this.setBackground(new Color(0, 0, 0));
         this.setLayout(null); //Necessary so that the buttons can be placed in the correct location
         this.setVisible(true);
      }

      //set a method called initialize
      public void repaintReal() {
         gamePanel.repaint();
      }

      public void initializeScaling() {
         if ((1.0 * MAX_Y / MAX_X) > (1.0 * DESIRED_Y / DESIRED_X)) { //Make sure that these are doubles
            //Y is excess
            scaling = 1.0 * MAX_X / DESIRED_X;
         } else {
            //X is excess
            scaling = 1.0 * MAX_Y / DESIRED_Y;
         }
         MAIN_FONT = new Font("Quicksand", Font.PLAIN, (int) (8 * scaling));
      }

      public void initializeSize() {
         int[] tempXy = {(int) (DESIRED_X * scaling / 2), (int) (DESIRED_Y * scaling / 2)};
         myMouseAdapter.setCenterXy(tempXy);
         myMouseAdapter.setScaling(scaling);
         //Game set up
         try {
            sheet = ImageIO.read(new File(".\\res\\Map.png"));
            sectors = new Sector[20][20];
            for (int i = 0; i < 20; i++) {
               for (int j = 0; j < 20; j++) {
                  sectors[j][i] = new Sector();
                  sectors[j][i].setImage(sheet.getSubimage(j * 500, i * 500, 500, 500));
                  sectors[j][i].setSectorCoords(j, i);
                  sectors[j][i].setScaling(scaling);
                  sectors[j][i].setCenterXy(tempXy);
               }
            }
         } catch (IOException e) {
            System.out.println("Image not found");
         }
         gamePanel = new GamePanel();
         gamePanel.setBounds((int) ((this.getWidth() - (DESIRED_X * scaling)) / 2), (int) ((this.getHeight() - (DESIRED_Y * scaling)) / 2), (int) (DESIRED_X * scaling), (int) (DESIRED_Y * scaling));
         this.add(gamePanel);
      }
   }

   private class GamePanel extends JPanel {//State=5
      private Graphics2D g2;
      private boolean generateGraphics = true;
      int[] midXy = new int[2];
      double adjustment = 0;
      int changeFactor = 1;
      private Shape rect;
      private Shape largeCircle;
      private Shape midCircle;
      private Shape smallCircle;
      private Area areaRect;
      private Area largeRing;
      private Area midRing;
      private Area areaSmallCircle;
      private Polygon BOTTOM_BAR = new Polygon();

      public GamePanel() {
         //Basic visuals
         this.setDoubleBuffered(true);
         this.setBackground(new Color(40, 40, 40));
         this.setLayout(null); //Necessary so that the buttons can be placed in the correct location
         this.setVisible(true);
         this.addMouseListener(myMouseAdapter);
         this.addMouseWheelListener(myMouseAdapter);
         this.addMouseMotionListener(myMouseAdapter);
      }

      @Override
      public void paintComponent(Graphics g) {
         if ((state == 5) && (generateGraphics)) {
            midXy[0] = (int) (DESIRED_X * scaling / 2);
            midXy[1] = (int) (DESIRED_Y * scaling / 2);
            for (GamePlayer currentGamePlayer : gamePlayers) {
               currentGamePlayer.setScaling(scaling);
               currentGamePlayer.setCenterXy(midXy);
            }
            g2 = (Graphics2D) g.create();
            g2.setFont(MAIN_FONT);
            generateGraphics = false;
            largeCircle = new Ellipse2D.Double(400 * scaling, 175 * scaling, 150 * scaling, 150 * scaling);
            // midCircle = new Ellipse2D.Double(439 * scaling, 220 * scaling, 60 * scaling, 60 * scaling);
            // smallCircle = new Ellipse2D.Double(300 * scaling, 150 * scaling, 200 * scaling, 200 * scaling);
            rect = new Rectangle2D.Double(0, 0, 950 * scaling, 500 * scaling);
            areaRect = new Area(rect);
            largeRing = new Area(largeCircle);
            areaRect.subtract(largeRing);
            //  midRing = new Area(midCircle);
            //  areaSmallCircle = new Area(smallCircle);
            // largeRing.subtract(midRing);
            // midRing.subtract(areaSmallCircle);
            BOTTOM_BAR.addPoint((int) (272 * scaling), (int) (500 * scaling));
            BOTTOM_BAR.addPoint((int) (265 * scaling), (int) (440 * scaling));
            BOTTOM_BAR.addPoint((int) (270 * scaling), (int) (435 * scaling));
            BOTTOM_BAR.addPoint((int) (680 * scaling), (int) (435 * scaling));
            BOTTOM_BAR.addPoint((int) (685 * scaling), (int) (440 * scaling));
            BOTTOM_BAR.addPoint((int) (678 * scaling), (int) (500 * scaling));
         } else {
            g2 = (Graphics2D) g;
         }
         g2.setFont(MAIN_FONT);
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         super.paintComponent(g2);
         //this.requestFocusInWindow(); Removed, this interferes with the textboxes. See if this is truly necessary
         //Sectors
         int startX = (int) ((myGamePlayer.getXy()[0] - 475.0) / 500.0);
         int finalX = (int) (Math.ceil((myGamePlayer.getXy()[0] + 475.0) / 500.0)) + 1;
         int startY = (int) ((myGamePlayer.getXy()[1] - 250.0) / 500.0);
         int finalY = (int) (Math.ceil((myGamePlayer.getXy()[1] + 250.0) / 500.0)) + 1;


         //FOR NOW, DRAW TWICE. IN THE FUTURE, FIND A BETTER WAY TO DO THIS
         for (int i = startY; i < finalY; i++) {
            for (int j = startX; j < finalX; j++) {
               if ((i >= 0) && (j >= 0) && (i < 20) && (j < 20)) {
                  sectors[j][i].drawSector(g2, myGamePlayer.getXy());
               }
            }
         }
         for (int i = startY; i < finalY; i++) {
            for (int j = startX; j < finalX; j++) {
               if ((i >= 0) && (j >= 0) && (i < 20) && (j < 20)) {
                  sectors[j][i].drawSector(g2, myGamePlayer.getXy());
               }
            }
         }
         // g2.drawImage(sheet, -(int) (scaling * myGamePlayer.getXy()[0]), -(int) (scaling * myGamePlayer.getXy()[1]), (int) (8752 * scaling), (int) (5920 * scaling), null);
         /*
         int width = 950;
         int height = 500;
         int x = myGamePlayer.getXy()[0] - 475;
         int y = myGamePlayer.getXy()[1] - 250;
         int xAdjust = 0;
         int yAdjust = 0;
         if ((x + width) > 8752) {
            width =  8752 - x;
            if (width < 0) {
               width = 0;
            }
         }
         if ((y + height) > 5920) {
            height =  5920 - y;
            if (height < 0) {
               height = 0;
            }
         }
         if (x < 0) {
            xAdjust = -x;
            x = 0;
         }
         if (y < 0) {
            yAdjust = -y;
            y = 0;
         }
         BufferedImage temp = sheet.getSubimage(x, y, width, height);
         g2.drawImage(temp, (int) (xAdjust * scaling), (int) (yAdjust * scaling), (int) (width * scaling), (int) (height * scaling), null);
         */
         //Game player
         for (GamePlayer currentGamePlayer : gamePlayers) {
            if (currentGamePlayer != null) {
               currentGamePlayer.draw(g2, myGamePlayer.getXy());
            }
         }
         /*
         g2.setColor(Color.white);
         g2.drawLine((int) (DESIRED_X * scaling / 2), (int) (DESIRED_Y * scaling / 2), (int) (DESIRED_X * scaling / 2), (int) (DESIRED_Y * scaling / 2) + 100);
         g2.setColor(Color.white);
         g2.drawLine((int) (DESIRED_X * scaling / 2), (int) (DESIRED_Y * scaling / 2), (int) (DESIRED_X * scaling / 2) + 100, (int) (DESIRED_Y * scaling / 2));
           */

         //Darkness
         g2.setColor(new Color(0f, 0f, 0f, 0.8f));
         g2.fill(areaRect);
         g2.setColor(new Color(0f, 0f, 0f, 0.7f));
         g2.fill(largeRing);
         //   g2.setColor(new Color(0.1f, 0.1f, 0.1f, 0.6f));
         //  g2.fill(midRing);
         //   g2.setColor(new Color(0.1f, 0.1f, 0.02f, 0.1f));
         //  g2.fill(smallCircle);
         /*
         g2.fillRect((int) (DESIRED_X * 67.0 / 80.0 * scaling), (int) (DESIRED_Y / 100 * scaling), (int) (DESIRED_X * 5.0 / 32.0 * scaling), (int) (DESIRED_Y / 4 * scaling));
         Polygon bottomBar = new Polygon();
         bottomBar.addPoint((int) (DESIRED_X / 160 * scaling), (int) (DESIRED_Y * 99.0 / 100.0 * scaling));
         bottomBar.addPoint((int) (DESIRED_X / 160 * scaling), (int) (DESIRED_Y * 39.0 / 50.0 * scaling));
         bottomBar.addPoint((int) (DESIRED_X / 32 * scaling), (int) (DESIRED_Y * 37.0 / 50.0 * scaling));
         bottomBar.addPoint((int) (DESIRED_X * 31.0 / 32.0 * scaling), (int) (DESIRED_Y * 37.0 / 50.0 * scaling));
         bottomBar.addPoint((int) (DESIRED_X * 159.0 / 160.0 * scaling), (int) (DESIRED_Y * 39.0 / 50.0 * scaling));
         bottomBar.addPoint((int) (DESIRED_X * 159.0 / 160.0 * scaling), (int) (DESIRED_Y * 99.0 / 100.0 * scaling));
        ;
         g2.fillRect((int) (DESIRED_X / 160 * scaling), (int) (DESIRED_Y / 100 * scaling), (int) (DESIRED_X / 4 * scaling), (int) (DESIRED_Y * 3.0 / 50.0 * scaling));
         g2.fillRect((int) (DESIRED_X / 160 * scaling), (int) (DESIRED_Y * 2.0 / 25.0 * scaling), (int) (DESIRED_X / 5 * scaling), (int) (DESIRED_Y / 50 * scaling));
         g2.fillRect((int) (DESIRED_X / 160 * scaling), (int) (DESIRED_Y * 11.0 / 100.0 * scaling), (int) (DESIRED_X / 5 * scaling), (int) (DESIRED_Y / 50 * scaling));
         */

         g2.setColor(new Color(165, 156, 148));
         //Minimap
         g2.drawRect((int) (830 * scaling), (int) (scaling), (int) (120 * scaling), (int) (120 * scaling));
         //Bottom bar
         g2.drawPolygon(BOTTOM_BAR);
         /*
          g2.drawPolygon(bottomBar);
         */

         //Stat bars
         g2.setColor(new Color(190, 40, 40));
         g2.fillRect(0, (int) (2 * scaling), (int) (121 * scaling * myGamePlayer.getHealth() / myGamePlayer.getMaxHealth()), (int) (5 * scaling));
         //Stat borders
         //  g2.drawRect((int) (5 * scaling), (int) (5 * scaling), (int) (200 * scaling), (int) ( * scaling)); This is the information panel, possibly move.
         /*
         g2.setColor(new Color(91, 85, 80));
         g2.drawRect(0, 0, (int) (120 * scaling), (int) (3 * scaling));
         g2.drawRect(0, (int) (6 * scaling), (int) (120 * scaling), (int) (3 * scaling));
         g2.drawRect(0, (int) (12 * scaling), (int) (120 * scaling), (int) (25 * scaling));//Menu bar
         */
         g2.setColor(new Color(165, 156, 148));
         g2.drawRect(0, (int) (2 * scaling), (int) (121 * scaling), (int) (5 * scaling));
         g2.drawRect(0, (int) (11 * scaling), (int) (121 * scaling), (int) (5 * scaling));
         //Bottom bar contents

         //Spells
         g2.fillRect((int) (565 * scaling), (int) (442 * scaling), (int) (30 * scaling), (int) (50 * scaling));
         g2.fillRect((int) (604 * scaling), (int) (442 * scaling), (int) (30 * scaling), (int) (50 * scaling));
         g2.fillRect((int) (643 * scaling), (int) (442 * scaling), (int) (30 * scaling), (int) (50 * scaling));


         //g2.fillRect((int) (485 * scaling),(int) (450 * scaling), (int) (29 * scaling), (int) (25 * scaling));
         //g2.fillRect((int) (542 * scaling),(int) (450 * scaling), (int) (29 * scaling), (int) (25 * scaling));
/*
  //Partition
         g2.fillRect((int) (474 * scaling), (int) (439 * scaling), (int) (2 * scaling), (int) (57 * scaling));

         g2.fillRect((int) (DESIRED_X * 17.0 / 32.0 * scaling), (int) (DESIRED_Y * 383.0 / 500.0 * scaling), (int) (DESIRED_X / 8 * scaling), (int) (DESIRED_Y / 5 * scaling));
         g2.fillRect((int) (DESIRED_X * 11.0 / 16.0 * scaling), (int) (DESIRED_Y * 383.0 / 500.0 * scaling), (int) (DESIRED_X / 8 * scaling), (int) (DESIRED_Y / 5 * scaling));
         g2.fillRect((int) (DESIRED_X * 27.0 / 32.0 * scaling), (int) (DESIRED_Y * 383.0 / 500.0 * scaling), (int) (DESIRED_X / 8 * scaling), (int) (DESIRED_Y / 5 * scaling));
         g2.setColor(new Color(20, 30, 50));
         g2.fillRect((int) (DESIRED_X * 17.0 / 32.0 * scaling), (int) ((DESIRED_Y * 483.0 / 500.0 - DESIRED_Y / 5 * myGamePlayer.getSpellPercent(0)) * scaling), (int) (DESIRED_X / 8 * scaling), (int) ((DESIRED_Y / 5 * myGamePlayer.getSpellPercent(0)) * scaling));
*/
         //Stat bars
         /*
         g2.setColor(Color.white);
         g2.drawString("Gold: " + myGamePlayer.getGold(), (int) (5 * scaling), (int) (31 * scaling));
         g2.drawString("Level: " + myGamePlayer.getLevel(), (int) (5 * scaling), (int) (22 * scaling));
         g2.drawString("Username: " + myGamePlayer.getUsername(), (int) (5 * scaling), (int) (13 * scaling));
         g2.drawString("Attack: " + myGamePlayer.getAttack(), (int) (10 * scaling), (int) (470 * scaling));
         g2.drawString("Mobility: " + myGamePlayer.getMobility(), (int) (10 * scaling), (int) (485 * scaling));
         g2.drawString("Range: " + myGamePlayer.getRange(), (int) (10 * scaling), (int) (455 * scaling));

         /*
         if (adjustment > 15) {
            changeFactor=-1;
         }else if (adjustment<-15){
            changeFactor=1;
         }
         adjustment+=0.4*changeFactor;
         */

         //g2.fillOval(midXy[0] - (int) ((220 + adjustment / 2.0) * scaling), midXy[1] - (int) ((220 + adjustment / 2.0) * scaling), (int) ((440 + adjustment) * scaling), (int) ((440 + adjustment) * scaling));
      }

   }

   private class CustomButton extends JButton {
      private Color foregroundColor = new Color(180, 180, 180);
      private Color backgroundColor = new Color(50, 50, 50);

      CustomButton(String description) {
         super(description);
         super.setContentAreaFilled(false);
         this.setFont(MAIN_FONT);
         this.setForeground(foregroundColor);
         this.setBackground(backgroundColor);
         this.setFocusPainted(false);
      }

      CustomButton(String description, Color foregroundColor, Color backgroundColor) {
         super(description);
         super.setContentAreaFilled(false);
         this.setFont(MAIN_FONT);
         this.foregroundColor = foregroundColor;
         this.backgroundColor = backgroundColor;
         this.setForeground(foregroundColor);
         this.setBackground(backgroundColor);
         this.setFocusPainted(false);
      }

      @Override
      protected void paintComponent(Graphics g) {
         if (getModel().isPressed()) {
            g.setColor(backgroundColor.brighter().brighter());
         } else if (getModel().isRollover()) {
            g.setColor(backgroundColor.brighter());
         } else {
            g.setColor(getBackground());
         }
         g.fillRect(0, 0, getWidth(), getHeight());
         super.paintComponent(g);
      }
   }
}

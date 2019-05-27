import javax.swing.JPanel;
import javax.swing.JFrame;

import java.awt.Graphics;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.Point;

import java.util.ArrayList;

class MapGenTester extends JFrame{
 private Disp display;
 private ArrayList<RoadNode> activeNodes;
  
  MapGenTester() {
    
    this.setSize(Toolkit.getDefaultToolkit().getScreenSize());
    
    
    
    MapGen gen = new MapGen();
    
    gen.generateMap2(50,800,200,100,0.2);
    gen.tetherAllNodes();
    
    activeNodes = gen.getNodes();
    
    display = new Disp();
    this.add(display);
    display.repaint();
    this.setVisible(true);
  }
  
  
  
   class Disp extends JPanel {
    
    private void drawOvalCustom(int radius, Graphics g) {
      g.drawOval(-radius,-radius,radius*2,radius*2);
    }
    
    private void drawLineCustom(Point start, Point end, Graphics g) {
    	g.drawLine(start.x,start.y,end.x,end.y);
    }
    
    public void paintComponent(Graphics g) {
      super.paintComponent(g);
      this.setCenter(g);
      
      g.setColor(Color.BLUE);
      this.drawOvalCustom(200, g);
      g.setColor(Color.GREEN);
      this.drawOvalCustom(200 - 50,g);
      this.drawOvalCustom(200 + 50,g);
      //g.drawOval(-200,-200,400,400);
     // g.drawOval(-225,-225,400 + 50,400 + 50);
     // g.drawOval(-175,-175,400 - 50,400 - 50);
//      g.drawOval(-150,-150,300,300);
//      g.drawOval(-50,-50,100,100);
      
      
      g.setColor(Color.RED);
      
      for (int i = 0; i < activeNodes.size(); i++) {
        g.fillOval((int)activeNodes.get(i).getPoint().getX() - 5,
        		(int)activeNodes.get(i).getPoint().getY() - 5,10,10);
        for (int j = 0; j < activeNodes.get(i).connections.size(); j++) {
        	this.drawLineCustom(activeNodes.get(i).location,activeNodes.get(i).connections.get(j),g);
        }
        
      }
    }

	private void setCenter(Graphics g) {
		g.translate( (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth()/2,
                (int) Toolkit.getDefaultToolkit().getScreenSize().getHeight()/2);
		
	}

       
    
    
  }
  
   class RoadNode {
    public Point location;
    public ArrayList<Point> connections;
    
    RoadNode(int xLoc, int yLoc) {
      location = new Point(xLoc,yLoc);
      this.connections = new ArrayList<Point>(0);
    }
    
    RoadNode(Point point) {
      this.location = point;
      this.connections = new ArrayList<Point>(0);
    }
    
    public Point getPoint() {
      return this.location;
    }
    
  }
  
   class MapGen {
    private ArrayList<RoadNode> nodes; 
    
    MapGen() {
      this.nodes = new ArrayList<RoadNode>(0);
    }
    
    public void tetherAllNodes() {
    	int numOfNodes = nodes.size();
    	
    	int[] xDeltas = new int[numOfNodes];
    	int[] yDeltas = new int[numOfNodes];
    	int tempDeltaX, tempDeltaY;
    	int negativeBest;
    	int positiveBest;
    	int positiveBestIdx = 0;
    	int negativeBestIdx = 0;    	
    	for (int idx = 0; idx < numOfNodes; idx++) {
    		positiveBest = 999;
    		negativeBest = 999;
    		for (int idx2 = 0; idx2 < numOfNodes; idx2++) {
    			xDeltas[idx2] = nodes.get(idx).location.x - nodes.get(idx2).location.x;
    			yDeltas[idx2] = nodes.get(idx).location.y - nodes.get(idx2).location.y;
    			
    			tempDeltaX = xDeltas[idx2];
    			tempDeltaY = yDeltas[idx2];
    			
    			
    			if (tempDeltaX > 0) {
    				if (tempDeltaX < positiveBest) {
    					positiveBest = tempDeltaX;
    					positiveBestIdx = idx2;    					
    				}
    			} else {
    				if (tempDeltaX > negativeBest) {
    					negativeBest = tempDeltaX;
    					negativeBestIdx = idx2;    					
    				}
    			}
    			
    			if (tempDeltaY > 0) {
    				if (tempDeltaY < positiveBest) {
    					positiveBest = tempDeltaY;
    					positiveBestIdx = idx2;    					
    				}
    			} else {
    				if (tempDeltaY > negativeBest) {
    					negativeBest = tempDeltaY;
    					negativeBestIdx = idx2;    					
    				}
    			}
    			
    			if (idx == idx2 + 1) {
    				idx2++;
    			}
    			
    		}
    		
    		nodes.get(idx).connections.add(nodes.get(positiveBestIdx).location);
    		nodes.get(idx).connections.add(nodes.get(negativeBestIdx).location);
    		
    		
    	}   
    	
    	
    }    

    
    private boolean randRoll(int chance) {
      if (Math.random()*1000 < chance) {
        return true;
      }
      
      return false;
    }
    
    private boolean roll (double chance) {
      if (Math.random() < chance) {
        return true;
      }
      
      return false;
    }
    
    public void generateMap(int numOfNodes, int mapSize, int targetRadius, int offsetRange, int offsetPacking) {
      for (int i = 0; i < numOfNodes; i ++) {
        double angle = Math.random()*2*Math.PI;
        int distance = targetRadius;
        int offset = 0;
        
        if (offsetPacking != 0) {
          for (int j = 0; j <= offsetPacking; j++) {
            int randNum = (int) (Math.random()*(offsetRange) - offsetRange/2);
            offset += randNum;
          }
          
          offset = (int)(offset/offsetPacking);
          
          distance += offset;
        }
        int pointX = (int) (distance*Math.cos(angle));
        int pointY = (int) (distance*Math.sin(angle));
        
        nodes.add(new RoadNode(new Point(pointY,pointX)));        
      }
    }
    
    public void generateMap2(int numOfNodes, double mapSize, double targetRadius, double offsetFactor, double stDev) {
      for (int i = 0; i < numOfNodes; i++) {
        //double angle = Math.random()*2*Math.PI;
        double angle = (2*Math.PI/numOfNodes)*i;
        double distance = targetRadius;
        double offset = 0;
        
        boolean exit = false;
        
        do {
        
          offset = (int) (Math.random()*(offsetFactor) - offsetFactor/2);
          //System.out.println(offset);
          
          double inclusionChance = ((1/Math.sqrt(2*Math.PI*stDev))*
            Math.pow(Math.E,(-(Math.pow((offset/offsetFactor),2))/(2*Math.pow(stDev,2)))));
          
//          double coefficent = 1/(Math.sqrt(2*Math.PI*stDev));
//          double numerator = Math.pow(offset/offsetFactor,2);
//          //System.out.println(numerator);
//          double denominator = 2*Math.pow(stDev,2);
//          double exponent = -(numerator/denominator);
          
          //inclusionChance = coefficent*Math.pow(Math.E,exponent);
          
          //System.out.println(inclusionChance);
          
            if (roll(inclusionChance)) {
              exit = true;
              //System.out.println(inclusionChance);
            }
            
        } while (!exit);
        
        distance += offset;
        
        int pointX = (int) (distance*Math.cos(angle));
        int pointY = (int) (distance*Math.sin(angle));
        
        nodes.add(new RoadNode(new Point(pointY,pointX)));        
      }
    }
        
    public ArrayList<RoadNode> getNodes() {
      return this.nodes;
    }             
}
}
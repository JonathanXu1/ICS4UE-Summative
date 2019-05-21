import java.awt.Graphics2D;

/**
 * GamePlayer.java
 * This is
 *
 * @author Will Jeong
 * @version 1.0
 * @since 2019-04-24
 */

public class GamePlayer extends Player {
   //Constants
   private int ID;
   private int[] xy = {300, 300};
   private int[] centerXy = new int[2];
   private double scaling;
   private GeneralClass thisClass = new TestClass();//Temporary, normally it should be determined in the constructor
   private boolean spell1;
   private int spell1Percent;

   GamePlayer(String username) {
      super(username);
   }

   public void setID(int ID) {
      this.ID = ID;
   }

   public void setCenterXy(int[] centerXy) {
      this.centerXy[0] = centerXy[0];
      this.centerXy[1] = centerXy[1];
   }

   public int[] getXy() {
      return (xy);
   }

   public void setXy(int[] xy) {
      this.xy = xy;
   }

   public void draw(Graphics2D g2, int[] midXy) {
      thisClass.drawReal(g2, centerXy[0] + (int) (scaling * (xy[0] - midXy[0])) - (int) (100 * scaling) / 2, centerXy[1] + (int) (scaling * (xy[1] - midXy[1])) - (int) (100 * scaling) / 2, (int) (100 * scaling), (int) (100 * scaling), spell1);
      if (spell1){
         spell1=false;
      }
   }

   public void setScaling(double scaling) {
      this.scaling = scaling;
   }

   public double getScaling() {
      return (scaling);
   }

   public void setSpell1(boolean spell1) {
      this.spell1 = spell1;
   }

   public boolean getSpell1() {
      return spell1;
   }

   public void setSpell1Percent(int spell1Percent) {
      this.spell1Percent = spell1Percent;
   }
   public double getSpell1Percent() {
      return spell1Percent/100.0;
   }
}

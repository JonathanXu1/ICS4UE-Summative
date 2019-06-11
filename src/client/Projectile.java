package client;

import java.awt.Color;
import java.awt.Graphics2D;

//tmp
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Projectile.java
 * This is
 *
 * @author Will Jeong
 * @version 1.0
 * @since 2019-05-30
 */

public class Projectile {
   private int ID;
   private int x;
   private int y;
   private static int[] xyAdjust;

   Projectile(int ID, int x, int y) {
      this.ID = ID;
      this.x = x;
      this.y = y;
   }

   public void draw(Graphics2D g2) {
      g2.setColor(Color.WHITE);
      g2.fillRect(x+xyAdjust[0], y+xyAdjust[1], 10, 10);
      particles.add(new FireParticle(x+xyAdjust[0], y+xyAdjust[1], (int) ((Math.random() * 5 + 5))));

      //Draws particles
      for (int i = 0; i < particles.size(); i++) {
         try {
            if (particles.get(i).update()) {
               particles.remove(i);
            } else {
               particles.get(i).render(g2);
               BufferedImage arrow1 = ImageIO.read(new File(System.getProperty("user.dir") + "/res/characters/archer/P_arrow1.png"));
               AffineTransform at = AffineTransform.getTranslateInstance(x+xyAdjust[0], y+xyAdjust[1]);

               //double xChange = (mouseX - x);
               //double yChange = (mouseY - y);

               //double radians = Math.atan2(yChange,xChange);
               double radians = Math.atan2(xyAdjust[1],xyAdjust[0]);


               at.rotate(radians-Math.PI/2);
               g2.drawImage(arrow1, at, null);
            }
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   public static void setXyAdjust(int[] xyAdjust1) {
      xyAdjust = xyAdjust1;
   }
}

package client;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Sector.java
 * This is
 *
 * @author Will Jeong
 * @version 1.0
 * @since 2019-05-19
 */

public class Sector {
   private BufferedImage image;
   private int[] sectorCoords = new int[2];
   private int[][] corners = new int[4][2];
   private int SECTOR_SIZE = 500;
   private int[] centerXy = new int[2];
   private double scaling;

   public void setImage(BufferedImage image) {
      this.image = image;
   }

   public void setSectorCoords(int sectorX, int sectorY) {
      this.sectorCoords[0] = sectorX;
      this.sectorCoords[1] = sectorY;
      /*
      for (int i = 0; i < 2; i++) {
         for (int j = 0; j < 2; j++) {
            int[] tempCorner = {(sectorX + j) * SECTOR_SIZE, (sectorY + i) * SECTOR_SIZE};
            corners[i + j * 2] = tempCorner;//Small binary conversion here
         }
      }
      */
   }

   public void setCenterXy(int[] centerXy) {
      this.centerXy[0] = centerXy[0];
      this.centerXy[1] = centerXy[1];
   }

   public void setScaling(double scaling) {
      this.scaling = scaling;
   }

   public void drawSector(Graphics2D g2, int[] midXy) {
      g2.drawImage(image, centerXy[0] + (int) (Math.ceil((scaling * (sectorCoords[0] * SECTOR_SIZE - midXy[0])) - (SECTOR_SIZE * scaling) / 2)), (int) (centerXy[1] + Math.ceil((scaling * (sectorCoords[1] * SECTOR_SIZE - midXy[1])) - (SECTOR_SIZE * scaling) / 2)), (int) (Math.ceil(SECTOR_SIZE * scaling)), (int) (Math.ceil(SECTOR_SIZE * scaling)), null);
   }
}
package client.particle;

import java.awt.*;
/**
 * SafeMarksmanParticle.java
 * This is a particle class used for Archer's animations
 *
 * @author Will Jeong, Jonathan Xu, Kamron Zaidi, Artem Sotnikov, Kolby Chong, Bill Liu
 * @version 1.0
 * @since 2019-06-13
 */
public class SafeMarksmanParticle extends Particle{
  private double angle;
  private double dx, dy;
  private static Color colours[] = {new Color(255,215,0), new Color(0,100,0), new Color(107,142,35), new Color(34,139,34)};

  /**
   * Class Constructor
   * @param x x-location
   * @param y y-location
   * @param size size of particle
   */
  public SafeMarksmanParticle(double x, double y, int size){
    super(x, y, size, (int)(Math.random()*20+20));

    int randomNum = (int) (Math.random() * colours.length);
    super.setColor(colours[randomNum]);

    // Creates particles that travel in a random circular direction
    angle = Math.random()*2*Math.PI;
    dx = Math.cos(angle) * 20;
    dy = Math.sin(angle) * 20;
  }

  /**
   * update method for the particle
   * @return the particle's state
   */
  public boolean update(){
    return super.update(dx, dy);
  }
}

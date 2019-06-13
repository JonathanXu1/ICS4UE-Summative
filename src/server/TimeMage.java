package server;
/**
 * SafeMarksman.java
 * This is
 *
 * @author Will Jeong
 * @version 1.0
 * @since 2019-05-19
 */
import java.util.ArrayList;
public class TimeMage extends Player{
  private int[] spellCooldowns = {100,100,100};
  private int[] spellTimers = {0,0,0};
  private static int Q_DAMAGE = 50;
  private static int E_RANGE = 500;
  private static int SPACE_DURATION = 100;
  
  private ArrayList<Player> qBlacklist = new ArrayList<Player>();
  
  TimeMage(String username, int teamNumber) {
    super(username,teamNumber);
    setMaxHealth(200);
    setHealth(200);
    setAttack(300);
    setMobility(7);
    setRange(50);//REE Change to -1 when add support for melee attacks
    setAutoAttackCooldown(10);
    setFlareCooldown(100);
    setMelee(true);
  }
  
  public boolean castSpell(int spellIndex){
    if (!getStunned()) {
      if (spellTimers[spellIndex]<=0) {
        spellTimers[spellIndex] = spellCooldowns[spellIndex];
        if (spellIndex==0) { //Q
          addStatus(new TimeMageQ(getX(), getY(), getMouseX(), getMouseY()));
          qBlacklist.clear();
        }else if (spellIndex==1){//E
          boolean eCast = false;
          for (int i = 0; (i < getEnemiesSize() && (!eCast)); i++){
            if (getEnemy(i).contains(getMouseX(), getMouseY()) && (Math.sqrt(Math.pow(getEnemy(i).getX()-getX(),2) + Math.pow(getEnemy(i).getY()-getY(),2)) < E_RANGE)){
              getEnemy(i).addStatus(new TimeMageE(getEnemy(i).getX(), getEnemy(i).getY()));//REE NOTE APPLIES TO ANYONE
              eCast = true;
            }
          }
          for (int i = 0; (i < getAlliesSize() && (!eCast)); i++){
            if (getAlly(i).contains(getMouseX(), getMouseY()) && (Math.sqrt(Math.pow(getAlly(i).getX()-getX(),2) + Math.pow(getAlly(i).getY()-getY(),2)) < E_RANGE)){
              getAlly(i).addStatus(new TimeMageE(getAlly(i).getX(), getAlly(i).getY()));//REE NOTE APPLIES TO ANYONE
              eCast = true;
            }
          }
          if (!eCast){
            addStatus(new TimeMageE(getX(), getY()));//REE NOTE APPLIES TO ANYONE
            eCast = true;
          }
        }else {//Space
          addStatus(new Uncollidable(SPACE_DURATION));
          addStatus(new Stun(SPACE_DURATION, 12));
          addStatus(new Invisible(SPACE_DURATION));
        }
        return true;
      } else {
        return false;
      }
    }else{
      return false;
    }
  }
  public int getSpellPercent(int spellIndex) {
    return (spellCooldowns[spellIndex] - spellTimers[spellIndex])/spellCooldowns[spellIndex]*100;
    /*
    if (spellTick - lastSpellTicks[spellIndex] > spellCooldowns[spellIndex]) {
      return (100);
    } else {
      return ((int) ((100.0 * (spellTick - lastSpellTicks[spellIndex]) / spellCooldowns[spellIndex])));
    }*/
  }
  
  public void update(){
    for (int i = 0; i < 3; i++){
      if (spellTimers[i] > 0){
        spellTimers[i]--;
      }
    }
    updateBasicTimers();
    //Update Projectiles
    for (int i = getProjectilesSize()-1; i >= 0; i--){
      getProjectile(i).advance();
      Projectile removed = null;
      if (getProjectile(i).getRemainingDuration() <= 0){
        removed = removeProjectile(i);
        if (removed instanceof FlareProjectile){
          addAOE(new FlareAOE(removed.getX(), removed.getY()));
        }
      } else {
        //Insert Collision with Terrain
        if (getProjectile(i) instanceof AutoProjectile){
          for (int j = 0; j < getEnemiesSize(); j++){
            if(getProjectile(i).collides(getEnemy(j))){
              getEnemy(j).damage(getAttack());
              removed = removeProjectile(i);
            }
          }
        }
      }
    }
    
    //Update AOEs
    for (int i = getAOESize()-1; i >= 0; i--){
      getAOE(i).advance();
      AOE removed = null;
      if (getAOE(i).getRemainingDuration() <= 0){
        removed = removeAOE(i);
      } else {
        if (getAOE(i) instanceof FlareAOE){
          for (int j = 0; j < getEnemiesSize(); j++){
            if (getAOE(i).collides(getEnemy(j))){
              getEnemy(j).addStatus(new Illuminated(500));
            }
          }
        } else if (getAOE(i) instanceof AutoAOE){
          for (int j = 0; j < getEnemiesSize(); j++){
            if (getAOE(i).collides(getEnemy(j))){
              getEnemy(j).damage(getAttack());
            }
          }
        } else if  (getAOE(i) instanceof TimeMageQAOE){
          for (int j = 0; j < getEnemiesSize(); j++){
            if (!qBlacklist.isEmpty()){
              for (int k = 0; k < qBlacklist.size(); k++){
                if (getEnemy(j) != qBlacklist.get(k)){
                  if (getAOE(i).collides(getEnemy(j))){
                    getEnemy(j).damage(Q_DAMAGE);
                    qBlacklist.add(getEnemy(j));
                  }
                }
              }
            } else {
              if (getAOE(i).collides(getEnemy(j))){
                getEnemy(j).damage(Q_DAMAGE);
                qBlacklist.add(getEnemy(j));
              }
            }
          }
          for (int j = 0; j < getAlliesSize(); j++){
            if (!qBlacklist.isEmpty()){
              for (int k = 0; k < qBlacklist.size(); k++){
                if (getAlly(j) != qBlacklist.get(k)){
                  if (getAOE(i).collides(getAlly(j))){
                    getAlly(j).addShield(new TimeMageQShield());
                    qBlacklist.add(getAlly(j));
                  }
                }
              }
            } else {
              if (getAOE(i).collides(getAlly(j))){
                getAlly(j).addShield(new TimeMageQShield());
                qBlacklist.add(getAlly(j));
              }
            }
          }
          /*
          for (int k = 0; k < qBlacklist.size(); k++){
            if (this != qBlacklist.get(k)){
              if (getAOE(i).collides(this)){
                addShield(new TimeMageQShield());
                qBlacklist.add(this);
              }
            }
          }*/
        }
      }
    }
    
    updateStatuses();
    
    for (int i = getShieldsSize()-1; i >= 0; i--){
      getShield(i).advance();
      Shield removed = null;
      if (getShield(i).getRemainingDuration() <= 0){
        removed = removeShield(i);
      }
    }
  }
}

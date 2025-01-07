package me.third;

import dev.robocode.tankroyale.botapi.Bot;
import dev.robocode.tankroyale.botapi.BotInfo;
import dev.robocode.tankroyale.botapi.Color;
import dev.robocode.tankroyale.botapi.events.*;

import java.util.HashMap;


public class ThirdBotT100 extends Bot {
    private final HashMap<Integer, TargetInfo> targetList = new HashMap<>();

    private States currentState = States.Seek;

    private boolean fullScan = false;
    private boolean lockScanToggle = false;
    private int lockScanMissCount = 0;

    private double radarAngle = -1;

    private int lockedTarget = -1;

    private int fireMissCount = 0;
    /*
    START
     */
    public static void main(String[] args) {
        new ThirdBotT100().start();
    }

    ThirdBotT100() {
        super(BotInfo.fromFile("ThirdBotT100.json"));
    }

    /*
    RUN
     */
    @Override
    public void run() {
        setBodyColor(Color.WHITE);
        setTurretColor(Color.WHITE);
        setRadarColor(Color.GREEN);

        targetList.clear();
        currentState = States.Seek;
        fullScan = false;
        radarAngle = getRadarDirection();
        lockedTarget = -1;
        lockScanToggle = false;
        lockScanMissCount = 0;

        setAdjustRadarForGunTurn(false);

        if(isRunning()){
            radarAngle = getRadarDirection();
            setRadarTurnRate(10);
        }
    }

    @Override
    public void onTick(TickEvent tickEvent) {
        radarAngle = getRadarDirection();
        currentState.onUpdate(this);

        if(fireMissCount >= 5) {
            resetTargeting();
            targetList.clear();
            fireMissCount = 0;
        }
    }

    @Override
    public void onScannedBot(ScannedBotEvent scannedBotEvent) {
        if(targetList.containsKey(scannedBotEvent.getScannedBotId())) {
            targetList.replace(scannedBotEvent.getScannedBotId(), new TargetInfo(scannedBotEvent));
            //System.out.println("Updating enemy tank!");
        } else {
            targetList.putIfAbsent(scannedBotEvent.getScannedBotId(), new TargetInfo(scannedBotEvent));
            //System.out.println("Found an enemy tank!");
        }

        if(lockedTarget != -1) {
            if(lockedTarget == scannedBotEvent.getScannedBotId()) {
                lockScanMissCount = 0;
            }
        }

    }

    @Override
    public void onBotDeath(BotDeathEvent botDeathEvent) {
        if(botDeathEvent.getVictimId() == lockedTarget) {
            resetTargeting();
        }
        targetList.remove(botDeathEvent.getVictimId());
    }

    @Override
    public void onBulletHitWall(BulletHitWallEvent bulletHitWallEvent) {
        fireMissCount++;
    }

    @Override
    public void onHitBot(HitBotEvent botHitBotEvent) {
        fireMissCount--;
    }

    private enum States {
        Seek() {

            @Override
            public void onUpdate(ThirdBotT100 self) {

                if(self.fullScan) {
                    if (!self.targetList.isEmpty()) {
                        final TargetInfo closestTarget = self.findClosest();

                        if (closestTarget != null) {
                            self.setLockedTarget(closestTarget);
                        }
                    }
                } else {
                    self.setRadarTurnRate(10);
                    if (self.getRadarTurnRemaining() <= 0) {
                        self.turnRadarLeft(15);
                    }
                    self.fullScan = (int) self.radarAngle >= 360 - (self.getRadarTurnRate() + 2);
                }
            }

        },
        Attack() {

            @Override
            public void onUpdate(ThirdBotT100 self) {
                if(self.lockedTarget == -1) {
                    self.resetTargeting();
                    return;
                }

                /*
                RADAR
                 */
                TargetInfo targetInfo = self.targetList.get(self.lockedTarget);
                double targetAngle = self.radarBearingTo(targetInfo.getX(), targetInfo.getY());
                //System.out.println(targetAngle);

                if (Math.abs(targetAngle) >= 50 || self.lockScanMissCount >= 3) {
                    if (self.getRadarTurnRemaining() <= 15) {
                        self.setRadarTurnRate(self.getRadarTurnRemaining());
                    } else {
                        self.setRadarTurnRate(15);
                    }

                    final TurnDir dir = self.bestTurnDirection(self.radarAngle, targetAngle);
                    switch(dir) {
                        case Right:
                            self.setTurnRadarRight(targetAngle);
                        case Left:
                            self.setTurnRadarLeft(targetAngle);
                    }

                } else {
                    if (self.lockScanToggle) {
                        self.setRadarTurnRate(30);
                        self.turnRadarLeft(60);
                    } else {
                        self.setRadarTurnRate(30);
                        self.turnRadarRight(60);

                    }
                    self.lockScanToggle = !self.lockScanToggle;
                    self.lockScanMissCount++;
                }
                /*
                GUN
                 */

                //TODO do further testing of this.
                double bulletSpeed = self.calcBulletSpeed(5);
                double distance = self.distanceTo(targetInfo.getX(), targetInfo.getY());
                final int predictTime = (int) Math.round(distance / bulletSpeed);

                final Vector2D predictPos = self.calcPredictedPosition(predictTime, targetInfo);
                targetAngle = self.gunBearingTo(predictPos.getPosX(), predictPos.getPosY());
                //System.out.println(targetAngle);

                if (self.getGunTurnRemaining() <= targetAngle) {
                    self.setGunTurnRate(self.getGunTurnRemaining());
                } else {
                    self.setGunTurnRate(targetAngle);
                }

                final TurnDir dir = self.bestTurnDirection(self.getGunDirection(), targetAngle);
                switch(dir) {
                    case Right:
                        self.setTurnGunRight(targetAngle);
                    case Left:
                        self.setTurnGunLeft(targetAngle);
                }

                if(self.getGunTurnRemaining() <= 0 && self.getGunHeat() <= 0) {
                    self.fire(5);
                }
            }
        };

        public abstract void onUpdate(ThirdBotT100 self);
    }

    /*
    Funcs
     */

    public void setLockedTarget(TargetInfo targetInfo) {
        this.lockedTarget = targetInfo.getScannedBotId();
        this.currentState = States.Attack;
    }

    public void resetTargeting() {
        this.lockedTarget = -1;
        this.currentState = States.Seek;
        this.fullScan = false;
        this.lockScanToggle = false;
        this.lockScanMissCount = 0;
        //this.targetList.clear();
        //System.out.println("Reset Targeting state");
    }

    /*
    Utils
     */

    public Vector2D calcPredictedPosition(int ticksAhead, TargetInfo targetInfo) {
        double posX = targetInfo.getX();
        double posY = targetInfo.getY();
        double dir = targetInfo.getDirection();
        double speed = targetInfo.getSpeed();

        for(int ticks = 0; ticks != ticksAhead; ticks++) {
            double angleRad = Math.toRadians(dir);
            double velX = speed * Math.cos(angleRad);
            double velY = speed * Math.sin(angleRad);

            posX += velX;
            posY += velY;
        }

        return new Vector2D(posX, posY);
    }

    public TurnDir bestTurnDirection(double fromAngle, double toAngle) {
        double deltaTheta = toAngle - fromAngle;

        deltaTheta = (deltaTheta + 180) % 360;
        if(deltaTheta > 180) {
            deltaTheta -= 360;
        }

        if (deltaTheta > 0) {
            return TurnDir.Right;
        } else if (deltaTheta < 0) {
            return TurnDir.Left;
        } else {
            return TurnDir.Err;
        }
    }

    private TargetInfo findClosest() {
        TargetInfo closest = null;
        double cDist = 100000;

        for(TargetInfo targetInfo : targetList.values()) {
            double tempDist = distanceTo(targetInfo.getX(), targetInfo.getY());

            if(tempDist < cDist) {
                cDist = tempDist;
                closest = targetInfo;
                //System.out.println("Closest Target Found! %s %s".formatted( ""+closest.scannedBotId, ""+cDist));
            }
        }
        return closest;
    }

    public static enum TurnDir {
        Left,
        Right,
        Err;
    }

    static class Vector2D {
        double posX = 0;
        double posY = 0;

        public Vector2D(double posX, double posY) {
            this.posX = posX;
            this.posY = posY;
        }

        public void setPosX(double posX) {
            this.posX = posX;
        }

        public void setPosY(double posY) {
            this.posY = posY;
        }

        public double getPosX() {
            return this.posX;
        }

        public double getPosY() {
            return this.posY;
        }
    }

    static class TargetInfo {
        private final int scannedByBotId;
        private final int scannedBotId;
        private final double energy;
        private final double x;
        private final double y;
        private final double direction;
        private final double speed;

        public TargetInfo(ScannedBotEvent event) {
            this.scannedByBotId = event.getScannedByBotId();
            this.scannedBotId = event.getScannedBotId();
            this.energy = event.getEnergy();
            this.x = event.getX();
            this.y = event.getY();
            this.direction = event.getDirection();
            this.speed = event.getSpeed();
        }

        public int getScannedByBotId() {
            return this.scannedByBotId;
        }

        public int getScannedBotId() {
            return this.scannedBotId;
        }

        public double getEnergy() {
            return this.energy;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getDirection() {
            return this.direction;
        }

        public double getSpeed() {
            return this.speed;
        }
    }

}
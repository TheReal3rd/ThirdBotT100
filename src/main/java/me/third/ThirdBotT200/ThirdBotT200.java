package me.third.ThirdBotT200;

import dev.robocode.tankroyale.botapi.Bot;
import dev.robocode.tankroyale.botapi.BotInfo;
import dev.robocode.tankroyale.botapi.Color;
import dev.robocode.tankroyale.botapi.events.*;

import java.util.HashMap;


public class ThirdBotT200 extends Bot {
    private final HashMap<Integer, TargetInfo> targetList = new HashMap<>(10);

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
        new ThirdBotT200().start();
    }

    ThirdBotT200() {
        super(BotInfo.fromFile("ThirdBotT200.json"));
    }

    /*
    RUN
     */
    @Override
    public void run() {
        setBodyColor(Color.WHITE);
        setTurretColor(Color.WHITE);
        setRadarColor(Color.GREEN);
        setScanColor(Color.GREEN);

        targetList.clear();
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
        if(!isRunning()) return;
        radarAngle = getRadarDirection();

        /*
        Radar Initial scan.
         */
        if(!fullScan) {
            setGunColor(Color.GREEN);
            setRadarTurnRate(20);
            if (getRadarTurnRemaining() <= 0) {
                turnRadarLeft(20);
            }
            fullScan = (int) radarAngle >= 360 - (getRadarTurnRate() + 2);
        } else {
            setGunColor(Color.RED);
            /*
            Radar Target scans.
             */
            TargetInfo targetInfo = null;
            if(lockedTarget == -1) {
                targetInfo = findClosest();
                if(targetInfo == null) {
                    fullScan = false;
                    return;
                }

                lockedTarget = targetInfo.getScannedBotId();
            }
            if(lockedTarget == -1) {
                fullScan = false;
                return;
            }

            targetInfo = targetList.get(lockedTarget);
            double targetAngle = radarBearingTo(targetInfo.getX(), targetInfo.getY());

            if (Math.abs(targetAngle) >= 50 || lockScanMissCount >= 3) {
                if (getRadarTurnRemaining() <= 15) {
                    setRadarTurnRate(getRadarTurnRemaining());
                } else {
                    setRadarTurnRate(15);
                }

                final TurnDir dir = bestTurnDirection(radarAngle, targetAngle);
                switch(dir) {
                    case Right:
                        setTurnRadarRight(targetAngle);
                    case Left:
                        setTurnRadarLeft(targetAngle);
                }

            } else {
                if (lockScanToggle) {
                    setRadarTurnRate(60);
                    turnRadarLeft(60);
                } else {
                    setRadarTurnRate(60);
                    turnRadarRight(60);
                }
                lockScanToggle = !lockScanToggle;
                lockScanMissCount++;
            }
        }

        if(fireMissCount >= 3) {
            targetList.clear();
            fireMissCount = 0;
        }
    }


    @Override
    public void onRoundStarted(RoundStartedEvent roundStartedEvent) {
        resetTargeting();
        targetList.clear();
    }

    @Override
    public void onScannedBot(ScannedBotEvent scannedBotEvent) {
        if(targetList.containsKey(scannedBotEvent.getScannedBotId())) {
            TargetInfo targetInfo = targetList.get(scannedBotEvent.getScannedBotId());
            targetInfo.updateState(scannedBotEvent);

            if(!fullScan) return;

            /*
            Start targeting and attacking.
             */
            if(lockedTarget == -1) {
                targetInfo = findClosest();
                if(targetInfo == null) {
                    fullScan = false;
                    return;
                }

                lockedTarget = targetInfo.getScannedBotId();
            }
            if(lockedTarget == -1) {
                fullScan = false;
                return;
            }
            targetInfo = targetList.get(lockedTarget);

            double bulletSpeed = calcBulletSpeed(Math.min(5, getEnergy()));// Speed of 5 is 11
            double distance = distanceTo(targetInfo.getX(), targetInfo.getY());
            final int predictTime = (int) Math.round(distance / bulletSpeed);
            //final int fireDelay = (int) ((int) getGunHeat() / getGunCoolingRate()); Bad idea no need.
            final int timeSinceLastUpdate = targetInfo.getTurn() - targetInfo.getPrevTurn();

            final int futureTickAmount = (predictTime  + timeSinceLastUpdate);

            final Vector2D predictPos = calcPredictedPosition(futureTickAmount, targetInfo);
            double targetAngle = gunBearingTo(predictPos.getPosX(), predictPos.getPosY());

            if (getGunTurnRemaining() <= targetAngle) {
                setGunTurnRate(Math.min(1, getGunTurnRemaining()));
            } else {
                setGunTurnRate(targetAngle);
            }

            final TurnDir dir = bestTurnDirection(getGunDirection(), targetAngle);
            switch(dir) {
                case Right:
                    setTurnGunRight(targetAngle);
                case Left:
                    setTurnGunLeft(targetAngle);
            }

            if(getGunTurnRemaining() <= 0 && getGunHeat() <= 0) {
                fire(Math.min(5, getEnergy()));
            }

        } else {
            targetList.putIfAbsent(scannedBotEvent.getScannedBotId(), new TargetInfo(scannedBotEvent));
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
            lockedTarget = -1;
        }
        targetList.remove(botDeathEvent.getVictimId());
    }

    /*
    Utils
     */

    public void resetTargeting() {
        this.lockedTarget = -1;
        this.fullScan = false;
        this.lockScanToggle = false;
        this.lockScanMissCount = 0;
    }

    public double distanceBetween(double posX, double posY, double posX1, double posY1) {
        return Math.hypot(posX - posX1, posY - posY1);
    }

    public Vector2D calcPredictedPosition(int ticksAhead, TargetInfo targetInfo) {
        double posX = targetInfo.getX();
        double posY = targetInfo.getY();
        double dir = targetInfo.getDirection();
        double speed = targetInfo.getSpeed();

        int tickDifferance = targetInfo.getTurn() - targetInfo.getPrevTurn();
        double turnRate = (targetInfo.getDirection() - targetInfo.getPrevDirection()) / tickDifferance;

        for(int ticks = 0; ticks != ticksAhead; ticks++) {
            double angleRad = Math.toRadians(dir);
            double velX = speed * Math.cos(angleRad);
            double velY = speed * Math.sin(angleRad);

            if(posX + velX >= getArenaWidth() || posX <= 0 || posY + velY >= getArenaHeight() || posY <= 0) {
                return new Vector2D(posX, posY);
            }

            posX += velX;
            posY += velY;
            dir = (dir + turnRate) % 360;
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
        private double x;
        private double y;
        private double direction;
        private double speed;
        private int turn;

        private double prevX = 0;
        private double prevY = 0;
        private double prevDirection = 0;
        private double prevSpeed = 0;
        private int prevTurn = 0;

        public TargetInfo(ScannedBotEvent event) {
            this.scannedByBotId = event.getScannedByBotId();
            this.scannedBotId = event.getScannedBotId();
            this.energy = event.getEnergy();
            this.x = event.getX();
            this.y = event.getY();
            this.direction = event.getDirection();
            this.speed = event.getSpeed();
            this.turn = event.getTurnNumber();
        }

        public void updateState(ScannedBotEvent event) {
            this.prevX = x;
            this.prevY = y;
            this.prevDirection = direction;
            this.prevSpeed = speed;
            this.prevTurn = turn;

            this.x = event.getX();
            this.y = event.getY();
            this.direction = event.getDirection();
            this.speed = event.getSpeed();
            this.turn = event.getTurnNumber();
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

        public double getPrevX() {
            return prevX;
        }

        public double getPrevY() {
            return prevY;
        }

        public double getPrevSpeed() {
            return prevSpeed;
        }

        public double getPrevDirection() {
            return prevDirection;
        }

        public int getTurn() {
            return turn;
        }

        public int getPrevTurn() {
            return prevTurn;
        }
    }

}
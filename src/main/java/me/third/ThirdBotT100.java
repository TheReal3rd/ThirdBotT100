package me.third;

import dev.robocode.tankroyale.botapi.Bot;
import dev.robocode.tankroyale.botapi.BotInfo;
import dev.robocode.tankroyale.botapi.Color;
import dev.robocode.tankroyale.botapi.events.BotDeathEvent;
import dev.robocode.tankroyale.botapi.events.ScannedBotEvent;
import dev.robocode.tankroyale.botapi.events.TickEvent;

import java.util.HashMap;


public class ThirdBotT100 extends Bot {
    private final HashMap<Integer, TargetInfo> targetList = new HashMap<>();

    private States currentState = States.Seek;

    private boolean fullScan = false;
    private boolean lockScanToggle = false;
    private int lockScanMissCount = 0;
    private int lockScanTickCount = 0;

    private double radarAngle = -1;

    private int lockedTarget = -1;
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

                    if (targetAngle < 0) {
                        self.setTurnRadarLeft(targetAngle);
                    } else {
                        self.setTurnRadarRight(targetAngle);
                    }
                } else {
                    if(self.lockScanTickCount >= 5) {
                        self.setRadarTurnRate(30);
                        if (self.lockScanToggle) {
                            self.turnRadarLeft(30);
                        } else {
                            self.turnRadarRight(60);
                            self.lockScanTickCount = 0;
                        }
                        self.lockScanToggle = !self.lockScanToggle;
                        self.lockScanMissCount++;
                    } else {
                        self.lockScanTickCount++;
                    }
                }
                /*
                GUN
                 */
                targetAngle = self.gunBearingTo(targetInfo.getX(), targetInfo.getY());
                //System.out.println(targetAngle);

                if (self.getGunTurnRemaining() <= 15) {
                    self.setGunTurnRate(self.getGunTurnRemaining());
                } else {
                    self.setGunTurnRate(15);
                }

                if (targetAngle < 0) {
                    self.setTurnGunLeft(targetAngle);
                } else {
                    self.setTurnGunRight(targetAngle);
                }

                if(self.getGunTurnRemaining() <= 2) {
                    if (self.getGunHeat() <= 0) {
                        if (targetInfo.getSpeed() <= 0) {
                            self.fire(5);
                        } else {
                            self.fire(1);
                        }
                    }
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
    }

    /*
    Utils
     */

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
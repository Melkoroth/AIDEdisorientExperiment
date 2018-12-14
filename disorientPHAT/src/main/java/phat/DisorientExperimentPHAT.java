package phat;

import java.io.*;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.swing.JFrame;
import javax.swing.JPanel;

import com.jme3.app.state.AppStateManager;

import com.jme3.scene.Node;
import phat.agents.Agent;
import phat.agents.HumanAgent;
import phat.agents.automaton.*;
import phat.agents.automaton.conditions.TimerFinishedCondition;
import phat.agents.commands.ActivateActuatorEventsLauncherCommand;
import phat.agents.commands.ActivateCallStateEventsLauncherCommand;
import phat.body.BodiesAppState;
import phat.body.commands.*;
import phat.commands.PHATCommand;
import phat.commands.PHATCommandListener;
import phat.config.AgentConfigurator;
import phat.config.BodyConfigurator;
import phat.config.DeviceConfigurator;
import phat.config.HouseConfigurator;
import phat.config.ServerConfigurator;
import phat.config.WorldConfigurator;
import phat.devices.commands.CreatePresenceSensorCommand;
import phat.sensors.Sensor;
import phat.sensors.SensorData;
import phat.sensors.SensorListener;
import phat.sensors.presence.PHATPresenceSensor;
import phat.sensors.presence.PresenceData;
import phat.sensors.presence.PresenceStatePanel;
import phat.server.ServerAppState;
import phat.server.commands.CreateAllPresenceSensorServersCommand;
import phat.structures.houses.HouseFactory;
import phat.world.WorldAppState;

/**
 * Phat Disorient Experiment
 * @author melkoroth
 */
public class DisorientExperimentPHAT implements PHATInitializer, PHATCommandListener, SensorListener {
    static GUIPHATInterface phat;

    private static LocalDateTime now = LocalDateTime.now();
    private static String fileName = "experiment";
    private static String fileExtension = ".txt";
    private static String dateStr = "-" + now.getYear() + now.getMonthValue() + now.getDayOfMonth() + now.getHour() + now.getMinute() + now.getSecond();
    private static String fileOut = fileName + dateStr + fileExtension;

    private final String bodyId = "Patient";
    private final String houseId = "House1";
    private JFrame sensorMonitor;

    //Presence sensors vars
    private final String bedroomID = "PreSen-Bedroom1-1";
    private final String kitchenID = "PreSen-Kitchen-1";
    private final String hallID = "PreSen-Hall-4";
    private final String bathroomID = "PreSen-Bathroom1-1";
    private final String livingroom0ID = "PreSen-Living-1";
    private final String livingroom1ID = "PreSen-Living-3";

    private final String[] sensorIDs = {bedroomID, kitchenID, hallID, bathroomID, livingroom0ID, livingroom1ID};

    //Declare agent
    Agent agent = new HumanAgent(bodyId);
    //Create and populate Finite State Machine
    FSM fsm = new FSM(agent);


    public static void main(String[] args) throws IOException {
        //String[] a = {"-record"};
        DisorientExperimentPHAT sim = new DisorientExperimentPHAT();
        phat = new GUIPHATInterface(sim);//, new GUIArgumentProcessor());
        phat.setStatView(true);
        phat.setDisplayFPS(true);
        phat.setSeed(0);
        phat.setDisplayHeight(800);
        phat.setDisplayWidth(480);
        phat.setTittle("PhatDisorientExperiment");
        phat.start();
        //Hide prettyLogger from GUI
        phat.hidePrettyLogger();

        //Start output file with column names
        appendToFile("timestamp id elapsedGen elapsedPart status");
    }

    @Override
    public void initWorld(WorldConfigurator worldConfig) {
        worldConfig.setTime(2018, 1, 1, 14, 30, 0);
        worldConfig.setTimeVisible(true);
        worldConfig.setLandType(WorldAppState.LandType.Grass);
    }

    @Override
    public void initHouse(HouseConfigurator houseConfig) {
        houseConfig.addHouseType(houseId, HouseFactory.HouseType.House3room2bath);
    }

    @Override
    public void initBodies(BodyConfigurator bodyConfig) {
        bodyConfig.createBody(BodiesAppState.BodyType.Young, bodyId);
        bodyConfig.runCommand(new BodyLabelCommand(bodyId, true));
        bodyConfig.setInSpace(bodyId, houseId, "BedRoom1LeftSide");
        bodyConfig.runCommand(new SetBodyHeightCommand(bodyId, 1.7f));
    }

    @Override
    public void initDevices(DeviceConfigurator deviceConfig) {
        long now = phat.getSimTime().getTimeInMillis() / 1000;
        //lastPresenceTimestamp = now;
        for (int i = 0; i < sensorIDs.length; i++) {
            //pSensors[i].lastToggle = now;
            createPrenceSensor(sensorIDs[i]);
        }

        AppStateManager stateManager = phat.app.getStateManager();
        stateManager.attach(phat.deviceConfig.getDevicesAppState());
        //Create GUI sensor monitor
        sensorMonitor = new JFrame("Sensor Monitoring");
        JPanel content = new JPanel();
        sensorMonitor.setContentPane(content);
        sensorMonitor.setVisible(true);
        //Attach sensor to scene
        ServerAppState serverAppState = new ServerAppState();
        stateManager.attach(serverAppState);
        serverAppState.runCommand(new CreateAllPresenceSensorServersCommand());
    }

    private CreatePresenceSensorCommand createPrenceSensor(String id) {
        CreatePresenceSensorCommand cpsc = new CreatePresenceSensorCommand(id, this);
        cpsc.setEnableDebug(true);
        cpsc.sethAngle(90f);
        cpsc.setvAngle(45f);
        cpsc.setAngleStep(10f);
        cpsc.setListener(this);
        phat.deviceConfig.getDevicesAppState().runCommand(cpsc);
        return cpsc;
    }

    @Override
    public void initServer(ServerConfigurator deviceConfig) { }

    @Override
    public void initAgents(AgentConfigurator agentsConfig) {
        DoNothing waitzIni = new DoNothing(agent, "waitzIni");
        waitzIni.setFinishCondition(new TimerFinishedCondition(0,0,5));
        fsm.registerStartState(waitzIni);

        SimpleState prevState = waitzIni;
        for (int i = 0; i < 200; i++) {
            SimpleState state = doSomethingRandom(agent, fsm, prevState, i);
            prevState = state;
        }

        //DoNothing waitz = new DoNothing(agent, "waitz");
        //waitz.setFinishCondition(new TimerFinishedCondition(0,0,5));
        DoNothing waitzEnd = new DoNothing(agent, "waitzEnd");
        waitzEnd.setFinishCondition(new TimerFinishedCondition(0,0,5));
        fsm.registerFinalState(waitzEnd);

        fsm.addListener(new AutomatonIcon());
        //Link FSM with agent
        agent.setAutomaton(fsm);
        agentsConfig.add(agent);

        System.setProperty("java.util.logging.config.class", "");
        Logger.getLogger("").setLevel(Level.ALL);

        agentsConfig.runCommand(new ActivateActuatorEventsLauncherCommand(null));
        agentsConfig.runCommand(new ActivateCallStateEventsLauncherCommand(null));
    }

    public SimpleState doSomethingRandom(Agent agent, FSM fsm, SimpleState prev, int i) {
        int prob = ThreadLocalRandom.current().nextInt(0, 100);

        //Theres a 0.4 probability of getting lost in the house
        if (prob >= 0 && prob < 32) {
            return doSomethingKitchen(prev, i);
        } else if (prob >= 32 && prob < 64) {
            return doSomethingLiving(prev, i);
        } else if (prob >= 64 && prob < 96) {
            return doSomethingBathroom(prev, i);
        } else if (prob >= 96 && prob <= 99) {
            //appendToFile("Gonna get lost!");
            return doGetLost(prev, i);
        }
        return null;
    }

    public SimpleState doSomethingKitchen(SimpleState prev, int i) {
        MoveToSpace moveToKitchen = new MoveToSpace(agent, "GoToKitchen" + i, "Kitchen");
        fsm.registerTransition(prev, moveToKitchen);
        UseObjectAutomaton useSink = new UseObjectAutomaton(agent, "Sink");
        useSink.setFinishCondition(new TimerFinishedCondition(0, ThreadLocalRandom.current().nextInt(1, 2), 0));
        fsm.registerTransition(moveToKitchen, useSink);
        return useSink;
    }

    public SimpleState doSomethingLiving(SimpleState prev, int i) {
        MoveToSpace moveToLiving = new MoveToSpace(agent, "GoToLiving" + i, "LivingRoom");
        fsm.registerTransition(prev, moveToLiving);
        UseObjectAutomaton useSofa = new UseObjectAutomaton(agent, "ArmChair1");
        useSofa.setFinishCondition(new TimerFinishedCondition(0, ThreadLocalRandom.current().nextInt(1, 2), 0));
        fsm.registerTransition(moveToLiving, useSofa);
        return useSofa;
    }

    public SimpleState doSomethingBathroom(SimpleState prev, int i) {
        MoveToSpace moveToBathroom = new MoveToSpace(agent, "GoToBathroom" + i, "BathRoom1");
        fsm.registerTransition(prev, moveToBathroom);
        UseObjectAutomaton useWC = new UseObjectAutomaton(agent, "WC1");
        useWC.setFinishCondition(new TimerFinishedCondition(0, ThreadLocalRandom.current().nextInt(1, 2), 0));
        fsm.registerTransition(moveToBathroom, useWC);
        return useWC;
    }

    public SimpleState doGetLost(SimpleState prev, int i) {
        MoveToSpace moveToBathroom = new MoveToSpace(agent, "GoToBathroom" + i, "BathRoom1");
        fsm.registerTransition(prev, moveToBathroom);
        DoNothing wait = new DoNothing(agent, "wait" + i);
        wait.setFinishCondition(new TimerFinishedCondition(0,0,1));;
        fsm.registerTransition(moveToBathroom, wait);
        return wait;
    }


    @Override
    public String getTittle() {
        return "PHAT Presencen Disorient Detector Experiment";
    }

    @Override
    public String getDescription() {
        return "This is a proof of concept simulation where the patient does disorient oocasionally";
    }

    //Called at init
    @Override
    public void commandStateChanged(PHATCommand command) {
        if (command instanceof CreatePresenceSensorCommand) {
            CreatePresenceSensorCommand cpsc = (CreatePresenceSensorCommand) command;
            Node psNode = phat.deviceConfig.getDevicesAppState().getDevice(cpsc.getPresenceSensorId());
            if (psNode != null) {
                PHATPresenceSensor psControl = psNode.getControl(PHATPresenceSensor.class);
                if (psControl != null) {
                    PresenceStatePanel psp1 = new PresenceStatePanel();
                    psControl.add(psp1);
                    sensorMonitor.getContentPane().add(psp1);
                    sensorMonitor.pack();
                    //Attach sensor state listeners
                    phat.deviceConfig.getDevicesAppState().getDevice(psControl.getId()).getControl(PHATPresenceSensor.class).add(this);
                }
            }
        }
    }

    //Called every time a sensor triggers
    @Override
    public void update(Sensor sensor, SensorData sensorData) {
        //Get sensor data
        PHATPresenceSensor ps = (PHATPresenceSensor)sensor;
        PresenceData pd = ps.getPresenceData();

        if (pd.isPresence()) {
            appendToFile(pd.getTimestamp()/1000 + " " + ps.getId() + " " + pd.isPresence());
        }
    }

    public static void appendToFile(String data) {
        try {
            FileWriter fw = new FileWriter(fileOut, true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(data);
            bw.newLine();
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void cleanUp() {

    }
}
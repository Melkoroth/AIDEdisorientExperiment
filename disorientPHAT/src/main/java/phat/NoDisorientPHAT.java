package phat;

import java.io.*;
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
public class NoDisorientPHAT implements PHATInitializer, PHATCommandListener, SensorListener {
    static GUIPHATInterface phat;

    private static String fileName = "nodisorient.txt";

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

    class PresenceSensor {
        public String id;
        public long lastToggle = 0;
        public PresenceSensor(String id) { this.id = id; }
    }

    //Array of info of sensors
    private final PresenceSensor pSensors[] = new PresenceSensor[] {
            new PresenceSensor(bedroomID),
            new PresenceSensor(kitchenID),
            new PresenceSensor(hallID),
            new PresenceSensor(bathroomID),
            new PresenceSensor(livingroom0ID),
            new PresenceSensor(livingroom1ID)
    };

    //Stores general timestamp
    long lastPresenceTimestamp;

    private PresenceSensor getPsByKey(String key) {
        for (int i = 0; i < pSensors.length; i++) {
            if (pSensors[i].id.equals(key)) {
                return pSensors[i];
            }
        }
        return null;
    }

    public static void main(String[] args) throws IOException {
        //String[] a = {"-record"};
        NoDisorientPHAT sim = new NoDisorientPHAT();
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
        FileWriter fw = new FileWriter(fileName);
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write("timestamp id elapsedGen elapsedPart status");
        bw.newLine();
        bw.close();
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
        for (int i = 0; i < pSensors.length; i++) {
            //pSensors[i].lastToggle = now;
            createPrenceSensor(pSensors[i].id);
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
        //Declare agent
        Agent agent = new HumanAgent(bodyId);

        MoveToSpace moveToKitchen2 = new MoveToSpace(agent, "GoToKitchen1", "Kitchen");

        UseObjectAutomaton useSink2 = new UseObjectAutomaton(agent, "Sink");
        useSink2.setFinishCondition(new TimerFinishedCondition(0, 5, 0));
        MoveToSpace moveToLiving2 = new MoveToSpace(agent, "GoToLiving2", "LivingRoom");
        DoNothing wait2 = new DoNothing(agent, "wait2");
        wait2.setFinishCondition(new TimerFinishedCondition(0,0,5));

        //Create and populate Finite State Machine
        FSM fsm = new FSM(agent);
        fsm.registerStartState(moveToKitchen2);
        fsm.registerTransition(moveToKitchen2, useSink2);
        fsm.registerTransition(useSink2, moveToLiving2);
        fsm.registerTransition(moveToLiving2, wait2);
        fsm.registerFinalState(wait2);

        fsm.addListener(new AutomatonIcon());
        //Link FSM with agent
        agent.setAutomaton(fsm);
        agentsConfig.add(agent);

        System.setProperty("java.util.logging.config.class", "");
        Logger.getLogger("").setLevel(Level.ALL);

        agentsConfig.runCommand(new ActivateActuatorEventsLauncherCommand(null));
        agentsConfig.runCommand(new ActivateCallStateEventsLauncherCommand(null));
    }

    @Override
    public String getTittle() {
        return "PHAT Presence No Disorient Experiment";
    }

    @Override
    public String getDescription() {
        return "This is a proof of concept simulation where the patient does not disorient";
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
        //Get sensor in array
        PresenceSensor sens = getPsByKey(ps.getId());
        //Convert to secs
        long now = pd.getTimestamp() / 1000;

        System.out.println("**********");
        System.out.print("Timestamp: " + now);
        System.out.print(" | ID: " + ps.getId());
        long elapsedGen = now - lastPresenceTimestamp;
        //Reflect it's the first time we reflect this value
        if (elapsedGen > 100000)
            elapsedGen = -1;
        System.out.print(" | Elapsed general: " + elapsedGen);
        long elapsedPar = now - sens.lastToggle;
        if (elapsedPar > 100000)
            elapsedPar = -1;
        System.out.print(" | Elapsed particular: " + elapsedPar);
        System.out.println(" | State: " + pd.isPresence());

        try {
            appendToFile(now + " " + ps.getId() + " " + elapsedGen + " " + elapsedPar + " " + pd.isPresence());
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Reset time vars
        sens.lastToggle = now;
        lastPresenceTimestamp = now;
    }

    public void appendToFile(String data) throws IOException {
        FileWriter fw = new FileWriter(fileName, true);
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(data);
        bw.newLine();
        bw.close();
    }

    @Override
    public void cleanUp() {

    }
}
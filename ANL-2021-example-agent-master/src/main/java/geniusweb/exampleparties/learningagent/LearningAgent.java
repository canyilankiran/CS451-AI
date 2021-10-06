package geniusweb.exampleparties.learningagent; // TODO: change name

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;

import geniusweb.actions.FileLocation;

import geniusweb.actions.Accept;
import geniusweb.actions.Action;
import geniusweb.actions.LearningDone;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.bidspace.AllBidsList;
import geniusweb.inform.ActionDone;
import geniusweb.inform.Agreements;
import geniusweb.inform.Finished;
import geniusweb.inform.Inform;
import geniusweb.inform.Settings;
import geniusweb.inform.YourTurn;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.UtilitySpace;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import geniusweb.references.Parameters;
import tudelft.utilities.logging.Reporter;

import com.fasterxml.jackson.databind.ObjectMapper;

public class LearningAgent extends DefaultParty { // TODO: change name

    private Bid lastReceivedBid = null;
    private PartyId me;
    private final Random random = new Random();
    protected ProfileInterface profileint = null;
    private Progress progress;
    private String protocol;
    private Parameters parameters;
    private UtilitySpace utilitySpace;
    private PersistentState persistentState;
    private NegotiationData negotiationData;
    private List<File> dataPaths;
    private File persistentPath;
    private String opponentName;
    private AllBidsList bidspace;
    private List<Bid> bidsList;
    private OpponentModel opponentModel;
    private List<Bid> receivedBids;
    private double AverageOfReceivedBids;
    private double opponentExpectedConsseionTime = 0.0;
    private ArrayList<Double> opponentExpectedUtilities;
    private double difference;
    private Bid mostOfferedBid;
    private double mostOfferedBidValue = 0.0;
    private HashMap<Double,Double> diffTime;
    private double minimumAcceptableUtilityValue = 1.0; //
    private double offerableAvg; //
    private Action action; //


    public LearningAgent() { // TODO: change name
    }

    public LearningAgent(Reporter reporter) { // TODO: change name
        super(reporter); // for debugging
    }

    /**
     * This method mostly contains utility functionallity for the agent to function
     * properly. The code that is of most interest for the ANL competition is
     * further below and in the other java files in this directory. It does,
     * however, not hurt to read through this code to have a better understanding of
     * what is going on.
     *
     * @param info information object for agent
     */
    @Override
    public void notifyChange(Inform info) {
        try {
            if (info instanceof Settings) {
                // info is a Settings object that is passed at the start of a negotiation
                Settings settings = (Settings) info;

                // ID of my agent
                this.me = settings.getID();

                // The progress object keeps track of the deadline
                this.progress = settings.getProgress();

                // Protocol that is initiate for the agent
                this.protocol = settings.getProtocol().getURI().getPath();


                // Parameters for the agent (can be passed through the GeniusWeb GUI, or a
                // JSON-file)
                this.parameters = settings.getParameters();

                // The PersistentState is loaded here (see 'PersistenData,java')
                if (this.parameters.containsKey("persistentstate"))
                    this.persistentPath = new FileLocation(
                            UUID.fromString((String) this.parameters.get("persistentstate"))).getFile();
                if (this.persistentPath != null && this.persistentPath.exists()) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    this.persistentState = objectMapper.readValue(this.persistentPath, PersistentState.class);
                } else {
                    this.persistentState = new PersistentState();
                }

                // The negotiation data paths are converted here from List<String> to List<File>
                // for improved usage. For safety reasons, this is more comprehensive than
                // normally.
                if (this.parameters.containsKey("negotiationdata")) {
                    List<String> dataPaths_raw = (List<String>) this.parameters.get("negotiationdata");
                    this.dataPaths = new ArrayList<>();
                    for (String path : dataPaths_raw)
                        this.dataPaths.add(new FileLocation(UUID.fromString(path)).getFile());
                }
                if ("Learn".equals(protocol)) {
                    // We are in the learning step: We execute the learning and notify when we are
                    // done. REMEMBER that there is a deadline of 60 seconds for this step.
                    learn();
                    getConnection().send(new LearningDone(me));
                } else {
                    // We are in the negotiation step.

                    // Create a new NegotiationData object to store information on this negotiation.
                    // See 'NegotiationData.java'.
                    this.negotiationData = new NegotiationData();

                    // Obtain our utility space, i.e. the problem we are negotiating and our
                    // preferences over it.
                    try {
                        this.profileint = ProfileConnectionFactory.create(settings.getProfile().getURI(),
                                getReporter());
                        this.utilitySpace = ((UtilitySpace) profileint.getProfile());
                        this.opponentModel = new OpponentModel(utilitySpace.getDomain(),opponentName);
                        this.bidspace = new AllBidsList(this.utilitySpace.getDomain());
                        this.bidsList = new ArrayList<>();
                        this.receivedBids = new ArrayList<>();
                        this.opponentExpectedUtilities = new ArrayList<>();
                        this.diffTime = new HashMap<>();
                        if(bidspace.size().intValue() > 0) {
                            for (Bid bid : this.bidspace) {
                                this.bidsList.add(bid);
                            }
                        }
                        sortBidsByUtility(this.bidsList);
//                        getReporter().log(Level.INFO,"Sorted bid list");
//                        for (Bid bid :bidsList) {
//                            getReporter().log(Level.INFO, "Bid with utility value: " + this.utilitySpace.getUtility(bid));
//                        }
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            } else if (info instanceof ActionDone) {
                // The info object is an action that is performed by an agent.
                Action action = ((ActionDone) info).getAction();

                // Check if this is not our own action
                if (!this.me.equals(action.getActor())) {
                    // Check if we already know who we are playing against.
                    if (this.opponentName == null) {
                        // The part behind the last _ is always changing, so we must cut it off.
                        String fullOpponentName = action.getActor().getName();
                        int index = fullOpponentName.lastIndexOf("_");
                        this.opponentName = fullOpponentName.substring(0, index);
                        // Add name of the opponent to the negotiation data
                        this.negotiationData.setOpponentName(this.opponentName);
                    }
                    // Process the action of the opponent.
                    processAction(action);
                }
            } else if (info instanceof YourTurn) {
                // Advance the round number if a round-based deadline is set.
                if (progress instanceof ProgressRounds) {
                    progress = ((ProgressRounds) progress).advance();
                }

                // The info notifies us that it is our turn
                   myTurn();
            } else if (info instanceof Finished) {
                // The info is a notification that th negotiation has ended. This Finished
                // object also contains the final agreement (if any).
                Agreements agreements = ((Finished) info).getAgreement();
                processAgreements(agreements);

                // Write the negotiation data that we collected to the path provided.
                if (this.dataPaths != null && this.negotiationData != null) {
                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        objectMapper.writerWithDefaultPrettyPrinter().writeValue(this.dataPaths.get(0),
                                this.negotiationData);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to write negotiation data to disk", e);
                    }
                }

                // Log the final outcome and terminate
                getReporter().log(Level.INFO, "Final outcome:" + info);
                terminate();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle info", e);
        }
    }

    /** Let GeniusWeb know what protocols that agent is capable of handling */
    @Override
    public Capabilities getCapabilities() {
        return new Capabilities(new HashSet<>(Arrays.asList("SAOP", "Learn")), Collections.singleton(Profile.class));
    }

    /** Terminate agent */
    @Override
    public void terminate() {
        super.terminate();
        if (this.profileint != null) {
            this.profileint.close();
            this.profileint = null;
        }
    }

    /*
     * *****************************NOTE:************************************
     * Everything below this comment is most relevant for the ANL competition.
     * **********************************************************************
     */

    /** Provide a description of the agent */
    @Override
    public String getDescription() {
        return "This is the example party of ANL 2021. It can handle the Learn protocol and learns simple characteristics of the opponent.";
    }

    /**
     * Processes an Action performed by the opponent.
     *
     * @param action
     */
    private void processAction(Action action) {
        if (action instanceof Offer) {
            // If the action was an offer: Obtain the bid and add it's value to our
            this.lastReceivedBid = ((Offer) action).getBid();
            this.opponentModel.updateFrequency(this.lastReceivedBid);
//            getReporter().log(Level.INFO, "lastreceivedbid" + lastReceivedBid);
            this.negotiationData.setOpponentName(this.opponentName);
            this.negotiationData.addBidUtil(this.utilitySpace.getUtility(this.lastReceivedBid).doubleValue());
            this.receivedBids.add(this.lastReceivedBid);
            this.opponentModel.lastReceivedBids.add(lastReceivedBid);
            if(this.opponentModel.isWindowReady()){
                if(this.opponentModel.isUpdateWeigthReady()){
                    this.opponentModel.updateWeights(progress.get(System.currentTimeMillis()));
                }
            }
            if(opponentModel.consseion_time != 0.0){
                this.opponentExpectedConsseionTime = opponentModel.consseion_time;
//                System.out.println("Concession time !!!!!!!!!!!!!!!! : " + this.opponentExpectedConsseionTime);
            }
            this.opponentExpectedUtilities.add(this.opponentModel.calculateDesirability(lastReceivedBid));
            if(this.opponentExpectedUtilities.size() >= 2){
                Double timee2 = progress.get(System.currentTimeMillis());
                this.difference = this.opponentExpectedUtilities.get(this.opponentExpectedUtilities.size()-1) - this.opponentExpectedUtilities.get(this.opponentExpectedUtilities.size()-2);
                this.negotiationData.addDifferenceTime(timee2,difference);
//                System.out.println("After the addition of the negodata !!!!!!!!!!!" +this.negotiationData.diffTime);
//                System.out.println("The difference between bit" + this.difference);
                double dif = this.difference;
                //reyhan hoca AC next simulated here -alp
                if(dif >= 0.1) {
                    if ((this.opponentExpectedUtilities.get(this.opponentExpectedUtilities.size() - 1)) >= this.mostOfferedBidValue) //offerable bid tablosundan seçiyoruz
                        minimumAcceptableUtilityValue = mostOfferedBidValue * offerableAvg;
                }
            }


        }

    }

    /**
     * This method is called when the negotiation has finished. It can process the
     * final agreement.
     *
     * @param agreements
     */
    private void processAgreements(Agreements agreements) {
        // Check if we reached an agreement (walking away or passing the deadline
        // results in no agreement)
        if (!agreements.getMap().isEmpty()) {
            // Get the bid that is agreed upon and add it's value to our negotiation data
            Bid agreement = agreements.getMap().values().iterator().next();
            this.negotiationData.addAgreementUtil(this.utilitySpace.getUtility(agreement).doubleValue());
        }
    }

    /**
     * send our next offer
     */
    private void myTurn() throws IOException {
        getReporter().log(Level.INFO, "****************************************MY NAME:" + me.getName());
        getReporter().log(Level.INFO, "****************************************Opponent name name:" + this.opponentName);
        getReporter().log(Level.INFO, "******************************************Is opponent known? :" + this.persistentState.knownOpponent(this.opponentName));

        if (lastReceivedBid == null) {
            action = makeAnOffer();
        } else {
            double upComing = utilitySpace.getUtility(lastReceivedBid).doubleValue();

            if (upComing >= 0.90) { // If the last received bid is good but not good as 0.93: create Accept action
                action = new Accept(me, lastReceivedBid);
            } else if (isGood(lastReceivedBid) && (SD(receivedBids) >= Math.abs(AverageOfReceivedBids - upComing))) { //standart sapma => ortalama - bid; //sd eklenecek.
                if(this.utilitySpace.getUtility(lastReceivedBid).doubleValue() >= Top50Percentile(bidsList))
                    action = new Accept(me, lastReceivedBid);
            } else {
                action = makeAnOffer();
            }
            // Send action
        }
        getConnection().send(action);
    }
    private void sortBidsByUtility(List<Bid> bidlist)
    {
        bidlist.sort((b1,b2) -> utilitySpace.isPreferredOrEqual(b1,b2) ? 1 : -1);
    }

    /**
     * The method checks if a bid is good.
     *
     * @param bid the bid to check
     * @return true iff bid is good for us.
     */
    private boolean isGood(Bid bid) {
        if (bid == null)
            return false;



        // Check a simple business rule
        double opponents_utility_for_us = utilitySpace.getUtility(lastReceivedBid).doubleValue();

        double minimumAcceptableUtilityValue = 1.0;
        double time = progress.get(System.currentTimeMillis());
        Boolean nearDeadline = progress.get(System.currentTimeMillis()) > 0.95;
        Boolean acceptable = this.utilitySpace.getUtility(bid).doubleValue() > 0.7;
        Boolean good = this.utilitySpace.getUtility(bid).doubleValue() > 0.9;
        //agent changes its time dependent acceptance strategy throughout the passed percentage of session
        if(time <= 0.3) {
            minimumAcceptableUtilityValue =  minimumAcceptableUtilityValue - (Math.pow(time,2)   * 0.7);
            return minimumAcceptableUtilityValue <= opponents_utility_for_us;
        }
        else if(time > 0.3 && time <= 0.95) {
            minimumAcceptableUtilityValue = minimumAcceptableUtilityValue - Math.pow((time/2),2) - 0.05;
            return minimumAcceptableUtilityValue <= opponents_utility_for_us;
        }
        return (nearDeadline && acceptable) || good;

    }

    /**
     * This method is invoked if the learning phase is started. There is now time to
     * process previously stored data and use it to update our persistent state.
     * This persistent state is passed to the agent again in future negotiation
     * session. REMEMBER that there is a deadline of 60 seconds for this step.
     */
    private void learn() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Iterate through the negotiation data file paths
        for (File dataPath : this.dataPaths) {
            NegotiationData negotiationData;
            try {
                // Load the negotiation data object of a previous negotiation
                negotiationData = objectMapper.readValue(dataPath, NegotiationData.class);
            } catch (IOException e) {
                throw new RuntimeException("Negotiation data provided to learning step does not exist", e);
            }

            // Process the negotiation data in our persistent state
            this.persistentState.update(negotiationData);
        }

        // Write the persistent state object to file
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(this.persistentPath, this.persistentState);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write persistent state to disk", e);
        }
    }


    //    //BURDAN SONRASINI NURETTIN KOYDU
    private double timeBasedConcedesFormula(double time,double e){
        double lowestUtility = 0.6;
        double degisken = Math.pow((1-time),(1/e));
        return lowestUtility + ((1-lowestUtility) * degisken);
    }
    private Offer makeAnOffer() throws IOException {
        double e = 5.0;
        boolean hardConceder = false;
        boolean mediumConceder = false;
        boolean lowConceder = false;
        Double currentTime = progress.get(System.currentTimeMillis());

        //Burada kendi bidlerimizi sortalamamız lazım. ( utility valuelarına göre.)
        List<Bid> offerableBids = new ArrayList<>();
        Bid offeredBid = null;
//       for (Bid bid: this.bidsList        ) {
//            System.out.println(bid + " with the utility of = " + this.utilitySpace.getUtility(bid).doubleValue());
//        }
        if (this.opponentExpectedUtilities.size() > 2 && this.persistentState.knownOpponent(this.opponentName)) {
            double timeOfConcession = this.persistentState.getTheTimeOfMostDifference(this.opponentName);
            double differenceOfConcession = this.persistentState.getTheDifferenceOfTime(this.opponentName, timeOfConcession);
            if (currentTime < timeOfConcession) {
                e = 5.0;
            } else {
                if (differenceOfConcession >= 0.05) {
                    e = 1.0;
                } else {
                    e = 2.0;
                }
            }
            double rangeMin = timeBasedConcedesFormula(currentTime, e); //0.99
            for (Bid bid : this.bidsList) {
                double bidUtility = this.utilitySpace.getUtility(bid).doubleValue();
                if (bidUtility >= rangeMin) {
                    if (currentTime > 0.3) {
                        if (this.opponentModel.calculateDesirability(bid) > 0.7) {
                            offerableBids.add(bid);
                        }
                    } else {
                        offerableBids.add(bid);
                    }

                }
            }
            if (offerableBids.size() == 0) {
//                getReporter().log(Level.INFO, "Offerablebids size == 0");
                Bid maxUtilityBid = this.bidsList.get(this.bidsList.size() - 1);
                if (utilitySpace.getUtility(maxUtilityBid).doubleValue() > 0.60)
                    offerableBids.add(maxUtilityBid);
            }
            Collections.shuffle(offerableBids);
            offeredBid = offerableBids.get(0);
            sortBidsByUtility(offerableBids);
            this.mostOfferedBid = offerableBids.get(offerableBids.size() - 1);
            this.mostOfferedBidValue = this.utilitySpace.getUtility(offerableBids.get(offerableBids.size() - 1)).doubleValue();

        } else {
            if (this.difference >= 0.05) {
                hardConceder = true;
            } else if (this.difference >= 0.0025) {
                mediumConceder = true;
            } else {
                lowConceder = true;
            }
            if (hardConceder) {
                e = 1.0;
            } else if (mediumConceder) {
                e = 2.0;
            }

            double rangeMin = timeBasedConcedesFormula(currentTime, e); //0.99
            double rangeMax = 1;
//            System.out.println("*****************RANGEMIN "+rangeMin+ " " +currentTime);


            for (Bid bid : this.bidsList) {
                double bidUtility = this.utilitySpace.getUtility(bid).doubleValue();
                if (bidUtility >= rangeMin) {
                    if (currentTime > 0.3) {
                        if (this.opponentModel.calculateDesirability(bid) > 0.5) {
                                offerableBids.add(bid);
                        }
                    } else {
                            offerableBids.add(bid);
                    }

                }
            }


            if (offerableBids.size() == 0) {
//                getReporter().log(Level.INFO, "Offerablebids size == 0");
                Bid maxUtilityBid = this.bidsList.get(this.bidsList.size() - 1);
                offerableBids.add(maxUtilityBid);
            }
            Collections.shuffle(offerableBids);
            offeredBid = offerableBids.get(0);
            sortBidsByUtility(offerableBids);
            for(Bid bid : offerableBids) {
                this.offerableAvg += this.utilitySpace.getUtility(bid).doubleValue();
            }
            offerableAvg = offerableAvg/offerableBids.size();
            this.mostOfferedBid = offerableBids.get(offerableBids.size()-1);
            this.mostOfferedBidValue = this.utilitySpace.getUtility(mostOfferedBid).doubleValue();

//        getReporter().log(Level.INFO, "<MyAgent>: I am offering bid: " + offeredBid);
            // Returns an offering action with the bid found
        }

//        System.out.println("Offering bid with the value : " + this.utilitySpace.getUtility(offeredBid).doubleValue());

        return new Offer(me, offeredBid);

    }
    public double SD(List<Bid> bidsList) { //her actionda çağrılacak.
        double utilSum = 0.0, standardDevitation = 0.0;
        int count = 0;
        if(bidsList.size() != 0){
            for (Bid bid : bidsList) {
                utilSum += this.utilitySpace.getUtility(bid).doubleValue(); //sor
                this.AverageOfReceivedBids = utilSum/count;
                count++;
            }
            for(Bid bid : bidsList) {
                standardDevitation += Math.pow(utilSum - this.AverageOfReceivedBids, 2); //bu SD değiş
            }
        }
        return Math.sqrt(standardDevitation/count); //SD değerini burası veriyor, variable isimleri karışık.
    }

    private double Top50Percentile(List<Bid> bidsList) {

        offerableAvg = 0.0;
        int counter = 0;
        double sum50 = 0.0;

        if (bidsList.size() != 0) {
            for(Bid bid : bidsList) this.offerableAvg += this.utilitySpace.getUtility(bid).doubleValue();
            for (Bid bid : bidsList) {
                if (!(utilitySpace.getUtility(bid).doubleValue() >= offerableAvg)) continue;
                counter++;
                sum50 += this.utilitySpace.getUtility(bid).doubleValue();

            }

        }
        return sum50 / counter;
    }
}

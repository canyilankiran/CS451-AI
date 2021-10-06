package geniusweb.exampleparties.learningagent; // TODO: change name

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import geniusweb.actions.Offer;
import geniusweb.bidspace.IssueInfo;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;

/**
 * This class can hold the persistent state of your agent. You can off course
 * also write something else to the file path that is provided to your agent,
 * but this provides an easy usable method. This object is serialized using
 * Jackson. NOTE that Jackson can serialize many default java classes, but not
 * custom classes out-of-the-box.
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY)
public class persistent {

    private Double averageUtility = 0.0;
    private Integer negotiations = 0;
    private Map<String, Double> avgMaxUtilityOpponent = new HashMap<String, Double>();
    private Map<String, Integer> opponentEncounters = new HashMap<String, Integer>();
    private HashMap<String,HashMap<String,HashMap<Value,Integer>>> frequencyOfIssuesForAcceptedBids = new HashMap<>();
    private HashMap<String,HashMap<String,HashMap<Value,Integer>>> frequencyOfIssuesForRejectedBids = new HashMap<>();
    private HashMap<String, Integer> numberOfAcceptedBids = new HashMap<>();
    private HashMap<String, Integer> numberOfRejectedBids = new HashMap<>();


    /**
     * Update the persistent state with a negotiation data of a previous negotiation
     * session
     *
     * @param negotiationData NegotiationData class holding the negotiation data
     *                        that is obtain during a negotiation session.
     */

    public void update(negodata negotiationData, Domain domain) {
        // Keep track of the average utility that we obtained
        this.averageUtility = (this.averageUtility * negotiations + negotiationData.getAgreementUtil())
                / (negotiations + 1);

        // Keep track of the number of negotiations that we performed
        negotiations++;

        // Get the name of the opponent that we negotiated against
        String opponent = negotiationData.getOpponentName();

        Set<String> issuesForDomain = domain.getIssues();
        HashMap<String,HashMap<Value,Integer>> temp = new HashMap<>();
        for (String issue: issuesForDomain) {
            HashMap<Value,Integer> temp2 = new HashMap<>();
            for (Value value: domain.getValues(issue)) {
                    temp2.put(value,0);
            }
            temp.put(issue,temp2);
        }
        frequencyOfIssuesForAcceptedBids.put(opponent,temp);
        frequencyOfIssuesForRejectedBids.put(opponent,temp);

        // update the frequencyofIssues if they already exists otherwise add them.
        List<Bid> acceptedBids = negotiationData.getAcceptedBids();
        if(knownOpponent(opponent)){
            int accepted = numberOfAcceptedBids.get(opponent);
            numberOfAcceptedBids.put(opponent,accepted+acceptedBids.size());
        }else{
            numberOfAcceptedBids.put(opponent,acceptedBids.size());
        }

        for (Bid bid:acceptedBids) {
            if (frequencyOfIssuesForAcceptedBids.containsKey(opponent)) {
                updateFrequency(opponent, bid, 0);
            }
        }
        List<Bid> rejectedBids = negotiationData.getRejectedBids();
        for (Bid bid:rejectedBids) {
            if (frequencyOfIssuesForRejectedBids.containsKey(opponent)) {
                updateFrequency(opponent, bid, 1);
            }
        }

        // Check for safety
        if (opponent != null) {
            // Update the number of encounters with an opponent
            Integer encounters = opponentEncounters.containsKey(opponent) ? opponentEncounters.get(opponent) : 0;
            opponentEncounters.put(opponent, encounters + 1);
            // Track the average value of the maximum that an opponent has offered us across
            // multiple negotiation sessions
            Double avgUtil = avgMaxUtilityOpponent.containsKey(opponent) ? avgMaxUtilityOpponent.get(opponent) : 0.0;
            avgMaxUtilityOpponent.put(opponent,
                    (avgUtil * encounters + negotiationData.getMaxReceivedUtil()) / (encounters + 1));
        }
    }

    public void updateFrequency(String opponent, Bid bid, int AorR) {
        HashMap<String,HashMap<Value,Integer>> temp;
        if (AorR == 0) {
            temp = frequencyOfIssuesForAcceptedBids.get(opponent);
            for (String issue : bid.getIssues()) {
                HashMap<Value,Integer> temp2 = temp.get(issue);
                Value value = bid.getValue(issue);
                int oldfrequency = temp2.get(value);
                temp2.put(value,oldfrequency+1);
                temp.put(issue,temp2);
            }
            frequencyOfIssuesForAcceptedBids.put(opponent, temp);
        } else {
            temp = frequencyOfIssuesForRejectedBids.get(opponent);
            for (String issue : bid.getIssues()) {
                HashMap<Value,Integer> temp2 = temp.get(issue);
                Value value = bid.getValue(issue);
                int oldfrequency = temp2.get(value);
                temp2.put(value,oldfrequency+1);
                temp.put(issue,temp2);
            }
            frequencyOfIssuesForRejectedBids.put(opponent, temp);
        }
    }

    public boolean isThisOfferGoodForOpponent(String opponent, Bid bid){
        double acceptanceProb = 1;
        HashMap<String,HashMap<Value,Integer>> temp = frequencyOfIssuesForAcceptedBids.get(opponent);
        for (String issue: bid.getIssues()) {
             int frequency = temp.get(issue).get(bid.getValue(issue));
             acceptanceProb *= (double) frequency /temp.get(issue).size();
        }
        acceptanceProb *= numberOfAcceptedBids.size();
        if (acceptanceProb > 0.50){     /// 0.50 den büyük gelirse oppenent kabul edebilir diye döndürdük (değişebilir)
            return true;
        }
        return false;
    }

    public Double getAvgMaxUtility(String opponent) {
        if (avgMaxUtilityOpponent.containsKey(opponent)) {
            return avgMaxUtilityOpponent.get(opponent);
        }
        return null;
    }

    public Integer getOpponentEncounters(String opponent) {
        if (opponentEncounters.containsKey(opponent)) {
            return opponentEncounters.get(opponent);
        }
        return null;
    }

    public Boolean knownOpponent(String opponent) {
        return opponentEncounters.containsKey(opponent);
    }
}

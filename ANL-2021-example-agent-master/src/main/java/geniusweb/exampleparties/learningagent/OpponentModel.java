package geniusweb.exampleparties.learningagent;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;

import java.lang.reflect.AnnotatedType;
import java.util.*;

public class OpponentModel {
    public HashMap<String, HashMap<Value,Integer>> frequencyOfBids = new HashMap<>();
    private String opponent;
    private HashMap<String,Double> issueWeights;
    ArrayList<ArrayList<Bid>> bidsReceived;        // separated by windows
    ArrayList<Bid> lastReceivedBids;
    private int k = 3;  // size dependent
    private int windowCount = 0;
    private int alpha = 10;
    private int beta = 5;
    private Domain domain;
    double consseion_time = 0.0;
    private int count = 0;


    public OpponentModel(Domain domain,String opponentName){
        this.opponent = opponentName;
        this.domain = domain;
        Set<String> issuesForDomain = domain.getIssues();
        double size = domain.getIssues().size();
        this.bidsReceived = new ArrayList<>();
        this.issueWeights = new HashMap<>();
        this.lastReceivedBids = new ArrayList<>();
        for (String issue: issuesForDomain) {
            this.issueWeights.put(issue, (1.0/size));
            HashMap<Value,Integer> temp = new HashMap<>();
            ValueSet values = domain.getValues(issue);
            for (Value value: values) {
                temp.put(value,0);
            }
            frequencyOfBids.put(issue,temp);
        }
//        System.out.println(this.issueWeights);
    }

    public double calculateDesirability(Bid bid){
        double result = 0;
        for (String issue : bid.getIssues()) {
            result += this.issueWeights.get(issue) * valueFunctionEstimation(issue,bid.getValue(issue));
//            System.out.println(this.issueWeights.get(issue) + " " + valueFunctionEstimation(issue,bid.getValue(issue)));
        }

        return result;
    }

    public double expectedUtilityInIssue(ArrayList<Double> Values, ArrayList<Double> frequencies){
        double result = 0;
        for(int i = 0; i< Values.size(); i++){
            result += Values.get(i)*frequencies.get(i);
        }
        return result;
    }


    public void addBidToLastReceivedBids(Bid bid){
        if(!this.lastReceivedBids.contains(bid)){      // check for disjoint list of last received bids
            this.lastReceivedBids.add(bid);
        }

    }

    public int getFrequencyOfValueInIssue(String issue,Value value){
        return frequencyOfBids.get(issue).get(value);
    }

    public int valueWithMaxFrequencyInSomeIssue(String issue){
        int max = 0;
        for (Integer fre : this.frequencyOfBids.get(issue).values()) {
            if(fre > max){
                max = fre;
            }
        }
        return max;
    }

    public double valueFunctionEstimation(String issueI,Value valueJ){
        return Math.pow(1 + getFrequencyOfValueInIssue(issueI,valueJ),0.25) / Math.pow(1 + valueWithMaxFrequencyInSomeIssue(issueI), 0.25);
    }
    public boolean isWindowReady(){
        if(lastReceivedBids.size() % this.k == 0 ){
            ArrayList<Bid> temp = new ArrayList<>();
            for(int i = count; i < lastReceivedBids.size(); i++){
                temp.add(lastReceivedBids.get(i));
            }
            this.bidsReceived.add(temp);
            this.count = this.count + 3;
//            System.out.println("WINDOW LİST" + this.bidsReceived );
            return true;

        }
//        System.out.println("WINDOW LİST" + this.bidsReceived );
          return false;                  // if there is a disjoint bids of size == k

    }
    public boolean isUpdateWeigthReady(){
//        System.out.println("WINDOW SİZE :" + bidsReceived.size());
//        System.out.println("WINDOW BİDS: " + bidsReceived);
//        for (ArrayList<Bid> bids: bidsReceived
//             ) {
//            for (Bid bid: bids
//                 ) {
////                System.out.println("BIDS RECEVİED IS = " + bid.toString());
//            }
//        }
        return bidsReceived.size() >= 2;

    }

    public double frequencyOfValueInWindow(String issue,Value value, ArrayList<Bid> window){
        double frequencyOfSomeValueInWindow = 0;
        for (Bid bid: window) {
               if(bid.getValue(issue).toString().equals(value.toString())){
                   frequencyOfSomeValueInWindow++;
            }
        }
//        System.out.println("Issue :" + issue + "and the value " + value + "with the frequency of " + "" + frequencyOfSomeValueInWindow + " in window " + window);
        return (1 + frequencyOfSomeValueInWindow) / (this.domain.getValues(issue).size().doubleValue() +  this.lastReceivedBids.size()); // laplace smoothing needed
    }

    public void updateFrequency(Bid bid) {              // changeable ??
        for (String issue : bid.getIssues()) {
            HashMap<Value,Integer> currentFrequencies = frequencyOfBids.get(issue);
            Value value = bid.getValue(issue);
            if(currentFrequencies.get(value) != null) {
                int oldFrequency = currentFrequencies.get(value);
                currentFrequencies.put(value, oldFrequency + 1);
            }
            frequencyOfBids.put(issue,currentFrequencies);
            }

//        System.out.println("VALUE :" + frequencyOfBids.keySet() + "FREQUENCY:" + frequencyOfBids.values());

    }


    public double updateRule(double time){
        return this.alpha * ( 1 - Math.pow(time,this.beta));
    }

//    public void weightUpdate(Bid latestBid){
//        for (String issue : latestBid.getIssues()) {
//            if (lastReceivedBids.size() >= 2) {
//                if (this.lastReceivedBids.get(lastReceivedBids.size() - 2).getIssues().contains(issue)) {
//                    this.issueWeights.put(issue, issueWeights.get(issue) + (0.1 / domain.getIssues().size()));
//                }else{
//                    this.issueWeights.put(issue,issueWeights.get(issue) - (0.1 / domain.getIssues().size()));
//                }
//            }
//        }
//
//    }
    public void updateWeights(double time) { // o1 = lastreceviedbids , O 1->t bids received
        ArrayList<String> issues = new ArrayList<>();
        boolean concession = false;
        HashMap<String, Double> temp = this.issueWeights;
        for (String issue : issueWeights.keySet()) {
            temp.put(issue, issueWeights.get(issue));
        }
        for (String issue : this.domain.getIssues()) {
//            HashMap<String, HashMap<Value,Double>> fi = new HashMap<>();
//            HashMap<String, HashMap<Value ,Double>> fi2 = new HashMap<>();
//            HashMap<Value, Double> valuesList = new HashMap<>();
            ArrayList<Double> lastWindow = new ArrayList<>();
            ArrayList<Double> firstWindow = new ArrayList<>();
            ArrayList<Double> valueCalculation = new ArrayList<>();
//            HashMap<Value, Double> temp1  = new HashMap<>();
//            HashMap<Value, Double> temp2 = new HashMap<>();
            for (Value value : this.domain.getValues(issue)) {
                lastWindow.add(frequencyOfValueInWindow(issue, value, this.bidsReceived.get(this.bidsReceived.size() - 1)));
                firstWindow.add(frequencyOfValueInWindow(issue, value, this.bidsReceived.get(this.bidsReceived.size() - 2)));
//                temp1.put(value,frequencyOfValueInWindow(issue, value, this.bidsReceived.get(this.bidsReceived.size()-1)));
//               // System.out.println("Window 1 : " + this.bidsReceived.get(this.bidsReceived.size()-1));
//                temp2.put(value,frequencyOfValueInWindow(issue, value, this.bidsReceived.get(this.bidsReceived.size()-2)));
//              //  System.out.println("Window 2 : " + this.bidsReceived.get(this.bidsReceived.size()-2));
            }

//            fi.put(issue,temp1);
//            fi2.put(issue,temp2);
            //  System.out.println(fi);
            //  System.out.println(fi2);
//            temp1.clear();
//            temp2.clear();
            double Pval = chiSquare(lastWindow, firstWindow);
//            System.out.println("Pval is " + Pval);
            if (Pval > 0.07) {
                issues.add(issue);
            }else{
                for (Value value : this.domain.getValues(issue)) {
                    valueCalculation.add(valueFunctionEstimation(issue,value));
                }
                double expectedUtilityForNew = expectedUtilityInIssue(valueCalculation,lastWindow);
                double expectedUtilityForBefore = expectedUtilityInIssue(valueCalculation,firstWindow);
//                System.out.println(expectedUtilityForNew);
//                System.out.println(expectedUtilityForBefore);
                if(expectedUtilityForNew < expectedUtilityForBefore){
                    concession = true;

//                    System.out.println("Concession bulundu" + this.bidsReceived.size());
                }

            }
            }

        if (concession && issues.size() != this.domain.getIssues().size()){
            this.consseion_time = time;
//            System.out.println("Concession found : " + time);
            for (String issue :issues) {
                temp.put(issue,temp.get(issue) + updateRule(time));
            }
        }

            this.issueWeights = temp;
        }


    public double chiSquare( ArrayList<Double> first, ArrayList<Double> second){
        double result = 0.0;
        for (int i= 0 ; i<first.size(); i++) {
            result += Math.pow(first.get(i) - second.get(i),2) / second.get(i);

        }
        return Math.sqrt(result);
    }

    }


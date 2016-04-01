package com.xebialabs.repository.tools;

public class Stats {
    private long allNodes;
    private long counter = 1;
    private long failedProperties;

    public void incrementAllNodes() {
        this.allNodes += 1;
    }

    public boolean commitSession() {
        counter += 1;
        return counter % 100 == 0;
    }

    public void incrementFailedProperties(){
        failedProperties += 1;
    }


    public long getCounter() {
        return counter;
    }

    @Override
    public String toString() {
        return "Stats{" +
                "allNodes=" + allNodes +
                ", failedProperties=" + failedProperties +
                '}';
    }
}

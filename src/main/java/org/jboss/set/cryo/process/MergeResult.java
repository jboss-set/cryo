package org.jboss.set.cryo.process;

import java.util.Collections;
import java.util.List;

//import org.jboss.set.cryo.process.BisectablePullRequest.CryoPRState;

public class MergeResult {

    protected List<BisectablePullRequest> mergeList;

    public MergeResult(List<BisectablePullRequest> mergedPullRequests) {
        this.mergeList = Collections.unmodifiableList(mergedPullRequests);
    }

    public int getCount() {
        return this.mergeList.size();
    }

    public List<BisectablePullRequest> getMergeList() {
        return mergeList;
    }

//
//    public long getMergeCount() {
//        // TODO: figure out if GOOD has to be included, how to handle BAD?
//        return mergeList.stream().filter(pr -> {
//            return pr.getState() == CryoPRState.MERGED;
//        }).count();
//    }

}
package org.jboss.set.cryo.process;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.jboss.set.aphrodite.domain.PullRequest;
import org.jboss.set.cryo.Cryo;
import org.jboss.set.cryo.Main;

/**
 * Simple wrapper for PR with some additional stuff.
 *
 * @author baranowb
 *
 */
public class BisectablePullRequest {

    protected CryoPRState state = CryoPRState.PRISTINE;
    protected PullRequest pullRequest;
    protected BisectablePullRequest parent;
    //hold merge id in order to unmerge if needs be.
    protected String mergeCommitID;

    //TODO: possibly remove this
    protected File repositoryLocation;
    public BisectablePullRequest(final File repositoryLocation, final PullRequest pullRequest) {
        super();
        this.pullRequest = pullRequest;
        this.repositoryLocation = repositoryLocation;
    }

    public CryoPRState getState() {
        return state;
    }

    public PullRequest getPullRequest() {
        return pullRequest;
    }

    public List<BisectablePullRequest> getDependencies(){
        //TODO: fill this
        return new ArrayList<>();
    }

    /**
     * Merge this PR and any deps it might have( deps first ). In case of failure change state and revert
     * @return
     */
    public boolean merge() {
        if(state != CryoPRState.PRISTINE) {
            //NOTE: should this throw?
            return false;
        }

        final ProcessBuilder mergePullRequest = new ProcessBuilder(Cryo.COMMAND_MERGE_PR(getId()));
        mergePullRequest.directory(repositoryLocation);
        final ProcessResult result = new ExecuteProcess(mergePullRequest).getProcessResult();
        switch (result.getOutcome()) {
            case SUCCESS:
                Main.log(Level.INFO, "[SUCCESS] Merge of: {0}", getId());
                this.state = CryoPRState.MERGED;
                final ProcessResult read = this.readMergeCommitHash();
                switch(read.getOutcome()) {
                    case SUCCESS:
                        this.mergeCommitID = read.getOutput();
                        break;
                    case FAILURE:
                    default:
                        read.reportError();
                        //TODO: make it gracious.
                        throw new RuntimeException();
                }
                return true;
            case FAILURE:
                this.state = CryoPRState.NO_MERGE;
                if(!reverse()) {
                    //TODO: make it better
                    throw new RuntimeException();
                }
            default:
                result.reportError();
                return false;
        }
    }

    public boolean reverse() {
        if (state == CryoPRState.NO_MERGE) {
            //NOTE: in case of merge failure repo is in limbo state. Current commit is old head, no new PRs in index are present
            //so reset to HEAD is enough
            //final ProcessBuilder readRepoURL = new ProcessBuilder(Cryo.COMMAND_GIT_RESET_TO_POINT("HEAD"));
            final ProcessBuilder mergeAbort = new ProcessBuilder(Cryo.COMMAND_GIT_MERGE_ABORT);
            mergeAbort.directory(repositoryLocation);
            final ProcessResult result = new ExecuteProcess(mergeAbort).getProcessResult();
            switch (result.getOutcome()) {
                case SUCCESS:
                    Main.log(Level.INFO, "[SUCCESS] Revert pull request after failure: {0}", getId());
                    return true;
                case FAILURE:
                default:
                    // NOTE: is this even possible?
                    result.reportError();
                    return false;
            }
        } else if(state == CryoPRState.MERGED){
            final ProcessBuilder mergeRevert = new ProcessBuilder(Cryo.COMMAND_GIT_RESET_TO_PREVIOUS(mergeCommitID));
            mergeRevert.directory(repositoryLocation);
            final ProcessResult result = new ExecuteProcess(mergeRevert).getProcessResult();
            switch (result.getOutcome()) {
                case SUCCESS:
                    Main.log(Level.INFO, "[SUCCESS] Revert pull request: {0}", getId());
                    mergeCommitID = null;
                    this.state = CryoPRState.PRISTINE;
                    return true;
                case FAILURE:
                default:
                    // NOTE: is this even possible?
                    mergeCommitID = null;
                    this.state = CryoPRState.CORRUPTED;
                    result.reportError();
                    return false;
            }
        }
        return true;
    }

    protected ProcessResult readMergeCommitHash() {
        final ProcessBuilder readCommitHead = new ProcessBuilder(Cryo.COMMAND_GIT_READ_CURRENT_COMMIT_HEAD);
        readCommitHead.directory(repositoryLocation);
        return new ExecuteProcess(readCommitHead).getProcessResult();
    }
    public String getId() {
        return this.pullRequest.getId();
    }

    public void markGood() {
        this.state = CryoPRState.GOOD;
    }

    public void markNoMerge() {
        this.state = CryoPRState.NO_MERGE;
    }

    public void markFailed() {
        this.state = CryoPRState.FAILED;
    }

    public void markCorrupted() {
        this.state = CryoPRState.CORRUPTED;
    }

    public static enum CryoPRState {
        /**
         * Initial state
         */
        PRISTINE,
        /**
         * Merged in local clone, awaiting test result
         */
        MERGED,
        /**
         * Failed tests
         */
        FAILED,
        /**
         * Merged and passed tests, a keeper.
         */
        GOOD,
        /**
         * Fails to merge
         */
        NO_MERGE,
        /**
         * wrong meta, has deps that are not in stream - basically anything else that needs to be reported that does not fall into FAIL/NO_MERGE
         */
        CORRUPTED;
    }

    @Override
    public String toString() {
        return "BisectablePullRequest [pullRequest=" + pullRequest.getId() + ", state=" + state + "]";
    }

}

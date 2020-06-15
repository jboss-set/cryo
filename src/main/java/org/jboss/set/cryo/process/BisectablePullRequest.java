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

    //TODO: remove this
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
            //TODO: make it better
            throw new RuntimeException();
        }
        //NOTE: there might be case when PR has more than one commit, one in the middle fails, how to handle that ?
        final ProcessBuilder mergePullRequest = new ProcessBuilder(Cryo.COMMAND_MERGE_PR(getId()));
        mergePullRequest.directory(repositoryLocation);
        final ProcessResult result = new ExecuteProcess(mergePullRequest).getProcessResult();
        switch (result.getOutcome()) {
            case SUCCESS:
                Main.log(Level.INFO, "[SUCCESS] Merge of: %s", getId());
                this.state = CryoPRState.MERGED;
                final ProcessResult read = this.readMergeCommitHash();
                switch(read.getOutcome()) {
                    case SUCCESS:
                        this.mergeCommitID = read.getOutput();
                        break;
                    case FAILURE:
                    default:
                        read.reportError();
                        break;
                }
                return true;
            case FAILURE:
                //TODO: failure processing here - either nightmare or just blow up...
                // #markFailed()
            default:
                result.reportError();
                return false;
        }
    }

    public boolean reverse() {
        final ProcessBuilder readRepoURL = new ProcessBuilder(Cryo.COMMAND_GIT_RESET_TO_PREVIOUS(mergeCommitID));
        readRepoURL.directory(repositoryLocation);
        final ProcessResult result = new ExecuteProcess(readRepoURL).getProcessResult();
        switch (result.getOutcome()) {
            case SUCCESS:
                Main.log(Level.INFO, "[SUCCESS] Revert pull request: %s", getId());
                return true;
            case FAILURE:
            default:
                result.reportError();
                return false;
        }
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

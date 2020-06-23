/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2020, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.set.cryo.process;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.jboss.set.aphrodite.domain.PullRequest;
import org.jboss.set.cryo.Main;
import org.jboss.set.cryo.staging.OperationCenter;
import org.jboss.set.cryo.staging.OperationResult;

/**
 * Simple wrapper for PR with some additional stuff.
 *
 * @author baranowb
 *
 */
public class BisectablePullRequest {

    protected CryoPRState state = CryoPRState.PRISTINE;
    protected final PullRequest pullRequest;
    protected BisectablePullRequest parent;
    //hold merge id in order to unmerge if needs be.
    protected String mergeCommitID;
    protected final OperationCenter operationCenter;
    //TODO: possibly remove this
    public BisectablePullRequest(final OperationCenter operationCenter, final PullRequest pullRequest) {
        super();
        this.pullRequest = pullRequest;
        //INFO: a bit counter pattern but this allows us to have action methods in BPR
        this.operationCenter = operationCenter;
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

        final OperationResult result = this.operationCenter.mergePullRequest(this.getId());
        switch (result.getOutcome()) {
            case SUCCESS:
                Main.log(Level.INFO, "[SUCCESS] Merge of: {0}", getId());
                this.state = CryoPRState.MERGED;
                final OperationResult read = this.readMergeCommitHash();
                switch(read.getOutcome()) {
                    case SUCCESS:
                        this.mergeCommitID = read.getOutput();
                        break;
                    case FAILURE:
                    default:
                        read.reportError("Merge of: "+getId());
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
            final OperationResult result = this.operationCenter.abortMerge();
            switch (result.getOutcome()) {
                case SUCCESS:
                    Main.log(Level.INFO, "[SUCCESS] Revert pull request after failure: {0}", getId());
                    return true;
                case FAILURE:
                default:
                    // NOTE: is this even possible?
                    result.reportError("Revert pull request after failure: "+getId());
                    return false;
            }
        } else if(state == CryoPRState.MERGED){
            final OperationResult result = this.operationCenter.revertToPreviousCommit(mergeCommitID);
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
                    result.reportError("Revert pull request: "+getId());
                    return false;
            }
        }
        return true;
    }

    protected OperationResult readMergeCommitHash() {
        return this.operationCenter.readRepositoryCommitHEAD();
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

    public void markExclude() {
        this.state = CryoPRState.EXCLUDE;
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
         * Excluded from reactor
         */
        EXCLUDE,
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

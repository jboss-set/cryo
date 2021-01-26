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

import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import javax.naming.NameNotFoundException;

import org.jboss.set.aphrodite.domain.Label;
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

    protected final String LABEL_HAS_ALL_ACKS = "Has All Acks";
    protected CryoPRState state = CryoPRState.PRISTINE;
    protected final PullRequest pullRequest;

    //hold merge id in order to unmerge if needs be.
    protected String mergeCommitID;
    protected final OperationCenter operationCenter;

    //dependency stuff
    protected BisectablePullRequest dependant;
    protected List<BisectablePullRequest> dependencies = new ArrayList<>();
    //hash/equals
    protected final URL id;
    public BisectablePullRequest(final OperationCenter operationCenter, final PullRequest pullRequest) {
        super();
        this.pullRequest = pullRequest;
        this.id = this.pullRequest.getURL();
        //INFO: a bit counter pattern but this allows us to have action methods in BPR
        this.operationCenter = operationCenter;
    }

    public BisectablePullRequest(final URL id) {
        super();
        //INFO: a bit counter pattern but this allows us to have action methods in BPR
        this.id = id;
        this.pullRequest = null;
        this.operationCenter = null;
    }

    public CryoPRState getState() {
        return state;
    }

    public PullRequest getPullRequest() {
        return pullRequest;
    }

    public List<BisectablePullRequest> getDependencies(){
        return this.dependencies;
    }

    public boolean hasDependencies() {
        return this.dependencies.size() > 0;
    }

    public boolean hasDefinedDependencies() {
        if(this.getPullRequest() != null){
            return this.pullRequest.hasDependencies();
        } else {
            return false;
        }
    }

    public boolean hasDependant() {
        return this.dependant != null;
    }

    public boolean hasAllAcks() {
        // TODO: no need for requrency?
        if (this.getPullRequest() == null) {
            // TODO check for failure if false
            return true;
        } else {
            try {
                for (Label l : this.getPullRequest().getLabels()) {
                    if (l.getName().equals(LABEL_HAS_ALL_ACKS)) {
                        return true;
                    }
                }
            } catch (NameNotFoundException e) {
                Main.log(Level.WARNING, "Can not fetch label list for: {0}", this.pullRequest.getURL());
            }
            return false;
        }
    }

    /**
     * Add dependency, check if either father or child is corrupted and mark both properly;
     * @param bisectablePullRequest
     * @return
     */
    public boolean addDependency(final BisectablePullRequest bisectablePullRequest) {
      //TODO: vet state:
      // - mark as corrupted if dep is corrupted
      // - mark both/one as corrupt in case of false
        if(bisectablePullRequest.dependant != null && !bisectablePullRequest.dependant.equals(this)) {
            //TODO: this should also never be non null?
            //TODO: if more than one PR is permitted to depend on another one, this has to be changed
            Main.log(Level.WARNING, "Pull Request[{0}] is already dependency of [{1}], can not add it as dependency of [{2}]. Marking both as corrupted!", new Object[] {
                    bisectablePullRequest.id,bisectablePullRequest.dependant.id, this.id
            });
            this.markCorrupted();
            bisectablePullRequest.markCorrupted();
            return false;
        } else {
            if(bisectablePullRequest.getState() == CryoPRState.CORRUPTED) {
                this.markCorrupted();
            } else if(this.getState() == CryoPRState.CORRUPTED) {
                bisectablePullRequest.markCorrupted();
            }
            bisectablePullRequest.dependant = this;
            this.dependencies.add(bisectablePullRequest);
            return true;
        }
    }

    /**
     * Merge this PR and any deps it might have( deps first ). In case of failure change state and revert
     * @return
     */
    public boolean merge() {
        //INFO: merge can only come from root. Leafs are not invoked directly. Root does it.
        if(state != CryoPRState.PRISTINE) {
            //NOTE: should this throw?
            return false;
        }

        if(this.dependencies.size()>0) {
            //INFO: Merge left to right (this should roughly retain order from "fetch")
            for(int index = 0;index<this.dependencies.size(); index++) {
                BisectablePullRequest dependency = this.dependencies.get(index);
                if(!dependency.merge()) {
                    //TODO: move to to reverse?
                    //TODO: CHECK FOR BUG we merge left to right, than go up and merge - meaning root merge commit contains ALL DEPS
                    //if we unmerge leafs and than issue unmerge on root, we end up unmerging root and keep leafs.
                    //INFO: this handles NO_MERGE for deps
                    Main.log(Level.WARNING, "Failed to merge dependency of PR[{0}], failed dependency[{1}]", new Object[] {this.getPullRequest().getURL(),dependency.getPullRequest().getURL()});
                    //reverse previous if we are root, NO_MERGE
                    for(int revIndex = index-1;revIndex>=0;revIndex--) {
                        dependency = this.dependencies.get(revIndex);
                        if(!dependency.reverse()) {
                            //TODO: make it better
                            throw new RuntimeException("[CRYO] Failed to clean up PR["+this.getPullRequest().getURL()+"], failed dependency["+dependency.getPullRequest().getURL()+"], repository is in corrupted state. Exploding!");
                        }
                    }
                    this._markNoMerge(true);
                    return false;
                }
            }
        }

        //INFO MERGE local root.
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
                        read.reportError();
                        throw new RuntimeException("[CRYO] Failed to read merge info after merging PR["+this.getPullRequest().getURL()+"], repository is in corrupted state. Exploding!");
                }
                return true;
            case FAILURE:
            default:
                //INFO: mark only this one for abort, rest will do regular. After reverse markNoMerge for whole tree
                this.state = CryoPRState.NO_MERGE;
                result.reportError();
                if(!reverse()) {
                    //TODO: make it better
                    throw new RuntimeException("[CRYO] Failed to clean up PR["+this.getPullRequest().getURL()+"], repository is in corrupted state. Exploding!");
                }
                //only mark our subtree.
                this._markNoMerge(true);
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
                    //INFO: just in case
                    //this._markNoMerge(true);
                    return true;
                case FAILURE:
                default:
                    // NOTE: is this even possible?
                    result.reportError("Revert pull request after failure: "+getId());
                    return false;
            }
        } else if(state == CryoPRState.MERGED){
            //INFO: reverse TOP to BOTTOM, RIGHT to LEFT - in reality we could revert first left most dep( first one merged)
            final OperationResult result = this.operationCenter.revertToPreviousCommit(mergeCommitID);
            switch (result.getOutcome()) {
                case SUCCESS:
                    Main.log(Level.INFO, "[SUCCESS] Revert pull request: {0} to previous {1}, {2}",
                            new Object[] { getId(), mergeCommitID, result.getOutput() });
                    mergeCommitID = null;
                    this.state = CryoPRState.PRISTINE;
                    for (int revIndex = this.dependencies.size() - 1; revIndex >= 0; revIndex--) {
                        final BisectablePullRequest dependency = this.dependencies.get(revIndex);
                        if (!dependency.reverse()) {
                            // TODO: make it better
                            throw new RuntimeException("[CRYO] Failed to clean up PR[" + this.getPullRequest().getURL()
                                    + "], failed dependency[" + dependency.getPullRequest().getURL()
                                    + "], repository is in corrupted state. Exploding!");
                        }
                    }
                    return true;
                case FAILURE:
                default:
                    // NOTE: is this even possible?
                    // TODO: add another state?
                    mergeCommitID = null;
                    this.state = CryoPRState.CORRUPTED;
                    result.reportError("Revert pull request: "+getId());
                    return false;
            }
        }
        return false;
    }

    protected OperationResult readMergeCommitHash() {
        return this.operationCenter.readRepositoryCommitHEAD();
    }
    public String getId() {
        if(this.pullRequest != null) {
        return this.pullRequest.getId();
        } else {
            return null;
        }
    }

    public void markGood() {
        //this.state = CryoPRState.GOOD;
        propagateState(CryoPRState.GOOD, true);
    }

    public void markNoMerge() {
        _markNoMerge(false);
    }

    protected void _markNoMerge(final boolean limited) {
        propagateState(CryoPRState.NO_MERGE, !limited);
    }
    public void markFailed() {
        propagateState(CryoPRState.FAILED, true);
    }

    public void markCorrupted() {
        propagateState(CryoPRState.CORRUPTED, true);
    }

    public void markExclude() {
        propagateState(CryoPRState.EXCLUDE, true);
    }

    public void markIneligible() {
        this.state = CryoPRState.INELIGIBLE;
        propagateState(CryoPRState.CORRUPTED, true);
    }

    protected void propagateState(final CryoPRState toSet, boolean goUp) {
        //TODO: improve this. Its fragile
        if(goUp && this.dependant != null) {
            this.dependant.propagateState(toSet, goUp);
        } else {
            for(int index=this.dependencies.size()-1;index>=0;index--) {
                this.dependencies.get(index).propagateState(toSet, false);
            }
            if(this.state == CryoPRState.INELIGIBLE || this.state == CryoPRState.CORRUPTED) {
                return;
            } else {
                this.state = toSet;
            }
        }
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
        CORRUPTED,
        /**
         * Indicate that PR did not pass all required check( ie, does not have all acks or something more fancy
         */
        INELIGIBLE;

    }

    public String toString() {
        StringBuilder buffer = new StringBuilder(50);
        _toString(buffer, "", "");
        return buffer.toString();
    }

    protected void _toString(StringBuilder buffer, String prefix, String childPrefix) {
        buffer.append(prefix);
        if(pullRequest != null) {
            //buffer.append("BisectablePullRequest ["+ pullRequest.getURL() + ", title='"+this.pullRequest.getTitle()+"'"+", state=" + state + "]");
            buffer.append("["+ pullRequest.getURL() + ", title='"+this.pullRequest.getTitle()+"'"+", state=" + state + "]");
        } else {
            //buffer.append("BisectablePullRequest ["+ id + ", title='NA'"+", state=" + state + "]");
            buffer.append("["+ id + ", title='NA'"+", state=" + state + "]");
        }
        buffer.append("\n");
        for(Iterator<BisectablePullRequest> it = this.dependencies.iterator();it.hasNext();) {
            BisectablePullRequest dep = it.next();
            if(it.hasNext()) {
                dep._toString(buffer, childPrefix + "├── ", childPrefix + "│   ");
            } else {
                dep._toString(buffer, childPrefix + "└── ", childPrefix + "    ");
            }
        }
    }

    //TODO: implement equals hash based on URL ?
}

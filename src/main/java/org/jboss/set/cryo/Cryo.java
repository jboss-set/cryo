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
package org.jboss.set.cryo;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.jboss.set.aphrodite.Aphrodite;
import org.jboss.set.aphrodite.domain.PullRequest;
import org.jboss.set.aphrodite.domain.PullRequestState;
import org.jboss.set.aphrodite.domain.Repository;
import org.jboss.set.aphrodite.domain.spi.PullRequestHome;
import org.jboss.set.aphrodite.repository.services.github.GithubPullRequestHomeService;
import org.jboss.set.aphrodite.simplecontainer.SimpleContainer;
import org.jboss.set.aphrodite.spi.AphroditeException;
import org.jboss.set.aphrodite.spi.NotFoundException;
import org.jboss.set.cryo.process.BisectablePullRequest;
import org.jboss.set.cryo.process.BisectablePullRequest.CryoPRState;
import org.jboss.set.cryo.process.MergeResult;
import org.jboss.set.cryo.staging.OperationCenter;
import org.jboss.set.cryo.staging.OperationResult;

/**
 * Cryo storage util. It will do some magic, merge, bisect and eventually push into cryo branch that will await awakening when
 * technology reach proper level for revival - or desperation...
 *
 * @author baranowb
 *
 */
public class Cryo {

    private static final SimpleContainer simpleContainer = (SimpleContainer) SimpleContainer.instance();

    protected OperationCenter operationCenter;
    /**
     * Physical location of repository to work on - most likely created with git clone.
     */
    protected File repositoryLocation;

    /**
     * Repository URL, we will read it from {@link #repositoryLocation}.
     */
    protected URL repositoryURL;
    /**
     * Branch on which we will work on.
     */
    protected String branch;

    protected String futureBranch;

    protected String remoteCodeBase;
    protected Aphrodite aphrodite;
    protected List<BisectablePullRequest> coldStorage;
    protected final boolean dryRun;
    protected final boolean invert;
    protected final boolean checkPRState;
    protected final Set<String> excludeSet;
    protected final List<String> includeList;
    protected final String suffix;
    protected final String opsCoreHint;
    protected final String[] mavenArgs;

    // TODO: redo with more sophisticated state machine?
    protected boolean weDone = false;

    public Cryo(final File directory, final boolean dryRun, final boolean invertPullRequests, final boolean checkPRState, Set<String> excludeSet, List<String> includeList, String suffix, String opsCore, String[] mavenArgs) {
        this.repositoryLocation = directory;
        this.dryRun = dryRun;
        this.invert = invertPullRequests;
        this.checkPRState = checkPRState;
        this.excludeSet = excludeSet;
        this.includeList = includeList;
        this.suffix = suffix;
        this.opsCoreHint = opsCore;
        this.mavenArgs = mavenArgs;
    }

    /**
     * Perform menial tasks - get repo URL, clean up act, change branch to proper one, fetch PRs and set up data structures
     *
     */
    protected boolean init() {
        ServiceLoader<OperationCenter> opsCore = ServiceLoader.load(OperationCenter.class);
        final Iterator<OperationCenter> it = opsCore.iterator();
        if(it.hasNext()) {
            while (it.hasNext()) {
                if (this.operationCenter == null) {
                    this.operationCenter = it.next();
                } else {
                    final OperationCenter tmpOps = it.next();
                    if(tmpOps.getClass().getName().endsWith(this.opsCoreHint)) {
                        this.operationCenter = tmpOps;
                        break;
                    }
                }
            }
            this.operationCenter = this.operationCenter.initializeOperationCenter(new Object[] {this.repositoryLocation});
        } else {
            CryoLogger.ROOT_LOGGER.failedToCreateOperationCenter();
            return false;

        }
        if (!determineRepositoryURL()) {
            return false;
        }

        if (!determineCurrentBranch()) {
            return false;
        }

        if (!cleanUpRepository()) {
            return false;
        }

        if (!initAphrodite()) {
            return false;
        }

        if (!fetchPRList()) {
            return false;
        } else {
            reportCurrentStateOfColdStorage();
        }
        return true;
    }

    protected boolean determineRepositoryURL() {
        final OperationResult result = this.operationCenter.determineRepositoryURL();
        switch (result.getOutcome()) {
            case SUCCESS:
                CryoLogger.ROOT_LOGGER.logRepositoryURL(result.getOutput());
                try {
                    //INFO in case of git:// we have to switch to https kind
                    String tmpURL = result.getOutput();
                    if(tmpURL.endsWith(".git")) {
                        tmpURL = tmpURL.substring(0, tmpURL.indexOf(".git"));
                    }

                    if(tmpURL.startsWith("git@")) {
                        final StringBuilder ulrStringBuilder = new StringBuilder();
                        ulrStringBuilder.append("https://");
                        ulrStringBuilder.append(tmpURL.substring(4,tmpURL.indexOf(":")));
                        ulrStringBuilder.append("/").append(tmpURL.substring(tmpURL.indexOf(":")+1,tmpURL.length()));
                        tmpURL = ulrStringBuilder.toString();
                        CryoLogger.ROOT_LOGGER.logTransformedRepositoryURL(tmpURL);
                    }
                    this.repositoryURL = new URL(tmpURL);
                } catch (MalformedURLException e) {
                    CryoLogger.ROOT_LOGGER.failedToParseRepositoryURL();
                    return false;
                }
                return true;
            case FAILURE:
            default:
                result.reportError("Failed to determine repository URL");
                return false;
        }
    }

    protected boolean determineCurrentBranch() {

        final OperationResult result = this.operationCenter.determineCurrentBranch();
        switch (result.getOutcome()) {
            case SUCCESS:
                CryoLogger.ROOT_LOGGER.logRepositoryBranch(result.getOutput());
                this.branch = result.getOutput();
                if(this.branch.endsWith(this.suffix)) {
                    this.remoteCodeBase = this.branch.substring(0,this.branch.indexOf(this.suffix));
                } else {
                    this.remoteCodeBase = this.branch;
                }
                return true;
            case FAILURE:
            default:
                result.reportError("Failed to determine current branch");
                return false;
        }
    }

    /**
     * Just in case, cleanup method.
     *
     * @return
     */
    protected boolean cleanUpRepository() {
        // Just in case.
        CryoLogger.ROOT_LOGGER.cleaningRepository();
        final OperationResult result = this.operationCenter.cleanUpRepository(System.out, this.mavenArgs);
        switch (result.getOutcome()) {
            case SUCCESS:
                    CryoLogger.ROOT_LOGGER.cleaningRepository(result.getOutput());
                return true;
            case FAILURE:
            default:
                result.reportError("Cleanup of repository");
                return false;
        }
    }

    protected boolean buildAndRunTestsuite() {
        final OperationResult result = this.operationCenter.buildAndRunTestsuite(System.out, this.mavenArgs);
        switch (result.getOutcome()) {
            case SUCCESS:
                CryoLogger.ROOT_LOGGER.logBuildAndTest(result.getOutput());
                return true;
            case FAILURE:
            default:
                result.reportError("Build and testsuite run");
                return false;
        }
    }

    /**
     * Intialize aphrodite.
     *
     * @return
     */
    protected boolean initAphrodite() {
        try {
            this.aphrodite = Aphrodite.instance();

            simpleContainer.register(Aphrodite.class.getSimpleName(), aphrodite);
            GithubPullRequestHomeService GithubPullRequestHomeService = new GithubPullRequestHomeService(aphrodite);
            simpleContainer.register(PullRequestHome.class.getSimpleName(), GithubPullRequestHomeService);
            return true;
        } catch (AphroditeException e) {
            CryoLogger.ROOT_LOGGER.failedToInitializeAphrodite(e);
            throw new RuntimeException("Failed to initialize aphrodite!", e);
        }
    }

    /**
     * Fetch PR list from remote repository, create local data structures and prepare to rumble!!
     *
     * @return
     */
    protected boolean fetchPRList() {
        this.coldStorage = new ArrayList<>();
        try {
            final Repository repository = this.aphrodite.getRepository(this.repositoryURL);
            final List<PullRequest> allPullRequests = aphrodite.getPullRequestsByState(repository, PullRequestState.OPEN);
            if(this.invert) {
                Collections.reverse(allPullRequests);
            }

            final LinkedHashMap<URL,BisectablePullRequest> inintialPullRequestPool = new LinkedHashMap<URL, BisectablePullRequest>(allPullRequests.size());

            CryoLogger.ROOT_LOGGER.logFetchingPRList( new String[]{this.remoteCodeBase});
            for (PullRequest pullRequest : allPullRequests) {
                try {
                    if(pullRequest == null || pullRequest.getCodebase() ==null || pullRequest.getCodebase().getName() ==null) {
                            CryoLogger.ROOT_LOGGER.logFirstBad(new Object[] { pullRequest });
                        continue;
                    }
                    if (pullRequest.getCodebase().getName().equalsIgnoreCase(this.remoteCodeBase)) {
                        final BisectablePullRequest req = new BisectablePullRequest(this.operationCenter, pullRequest);
                        CryoLogger.ROOT_LOGGER.logRetainingPRList(new Object[] { req });
                        inintialPullRequestPool.put(pullRequest.getURL(), req);

                    } else {
                        final BisectablePullRequest req = new BisectablePullRequest(this.operationCenter, pullRequest);
                        CryoLogger.ROOT_LOGGER.logPurgingPRList(new Object[] { req });
                    }
                } catch (Exception e) {
                    // TODO improve this.
                    e.printStackTrace();
                }
            }

            //INFO: get most URL part without prefix for mockups, not ideal but...
            final String urlPrefixOfPR = procureURLPrefix(inintialPullRequestPool);
            final LinkedHashMap<URL,BisectablePullRequest> pullRequestList;
            // if include list is present, build PR tree from it, if not, use active set
            if (inintialPullRequestPool.size() > 0 && this.includeList != null && this.includeList.size() > 0) {
                pullRequestList = new LinkedHashMap<URL, BisectablePullRequest>(this.includeList.size());
                for (String idString : this.includeList) {
                    final Iterator<BisectablePullRequest> it = inintialPullRequestPool.values().iterator();
                    BisectablePullRequest toIncludePR = null;
                    while (it.hasNext()) {
                        final BisectablePullRequest tmp = it.next();
                        if (tmp.getId().contentEquals(idString)) {
                            toIncludePR = tmp;
                            break;
                        }
                    }

                    if (toIncludePR != null) {
                        pullRequestList.put(toIncludePR.getPullRequest().getURL(), toIncludePR);
                    } else {
                        final URL tmpIdURL = new URL(urlPrefixOfPR+idString);
                        final BisectablePullRequest missing = new BisectablePullRequest(tmpIdURL);
                        missing.markIneligible();
                        pullRequestList.put(tmpIdURL, missing);
                        CryoLogger.ROOT_LOGGER.failedToFindPR( new String[] { idString });
                    }
                }
            } else {
                //use full list and vet after
                pullRequestList = inintialPullRequestPool;
            }
            //INFO: build deps.
            //after this we should have a tree built, after we need to vet into mergable structure
            // and store in #coldStorage
            for(URL u:pullRequestList.keySet()) {
                final BisectablePullRequest currentToScrutiny = pullRequestList.get(u);

                if(!currentToScrutiny.hasDefinedDependencies()) {
                    continue;
                }
                //INFO: else we have deps and lets try to collect them from #pullRequestList
                final List<URL> deps = currentToScrutiny.getPullRequest().findDependencyPullRequestsURL();

                CryoLogger.ROOT_LOGGER.logSearchingForDependencies(new Object[] {u,deps.stream()
                        .map(str->str.toString())
                        .collect(Collectors.joining("\n"))});
                for(URL dependencyURL:deps) {
                    if(pullRequestList.containsKey(dependencyURL)) {
                        currentToScrutiny.addDependency(pullRequestList.get(dependencyURL));
                    } else {
                        //TODO: add "NON_EXISTING" or set "INELIGIBLE" BisectablePullRequest and add dep for log transparency
                        CryoLogger.ROOT_LOGGER.failedToFindDependecy(u,dependencyURL);
                        final BisectablePullRequest missing = new BisectablePullRequest(dependencyURL);
                        currentToScrutiny.markCorrupted();
                        currentToScrutiny.addDependency(missing);
                        missing.markIneligible();
                    }
                }
            }

            while(pullRequestList.size() >0) {
                for(Iterator<URL> it =  pullRequestList.keySet().iterator();it.hasNext() ;) {
                    final URL u = it.next();
                    final BisectablePullRequest bisectablePullRequest = pullRequestList.get(u);
                    if(bisectablePullRequest.hasDependant()) {
                        //INFO: ignore and remove. deps are going to be merged from dependant
                    } else {
                        coldStorage.add(bisectablePullRequest);
                    }
                    it.remove();
                    continue;
                }
            }
            vetPullRequestsStateInColdStorage();
            ostracizeColdStorage();
            return true;
        } catch (NotFoundException | MalformedURLException e) {
            CryoLogger.ROOT_LOGGER.failedToFectchRepository(this.repositoryURL,e);
        }
        return false;
    }

    private String procureURLPrefix(LinkedHashMap<URL, BisectablePullRequest> inintialPullRequestPool) {
        if(inintialPullRequestPool.size() == 0) {
            return null;
        }
        final String tmp = inintialPullRequestPool.keySet().iterator().next().toString();
        return tmp.substring(0,tmp.lastIndexOf("/")+1);
    }

    /**
     * Mark PRs as excluded if they are in exclude set. This will take care of deps as well
     */
    protected void ostracizeColdStorage() {
        //FIXME: this wont work if dependency is in exclude?
        if(excludeSet.size()>0)
        for(BisectablePullRequest bisectablePullRequest:this.coldStorage) {
            if(this.excludeSet.contains(bisectablePullRequest.getId())) {
                bisectablePullRequest.markExclude();
            }
        }
    }

    protected void vetPullRequestsStateInColdStorage() {
        for(BisectablePullRequest bisectablePullRequest:this.coldStorage) {
            vetPullRequestState(bisectablePullRequest);
        }
    }

    protected void vetPullRequestState(final BisectablePullRequest bisectablePullRequest) {
        if(!this.checkPRState) {
            return;
        }
        if(!bisectablePullRequest.hasAllAcks()) {
            bisectablePullRequest.markIneligible();
            CryoLogger.ROOT_LOGGER.failedToMeetRequirementsForMerge(new Object[] {bisectablePullRequest.getPullRequest()});
        } else {
            CryoLogger.ROOT_LOGGER.logPRMeetsRequirements(new Object[] {bisectablePullRequest.getPullRequest()});
        }
        for(BisectablePullRequest dependency: bisectablePullRequest.getDependencies())
        {
            vetPullRequestState(dependency);
        }
    }

    protected void createStorage() {
        if (!init()) {
            CryoLogger.ROOT_LOGGER.failedToInitialize();
            return;

        }
        if (!setUpFutureBranch()) {
            return;
        }
        while (!this.weDone) {
            final MergeResult result = mergeAvailablePullRequests();
            if (result.getCount() == 0) {
                this.weDone = true;
                continue;
            }
            // more than one PR has been merged, we need to test
            if (runTestSuite()) {
                this.weDone = true;
                for (BisectablePullRequest bisectablePullRequest : result.getMergeList()) {
                    bisectablePullRequest.markGood();
                }
                continue;
            } else {
                // TODO: stop condition on no PRs passing(should be handled in PR state)
                performBisect(result);

            }
        }
        informPRState();

        pushFutureBranch();
    }

    /**
     * Read branch name, create new name based on it and checkout
     *
     * @return
     */
    protected boolean setUpFutureBranch() {
        if (this.branch.endsWith(this.suffix)) {
            this.futureBranch = this.branch;
            CryoLogger.ROOT_LOGGER.proposedSuffixAlreadyPresent(this.branch, this.suffix);
            return true;
        } else {
            this.futureBranch = this.branch + this.suffix;
            final OperationResult result = this.operationCenter.createNewBranch(this.futureBranch);
            switch (result.getOutcome()) {
                case SUCCESS:
                    CryoLogger.ROOT_LOGGER.logCreatedBranch(this.futureBranch);
                    break;
                case FAILURE:
                default:
                    result.reportError("Failed to create future branch");
                    return false;
            }

            return true;
        }
    }

    protected boolean runTestSuite() {
        return this.cleanUpRepository() && this.buildAndRunTestsuite();
    }

    protected MergeResult mergeAvailablePullRequests() {
        // NOTE: this will fail if more than one PR depends on another ie A->C and B->C.
        List<BisectablePullRequest> mergedPullRequests = new ArrayList<>();
        for (BisectablePullRequest currentPullRequest : coldStorage) {
            if (currentPullRequest.getState() == CryoPRState.PRISTINE) {
                // TRY to merge, if there are dependencies, merge them first. Condition is those have to be in storage as well.
                if (currentPullRequest.merge()) {
                    // by design, top PR is merged only if all deps make it, if one does not, everything is reverted
                    // if (currentPullRequest.getDependencies().size() > 0) {
                    // mergedPullRequests.addAll(currentPullRequest.getDependencies());
                    // }
                    mergedPullRequests.add(currentPullRequest);
                } else {
                    // NOTE: do we need to fix this here?
                }
            }
        }
        return new MergeResult(mergedPullRequests);
    }

    /**
     * This method will perform PER PR bisect. Once it is done it will revert changes to point where TS pass, it will mark bad
     * PR as unmergable, revert state of previous PRs to pristine, so they can be merged in next batch.
     *
     * @param mergeResult
     * @return
     */
    protected void performBisect(final MergeResult mergeResult) {
        // Now this is going to be slightly nasty...
        // INFO: we dont need to guard against going back too far as we act only on MergeResult
        //Main.log(Level.INFO, "[BISECT] Starting bisect [{0}]", this.timeTracker.interim());
        dump(Level.INFO,"[BISECT] Starting state:",mergeResult.getMergeList().toArray(new BisectablePullRequest[mergeResult.getMergeList().size()]));
        // manual bisect based on PRs. PR can have more than one commit. It is easier to do this on array.
        final BisectablePullRequest[] danceFloor = mergeResult.getMergeList()
                .toArray(new BisectablePullRequest[mergeResult.getMergeList().size()]);
        int L = 0, R = danceFloor.length - 1;
        // int lastGood=-1;
        int firstBad = -1;
        while (L <= R) {
            int M = (L + R) / 2;
            adjustMergeRange(danceFloor, M);
            if (runTestSuite()) {
                // passed
                L = M + 1;
                // lastGood = M;
            } else {
                // failed
                R = M - 1;
                firstBad = M;
            }
            dump(Level.INFO,"[BISECT] State:",danceFloor);
        }
        // retain only good PRs, rest will follow in another batch of merge.
        // NOTE: check if this is correct
        //TODO: do we need to check on this reverse?
        CryoLogger.ROOT_LOGGER.failedToReverseFirstBad(firstBad);
        CryoLogger.ROOT_LOGGER.failedToReverseFirstBad(danceFloor[firstBad]);
        //INFO: PRISITINE, it might have been rewind in last adjust
        if(danceFloor[firstBad].getState() !=CryoPRState.PRISTINE && !danceFloor[firstBad].reverse()) {
            //NOTE: this should not happen unless there is something serious going on, blow up now.
            throw new RuntimeException("[CRYO] [BISECT] Failed to reverse first bad["+danceFloor[firstBad].getPullRequest().getURL()+"], repository is in corrupted state. Exploding!");
        }
        danceFloor[firstBad].markFailed();

        for (int i = 0; i < firstBad; i++) {
            danceFloor[i].markGood();
        }

        return;
    }

    /**
     * Perform either remerge or reverse based on pointer. index<=point == merged, index>point == not merged once this method is
     * done
     *
     * @param target
     * @param point - index of last merged PR.
     */
    private void adjustMergeRange(BisectablePullRequest[] target, int point) {
        // TODO: handle merge/reverse failures properly.
        // TODO: check state - is it needed? We should have clean slate here
        int currentMergePoint = target.length - 1;
        for (; currentMergePoint >= 0; currentMergePoint--) {
            if (target[currentMergePoint].getState() == CryoPRState.MERGED) {
                break;
            }
        }
        if (currentMergePoint > point) {
            // rewind, right to left.
            for (; currentMergePoint > point; currentMergePoint--) {
              //TODO: is boom enough?
                if(!target[currentMergePoint].reverse())
                    throw new RuntimeException("[CRYO] [BISECT] Failed to adjust merge range["+target[currentMergePoint].getPullRequest().getURL()+"], repository is in corrupted state. Exploding!");
            }
        } else {
            // merge, left to right
            //need to adjust, since above we looked for last merge
            //here we aim a bit higher
            currentMergePoint++;
            for (; currentMergePoint <= point; currentMergePoint++) {
                //TODO: is boom enough?
                if(!target[currentMergePoint].merge())
                    throw new RuntimeException("[CRYO] [BISECT] Failed to adjust merge range["+target[currentMergePoint].getPullRequest().getURL()+"], repository is in corrupted state. Exploding!");
            }
        }
    }

    protected void pushFutureBranch() {
        CryoLogger.ROOT_LOGGER.logPreparingFutureBranch(futureBranch);
        //TODO: update when deps are in.
        reportCurrentStateOfColdStorage();
        if(this.dryRun) {
            CryoLogger.ROOT_LOGGER.logDryRunComplete();
            return;
        }
        if (getGoodPullRequestCount() != 0) {
            final OperationResult result = this.operationCenter.pushCurrentBranch(this.futureBranch);
            switch (result.getOutcome()) {
                case SUCCESS:
                    CryoLogger.ROOT_LOGGER.logPushFutureBranchSuccessful(result.getOutput());
                    break;
                case FAILURE:
                default:
                    CryoLogger.ROOT_LOGGER.logPushFutureBranchFailed();
                    result.reportError();
            }
        } else {
            CryoLogger.ROOT_LOGGER.logPushFutureBranchSemiSuccessful();
        }
    }

    protected void reportCurrentStateOfColdStorage() {
        dump(Level.INFO,"Cold storage:", this.coldStorage.toArray(new BisectablePullRequest[this.coldStorage.size()]));
    }

    protected void dump(final Level level,final String prefix,  final BisectablePullRequest... arr) {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(prefix);
        stringBuilder.append("\n");
        for(int i=0;i<arr.length;i++) {
            stringBuilder.append(arr[i].toString());
            if(i<arr.length-1) {
                stringBuilder.append("\n");
            }
        }
    }

    protected long getGoodPullRequestCount() {
        // count only GOOD.
        return this.coldStorage.stream().filter(pr -> {
            return pr.getState() == CryoPRState.GOOD;
        }).count();
    }

    protected void informPRState() {
        for (BisectablePullRequest bpr : coldStorage) {
            CryoLogger.ROOT_LOGGER.logMessage((bpr.hasDependencies() ? "\n" : "") + bpr);
        }
    }
}

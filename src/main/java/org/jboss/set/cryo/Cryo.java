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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

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
import org.jboss.set.cryo.process.ExecuteProcess;
import org.jboss.set.cryo.process.MergeResult;
import org.jboss.set.cryo.process.ProcessResult;

/**
 * Cryo storage util. It will do some magic, merge, bisect and eventually push into cryo branch that will await awakening when
 * technology reach proper level for revival - or desperation...
 *
 * @author baranowb
 *
 */
public class Cryo {

    // TODO: check jgit in v2(though it does not have bisect it seems)
    public static final String[] COMMAND_GIT_GET_URL = new String[] {"git", "remote", "get-url", "origin" };

    public static final String[] COMMAND_GIT_GET_CURRENT_BRANCH = new String[] { "git", "rev-parse", "--abbrev-ref", "HEAD" };

    public static final String[] COMMAND_GIT_CHECKOUT_NEW_BRANCH = new String[] { "git", "checkout", "-b" };

    public static final String[] COMMAND_GIT_READ_CURRENT_COMMIT_HEAD = new String[] { "git", "rev-parse", "HEAD" };
    // git rev-list --max-count=1 FIRST_COMMIT_IN_MERGE_LIST^
    public static final String[] COMMAND_GIT_READ_PREVIOUS_COMMIT_TO = new String[] { "git", "rev-list", "--max-count=1" };

    public static final String[] COMMAND_MVN_CLEAN = new String[] { "mvn", "clean", "-DallTests" };

    public static final String[] COMMAND_MVN_INSTALL_AND_TEST = new String[] { "mvn", "install", "-fae", "-DallTests" };

    public static final String[] COMMAND_GIT_BISECT_START = new String[] { "git", "bisect", "start" };

    public static final String[] COMMAND_GIT_BISECT_GOOD = new String[] { "git", "bisect", "good" };

    public static final String[] COMMAND_GIT_BISECT_BAD = new String[] { "git", "bisect", "bad" };

    public static final String[] COMMAND_GIT_BISECT_RESTART = new String[] { "git", "bisect", "restart" };

    public static final String[] COMMAND_GIT_BISECT_RUN = new String[] { "git", "bisect", "run", "mvn", "clean", "install",
            "-fae", "-DallTests" };

    public static final String[] COMMAND_GIT_PUSH = new String[] { "git", "push", "origin" };

    //public static final String[] COMMAND_GIT_MERGE_ABORT = new String[] { "git", "merge", "--abort" };

    public static final String[] COMMAND_GIT_MERGE_ABORT = new String[] { "git", "reset", "--merge" };

    public static final String[] COMMAND_GIT_RESET_TO = new String[] { "git", "reset", "--hard" };

    public static String[] COMMAND_GIT_RESET_TO_PREVIOUS(final String commitHash) {
        final String[] cmd = Arrays.copyOf(COMMAND_GIT_RESET_TO, COMMAND_GIT_RESET_TO.length + 1);
        cmd[cmd.length - 1] = commitHash + "^";
        return cmd;
    }

    public static String[] COMMAND_GIT_RESET_TO_POINT(final String commitHash) {
        final String[] cmd = Arrays.copyOf(COMMAND_GIT_RESET_TO, COMMAND_GIT_RESET_TO.length + 1);
        cmd[cmd.length - 1] = commitHash;
        return cmd;
    }

    public static String[] COMMAND_MERGE_PR(final String prNumber) {
        return new String[] { "merge.sh", "origin", prNumber };
    }

    public static String[] COMMAND_GIT_CHECKOUT_NEW_BRANCH(final String branchName) {
        final String[] cmd = Arrays.copyOf(COMMAND_GIT_CHECKOUT_NEW_BRANCH, COMMAND_GIT_CHECKOUT_NEW_BRANCH.length + 1);
        cmd[cmd.length - 1] = branchName;
        return cmd;
    }

    public static String[] COMMAND_GIT_READ_PREVIOUS_COMMIT_TO(final String markedCommit) {
        final String[] cmd = Arrays.copyOf(COMMAND_GIT_READ_PREVIOUS_COMMIT_TO, COMMAND_GIT_READ_PREVIOUS_COMMIT_TO.length + 1);
        cmd[cmd.length - 1] = markedCommit + "^";
        return cmd;
    }

    public static String[] COMMAND_GIT_BISECT_GOOD(final String markedCommit) {
        final String[] cmd = Arrays.copyOf(COMMAND_GIT_BISECT_GOOD, COMMAND_GIT_BISECT_GOOD.length + 1);
        cmd[cmd.length - 1] = markedCommit;
        return cmd;
    }

    public static String[] COMMAND_GIT_BISECT_BAD(final String markedCommit) {
        final String[] cmd = Arrays.copyOf(COMMAND_GIT_BISECT_BAD, COMMAND_GIT_BISECT_BAD.length + 1);
        cmd[cmd.length - 1] = markedCommit;
        return cmd;
    }

    public static String[] COMMAND_GIT_PUSH(final String branch) {
        final String[] cmd = Arrays.copyOf(COMMAND_GIT_PUSH, COMMAND_GIT_PUSH.length + 1);
        cmd[cmd.length - 1] = branch;
        return cmd;
    }

    private static final SimpleContainer simpleContainer = (SimpleContainer) SimpleContainer.instance();
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

    protected Aphrodite aphrodite;
    protected List<BisectablePullRequest> coldStorage;
    protected boolean dryRun = true;
    protected boolean invert = true;
    // TODO: redo with more sophisticated state machine
    protected boolean weDone = false;

    public Cryo(final File directory, final boolean dryRun, final boolean invert) {
        this.repositoryLocation = directory;
        this.dryRun = dryRun;
        this.invert = invert;
    }

    /**
     * Perform menial tasks - get repo URL, clean up act, change branch to proper one, fetch PRs and set up data structures
     *
     */
    protected boolean init() {
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
        //final ProcessBuilder readRepoURL = new ProcessBuilder("git", "remote", "get-url", "origin");
        final ProcessBuilder readRepoURL = new ProcessBuilder(COMMAND_GIT_GET_URL);
        readRepoURL.directory(repositoryLocation);
        final ProcessResult result = new ExecuteProcess(readRepoURL).getProcessResult();
        switch (result.getOutcome()) {
            case SUCCESS:
                Main.log(Level.INFO, "[SUCCESS] Repository URL: {0}", result.getOutput());
                try {
                    this.repositoryURL = new URL(result.getOutput());
                } catch (MalformedURLException e) {
                    Main.log(Level.SEVERE, "Failed to parse repository URL!", e);
                    return false;
                }
                return true;
            case FAILURE:
            default:
                result.reportError();
                return false;
        }
    }

    protected boolean determineCurrentBranch() {

        final ProcessBuilder readCurrentBranch = new ProcessBuilder(COMMAND_GIT_GET_CURRENT_BRANCH);
        readCurrentBranch.directory(repositoryLocation);
        final ProcessResult result = new ExecuteProcess(readCurrentBranch).getProcessResult();
        switch (result.getOutcome()) {
            case SUCCESS:
                Main.log(Level.INFO, "[SUCCESS] Repository branch: {0}", result.getOutput());
                this.branch = result.getOutput();
                return true;
            case FAILURE:
            default:
                result.reportError();
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
        // final ProcessBuilder readRepoURL = new ProcessBuilder("mvn", "clean", "-DallTests");
        final ProcessBuilder cleanRepository = new ProcessBuilder(COMMAND_MVN_CLEAN);
        cleanRepository.directory(repositoryLocation);
        final ProcessResult result = new ExecuteProcess(cleanRepository).getProcessResult();
        switch (result.getOutcome()) {
            case SUCCESS:
                Main.log(Level.INFO, "Cleanup of repository: {0}", result.getOutput());
                return true;
            case FAILURE:
            default:
                result.reportError();
                return false;
        }
    }

    protected boolean buildAndRunTestsuite() {
        final ProcessBuilder buildRepository = new ProcessBuilder(COMMAND_MVN_INSTALL_AND_TEST);
        buildRepository.directory(repositoryLocation);
        final ProcessResult result = new ExecuteProcess(buildRepository).getProcessResult();
        switch (result.getOutcome()) {
            case SUCCESS:
                Main.log(Level.INFO, "[SUCCESS] Build and test: {0}", result.getOutput());
                return true;
            case FAILURE:
            default:
                result.reportError();
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
            Main.log("Failed to initialize aphrodite!", e);
        }
        return false;
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
            List<PullRequest> allPullRequests = aphrodite.getPullRequestsByState(repository, PullRequestState.OPEN);
            for (PullRequest pullRequest : allPullRequests) {
                if (pullRequest.getCodebase().getName().equalsIgnoreCase(this.branch)) {
                    final BisectablePullRequest bisectablePullRequest = new BisectablePullRequest(this.repositoryLocation,
                            pullRequest);
                    this.coldStorage.add(bisectablePullRequest);
                }
            }
            if(this.invert) {
                Collections.reverse(this.coldStorage);
            }
            return true;
        } catch (NotFoundException e) {
            Main.log(Level.SEVERE, "Failed to fetch repository '"+this.repositoryURL+"' due to:", e);
        }
        return false;
        // try {
        // //Build referencable set. Once we have those, we can take care of deps
        // final Map<String, BisectablePullRequest> temporaryColdStaorage = new TreeMap<>();
        // final Map<String, BisectablePullRequest> temporaryDependenciesColdStaorage = new TreeMap<>();
        // final Repository repository = this.aphrodite.getRepository(this.repositoryURL);
        // List<PullRequest> allPullRequests = aphrodite.getPullRequestsByState(repository, PullRequestState.OPEN);
        // for (PullRequest pullRequest : allPullRequests) {
        // if (pullRequest.getCodebase().getName().equalsIgnoreCase(this.branch)) {
        // //NOTE: ONly one level
        // final BisectablePullRequest bisectablePullRequest = new BisectablePullRequest(pullRequest);
        // if(pullRequest.hasDependencies()) {
        // temporaryDependenciesColdStaorage.put(bisectablePullRequest.getPullRequest().getId(),bisectablePullRequest);
        // } else {
        // temporaryColdStaorage.put(bisectablePullRequest.getPullRequest().getId(),bisectablePullRequest);
        // }
        // }
        // }
        //
        // //At this point we have list of all, we need to vet deps, add them and check if any are left( not in
        // temporaryColdStaorage)
        // while (temporaryColdStaorage.size() != 0) {
        // for (String pullRequestNumber : temporaryColdStaorage.keySet()) {
        // Bse
        // }
        // }
        // //TODO: mark PR as CORRUPT if deps are not in stream
        // //TODO: organize PRs deps first, followed by top PR vs only top PRs in cold storage?
        // //TODO: multilayer deps?
        // return true;
        // } catch (NotFoundException e) {
        // Main.log(Level.SEVERE, "Failed to fetch repository '%s' due to '%s'", new Object[] { this.repositoryURL, e });
        // }
        // return false;
    }

    protected void createStorage() {
        if (!init()) {
            Main.log(Level.WARNING, "Failed to initialize, check previous errors.");
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
        pushFutureBranch();
    }

    /**
     * Read branch name, create new name based on it and checkout
     *
     * @return
     */
    protected boolean setUpFutureBranch() {
        this.futureBranch = this.branch + ".future";
        final ProcessBuilder checkoutNewBranch = new ProcessBuilder(COMMAND_GIT_CHECKOUT_NEW_BRANCH(this.futureBranch));
        checkoutNewBranch.directory(repositoryLocation);
        final ProcessResult result = new ExecuteProcess(checkoutNewBranch).getProcessResult();
        switch (result.getOutcome()) {
            case SUCCESS:
                Main.log(Level.INFO, "[SUCCESS] Created branch: {0}", this.futureBranch);
                break;
            case FAILURE:
            default:
                result.reportError();
                return false;
        }

        return true;
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
        Main.log(Level.INFO, "[BISECT] Starting bisect on XXXX");
        // manual bisec based on PRs. PR can have more than one commit. It is easier to do this on array.
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
        }
        // retain only good PRs, rest will follow in another batch of merge.
        // NOTE: check if this is correct
        //TODO: do we need to check on this reverse?
        if(!danceFloor[firstBad].reverse())
            throw new RuntimeException();
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
                    throw new RuntimeException();
            }
        } else {
            // merge, left to right
            //need to adjust, since above we looked for last merge
            //here we aim a bit higher
            currentMergePoint++;
            for (; currentMergePoint <= point; currentMergePoint++) {
                //TODO: is boom enough?
                if(!target[currentMergePoint].merge())
                    throw new RuntimeException();
            }
        }
    }

    protected void pushFutureBranch() {
        Main.log(Level.INFO, "[SUCCESS] Finished preparing future branch {0}, report:",this.futureBranch);
        //TODO: update when deps are in.
        reportCurrentStateOfColdStorage();
        if(this.dryRun) {
            Main.log(Level.INFO, "[SUCCESS] Dry run complete, remote repository remain unchanged. Local repository can be pushed manually!");
            return;
        }
        if (getGoodPullRequestCount() != 0) {
            final ProcessBuilder pushBranch = new ProcessBuilder(COMMAND_GIT_PUSH(this.futureBranch));
            pushBranch.directory(repositoryLocation);
            final ProcessResult result = new ExecuteProcess(pushBranch).getProcessResult();
            switch (result.getOutcome()) {
                case SUCCESS:
                    Main.log(Level.FINE, "[BISECT] [SUCCESS] Push future branch: {0}", result.getOutput());
                    break;
                case FAILURE:
                default:
                    Main.log(Level.INFO, "[BISECT] [FAILURE] Push future branch");
                    result.reportError();
            }
        }
    }

    protected void reportCurrentStateOfColdStorage() {
        for (BisectablePullRequest bisectablePullRequest : this.coldStorage) {
            Main.log(Level.INFO, "Pull Request:[{0}] Status[{1}] Desc:[{2}]",
                    new Object[] { bisectablePullRequest.getPullRequest().getURL(),
                            bisectablePullRequest.getState(),
                            bisectablePullRequest.getPullRequest().getTitle() });
        }
    }

    protected long getGoodPullRequestCount() {
        // count only GOOD.
        return this.coldStorage.stream().filter(pr -> {
            return pr.getState() == CryoPRState.GOOD;
        }).count();
    }
}

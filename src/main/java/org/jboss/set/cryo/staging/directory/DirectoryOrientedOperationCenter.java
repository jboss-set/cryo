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
package org.jboss.set.cryo.staging.directory;

import java.io.File;
import java.io.PrintStream;
import java.nio.channels.IllegalSelectorException;
import java.util.Arrays;

import org.jboss.set.cryo.process.ExecuteProcess;
import org.jboss.set.cryo.staging.OperationCenter;
import org.jboss.set.cryo.staging.OperationResult;

public class DirectoryOrientedOperationCenter implements OperationCenter {
    protected static final String[] COMMAND_GIT_GET_URL = new String[] {"git", "remote", "get-url", "origin" };
    protected static final String[] COMMAND_GIT_GET_CURRENT_BRANCH = new String[] { "git", "rev-parse", "--abbrev-ref", "HEAD" };
    protected static final String[] COMMAND_MVN_CLEAN = new String[] { "mvn", "clean", "-DallTests" };
    protected static final String[] COMMAND_MVN_INSTALL_AND_TEST = new String[] { "mvn", "install", "-fae", "-DallTests" };
    protected static final String[] COMMAND_GIT_CHECKOUT_NEW_BRANCH = new String[] { "git", "checkout", "-b" };
    protected static final String[] COMMAND_GIT_PUSH = new String[] { "git", "push", "origin" };
    protected static final String[] COMMAND_GIT_READ_CURRENT_COMMIT_HEAD = new String[] { "git", "rev-parse", "HEAD" };
    //public static final String[] COMMAND_GIT_MERGE_ABORT = new String[] { "git", "merge", "--abort" };
    protected static final String[] COMMAND_GIT_MERGE_ABORT = new String[] { "git", "reset", "--merge" };
    protected static final String[] COMMAND_GIT_RESET_TO = new String[] { "git", "reset", "--hard" };
    protected static String[] COMMAND_GIT_CHECKOUT_NEW_BRANCH(final String branchName) {
        final String[] cmd = Arrays.copyOf(COMMAND_GIT_CHECKOUT_NEW_BRANCH, COMMAND_GIT_CHECKOUT_NEW_BRANCH.length + 1);
        cmd[cmd.length - 1] = branchName;
        return cmd;
    }

    protected static String[] COMMAND_GIT_PUSH(final String branch) {
        final String[] cmd = Arrays.copyOf(COMMAND_GIT_PUSH, COMMAND_GIT_PUSH.length + 1);
        cmd[cmd.length - 1] = branch;
        return cmd;
    }

    protected static String[] COMMAND_MERGE_PR(final String prNumber) {
        return new String[] { "pr-merge", "origin", prNumber };
    }

    protected static String[] COMMAND_GIT_RESET_TO_PREVIOUS(final String commitHash) {
        final String[] cmd = Arrays.copyOf(COMMAND_GIT_RESET_TO, COMMAND_GIT_RESET_TO.length + 1);
        cmd[cmd.length - 1] = commitHash + "^";
        return cmd;
    }

    protected static String[] COMMAND_GIT_RESET_TO_POINT(final String commitHash) {
        final String[] cmd = Arrays.copyOf(COMMAND_GIT_RESET_TO, COMMAND_GIT_RESET_TO.length + 1);
        cmd[cmd.length - 1] = commitHash;
        return cmd;
    }
    protected File repositoryLocation;
    protected void vetOperationRoom() {
        if(repositoryLocation == null) {
            throw new IllegalSelectorException();
        }
    }

    public DirectoryOrientedOperationCenter() {
        //NO op constructor for services SPI
    }
    protected DirectoryOrientedOperationCenter(final File operationRoom) {
        this.repositoryLocation = operationRoom;
    }

    @Override
    public OperationCenter initializeOperationCenter(Object[] inserts) {
        return new DirectoryOrientedOperationCenter((File)inserts[0]);
    }

    @Override
    public OperationResult determineRepositoryURL() {
        vetOperationRoom();
        final ProcessBuilder readRepoURL = new ProcessBuilder(COMMAND_GIT_GET_URL);
        readRepoURL.directory(repositoryLocation);
        return new ExecuteProcess(readRepoURL).getProcessResult();
    }

    @Override
    public OperationResult determineCurrentBranch() {
        final ProcessBuilder readCurrentBranch = new ProcessBuilder(COMMAND_GIT_GET_CURRENT_BRANCH);
        readCurrentBranch.directory(repositoryLocation);
        return new ExecuteProcess(readCurrentBranch).getProcessResult();
    }

    @Override
    public OperationResult cleanUpRepository(final PrintStream out) {
        final ProcessBuilder cleanRepository = new ProcessBuilder(COMMAND_MVN_CLEAN);
        cleanRepository.directory(repositoryLocation);
        return new ExecuteProcess(out,cleanRepository).getProcessResult();
    }

    @Override
    public OperationResult buildAndRunTestsuite(final PrintStream out) {
        final ProcessBuilder buildRepository = new ProcessBuilder(COMMAND_MVN_INSTALL_AND_TEST);
        buildRepository.directory(repositoryLocation);
        return new ExecuteProcess(out,buildRepository).getProcessResult();
    }

    @Override
    public OperationResult createNewBranch(final String newBranchName) {
        final ProcessBuilder checkoutNewBranch = new ProcessBuilder(COMMAND_GIT_CHECKOUT_NEW_BRANCH(newBranchName));
        checkoutNewBranch.directory(repositoryLocation);
        return new ExecuteProcess(checkoutNewBranch).getProcessResult();
    }

    @Override
    public OperationResult pushCurrentBranch(final String newBranchName) {
        //TODO: add read so we dont need arg?
        //TODO: or ignore
        final ProcessBuilder pushBranch = new ProcessBuilder(COMMAND_GIT_PUSH(newBranchName));
        pushBranch.directory(repositoryLocation);
        return new ExecuteProcess(pushBranch).getProcessResult();
    }

    @Override
    public OperationResult readRepositoryCommitHEAD() {
        final ProcessBuilder readCommitHead = new ProcessBuilder(COMMAND_GIT_READ_CURRENT_COMMIT_HEAD);
        readCommitHead.directory(repositoryLocation);
        return new ExecuteProcess(readCommitHead).getProcessResult();
    }

    @Override
    public OperationResult mergePullRequest(String someID) {
        final ProcessBuilder mergePullRequest = new ProcessBuilder(COMMAND_MERGE_PR(someID));
        mergePullRequest.directory(repositoryLocation);
        return new ExecuteProcess(mergePullRequest).getProcessResult();
    }

    @Override
    public OperationResult abortMerge() {
        final ProcessBuilder mergeAbort = new ProcessBuilder(COMMAND_GIT_MERGE_ABORT);
        mergeAbort.directory(repositoryLocation);
        return new ExecuteProcess(mergeAbort).getProcessResult();
    }

    @Override
    public OperationResult revertToPreviousCommit(String commitHash) {
        final ProcessBuilder mergeRevert = new ProcessBuilder(COMMAND_GIT_RESET_TO_PREVIOUS(commitHash));
        mergeRevert.directory(repositoryLocation);
        return new ExecuteProcess(mergeRevert).getProcessResult();
    }

}

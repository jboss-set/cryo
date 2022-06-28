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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.jboss.set.aphrodite.Aphrodite;
import org.jboss.set.aphrodite.config.AphroditeConfig;
import org.jboss.set.aphrodite.config.IssueTrackerConfig;
import org.jboss.set.aphrodite.config.RepositoryConfig;
import org.jboss.set.aphrodite.config.StreamConfig;
import org.jboss.set.aphrodite.config.StreamType;
import org.jboss.set.aphrodite.repository.services.common.RepositoryType;
import org.jboss.set.aphrodite.repository.services.github.GithubPullRequestHomeService;
import org.jboss.set.aphrodite.spi.AphroditeException;
import org.jboss.set.cryo.Cryo;
import org.jboss.set.cryo.process.BisectablePullRequest;
import org.jboss.set.cryo.process.ExecuteProcess;

public class CryoAccess extends Cryo {
    private static final String testDirLocation = System.getProperty("user.dir") + "/cryo-tests/";
    protected static final String[] COMMAND_GIT_CHECKOUT_MASTER = new String[] { "git", "checkout", "master" };
    protected static final String[] COMMAND_GIT_DELETE_FUTURE = new String[] { "git", "branch", "-D", "masterfuture" };
    protected static final String[] COMMAND_GIT_LOG = new String[] { "git", "log" };
    protected static Aphrodite aphro;

    CryoAccess() {
        super(new File(testDirLocation), true, false, false, new HashSet<String>(),
                CryoAccess.createIncludeList(new String[] { "1" }), "future", "", new String[] {});
    }

    CryoAccess(String[] includeList) {
        super(new File(testDirLocation), true, false, false, new HashSet<String>(),
                CryoAccess.createIncludeList(includeList), "future", "", new String[] {});
    }

    private static List<String> createIncludeList(String[] includeList) {
        List<String> include;
        include = new ArrayList<>();
        for (String pr : includeList)
            include.add(pr);
        return include;
    }

    public boolean createOperationCenter() {
        return super.createOperationCenter();
    }

    public boolean determineRepositoryURL() {
        return super.determineRepositoryURL();
    }

    public boolean determineCurrentBranch() {
        return super.determineCurrentBranch();
    }

    public boolean fetchPRList(String repoUrl) throws MalformedURLException {
        determineCurrentBranch();
        super.repositoryURL = new URL(repoUrl);
        return super.fetchPRList();
    }

    public URL getRepositoryURL() {
        return super.repositoryURL;
    }

    public void initializeAphrodite() throws MalformedURLException {

        RepositoryConfig githubService = new RepositoryConfig("https://github.com/", "fazer1929",
                "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",
                RepositoryType.GITHUB);
        List<RepositoryConfig> repositoryConfigs = new ArrayList<>();
        repositoryConfigs.add(githubService);

        List<IssueTrackerConfig> issueTrackerConfigs = new ArrayList<>();

        StreamConfig streamService = new StreamConfig(
                new URL("https://raw.githubusercontent.com/jboss-set/jboss-streams/master/streams.json"), StreamType.JSON);
        List<StreamConfig> streamConfigs = new ArrayList<>();
        streamConfigs.add(streamService);

        try {
            AphroditeConfig aphroditeConfig = new AphroditeConfig(issueTrackerConfigs, repositoryConfigs, streamConfigs);
            this.aphrodite = Aphrodite.instance(aphroditeConfig);
            GithubPullRequestHomeService GithubPullRequestHomeService = new GithubPullRequestHomeService(aphrodite);
            super.aphrodite = this.aphrodite;
        } catch (AphroditeException e) {
            e.printStackTrace();
        }
    }

    public void setUpCryo(String repoURL) throws MalformedURLException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                new String[] { "git", "clone", repoURL });
        ExecuteProcess executeProcess = new ExecuteProcess(processBuilder);
        executeProcess.getProcessResult();

        this.createOperationCenter();
        if (CryoAccess.aphro == null) {
            this.initializeAphrodite();
            CryoAccess.aphro = super.aphrodite;
        } else {
            this.aphrodite = CryoAccess.aphro;
            super.aphrodite = CryoAccess.aphro;
        }
    }

    private void cleanupFutureBranch() {
        ProcessBuilder processBuilder = new ProcessBuilder(
                COMMAND_GIT_CHECKOUT_MASTER);
        processBuilder.directory(new File(testDirLocation));
        ExecuteProcess executeProcess = new ExecuteProcess(processBuilder);
        executeProcess.getProcessResult();
        processBuilder = new ProcessBuilder(
                COMMAND_GIT_DELETE_FUTURE);
        processBuilder.directory(new File(testDirLocation));
        executeProcess = new ExecuteProcess(processBuilder);
        executeProcess.getProcessResult();
    }

    public boolean setUpFutureBranch() {
        if (super.setUpFutureBranch()) {
            cleanupFutureBranch();
            return true;
        }
        return false;
    }

    private boolean checkShaExists() {
        // Getting latest commit ids of every eligible PR
        List<String> shas = new ArrayList<>();
        for (BisectablePullRequest bpr : coldStorage) {
            shas.add(bpr.getPullRequest().getCommits().get(0).getSha());
        }
        boolean includesAll = true;

        // Getting the logs of directory cryo created

        ProcessBuilder processBuilder = new ProcessBuilder(
                COMMAND_GIT_LOG);
        processBuilder.directory(new File(testDirLocation));
        ExecuteProcess executeProcess = new ExecuteProcess(processBuilder);
        String logs = executeProcess.getProcessResult().getOutput();

        // Checking id all the commits exist
        for (String sha : shas) {
            includesAll = logs.contains(sha);
            if (!includesAll)
                break;
        }
        return includesAll;
    }

    public boolean mergePRs() {
        try {
            cleanupFutureBranch();
            super.createStorage();
            if (checkShaExists())
                return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
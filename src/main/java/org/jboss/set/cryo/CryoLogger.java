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
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.set.aphrodite.spi.AphroditeException;
import org.jboss.set.aphrodite.spi.NotFoundException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.file.Path;

import static org.jboss.logging.Logger.Level.*;

@MessageLogger(projectCode = "UT")
public interface CryoLogger extends BasicLogger {

    CryoLogger ROOT_LOGGER = Logger.getMessageLogger(CryoLogger.class, CryoLogger.class.getPackage().getName());
    CryoLogger PROXY_REQUEST_LOGGER = Logger.getMessageLogger(CryoLogger.class, CryoLogger.class.getPackage().getName() + ".proxy");
    CryoLogger REQUEST_DUMPER_LOGGER = Logger.getMessageLogger(CryoLogger.class, CryoLogger.class.getPackage().getName() + ".request.dump");


    @LogMessage(level = INFO)
    @Message(id = 5001, value = "Cleaning up repository")
    void cleaningRepository();

    @LogMessage(level = INFO)
    @Message(id = 5002, value = "Cleaning up repository %s")
    void cleaningRepository(String TimeTrackerInterim);

    @LogMessage(level = ERROR)
    @Message(id = 5003, value = "Failed to Initialize Aphrodite\n%s")
    void failedToInitializeAphrodite(AphroditeException e);

    @LogMessage(level = FATAL)
    @Message(id = 5004, value = "Failed to create OperationCenter")
    void failedToCreateOperationCenter();

    @LogMessage(level = INFO)
    @Message(id = 5005, value = "[SUCCESS] Repository URL: %s")
    void logRepositoryURL(String repoURL);

    @LogMessage(level = ERROR)
    @Message(id = 5006, value = "Failed to parse repository URL!")
    void failedToParseRepositoryURL();

    @LogMessage(level = INFO)
    @Message(id = 5007, value = "[SUCCESS] Repository Branch: %s")
    void logRepositoryBranch(String repoBranch);

    @LogMessage(level = FATAL)
    @Message(id = 5008, value = "Failed to parse repository URL!")
    void failedToParseRepositoryBranch();

    @LogMessage(level = INFO)
    @Message(id = 5009, value = "[SUCCESS] Build and Test: %s")
    void logBuildAndTest(String buildAndTestSuite);

    @LogMessage(level = ERROR)
    @Message(id = 5010, value = "Failed Build and Test!")
    void failedToBuildAndTest();

    @LogMessage(level = INFO)
    @Message(id = 5011, value = "Fetching PR list, desired codebase: %s")
    void logFetchingPRList(String[] PRList);

    @LogMessage(level = INFO)
    @Message(id = 5012, value = "Retaining PullRequest: %s")
    void logRetainingPRList(Object[] PRList);

    @LogMessage(level = INFO)
    @Message(id = 5013, value = "Purging PullRequest: %s")
    void logPurgingPRList(Object[] PRList);

    @LogMessage(level = FATAL)
    @Message(id = 5014, value = "Bad PullRequest, skipping: %s")
    void failedToFetchPR(String[] PRList);

    @LogMessage(level = WARN)
    @Message(id = 5015, value = "Failed to find PR[%s] in list of available PullRequests")
    void failedToFindPR(String[] PRList);

    @LogMessage(level = INFO)
    @Message(id = 5016, value = "Searching for dependencies for PR,list: [%s]")
    void logSearchingForDependencies(Object deps);

    @LogMessage(level = WARN)
    @Message(id = 5017, value = "Failed to find dependency for PR[%s], dependency [%s]")
    void failedToFindDependecy(URL pr, URL dependency);

    @LogMessage(level = FATAL)
    @Message(id = 5018, value = "Failed to fetch repository '%s' due to %s")
    void failedToFectchRepository(URL repoURL, Exception e);

    @LogMessage(level = WARN)
    @Message(id = 5019, value = "[%s], does not meet requirments for merge")
    void failedToMeetRequirementsForMerge(Object pullRequests[]);

    @LogMessage(level = INFO)
    @Message(id = 5020, value = "[%s], does meet requirments for merge")
    void logPRMeetsRequirements(Object pullRequests[]);

    @LogMessage(level = ERROR)
    @Message(id = 5021, value = "Failed to initialize, check previous errors.")
    void failedToInitialize();

    @LogMessage(level = WARN)
    @Message(id = 5022, value = "Current branch [%s] already has proposed suffix [%s]. Either CRYO already ran or configuration is off a bit.")
    void proposedSuffixAlreadyPresent(String branch, String suffix);

    @LogMessage(level = INFO)
    @Message(id = 5023, value = "Created branch: %s")
    void logCreatedBranch(String branch);

    @LogMessage(level = ERROR)
    @Message(id = 5024, value = "Failed to create future branch.")
    void failedToCreateBranch();

    @LogMessage(level = INFO)
    @Message(id = 5025, value = "[BISECT] found first bad[%s]")
    void logFirstBad(Object fristBad[]);

    @LogMessage(level = INFO)
    @Message(id = 5027, value = "[SUCCESS] Finished preparing future branch {%s}, report:")
    void logPreparingFutureBranch(String futureBranch);

    @LogMessage(level = INFO)
    @Message(id = 5028, value = "Dry run complete, remote repository remain unchanged. Local repository can be pushed manually!")
    void logDryRunComplete();

    @LogMessage(level = INFO)
    @Message(id = 5029, value = "[BISECT] [SUCCESS] Push future branch: %s")
    void logPushFutureBranchSuccessful(String branch);

    @LogMessage(level = INFO)
    @Message(id = 5030, value = "[BISECT] [Failure] Push future branch")
    void logPushFutureBranchFailed();

    @LogMessage(level = INFO)
    @Message(id = 5031, value = "[BISECT] [SEMI-SUCCESS] Run complete, but there is nothing to merge.... what a waste of time!")
    void logPushFutureBranchSemiSuccessful();

    @LogMessage(level = WARN)
    @Message(id = 5032, value = "Can not fetch label list for: %s")
    void failedToFetchLableList(URL prURL);

    @LogMessage(level = WARN)
    @Message(id = 5033, value = "Pull Request[%s] is already dependency of [%s], can not add it as dependency of [%s]. Marking both as corrupted!")
    void logPRIsAlreadyADependency(URL bisectablePrURL, URL prDependentURL, URL prURL);

    @LogMessage(level = WARN)
    @Message(id = 5034, value = "Failed to merge dependency of PR[%s], failed dependency[%s]")
    void failedToMergeDependency(URL prURL, URL prDependentURL);

    @LogMessage(level = INFO)
    @Message(id = 5035, value = "[SUCCESS] Merge of: %s")
    void logSuccessfulMerge(String id);

    @LogMessage(level = INFO)
    @Message(id = 5036, value = "[SUCCESS] Revert pull request after failure: %s")
    void logSuccessfulRevertAfterFailure(String id);

    @LogMessage(level = INFO)
    @Message(id = 5037, value = "Revert pull request after failure: %s")
    void failedToRevertPR(String id);

    @LogMessage(level = INFO)
    @Message(id = 5038, value = "[SUCCESS] Revert pull request: %s to previous %s, %s")
    void logRevertPullRequest(String id, String mergeCommitID, String output);

    @LogMessage(level = INFO)
    @Message(id = 5039, value = "[SUCCESS] Transformed Repository URL: %s")
    void logTransformedRepositoryURL(String tempURL);

    @LogMessage(level = INFO)
    @Message(id = 5040, value = "%s")
    void logMessage(String msg);

    @LogMessage(level = INFO)
    @Message(id = 5041, value = "[SUCCESS] Execution of [%s] went smooth: %s")
    void logExecutionWentSmooth(String cmd, String output);

    @LogMessage(level = FATAL)
    @Message(id = 5042, value = "[FAILED] Execution of %s failed with: \n%s")
    void failedToExecuteCommand(String cmd, Object err);

//    @LogMessage(level = FATAL)
//    @Message(id = 5043, value = "FAILED] Execution of %s failed with: \n%s")
//    void failedToExecuteCommand(String cmd, String err);

    @LogMessage(level = FATAL)
    @Message(id = 5044, value = "FAILED] Execution failed somehow.... %s")
    void failedToExecuteCommand(String output);

    @LogMessage(level = FATAL)
    @Message(id = 5045, value = "FAILED] %s: %s failed %s with: %s")
    void failedToExecuteCustom(String custom, String cmd, String out, Throwable err);

    @LogMessage(level = FATAL)
    @Message(id = 5046, value = "FAILED] %s: %s failed with: %s")
    void failedToExecuteCustom(String custom, String cmd, Object err);

    @LogMessage(level = ERROR)
    @Message(id = 5047, value = "[[BISECT] Failed to reverse first bad[%s], repository is in corrupted state. Exploding!")
    void failedToReverseFirstBad(Object badPRURL);

    @LogMessage(level = FATAL)
    @Message(id = 5048, value = "FAILED] %s: %s ")
    void failedToExecuteCustom(String custom, String cmd);
}

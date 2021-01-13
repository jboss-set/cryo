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
package org.jboss.set.cryo.staging;

import java.io.PrintStream;

/**
 * Provider interface that hides how requests are carried out.
 * @author baranowb
 *
 */
public interface OperationCenter {
    OperationCenter initializeOperationCenter(Object[] inserts);

    OperationResult abortMerge();
    OperationResult buildAndRunTestsuite(final PrintStream out, final String[] args);
    OperationResult cleanUpRepository(final PrintStream out, final String[] args);
    OperationResult createNewBranch(final String newBranchName);
    OperationResult determineCurrentBranch();
    OperationResult determineRepositoryURL();
    OperationResult mergePullRequest(final String someID);
    OperationResult pushCurrentBranch(final String newBranchName);
    OperationResult readRepositoryCommitHEAD();
    OperationResult revertToPreviousCommit(final String commitHash);
}

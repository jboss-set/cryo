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

import org.jboss.set.cryo.process.ExecuteProcess;
import org.jboss.set.cryo.staging.OperationCenter;
import org.jboss.set.cryo.staging.OperationResult;
import org.jboss.set.cryo.staging.OperationResult.Outcome;

public class HarmoniaOperationCenter extends DirectoryOrientedOperationCenter {
    public static final String ENV_HARMONIA_BUILD_SH="HARMONIA_BUILD_SCRIPT";
    protected static final String[] COMMAND_HARMONIA_BUILD = new String[] { "bash", "-x", System.getenv("ENV_HARMONIA_BUILD_SH"), "build" };
    protected static final String[] COMMAND_HARMONIA_TEST = new String[] { "bash", "-x", System.getenv("ENV_HARMONIA_BUILD_SH"), "testsuite" };

    public HarmoniaOperationCenter() {
        //NO op constructor for services SPI
    }

    public HarmoniaOperationCenter(final File file) {
        super(file);
    }
    @Override
    public OperationResult buildAndRunTestsuite(final PrintStream out, final String[] args) {
        if (System.getenv("ENV_HARMONIA_BUILD_SH") == null
                || System.getenv("ENV_HARMONIA_BUILD_SH").equals("")
                || System.getenv("ENV_HARMONIA_BUILD_SH").equals("null")) {
            throw new RuntimeException("env.ENV_HARMONIA_BUILD_SH needs to be specified to run build and testsuite.");
        }
        //INFO: tad cheat, since harmonia splits build and test
        final ProcessBuilder buildRepository = new ProcessBuilder(COMMAND_HARMONIA_BUILD);
        buildRepository.directory(repositoryLocation);
        OperationResult result = new ExecuteProcess(out,buildRepository).getProcessResult();
        if(result.getOutcome() == Outcome.FAILURE) {
            return result;
        }
        final ProcessBuilder testRepository = new ProcessBuilder(COMMAND_HARMONIA_TEST);
        testRepository.directory(repositoryLocation);
        return new ExecuteProcess(out,testRepository).getProcessResult();
    }

    @Override
    public OperationResult cleanUpRepository(final PrintStream out, final String[] args) {
        //NO-OP, harmonia handles it via scripts, there is no separate clean command
        final ProcessBuilder cleanRepository = new ProcessBuilder(COMMAND_MVN_CLEAN);
        cleanRepository.directory(repositoryLocation);
        return new OperationResult(cleanRepository, Outcome.SUCCESS, "NO-OP");
    }

    @Override
    public OperationCenter initializeOperationCenter(Object[] inserts) {
        return new HarmoniaOperationCenter((File)inserts[0]);
    }
}
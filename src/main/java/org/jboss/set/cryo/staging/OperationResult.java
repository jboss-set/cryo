
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

import java.util.logging.Level;

import org.jboss.set.cryo.Cryo;
import org.jboss.set.cryo.CryoLogger;
import org.jboss.set.cryo.Main;

public class OperationResult {

    private Outcome outcome;
    private String output;
    private Throwable error;
    //TODO: possible separate this as well?
    private ProcessBuilder processBuilder;

    public OperationResult(final ProcessBuilder processBuilder, final Outcome outcome, final String output) {
        super();
        this.processBuilder = processBuilder;
        this.outcome = outcome;
        this.output = output;
    }

    public OperationResult(final ProcessBuilder processBuilder, final Outcome outcome, final String output,
                           final Exception error) {
        super();
        this.processBuilder = processBuilder;
        this.outcome = outcome;
        this.output = output;
        this.error = error;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getOutput() {
        return output;
    }

    public Throwable getError() {
        return error;
    }

    public void reportSuccess() {
        if (this.outcome == Outcome.SUCCESS) {
            CryoLogger.ROOT_LOGGER.logExecutionWentSmooth(String.join(" ", this.processBuilder.command().toArray(new String[0])), this.output );
        }
    }

    public void reportSuccess(final String msg) {
        if (this.outcome == Outcome.SUCCESS) {
            CryoLogger.ROOT_LOGGER.logMessage(msg);
        }
    }

    public void reportError() {
        if (this.outcome == Outcome.FAILURE) {
            final String command = String.join(" ", this.processBuilder.command().toArray(new String[0]));
            if (this.output != null && this.error != null) {
                CryoLogger.ROOT_LOGGER.failedToExecuteCommand(command,this.error);
            } else if (this.output == null && this.error != null) {
                CryoLogger.ROOT_LOGGER.failedToExecuteCommand(command,this.error);
            } else if (this.output != null && this.error == null) {
                CryoLogger.ROOT_LOGGER.failedToExecuteCommand(command,this.output);
            } else {
                CryoLogger.ROOT_LOGGER.failedToExecuteCommand(String.join(" ", this.processBuilder.command().toArray(new String[0])));
            }
        }
    }

    public void reportError(final String custom) {
        if (this.outcome == Outcome.FAILURE) {
            final String command = String.join(" ", this.processBuilder.command().toArray(new String[0]));
            if (this.output != null && this.error != null) {
                CryoLogger.ROOT_LOGGER.failedToExecuteCustom(custom,command,this.output,this.error);
            } else if (this.output == null && this.error != null) {
                CryoLogger.ROOT_LOGGER.failedToExecuteCustom(custom,command,this.error);
            } else if (this.output != null && this.error == null) {
                CryoLogger.ROOT_LOGGER.failedToExecuteCustom(custom,command, this.output);
            } else {
                CryoLogger.ROOT_LOGGER.failedToExecuteCustom(custom,
                        String.join(" ", this.processBuilder.command().toArray(new String[0])));
            }
        }
    }

    public static enum Outcome {
        SUCCESS, FAILURE;
    }
}
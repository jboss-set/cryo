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

import java.util.logging.Level;

import org.jboss.set.cryo.Main;

public class ProcessResult {

    private Outcome outcome;
    private String output;
    private Throwable error;
    private ProcessBuilder processBuilder;

    public ProcessResult(final ProcessBuilder processBuilder, final Outcome outcome, final String output) {
        super();
        this.processBuilder = processBuilder;
        this.outcome = outcome;
        this.output = output;
    }

    public ProcessResult(final ProcessBuilder processBuilder, final Outcome outcome, final String output,
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
            Main.log(Level.INFO, "[SUCCESS] Execution of [{0}] went smooth: {1}' ",
                    new Object[] { String.join(" ", this.processBuilder.command().toArray(new String[0])), this.output });
        }
    }

    public void reportSuccess(final String msg) {
        if (this.outcome == Outcome.SUCCESS) {
            Main.log(Level.INFO, "[SUCCESS] {0}",
                    new Object[] {msg});
        }
    }

    public void reportError() {
        if (this.outcome == Outcome.FAILURE) {
            final String command = String.join(" ", this.processBuilder.command().toArray(new String[0]));
            if (this.output != null && this.error != null) {
                Main.log("[FAILED] Execution of '"+command+"' failed '"+this.output+"' with:\n", this.error);
            } else if (this.output == null && this.error != null) {
                Main.log("[FAILED] Execution of '"+command+"' failed with:\n",this.error);
            } else if (this.output != null && this.error == null) {
                Main.log(Level.SEVERE, "[FAILED] Execution of [{0}] failed:\n{1} ",
                        new Object[] { command, this.output });
            } else {
                Main.log(Level.SEVERE, "[FAILED] Execution failed somehow.... {0}",
                        String.join(" ", this.processBuilder.command().toArray(new String[0])));
            }
        }
    }

    public void reportError(final String custom) {
        if (this.outcome == Outcome.FAILURE) {
            final String command = String.join(" ", this.processBuilder.command().toArray(new String[0]));
            if (this.output != null && this.error != null) {
                Main.log("[FAILED] "+custom+":'"+command+"' failed '"+this.output+"' with:\n", this.error);
            } else if (this.output == null && this.error != null) {
                Main.log("[FAILED] "+custom+":'"+command+"' failed with:\n",this.error);
            } else if (this.output != null && this.error == null) {
                Main.log(Level.SEVERE, "[FAILED]  {0}:[{1}] failed:\n{2} ",
                        new Object[] { custom,command, this.output });
            } else {
                Main.log(Level.SEVERE, "[FAILED] {0}.Execution failed somehow.... {1}", new Object[] {custom,
                        String.join(" ", this.processBuilder.command().toArray(new String[0]))});
            }
        }
    }

    public static enum Outcome {
        SUCCESS, FAILURE;
    }
}

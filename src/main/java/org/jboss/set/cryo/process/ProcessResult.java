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
            Main.log(Level.SEVERE, "[SUCCESS] Execution of \'{0}\' went smooth \'{1}\' ",
                    new Object[] { String.join(" ", this.processBuilder.command().toArray(new String[0])), this.output });
        }
    }

    public void reportError() {
        if (this.outcome == Outcome.FAILURE) {
            final String command = String.join(" ", this.processBuilder.command().toArray(new String[0]));
            if (this.output != null && this.error != null) {
                Main.log("[FAILED] Execution of '"+command+"' failed '"+this.output+"' with: ", this.error);
            } else if (this.output == null && this.error != null) {
                Main.log("[FAILED] Execution of '"+command+"' failed with: ",this.error);
            } else if (this.output != null && this.error == null) {
                Main.log(Level.SEVERE, "[FAILED] Execution of [{0}] failed:\n {1} ",
                        new Object[] { command, this.output });
            } else {
                Main.log(Level.SEVERE, "[FAILED] Execution failed somehow.... {0}",
                        String.join(" ", this.processBuilder.command().toArray(new String[0])));
            }
        }
    }

    public static enum Outcome {
        SUCCESS, FAILURE;
    }
}

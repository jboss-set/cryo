package org.jboss.set.cryo.process;

import java.util.logging.Level;

import org.jboss.set.cryo.Main;

public class ProcessResult {

    private Outcome outcome;
    private String output;
    private Exception error;
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

    public Exception getError() {
        return error;
    }

    public void reportSuccess() {
        if (this.outcome == Outcome.SUCCESS) {
            Main.log(Level.SEVERE, "[SUCCESS] Execution of '%s' went smooth '%s' ",
                    new Object[] { String.join(" ", this.processBuilder.command().toArray(new String[0])), this.output });
        }
    }

    public void reportError() {
        if (this.outcome == Outcome.FAILURE) {
            if (this.output != null && this.error != null) {
                Main.log(Level.SEVERE, "[FAILED] Execution of '%s' failed '%s' with '%s'", new Object[] {
                        String.join(" ", this.processBuilder.command().toArray(new String[0])), this.output, this.error });
            } else if (this.output == null && this.error != null) {
                Main.log(Level.SEVERE, "[FAILED] Execution of '%s' failed with '%s'",
                        new Object[] { String.join(" ", this.processBuilder.command().toArray(new String[0])), this.error });
            } else if (this.output != null && this.error == null) {
                Main.log(Level.SEVERE, "[FAILED] Execution of '%s' failed '%s' ",
                        new Object[] { String.join(" ", this.processBuilder.command().toArray(new String[0])), this.output });
            } else {
                Main.log(Level.SEVERE, "[FAILED] Execution failed somehow.... '' ",
                        String.join(" ", this.processBuilder.command().toArray(new String[0])));
            }
        }
    }

    public static enum Outcome {
        SUCCESS, FAILURE;
    }
}

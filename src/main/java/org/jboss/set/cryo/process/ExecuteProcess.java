package org.jboss.set.cryo.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class ExecuteProcess {

    protected final ProcessBuilder processBuilder;

    public ExecuteProcess(ProcessBuilder processBuilder) {
        super();
        this.processBuilder = processBuilder;
    }

    public ProcessResult getProcessResult() {
        Process process = null;
        try {
            process = processBuilder.start();
            final int result = process.waitFor();
            final String output = readOutput(process);
            return result == 0 ? new ProcessResult(processBuilder, ProcessResult.Outcome.SUCCESS, output) : new ProcessResult(processBuilder, ProcessResult.Outcome.FAILURE, output);
        } catch( Exception e) {
            if(process != null) {
//                final int result = process.exitValue();
                final String output = readOutput(process);
                return new ProcessResult(processBuilder, ProcessResult.Outcome.FAILURE, output,e);
            } else {
                return new ProcessResult(processBuilder, ProcessResult.Outcome.FAILURE, null, e);
            }
        }
    }

    public static String readOutput(final Process process) {
        final StringBuilder stringBuilder = new StringBuilder();
        final InputStream inputStream = process.getInputStream();
        final BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
        try {
            while(inputStream.available() > 0) {
                stringBuilder.append(br.lines().collect(Collectors.joining("\n")));
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }
}

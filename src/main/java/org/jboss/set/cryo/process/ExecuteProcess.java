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

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.jboss.set.cryo.CryoLogger;
import org.jboss.set.cryo.Main;
import org.jboss.set.cryo.staging.OperationResult;

public class ExecuteProcess {

    private static final Pattern PROGRESS_DOWNLOAD_MATCH = Pattern.compile("\\s*\\b(?:Progress|Download| kB )\\b\\s*",
            Pattern.CASE_INSENSITIVE);
    protected final ProcessBuilder processBuilder;
    protected final PrintStream out;

    public ExecuteProcess(PrintStream out, ProcessBuilder processBuilder) {
        super();
        this.processBuilder = processBuilder;
        this.out = out;
    }

    public ExecuteProcess(ProcessBuilder processBuilder) {
        super();
        this.processBuilder = processBuilder;
        this.out = null;
    }
    public OperationResult getProcessResult() {
        Process process = null;
        FileWriter fWriter = null;
        try {
            process = processBuilder.start();
            String output = "<EMPTY>";
            int result;
            fWriter = new FileWriter("cryo_logs.txt");
            if(out != null) {
                //INFO: long running command, we dont care about output; Just dump it;
                while (process.isAlive()) {
                    if (Main.isFast()) {
                        final InputStream inputStream = process.getInputStream();
                        while (inputStream.available() > 0) {
                            IOUtils.copy(inputStream, this.out);
                        }
                    } else {
                        final BufferedReader inputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        String line;
                        while ((line = inputStream.readLine()) != null) {
                            // Main.log(Level.INFO, line);
                            if (!PROGRESS_DOWNLOAD_MATCH.matcher(line).find())
                                CryoLogger.ROOT_LOGGER.logMessage(line);
                            else
                                fWriter.append(line+"\n");
                        }
                    }
                }
                result = process.waitFor();
            } else {
            result = process.waitFor();
            output = readOutput(process);
            }
            return result == 0 ? new OperationResult(processBuilder, OperationResult.Outcome.SUCCESS, output) : new OperationResult(processBuilder, OperationResult.Outcome.FAILURE, output);
        } catch( Exception e) {
            if(process != null) {
//                final int result = process.exitValue();
                //TODO: this might not be optimal. as output might be genormous.
                final String output = readOutput(process);
                return new OperationResult(processBuilder, OperationResult.Outcome.FAILURE, output,e);
            } else {
                return new OperationResult(processBuilder, OperationResult.Outcome.FAILURE, null, e);
            }
        } finally {
            try {
                if (fWriter != null)
                    fWriter.close();
            } catch (Exception e) {
                e.printStackTrace();
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
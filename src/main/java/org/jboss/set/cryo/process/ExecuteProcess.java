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

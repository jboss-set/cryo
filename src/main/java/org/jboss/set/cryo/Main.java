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

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class Main {
    private static Logger LOGGER = Logger.getLogger(Main.class.getPackage().getName());
//    static {
//        try {
//            //TODO fix this or run with: -Djava.util.logging.SimpleFormatter.format='%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %2$s %5$s%6$s%n'
//            //LogManager.getLogManager().readConfiguration(Main.class.getClassLoader().getResourceAsStream("logging.properties"));
//            LOGGER = Logger.getLogger(Main.class.getPackage().getName());
//        } catch (SecurityException | IOException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//    }
    public static void main(String[] args) throws Exception {

          // NOTE: does not seem we need args? branchName, URL etc can be retrieved from repo.
        ArgumentParser parser = ArgumentParsers.newArgumentParser("cryo");
        parser.addArgument("-r", "--repository").nargs(1).required(true)
                .help("Full patht o local clone of remote repository");
        parser.addArgument("-d", "--dry-run").action(Arguments.storeTrue()).required(false)
        .help("If present no changes will be pushed to remote repo, only local one will contain. Good for validation.");
        parser.addArgument("-i", "--invert").action(Arguments.storeTrue()).required(false)
        .help("Invert order of PRs. By default aphrodite/github return new PRs first.");
        try {
            Namespace ns = parser.parseArgs(args);
            final File directory = new File(ns.getString("repository").replace("[", "").replace("]", ""));

            final Cryo freezerProgram = new Cryo(directory,ns.getBoolean("dry_run"), ns.getBoolean("invert"));
            freezerProgram.createStorage();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }

    public static void log(final Level level, final String msg) {
        LOGGER.log(level, "[CRYO]: " + msg);
    }

    public static void log(final Level level, final String msg, final Object param) {
        LOGGER.log(level, "[CRYO]: " + msg, param);
    }

    public static void log(final Level level, final String msg, final Object[] params) {
        LOGGER.log(level, "[CRYO]: " + msg, params);
    }

    public static void log(final String msg, final Throwable t) {
        LOGGER.log(Level.SEVERE, "[CRYO]: " + msg, t);
    }
}

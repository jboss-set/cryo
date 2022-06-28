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

import static junit.framework.TestCase.assertEquals;

import java.net.MalformedURLException;
import java.net.URL;

import org.jboss.set.cryo.process.ExecuteProcess;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestCryo {
    private static CryoAccess cryo;
    private static String repoURL;
    protected static final String[] COMMAND_REMOVE_TEST_DIR = new String[]{"rm", "-rf", "cryo-tests"};

    public TestCryo() throws MalformedURLException {
        repoURL = "https://github.com/jboss-set/cryo-tests";
        cryo = new CryoAccess();
        System.setProperty("aphrodite.config", "/home/abagrawa/Desktop/aphrodite.properties.json");
        cryo.setUpCryo(repoURL);

    }

    @BeforeClass
    public static void setup() throws MalformedURLException {
        System.out.println("Lol");
    }

    @Test
    public void test1DetermineRepositoryURL() throws MalformedURLException {
        assertEquals(cryo.determineRepositoryURL(), true);
        assertEquals(cryo.getRepositoryURL(), new URL(repoURL));
    }

    @Test
    public void test2DetermineCurrentBranch() {
        assertEquals(cryo.determineCurrentBranch(), true);
    }

    @Test
    public void test3FetchPRList() throws MalformedURLException {
        assertEquals(cryo.fetchPRList(repoURL), true);
    }

    @Test
    public void test4SetupFutureBranch() throws Exception {
        cryo.fetchPRList(repoURL);
        assertEquals(cryo.setUpFutureBranch(), true);
    }

    @Test
    public void test5MergeSinglePR() throws MalformedURLException {
        assertEquals(cryo.mergePRs(), true);
    }

    @Test
    public void test6MergemultiplePRs() throws MalformedURLException {
        cryo = new CryoAccess(new String[]{"2", "3"});
        cryo.setUpCryo(repoURL);
        assertEquals(cryo.mergePRs(), true);
    }

    @Test
    public void test7MergePRWithDependency() throws MalformedURLException {
        cryo = new CryoAccess(new String[]{"10", "11", "12"});
        cryo.setUpCryo(repoURL);
        assertEquals(cryo.mergePRs(), true);
    }

    @AfterClass
    public static void removeDir() {
        ProcessBuilder processBuilder = new ProcessBuilder(COMMAND_REMOVE_TEST_DIR);
        ExecuteProcess executeProcess = new ExecuteProcess(processBuilder);
        executeProcess.getProcessResult();
    }
}
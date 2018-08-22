/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.tools.ant.taskdefs.optional.junitlauncher;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.BuildFileRule;
import org.example.junitlauncher.jupiter.JupiterSampleTest;
import org.example.junitlauncher.vintage.AlwaysFailingJUnit4Test;
import org.example.junitlauncher.vintage.ForkedTest;
import org.example.junitlauncher.vintage.JUnit4SampleTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.example.junitlauncher.Tracker.verifyFailed;
import static org.example.junitlauncher.Tracker.verifySkipped;
import static org.example.junitlauncher.Tracker.verifySuccess;
import static org.example.junitlauncher.Tracker.wasTestRun;

/**
 * Tests the {@link JUnitLauncherTask}
 */
public class JUnitLauncherTaskTest {

    @Rule
    public final BuildFileRule buildRule = new BuildFileRule();

    /**
     * The JUnit setup method.
     */
    @Before
    public void setUp() {
        buildRule.configureProject("src/etc/testcases/taskdefs/optional/junitlauncher.xml");
    }

    /**
     * Tests that when a test, that's configured with {@code haltOnFailure=true}, stops the build, when the
     * test fails
     */
    @Test
    public void testFailureStopsBuild() throws Exception {
        final String targetName = "test-failure-stops-build";
        final Path trackerFile = setupTrackerProperty(targetName);
        try {
            buildRule.executeTarget(targetName);
            Assert.fail(targetName + " was expected to fail");
        } catch (BuildException e) {
            // expected, but do further tests to make sure the build failed for expected reason
            if (!verifyFailed(trackerFile, AlwaysFailingJUnit4Test.class.getName(),
                    "testWillFail")) {
                // throw back the original cause
                throw e;
            }
        }
    }

    /**
     * Tests that when a test, that's isn't configured with {@code haltOnFailure=true}, continues the
     * build even when there are test failures
     */
    @Test
    public void testFailureContinuesBuild() throws Exception {
        final String targetName = "test-failure-continues-build";
        final Path trackerFile = setupTrackerProperty(targetName);
        buildRule.executeTarget(targetName);
        // make sure the test that was expected to be run (and fail), did indeed fail
        Assert.assertTrue("AlwaysFailingJUnit4Test#testWillFail was expected to run", wasTestRun(trackerFile,
                AlwaysFailingJUnit4Test.class.getName(), "testWillFail"));
        Assert.assertTrue("AlwaysFailingJUnit4Test#testWillFail was expected to fail", verifyFailed(trackerFile,
                AlwaysFailingJUnit4Test.class.getName(), "testWillFail"));
    }

    /**
     * Tests the execution of test that's expected to succeed
     */
    @Test
    public void testSuccessfulTests() throws Exception {
        final String targetName = "test-success";
        final Path trackerFile = setupTrackerProperty(targetName);
        buildRule.executeTarget(targetName);
        // make sure the right test(s) were run
        Assert.assertTrue("JUnit4SampleTest test was expected to be run", wasTestRun(trackerFile, JUnit4SampleTest.class.getName()));
        Assert.assertTrue("JUnit4SampleTest#testFoo was expected to succeed", verifySuccess(trackerFile,
                JUnit4SampleTest.class.getName(), "testFoo"));
    }

    /**
     * Tests execution of a test which is configured to execute only a particular set of test methods
     */
    @Test
    public void testSpecificMethodTest() throws Exception {
        final String targetSpecificMethod = "test-one-specific-method";
        final Path tracker1 = setupTrackerProperty(targetSpecificMethod);
        buildRule.executeTarget(targetSpecificMethod);
        // verify only that specific method was run
        Assert.assertTrue("testBar was expected to be run", wasTestRun(tracker1, JUnit4SampleTest.class.getName(),
                "testBar"));
        Assert.assertFalse("testFoo wasn't expected to be run", wasTestRun(tracker1, JUnit4SampleTest.class.getName(),
                "testFoo"));


        final String targetMultipleMethods = "test-multiple-specific-methods";
        final Path tracker2 = setupTrackerProperty(targetMultipleMethods);
        buildRule.executeTarget(targetMultipleMethods);
        Assert.assertTrue("testFooBar was expected to be run", wasTestRun(tracker2, JUnit4SampleTest.class.getName(),
                "testFooBar"));
        Assert.assertTrue("testFoo was expected to be run", wasTestRun(tracker2, JUnit4SampleTest.class.getName(),
                "testFoo"));
        Assert.assertFalse("testBar wasn't expected to be run", wasTestRun(tracker2, JUnit4SampleTest.class.getName(),
                "testBar"));
    }

    /**
     * Tests the execution of more than one {@code &lt;test&gt;} elements in the {@code &lt;junitlauncher&gt;} task
     */
    @Test
    public void testMultipleIndividualTests() throws Exception {
        final String targetName = "test-multiple-individual";
        final Path trackerFile1 = setupTrackerProperty(targetName + "-1");
        final Path trackerFile2 = setupTrackerProperty(targetName + "-2");
        buildRule.executeTarget(targetName);

        Assert.assertTrue("AlwaysFailingJUnit4Test#testWillFail was expected to be run", wasTestRun(trackerFile1,
                AlwaysFailingJUnit4Test.class.getName(), "testWillFail"));
        Assert.assertTrue("JUnit4SampleTest#testFoo was expected to be run", wasTestRun(trackerFile2,
                JUnit4SampleTest.class.getName(), "testFoo"));
    }

    /**
     * Tests execution of tests, that have been configured using the {@code &lt;testclasses&gt;} nested element
     * of the {@code &lt;junitlauncher&gt;} task
     */
    @Test
    public void testTestClasses() throws Exception {
        final String targetName = "test-batch";
        final Path trackerFile = setupTrackerProperty(targetName);
        buildRule.executeTarget(targetName);

        Assert.assertTrue("JUnit4SampleTest#testFoo was expected to succeed", verifySuccess(trackerFile,
                JUnit4SampleTest.class.getName(), "testFoo"));
        Assert.assertTrue("AlwaysFailingJUnit4Test#testWillFail was expected to fail", verifyFailed(trackerFile,
                AlwaysFailingJUnit4Test.class.getName(), "testWillFail"));
        Assert.assertTrue("JupiterSampleTest#testSucceeds was expected to succeed", verifySuccess(trackerFile,
                JupiterSampleTest.class.getName(), "testSucceeds"));
        Assert.assertTrue("JupiterSampleTest#testFails was expected to succeed", verifyFailed(trackerFile,
                JupiterSampleTest.class.getName(), "testFails"));
        Assert.assertTrue("JupiterSampleTest#testSkipped was expected to be skipped", verifySkipped(trackerFile,
                JupiterSampleTest.class.getName(), "testSkipped"));
        Assert.assertFalse("ForkedTest wasn't expected to be run", wasTestRun(trackerFile, ForkedTest.class.getName()));
    }

    /**
     * Tests the execution of a forked test
     */
    @Test
    public void testBasicFork() throws Exception {
        final String targetName = "test-basic-fork";
        final Path trackerFile = setupTrackerProperty(targetName);
        // setup a dummy and incorrect value of a sysproperty that's used in the test
        // being forked
        System.setProperty(ForkedTest.SYS_PROP_ONE, "dummy");
        buildRule.executeTarget(targetName);
        // verify that our JVM's sysprop value didn't get changed
        Assert.assertEquals("System property " + ForkedTest.SYS_PROP_ONE + " was unexpected updated",
                "dummy", System.getProperty(ForkedTest.SYS_PROP_ONE));

        Assert.assertTrue("ForkedTest#testSysProp was expected to succeed", verifySuccess(trackerFile,
                ForkedTest.class.getName(), "testSysProp"));
    }

    private Path setupTrackerProperty(final String targetName) {
        final String filename = targetName + "-tracker.txt";
        buildRule.getProject().setProperty(targetName + ".tracker", filename);
        final String outputDir = buildRule.getProject().getProperty("output.dir");
        return Paths.get(outputDir, filename);
    }
}

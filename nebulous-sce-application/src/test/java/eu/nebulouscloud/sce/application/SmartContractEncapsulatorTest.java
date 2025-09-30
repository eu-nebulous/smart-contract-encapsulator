package eu.nebulouscloud.sce.application;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class SmartContractEncapsulatorTest
        extends TestCase {
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public SmartContractEncapsulatorTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(SmartContractEncapsulatorTest.class);
    }

    /**
     * Rigourous Test :-)
     */
    public void testApp() {
        assertTrue(true);
    }
}

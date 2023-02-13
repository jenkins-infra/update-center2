package io.jenkins.update_center;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HPITest {
    @Test
    public void isValidCoreDependencyTest() {
        assertTrue(HPI.isValidCoreDependency("1.0"));
        assertTrue(HPI.isValidCoreDependency("1.654"));
        assertTrue(HPI.isValidCoreDependency("2.0"));
        assertTrue(HPI.isValidCoreDependency("2.1"));
        assertTrue(HPI.isValidCoreDependency("2.1000"));
        assertFalse(HPI.isValidCoreDependency("2.00"));
        assertFalse(HPI.isValidCoreDependency("2.01"));
        assertFalse(HPI.isValidCoreDependency("2.100-SNAPSHOT"));
        assertFalse(HPI.isValidCoreDependency("2.0-rc-1"));
        assertFalse(HPI.isValidCoreDependency("2.0-rc-1.vabcd1234"));
    }
}

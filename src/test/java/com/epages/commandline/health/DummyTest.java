package com.epages.commandline.health;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class DummyTest {

    @Test
    public void should_compile() {
        JmxHealthCheck check = new JmxHealthCheck();
        assertNotNull(check);
    }
}

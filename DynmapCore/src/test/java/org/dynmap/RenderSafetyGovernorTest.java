package org.dynmap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderSafetyGovernorTest {
    @Test void heapAloneAndSingleGcSpikeDoNotPause() {
        var safety = new RenderSafetyGovernor();
        for (int i = 0; i < 20; i++) assertFalse(safety.update(.99, .01));
        assertFalse(safety.update(.99, .5));
        assertFalse(safety.update(.5, .01));
        assertFalse(safety.update(.99, .5));
        assertFalse(safety.update(-1, -1));
        assertFalse(safety.update(Double.NaN, Double.NaN));
    }

    @Test void requiresSustainedRecoveryAndDoesNotTreatUnknownAsRecovered() {
        var safety = new RenderSafetyGovernor();
        for (int i = 0; i < 3; i++) safety.update(.99, .5);
        for (int i = 0; i < 10; i++) assertTrue(safety.update(-1, -1));
        for (int i = 0; i < 5; i++) assertTrue(safety.update(.6, .01));
        assertFalse(safety.update(.6, .01));
    }
}

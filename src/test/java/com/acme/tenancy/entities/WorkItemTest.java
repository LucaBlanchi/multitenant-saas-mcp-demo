package com.acme.tenancy.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class WorkItemTest {
    @Test
    void updatePreservesTitleDescriptionAndStatusInvariants() {
        WorkItem item = new WorkItem(mock(Tenancy.class), "Initial", null);

        assertThrows(IllegalArgumentException.class, () -> item.update(" ", null, "OPEN"));
        assertThrows(IllegalArgumentException.class, () -> item.update("x".repeat(161), null, "OPEN"));
        assertThrows(IllegalArgumentException.class, () -> item.update("Valid", "x".repeat(2001), "OPEN"));
        assertThrows(IllegalArgumentException.class, () -> item.update("Valid", null, "UNKNOWN"));

        item.update(" Updated ", "Details", "DONE");
        assertEquals("Updated", item.getTitle());
        assertEquals("Details", item.getDescription());
        assertEquals("DONE", item.getStatus());
    }
}

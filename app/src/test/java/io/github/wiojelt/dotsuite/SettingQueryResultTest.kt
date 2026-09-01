package io.github.wiojelt.dotsuite
import io.github.wiojelt.dotsuite.data.SettingQueryResult
import org.junit.Assert.*
import org.junit.Test

class SettingQueryResultTest {
    @Test fun missingAndLiteralNullStayDifferent() {
        assertTrue(SettingQueryResult.parse("No result found.").valid)
        assertNull(SettingQueryResult.parse("No result found.").value)
        assertEquals("null", SettingQueryResult.parse("Row: 0 value=null").value)
        assertEquals("NULL", SettingQueryResult.parse("Row: 0 value=NULL").value)
    }
    @Test fun valuesArePreservedExactlyIncludingSpaces() {
        assertEquals("", SettingQueryResult.parse("Row: 0 value=").value)
        assertEquals(" name ", SettingQueryResult.parse("Row: 0 value= name ").value)
        assertEquals("0.750", SettingQueryResult.parse("Row: 0 value=0.750").value)
    }
    @Test fun errorsAndAmbiguousOutputAreNotMissingValues() {
        listOf("", "Permission denied", "Error: not found", "Row: 0 value=1\nRow: 1 value=2", "Row: 0 name=x, value=1").forEach {
            assertFalse(it, SettingQueryResult.parse(it).valid)
        }
    }
}

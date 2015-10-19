package sonar.examples;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LibraryTest {

    @Test
    public void shouldBeJUnitTest() {
        assertTrue(new Library().someLibraryMethodJUnit());
    }
}

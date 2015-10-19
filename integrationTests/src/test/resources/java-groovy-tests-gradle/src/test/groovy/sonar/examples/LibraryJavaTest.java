package sonar.examples;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LibraryJavaTest {

    @Test
    public void shouldBeJUnitTest() {
        assertTrue(new LibraryJava().someLibraryMethodJUnit());
    }
}

package example;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.fail;

public class FailingTest {
	
	@Test
	public void testAuthenticator() throws Exception {
		fail("FAIL!");
	}
}

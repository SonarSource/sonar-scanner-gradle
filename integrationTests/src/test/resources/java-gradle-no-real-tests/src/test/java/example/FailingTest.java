package example;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.fail;

public class FailingTest {

	public void testAuthenticator() throws Exception {
		fail("FAIL!");
	}
}

package com.coresolution.csm;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Full-context smoke test: needs a live DB, so it is excluded from the DB-less
// CI prod gate (-PexcludeIntegration). Runs locally / where a DB is available.
@Tag("integration")
@SpringBootTest
class CsmApplicationTests {

	@Test
	void contextLoads() {
	}

}

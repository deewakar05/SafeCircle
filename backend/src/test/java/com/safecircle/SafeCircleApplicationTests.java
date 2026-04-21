package com.safecircle;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SafeCircleApplicationTests {

    @Test
    void contextLoads() {
        // Verifies Spring context starts correctly with all beans
    }
}

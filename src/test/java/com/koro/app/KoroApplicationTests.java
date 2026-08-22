package com.koro.app;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires a running MongoDB instance to load application context.")
class KoroApplicationTests {

    @Test
    void contextLoads() {
    }

}

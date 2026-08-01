package com.suno.mall.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QualityGateConfigurationTest {

    @Test
    void jacocoEnforcesCurrentSharedCodeAndFutureDomainApplicationCoverage() throws Exception {
        String pom = Files.readString(Path.of("..", "pom.xml"));

        assertTrue(pom.contains("<include>com/suno/mall/common/*</include>"));
        assertTrue(pom.contains("<include>com/suno/mall/*/domain/*</include>"));
        assertTrue(pom.contains("<include>com/suno/mall/*/application/*</include>"));
        assertTrue(pom.contains("<minimum>0.70</minimum>"));
        assertTrue(pom.contains("<minimum>0.80</minimum>"));
    }
}

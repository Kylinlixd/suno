package com.suno.mall.persistence;

import com.suno.mall.testsupport.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlContainerImageTest {

    @Test
    void usesTheReviewedMysql84ImageDigest() {
        assertThat(MySqlContainerSupport.MYSQL_IMAGE)
                .isEqualTo("mysql:8.4@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb");
        assertThat(MySqlContainerSupport.MYSQL_IMAGE_NAME.getUnversionedPart()).isEqualTo("mysql:8.4");
    }

    @Test
    void testcontainerJdbcUrlsKeepTinyintOneColumnsAsTinyint() throws ReflectiveOperationException {
        Field parametersField = JdbcDatabaseContainer.class.getDeclaredField("urlParameters");
        parametersField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, String> urlParameters =
                (Map<String, String>) parametersField.get(MySqlContainerSupport.mysql());

        assertThat(urlParameters).containsEntry("tinyInt1isBit", "false");
    }
}

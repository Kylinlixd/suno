package com.suno.mall.persistence;

import com.suno.mall.entity.PaymentReplayAutoHandleIdempotencyEntity;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReplayAutoHandleIdempotencyEntityMappingTest {

    @Test
    void responseJsonDeclaresTextToMatchTheCanonicalSchema() throws NoSuchFieldException {
        Field responseJson = PaymentReplayAutoHandleIdempotencyEntity.class.getDeclaredField("responseJson");

        Column column = responseJson.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.nullable()).isFalse();
        assertThat(column.columnDefinition()).isEqualTo("TEXT");
    }
}

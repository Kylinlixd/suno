package com.recycle.mall.interfaces.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerIntegrationTest.TestExceptionController.class)
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldHandleIllegalArgumentException() throws Exception {
        JsonNode root = performAndParse(get("/api/test/exceptions/illegal"), 400);
        assertFalse(root.get("success").asBoolean());
        assertEquals("非法业务参数", root.get("message").asText());
    }

    @Test
    void shouldHandleMethodArgumentNotValidException() throws Exception {
        JsonNode root = performAndParse(
                post("/api/test/exceptions/method-arg-not-valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"),
                400
        );
        assertFalse(root.get("success").asBoolean());
    }

    @Test
    void shouldHandleConstraintViolationException() throws Exception {
        JsonNode root = performAndParse(get("/api/test/exceptions/constraint").param("keyword", ""), 400);
        assertFalse(root.get("success").asBoolean());
    }

    @Test
    void shouldHandleMissingServletRequestParameterException() throws Exception {
        JsonNode root = performAndParse(get("/api/test/exceptions/missing-param"), 400);
        assertFalse(root.get("success").asBoolean());
        assertEquals("缺少必填参数: requiredParam", root.get("message").asText());
    }

    @Test
    void shouldHandleMethodArgumentTypeMismatchException() throws Exception {
        JsonNode root = performAndParse(get("/api/test/exceptions/type-mismatch").param("page", "abc"), 400);
        assertFalse(root.get("success").asBoolean());
        assertEquals("参数类型错误: page", root.get("message").asText());
    }

    @Test
    void shouldHandleHttpMessageNotReadableException() throws Exception {
        JsonNode root = performAndParse(
                post("/api/test/exceptions/not-readable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"),
                400
        );
        assertFalse(root.get("success").asBoolean());
        assertEquals("请求体格式错误，请检查 JSON 内容", root.get("message").asText());
    }

    @Test
    void shouldHandleOptimisticLockException() throws Exception {
        JsonNode root = performAndParse(get("/api/test/exceptions/optimistic-lock"), 409);
        assertFalse(root.get("success").asBoolean());
        assertEquals("操作过于频繁，请重试", root.get("message").asText());
    }

    @Test
    void shouldHandleDataIntegrityViolationException() throws Exception {
        JsonNode root = performAndParse(get("/api/test/exceptions/data-integrity"), 409);
        assertFalse(root.get("success").asBoolean());
        assertEquals("数据冲突，请检查请求后重试", root.get("message").asText());
    }

    @Test
    void shouldHandleUnknownException() throws Exception {
        JsonNode root = performAndParse(get("/api/test/exceptions/runtime"), 500);
        assertFalse(root.get("success").asBoolean());
        assertEquals("系统异常，请稍后重试", root.get("message").asText());
    }

    private JsonNode performAndParse(org.springframework.test.web.servlet.RequestBuilder request, int expectedStatus) throws Exception {
        String content = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }

    @Validated
    @RestController
    @RequestMapping("/api/test/exceptions")
    static class TestExceptionController {

        @GetMapping("/illegal")
        public Map<String, Object> illegal() {
            throw new IllegalArgumentException("非法业务参数");
        }

        @PostMapping("/method-arg-not-valid")
        public Map<String, Object> methodArgNotValid(@Valid @RequestBody NamePayload payload) {
            return Map.of("name", payload.name());
        }

        @GetMapping("/constraint")
        public Map<String, Object> constraint(@RequestParam @NotBlank String keyword) {
            return Map.of("keyword", keyword);
        }

        @GetMapping("/missing-param")
        public Map<String, Object> missing(@RequestParam String requiredParam) {
            return Map.of("requiredParam", requiredParam);
        }

        @GetMapping("/type-mismatch")
        public Map<String, Object> typeMismatch(@RequestParam Integer page) {
            return Map.of("page", page);
        }

        @PostMapping("/not-readable")
        public Map<String, Object> notReadable(@RequestBody Map<String, Object> payload) {
            return payload;
        }

        @GetMapping("/optimistic-lock")
        public Map<String, Object> optimisticLock() {
            throw new ObjectOptimisticLockingFailureException("test", 1L);
        }

        @GetMapping("/data-integrity")
        public Map<String, Object> dataIntegrity() {
            throw new DataIntegrityViolationException("duplicate");
        }

        @GetMapping("/runtime")
        public Map<String, Object> runtime() {
            throw new RuntimeException("boom");
        }
    }

    record NamePayload(@NotBlank String name) {
    }
}

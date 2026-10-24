package com.suno.mall.payment;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RSA2 签名/验签与参数规范化单测（测试密钥对，仅测试用途）。
 */
class AlipaySignatureServiceTest {

    private static final String PRIVATE_KEY = "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDKSuhIrzjs7Mm0bJDva6VC2qr3j02sVK3XhgZWIGA7PC86TJyQUaG48NX/+FIkeX8s3UnT9BLaCgeAyjHc0kTsLZ9/tb5F5kw94nFm/xkZMXM/AmKl2xczJZPxKpWwOQnj5mzj+hZAYOSJNW74uVRzKoHP7N9BoEb60K3NYgoV9nA5bXOkDkBFkF0FsNnF1yo20C4XsP9qARiU3DpEclMf0emKHQYilm0tVxemkH7RC8LAQAqaPO0znsooV/ioMUNaBFE7pPD8CTaYs59Z+ucYuxX49gJw9rTaQlt7pSLQ/9oth//OpZ24SNtLP1QeHyJuBpbMdjf9HDNSGLsGf3JlAgMBAAECggEASC3iI80hqqfycIYsUME//lK3ng1r0YmFXdlceW6j537Z+xtK4dO+uJZ2RRb9eDZBwmhS52W3NEVP2AxNLlV4ciz1NERM1/QMUChz/iQXeH3EeT8Io9VAuec2ml5veqlfdgnJ4K5jMiVgZfTC28yE7XLFTsNeBbSCnwvGd/n7T0o0MkqFS3ELrTNBoqxaY45y6UXFP7SzwnJxCwLSNxDD9ZBK6cm9JlEGeTxovQpICPdvoHy5PcgTRhLAT3i2EpSloo9VcSv/J+fxVi+NCx2Mk4gmA1rRRFSgvXB98I7FCmNfSmwo8iXbEUh5H1FsOd7sHfuQKCehhOLuAIAlyTy4IQKBgQD6DvofYaImaRJgYpBFXuD/BGcVcEUppkMHXio2FYbRlctl7oz4mxGs6owmoD9UXBippxErSGjJB3PZ/JNpC0stKx/8QwDRWOg8oKRAYxUscE052vRI5aHl5C0XQq5Uw19ERXtCu9Q2b1RyBtQHGURiclsVoq+eIKBe6Fg0xaBCjwKBgQDPGWLeT1CcM49AzE/+SfaJLLXVa1Gyp2LE9VFE+QZcP3J/HkVFJJJceynbo5R11Cf9mURPkkYpGOvluXaNj/6bZyb7MedLeQMY/kTCmkutVXAeumryZNGOZCjClzzPAQLVVuPNHpEmOLaj3IUZOO5Nde/Wmg6pfSWH1FvTInQlywKBgQDQQHDmebVFL/Rx+SQV4fKX79656ApxqVnhZPbanArvuUTv2z/kO6D41U1rTcHwS9LuV4jg9yjtYxuEy+kobpNpRqUYveSMdPLUqySfyuTzYYOvTz5rB3NRtjonSQ1O4ncQsJz68trFiupVHIKwdsaRBpP87MQ9eQaWhAIR0RSq2QKBgQCNk2s1Ur9mqRCrWYDt854oFHqR1XXaTfeFcbcHEb45Xd5l7EmqhN/RfRkWX3NkQY69HQPH6dUwzyqktu7Gw8NodVH+sxTNUYK+mURkTJqjeh7VwtbNsgJX9zyuGjmlwakwMuA0H0tW97pTtrL+lR7DsXYuHM8dKsJ3YsMLSShhlQKBgQC6z8AzzPfymkQGVDCNq1i0avNRlMUEdM5mkqRGEkSbo0LzkU1pqNEaeTCU9jXw2LO7B3VTSYxaa8F1XIJB//q+F/DhA+P2xUUkTs915edWVfy5PXrresjfnf0zyoSOwKEaAlUxtDvdtO42uUumdzOn+eSAc3Fzz7oM8nLFVXDIKw";
    private static final String PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAykroSK847OzJtGyQ72ulQtqq949NrFSt14YGViBgOzwvOkyckFGhuPDV//hSJHl/LN1J0/QS2goHgMox3NJE7C2ff7W+ReZMPeJxZv8ZGTFzPwJipdsXMyWT8SqVsDkJ4+Zs4/oWQGDkiTVu+LlUcyqBz+zfQaBG+tCtzWIKFfZwOW1zpA5ARZBdBbDZxdcqNtAuF7D/agEYlNw6RHJTH9Hpih0GIpZtLVcXppB+0QvCwEAKmjztM57KKFf4qDFDWgRRO6Tw/Ak2mLOfWfrnGLsV+PYCcPa02kJbe6Ui0P/aLYf/zqWduEjbSz9UHh8ibgaWzHY3/RwzUhi7Bn9yZQIDAQAB";

    private final AlipaySignatureService service = new AlipaySignatureService();

    @Test
    void canonicalContentSortsKeysAndSkipsSignFieldsAndEmptyValues() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("charset", "utf-8");
        params.put("sign", "should-be-skipped");
        params.put("sign_type", "RSA2");
        params.put("app_id", "2021000000000001");
        params.put("empty", "");
        assertEquals("app_id=2021000000000001&charset=utf-8", service.canonicalContent(params));
    }

    @Test
    void signThenVerifyRoundTripAcceptsAndTamperingFails() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", "2021000000000001");
        params.put("out_trade_no", "B2C-TEST0001");
        params.put("total_amount", "1999.00");
        params.put("trade_status", "TRADE_SUCCESS");
        String sign = service.sign(params, PRIVATE_KEY);
        assertTrue(service.verify(params, sign, PUBLIC_KEY));

        params.put("total_amount", "0.01");
        assertFalse(service.verify(params, sign, PUBLIC_KEY), "金额被篡改后必须验签失败");
        assertFalse(service.verify(params, "not-a-sign", PUBLIC_KEY));
    }

    @Test
    void acceptsPemWrappedKeys() {
        String pemPrivate = "-----BEGIN PRIVATE KEY-----\n" + PRIVATE_KEY + "\n-----END PRIVATE KEY-----";
        Map<String, String> params = Map.of("k", "v");
        String sign = service.sign(params, pemPrivate);
        assertTrue(service.verify(params, sign, PUBLIC_KEY));
    }
}

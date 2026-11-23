package com.afs.controller.filter;

import com.afs.dao.UserMapper;
import com.afs.dao.model.User;
import com.afs.utils.Coder;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsoleAuthorizationTest {
    private final AuthInterceptor interceptor = new AuthInterceptor();
    private final UserMapper users = mock(UserMapper.class);
    private final String path = "/cms/ad/resource/rule/del";

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(interceptor, "userMapper", users);
    }

    @Test
    void consoleRequiresAuthenticationEvenForLegacyNoAuthActions() throws Exception {
        var request = new MockHttpServletRequest("POST", path);
        request.addHeader("admin-console", "1");
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, handler())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void localAdminStillNeedsAValidSignatureForProtectedActions() throws Exception {
        ReflectionTestUtils.setField(interceptor, "localWorkspace", true);
        var unsigned = new MockHttpServletRequest("POST", path);
        unsigned.addHeader("admin-name", "local-admin");
        var signed = signed(User.RootUser, "local-admin", true);
        var denied = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(unsigned, denied, handler())).isFalse();
        assertThat(denied.getStatus()).isEqualTo(401);
        var approved = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(signed, approved, handler())).isTrue();
        interceptor.afterCompletion(signed, approved, handler(), null);
    }

    @Test
    void rootIsRequiredForRuleChangesEvenWithoutConsoleHeader() throws Exception {
        ReflectionTestUtils.setField(interceptor, "localWorkspace", true);

        var unsigned = new MockHttpServletRequest("POST", path);
        unsigned.addHeader("admin-name", "sample");
        var denied = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(unsigned, denied, handler())).isFalse();
        assertThat(denied.getStatus()).isEqualTo(401);

        var nonRoot = signed(User.CommonUser, "sample", false);
        var forbidden = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(nonRoot, forbidden, handler())).isFalse();
        assertThat(forbidden.getStatus()).isEqualTo(403);

        var root = signed(User.RootUser, "sample", false);
        var approved = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(root, approved, handler())).isTrue();
        interceptor.afterCompletion(root, approved, handler(), null);
    }

    private MockHttpServletRequest signed(int role, String username, boolean console) {
        var user = new User();
        user.setName(username);
        user.setPassword("test-signing-secret");
        user.setRoot(role);
        user.setStatus(User.StatusValid);
        when(users.get(user.getName())).thenReturn(user);
        var request = new MockHttpServletRequest("POST", path);
        var headers = new TreeMap<String, String>();
        headers.put("admin-name", user.getName());
        headers.put("admin-nonce", UUID.randomUUID().toString());
        headers.put("admin-timestamp", Long.toString(System.currentTimeMillis()));
        if (console) headers.put("admin-console", "1");
        var signature = new StringBuilder(path).append("&POST&");
        headers.forEach((name, value) -> {
            request.addHeader(name, value);
            signature.append(name).append('=').append(value).append('&');
        });
        signature.append(user.getPassword()).append('&');
        request.addHeader("admin-signature", Coder.from(signature.toString()).md5().asHexLower());
        return request;
    }

    private HandlerMethod handler() throws NoSuchMethodException {
        return new HandlerMethod(this, getClass().getMethod("protectedHandler"));
    }

    public void protectedHandler() {}
}

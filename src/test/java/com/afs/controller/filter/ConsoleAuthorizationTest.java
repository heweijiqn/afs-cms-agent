package com.afs.controller.filter;

import com.afs.dao.UserMapper;
import com.afs.dao.model.User;
import com.afs.utils.Coder;
import java.util.Map;
import java.util.TreeMap;
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
        var signed = signed(User.RootUser, "local-admin");
        var denied = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(unsigned, denied, handler())).isFalse();
        assertThat(denied.getStatus()).isEqualTo(401);
        var approved = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(signed, approved, handler())).isTrue();
        interceptor.afterCompletion(signed, approved, handler(), null);
    }

    @Test
    void consoleEnforcesRootForRuleChangesAndPreservesLegacyClientCompatibility() throws Exception {
        var unsignedLegacy = new MockHttpServletRequest("POST", path);
        assertThat(interceptor.preHandle(unsignedLegacy, new MockHttpServletResponse(), handler())).isTrue();

        var denied = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(signed(User.CommonUser), denied, handler())).isFalse();
        assertThat(denied.getStatus()).isEqualTo(403);

        var approved = signed(User.RootUser);
        var response = new MockHttpServletResponse();
        var handler = handler();
        assertThat(interceptor.preHandle(approved, response, handler)).isTrue();
        interceptor.afterCompletion(approved, response, handler, null);
    }

    private MockHttpServletRequest signed(int role) {
        return signed(role, "sample");
    }

    private MockHttpServletRequest signed(int role, String username) {
        var user = new User();
        user.setName(username);
        user.setPassword("test-signing-secret");
        user.setRoot(role);
        user.setStatus(User.StatusValid);
        when(users.get(user.getName())).thenReturn(user);
        var request = new MockHttpServletRequest("POST", path);
        var headers = new TreeMap<>(Map.of(
                "admin-console", "1", "admin-name", user.getName(), "admin-nonce", "test-nonce",
                "admin-timestamp", Long.toString(System.currentTimeMillis())));
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
        return new HandlerMethod(this, getClass().getMethod("legacyHandler"));
    }

    @NoAuth
    public void legacyHandler() {}
}


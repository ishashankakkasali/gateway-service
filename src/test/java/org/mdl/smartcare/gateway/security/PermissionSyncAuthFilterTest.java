package org.mdl.smartcare.gateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mdl.smartcare.gateway.exception.SmartcareBusinessException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PermissionSyncAuthFilterTest {

  private static final String SYNC_PATH = "/api/permissions/sync-to-keycloak";
  private static final String REQUIRED_ROLE = "permission-sync";

  @Mock private JwtUtil jwtUtil;
  @Mock private WebFilterChain chain;

  private PermissionSyncAuthFilter filter;

  @BeforeEach
  void setUp() {
    filter = new PermissionSyncAuthFilter(jwtUtil, REQUIRED_ROLE);
  }

  private MockServerWebExchange post(String path, String authHeader) {
    MockServerHttpRequest.BodyBuilder request = MockServerHttpRequest.post(path);
    if (authHeader != null) {
      request.header(HttpHeaders.AUTHORIZATION, authHeader);
    }
    return MockServerWebExchange.from(request.build());
  }

  @Test
  void syncCallWithoutToken_isRejectedWith401() {
    when(jwtUtil.extractTokenFromHeader(null))
        .thenThrow(new SmartcareBusinessException("Invalid authorization header"));
    MockServerWebExchange exchange = post(SYNC_PATH, null);

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    verify(chain, never()).filter(any());
  }

  @Test
  void syncCallWithInvalidToken_isRejectedWith401() {
    when(jwtUtil.extractTokenFromHeader("Bearer bad")).thenReturn("bad");
    when(jwtUtil.validateToken("bad")).thenReturn(false);
    MockServerWebExchange exchange = post(SYNC_PATH, "Bearer bad");

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    verify(chain, never()).filter(any());
  }

  @Test
  void syncCallWithValidTokenButWithoutTheRole_isRejectedWith403() {
    when(jwtUtil.extractTokenFromHeader("Bearer good")).thenReturn("good");
    when(jwtUtil.validateToken("good")).thenReturn(true);
    when(jwtUtil.extractPermissions("good")).thenReturn(List.of("insights-viewer"));
    MockServerWebExchange exchange = post(SYNC_PATH, "Bearer good");

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    verify(chain, never()).filter(any());
  }

  @Test
  void syncCallWithValidTokenAndTheRole_goesOn() {
    when(jwtUtil.extractTokenFromHeader("Bearer good")).thenReturn("good");
    when(jwtUtil.validateToken("good")).thenReturn(true);
    when(jwtUtil.extractPermissions("good")).thenReturn(List.of("insights-viewer", REQUIRED_ROLE));
    MockServerWebExchange exchange = post(SYNC_PATH, "Bearer good");
    when(chain.filter(exchange)).thenReturn(Mono.empty());

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    verify(chain).filter(exchange);
    assertNull(exchange.getResponse().getStatusCode());
  }

  @Test
  void testConnectionCallWithoutToken_isRejectedToo() {
    when(jwtUtil.extractTokenFromHeader(null))
        .thenThrow(new SmartcareBusinessException("Invalid authorization header"));
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/permissions/keycloak/test-connection").build());

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
  }

  @Test
  void otherPaths_goOnWithoutATokenCheck() {
    MockServerWebExchange exchange = post("/v1/events", null);
    when(chain.filter(exchange)).thenReturn(Mono.empty());

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    verify(chain).filter(exchange);
    verifyNoInteractions(jwtUtil);
  }

  @Test
  void aPathThatOnlyStartsWithTheSameLetters_isNotAPermissionSyncPath() {
    MockServerWebExchange exchange = post("/api/permissions-report", null);
    when(chain.filter(exchange)).thenReturn(Mono.empty());

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    verify(chain).filter(exchange);
    verifyNoInteractions(jwtUtil);
  }
}

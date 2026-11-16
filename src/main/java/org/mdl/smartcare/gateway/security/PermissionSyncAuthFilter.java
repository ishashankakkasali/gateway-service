package org.mdl.smartcare.gateway.security;

import java.util.List;
import org.mdl.smartcare.gateway.constants.GatewayConstants;
import org.mdl.smartcare.gateway.exception.SmartcareBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Protects the gateway's own admin endpoints under {@code /api/permissions}.
 *
 * <p>These endpoints are not routes. So the {@link JwtValidationFilter} of the routes never runs
 * for them. Without this filter anyone who can reach the gateway could call them.
 *
 * <p>A call passes only when:
 *
 * <ol>
 *   <li>it has a Bearer token (no token: 401);
 *   <li>the token is valid (not valid: 401);
 *   <li>the token has the role in {@code permission-sync.required-role} (no role: 403).
 * </ol>
 *
 * <p>Every other path goes on unchanged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PermissionSyncAuthFilter implements WebFilter {

  private static final Logger logger = LoggerFactory.getLogger(PermissionSyncAuthFilter.class);

  private final JwtUtil jwtUtil;
  private final String requiredRole;

  public PermissionSyncAuthFilter(
      JwtUtil jwtUtil, @Value("${permission-sync.required-role:permission-sync}") String requiredRole) {
    this.jwtUtil = jwtUtil;
    this.requiredRole = requiredRole;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getPath().pathWithinApplication().value();
    if (!isPermissionSyncPath(path)) {
      return chain.filter(exchange);
    }

    String token = bearerToken(exchange);
    if (token == null || !jwtUtil.validateToken(token)) {
      logger.warn("Permission sync call rejected: no valid Bearer token. path={}", path);
      return reject(exchange.getResponse(), HttpStatus.UNAUTHORIZED, GatewayConstants.Messages.UNAUTHORIZED);
    }

    List<String> roles = jwtUtil.extractPermissions(token);
    if (roles == null || !roles.contains(requiredRole)) {
      logger.warn(
          "Permission sync call rejected: token has no role '{}'. path={}", requiredRole, path);
      return reject(exchange.getResponse(), HttpStatus.FORBIDDEN, GatewayConstants.Messages.FORBIDDEN);
    }

    return chain.filter(exchange);
  }

  private boolean isPermissionSyncPath(String path) {
    return path.equals(GatewayConstants.ApiPaths.PERMISSIONS_BASE)
        || path.startsWith(GatewayConstants.ApiPaths.PERMISSIONS_BASE + "/");
  }

  /** Give the Bearer token of the call, or {@code null} when there is none. */
  private String bearerToken(ServerWebExchange exchange) {
    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    try {
      return jwtUtil.extractTokenFromHeader(authHeader);
    } catch (SmartcareBusinessException e) {
      return null;
    }
  }

  private Mono<Void> reject(ServerHttpResponse response, HttpStatus status, String error) {
    response.setStatusCode(status);
    response.getHeaders().add(GatewayConstants.Headers.CONTENT_TYPE, "application/json");
    String body = String.format("{\"error\":\"%s\",\"status\":%d}", error, status.value());
    return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
  }
}

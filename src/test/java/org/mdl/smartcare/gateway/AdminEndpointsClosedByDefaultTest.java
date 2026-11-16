package org.mdl.smartcare.gateway;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mdl.smartcare.gateway.controller.PermissionSyncController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * With the default configuration, the gateway's own admin endpoints are closed.
 *
 * <ul>
 *   <li>The permission sync does not exist, and a call without a token gets 401.
 *   <li>The {@code gateway} actuator endpoint, which can change routes, is not exposed.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AdminEndpointsClosedByDefaultTest {

  @Autowired private WebTestClient webTestClient;
  @Autowired private ApplicationContext applicationContext;
  @Autowired private PathMappedEndpoints pathMappedEndpoints;

  @Test
  void permissionSyncController_doesNotExistByDefault() {
    assertTrue(applicationContext.getBeansOfType(PermissionSyncController.class).isEmpty());
  }

  @Test
  void syncToKeycloak_withoutToken_returns401() {
    webTestClient
        .post()
        .uri("/api/permissions/sync-to-keycloak")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void keycloakTestConnection_withoutToken_returns401() {
    webTestClient
        .get()
        .uri("/api/permissions/keycloak/test-connection")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void gatewayActuatorEndpoint_isNotExposed() {
    assertNull(pathMappedEndpoints.getPath(EndpointId.of("gateway")));
    assertNotNull(pathMappedEndpoints.getPath(EndpointId.of("health")));

    // An unknown path is answered with an error by the gateway's exception handler, never with 2xx.
    webTestClient
        .get()
        .uri("/actuator/gateway/routes")
        .exchange()
        .expectStatus()
        .value(status -> assertTrue(status >= 400));
  }

  @Test
  void healthActuatorEndpoint_isStillReachable() {
    webTestClient
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .value(status -> assertTrue(status == 200 || status == 503));
  }
}

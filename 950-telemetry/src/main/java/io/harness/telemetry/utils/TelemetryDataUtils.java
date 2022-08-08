/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.telemetry.utils;

import static io.harness.TelemetryConstants.SYSTEM_USER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.container.ContainerRequestContext;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.internal.routing.UriRoutingContext;
import org.glassfish.jersey.uri.UriTemplate;

@UtilityClass
@Slf4j
public class TelemetryDataUtils {
  public static String readIdentityFromPrincipal() {
    Principal principal = SecurityContextBuilder.getPrincipal();
    String identity = null;
    if (principal != null) {
      switch (principal.getType()) {
        case USER:
          UserPrincipal userPrincipal = (UserPrincipal) principal;
          identity = userPrincipal.getEmail();
          break;
        case API_KEY:
        case SERVICE:
          identity = principal.getName();
          break;
        default:
          log.warn("Unknown principal type from SecurityContextBuilder when reading identity");
      }
    }
    if (isEmpty(identity)) {
      log.debug("Failed to read identity from principal, use system user instead");
      // TODO add "-{accountId}" after "system" when accountId is provided in principal
      identity = SYSTEM_USER;
    }
    return identity;
  }

  public static String readAccountIdFromPrincipal() {
    Principal principal = SecurityContextBuilder.getPrincipal();
    String accountId = "unknown accountId";
    if (principal != null) {
      switch (principal.getType()) {
        case USER:
          UserPrincipal userPrincipal = (UserPrincipal) principal;
          accountId = userPrincipal.getAccountId();
          break;
        case API_KEY:
        case SERVICE:
          // TODO: accountId should be provided in principal
          break;
        default:
          log.warn("Unknown principal type from SecurityContextBuilder when reading accountId");
      }
    }
    return accountId;
  }

  public static Map<String, String> filterNonNullProperties(Map<String, Object> properties) {
    Map<String, String> nonNullProperties = new HashMap<>();
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      if (entry.getValue() != null) {
        nonNullProperties.put(entry.getKey(), entry.getValue().toString());
      }
    }
    return nonNullProperties;
  }

  public static String getApiPattern(ContainerRequestContext containerRequestContext) {
    List<UriTemplate> templates = ((UriRoutingContext) containerRequestContext.getUriInfo()).getMatchedTemplates();
    StringBuilder pattern = new StringBuilder("");
    for (int i = 0; i < templates.size(); i++) {
      pattern.append(templates.get(templates.size() - 1 - i).getTemplate());
    }
    return pattern.toString();
  }
}

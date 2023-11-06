/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.plugin.events;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_OAUTH_CONFIG;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.idp.common.OAuthUtils;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(IDP)
@Getter
@NoArgsConstructor
public class OAuthConfigUpdateEvent implements Event {
  public static final String OAUTH_CONFIG_UPDATED = "OAuthConfigUpdated";

  private List<BackstageEnvVariable> newBackstageEnvVariables;
  private List<BackstageEnvVariable> oldBackstageEnvVariables;
  private String accountIdentifier;
  private String authId;

  public OAuthConfigUpdateEvent(String accountIdentifier, String authId, List<BackstageEnvVariable> newEnvVariables,
      List<BackstageEnvVariable> oldEnvVariables) {
    this.newBackstageEnvVariables = newEnvVariables;
    this.oldBackstageEnvVariables = oldEnvVariables;
    this.accountIdentifier = accountIdentifier;
    this.authId = authId;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, OAuthUtils.getAuthNameForId(authId));
    return Resource.builder()
        .identifier(authId + "_" + accountIdentifier)
        .type(IDP_OAUTH_CONFIG)
        .labels(labels)
        .build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return OAUTH_CONFIG_UPDATED;
  }
}
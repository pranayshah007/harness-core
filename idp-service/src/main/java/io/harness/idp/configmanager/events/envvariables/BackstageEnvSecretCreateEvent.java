/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.events.envvariables;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_CONFIG_ENV_VARIABLES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(IDP)
@Getter
@NoArgsConstructor
public class BackstageEnvSecretCreateEvent implements Event {
  public static final String ENV_VARIABLE_CREATED = "EnvVariableCreated";

  private BackstageEnvSecretVariable newBackstageEnvSecretVariable;
  private String accountIdentifier;

  public BackstageEnvSecretCreateEvent(
      String accountIdentifier, BackstageEnvSecretVariable newBackstageEnvSecretVariable) {
    this.newBackstageEnvSecretVariable = newBackstageEnvSecretVariable;
    this.accountIdentifier = accountIdentifier;
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
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, "IDP - " + newBackstageEnvSecretVariable.getEnvName());
    return Resource.builder()
        .identifier(newBackstageEnvSecretVariable.getEnvName() + "_" + accountIdentifier)
        .type(IDP_CONFIG_ENV_VARIABLES)
        .labels(labels)
        .build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return ENV_VARIABLE_CREATED;
  }
}

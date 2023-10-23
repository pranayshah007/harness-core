/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.azureartifacts;

import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;

import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("io.harness.ConnectorDisconnectHandler.entities.embedded.azureartifacts.AzureArtifactsHttpAuthentication")
public class AzureArtifactsAuthentication {
  /**
   * Authentication Type - Personal Access Token
   */
  @NotEmpty AzureArtifactsAuthenticationType type;

  /**
   * Auth containing tokenRef
   */
  @NotEmpty AzureArtifactsTokenCredentials auth;
}

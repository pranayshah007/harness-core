/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.azureartifacts.beans;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import java.net.URI;
import javax.ws.rs.core.UriBuilder;
import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Value
@Builder
@OwnedBy(HarnessTeam.CDC)
public class AzureArtifactsInternalConfig {
  /**
   * Registry Url
   */
  String registryUrl;

  /**
   * PackageId
   */
  String packageId;

  /**
   * Project
   */
  String project;

  /**
   * feed
   */
  String feed;

  /**
   * Username
   */
  String username;

  /**
   * Password
   */
  String password;

  /**
   * Token
   */
  String token;

  /**
   * Authentication Mechanism
   */
  String authMechanism;

  boolean isCertValidationRequired;

  private boolean useConnectorUrlForJobExecution;

  public boolean hasCredentials() {
    return isNotEmpty(username);
  }

  public String getAzureArtifactsRegistryUrl() {
    URI uri = UriBuilder.fromUri(registryUrl).build();

    return UriBuilder.fromUri(registryUrl).path(uri.getPath().endsWith("/") ? "" : "/").build().toString();
  }
}

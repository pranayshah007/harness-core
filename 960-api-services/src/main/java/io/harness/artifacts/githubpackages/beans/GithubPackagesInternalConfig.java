/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.githubpackages.beans;
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
public class GithubPackagesInternalConfig {
  String githubPackagesUrl;
  String username;
  String password;
  String token;
  String authMechanism;
  boolean isCertValidationRequired;
  private boolean useConnectorUrlForJobExecution;

  public boolean hasCredentials() {
    return isNotEmpty(username);
  }

  public String getGithubPackagesRegistryUrl() {
    URI uri = UriBuilder.fromUri(githubPackagesUrl).build();

    return UriBuilder.fromUri(githubPackagesUrl).path(uri.getPath().endsWith("/") ? "" : "/").build().toString();
  }
}

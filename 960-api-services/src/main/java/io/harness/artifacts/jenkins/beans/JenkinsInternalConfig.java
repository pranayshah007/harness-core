/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.jenkins.beans;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import java.net.URI;
import java.util.Map;
import javax.ws.rs.core.UriBuilder;
import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Value
@Builder
@OwnedBy(HarnessTeam.CDC)
public class JenkinsInternalConfig {
  String jenkinsUrl;
  String username;
  char[] password;
  char[] token;
  String authMechanism;
  Map<String, String> jobParameter;
  boolean isCertValidationRequired;
  private boolean useConnectorUrlForJobExecution;

  public boolean hasCredentials() {
    return isNotEmpty(username);
  }

  public String getJenkinsRegistryUrl() {
    URI uri = UriBuilder.fromUri(jenkinsUrl).build();
    return UriBuilder.fromUri(jenkinsUrl).path(uri.getPath().endsWith("/") ? "" : "/").build().toString();
  }
}

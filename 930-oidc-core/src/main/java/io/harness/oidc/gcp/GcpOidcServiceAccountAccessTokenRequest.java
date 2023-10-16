/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.gcp;

import static io.harness.oidc.gcp.GcpOidcIdTokenConstants.DELEGATES;
import static io.harness.oidc.gcp.GcpOidcIdTokenConstants.LIFETIME;
import static io.harness.oidc.gcp.GcpOidcIdTokenConstants.SCOPE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;

@OwnedBy(HarnessTeam.PL)
@Builder
public class GcpOidcServiceAccountAccessTokenRequest {
  @JsonProperty(DELEGATES) private List<String> delegates;
  @JsonProperty(SCOPE) private List<String> scope;
  @JsonProperty(LIFETIME) private String lifetime;
}

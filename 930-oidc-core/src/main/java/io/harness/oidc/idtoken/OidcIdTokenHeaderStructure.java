/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.idtoken;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
public class OidcIdTokenHeaderStructure {
  @JsonProperty(OidcIdTokenConstants.TYPE) private String typ;

  @JsonProperty(OidcIdTokenConstants.ALGORITHM) private String alg;

  @JsonProperty(OidcIdTokenConstants.KEY_IDENTIFIER) private String kid;
}

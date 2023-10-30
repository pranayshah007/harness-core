/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.dto;

import io.harness.oidc.config.OidcConfigConstants;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder

public class JwksPublicKeyDTO {
  @JsonProperty(OidcConfigConstants.ALGORITHM) String algorithm;
  @JsonProperty(OidcConfigConstants.EXPONENT) String exponent;
  @JsonProperty(OidcConfigConstants.KID) String kid;
  @JsonProperty(OidcConfigConstants.KEY_TYPE) String keyType;
  @JsonProperty(OidcConfigConstants.MODULUS) String modulus;
  @JsonProperty(OidcConfigConstants.USE) String use;
}

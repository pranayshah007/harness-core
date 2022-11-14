/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@OwnedBy(PL)
@Schema(name = "JwtVerificationApiKey",
    description = "This represents JWT Verification API Key details defined in Harness service account.")
@NoArgsConstructor
public class JwtVerificationApiKeyDTO extends ApiKeyDTO {
  @Schema(description = "Key field to match in JWT token") private String keyField;

  @Schema(description = "Value of key to match in JWT token") private String valueField;

  @Schema(description = "Certificate public URL") private String certificateUrl;
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import java.util.Map;
import javax.validation.constraints.Size;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.hibernate.validator.constraints.NotBlank;

@Data
@Builder
@OwnedBy(PL)
@Schema(name = "JwtVerificationApiKey", description = "This represents JWT Verification API Key details defined in Harness service account.")
public class JWTVerificationApiKeyDTO {
  @ApiModelProperty(required = true)
  @EntityIdentifier
  @NotBlank
  @Schema(description = "Identifier of the JWT Verification API Key")
  private String identifier;
  @FormDataParam("name") @ApiModelProperty(required = true) @NotBlank @Schema(description = "Name of the JWT Verification API Key") private String name;
  @FormDataParam("data") @Size(max = 1024) @Schema(description = "Description of the JWT Verification API Key") String description;
  @FormDataParam("tags") @Size(max = 128) @Schema(description = "Tags for the JWT Verification API Key")
  Map<String, String> tags;
  @ApiModelProperty(required = true)
  @Schema(description = "Parent Entity Identifier of the JWT Verification API Key")
  @NotBlank
  private String parentIdentifier;

  @FormDataParam("keyToMatch")
  @ApiModelProperty(required = true)
  @Schema(description = "Key to match in JWT token")
  @NotBlank
  private String keyToMatch;

  @FormDataParam("valueToMatch")
  @ApiModelProperty(required = true)
  @Schema(description = "Value of key to match in JWT token")
  @NotBlank
  private String valueToMatch;

  @ApiModelProperty(required = true)
  @NotBlank
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE)
  private String accountIdentifier;
  @EntityIdentifier(allowBlank = true)
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
  private String projectIdentifier;
  @EntityIdentifier(allowBlank = true)
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE)
  private String orgIdentifier;

  private ApiKeyType apiKeyType = ApiKeyType.SERVICE_ACCOUNT_JWT_VERIFICATION;
  @Schema(description = "Certificate file")
  private byte[] certificate;
  @Schema(description = "Certificate public URL")
  private String certificateUrl;
}

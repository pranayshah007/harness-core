/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.ng.core.common.beans.ApiKeyType;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.NotBlank;

@Data
@SuperBuilder
@OwnedBy(PL)
@Schema(name = "ApiKey", description = "This has API Key details defined in Harness.")
@NoArgsConstructor
public class ApiKeyDTO {
  @ApiModelProperty(required = true)
  @EntityIdentifier
  @NotBlank
  @Schema(description = "Identifier of the API Key")
  private String identifier;
  @ApiModelProperty(required = true) @NotBlank @Schema(description = "Name of the API Key") private String name;
  @Size(max = 1024) @Schema(description = "Description of the API Key") String description;
  @Size(max = 128) @Schema(description = "Tags for the API Key") Map<String, String> tags;
  @ApiModelProperty(required = true) @Schema(description = "Type of the API Key") private ApiKeyType apiKeyType;
  @ApiModelProperty(required = true)
  @Schema(description = "Parent Entity Identifier of the API Key")
  @NotBlank
  private String parentIdentifier;
  @Schema(description = "Default expiration time of the Token within API Key.") private Long defaultTimeToExpireToken;

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
}

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
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@Builder
@OwnedBy(PL)
@Schema(name = "Token", description = "This has the API Key Token details defined in Harness.")
public class TokenDTO {
  @ApiModelProperty(required = true)
  @EntityIdentifier
  @Schema(description = "Identifier of the Token")
  private String identifier;
  @ApiModelProperty(required = true) @NotEmpty @Schema(description = "Name of the Token") private String name;
  @Schema(description = "This is the time from which the Token is valid. The time is in milliseconds.")
  private Long validFrom;
  @Schema(description = "This is the time till which the Token is valid. The time is in milliseconds.")
  private Long validTo;
  @Schema(description = "Scheduled expiry time in milliseconds.") private Long scheduledExpireTime;
  @Schema(description = "Boolean value to indicate if Token is valid or not.") private boolean valid;

  @ApiModelProperty(required = true)
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE)
  private String accountIdentifier;
  @EntityIdentifier(allowBlank = true)
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
  private String projectIdentifier;
  @EntityIdentifier(allowBlank = true)
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE)
  private String orgIdentifier;
  @ApiModelProperty(required = true)
  @EntityIdentifier
  @Schema(description = "This is the API Key Id within which the Token is created.")
  private String apiKeyIdentifier;
  @ApiModelProperty(required = true)
  @Schema(description = "This is the ID of the Parent entity from which the Token inherits its role bindings.")
  private String parentIdentifier;
  @ApiModelProperty(required = true) @Schema(description = "Type of the API Key") private ApiKeyType apiKeyType;

  @Size(max = 1024) @Schema(description = "Description of the Token") String description;
  @Size(max = 128) @Schema(description = "Tags for the Token") Map<String, String> tags;
  @Schema(description = "Email Id of the user who created the Token.") private String email;
  @Schema(description = "Name of the user who created the Token.") private String username;
  @Schema(description = "This is the encoded password of the Token.") private String encodedPassword;
}

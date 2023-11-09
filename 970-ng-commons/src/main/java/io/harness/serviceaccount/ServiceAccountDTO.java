/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serviceaccount;

import io.harness.NGCommonEntityConstants;
import io.harness.data.validator.EntityIdentifier;

import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotBlank;

@Getter
@SuperBuilder
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "ServiceAccount", description = "This has the details of Service Account in Harness.")
public class ServiceAccountDTO {
  @ApiModelProperty(required = true)
  @EntityIdentifier
  @NotBlank
  @Schema(description = "Identifier of the Service Account.")
  String identifier;
  @JsonView(JsonViews.Internal.class)
  @Schema(hidden = true, description = "UniqueId of Service Account")
  String uniqueId;
  @ApiModelProperty(required = true) @NotBlank @Schema(description = "Name of the Service Account.") String name;
  @ApiModelProperty(required = true)
  @Email
  @NotBlank
  @Schema(description = "Email of the Service Account.")
  String email;
  @Size(max = 1024) @Schema(description = "Description of the Service Account.") String description;
  @Size(max = 128) @Schema(description = "Tags of the Service Account.") Map<String, String> tags;
  @ApiModelProperty(required = true)
  @NotBlank
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE)
  String accountIdentifier;
  @EntityIdentifier(allowBlank = true)
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE)
  String orgIdentifier;
  @EntityIdentifier(allowBlank = true)
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
  String projectIdentifier;
}

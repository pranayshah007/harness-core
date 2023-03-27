/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.ConnectorConstants;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.entity.AllowedSourceType;
import io.harness.ng.core.common.beans.NGTag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotBlank;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@ApiModel("IPAllowlistConfig")
@OwnedBy(PL)
@Schema(
    name = "IPAllowlistConfig", description = "This contains data for a config set in Harness to allow selected IPs.")
public class IPAllowlistConfigDTO {
  @NotNull @NotBlank @NGEntityName @Schema(description = ConnectorConstants.CONNECTOR_NAME) String name;
  @NotNull
  @NotBlank
  @EntityIdentifier
  @Schema(description = ConnectorConstants.CONNECTOR_IDENTIFIER_MSG)
  String identifier;
  @Schema(description = NGCommonEntityConstants.DESCRIPTION) String description;
  @Singular @Schema(description = NGCommonEntityConstants.TAGS) @Size(max = 128) List<NGTag> tags;
  @Builder.Default Boolean enabled = Boolean.FALSE;
  @Builder.Default Set<AllowedSourceType> allowedSourceType = Set.of(AllowedSourceType.API, AllowedSourceType.UI);
  @NotNull String value;
}

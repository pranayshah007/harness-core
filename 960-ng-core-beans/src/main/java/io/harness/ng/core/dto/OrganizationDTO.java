/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonView;
import io.harness.OrganizationConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import software.wings.jersey.JsonViews;

@OwnedBy(PL)
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@ApiModel(value = "Organization")
@Schema(name = "Organization", description = "This has details of the Organization as defined in Harness.")
public class OrganizationDTO {
  @ApiModelProperty(required = true)
  @EntityIdentifier(allowBlank = false)
  @Schema(description = OrganizationConstants.IDENTIFIER)
  String identifier;
  @JsonView(JsonViews.Internal.class) String uniqueId;
  @ApiModelProperty(required = true) @NGEntityName @Schema(description = OrganizationConstants.NAME) String name;
  @Size(max = 1024) @Schema(description = "Description of the Organization.") String description;
  @Size(max = 128) @Schema(description = "Tags for the Organization.") Map<String, String> tags;
  @JsonIgnore Long version;
  @JsonIgnore boolean harnessManaged;

  @Builder
  public OrganizationDTO(
      String identifier, String name, String description, Map<String, String> tags, String uniqueId) {
    this.identifier = identifier;
    this.name = name;
    this.description = description;
    this.tags = tags;
    this.uniqueId = uniqueId;
  }
}

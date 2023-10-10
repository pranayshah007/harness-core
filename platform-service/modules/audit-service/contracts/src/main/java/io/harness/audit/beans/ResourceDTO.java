/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.Resource;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(PL)
@Data
@Builder
@JsonInclude(NON_NULL)
@FieldNameConstants(innerTypeName = "ResourceKeys")
@Schema(name = "Resource", description = "This contains info about Resource saved as a part of Audit Event")
public class ResourceDTO {
  @Schema(description = "Path to ResourceType")
  public static final String RESOURCE_TYPE_DATA_TYPE = "io.harness.audit.ResourceType";
  @NotNull
  @NotBlank
  @ApiModelProperty(dataType = RESOURCE_TYPE_DATA_TYPE)
  @Schema(description = "Resource Type")
  String type;
  @NotNull @NotBlank @Schema(description = "Resource Identifier") String identifier;
  String uniqueId;
  @Size(max = 5) @Schema(description = "Map of additional information about the Resource.") Map<String, String> labels;

  public static ResourceDTO fromResource(Resource resource) {
    if (resource == null) {
      return null;
    }
    return ResourceDTO.builder()
        .type(resource.getType())
        .identifier(resource.getIdentifier())
        .labels(resource.getLabels())
        .uniqueId(resource.getUniqueId())
        .build();
  }
}

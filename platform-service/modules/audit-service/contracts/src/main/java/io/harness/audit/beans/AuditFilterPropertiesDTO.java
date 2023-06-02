/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.filter.FilterConstants.AUDIT_FILTER;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.StaticAuditFilter;
import io.harness.audit.remote.StaticAuditFilterV2;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(PL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(AUDIT_FILTER)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("AuditFilterProperties")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(name = "AuditFilterProperties",
    description =
        "This contains the Audit Event filter information. This is used to filter Audit Events depending on the information provided.")
public class AuditFilterPropertiesDTO extends FilterPropertiesDTO {
  @Schema(description = "List of Resource Scopes") List<ResourceScopeDTO> scopes;
  @Schema(description = "List of Resources") List<ResourceDTO> resources;

  @Schema(description = "List of Module Types") List<ModuleType> modules;
  @Schema(description = "List of Actions") List<Action> actions;
  @Schema(description = "List of Environments") List<Environment> environments;
  @Schema(description = "List of Principals") List<Principal> principals;

  @Schema(description = "Pre-defined Filter") StaticAuditFilter staticFilter;

  @ApiModelProperty(hidden = true) @JsonIgnore @Hidden List<StaticAuditFilterV2> staticFilters;

  @Schema(description =
              "Used to specify a start time for retrieving Audit events that occurred at or after the time indicated.")
  Long startTime;
  @Schema(description =
              "Used to specify the end time for retrieving Audit events that occurred at or before the time indicated.")
  Long endTime;

  @Override
  @Schema(description = "This specifies the corresponding Entity of the filter.", type = "string",
      allowableValues = {"Audit"})
  public FilterType
  getFilterType() {
    return FilterType.AUDIT;
  }
}

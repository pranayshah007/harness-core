/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.beans;

import io.harness.data.validator.EntityIdentifier;
import io.harness.gitsync.beans.YamlDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.DefaultValue;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "Downtime", description = "This is the Downtime entity defined in Harness")
@EqualsAndHashCode()
public class DowntimeDTO implements YamlDTO {
  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String orgIdentifier;
  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String projectIdentifier;
  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String identifier;
  @ApiModelProperty(required = true) @NotNull String name;
  String description;
  @Size(max = 128) Map<String, String> tags;
  @ApiModelProperty(required = true) @NotNull DowntimeCategory category;
  @ApiModelProperty(required = true) @DefaultValue("Project") DowntimeScope scope;
  @Valid @NotNull DowntimeSpecDTO spec;

  @ApiModelProperty(required = true) @NotNull EntitiesRule entitiesRule;

  boolean enabled;
}

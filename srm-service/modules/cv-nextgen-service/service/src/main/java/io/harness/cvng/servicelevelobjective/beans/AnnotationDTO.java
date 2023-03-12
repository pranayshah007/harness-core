/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import io.harness.data.validator.EntityIdentifier;
import io.harness.gitsync.beans.YamlDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
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
@Schema(name = "Annotation", description = "This is the Annotation entity defined in Harness")
@EqualsAndHashCode()
public class AnnotationDTO implements YamlDTO {
  @ApiModelProperty(required = true) @EntityIdentifier(allowBlank = true) String orgIdentifier;
  @ApiModelProperty(required = true) @EntityIdentifier(allowBlank = true) String projectIdentifier;
  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String sloIdentifier;
  @NotNull @Size(min = 1, max = 1000) String message;
  @ApiModelProperty(required = true) @NotNull long startTime;
  @ApiModelProperty(required = true) @NotNull long endTime;
}

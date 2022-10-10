/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeAlias("io.harness.engine.expressions.ShellScriptBaseDTO")
public class ShellScriptBaseDTO {
  @EntityIdentifier String orgIdentifier;

  @EntityIdentifier String projectIdentifier;

  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String identifier;

  @ApiModelProperty(required = true) @NotNull @NGEntityName String name;

  @ApiModelProperty(required = true) @NotNull String versionLabel;

  @JsonProperty("type") String type;

  @NotNull @JsonProperty("spec") ShellScriptSpec shellScriptSpec;
}
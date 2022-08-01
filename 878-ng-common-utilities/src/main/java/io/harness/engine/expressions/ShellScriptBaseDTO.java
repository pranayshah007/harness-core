/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.ng.core.template.TemplateEntityConstants;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeAlias("ShellScriptBaseDTO")
public class ShellScriptBaseDTO {
  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String orgIdentifier;

  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String projectIdentifier;

  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String identifier;

  @ApiModelProperty(required = true) @NotNull @NGEntityName String name;

  @ApiModelProperty(required = true) @NotNull String versionLabel;

  @JsonProperty("type") String type;

  @NotNull @JsonProperty("spec") ShellScriptSpec shellScriptSpec;
}

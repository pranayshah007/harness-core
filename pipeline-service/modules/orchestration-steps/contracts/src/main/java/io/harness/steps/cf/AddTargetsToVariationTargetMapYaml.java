/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.cf;

import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("AddTargetsToVariationTargetMap")
@TypeAlias("AddTargetsToVariationTargetMapYaml")
public class AddTargetsToVariationTargetMapYaml implements PatchInstruction {
  @Builder.Default
  @NotNull
  @ApiModelProperty(allowableValues = "AddTargetsToVariationTargetMap")
  private PatchInstruction.Type type = Type.ADD_TARGETS_TO_VARIATION_TARGET_MAP;
  @NotNull private String identifier;
  @NotNull private AddTargetsToVariationTargetMapYamlSpec spec;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AddTargetsToVariationTargetMapYamlSpec {
    @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> variation;
    @NotNull
    @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
    private ParameterField<List<String>> targets;
  }
}

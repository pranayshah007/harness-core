/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.cf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cf.openapi.model.Distribution;
import io.harness.cf.openapi.model.Serve;
import io.harness.cf.openapi.model.WeightedVariation;
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

@OwnedBy(HarnessTeam.CF)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("UpdateRule")
@TypeAlias("UpdateRuleYaml")
public class UpdateRuleYaml implements PatchInstruction {
  @Builder.Default @NotNull @ApiModelProperty(allowableValues = "UpdateRule") private Type type = Type.UPDATE_RULE;
  @NotNull private String identifier;
  @NotNull private UpdateRuleYamlSpec spec;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UpdateRuleYamlSpec {
    @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> ruleID;
    @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> bucketBy;
    private List<AddRuleYaml.VariationYamlSpec> variations;

    public Serve getServe() {
      Serve serve = new Serve();
      Distribution distribution = new Distribution();
      distribution.bucketBy(getBucketBy().getValue());

      if (variations != null) {
        getVariations().forEach(variationSpec -> {
          WeightedVariation var = new WeightedVariation();
          var.setVariation(variationSpec.getVariation().getValue());
          var.setWeight(variationSpec.getWeight().getValue());
          distribution.addVariationsItem(var);
        });
      }
      serve.setDistribution(distribution);

      return serve;
    }
  }
}

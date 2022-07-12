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
import io.harness.cf.openapi.model.Clause;
import io.harness.cf.openapi.model.Distribution;
import io.harness.cf.openapi.model.Serve;
import io.harness.cf.openapi.model.WeightedVariation;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
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
@JsonTypeName("AddRule")
@TypeAlias("AddRuleYaml")
public class AddRuleYaml implements PatchInstruction {
  @Builder.Default
  @NotNull
  @ApiModelProperty(allowableValues = "AddRule")
  private PatchInstruction.Type type = Type.ADD_RULE;
  @NotNull private String identifier;
  @NotNull private AddRuleYamlSpec spec;

  enum Operator { STARTS_WITH, EQUALS }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AddRuleYamlSpec {
    @ApiModelProperty(dataType = SwaggerConstants.INTEGER_CLASSPATH) private ParameterField<Integer> priority;
    private DistributionYamlSpec distribution;

    /**
     * Get Serve builds a serve object from the AddRuleYamlSpec
     * @return Serve
     */
    public Serve getServe() {
      Serve serve = new Serve();
      if (getDistribution() != null) {
        serve.setDistribution(getDistribution().build());
      }
      return serve;
    }

    /**
     * getClauses returns a list of clauses from the spec
     * @return List
     */
    public List<Clause> getClauses() {
      List<Clause> clauses = new ArrayList<>();
      if (distribution != null) {
        distribution.clauses.forEach(clauseYaml -> {
          Clause clause = new Clause();
          clause.attribute(clauseYaml.getAttribute().getValue());
          clause.op(clauseYaml.getOp().getValue());
          clause.values(clauseYaml.getValues().getValue());
          clauses.add(clause);
        });
      }
      return clauses;
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DistributionYamlSpec {
    @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> bucketBy;
    private List<VariationYamlSpec> variations;
    private List<ClauseYamlSpec> clauses;

    public Distribution build() {
      Distribution distribution = new Distribution();
      distribution.bucketBy(getBucketBy().getValue());

      getVariations().forEach(variationSpec -> {
        WeightedVariation var = new WeightedVariation();
        var.setVariation(variationSpec.getVariation().getValue());
        var.setWeight(variationSpec.getWeight().getValue());
        distribution.addVariationsItem(var);
      });

      return distribution;
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class VariationYamlSpec {
    @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> variation;
    @NotNull @ApiModelProperty(dataType = SwaggerConstants.INTEGER_CLASSPATH) private ParameterField<Integer> weight;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ClauseYamlSpec {
    @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> attribute;
    @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> op;
    @NotNull
    @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
    private ParameterField<List<String>> values;
  }
}

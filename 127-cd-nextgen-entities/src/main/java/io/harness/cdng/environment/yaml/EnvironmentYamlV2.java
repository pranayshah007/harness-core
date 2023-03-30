/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtimeEmptyStringAllowed;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.helper.EnvironmentYamlV2VisitorHelper;
import io.harness.cdng.gitops.yaml.ClusterYaml;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@SimpleVisitorHelper(helperClass = EnvironmentYamlV2VisitorHelper.class)
@TypeAlias("environmentYamlV2")
@OwnedBy(CDC)
@RecasterAlias("io.harness.cdng.environment.yaml.EnvironmentYamlV2")
public class EnvironmentYamlV2 implements Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull
  @Pattern(regexp = NGRegexValidatorConstants.NON_EMPTY_STRING_PATTERN)
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  private ParameterField<String> environmentRef;

  /*
  Deploy to all underlying infrastructures (or gitops clusters)
   */
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  @YamlSchemaTypes({runtime})
  ParameterField<Boolean> deployToAll;

  @ApiModelProperty(dataType = SwaggerConstants.FILTER_YAML_LIST_CLASSPATH)
  @YamlSchemaTypes(runtime)
  ParameterField<List<FilterYaml>> filters;

  @ApiModelProperty(dataType = SwaggerConstants.INFRASTRUCTURE_DEFINITION_YAML_NODE_LIST_CLASSPATH)
  @YamlSchemaTypes({runtimeEmptyStringAllowed})
  ParameterField<List<InfraStructureDefinitionYaml>> infrastructureDefinitions;

  @ApiModelProperty(dataType = "io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml")
  @YamlSchemaTypes({expression})
  ParameterField<InfraStructureDefinitionYaml> infrastructureDefinition;

  @Nullable @VariableExpression(skipVariableExpression = true) ExecutionElementConfig provisioner;

  // environmentInputs
  @ApiModelProperty(dataType = SwaggerConstants.JSON_NODE_CLASSPATH)
  @YamlSchemaTypes({runtimeEmptyStringAllowed})
  ParameterField<Map<String, Object>> environmentInputs;

  @ApiModelProperty(dataType = SwaggerConstants.JSON_NODE_CLASSPATH)
  @YamlSchemaTypes({runtimeEmptyStringAllowed})
  ParameterField<Map<String, Object>> serviceOverrideInputs;

  @ApiModelProperty(dataType = SwaggerConstants.CLUSTER_YAML_NODE_LIST_CLASSPATH)
  @YamlSchemaTypes({runtime})
  ParameterField<List<ClusterYaml>> gitOpsClusters;

  List<ServiceOverrideInputsYaml> servicesOverrides;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  public ParameterField<Boolean> getDeployToAll() {
    // default to false
    if (deployToAll == null) {
      return ParameterField.createValueField(false);
    }
    return !deployToAll.isExpression() && deployToAll.getValue() == null ? ParameterField.createValueField(false)
                                                                         : deployToAll;
  }

  public EnvironmentYamlV2 clone() {
    ParameterField<List<FilterYaml>> filtersCloned = null;
    if (ParameterField.isNotNull(this.filters) && this.filters.getValue() != null) {
      filtersCloned = ParameterField.createValueField(
          this.filters.getValue().stream().map(FilterYaml::clone).collect(Collectors.toList()));
    }
    return EnvironmentYamlV2.builder()
        .environmentInputs(this.environmentInputs)
        .environmentRef(this.environmentRef)
        .deployToAll(this.deployToAll)
        .filters(filtersCloned)
        .gitOpsClusters(this.gitOpsClusters)
        .infrastructureDefinition(this.infrastructureDefinition)
        .infrastructureDefinitions(this.infrastructureDefinitions)
        .provisioner(this.provisioner)
        .serviceOverrideInputs(this.serviceOverrideInputs)
        .build();
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    if (ParameterField.isNotNull(infrastructureDefinitions) && infrastructureDefinitions.getValue() != null) {
      infrastructureDefinitions.getValue().forEach(id -> children.add("infrastructureDefinitions", id));
    }

    if (ParameterField.isNotNull(infrastructureDefinition) && infrastructureDefinition.getValue() != null) {
      children.add("infrastructureDefinition", infrastructureDefinition);
    }
    return children;
  }
}
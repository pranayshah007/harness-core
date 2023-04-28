/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.envgroup.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.envgroup.helper.EnvironmentGroupYamlVisitorHelper;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@SimpleVisitorHelper(helperClass = EnvironmentGroupYamlVisitorHelper.class)
@TypeAlias("environmentGroupYaml")
@OwnedBy(CDC)
@RecasterAlias("io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml")
@FieldNameConstants(innerTypeName = "environmentGroupYamlKeys")
public class EnvironmentGroupYaml implements Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME) @ApiModelProperty(hidden = true) private String uuid;

  @Pattern(regexp = NGRegexValidatorConstants.NON_EMPTY_STRING_PATTERN)
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  private ParameterField<String> envGroupRef;

  @ApiModelProperty(dataType = SwaggerConstants.ENVIRONMENT_YAML_LIST_CLASSPATH)
  @YamlSchemaTypes(runtime)
  ParameterField<List<EnvironmentYamlV2>> environments;

  @ApiModelProperty(dataType = SwaggerConstants.FILTER_YAML_LIST_CLASSPATH)
  @YamlSchemaTypes(runtime)
  ParameterField<List<FilterYaml>> filters;

  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  @YamlSchemaTypes(runtime)
  ParameterField<Boolean> deployToAll;

  @JsonProperty("metadata") EnvironmentGroupMetadata environmentGroupMetadata;

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    if (ParameterField.isNotNull(environments) && !environments.isExpression()) {
      environments.getValue().forEach(environmentYamlV2 -> children.add("environments", environmentYamlV2));
    }
    return children;
  }

  public ParameterField<Boolean> getDeployToAll() {
    if (deployToAll == null) {
      return ParameterField.createValueField(false);
    }
    return !deployToAll.isExpression() && deployToAll.getValue() == null ? ParameterField.createValueField(false)
                                                                         : deployToAll;
  }

  public EnvironmentGroupYaml clone() {
    ParameterField<List<EnvironmentYamlV2>> environmentsCloned =
        ParameterField.createValueField(Collections.emptyList());
    if (ParameterField.isNotNull(this.environments) && EmptyPredicate.isNotEmpty(this.environments.getValue())) {
      environmentsCloned = ParameterField.createValueField(
          this.environments.getValue().stream().map(EnvironmentYamlV2::clone).collect(Collectors.toList()));
    }
    ParameterField<List<FilterYaml>> filtersCloned = ParameterField.createValueField(Collections.emptyList());
    if (ParameterField.isNotNull(this.filters) && EmptyPredicate.isNotEmpty(this.filters.getValue())) {
      filtersCloned = ParameterField.createValueField(
          this.filters.getValue().stream().map(FilterYaml::clone).collect(Collectors.toList()));
    }
    return EnvironmentGroupYaml.builder()
        .environments(environmentsCloned)
        .environmentGroupMetadata(this.environmentGroupMetadata)
        .deployToAll(this.deployToAll)
        .envGroupRef(this.envGroupRef)
        .filters(filtersCloned)
        .uuid(this.uuid)
        .build();
  }
}
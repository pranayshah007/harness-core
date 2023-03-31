/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.yaml;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChild;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@RecasterAlias("io.harness.cdng.environment.yaml.EnvironmentsYaml")
@SimpleVisitorHelper(helperClass = EnvironmentsVisitorHelper.class)
@FieldNameConstants(innerTypeName = "keys")
public class EnvironmentsYaml implements Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @ApiModelProperty(dataType = SwaggerConstants.FILTER_YAML_LIST_CLASSPATH)
  @YamlSchemaTypes(runtime)
  ParameterField<List<FilterYaml>> filters;

  @ApiModelProperty(dataType = SwaggerConstants.ENVIRONMENT_YAML_LIST_CLASSPATH)
  @YamlSchemaTypes(value = {expression})
  @VariableExpression(skipVariableExpression = true)
  ParameterField<List<EnvironmentYamlV2>> values;

  @JsonProperty("metadata") EnvironmentsMetadata environmentsMetadata;

  @Override
  public VisitableChildren getChildrenToWalk() {
    List<VisitableChild> children = new ArrayList<>();
    if (ParameterField.isNotNull(values) && !values.isExpression()) {
      for (EnvironmentYamlV2 environmentYamlV2 : values.getValue()) {
        children.add(VisitableChild.builder().value(environmentYamlV2).fieldName("values").build());
      }
    }
    return VisitableChildren.builder().visitableChildList(children).build();
  }

  @JsonIgnore
  public void setEnvironmentsMetadata(String environmentsMetadata) {
    // do Nothing
  }

  @JsonIgnore
  public String getMetadata() {
    return null;
  }

  public EnvironmentsYaml clone() {
    ParameterField<List<FilterYaml>> filtersCloned = ParameterField.createValueField(Collections.emptyList());
    if (ParameterField.isNotNull(this.filters) && this.filters.getValue() != null) {
      filtersCloned = ParameterField.createValueField(
          this.filters.getValue().stream().map(FilterYaml::clone).collect(Collectors.toList()));
    }
    ParameterField<List<EnvironmentYamlV2>> valuesCloned = ParameterField.createValueField(Collections.emptyList());
    if (ParameterField.isNotNull(this.values) && this.values.getValue() != null) {
      valuesCloned = ParameterField.createValueField(
          this.getValues().getValue().stream().map(EnvironmentYamlV2::clone).collect(Collectors.toList()));
    }
    return EnvironmentsYaml.builder()
        .filters(filtersCloned)
        .environmentsMetadata(this.environmentsMetadata)
        .values(valuesCloned)
        .build();
  }
}

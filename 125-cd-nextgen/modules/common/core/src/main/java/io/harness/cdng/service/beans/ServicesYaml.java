/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.beans;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.utils.YamlConstants.INPUT;

import io.harness.annotation.RecasterAlias;
import io.harness.beans.SwaggerConstants;
import io.harness.common.NGExpressionUtils;
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
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

@Value
@Builder
@RecasterAlias("io.harness.cdng.service.beans.Services")
@SimpleVisitorHelper(helperClass = ServicesVisitorHelper.class)
public class ServicesYaml implements Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @VariableExpression(skipVariableExpression = true)
  @ApiModelProperty(dataType = SwaggerConstants.SERVICE_YAML_LIST_CLASSPATH)
  @YamlSchemaTypes(value = {runtime})
  ParameterField<List<ServiceYamlV2>> values;

  @JsonProperty("metadata") ServicesMetadata servicesMetadata;

  @Override
  public VisitableChildren getChildrenToWalk() {
    List<VisitableChild> children = new ArrayList<>();
    if (!values.isExpression()) {
      for (ServiceYamlV2 serviceYamlV2 : values.getValue()) {
        children.add(VisitableChild.builder().value(serviceYamlV2).fieldName("values").build());
      }
    } else if (NGExpressionUtils.isRuntimeField(values.getExpressionValue())) {
      ServiceYamlV2 serviceYamlV2 = ServiceYamlV2.builder()
                                        .serviceRef(ParameterField.createExpressionField(true, INPUT, null, true))
                                        .serviceInputs(ParameterField.createExpressionField(true, INPUT, null, false))
                                        .build();
      children.add(VisitableChild.builder().value(serviceYamlV2).fieldName("values").build());
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
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.visitor.helpers.serviceconfig.ServiceConfigVisitorHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validation.OneOfField;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.intfc.OverridesApplier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Wither;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@OneOfField(fields = {"useFromStage", "service", "serviceRef"})
@OneOfField(fields = {"serviceDefinition", "useFromStage"})
@SimpleVisitorHelper(helperClass = ServiceConfigVisitorHelper.class)
@OwnedBy(CDC)
@RecasterAlias("io.harness.cdng.service.beans.ServiceConfig")
public class ServiceConfig implements OverridesApplier<ServiceConfig>, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @Wither @VariableExpression(skipVariableExpression = true) private ServiceUseFromStage useFromStage;

  @Wither private ServiceYaml service;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> serviceRef;
  private ServiceDefinition serviceDefinition;
  @Wither @VariableExpression(skipVariableExpression = true) private StageOverridesConfig stageOverrides;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @JsonIgnore
  public ServiceConfig applyUseFromStage(ServiceConfig serviceConfigToUseFrom) {
    return serviceConfigToUseFrom.withStageOverrides(stageOverrides).withUseFromStage(useFromStage);
  }

  @Override
  public ServiceConfig applyOverrides(ServiceConfig overrideConfig) {
    ServiceYaml resultantConfigService = service;
    ServiceYaml overrideConfigService = overrideConfig.getService();
    if (EmptyPredicate.isNotEmpty(overrideConfigService.getName())) {
      resultantConfigService = resultantConfigService.withName(overrideConfigService.getName());
    }
    if (!ParameterField.isNull(overrideConfigService.getDescription())) {
      resultantConfigService = resultantConfigService.withDescription(overrideConfigService.getDescription());
    }

    ServiceConfig resultantConfig = this;
    resultantConfig = resultantConfig.withService(resultantConfigService);
    return resultantConfig;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    children.add("service", service);
    children.add("serviceDefinition", serviceDefinition);
    children.add("useFromStage", useFromStage);
    children.add("stageOverrides", stageOverrides);
    return children;
  }
}

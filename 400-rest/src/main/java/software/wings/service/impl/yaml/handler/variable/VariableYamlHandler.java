/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.variable;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.HarnessException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.Variable;
import software.wings.beans.Variable.VariableBuilder;
import software.wings.beans.VariableType;
import software.wings.beans.VariableYaml;
import software.wings.beans.Workflow;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.yaml.handler.ArtifactVariableYamlHelper;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.SettingsService;
import software.wings.utils.Utils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 10/28/17
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
public class VariableYamlHandler extends BaseYamlHandler<VariableYaml, Variable> {
  @Inject FeatureFlagService featureFlagService;
  @Inject SettingsService settingsService;
  @Inject ArtifactStreamService artifactStreamService;
  @Inject AppService appService;
  @Inject ArtifactVariableYamlHelper artifactVariableYamlHelper;

  private Variable toBean(ChangeContext<VariableYaml> changeContext, Workflow previousWorkflow) {
    VariableYaml yaml = changeContext.getYaml();
    VariableType variableType = Utils.getEnumFromString(VariableType.class, yaml.getType());
    String variableName = yaml.getName();
    if (variableType == VariableType.ARTIFACT) {
      throw new WingsException(
          format("Variable type ARTIFACT not supported, skipping processing of variable [%s]", variableName),
          WingsException.USER);
    }

    if (previousWorkflow != null) {
      List<Variable> variables = previousWorkflow.getOrchestrationWorkflow().getUserVariables();
      // Check only if new variables contain hyphens. Old variables should not be checked for backward compatibility
      if (variables.stream().allMatch(variable -> !variable.getName().equals(variableName))) {
        if (variableName.contains("-")) {
          throw new InvalidRequestException(
              format("Adding variable name %s with hyphens (dashes) is not allowed", variableName));
        }
      }
    }

    return VariableBuilder.aVariable()
        .description(yaml.getDescription())
        .fixed(yaml.isFixed())
        .mandatory(yaml.isMandatory())
        .name(variableName)
        .type(variableType)
        .value(yaml.getValue())
        .allowedValues(yaml.getAllowedValues()) // convert to comma separated and set this
        .build();
  }

  @Override
  public VariableYaml toYaml(Variable bean, String appId) {
    VariableType type = bean.getType();
    if (type == VariableType.ARTIFACT) {
      throw new WingsException(
          format("Variable type ARTIFACT not supported, skipping processing of variable [%s]", bean.getName()),
          WingsException.USER);
    }
    return VariableYaml.builder()
        .description(bean.getDescription())
        .fixed(bean.isFixed())
        .mandatory(bean.isMandatory())
        .name(bean.getName())
        .type(bean.getType() != null ? bean.getType().name() : null)
        .value(bean.getValue())
        .allowedValues(bean.getAllowedValues())
        .build();
  }

  @Override
  public Variable upsertFromYaml(ChangeContext<VariableYaml> changeContext, List<ChangeContext> changeSetContext)
      throws HarnessException {
    return toBean(changeContext, null);
  }

  @Override
  public Class getYamlClass() {
    return VariableYaml.class;
  }

  @Override
  public Variable get(String accountId, String yamlFilePath) {
    throw new WingsException(ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION);
  }

  @Override
  public void delete(ChangeContext<VariableYaml> changeContext) {
    // Do nothing
  }

  public Variable upsertFromYaml(ChangeContext<VariableYaml> changeContext, Workflow previousWorkflow)
      throws WingsException {
    return toBean(changeContext, previousWorkflow);
  }
}

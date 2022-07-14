/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.common;

import static software.wings.expression.ManagerExpressionEvaluator.matchesVariablePattern;
import static software.wings.expression.ManagerExpressionEvaluator.wingsVariablePattern;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.regex.Matcher;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class TemplateExpressionProcessor {
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private SettingsService settingsService;

  public static String changeToWorkflowVariable(TemplateExpression templateExpression) {
    String templateVariable = templateExpression.getExpression();
    Matcher matcher = wingsVariablePattern.matcher(templateVariable);
    if (matcher.matches()) {
      templateVariable = matcher.group(0);
      templateVariable = templateVariable.substring(2, templateVariable.length() - 1);
      if (!templateVariable.startsWith("workflow.variables.")) {
        templateVariable = "${workflow.variables." + templateVariable + "}";
      }
    } else {
      throw new WingsException("Invalid template expression: " + templateExpression);
    }
    return templateVariable;
  }

  public InfrastructureMapping resolveInfraMapping(
      ExecutionContext context, String appId, TemplateExpression templateExpression) {
    String infraMappingId = resolveTemplateExpression(context, templateExpression);
    InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(appId, infraMappingId);
    if (infrastructureMapping == null) {
      if (matchesVariablePattern(infraMappingId)) {
        throw new WingsException("No value provided for templated Service Infrastructure workflow variable ["
                + templateExpression.getExpression() + "]",
            WingsException.USER);
      }
      throw new WingsException("Service Infrastructure expression  " + templateExpression.getExpression()
              + " resolved as [" + infraMappingId
              + "]. However, no Service Infrastructure found with InframappingId : " + infraMappingId,
          WingsException.USER);
    }
    return infrastructureMapping;
  }

  public InfrastructureDefinition resolveInfraDefinition(
      ExecutionContext context, String appId, TemplateExpression templateExpression) {
    String infraDefinitionId = resolveTemplateExpression(context, templateExpression);
    InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionService.get(appId, infraDefinitionId);
    if (infrastructureDefinition == null) {
      infrastructureDefinition = infrastructureDefinitionService.getInfraDefByName(
          appId, ((ExecutionContextImpl) context).getEnv().getUuid(), infraDefinitionId);
    }
    if (infrastructureDefinition == null) {
      if (matchesVariablePattern(infraDefinitionId)) {
        if (infraDefinitionId.contains(".")) {
          throw new InvalidRequestException(infraDefinitionId + " doesn't resolve to a valid value");
        }
        throw new WingsException("No value provided for templated Infra Definition workflow variable ["
                + templateExpression.getExpression() + "]",
            WingsException.USER);
      }
      throw new WingsException("Infrastructure Definition expression  " + templateExpression.getExpression()
              + " resolved as [" + infraDefinitionId
              + "]. However, no Infrastructure Definition found with InfraDefinitionId : " + infraDefinitionId,
          WingsException.USER);
    }
    return infrastructureDefinition;
  }

  public Service resolveService(ExecutionContext context, String appId, TemplateExpression templateExpression) {
    String serviceId = resolveTemplateExpression(context, templateExpression);
    // Then variable contains serviceId
    Service service = serviceResourceService.get(appId, serviceId, false);
    if (service == null) {
      if (matchesVariablePattern(serviceId)) {
        throw new WingsException(
            "No value provided for templated Service workflow variable [" + templateExpression.getExpression() + "]",
            WingsException.USER);
      }
      throw new WingsException("Service templated workflow variable " + templateExpression.getExpression()
              + " resolved as [" + serviceId + "]. However, no service found with service id: " + serviceId,
          WingsException.USER);
    }
    return service;
  }

  public SettingAttribute resolveSettingAttribute(ExecutionContext context, TemplateExpression templateExpression) {
    String settingId = resolveTemplateExpression(context, templateExpression);
    if (settingId == null) {
      throw new WingsException("No value provided for template expression [" + templateExpression.getExpression() + "]",
          WingsException.USER);
    }
    SettingAttribute settingAttribute = settingsService.get(settingId);
    return validateSettingAttribute(templateExpression, settingId, settingAttribute);
  }

  @NotNull
  private SettingAttribute validateSettingAttribute(
      TemplateExpression templateExpression, String settingId, SettingAttribute settingAttribute) {
    if (settingAttribute == null) {
      if (matchesVariablePattern(settingId)) {
        throw new WingsException(
            "No value provided for template expression  [" + templateExpression.getExpression() + "]",
            WingsException.USER);
      }
      throw new WingsException("Setting expression " + templateExpression.getExpression() + " resolved as [" + settingId
              + "]. However, no Connector/Cloud Provider found with id : " + settingId,
          WingsException.USER);
    }
    return settingAttribute;
  }

  public SettingAttribute resolveSettingAttributeByNameOrId(
      ExecutionContext context, TemplateExpression templateExpression, SettingVariableTypes settingVariableTypes) {
    String settingIdOrName = resolveTemplateExpression(context, templateExpression);
    if (settingIdOrName == null) {
      throw new WingsException("No value provided for template expression [" + templateExpression.getExpression() + "]",
          WingsException.USER);
    }
    log.info("Checking  Setting attribute can be found by id {} first.", settingIdOrName);
    SettingAttribute settingAttribute = settingsService.get(settingIdOrName);
    if (settingAttribute == null) {
      log.info("Setting attribute not found by id. Verifying if it does exist by Name {} ", settingIdOrName);
      settingAttribute =
          settingsService.fetchSettingAttributeByName(context.getAccountId(), settingIdOrName, settingVariableTypes);
    }
    return validateSettingAttribute(templateExpression, settingIdOrName, settingAttribute);
  }

  public String resolveTemplateExpression(ExecutionContext context, TemplateExpression templateExpression) {
    String expression = changeToWorkflowVariable(templateExpression);
    return context.renderExpression(expression);
  }

  public TemplateExpression getTemplateExpression(List<TemplateExpression> templateExpressions, String fieldName) {
    return templateExpressions.stream()
        .filter(templateExpression -> templateExpression.getFieldName().equals(fieldName))
        .findFirst()
        .orElse(null);
  }

  public static boolean checkFieldTemplatized(String fieldName, List<TemplateExpression> templateExpressions) {
    if (templateExpressions == null) {
      return false;
    }
    return templateExpressions.stream().anyMatch(
        templateExpression -> templateExpression.getFieldName().equals(fieldName));
  }
}

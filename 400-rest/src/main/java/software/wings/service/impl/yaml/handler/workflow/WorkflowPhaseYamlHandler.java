/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.HarnessException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;

import software.wings.api.DeploymentType;
import software.wings.beans.EntityType;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.NameValuePair;
import software.wings.beans.PhaseStep;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder;
import software.wings.beans.WorkflowPhase.Yaml;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.YamlConstants;
import software.wings.beans.yaml.YamlType;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.handler.template.TemplateExpressionYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.utils.Utils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author rktummala on 10/27/17
 */
@OwnedBy(CDC)
@Singleton
public class WorkflowPhaseYamlHandler extends BaseYamlHandler<WorkflowPhase.Yaml, WorkflowPhase> {
  @Inject private YamlHelper yamlHelper;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private SettingsService settingsService;
  @Inject private InfrastructureMappingService infraMappingService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private YamlHandlerFactory yamlHandlerFactory;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private AppService appService;
  @Inject private WorkflowServiceHelper workflowServiceHelper;

  private WorkflowPhase toBean(ChangeContext<Yaml> context, List<ChangeContext> changeSetContext) {
    Yaml yaml = context.getYaml();
    Change change = context.getChange();
    String accountId = change.getAccountId();
    String appId = yamlHelper.getAppId(accountId, change.getFilePath());
    notNullCheck("Could not retrieve valid app from path: " + change.getFilePath(), appId, USER);

    String envId = context.getEntityIdMap().get(EntityType.ENVIRONMENT.name());
    String infraMappingId = null;
    String infraMappingName = null;
    String infraDefId = null;
    String infraDefName = null;
    String deploymentTypeString = yaml.getType();

    if (isNotEmpty(yaml.getInfraDefinitionName())) {
      InfrastructureDefinition infrastructureDefinition =
          infrastructureDefinitionService.getInfraDefByName(appId, envId, yaml.getInfraDefinitionName());
      infraDefId = infrastructureDefinition != null ? infrastructureDefinition.getUuid() : null;
      infraDefName = infrastructureDefinition != null ? infrastructureDefinition.getName() : null;

    } else if (envId != null && isNotEmpty(yaml.getInfraMappingName())) {
      InfrastructureMapping infraMapping =
          infraMappingService.getInfraMappingByName(appId, envId, yaml.getInfraMappingName());
      infraMappingId = infraMapping != null ? infraMapping.getUuid() : null;
      infraMappingName = infraMapping != null ? infraMapping.getName() : null;
    }

    String serviceId = null;
    if (isNotEmpty(yaml.getServiceName())) {
      Service service = serviceResourceService.getServiceByName(appId, yaml.getServiceName());
      serviceId = service != null ? service.getUuid() : null;
    }

    // phase step
    List<PhaseStep> phaseSteps = Lists.newArrayList();
    if (yaml.getPhaseSteps() != null) {
      PhaseStepYamlHandler phaseStepYamlHandler = yamlHandlerFactory.getYamlHandler(YamlType.PHASE_STEP);
      phaseSteps = yaml.getPhaseSteps()
                       .stream()
                       .map(phaseStep -> {
                         try {
                           ChangeContext.Builder clonedContext = cloneFileChangeContext(context, phaseStep);
                           return phaseStepYamlHandler.upsertFromYaml(clonedContext.build(), changeSetContext);
                         } catch (HarnessException e) {
                           throw new WingsException(e);
                         }
                       })
                       .collect(toList());
    }

    // template expressions
    List<TemplateExpression> templateExpressions = Lists.newArrayList();
    if (yaml.getTemplateExpressions() != null) {
      TemplateExpressionYamlHandler templateExprYamlHandler =
          yamlHandlerFactory.getYamlHandler(YamlType.TEMPLATE_EXPRESSION);
      templateExpressions =
          yaml.getTemplateExpressions()
              .stream()
              .map(templateExpr -> {
                try {
                  ChangeContext.Builder clonedContext = cloneFileChangeContext(context, templateExpr);
                  return templateExprYamlHandler.upsertFromYaml(clonedContext.build(), changeSetContext);
                } catch (HarnessException e) {
                  throw new WingsException(e);
                }
              })
              .collect(toList());
    }

    SettingAttribute computeProvider = settingsService.getByName(accountId, appId, yaml.getComputeProviderName());
    String computeProviderId;
    if (computeProvider == null) {
      computeProviderId = yaml.getComputeProviderName();
    } else {
      computeProviderId = computeProvider.getUuid();
    }

    List<NameValuePair> serviceVariableOverride = Lists.newArrayList();
    if (yaml.getServiceVariableOverrides() != null) {
      serviceVariableOverride =
          yaml.getServiceVariableOverrides()
              .stream()
              .map(variableOverride
                  -> NameValuePair.builder()
                         .name(variableOverride.getName())
                         .value(variableOverride.getValue() == null ? "" : variableOverride.getValue())
                         .valueType(variableOverride.getValueType())
                         .build())
              .collect(Collectors.toList());
    }

    Boolean isRollback = (Boolean) context.getProperties().get(YamlConstants.IS_ROLLBACK);
    WorkflowPhaseBuilder phase = WorkflowPhaseBuilder.aWorkflowPhase();
    DeploymentType deploymentType = Utils.getEnumFromString(DeploymentType.class, deploymentTypeString);
    WorkflowPhase workflowPhase = phase.computeProviderId(computeProviderId)
                                      .deploymentType(deploymentType)
                                      .infraMappingId(infraMappingId)
                                      .infraMappingName(infraMappingName)
                                      .infraDefinitionName(infraDefName)
                                      .infraDefinitionId(infraDefId)
                                      .name(yaml.getName())
                                      .phaseNameForRollback(yaml.getPhaseNameForRollback())
                                      .phaseSteps(phaseSteps)
                                      .serviceId(serviceId)
                                      .rollback(isRollback)
                                      .templateExpressions(templateExpressions)
                                      .daemonSet(yaml.isDaemonSet())
                                      .statefulSet(yaml.isStatefulSet())
                                      .variableOverrides(serviceVariableOverride)
                                      .build();
    workflowServiceHelper.validateService(
        workflowPhase, yaml.getServiceName(), (Boolean) context.getProperties().get("IS_BUILD"));
    return workflowPhase;
  }

  @Override
  public Yaml toYaml(WorkflowPhase bean, String appId) {
    SettingAttribute settingAttribute = settingsService.get(bean.getComputeProviderId());
    String computeProviderName;
    if (settingAttribute == null) {
      computeProviderName = bean.getComputeProviderId();
    } else {
      computeProviderName = settingAttribute.getName();
    }

    // template expressions
    List<TemplateExpression.Yaml> templateExprYamlList = Lists.newArrayList();
    if (bean.getTemplateExpressions() != null) {
      TemplateExpressionYamlHandler templateExpressionYamlHandler =
          yamlHandlerFactory.getYamlHandler(YamlType.TEMPLATE_EXPRESSION);
      templateExprYamlList =
          bean.getTemplateExpressions()
              .stream()
              .map(templateExpression -> templateExpressionYamlHandler.toYaml(templateExpression, appId))
              .collect(toList());
    }

    List<PhaseStep.Yaml> phaseStepYamlList = Lists.newArrayList();
    if (bean.getPhaseSteps() != null) {
      PhaseStepYamlHandler phaseStepYamlHandler = yamlHandlerFactory.getYamlHandler(YamlType.PHASE_STEP);
      phaseStepYamlList = bean.getPhaseSteps()
                              .stream()
                              .map(phaseStep -> phaseStepYamlHandler.toYaml(phaseStep, appId))
                              .collect(toList());
    }

    String deploymentType = Utils.getStringFromEnum(bean.getDeploymentType());
    String serviceName = null;
    if (isNotEmpty(bean.getServiceId())) {
      if (!bean.isSrvTemplatised()) {
        Service service = serviceResourceService.getWithDetails(appId, bean.getServiceId());
        serviceName = service != null ? service.getName() : null;
      }
    }

    String infraMappingName = null;
    String infraDefId = bean.getInfraDefinitionId();
    String infraDefName = null;
    InfrastructureDefinition infrastructureDefinition = null;
    if (isNotEmpty(infraDefId)) {
      infrastructureDefinition = infrastructureDefinitionService.get(appId, infraDefId);
      if (infrastructureDefinition != null) {
        infraDefName = infrastructureDefinition.getName();
      }
    }

    // when templatized infraMappings used, we do expect infraMapping can be null, so don't perform this check
    if (isNotEmpty(infraDefId) && infrastructureDefinition == null && !bean.checkInfraDefinitionTemplatized()) {
      String message = format(
          "Infra-definition:%s could not be found for workflowPhase:%s, for app:%s", infraDefId, bean.getName(), appId);
      throw new WingsException(ErrorCode.GENERAL_ERROR, USER).addParam("message", message);
    }

    return Yaml.builder()
        .computeProviderName(computeProviderName)
        .infraMappingName(infraMappingName)
        .infraDefinitionName(infraDefName)
        .serviceName(serviceName)
        .name(bean.getName())
        .phaseNameForRollback(bean.getPhaseNameForRollback())
        .phaseSteps(phaseStepYamlList)
        .provisionNodes(bean.isProvisionNodes())
        .templateExpressions(templateExprYamlList)
        .type(deploymentType)
        .daemonSet(bean.isDaemonSet())
        .statefulSet(bean.isStatefulSet())
        .serviceVariableOverrides(bean.getVariableOverrides())
        .build();
  }

  @Override
  public WorkflowPhase upsertFromYaml(ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext)
      throws HarnessException {
    return toBean(changeContext, changeSetContext);
  }

  @Override
  public Class getYamlClass() {
    return WorkflowPhase.Yaml.class;
  }

  @Override
  public WorkflowPhase get(String accountId, String yamlFilePath) {
    throw new WingsException(ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION);
  }

  @Override
  public void delete(ChangeContext<Yaml> changeContext) throws HarnessException {
    // Do nothing
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.unhandled;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.yaml.YamlConstants.DEFAULTS_YAML;
import static software.wings.beans.yaml.YamlConstants.INDEX_YAML;
import static software.wings.beans.yaml.YamlConstants.TAGS_YAML;
import static software.wings.beans.yaml.YamlConstants.YAML_EXTENSION;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_APP_SETTINGS_ENV_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_APP_SETTINGS_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_CONN_STRINGS_ENV_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_CONN_STRINGS_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_HELM_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_HELM_OVERRIDES_ALL_SERVICE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_KUSTOMIZE_PATCHES_ENV_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_KUSTOMIZE_PATCHES_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_OC_PARAMS_ENV_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_OC_PARAMS_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_PCF_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_PCF_OVERRIDES_ALL_SERVICE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_VALUES_ENV_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_VALUES_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.EVENT_RULE;
import static software.wings.settings.SettingVariableTypes.OCI_HELM_REPO;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CgEventConfig;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.exception.YamlException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.UuidAccess;
import io.harness.rest.RestResponse;
import io.harness.service.EventConfigService;
import io.harness.yaml.BaseYaml;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.Base;
import software.wings.beans.ConfigFile;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.HarnessTag;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.InfrastructureProvisioner.InfraProvisionerYaml;
import software.wings.beans.LambdaSpecification;
import software.wings.beans.NotificationGroup;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Workflow;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ApplicationManifest.AppManifestSource;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.command.Command;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.container.ContainerTask;
import software.wings.beans.container.EcsServiceSpecification;
import software.wings.beans.container.HelmChartSpecification;
import software.wings.beans.container.PcfServiceSpecification;
import software.wings.beans.container.UserDataSpecification;
import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.beans.template.Template;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.yaml.YamlConstants;
import software.wings.beans.yaml.YamlType;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.InfrastructureDefinitionYaml;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.handler.setting.SettingValueYamlHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.CommandService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.NotificationSetupService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.compliance.GovernanceConfigService;
import software.wings.service.intfc.template.TemplateService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.service.intfc.yaml.YamlArtifactStreamService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.YamlResourceService;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.verification.CVConfiguration;
import software.wings.verification.CVConfigurationYaml;
import software.wings.yaml.YamlHelper;
import software.wings.yaml.YamlPayload;
import software.wings.yaml.command.CommandYaml;
import software.wings.yaml.templatelibrary.TemplateLibraryYaml;
import software.wings.yaml.templatelibrary.TemplateYamlConfig;
import software.wings.yaml.workflow.WorkflowYaml;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;

@Singleton
@Slf4j
@OwnedBy(DX)
public class YamlResourceServiceImpl implements YamlResourceService {
  @Inject private AppService appService;
  @Inject private CommandService commandService;
  @Inject private ConfigService configService;
  @Inject private EnvironmentService environmentService;
  @Inject private PipelineService pipelineService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private TriggerService triggerService;
  @Inject private YamlArtifactStreamService yamlArtifactStreamService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private InfrastructureMappingService infraMappingService;
  @Inject private WorkflowService workflowService;
  @Inject private SettingsService settingsService;
  @Inject private YamlGitService yamlGitSyncService;
  @Inject private YamlHandlerFactory yamlHandlerFactory;
  @Inject private NotificationSetupService notificationSetupService;
  @Inject private InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject private CVConfigurationService cvConfigurationService;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject private HarnessTagService harnessTagService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private TemplateService templateService;
  @Inject private GovernanceConfigService governanceConfigService;
  @Inject private EventConfigService eventConfigService;

  /**
   * Find by app, service and service command ids.
   *
   * @param appId     the app id
   * @param serviceCommandId the service command id
   * @return the application
   */
  @Override
  public RestResponse<YamlPayload> getServiceCommand(@NotEmpty String appId, @NotEmpty String serviceCommandId) {
    String accountId = appService.getAccountIdByAppId(appId);
    notNullCheck("No account found for appId:" + appId, accountId);

    ServiceCommand serviceCommand = commandService.getServiceCommand(appId, serviceCommandId);
    if (serviceCommand != null) {
      Command command = commandService.getCommand(appId, serviceCommand.getUuid(), serviceCommand.getDefaultVersion());
      notNullCheck("No command with the given service command id:" + serviceCommandId, command);

      serviceCommand.setCommand(command);

      CommandYaml commandYaml =
          (CommandYaml) yamlHandlerFactory.getYamlHandler(YamlType.COMMAND).toYaml(serviceCommand, appId);
      return YamlHelper.getYamlRestResponse(yamlGitSyncService, serviceCommand.getUuid(), accountId, commandYaml,
          serviceCommand.getName() + YAML_EXTENSION);
    } else {
      throw new YamlException("The ServiceCommand with appId: '" + appId + "' and serviceCommandId: '"
              + serviceCommandId + "' was not found!",
          USER);
    }
  }

  /**
   * Gets the yaml version of a pipeline by pipelineId
   *
   * @param appId     the app id
   * @param pipelineId the pipeline id
   * @return the rest response
   */
  @Override
  public RestResponse<YamlPayload> getPipeline(String appId, String pipelineId) {
    String accountId = appService.getAccountIdByAppId(appId);
    notNullCheck("No account found for appId:" + appId, accountId);
    Pipeline pipeline = pipelineService.readPipeline(appId, pipelineId, false);
    notNullCheck("No pipeline with the given id:" + pipelineId, pipeline);
    Pipeline.Yaml pipelineYaml =
        (Pipeline.Yaml) yamlHandlerFactory.getYamlHandler(YamlType.PIPELINE).toYaml(pipeline, appId);
    return YamlHelper.getYamlRestResponse(
        yamlGitSyncService, pipeline.getUuid(), accountId, pipelineYaml, pipeline.getName() + YAML_EXTENSION);
  }

  @Override
  public RestResponse<YamlPayload> getApplicationManifest(String appId, String applicationManifestId) {
    String accountId = appService.getAccountIdByAppId(appId);
    notNullCheck("No account found for appId:" + appId, accountId);
    ApplicationManifest applicationManifest = applicationManifestService.getById(appId, applicationManifestId);

    notNullCheck("No Application Manifest with the given id:" + applicationManifestId, applicationManifest);
    ApplicationManifest.Yaml yaml =
        (ApplicationManifest.Yaml) yamlHandlerFactory.getYamlHandler(getYamlTypeFromAppManifest(applicationManifest))
            .toYaml(applicationManifest, appId);
    return YamlHelper.getYamlRestResponse(yamlGitSyncService, applicationManifest.getUuid(), accountId, yaml,
        StringUtils.isNotBlank(applicationManifest.getName()) ? applicationManifest.getName() + YAML_EXTENSION
                                                              : INDEX_YAML);
  }

  @Override
  public RestResponse<YamlPayload> getManifestFile(String appId, String manifestFileId) {
    String accountId = appService.getAccountIdByAppId(appId);
    notNullCheck("No account found for appId:" + appId, accountId);
    ManifestFile manifestFile = applicationManifestService.getManifestFileById(appId, manifestFileId);

    notNullCheck("No Manifest File with the given id:" + manifestFileId, manifestFile);
    return YamlHelper.getYamlRestResponseForActualFile(manifestFile.getFileContent(), manifestFile.getFileName());
  }

  @Override
  public RestResponse<YamlPayload> getNotificationGroup(String accountId, String notificationGroupId) {
    NotificationGroup notificationGroup =
        notificationSetupService.readNotificationGroup(accountId, notificationGroupId);
    notNullCheck("No notification group exists with the given id:" + notificationGroupId, notificationGroup);

    NotificationGroup.Yaml notificationGroupYaml =
        (NotificationGroup.Yaml) yamlHandlerFactory.getYamlHandler(YamlType.NOTIFICATION_GROUP)
            .toYaml(notificationGroup, GLOBAL_APP_ID);
    return YamlHelper.getYamlRestResponse(yamlGitSyncService, notificationGroup.getUuid(), accountId,
        notificationGroupYaml, notificationGroup.getName() + YAML_EXTENSION);
  }

  /**
   * Gets the yaml version of a trigger by artifactStreamId
   *
   * @param appId     the app id
   * @param artifactStreamId the artifact stream id
   * @return the rest response
   */
  @Override
  public RestResponse<YamlPayload> getArtifactTrigger(String appId, String artifactStreamId) {
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (artifactStream == null) {
      // handle missing artifactStream
      throw new YamlException("The ArtifactStream artifactStreamId: '" + artifactStreamId + "' was not found!", USER);
    }

    return getArtifactTrigger(appId, artifactStream);
  }

  private RestResponse<YamlPayload> getArtifactTrigger(String appId, ArtifactStream artifactStream) {
    String artifactStreamId = artifactStream.getUuid();
    ArtifactStream.Yaml artifactStreamYaml = yamlArtifactStreamService.getArtifactStreamYamlObject(artifactStreamId);
    if (!GLOBAL_APP_ID.equals(appId)) {
      // TODO: ASR: IMP: hack to make yaml push work as yaml changes require binding info but the binding info is
      // deleted in parallel
      Service service;
      if (artifactStream.getService() != null) {
        service = artifactStream.getService();
      } else {
        service = artifactStreamServiceBindingService.getService(appId, artifactStreamId, false);
        if (service == null) {
          throw new YamlException("The Service with artifactStreamId: '" + artifactStreamId + "' was not found!", USER);
        }
      }

      String serviceName = service.getName();
      String payLoadName = artifactStream.getSourceName() + "(" + serviceName + ")";
      return YamlHelper.getYamlRestResponse(yamlGitSyncService, artifactStream.getUuid(), artifactStream.getAccountId(),
          artifactStreamYaml, payLoadName + ".yaml");
    } else {
      String payLoadName = artifactStream.getSourceName();
      return YamlHelper.getYamlRestResponse(artifactStreamYaml, payLoadName + ".yaml");
    }
  }

  /**
   * Gets the yaml version of a trigger by trigger id
   *
   * @param appId     the app id
   * @param triggerId the trigger id
   * @return the rest response
   */
  @Override
  public RestResponse<YamlPayload> getTrigger(String appId, String triggerId) {
    String accountId = appService.getAccountIdByAppId(appId);
    notNullCheck("No account found for appId:" + appId, accountId);

    Trigger trigger = triggerService.get(appId, triggerId);

    Trigger.Yaml triggerYaml = (Trigger.Yaml) yamlHandlerFactory
                                   .getYamlHandler(YamlType.TRIGGER)

                                   .toYaml(trigger, appId);

    return YamlHelper.getYamlRestResponse(
        yamlGitSyncService, trigger.getUuid(), accountId, triggerYaml, trigger.getName() + YAML_EXTENSION);
  }

  /**
   * Gets the yaml for a workflow
   *
   * @param appId     the app id
   * @param workflowId the workflow id
   * @return the rest response
   */
  @Override
  public RestResponse<YamlPayload> getWorkflow(String appId, String workflowId) {
    String accountId = appService.getAccountIdByAppId(appId);
    notNullCheck("No account found for appId:" + appId, accountId);
    Workflow workflow = workflowService.readWorkflow(appId, workflowId);
    WorkflowYaml workflowYaml = (WorkflowYaml) yamlHandlerFactory
                                    .getYamlHandler(YamlType.WORKFLOW,
                                        workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType().name())
                                    .toYaml(workflow, appId);

    return YamlHelper.getYamlRestResponse(
        yamlGitSyncService, workflow.getUuid(), accountId, workflowYaml, workflow.getName() + YAML_EXTENSION);
  }

  /**
   * Gets the yaml for a ApplicationManifest
   *
   * @param appId     the app id
   * @return the rest response
   */
  public RestResponse<YamlPayload> getApplicationManifest(String appId, ApplicationManifest manifest) {
    ApplicationManifest.Yaml yaml =
        (ApplicationManifest.Yaml) yamlHandlerFactory.getYamlHandler(getYamlTypeFromAppManifest(manifest))
            .toYaml(manifest, appId);

    return YamlHelper.getYamlRestResponse(yamlGitSyncService, null, null, yaml, INDEX_YAML);
  }

  /**
   * Gets the yaml for a workflow
   *
   * @param appId     the app id
   * @param provisionerId the provisioner id
   * @return the rest response
   */
  @Override
  public RestResponse<YamlPayload> getProvisioner(String appId, String provisionerId) {
    String accountId = appService.getAccountIdByAppId(appId);
    notNullCheck("No account found for appId:" + appId, accountId);
    InfrastructureProvisioner infrastructureProvisioner = infrastructureProvisionerService.get(appId, provisionerId);
    InfraProvisionerYaml provisionerYaml =
        (InfraProvisionerYaml) yamlHandlerFactory
            .getYamlHandler(YamlType.PROVISIONER, infrastructureProvisioner.getInfrastructureProvisionerType())
            .toYaml(infrastructureProvisioner, appId);

    return YamlHelper.getYamlRestResponse(yamlGitSyncService, infrastructureProvisioner.getUuid(), accountId,
        provisionerYaml, infrastructureProvisioner.getName() + YAML_EXTENSION);
  }

  /**
   * Gets the yaml for a template library
   *
   * @param accountId  the account id
   * @param appId  the app id
   * @param templateId the template id
   * @return the rest response
   */
  @Override
  public RestResponse<YamlPayload> getTemplateLibrary(String accountId, String appId, String templateId) {
    final YamlType yamlType = TemplateYamlConfig.getInstance(appId).getYamlType();
    Template template = templateService.get(accountId, templateId, "latest");
    TemplateLibraryYaml templateLibraryYaml =
        (TemplateLibraryYaml) yamlHandlerFactory.getYamlHandler(yamlType, template.getType())
            .toYaml(template, accountId);
    return YamlHelper.getYamlRestResponse(
        yamlGitSyncService, template.getUuid(), accountId, templateLibraryYaml, template.getName() + YAML_EXTENSION);
  }

  @Override
  public RestResponse<YamlPayload> getCVConfiguration(String appId, String cvConfigId) {
    String accountId = appService.getAccountIdByAppId(appId);
    notNullCheck("No account found for appId:" + appId, accountId);

    CVConfiguration cvConfiguration = cvConfigurationService.getConfiguration(cvConfigId);
    CVConfigurationYaml cvConfigurationYaml =
        (CVConfigurationYaml) yamlHandlerFactory
            .getYamlHandler(YamlType.CV_CONFIGURATION, cvConfiguration.getStateType().name())
            .toYaml(cvConfiguration, appId);

    return YamlHelper.getYamlRestResponse(yamlGitSyncService, cvConfiguration.getUuid(), accountId, cvConfigurationYaml,
        cvConfiguration.getName() + YAML_EXTENSION);
  }

  /**
   * Gets all the setting attributes of a given type by accountId
   *
   * @param accountId   the account id
   * @param type        the SettingVariableTypes
   * @return the rest response
   */
  @Override
  public RestResponse<YamlPayload> getGlobalSettingAttributesList(String accountId, String type) {
    List<SettingAttribute> settingAttributeList = settingsService.getSettingAttributesByType(accountId, type);
    BaseYaml yaml =
        yamlHandlerFactory.getYamlHandler(YamlType.ACCOUNT_DEFAULTS).toYaml(settingAttributeList, GLOBAL_APP_ID);
    return YamlHelper.getYamlRestResponse(yamlGitSyncService, null, accountId, yaml, DEFAULTS_YAML);
  }

  /**
   * Gets the yaml version of an environment by envId
   *
   * @param appId   the app id
   * @param envId   the environment id
   * @return the rest response
   */
  @Override
  public RestResponse<YamlPayload> getEnvironment(String appId, String envId) {
    Application app = appService.get(appId);
    Environment environment = environmentService.get(appId, envId, true);
    BaseYaml yaml = yamlHandlerFactory.getYamlHandler(YamlType.ENVIRONMENT).toYaml(environment, appId);
    return YamlHelper.getYamlRestResponse(
        yamlGitSyncService, environment.getUuid(), app.getAccountId(), yaml, environment.getName() + YAML_EXTENSION);
  }

  @Override
  public RestResponse<YamlPayload> getService(String appId, String serviceId) {
    Application app = appService.get(appId);
    Service service = serviceResourceService.get(appId, serviceId, true);
    notNullCheck("Service is null for Id: " + serviceId, service);
    BaseYaml yaml = yamlHandlerFactory.getYamlHandler(YamlType.SERVICE).toYaml(service, appId);
    return YamlHelper.getYamlRestResponse(
        yamlGitSyncService, service.getUuid(), app.getAccountId(), yaml, service.getName() + YAML_EXTENSION);
  }

  /**
   * Gets the yaml version of an environment by envId
   *
   * @param accountId the account id
   * @param appId   the app id
   * @param infraMappingId   infra mapping id
   * @return the rest response
   */
  @Override
  public RestResponse<YamlPayload> getInfraMapping(String accountId, String appId, String infraMappingId) {
    InfrastructureMapping infraMapping = infraMappingService.get(appId, infraMappingId);

    BaseYaml yaml = yamlHandlerFactory.getYamlHandler(YamlType.INFRA_MAPPING, infraMapping.getInfraMappingType())
                        .toYaml(infraMapping, appId);

    return YamlHelper.getYamlRestResponse(
        yamlGitSyncService, infraMapping.getUuid(), accountId, yaml, infraMapping.getName() + YAML_EXTENSION);
  }

  @Override
  public RestResponse<YamlPayload> getInfraDefinition(String appId, String infraDefinitionId) {
    String accountId = appService.getAccountIdByAppId(appId);
    InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionService.get(appId, infraDefinitionId);
    notNullCheck("InfraDefinition not found for appId:" + appId, infrastructureDefinition);
    InfrastructureDefinitionYaml infraDefinitionYaml =
        (InfrastructureDefinitionYaml) yamlHandlerFactory.getYamlHandler(YamlType.INFRA_DEFINITION)
            .toYaml(infrastructureDefinition, appId);
    return YamlHelper.getYamlRestResponse(yamlGitSyncService, infrastructureDefinition.getUuid(), accountId,
        infraDefinitionYaml, infrastructureDefinition.getName() + YAML_EXTENSION);
  }

  @Override
  public RestResponse<YamlPayload> getContainerTask(String accountId, String appId, String containerTaskId) {
    ContainerTask containerTask = serviceResourceService.getContainerTaskById(appId, containerTaskId);
    String yamlFileName;
    String yamlSubType;
    if (DeploymentType.ECS.name().equals(containerTask.getDeploymentType())) {
      yamlSubType = DeploymentType.ECS.name();
      yamlFileName = YamlConstants.ECS_CONTAINER_TASK_YAML_FILE_NAME;
    } else if (DeploymentType.KUBERNETES.name().equals(containerTask.getDeploymentType())) {
      yamlSubType = DeploymentType.KUBERNETES.name();
      yamlFileName = YamlConstants.KUBERNETES_CONTAINER_TASK_YAML_FILE_NAME;
    } else {
      throw new InvalidRequestException("Unsupported deployment type: " + containerTask.getDeploymentType());
    }

    BaseYaml yaml =
        yamlHandlerFactory.getYamlHandler(YamlType.DEPLOYMENT_SPECIFICATION, yamlSubType).toYaml(containerTask, appId);
    return YamlHelper.getYamlRestResponse(
        yamlGitSyncService, containerTask.getUuid(), accountId, yaml, yamlFileName + YAML_EXTENSION);
  }

  @Override
  public RestResponse<YamlPayload> getHelmChartSpecification(
      String accountId, String appId, String helmChartSpecificationId) {
    HelmChartSpecification helmChartSpecification =
        serviceResourceService.getHelmChartSpecificationById(appId, helmChartSpecificationId);
    String yamlFileName;
    yamlFileName = YamlConstants.HELM_CHART_YAML_FILE_NAME;

    BaseYaml yaml = yamlHandlerFactory.getYamlHandler(YamlType.DEPLOYMENT_SPECIFICATION, DeploymentType.HELM.name())
                        .toYaml(helmChartSpecification, appId);
    return YamlHelper.getYamlRestResponse(
        yamlGitSyncService, helmChartSpecification.getUuid(), accountId, yaml, yamlFileName + YAML_EXTENSION);
  }

  @Override
  public RestResponse<YamlPayload> getEcsServiceSpecification(
      String accountId, String appId, String ecsServiceSpecificationId) {
    EcsServiceSpecification ecsServiceSpecification =
        serviceResourceService.getEcsServiceSpecificationById(appId, ecsServiceSpecificationId);
    String yamlFileName = YamlConstants.ECS_SERVICE_SPEC_YAML_FILE_NAME;

    BaseYaml yaml =
        yamlHandlerFactory.getYamlHandler(YamlType.DEPLOYMENT_SPECIFICATION, YamlHandlerFactory.ECS_SERVICE_SPEC)
            .toYaml(ecsServiceSpecification, appId);
    return YamlHelper.getYamlRestResponse(
        yamlGitSyncService, ecsServiceSpecification.getUuid(), accountId, yaml, yamlFileName + YAML_EXTENSION);
  }

  @Override
  public RestResponse<YamlPayload> getPcfServiceSpecification(
      String accountId, String appId, String pcfServiceSpecificationId) {
    PcfServiceSpecification pcfServiceSpecification =
        serviceResourceService.getPcfServiceSpecificationById(appId, pcfServiceSpecificationId);
    String yamlFileName;
    yamlFileName = YamlConstants.PCF_MANIFEST_YAML_FILE_NAME;

    BaseYaml yaml = yamlHandlerFactory.getYamlHandler(YamlType.DEPLOYMENT_SPECIFICATION, DeploymentType.PCF.name())
                        .toYaml(pcfServiceSpecification, appId);
    return YamlHelper.getYamlRestResponse(
        yamlGitSyncService, pcfServiceSpecification.getUuid(), accountId, yaml, yamlFileName + YAML_EXTENSION);
  }

  @Override
  public RestResponse<YamlPayload> getLambdaSpec(String accountId, String appId, String lambdaSpecId) {
    LambdaSpecification lambdaSpecification = serviceResourceService.getLambdaSpecificationById(appId, lambdaSpecId);

    BaseYaml yaml =
        yamlHandlerFactory.getYamlHandler(YamlType.DEPLOYMENT_SPECIFICATION, DeploymentType.AWS_LAMBDA.name())
            .toYaml(lambdaSpecification, appId);
    return YamlHelper.getYamlRestResponse(yamlGitSyncService, lambdaSpecification.getUuid(), accountId, yaml,
        YamlConstants.LAMBDA_SPEC_YAML_FILE_NAME + YAML_EXTENSION);
  }

  @Override
  public RestResponse<YamlPayload> getUserDataSpec(String accountId, String appId, String userDataSpecId) {
    UserDataSpecification userDataSpecification =
        serviceResourceService.getUserDataSpecificationById(appId, userDataSpecId);

    BaseYaml yaml = yamlHandlerFactory.getYamlHandler(YamlType.DEPLOYMENT_SPECIFICATION, DeploymentType.AMI.name())
                        .toYaml(userDataSpecification, appId);
    return YamlHelper.getYamlRestResponse(yamlGitSyncService, userDataSpecification.getUuid(), accountId, yaml,
        YamlConstants.USER_DATA_SPEC_YAML_FILE_NAME + YAML_EXTENSION);
  }

  @Override
  public RestResponse<YamlPayload> getSettingAttribute(String accountId, String uuid) {
    SettingAttribute settingAttribute = settingsService.get(uuid);
    notNullCheck("SettingAttribute is not null for:" + uuid, settingAttribute);

    SettingValueYamlHandler yamlHandler = getSettingValueYamlHandler(settingAttribute);
    BaseYaml yaml = null;
    if (yamlHandler != null) {
      // TODO check if this is true
      yaml = yamlHandler.toYaml(settingAttribute, GLOBAL_APP_ID);
    }

    return YamlHelper.getYamlRestResponse(
        yamlGitSyncService, settingAttribute.getUuid(), accountId, yaml, settingAttribute.getName() + YAML_EXTENSION);
  }

  @Override
  public RestResponse<YamlPayload> getDefaultVariables(String accountId, String appId) {
    List<SettingAttribute> settingAttributeList =
        settingsService.getSettingAttributesByType(accountId, appId, GLOBAL_ENV_ID, SettingVariableTypes.STRING.name());

    YamlType yamlType = GLOBAL_APP_ID.equals(appId) ? YamlType.ACCOUNT_DEFAULTS : YamlType.APPLICATION_DEFAULTS;
    BaseYaml yaml = yamlHandlerFactory.getYamlHandler(yamlType).toYaml(settingAttributeList, appId);
    return YamlHelper.getYamlRestResponse(yamlGitSyncService, appId, accountId, yaml, DEFAULTS_YAML);
  }

  private SettingValueYamlHandler getSettingValueYamlHandler(SettingAttribute settingAttribute) {
    SettingValue settingValue = settingAttribute.getValue();
    SettingVariableTypes settingVariableType = settingValue.getSettingType();

    if (!featureFlagService.isEnabled(FeatureName.HELM_OCI_SUPPORT, settingAttribute.getAccountId())
        && settingVariableType.equals(OCI_HELM_REPO)) {
      log.warn("Unknown SettingVariable type:" + settingVariableType);
      return null;
    }

    switch (settingVariableType) {
      // cloud providers
      case AWS:
      case GCP:
      case AZURE:
      case KUBERNETES_CLUSTER:
      case PHYSICAL_DATA_CENTER:
      case PCF:
      case SPOT_INST:
      case RANCHER:
        return yamlHandlerFactory.getYamlHandler(YamlType.CLOUD_PROVIDER, settingVariableType.name());

      // artifact servers - these don't have separate folders
      case JENKINS:
      case BAMBOO:
      case DOCKER:
      case NEXUS:
      case ARTIFACTORY:
      case ECR:
      case GCR:
      case ACR:
      case AMAZON_S3:
      case HTTP_HELM_REPO:
      case OCI_HELM_REPO:
      case AMAZON_S3_HELM_REPO:
      case GCS_HELM_REPO:
      case AZURE_ARTIFACTS_PAT:
      case SMB:
      case SFTP:
      case CUSTOM:
        return yamlHandlerFactory.getYamlHandler(YamlType.ARTIFACT_SERVER, settingVariableType.name());

      // collaboration providers
      case SMTP:
      case JIRA:
      case SERVICENOW:
        return yamlHandlerFactory.getYamlHandler(YamlType.COLLABORATION_PROVIDER, settingVariableType.name());
        // source repo providers
      case GIT:
        return yamlHandlerFactory.getYamlHandler(YamlType.SOURCE_REPO_PROVIDER, settingVariableType.name());

      // CD-7865 : Change as part of removing SLACK Collaboration provider connector
      case SLACK:
        break;
      // load balancers
      case ELB:
        return yamlHandlerFactory.getYamlHandler(YamlType.LOADBALANCER_PROVIDER, settingVariableType.name());

      // verification providers
      // JENKINS is also a (logical) part of this group
      case APP_DYNAMICS:
      case SPLUNK:
      case ELK:
      case INSTANA:
      case DATA_DOG_LOG:
      case LOGZ:
      case SUMO:
      case NEW_RELIC:
      case DYNA_TRACE:
      case PROMETHEUS:
      case DATA_DOG:
      case APM_VERIFICATION:
      case BUG_SNAG:
        return yamlHandlerFactory.getYamlHandler(YamlType.VERIFICATION_PROVIDER, settingVariableType.name());

      case HOST_CONNECTION_ATTRIBUTES:
      case BASTION_HOST_CONNECTION_ATTRIBUTES:
        break;
      case KMS:
      case VAULT:
        break;
      case SERVICE_VARIABLE:
      case CONFIG_FILE:
      case SSH_SESSION_CONFIG:
      case YAML_GIT_SYNC:
      case KUBERNETES:
      case STRING:
        break;
      default:
        log.warn("Unknown SettingVariable type:" + settingVariableType);
    }
    return null;
  }

  @Override
  public RestResponse<YamlPayload> getConfigFileYaml(String accountId, String appId, String configFileUuid) {
    ConfigFile configFile = configService.get(appId, configFileUuid);

    return getConfigFileYaml(accountId, configFile);
  }

  private RestResponse<YamlPayload> getConfigFileYaml(String accountId, ConfigFile configFile) {
    String appId = configFile.getAppId();

    if (configFile.getEntityType() == EntityType.SERVICE) {
      return getConfigFileYaml(accountId, appId, configFile);
    } else {
      return getConfigFileOverrideYaml(accountId, appId, configFile);
    }
  }

  @Override
  public RestResponse<YamlPayload> getConfigFileYaml(String accountId, String appId, ConfigFile configFile) {
    ConfigFile.Yaml yaml =
        (ConfigFile.Yaml) yamlHandlerFactory.getYamlHandler(YamlType.CONFIG_FILE).toYaml(configFile, appId);
    return YamlHelper.getYamlRestResponse(
        yamlGitSyncService, configFile.getUuid(), accountId, yaml, yaml.getFileName() + YAML_EXTENSION);
  }

  private RestResponse<YamlPayload> getConfigFileOverrideYaml(String accountId, String appId, ConfigFile configFile) {
    ConfigFile.OverrideYaml yaml =
        (ConfigFile.OverrideYaml) yamlHandlerFactory.getYamlHandler(YamlType.CONFIG_FILE_OVERRIDE)
            .toYaml(configFile, appId);
    return YamlHelper.getYamlRestResponse(
        yamlGitSyncService, configFile.getUuid(), accountId, yaml, yaml.getFileName() + YAML_EXTENSION);
  }

  private RestResponse<YamlPayload> getAppManifestYaml(String accountId, ApplicationManifest applicationManifest) {
    YamlType yamlType = getYamlTypeFromAppManifest(applicationManifest);

    ApplicationManifest.Yaml yaml = (ApplicationManifest.Yaml) yamlHandlerFactory.getYamlHandler(yamlType).toYaml(
        applicationManifest, applicationManifest.getAppId());

    return YamlHelper.getYamlRestResponse(yamlGitSyncService, applicationManifest.getUuid(), accountId, yaml,
        YamlConstants.APPLICATIONS_MANIFEST + YAML_EXTENSION);
  }

  private RestResponse<YamlPayload> getManifestFileYaml(String accountId, ManifestFile manifestFile) {
    ApplicationManifest applicationManifest =
        applicationManifestService.getById(manifestFile.getAppId(), manifestFile.getApplicationManifestId());
    YamlType yamlType = getYamlTypeFromAppManifest(applicationManifest);

    ManifestFile.Yaml yaml = (ManifestFile.Yaml) yamlHandlerFactory.getYamlHandler(yamlType).toYaml(
        applicationManifest, applicationManifest.getAppId());

    return YamlHelper.getYamlRestResponse(yamlGitSyncService, applicationManifest.getUuid(), accountId, yaml,
        manifestFile.getFileName() + YAML_EXTENSION);
  }

  @Override
  public <T> RestResponse<YamlPayload> obtainEntityYamlVersion(String accountId, T entity) {
    if (entity instanceof ArtifactStream) {
      ArtifactStream artifactStream = (ArtifactStream) entity;
      return getArtifactTrigger(artifactStream.fetchAppId(), artifactStream);
    } else if (entity instanceof ConfigFile) {
      return getConfigFileYaml(accountId, (ConfigFile) entity);
    } else if (entity instanceof SettingAttribute) {
      return getSettingAttribute(accountId, ((SettingAttribute) entity).getUuid());
    } else if (entity instanceof ServiceCommand) {
      ServiceCommand serviceCommand = (ServiceCommand) entity;
      return getServiceCommand(serviceCommand.getAppId(), serviceCommand.getUuid());
    } else if (entity instanceof ApplicationManifest) {
      return getAppManifestYaml(accountId, (ApplicationManifest) entity);
    } else if (entity instanceof ManifestFile) {
      return getManifestFileYaml(accountId, (ManifestFile) entity);
    } else if (entity instanceof Template) {
      final Template template = (Template) entity;
      return getTemplateLibrary(accountId, template.getAppId(), template.getUuid());
    }

    if (entity instanceof Base) {
      String appId = ((Base) entity).getAppId();
      String entityId = ((Base) entity).getUuid();
      // Validator.notNullCheck("No account found for appId:" + appId, accountId);

      return getYamlPayloadRestResponseForEntity(accountId, entity, appId, entityId);
    } else if (entity instanceof ApplicationAccess && entity instanceof UuidAccess) {
      String appId = ((ApplicationAccess) entity).getAppId();
      String entityId = ((UuidAccess) entity).getUuid();

      return getYamlPayloadRestResponseForEntity(accountId, entity, appId, entityId);
    }

    throw new InvalidRequestException(
        "Unhandled case while getting entity yaml version for entity type " + entity.getClass().getSimpleName());
  }

  private <T> RestResponse<YamlPayload> getYamlPayloadRestResponseForEntity(
      String accountId, T entity, String appId, String entityId) {
    entity = preProcessEntity(appId, entityId, entity);
    YamlType yamlType = yamlHandlerFactory.obtainEntityYamlType(entity);
    String entityName = yamlHandlerFactory.obtainEntityName(entity);
    String yamlHandlerSubType = yamlHandlerFactory.obtainYamlHandlerSubtype(entity);
    BaseYaml yaml = yamlHandlerFactory.getYamlHandler(yamlType, yamlHandlerSubType).toYaml(entity, appId);

    return YamlHelper.getYamlRestResponse(yamlGitSyncService, entityId, accountId, yaml, entityName + YAML_EXTENSION);
  }

  private <T> T preProcessEntity(String appId, String entityId, T entity) {
    if (entity instanceof Environment) {
      return (T) environmentService.get(appId, entityId, true);
    } else if (entity instanceof Pipeline) {
      return (T) pipelineService.readPipeline(appId, entityId, false);
    } else if (entity instanceof Workflow) {
      return (T) workflowService.readWorkflow(appId, entityId);
    } else if (entity instanceof Service) {
      return (T) serviceResourceService.get(appId, entityId, true);
    }

    return entity;
  }

  @Override
  @Nonnull
  public YamlType getYamlTypeFromAppManifest(@Nonnull ApplicationManifest applicationManifest) {
    AppManifestSource appManifestSource = applicationManifestService.getAppManifestType(applicationManifest);

    switch (appManifestSource) {
      case SERVICE:
        return APPLICATION_MANIFEST;
      case ENV:
        return getYamlTypeForEnvOverrideAllServices(applicationManifest);
      case ENV_SERVICE:
        return getYamlTypeForEnvServiceOverride(applicationManifest);
      default:
        unhandled(appManifestSource);
        throw new WingsException("Unhandled app manifest type");
    }
  }

  private YamlType getYamlTypeForEnvServiceOverride(ApplicationManifest applicationManifest) {
    notNullCheck("ApplicationManifest Kind can not be null", applicationManifest.getKind());

    switch (applicationManifest.getKind()) {
      case VALUES:
        return APPLICATION_MANIFEST_VALUES_ENV_SERVICE_OVERRIDE;
      case PCF_OVERRIDE:
        return APPLICATION_MANIFEST_PCF_ENV_SERVICE_OVERRIDE;
      case HELM_CHART_OVERRIDE:
        return APPLICATION_MANIFEST_HELM_ENV_SERVICE_OVERRIDE;
      case OC_PARAMS:
        return APPLICATION_MANIFEST_OC_PARAMS_ENV_SERVICE_OVERRIDE;
      case KUSTOMIZE_PATCHES:
        return APPLICATION_MANIFEST_KUSTOMIZE_PATCHES_ENV_SERVICE_OVERRIDE;
      case AZURE_APP_SETTINGS_OVERRIDE:
        return APPLICATION_MANIFEST_APP_SETTINGS_ENV_SERVICE_OVERRIDE;
      case AZURE_CONN_STRINGS_OVERRIDE:
        return APPLICATION_MANIFEST_CONN_STRINGS_ENV_SERVICE_OVERRIDE;
      default:
        throw new UnexpectedException("Invalid ApplicationManifestKind: " + applicationManifest.getKind());
    }
  }

  private YamlType getYamlTypeForEnvOverrideAllServices(ApplicationManifest applicationManifest) {
    notNullCheck("ApplicationManifest can not be null", applicationManifest);
    notNullCheck("ApplicationManifest Kind can not be null", applicationManifest.getKind());

    switch (applicationManifest.getKind()) {
      case VALUES:
        return APPLICATION_MANIFEST_VALUES_ENV_OVERRIDE;
      case PCF_OVERRIDE:
        return APPLICATION_MANIFEST_PCF_OVERRIDES_ALL_SERVICE;
      case HELM_CHART_OVERRIDE:
        return APPLICATION_MANIFEST_HELM_OVERRIDES_ALL_SERVICE;
      case OC_PARAMS:
        return APPLICATION_MANIFEST_OC_PARAMS_ENV_OVERRIDE;
      case KUSTOMIZE_PATCHES:
        return APPLICATION_MANIFEST_KUSTOMIZE_PATCHES_ENV_OVERRIDE;
      case AZURE_APP_SETTINGS_OVERRIDE:
        return APPLICATION_MANIFEST_APP_SETTINGS_ENV_OVERRIDE;
      case AZURE_CONN_STRINGS_OVERRIDE:
        return APPLICATION_MANIFEST_CONN_STRINGS_ENV_OVERRIDE;
      default:
        throw new UnexpectedException("Invalid ApplicationManifestKind: " + applicationManifest.getKind());
    }
  }

  @Override
  public RestResponse<YamlPayload> getHarnessTags(String accountId) {
    List<HarnessTag> harnessTags = harnessTagService.listTags(accountId);
    BaseYaml yaml = yamlHandlerFactory.getYamlHandler(YamlType.TAG).toYaml(harnessTags, GLOBAL_APP_ID);

    return YamlHelper.getYamlRestResponse(yamlGitSyncService, GLOBAL_APP_ID, accountId, yaml, TAGS_YAML);
  }

  /**
   * Get YAML for Governance Config for an account
   *
   * @param accountId     the account id
   * @return Governance Config yaml
   */
  @Override
  public RestResponse<YamlPayload> getGovernanceConfig(@NotEmpty String accountId) {
    notNullCheck("No account found for Id:" + accountId, accountId);
    GovernanceConfig governanceConfig = governanceConfigService.get(accountId);

    if (governanceConfig != null) {
      GovernanceConfig.Yaml yaml = (GovernanceConfig.Yaml) yamlHandlerFactory.getYamlHandler(YamlType.GOVERNANCE_CONFIG)
                                       .toYaml(governanceConfig, accountId);
      return YamlHelper.getYamlRestResponse(
          yamlGitSyncService, accountId, accountId, yaml, YamlConstants.DEPLOYMENT_GOVERNANCE_FOLDER + YAML_EXTENSION);

    } else {
      throw new YamlException("The Governance Config with accountId: '" + accountId + "' was not found!", USER);
    }
  }
  @Override
  public RestResponse<YamlPayload> getEventConfig(String appId, String eventConfigId) {
    notNullCheck("No app found for Id:" + appId, appId);
    String accountId = appService.getAccountIdByAppId(appId);
    CgEventConfig cgEventConfig = eventConfigService.getEventsConfig(accountId, appId, eventConfigId);
    if (cgEventConfig == null) {
      throw new YamlException("The EventConfig with eventConfigId: '" + eventConfigId + "' was not found!", USER);
    }
    CgEventConfig.Yaml yaml =
        (CgEventConfig.Yaml) yamlHandlerFactory.getYamlHandler(EVENT_RULE).toYaml(cgEventConfig, appId);
    return YamlHelper.getYamlRestResponse(
        yamlGitSyncService, cgEventConfig.getUuid(), accountId, yaml, cgEventConfig.getName() + YAML_EXTENSION);
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.yaml.YamlConstants.DEFAULTS_YAML;
import static software.wings.beans.yaml.YamlConstants.ECS_CONTAINER_TASK_YAML_FILE_NAME;
import static software.wings.beans.yaml.YamlConstants.ECS_SERVICE_SPEC_YAML_FILE_NAME;
import static software.wings.beans.yaml.YamlConstants.INDEX_YAML;
import static software.wings.beans.yaml.YamlConstants.KUBERNETES_CONTAINER_TASK_YAML_FILE_NAME;
import static software.wings.beans.yaml.YamlConstants.LAMBDA_SPEC_YAML_FILE_NAME;
import static software.wings.beans.yaml.YamlConstants.PCF_MANIFEST_YAML_FILE_NAME;
import static software.wings.beans.yaml.YamlConstants.TAGS_YAML;
import static software.wings.beans.yaml.YamlConstants.USER_DATA_SPEC_YAML_FILE_NAME;
import static software.wings.beans.yaml.YamlConstants.YAML_EXTENSION;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.FeatureName;
import io.harness.beans.SecretManagerConfig;
import io.harness.context.GlobalContextData;
import io.harness.dashboard.DashboardSettings;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateScope;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.globalcontex.AuditGlobalContextData;
import io.harness.globalcontex.EntityOperationIdentifier;
import io.harness.globalcontex.EntityOperationIdentifier.EntityOperation;
import io.harness.globalcontex.PurgeGlobalContextData;
import io.harness.governance.pipeline.service.model.PipelineGovernanceConfig;
import io.harness.manage.GlobalContextManager;
import io.harness.persistence.UuidAccess;

import software.wings.audit.EntityAuditRecord;
import software.wings.audit.EntityAuditRecord.EntityAuditRecordBuilder;
import software.wings.audit.ResourceType;
import software.wings.beans.Account;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.CGConstants;
import software.wings.beans.ConfigFile;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.beans.Event.Type;
import software.wings.beans.HarnessTag;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.LambdaSpecification;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.ServiceVariable;
import software.wings.beans.SettingAttribute;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.beans.Workflow;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.container.EcsContainerTask;
import software.wings.beans.container.EcsServiceSpecification;
import software.wings.beans.container.HelmChartSpecification;
import software.wings.beans.container.KubernetesContainerTask;
import software.wings.beans.container.PcfServiceSpecification;
import software.wings.beans.container.UserDataSpecification;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.beans.loginSettings.LoginSettings;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.access.Whitelist;
import software.wings.beans.sso.SSOSettings;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateFolder;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.yaml.YamlConstants;
import software.wings.dl.WingsPersistence;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.verification.CVConfiguration;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._940_CG_AUDIT_SERVICE)
public class EntityHelper {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private YamlHelper yamlHelper;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;

  private static final String DEPLOYMENT_SPECIFICATION_YAML_PATH_FORMAT =
      "Setup/Application/%s/Services/%s/Deloyment Specifications/%s.yaml";
  private static final String COMMANDS_YAML_PATH_FORMAT = "Setup/Application/%s/Services/%s/Commands/%s.yaml";
  private static final String STRENGTH_POLICY = "Password Strength Policy";
  private static final String EXPIRATION_POLICY = "Password Expiration Policy";
  private static final String LDAP_SETTINGS = "LDAP_SETTINGS";
  private static final String OAUTH_SETTINGS = "OAUTH_SETTINGS";
  private static final String SAML_SETTINGS = "SAML_SETTINGS";

  // Some Setting Attributes defined that do not have Yaml
  static final String SSH_KEYS_ENTITY_TYPE = "Host Connection Attributes";
  static final String WIN_RM_CONNECTION_ENTITY_TYPE = "Win Rm Connection Attributes";

  public <T extends UuidAccess> void loadMetaDataForEntity(T entity, EntityAuditRecordBuilder builder, Type type) {
    String entityName = EMPTY;
    String entityId = entity.getUuid();
    String entityType = EMPTY;
    String appId = "__GLOBAL_APP_ID__";
    String appName = EMPTY;
    String affectedResourceId = EMPTY;
    String affectedResourceName = EMPTY;
    String affectedResourceType = EMPTY;

    String affectedResourceOperation = Type.UPDATE.name();

    if (entity instanceof Environment) {
      Environment environment = (Environment) entity;
      entityType = EntityType.ENVIRONMENT.name();
      entityName = environment.getName();
      appId = environment.getAppId();
      affectedResourceId = environment.getUuid();
      affectedResourceName = environment.getName();
      affectedResourceType = EntityType.ENVIRONMENT.name();
      affectedResourceOperation = type.name();
    } else if (entity instanceof ApiKeyEntry) {
      ApiKeyEntry apiKeyEntry = (ApiKeyEntry) entity;
      entityType = EntityType.API_KEY.name();
      entityName = apiKeyEntry.getName();
      affectedResourceId = apiKeyEntry.getUuid();
      affectedResourceName = apiKeyEntry.getName();
      affectedResourceType = entityType;
      affectedResourceOperation = type.name();
    } else if (entity instanceof Delegate) {
      Delegate delegate = (Delegate) entity;
      entityType = EntityType.DELEGATE.name();
      entityName = delegate.getHostName();
      affectedResourceId = delegate.getUuid();
      affectedResourceName = delegate.getHostName();
      affectedResourceType = entityType;
      affectedResourceOperation = type.name();
    } else if (entity instanceof DelegateScope) {
      DelegateScope delegateScope = (DelegateScope) entity;
      entityType = EntityType.DELEGATE_SCOPE.name();
      entityName = delegateScope.getName();
      affectedResourceId = delegateScope.getUuid();
      affectedResourceName = delegateScope.getName();
      affectedResourceType = entityType;
      affectedResourceOperation = type.name();
    } else if (entity instanceof DelegateProfile) {
      DelegateProfile delegateProfile = (DelegateProfile) entity;
      entityType = EntityType.DELEGATE_PROFILE.name();
      entityName = delegateProfile.getName();
      affectedResourceId = delegateProfile.getUuid();
      affectedResourceName = delegateProfile.getName();
      affectedResourceType = entityType;
      affectedResourceOperation = type.name();
    } else if (entity instanceof UserGroup) {
      UserGroup userGroup = (UserGroup) entity;
      entityType = EntityType.USER_GROUP.name();
      entityName = userGroup.getName();
      affectedResourceId = userGroup.getUuid();
      affectedResourceName = userGroup.getName();
      affectedResourceType = entityType;
      affectedResourceOperation = type.name();
      log.info("Auditing user group. User Group : {}. Operation : {}.", entityName, affectedResourceOperation);
    } else if (entity instanceof Whitelist) {
      Whitelist whitelist = (Whitelist) entity;
      entityType = EntityType.WHITELISTED_IP.name();
      entityName = whitelist.getFilter();
      affectedResourceId = whitelist.getUuid();
      affectedResourceName = whitelist.getFilter();
      affectedResourceType = entityType;
      affectedResourceOperation = type.name();
    } else if (entity instanceof User) {
      User user = (User) entity;
      entityType = EntityType.USER.name();
      entityName = user.getName();
      affectedResourceId = user.getUuid();
      affectedResourceName = user.getName();
      affectedResourceType = entityType;
      affectedResourceOperation = type.name();
      log.info("Auditing user. User : {}. Operation : {}.", entityName, affectedResourceOperation);
    } else if (entity instanceof Account) {
      Account account = (Account) entity;
      entityType = EntityType.ACCOUNT.name();
      entityName = account.getAccountName();
      affectedResourceId = account.getUuid();
      affectedResourceName = account.getAccountName();
      affectedResourceType = entityType;
      affectedResourceOperation = type.name();
      log.info("Auditing account. Account : {}. Operation : {}.", entityName, affectedResourceOperation);
    } else if (entity instanceof UserInvite) {
      UserInvite userInvite = (UserInvite) entity;
      entityType = EntityType.USER_INVITE.name();
      entityName = userInvite.getEmail();
      affectedResourceId = userInvite.getUuid();
      affectedResourceName = userInvite.getEmail();
      affectedResourceType = entityType;
      affectedResourceOperation = type.name();
    } else if (entity instanceof Pipeline) {
      Pipeline pipeline = (Pipeline) entity;
      entityType = EntityType.PIPELINE.name();
      entityName = pipeline.getName();
      appId = pipeline.getAppId();
      affectedResourceId = pipeline.getUuid();
      affectedResourceName = pipeline.getName();
      affectedResourceType = EntityType.PIPELINE.name();
      affectedResourceOperation = type.name();
    } else if (entity instanceof Application) {
      Application application = (Application) entity;
      entityType = EntityType.APPLICATION.name();
      entityName = application.getName();
      appId = application.getAppId();
      affectedResourceId = application.getUuid();
      affectedResourceName = application.getName();
      affectedResourceType = EntityType.APPLICATION.name();
      affectedResourceOperation = type.name();
    } else if (entity instanceof InfrastructureMapping) {
      InfrastructureMapping mapping = (InfrastructureMapping) entity;
      entityType = EntityType.INFRASTRUCTURE_MAPPING.name();
      entityName = mapping.getName();
      appId = mapping.getAppId();
      affectedResourceId = mapping.getEnvId();
      affectedResourceName = getEnvironmentName(mapping.getEnvId(), appId);
      affectedResourceType = EntityType.ENVIRONMENT.name();
      affectedResourceOperation =
          getAffectedResourceOperation(EntityType.ENVIRONMENT, affectedResourceId, affectedResourceName);
    } else if (entity instanceof InfrastructureDefinition) {
      final InfrastructureDefinition infrastructureDefinition = (InfrastructureDefinition) entity;
      entityType = EntityType.INFRASTRUCTURE_DEFINITION.name();
      entityName = infrastructureDefinition.getName();
      appId = infrastructureDefinition.getAppId();
      affectedResourceId = infrastructureDefinition.getEnvId();
      affectedResourceName = getEnvironmentName(infrastructureDefinition.getEnvId(), appId);
      affectedResourceType = EntityType.ENVIRONMENT.name();
      affectedResourceOperation =
          getAffectedResourceOperation(EntityType.ENVIRONMENT, affectedResourceId, affectedResourceName);
    } else if (entity instanceof SSOSettings) {
      SSOSettings ssoSettings = (SSOSettings) entity;
      entityName = ssoSettings.getDisplayName();
      affectedResourceId = ssoSettings.getUuid();
      affectedResourceName = ssoSettings.getDisplayName();
      affectedResourceType = EntityType.SSO_SETTINGS.name();
      switch (ssoSettings.getType()) {
        case LDAP:
          entityType = LDAP_SETTINGS;
          break;
        case SAML:
          entityType = SAML_SETTINGS;
          break;
        case OAUTH:
          entityType = OAUTH_SETTINGS;
          break;
        default:
          break;
      }
      affectedResourceOperation = type.name();
    } else if (entity instanceof Workflow) {
      Workflow workflow = (Workflow) entity;
      entityType = EntityType.WORKFLOW.name();
      entityName = workflow.getName();
      appId = workflow.getAppId();
      affectedResourceId = workflow.getUuid();
      affectedResourceName = workflow.getName();
      affectedResourceType = EntityType.WORKFLOW.name();
      affectedResourceOperation = type.name();
    } else if (entity instanceof InfrastructureProvisioner) {
      InfrastructureProvisioner provisioner = (InfrastructureProvisioner) entity;
      entityType = EntityType.PROVISIONER.name();
      entityName = provisioner.getName();
      appId = provisioner.getAppId();
      affectedResourceId = provisioner.getUuid();
      affectedResourceName = provisioner.getName();
      affectedResourceType = EntityType.PROVISIONER.name();
      affectedResourceOperation = type.name();
    } else if (entity instanceof Trigger) {
      Trigger trigger = (Trigger) entity;
      entityType = EntityType.TRIGGER.name();
      entityName = trigger.getName();
      appId = trigger.getAppId();
      affectedResourceId = trigger.getUuid();
      affectedResourceName = trigger.getName();
      affectedResourceType = EntityType.TRIGGER.name();
      affectedResourceOperation = type.name();
    } else if (entity instanceof ArtifactStream) {
      ArtifactStream artifactStream = (ArtifactStream) entity;
      entityType = EntityType.ARTIFACT_STREAM.name();
      entityName = artifactStream.getName();
      appId = artifactStream.fetchAppId();
      // TODO: ASR: maybe change this to use setting_id
      Service service = artifactStreamServiceBindingService.getService(appId, artifactStream.getUuid(), false);
      if (service == null) {
        affectedResourceId = "";
        affectedResourceName = "";
      } else {
        affectedResourceId = service.getUuid();
        affectedResourceName = service.getName();
      }
      affectedResourceType = EntityType.SERVICE.name();
      affectedResourceOperation =
          getAffectedResourceOperation(EntityType.SERVICE, affectedResourceId, affectedResourceName);
    } else if (entity instanceof Service) {
      Service service = (Service) entity;
      entityType = EntityType.SERVICE.name();
      entityName = service.getName();
      appId = service.getAppId();
      affectedResourceId = service.getUuid();
      affectedResourceName = service.getName();
      affectedResourceType = EntityType.SERVICE.name();
      affectedResourceOperation = type.name();
    } else if (entity instanceof HelmChartSpecification) {
      HelmChartSpecification chartSpecification = (HelmChartSpecification) entity;
      entityType = EntityType.HELM_CHART_SPECIFICATION.name();
      entityName = chartSpecification.getChartName();
      appId = chartSpecification.getAppId();
      affectedResourceId = chartSpecification.getServiceId();
      affectedResourceName = getServiceName(chartSpecification.getServiceId(), appId);
      affectedResourceType = EntityType.SERVICE.name();
      affectedResourceOperation =
          getAffectedResourceOperation(EntityType.SERVICE, affectedResourceId, affectedResourceName);
    } else if (entity instanceof PcfServiceSpecification) {
      PcfServiceSpecification serviceSpecification = (PcfServiceSpecification) entity;
      entityType = EntityType.PCF_SERVICE_SPECIFICATION.name();
      entityName = PCF_MANIFEST_YAML_FILE_NAME;
      appId = serviceSpecification.getAppId();
      affectedResourceId = serviceSpecification.getServiceId();
      affectedResourceName = getServiceName(serviceSpecification.getServiceId(), appId);
      affectedResourceType = EntityType.SERVICE.name();
      affectedResourceOperation =
          getAffectedResourceOperation(EntityType.SERVICE, affectedResourceId, affectedResourceName);
    } else if (entity instanceof LambdaSpecification) {
      LambdaSpecification lambdaSpecification = (LambdaSpecification) entity;
      entityType = EntityType.LAMBDA_SPECIFICATION.name();
      entityName = LAMBDA_SPEC_YAML_FILE_NAME;
      appId = lambdaSpecification.getAppId();
      affectedResourceId = lambdaSpecification.getServiceId();
      affectedResourceName = getServiceName(lambdaSpecification.getServiceId(), appId);
      affectedResourceType = EntityType.SERVICE.name();
      affectedResourceOperation =
          getAffectedResourceOperation(EntityType.SERVICE, affectedResourceId, affectedResourceName);
    } else if (entity instanceof UserDataSpecification) {
      UserDataSpecification dataSpecification = (UserDataSpecification) entity;
      entityType = EntityType.USER_DATA_SPECIFICATION.name();
      entityName = USER_DATA_SPEC_YAML_FILE_NAME;
      appId = dataSpecification.getAppId();
      affectedResourceId = dataSpecification.getServiceId();
      affectedResourceName = getServiceName(dataSpecification.getServiceId(), appId);
      affectedResourceType = EntityType.SERVICE.name();
      affectedResourceOperation =
          getAffectedResourceOperation(EntityType.SERVICE, affectedResourceId, affectedResourceName);
    } else if (entity instanceof EcsContainerTask) {
      EcsContainerTask task = (EcsContainerTask) entity;
      entityType = EntityType.ECS_CONTAINER_SPECIFICATION.name();
      entityName = ECS_CONTAINER_TASK_YAML_FILE_NAME;
      appId = task.getAppId();
      affectedResourceId = task.getServiceId();
      affectedResourceName = getServiceName(task.getServiceId(), appId);
      affectedResourceType = EntityType.SERVICE.name();
      affectedResourceOperation =
          getAffectedResourceOperation(EntityType.SERVICE, affectedResourceId, affectedResourceName);
    } else if (entity instanceof EcsServiceSpecification) {
      EcsServiceSpecification ecsServiceSpecification = (EcsServiceSpecification) entity;
      entityType = EntityType.ECS_SERVICE_SPECIFICATION.name();
      entityName = ECS_SERVICE_SPEC_YAML_FILE_NAME;
      appId = ecsServiceSpecification.getAppId();
      affectedResourceId = ecsServiceSpecification.getServiceId();
      affectedResourceName = getServiceName(ecsServiceSpecification.getServiceId(), appId);
      affectedResourceType = EntityType.SERVICE.name();
      affectedResourceOperation =
          getAffectedResourceOperation(EntityType.SERVICE, affectedResourceId, affectedResourceName);
    } else if (entity instanceof KubernetesContainerTask) {
      KubernetesContainerTask task = (KubernetesContainerTask) entity;
      entityType = EntityType.K8S_CONTAINER_SPECIFICATION.name();
      entityName = KUBERNETES_CONTAINER_TASK_YAML_FILE_NAME;
      appId = task.getAppId();
      affectedResourceId = task.getServiceId();
      affectedResourceName = getServiceName(task.getServiceId(), appId);
      affectedResourceType = EntityType.SERVICE.name();
      affectedResourceOperation =
          getAffectedResourceOperation(EntityType.SERVICE, affectedResourceId, affectedResourceName);
    } else if (entity instanceof ConfigFile) {
      ConfigFile configFile = (ConfigFile) entity;
      entityType = EntityType.CONFIG_FILE.name();
      entityName = configFile.getRelativeFilePath();
      appId = configFile.getAppId();
      String envId = configFile.getEnvId();
      if (CGConstants.GLOBAL_ENV_ID.equals(envId)) {
        EntityType entityTypeForFile = configFile.getEntityType();
        affectedResourceId = configFile.getEntityId();
        // Config file defined in service
        if (EntityType.SERVICE == entityTypeForFile) {
          affectedResourceName = getServiceName(affectedResourceId, appId);
          affectedResourceType = EntityType.SERVICE.name();
          affectedResourceOperation =
              getAffectedResourceOperation(EntityType.SERVICE, affectedResourceId, affectedResourceName);
        } else if (EntityType.ENVIRONMENT == entityTypeForFile) {
          // Config file override in ENV for all services
          affectedResourceName = getEnvironmentName(affectedResourceId, appId);
          affectedResourceType = EntityType.ENVIRONMENT.name();
          affectedResourceOperation =
              getAffectedResourceOperation(EntityType.ENVIRONMENT, affectedResourceId, affectedResourceName);
        }
      } else {
        // Config file override in ENV for specific service.
        affectedResourceId = envId;
        affectedResourceName = getEnvironmentName(envId, appId);
        affectedResourceType = EntityType.ENVIRONMENT.name();
        affectedResourceOperation =
            getAffectedResourceOperation(EntityType.ENVIRONMENT, affectedResourceId, affectedResourceName);
      }
    } else if (entity instanceof LoginSettings) {
      LoginSettings loginSettings = (LoginSettings) entity;
      entityType = EntityType.LOGIN_SETTINGS.name();
      affectedResourceId = loginSettings.getUuid();
      if (loginSettings.getPasswordExpirationPolicy() == null) {
        entityName = STRENGTH_POLICY;
        affectedResourceName = entityName;
      }
      if (loginSettings.getPasswordStrengthPolicy() == null) {
        entityName = EXPIRATION_POLICY;
        affectedResourceName = entityName;
      }
      affectedResourceType = EntityType.LOGIN_SETTINGS.name();
      affectedResourceOperation = type.name();
    } else if (entity instanceof SettingAttribute) {
      SettingAttribute settingAttribute = (SettingAttribute) entity;
      SettingValue value = settingAttribute.getValue();
      entityType = getEntityTypeForSettingValue(value);
      entityName = settingAttribute.getName();
      if (value != null && SettingVariableTypes.STRING.name().equals(value.getType())) {
        // For String value, appId != __GLOBAL_APP_ID__
        // We need this appId for Yaml handling
        appId = settingAttribute.getAppId();
      } else {
        appId = CGConstants.GLOBAL_APP_ID;
      }
      affectedResourceId = settingAttribute.getUuid();
      affectedResourceName = settingAttribute.getName();
      affectedResourceType = getAffectedResourceTypeForSettingValue(value);
      affectedResourceOperation = type.name();
    } else if (entity instanceof ServiceCommand) {
      ServiceCommand serviceCommand = (ServiceCommand) entity;
      entityType = EntityType.SERVICE_COMMAND.name();
      entityName = serviceCommand.getName();
      appId = serviceCommand.getAppId();
      affectedResourceId = serviceCommand.getServiceId();
      affectedResourceName = getServiceName(serviceCommand.getServiceId(), appId);
      affectedResourceType = EntityType.SERVICE.name();
      affectedResourceOperation =
          getAffectedResourceOperation(EntityType.SERVICE, affectedResourceId, affectedResourceName);
    } else if (entity instanceof ManifestFile) {
      ManifestFile manifestFile = (ManifestFile) entity;
      entityType = EntityType.MANIFEST_FILE.name();
      entityName = manifestFile.getFileName();
      appId = manifestFile.getAppId();
      ApplicationManifest manifest =
          wingsPersistence.get(ApplicationManifest.class, manifestFile.getApplicationManifestId());
      if (manifest != null) {
        String envId = manifest.getEnvId();
        if (isNotEmpty(envId)) {
          affectedResourceId = envId;
          affectedResourceName = getEnvironmentName(envId, appId);
          affectedResourceType = EntityType.ENVIRONMENT.name();
          affectedResourceOperation =
              getAffectedResourceOperation(EntityType.ENVIRONMENT, affectedResourceId, affectedResourceName);
        } else {
          String serviceId = manifest.getServiceId();
          if (isNotEmpty(serviceId)) {
            affectedResourceId = serviceId;
            affectedResourceName = getServiceName(serviceId, appId);
            affectedResourceType = EntityType.SERVICE.name();
            affectedResourceOperation =
                getAffectedResourceOperation(EntityType.SERVICE, affectedResourceId, affectedResourceName);
          }
        }
      }
    } else if (entity instanceof ApplicationManifest) {
      ApplicationManifest applicationManifest = (ApplicationManifest) entity;
      entityType = EntityType.APPLICATION_MANIFEST.name();
      entityName = format("Application Manifest: [%s]", applicationManifest.getKind().name());
      appId = applicationManifest.getAppId();
      String envId = applicationManifest.getEnvId();
      if (isNotEmpty(envId)) {
        affectedResourceId = envId;
        affectedResourceName = getEnvironmentName(envId, appId);
        affectedResourceType = EntityType.ENVIRONMENT.name();
        affectedResourceOperation =
            getAffectedResourceOperation(EntityType.ENVIRONMENT, affectedResourceId, affectedResourceName);
      } else {
        String serviceId = applicationManifest.getServiceId();
        if (isNotEmpty(serviceId)) {
          affectedResourceId = serviceId;
          affectedResourceName = getServiceName(serviceId, appId);
          affectedResourceType = EntityType.SERVICE.name();
          affectedResourceOperation =
              getAffectedResourceOperation(EntityType.SERVICE, affectedResourceId, affectedResourceName);
        }
      }
    } else if (entity instanceof ServiceVariable) {
      ServiceVariable variable = (ServiceVariable) entity;
      entityType = EntityType.SERVICE_VARIABLE.name();
      entityName = variable.getName();
      appId = variable.getAppId();
      String envId = variable.getEnvId();
      if (CGConstants.GLOBAL_ENV_ID.equals(envId)) {
        EntityType entityTypeForVariable = variable.getEntityType();
        if (EntityType.SERVICE == entityTypeForVariable) {
          affectedResourceId = variable.getEntityId();
          affectedResourceName = getServiceName(affectedResourceId, appId);
          affectedResourceOperation =
              getAffectedResourceOperation(EntityType.SERVICE, affectedResourceId, affectedResourceName);
          affectedResourceType = EntityType.SERVICE.name();
        } else if (EntityType.ENVIRONMENT == entityTypeForVariable) {
          affectedResourceId = variable.getEntityId();
          affectedResourceName = getEnvironmentName(affectedResourceId, appId);
          affectedResourceType = EntityType.ENVIRONMENT.name();
          affectedResourceOperation =
              getAffectedResourceOperation(EntityType.ENVIRONMENT, affectedResourceId, affectedResourceName);
        }
      } else {
        affectedResourceId = envId;
        affectedResourceName = getEnvironmentName(envId, appId);
        affectedResourceType = EntityType.ENVIRONMENT.name();
        affectedResourceOperation =
            getAffectedResourceOperation(EntityType.ENVIRONMENT, affectedResourceId, affectedResourceName);
      }
    } else if (entity instanceof Template) {
      Template template = (Template) entity;
      entityType = EntityType.TEMPLATE.name();
      entityName = template.getName();
      appId = template.getAppId();
      affectedResourceId = template.getUuid();
      affectedResourceName = template.getName();
      affectedResourceType = EntityType.TEMPLATE.name();
      affectedResourceOperation = type.name();
    } else if (entity instanceof TemplateFolder) {
      TemplateFolder templateFolder = (TemplateFolder) entity;
      entityType = EntityType.TEMPLATE_FOLDER.name();
      entityName = templateFolder.getName();
      appId = templateFolder.getAppId();
      affectedResourceId = templateFolder.getUuid();
      affectedResourceName = templateFolder.getName();
      affectedResourceType = EntityType.TEMPLATE_FOLDER.name();
      affectedResourceOperation = type.name();
    } else if (entity instanceof EncryptedData) {
      EncryptedData encryptedData = (EncryptedData) entity;
      entityType = encryptedData.getType().name();
      entityName = encryptedData.getName();
      affectedResourceId = encryptedData.getUuid();
      affectedResourceName = encryptedData.getName();
      affectedResourceType = EntityType.ENCRYPTED_RECORDS.name();
      affectedResourceOperation = type.name();
    } else if (entity instanceof CVConfiguration) {
      CVConfiguration cvConfiguration = (CVConfiguration) entity;
      entityType = EntityType.CV_CONFIGURATION.name();
      entityName = cvConfiguration.getName();
      appId = cvConfiguration.getAppId();
      affectedResourceId = cvConfiguration.getEnvId();
      affectedResourceName = getEnvironmentName(cvConfiguration.getEnvId(), appId);
      affectedResourceType = EntityType.ENVIRONMENT.name();
      affectedResourceOperation =
          getAffectedResourceOperation(EntityType.ENVIRONMENT, affectedResourceId, affectedResourceName);
    } else if (entity instanceof GovernanceConfig) {
      GovernanceConfig config = (GovernanceConfig) entity;
      entityType = ResourceType.DEPLOYMENT_FREEZE.name();
      appId = CGConstants.GLOBAL_APP_ID;
      affectedResourceId = config.getUuid();
      affectedResourceType = entityType;
      affectedResourceOperation = type.name();
    } else if (entity instanceof PipelineGovernanceConfig) {
      PipelineGovernanceConfig config = (PipelineGovernanceConfig) entity;
      entityType = EntityType.PIPELINE_GOVERNANCE_STANDARD.name();
      entityName = config.getName();
      appId = CGConstants.GLOBAL_APP_ID;
      affectedResourceId = config.getUuid();
      affectedResourceType = entityType;
      affectedResourceName = config.getName();
      affectedResourceOperation = type.name();
    } else if (entity instanceof HarnessTag) {
      HarnessTag harnessTag = (HarnessTag) entity;
      entityType = ResourceType.TAG.name();
      entityName = harnessTag.getKey();
      appId = CGConstants.GLOBAL_APP_ID;
      affectedResourceId = harnessTag.getUuid();
      affectedResourceName = ResourceType.TAG.name();
      affectedResourceType = ResourceType.TAG.name();
      affectedResourceOperation = type.name();
    } else if (entity instanceof DashboardSettings) {
      DashboardSettings dashboardSettings = (DashboardSettings) entity;
      entityType = ResourceType.CUSTOM_DASHBOARD.name();
      entityName = dashboardSettings.getName();
      appId = CGConstants.GLOBAL_APP_ID;
      affectedResourceId = dashboardSettings.getUuid();
      affectedResourceName = ResourceType.CUSTOM_DASHBOARD.name();
      affectedResourceType = ResourceType.CUSTOM_DASHBOARD.name();
      affectedResourceOperation = type.name();
    } else if (entity instanceof SecretManagerConfig) {
      SecretManagerConfig secretManagerConfig = (SecretManagerConfig) entity;
      entityType = ResourceType.SECRET_MANAGER.name();
      entityName = secretManagerConfig.getName();
      appId = CGConstants.GLOBAL_APP_ID;
      affectedResourceId = secretManagerConfig.getUuid();
      affectedResourceName = ResourceType.SECRET_MANAGER.name();
      affectedResourceType = ResourceType.SECRET_MANAGER.name();
      affectedResourceOperation = type.name();
    } else {
      log.error("Unhandled class for auditing: [{}]", entity.getClass().getSimpleName());
      entityType = format("Object of class: [%s]", entity.getClass().getSimpleName());
      entityName = format("Name of class: [%s]", entity.getClass().getSimpleName());
    }

    if (isNotEmpty(appId) && !CGConstants.GLOBAL_APP_ID.equals(appId)) {
      appName = getApplicationName(appId);
    }

    builder.entityId(entityId)
        .entityName(entityName)
        .entityType(entityType)
        .appId(appId)
        .appName(appName)
        .operationType(type.name())
        .affectedResourceId(affectedResourceId)
        .affectedResourceName(affectedResourceName)
        .affectedResourceType(affectedResourceType)
        .affectedResourceOperation(affectedResourceOperation);
  }

  private String getAffectedResourceTypeForSettingValue(SettingValue value) {
    if (value == null) {
      return "Setting_Attribute";
    }

    return value.fetchResourceCategory();
  }

  private String getAffectedResourceOperation(
      EntityType entityType, String affectedResourceId, String affectedResourceName) {
    if (isPurgeActivity()) {
      return Type.DELETE.name();
    }

    AuditGlobalContextData auditGlobalContextData =
        (AuditGlobalContextData) GlobalContextManager.get(AuditGlobalContextData.AUDIT_ID);
    if (auditGlobalContextData == null || auditGlobalContextData.getEntityOperationIdentifierSet() == null) {
      return Type.UPDATE.name();
    }

    EntityOperationIdentifier operationIdentifier = EntityOperationIdentifier.builder()
                                                        .entityId(affectedResourceId)
                                                        .entityName(affectedResourceName)
                                                        .entityType(entityType.name())
                                                        .operation(EntityOperation.CREATE)
                                                        .build();
    if (auditGlobalContextData.getEntityOperationIdentifierSet().contains(operationIdentifier)) {
      return Type.CREATE.name();
    }

    return Type.UPDATE.name();
  }

  private boolean isPurgeActivity() {
    boolean purgeActivity = false;

    GlobalContextData globalContextData = GlobalContextManager.get(PurgeGlobalContextData.PURGE_OP);
    if (globalContextData != null) {
      purgeActivity = true;
    }

    return purgeActivity;
  }

  private String getApplicationName(String appId) {
    List<Application> applications = wingsPersistence.createQuery(Application.class)
                                         .filter(ApplicationKeys.appId, appId)
                                         .project(ApplicationKeys.name, true)
                                         .asList();
    if (isEmpty(applications)) {
      return "";
    }
    return applications.get(0).getName();
  }

  private String getServiceName(String serviceId, String appId) {
    List<Service> services = wingsPersistence.createQuery(Service.class)
                                 .filter(ID_KEY, serviceId)
                                 .filter(ApplicationKeys.appId, appId)
                                 .project(ServiceKeys.name, true)
                                 .asList();
    if (isEmpty(services)) {
      return "";
    }
    return services.get(0).getName();
  }

  private String getEnvironmentName(String envId, String appId) {
    List<Environment> environments = wingsPersistence.createQuery(Environment.class)
                                         .filter(ID_KEY, envId)
                                         .filter(ApplicationKeys.appId, appId)
                                         .project(EnvironmentKeys.name, true)
                                         .asList();
    if (isEmpty(environments)) {
      return "";
    }
    return environments.get(0).getName();
  }

  private String getEntityTypeForSettingValue(SettingValue settingValue) {
    // This will be case when going through yaml path.
    if (settingValue == null || settingValue.getSettingType() == null) {
      return "Setting_Value";
    }

    return settingValue.getSettingType().name();
  }

  @VisibleForTesting
  String getYamlPathForDeploymentSpecification(Object entity, EntityAuditRecord record) {
    if (entity instanceof HelmChartSpecification) {
      // In the case of HelmChartSpecification, the actual entity is
      // ApplicationManifest. Here we return empty
      return EMPTY;
    } else if (entity instanceof PcfServiceSpecification) {
      return format(DEPLOYMENT_SPECIFICATION_YAML_PATH_FORMAT, record.getAppName(), record.getAffectedResourceName(),
          PCF_MANIFEST_YAML_FILE_NAME);
    } else if (entity instanceof LambdaSpecification) {
      return format(DEPLOYMENT_SPECIFICATION_YAML_PATH_FORMAT, record.getAppName(), record.getAffectedResourceName(),
          LAMBDA_SPEC_YAML_FILE_NAME);
    } else if (entity instanceof UserDataSpecification) {
      return format(DEPLOYMENT_SPECIFICATION_YAML_PATH_FORMAT, record.getAppName(), record.getAffectedResourceName(),
          USER_DATA_SPEC_YAML_FILE_NAME);
    } else if (entity instanceof EcsServiceSpecification) {
      return format(DEPLOYMENT_SPECIFICATION_YAML_PATH_FORMAT, record.getAppName(), record.getAffectedResourceName(),
          ECS_SERVICE_SPEC_YAML_FILE_NAME);
    } else if (entity instanceof EcsContainerTask) {
      return format(DEPLOYMENT_SPECIFICATION_YAML_PATH_FORMAT, record.getAppName(), record.getAffectedResourceName(),
          ECS_CONTAINER_TASK_YAML_FILE_NAME);
    } else if (entity instanceof KubernetesContainerTask) {
      return format(DEPLOYMENT_SPECIFICATION_YAML_PATH_FORMAT, record.getAppName(), record.getAffectedResourceName(),
          KUBERNETES_CONTAINER_TASK_YAML_FILE_NAME);
    } else if (entity instanceof ServiceCommand) {
      return format(COMMANDS_YAML_PATH_FORMAT, record.getAppName(), record.getAffectedResourceName(),
          ((ServiceCommand) entity).getName());
    } else {
      String errorMessage =
          format("Unrecognized class name while getting Yaml path for class: [%s]", entity.getClass().getSimpleName());
      log.error(errorMessage, new Exception());
      return EMPTY;
    }
  }

  public String getFullYamlPathForEntity(Object entity, EntityAuditRecord record) {
    try {
      if (entity == null) {
        return EMPTY;
      }

      // Deployment specs
      if (entity instanceof HelmChartSpecification || entity instanceof PcfServiceSpecification
          || entity instanceof LambdaSpecification || entity instanceof UserDataSpecification
          || entity instanceof EcsContainerTask || entity instanceof EcsServiceSpecification
          || entity instanceof KubernetesContainerTask || entity instanceof ServiceCommand) {
        return getYamlPathForDeploymentSpecification(entity, record);
      }

      String yamlPrefix = yamlHelper.getYamlPathForEntity(entity);
      if (isEmpty(yamlPrefix)) {
        return EMPTY;
      }
      String finalYaml;
      if (entity instanceof Environment) {
        finalYaml = format("%s/%s", yamlPrefix, INDEX_YAML);
      } else if (entity instanceof Pipeline) {
        Pipeline pipeline = (Pipeline) entity;
        finalYaml = format("%s/%s%s", yamlPrefix, pipeline.getName(), YAML_EXTENSION);
      } else if (entity instanceof Application) {
        finalYaml = format("%s/%s", yamlPrefix, INDEX_YAML);
      } else if (entity instanceof InfrastructureMapping) {
        InfrastructureMapping mapping = (InfrastructureMapping) entity;
        finalYaml = format("%s/%s%s", yamlPrefix, mapping.getName(), YAML_EXTENSION);
      } else if (entity instanceof Workflow) {
        Workflow workflow = (Workflow) entity;
        finalYaml = format("%s/%s%s", yamlPrefix, workflow.getName(), YAML_EXTENSION);
      } else if (entity instanceof InfrastructureProvisioner) {
        InfrastructureProvisioner provisioner = (InfrastructureProvisioner) entity;
        finalYaml = format("%s/%s%s", yamlPrefix, provisioner.getName(), YAML_EXTENSION);
      } else if (entity instanceof ArtifactStream) {
        ArtifactStream artifactStream = (ArtifactStream) entity;
        finalYaml = format("%s/%s%s", yamlPrefix, artifactStream.getName(), YAML_EXTENSION);
      } else if (entity instanceof Service) {
        finalYaml = format("%s/%s", yamlPrefix, INDEX_YAML);
      } else if (entity instanceof Trigger) {
        Trigger trigger = (Trigger) entity;
        finalYaml = format("%s/%s%s", yamlPrefix, trigger.getName(), YAML_EXTENSION);
      } else if (entity instanceof ConfigFile) {
        ConfigFile configFile = (ConfigFile) entity;
        finalYaml = format("%s/%s%s", yamlPrefix, configFile.getFileName(), YAML_EXTENSION);
      } else if (entity instanceof SettingAttribute) {
        SettingAttribute settingAttribute = (SettingAttribute) entity;
        SettingValue settingValue = settingAttribute.getValue();
        if (SettingVariableTypes.STRING.name().equals(settingValue.getType())) {
          finalYaml = format("Setup/Applications/%s/%s", record.getAppName(), DEFAULTS_YAML);
        } else {
          finalYaml = format("%s/%s%s", yamlPrefix, settingAttribute.getName(), YAML_EXTENSION);
        }
      } else if (entity instanceof ApplicationManifest) {
        ApplicationManifest appManifest = (ApplicationManifest) entity;
        if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, appManifest.getAccountId())
            && isNotBlank(appManifest.getName())) {
          finalYaml = format("%s/%s%s", yamlPrefix, appManifest.getName(), YAML_EXTENSION);
        } else {
          finalYaml = format("%s/%s", yamlPrefix, INDEX_YAML);
        }
      } else if (entity instanceof ManifestFile) {
        ManifestFile manifestFile = (ManifestFile) entity;
        finalYaml = format("%s/%s", yamlPrefix, manifestFile.getFileName());
      } else if (entity instanceof CVConfiguration) {
        CVConfiguration cvConfiguration = (CVConfiguration) entity;
        finalYaml = format("%s/%s%s", yamlPrefix, cvConfiguration.getName(), YAML_EXTENSION);
      } else if (entity instanceof HarnessTag) {
        finalYaml = format("Setup/%s", TAGS_YAML);
      } else if (entity instanceof Template) {
        Template template = (Template) entity;
        finalYaml = format("%s/%s%s", yamlPrefix, template.getName(), YAML_EXTENSION);
      } else if (entity instanceof GovernanceConfig) {
        finalYaml = format("%s/%s%s", yamlPrefix, YamlConstants.DEPLOYMENT_GOVERNANCE_FOLDER, YAML_EXTENSION);
      } else if (entity instanceof UserGroup) {
        finalYaml = ((UserGroup) entity).getName() + YAML_EXTENSION;
      } else if (entity instanceof ApiKeyEntry) {
        finalYaml = ((ApiKeyEntry) entity).getName() + YAML_EXTENSION;
      } else if (entity instanceof Account) {
        finalYaml = ((Account) entity).getAccountName() + YAML_EXTENSION;
      } else {
        finalYaml = yamlPrefix;
      }
      return finalYaml;
    } catch (InvalidRequestException ex) {
      log.error("Exception while getting Yaml path for entity id: [{}] of class: [{}]", ((UuidAccess) entity).getUuid(),
          entity.getClass().getSimpleName());
      return EMPTY;
    }
  }
}

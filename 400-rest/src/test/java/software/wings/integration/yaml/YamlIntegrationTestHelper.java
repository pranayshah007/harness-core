/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration.yaml;

import static io.harness.eraro.ErrorCode.GIT_CONNECTION_ERROR;
import static io.harness.exception.WingsException.USER_ADMIN;
import static io.harness.govern.Switch.unhandled;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CloudFormationSourceType.TEMPLATE_BODY;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.PhysicalInfrastructureMapping.Builder.aPhysicalInfrastructureMapping;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.integration.yaml.YamlIntegrationTestConstants.DESCRIPTION;
import static software.wings.settings.SettingVariableTypes.PHYSICAL_DATA_CENTER;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.EnvironmentType;
import io.harness.exception.GitConnectionDelegateException;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.AccessType;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.HostConnectionAttributes.ConnectionType;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.InfrastructureProvisionerType;
import software.wings.beans.PhysicalDataCenterConfig;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceTemplate.Builder;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.beans.yaml.YamlConstants;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.ArtifactType;
import software.wings.yaml.gitSync.beans.YamlGitConfig;
import software.wings.yaml.gitSync.beans.YamlGitConfig.SyncMode;

import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

@Singleton
public class YamlIntegrationTestHelper {
  public static final String URL_FOR_GIT_SYNC = "https://github.com/wings-software/yamlIntegrationTest.git";

  public Application createApplication(String appName, String accountId, AppService appService) {
    return appService.save(
        anApplication().accountId(accountId).name(appName).description("Application for Integration Test").build());
  }

  public Service createService(
      String serviceName, Application application, ServiceResourceService serviceResourceService) {
    return serviceResourceService.save(Service.builder()
                                           .appId(application.getUuid())
                                           .name(serviceName)
                                           .artifactType(ArtifactType.WAR)
                                           .description("Service For Integration Test")
                                           .build());
  }

  public YamlGitConfig createYamlGitConfig(
      String accountId, YamlGitService yamlGitService, WingsPersistence wingsPersistence, ScmSecret scmSecret) {
    YamlGitConfig yamlGitConfig = yamlGitService.get(accountId, accountId, EntityType.ACCOUNT);

    if (yamlGitConfig == null) {
      yamlGitConfig = YamlGitConfig.builder()
                          .syncMode(SyncMode.BOTH)
                          .accountId(accountId)
                          .url(URL_FOR_GIT_SYNC)
                          .username("yaml-test-harness")
                          .password(scmSecret.decryptToCharArray(new SecretName("yaml_integration_git_connector_pwd")))
                          .branchName("test")
                          .enabled(true)
                          .build();
      yamlGitConfig.setAppId(GLOBAL_APP_ID);
    } else {
      yamlGitConfig.setSyncMode(SyncMode.BOTH);
      yamlGitConfig.setUrl(URL_FOR_GIT_SYNC);
      yamlGitConfig.setUsername("yaml-test-harness");
      yamlGitConfig.setBranchName("test");
      yamlGitConfig.setEnabled(true);
      yamlGitConfig.setPassword(scmSecret.decryptToCharArray(new SecretName("yaml_integration_git_connector_pwd")));
    }

    String gitConfigId = wingsPersistence.save(yamlGitConfig);
    return wingsPersistence.get(YamlGitConfig.class, gitConfigId);
  }

  public SettingAttribute createGitConnector(
      YamlGitConfig yamlGitConfig, WingsPersistence wingsPersistence, ScmSecret scmSecret) {
    SettingAttribute gitConnector =
        aSettingAttribute()
            .withName("GitConnector" + System.currentTimeMillis())
            .withCategory(SettingCategory.CONNECTOR)
            .withAccountId(yamlGitConfig.getAccountId())
            .withValue(GitConfig.builder()
                           .accountId(yamlGitConfig.getAccountId())
                           .repoUrl(yamlGitConfig.getUrl())
                           .username(yamlGitConfig.getUsername())
                           .password(scmSecret.decryptToCharArray(new SecretName("yaml_integration_git_connector_pwd")))
                           .branch(yamlGitConfig.getBranchName())
                           .build())
            .build();

    wingsPersistence.save(gitConnector);
    return gitConnector;
  }

  public void decryptGitConfig(
      GitConfig gitConfig, SecretManager secretManager, Logger logger, EncryptionService encryptionService) {
    try {
      List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(gitConfig, GLOBAL_APP_ID, null);

      encryptionService.decrypt(gitConfig, encryptionDetails, false);
    } catch (Exception ex) {
      throw new GitConnectionDelegateException(GIT_CONNECTION_ERROR, null, null, USER_ADMIN);
    }
  }

  public SettingAttribute createSettingAttribute(String name, String accountId, String appId, String envId,
      SettingVariableTypes settingVariableTypes, SettingsService settingsService) {
    SettingAttribute.Builder builder =
        aSettingAttribute().withAppId(appId).withAccountId(accountId).withName(name).withEnvId(envId);

    SettingValue settingValue = null;
    switch (settingVariableTypes) {
      case PHYSICAL_DATA_CENTER:
        settingValue =
            PhysicalDataCenterConfig.Builder.aPhysicalDataCenterConfig().withType(settingVariableTypes.name()).build();
        break;
      case HOST_CONNECTION_ATTRIBUTES:
        settingValue = HostConnectionAttributes.Builder.aHostConnectionAttributes()
                           .withAccessType(AccessType.USER_PASSWORD)
                           .withAccountId(accountId)
                           .withConnectionType(ConnectionType.SSH)
                           .build();
        break;

      default:
        unhandled(settingVariableTypes);
    }
    builder.withValue(settingValue);

    return settingsService.save(builder.build());
  }

  public void deleteSettingAttribute(SettingAttribute settingAttribute, SettingsService settingsService) {
    settingsService.delete(settingAttribute.getAppId(), settingAttribute.getUuid());
  }

  public InfrastructureProvisioner createInfraProvisioner(String name, String appId,
      InfrastructureProvisionerType provisionerType,
      InfrastructureProvisionerService infrastructureProvisionerService) {
    InfrastructureProvisioner infrastructureProvisioner = null;

    switch (provisionerType) {
      case CLOUD_FORMATION:
        infrastructureProvisioner = CloudFormationInfrastructureProvisioner.builder()
                                        .name(name)
                                        .appId(appId)
                                        .sourceType(TEMPLATE_BODY.name())
                                        .description(DESCRIPTION)
                                        .build();
        break;
      default:
        unhandled(provisionerType);
    }

    return infrastructureProvisionerService.save(infrastructureProvisioner);
  }

  public void deleteInfraProvisioner(InfrastructureProvisioner infrastructureProvisioner,
      InfrastructureProvisionerService infrastructureProvisionerService) {
    infrastructureProvisionerService.delete(infrastructureProvisioner.getAppId(), infrastructureProvisioner.getUuid());
  }

  public void deleteApplication(Application application, AppService appService) {
    appService.delete(application.getAppId());
  }

  public String getCloudProviderYamlPath(String cloudProviderName) {
    return new StringBuilder()
        .append(YamlConstants.SETUP_FOLDER)
        .append("/")
        .append(YamlConstants.CLOUD_PROVIDERS_FOLDER)
        .append("/")
        .append(cloudProviderName)
        .append(YamlConstants.YAML_EXTENSION)
        .toString();
  }

  public String getInfraProvisionerYamlPath(
      Application application, InfrastructureProvisioner infrastructureProvisioner) {
    return new StringBuilder()
        .append(YamlConstants.SETUP_FOLDER)
        .append("/")
        .append(YamlConstants.APPLICATIONS_FOLDER)
        .append("/")
        .append(application.getName())
        .append("/")
        .append(YamlConstants.PROVISIONERS_FOLDER)
        .append("/")
        .append(infrastructureProvisioner.getName())
        .append(YamlConstants.YAML_EXTENSION)
        .toString();
  }

  public String getApplicationYamlPath(Application application) {
    return new StringBuilder()
        .append(YamlConstants.SETUP_FOLDER)
        .append("/")
        .append(YamlConstants.APPLICATIONS_FOLDER)
        .append("/")
        .append(application.getName())
        .toString();
  }

  public Environment createEnvironment(String envName, String appId, EnvironmentService environmentService) {
    Environment.Builder builder =
        anEnvironment().name(envName).appId(appId).environmentType(EnvironmentType.PROD).description(DESCRIPTION);

    return environmentService.save(builder.build());
  }

  public String getEnvironmentYamlPath(Application application, Environment environment) {
    return new StringBuilder()
        .append(YamlConstants.SETUP_FOLDER)
        .append("/")
        .append(YamlConstants.APPLICATIONS_FOLDER)
        .append("/")
        .append(application.getName())
        .append("/")
        .append(YamlConstants.ENVIRONMENTS_FOLDER)
        .append("/")
        .append(environment.getName())
        .toString();
  }

  public InfrastructureMapping getInfrastructureMapping(String accountId, String appId, String serviceId, String envId,
      InfrastructureMappingType infrastructureMappingType, String computeProviderName, String serviceTemplateId,
      String settingAttributeId, String computeProviderSettingId,
      InfrastructureMappingService infrastructureMappingService) {
    InfrastructureMapping infrastructureMapping = getInfrastructureMapping(infrastructureMappingType,
        computeProviderName, serviceTemplateId, settingAttributeId, computeProviderSettingId);

    infrastructureMapping.setAccountId(accountId);
    infrastructureMapping.setAppId(appId);
    infrastructureMapping.setServiceId(serviceId);
    infrastructureMapping.setEnvId(envId);

    return infrastructureMappingService.save(infrastructureMapping, null);
  }

  private InfrastructureMapping getInfrastructureMapping(InfrastructureMappingType infrastructureMappingType,
      String computeProviderName, String serviceTemplateId, String settingAttributeId, String computeProvideSettingId) {
    InfrastructureMapping infrastructureMapping = null;

    switch (infrastructureMappingType) {
      case PHYSICAL_DATA_CENTER_SSH:
        infrastructureMapping = aPhysicalInfrastructureMapping()
                                    .withDeploymentType(DeploymentType.SSH.name())
                                    .withComputeProviderType(PHYSICAL_DATA_CENTER.name())
                                    .withComputeProviderSettingId(computeProvideSettingId)
                                    .withInfraMappingType(infrastructureMappingType.getName())
                                    .withComputeProviderName(computeProviderName)
                                    .withHostNames(asList(YamlIntegrationTestConstants.HOST))
                                    .withHostConnectionAttrs(settingAttributeId)
                                    .withServiceTemplateId(serviceTemplateId)
                                    .build();
        break;

      default:
        unhandled(infrastructureMappingType);
    }

    return infrastructureMapping;
  }

  public ServiceTemplate createServiceTemplate(
      String name, String appId, String serviceId, String envId, ServiceTemplateService serviceTemplateService) {
    ServiceTemplate serviceTemplate =
        Builder.aServiceTemplate().withAppId(appId).withServiceId(serviceId).withEnvId(envId).withName(name).build();

    return serviceTemplateService.save(serviceTemplate);
  }

  public void verifyResultFromGit(Map<String, String> expectedFiles, GitFetchFilesResult gitFetchFilesResult) {
    gitFetchFilesResult.getFiles().forEach(gitFile -> {
      String filePath = gitFile.getFilePath();
      assertThat(expectedFiles.containsKey(filePath) && expectedFiles.get(filePath).equals(gitFile.getFileContent()))
          .isTrue();
    });
  }
}

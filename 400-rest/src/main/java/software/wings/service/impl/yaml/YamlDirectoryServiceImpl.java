/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.annotations.dev.HarnessModule._951_CG_GIT_SYNC;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.beans.FeatureName.NOTIFY_GIT_SYNC_ERRORS_PER_APP;
import static io.harness.beans.PageRequest.PageRequestBuilder;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.govern.Switch.unhandled;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.ConfigFile.DEFAULT_TEMPLATE_ID;
import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.SERVICE_TEMPLATE;
import static software.wings.beans.Service.GLOBAL_SERVICE_NAME_FOR_YAML;
import static software.wings.beans.appmanifest.AppManifestKind.AZURE_APP_SERVICE_MANIFEST;
import static software.wings.beans.appmanifest.AppManifestKind.HELM_CHART_OVERRIDE;
import static software.wings.beans.appmanifest.AppManifestKind.K8S_MANIFEST;
import static software.wings.beans.appmanifest.AppManifestKind.KUSTOMIZE_PATCHES;
import static software.wings.beans.appmanifest.AppManifestKind.OC_PARAMS;
import static software.wings.beans.appmanifest.AppManifestKind.VALUES;
import static software.wings.beans.yaml.YamlConstants.APPLICATIONS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.APPLICATION_TEMPLATE_LIBRARY_FOLDER;
import static software.wings.beans.yaml.YamlConstants.ARTIFACT_SOURCES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.ARTIFACT_STREAMS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.AZURE_APP_SETTINGS_OVERRIDES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.AZURE_CONN_STRINGS_OVERRIDES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.CG_EVENT_CONFIG_FOLDER;
import static software.wings.beans.yaml.YamlConstants.CLOUD_PROVIDERS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.COLLABORATION_PROVIDERS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.COMMANDS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.CONFIG_FILES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.CV_CONFIG_FOLDER;
import static software.wings.beans.yaml.YamlConstants.DEFAULTS_YAML;
import static software.wings.beans.yaml.YamlConstants.DEPLOYMENT_SPECIFICATION_FOLDER;
import static software.wings.beans.yaml.YamlConstants.ENVIRONMENTS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.GIT_YAML_LOG_PREFIX;
import static software.wings.beans.yaml.YamlConstants.GLOBAL_TEMPLATE_LIBRARY_FOLDER;
import static software.wings.beans.yaml.YamlConstants.GOVERNANCE_FOLDER;
import static software.wings.beans.yaml.YamlConstants.HELM_CHART_OVERRIDE_FOLDER;
import static software.wings.beans.yaml.YamlConstants.INDEX_YAML;
import static software.wings.beans.yaml.YamlConstants.INFRA_DEFINITION_FOLDER;
import static software.wings.beans.yaml.YamlConstants.INFRA_MAPPING_FOLDER;
import static software.wings.beans.yaml.YamlConstants.KUSTOMIZE_PATCHES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.LOAD_BALANCERS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.MANIFEST_FILE_FOLDER;
import static software.wings.beans.yaml.YamlConstants.MANIFEST_FOLDER;
import static software.wings.beans.yaml.YamlConstants.MANIFEST_FOLDER_APP_SERVICE;
import static software.wings.beans.yaml.YamlConstants.NOTIFICATION_GROUPS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.OC_PARAMS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.beans.yaml.YamlConstants.PCF_OVERRIDES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.PIPELINES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.PROVISIONERS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.SERVICES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.SETUP_FOLDER;
import static software.wings.beans.yaml.YamlConstants.SOURCE_REPO_PROVIDERS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.TAGS_YAML;
import static software.wings.beans.yaml.YamlConstants.TRIGGER_FOLDER;
import static software.wings.beans.yaml.YamlConstants.VALUES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.VERIFICATION_PROVIDERS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.WORKFLOWS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.YAML_EXTENSION;
import static software.wings.common.TemplateConstants.TEMPLATE_TYPES_WITH_YAML_SUPPORT;
import static software.wings.security.UserThreadLocal.userGuard;
import static software.wings.settings.SettingVariableTypes.PHYSICAL_DATA_CENTER;

import static java.util.Collections.emptySet;
import static java.util.Collections.sort;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.CgEventConfig;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SortOrder;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.ChangeType;
import io.harness.governance.GovernanceFreezeConfig;
import io.harness.service.EventConfigService;

import software.wings.api.DeploymentType;
import software.wings.beans.Account;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.Application;
import software.wings.beans.ConfigFile;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.beans.HarnessTag;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.LambdaSpecification;
import software.wings.beans.NotificationGroup;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.SettingAttribute;
import software.wings.beans.User;
import software.wings.beans.Workflow;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.GitSyncErrorAlert;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ApplicationManifest.AppManifestSource;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.container.ContainerTask;
import software.wings.beans.container.EcsServiceSpecification;
import software.wings.beans.container.HelmChartSpecification;
import software.wings.beans.container.PcfServiceSpecification;
import software.wings.beans.container.UserDataSpecification;
import software.wings.beans.defaults.Defaults;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.beans.security.UserGroup;
import software.wings.beans.template.Template;
import software.wings.beans.template.Template.TemplateKeys;
import software.wings.beans.template.TemplateFolder;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.yaml.FullSyncError;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.GitFileChange.Builder;
import software.wings.beans.yaml.YamlConstants;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.InfrastructureDefinition.InfrastructureDefinitionKeys;
import software.wings.security.AccountPermissionSummary;
import software.wings.security.AppPermissionSummary;
import software.wings.security.AppPermissionSummary.EnvInfo;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.NotificationSetupService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.compliance.GovernanceConfigService;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.template.TemplateService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.service.intfc.yaml.AppYamlResourceService;
import software.wings.service.intfc.yaml.YamlArtifactStreamService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.YamlResourceService;
import software.wings.service.intfc.yaml.sync.GitSyncErrorService;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.ArtifactType;
import software.wings.utils.Utils;
import software.wings.verification.CVConfiguration;
import software.wings.yaml.YamlVersion.Type;
import software.wings.yaml.directory.AccountLevelYamlNode;
import software.wings.yaml.directory.AppLevelYamlNode;
import software.wings.yaml.directory.DirectoryNode;
import software.wings.yaml.directory.DirectoryPath;
import software.wings.yaml.directory.EnvLevelYamlNode;
import software.wings.yaml.directory.FolderNode;
import software.wings.yaml.directory.ServiceLevelYamlNode;
import software.wings.yaml.directory.SettingAttributeYamlNode;
import software.wings.yaml.directory.YamlNode;
import software.wings.yaml.gitSync.beans.YamlGitConfig;
import software.wings.yaml.gitSync.beans.YamlGitConfig.SyncMode;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
@TargetModule(_951_CG_GIT_SYNC)
@OwnedBy(DX)
public class YamlDirectoryServiceImpl implements YamlDirectoryService {
  @Inject private AppService appService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private EnvironmentService environmentService;
  @Inject private SettingsService settingsService;

  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private CVConfigurationService cvConfigurationService;
  @Inject private WorkflowService workflowService;
  @Inject private PipelineService pipelineService;
  @Inject private InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private ServiceTemplateService serviceTemplateService;

  @Inject private YamlArtifactStreamService yamlArtifactStreamService;
  @Inject private YamlGitService yamlGitSyncService;
  @Inject private AppYamlResourceService appYamlResourceService;
  @Inject private YamlResourceService yamlResourceService;
  @Inject private YamlGitService yamlGitService;
  @Inject private AlertService alertService;
  @Inject private YamlChangeSetHelper yamlChangeSetHelper;
  @Inject private ConfigService configService;
  @Inject private NotificationSetupService notificationSetupService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private ExecutorService executorService;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private TriggerService triggerService;
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject private TemplateService templateService;
  @Inject private AuthService authService;
  @Inject private GitSyncErrorService gitSyncErrorService;
  @Inject private TemplateGalleryService templateGalleryService;
  @Inject private GovernanceConfigService governanceConfigService;
  @Inject private EventConfigService eventConfigService;

  @Override
  public YamlGitConfig weNeedToPushChanges(String accountId, String appId) {
    EntityType entityType = GLOBAL_APP_ID.equals(appId) ? EntityType.ACCOUNT : EntityType.APPLICATION;
    String entityId = GLOBAL_APP_ID.equals(appId) ? accountId : appId;

    YamlGitConfig yamlGitConfig = yamlGitSyncService.get(accountId, entityId, entityType);

    return validateYamlGitConfig(yamlGitConfig);
  }

  private YamlGitConfig validateYamlGitConfig(YamlGitConfig yamlGitConfig) {
    if (yamlGitConfig != null && yamlGitConfig.isEnabled() && yamlGitConfig.getSyncMode() != SyncMode.GIT_TO_HARNESS) {
      return yamlGitConfig;
    }

    return null;
  }

  @Override
  public List<GitFileChange> traverseDirectory(List<GitFileChange> gitFileChanges, String accountId, FolderNode fn,
      final String path, boolean includeFiles, boolean failFast, List<FullSyncError> listOfYamlErrors) {
    final String finalPath = path + (isNotBlank(path) ? PATH_DELIMITER : "") + fn.getName();

    for (DirectoryNode dn : fn.getChildren()) {
      getGitFileChange(dn, finalPath, accountId, includeFiles, gitFileChanges, failFast, listOfYamlErrors, true);

      if (dn instanceof FolderNode) {
        traverseDirectory(
            gitFileChanges, accountId, (FolderNode) dn, finalPath, includeFiles, failFast, listOfYamlErrors);
      }
    }
    return gitFileChanges;
  }

  @Override
  public void getGitFileChange(DirectoryNode dn, String path, String accountId, boolean includeFiles,
      List<GitFileChange> gitFileChanges, boolean failFast, List<FullSyncError> listOfYamlErrors, boolean gitSyncPath) {
    if (dn == null) {
      log.error("Directory node is null");
      return;
    }

    log.info("Traverse Directory: " + (dn.getName() == null ? dn.getName() : path + "/" + dn.getName()));

    boolean addToFileChangeList = true;
    if (dn instanceof YamlNode) {
      String entityId = ((YamlNode) dn).getUuid();
      String yaml = "";
      String appId = "";
      boolean exceptionThrown = false;

      try {
        switch (dn.getShortClassName()) {
          case "Application":
            yaml = appYamlResourceService.getApp(entityId).getResource().getYaml();
            break;
          case "Service":
            appId = ((AppLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getService(appId, entityId).getResource().getYaml();
            break;
          case "Environment":
            appId = ((AppLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getEnvironment(appId, entityId).getResource().getYaml();
            break;
          case "InfrastructureMapping":
            appId = ((AppLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getInfraMapping(accountId, appId, entityId).getResource().getYaml();
            break;
          case "InfrastructureDefinition":
            appId = ((AppLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getInfraDefinition(appId, entityId).getResource().getYaml();
            break;
          case "CVConfiguration":
            appId = ((EnvLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getCVConfiguration(appId, entityId).getResource().getYaml();
            break;
          case "ServiceCommand":
            appId = ((ServiceLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getServiceCommand(appId, entityId).getResource().getYaml();
            break;
          case "ArtifactStream":
            appId = ((AppLevelYamlNode) dn).getAppId();
            yaml = yamlArtifactStreamService.getArtifactStreamYamlString(appId, entityId);
            break;
          case "Defaults":
            if (dn instanceof AppLevelYamlNode) {
              appId = ((AppLevelYamlNode) dn).getAppId();
            } else {
              appId = GLOBAL_APP_ID;
            }
            yaml = yamlResourceService.getDefaultVariables(accountId, appId).getResource().getYaml();
            break;
          case "ConfigFile":
            if (dn instanceof ServiceLevelYamlNode) {
              appId = ((ServiceLevelYamlNode) dn).getAppId();
            } else if (dn instanceof EnvLevelYamlNode) {
              appId = ((EnvLevelYamlNode) dn).getAppId();
            }

            if (includeFiles) {
              ConfigFile configFile = configService.get(appId, entityId);
              List<GitFileChange> gitChangeSet =
                  yamlChangeSetHelper.getConfigFileGitChangeSet(configFile, ChangeType.ADD);
              gitFileChanges.addAll(gitChangeSet);
              addToFileChangeList = false;
            } else {
              yaml = yamlResourceService.getConfigFileYaml(accountId, appId, entityId).getResource().getYaml();
            }
            break;
          case "ApplicationManifest":
            gitFileChanges.addAll(getApplicationManifestGitFileChanges(dn, entityId));
            addToFileChangeList = false;
            break;
          case "ManifestFile":
            gitFileChanges.addAll(getManifestFileGitFileChanges(dn, entityId));
            addToFileChangeList = false;
            break;
          case "Workflow":
            appId = ((AppLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getWorkflow(appId, entityId).getResource().getYaml();
            break;
          case "Trigger":
            appId = ((AppLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getTrigger(appId, entityId).getResource().getYaml();
            break;
          case "InfrastructureProvisioner":
            appId = ((AppLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getProvisioner(appId, entityId).getResource().getYaml();
            break;
          case "Pipeline":
            appId = ((AppLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getPipeline(appId, entityId).getResource().getYaml();
            break;
          case "NotificationGroup":
            yaml = yamlResourceService.getNotificationGroup(accountId, entityId).getResource().getYaml();
            break;
          case "SettingAttribute":
            yaml = yamlResourceService.getSettingAttribute(accountId, entityId).getResource().getYaml();
            break;
          case "ContainerTask":
            appId = ((ServiceLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getContainerTask(accountId, appId, entityId).getResource().getYaml();
            break;
          case "PcfServiceSpecification":
            appId = ((ServiceLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getPcfServiceSpecification(accountId, appId, entityId).getResource().getYaml();
            break;
          case "EcsServiceSpecification":
            appId = ((ServiceLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getEcsServiceSpecification(accountId, appId, entityId).getResource().getYaml();
            break;
          case "HelmChartSpecification":
            appId = ((ServiceLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getHelmChartSpecification(accountId, appId, entityId).getResource().getYaml();
            break;
          case "LambdaSpecification":
            appId = ((ServiceLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getLambdaSpec(accountId, appId, entityId).getResource().getYaml();
            break;
          case "UserDataSpecification":
            appId = ((ServiceLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getUserDataSpec(accountId, appId, entityId).getResource().getYaml();
            break;

          case "HarnessTag":
            yaml = yamlResourceService.getHarnessTags(accountId).getResource().getYaml();
            break;
          case "Template":
            if (dn instanceof AppLevelYamlNode) {
              appId = ((AppLevelYamlNode) dn).getAppId();
            } else {
              appId = GLOBAL_APP_ID;
            }
            yaml = yamlResourceService.getTemplateLibrary(accountId, appId, entityId).getResource().getYaml();
            break;
          case "GovernanceConfig":
            yaml = yamlResourceService.getGovernanceConfig(accountId).getResource().getYaml();
            break;
          case "Event Rules":
            appId = ((AppLevelYamlNode) dn).getAppId();
            yaml = yamlResourceService.getEventConfig(appId, entityId).getResource().getYaml();
            break;

          default:
            log.warn("No toYaml for entity[{}, {}]", dn.getShortClassName(), entityId);
        }
      } catch (Exception e) {
        exceptionThrown = true;
        String fileName = dn.getName() == null ? dn.getName() : path + PATH_DELIMITER + dn.getName();
        String message;
        if (isNotBlank(ExceptionUtils.getMessage(e))) {
          message = ExceptionUtils.getMessage(e);
        } else {
          message = "Failed in yaml conversion during Harness to Git full sync for file:" + fileName;
        }
        log.warn(GIT_YAML_LOG_PREFIX + message, e);

        // Add GitSyncError record
        GitFileChange gitFileChange = Builder.aGitFileChange()
                                          .withAccountId(accountId)
                                          .withFilePath(dn.getName() == null ? dn.getName() : path + "/" + dn.getName())
                                          .withFileContent(yaml)
                                          .withChangeType(ChangeType.ADD)
                                          .build();

        if (gitSyncPath) {
          gitSyncErrorService.upsertGitSyncErrors(gitFileChange, message, true, false);

          if (featureFlagService.isEnabled(NOTIFY_GIT_SYNC_ERRORS_PER_APP, accountId)) {
            alertService.openAlert(accountId, appId, AlertType.GitSyncError,
                GitSyncErrorAlert.builder().accountId(accountId).message(message).build());
          } else {
            // createAlert of type HarnessToGitFullSyncError
            alertService.openAlert(accountId, GLOBAL_APP_ID, AlertType.GitSyncError,
                GitSyncErrorAlert.builder().accountId(accountId).message(message).build());
          }
        }

        if (failFast) {
          throw new GeneralException(message, WingsException.USER);
        } else {
          listOfYamlErrors.add(FullSyncError.builder().error(message).gitFileChange(gitFileChange).build());
        }
      }

      if (addToFileChangeList && !exceptionThrown) {
        GitFileChange gitFileChange = Builder.aGitFileChange()
                                          .withAccountId(accountId)
                                          .withFilePath(dn.getName() == null ? dn.getName() : path + "/" + dn.getName())
                                          .withFileContent(yaml)
                                          .withChangeType(ChangeType.ADD)
                                          .build();
        gitFileChanges.add(gitFileChange);
      }
    }
  }

  private List<GitFileChange> getApplicationManifestGitFileChanges(DirectoryNode dn, String entityId) {
    String appId = null;

    if (dn instanceof ServiceLevelYamlNode) {
      appId = ((ServiceLevelYamlNode) dn).getAppId();
    } else if (dn instanceof EnvLevelYamlNode) {
      appId = ((EnvLevelYamlNode) dn).getAppId();
    }

    ApplicationManifest applicationManifest = applicationManifestService.getById(appId, entityId);
    return yamlChangeSetHelper.getApplicationManifestGitChangeSet(applicationManifest, ChangeType.ADD);
  }

  private List<GitFileChange> getManifestFileGitFileChanges(DirectoryNode dn, String entityId) {
    String appId = null;

    if (dn instanceof ServiceLevelYamlNode) {
      appId = ((ServiceLevelYamlNode) dn).getAppId();
    } else if (dn instanceof EnvLevelYamlNode) {
      appId = ((EnvLevelYamlNode) dn).getAppId();
    }

    ManifestFile manifestFile = applicationManifestService.getManifestFileById(appId, entityId);
    return yamlChangeSetHelper.getManifestFileGitChangeSet(manifestFile, ChangeType.ADD);
  }

  @Override
  public DirectoryNode getDirectory(@NotEmpty String accountId, String appId) {
    return getDirectory(YamlDirectoryFetchPayload.builder()
                            .accountId(accountId)
                            .entityId(accountId)
                            .applyPermissions(true)
                            .userPermissionInfo(getUserPermissionInfo(accountId))
                            .appLevelYamlTreeOnly(true)
                            .addApplication(true)
                            .appId(appId)
                            .build());
  }

  @Override
  public FolderNode getDirectory(
      @NotEmpty String accountId, String entityId, boolean applyPermissions, UserPermissionInfo userPermissionInfo) {
    return getDirectory(YamlDirectoryFetchPayload.builder()
                            .accountId(accountId)
                            .entityId(entityId)
                            .applyPermissions(applyPermissions)
                            .userPermissionInfo(userPermissionInfo)
                            .appLevelYamlTreeOnly(false)
                            .addApplication(false)
                            .appId(null)
                            .build());
  }

  private FolderNode getDirectory(YamlDirectoryFetchPayload yamlDirectoryFetchPayload) {
    String accountId = yamlDirectoryFetchPayload.getAccountId();
    DirectoryPath directoryPath = new DirectoryPath(SETUP_FOLDER);

    FolderNode configFolder = new FolderNode(accountId, SETUP_FOLDER, Account.class, directoryPath);
    long startTime = System.nanoTime();

    String defaultVarsYamlFileName = DEFAULTS_YAML;
    configFolder.addChild(new YamlNode(accountId, GLOBAL_APP_ID, defaultVarsYamlFileName, Defaults.class,
        directoryPath.clone().add(defaultVarsYamlFileName), Type.ACCOUNT_DEFAULTS));

    String tagsFileName = TAGS_YAML;
    configFolder.addChild(new YamlNode(
        accountId, GLOBAL_APP_ID, tagsFileName, HarnessTag.class, directoryPath.clone().add(tagsFileName), Type.TAGS));

    List<Future<FolderNode>> futureList = new ArrayList<>();
    User user = UserThreadLocal.get();
    if (yamlDirectoryFetchPayload.isAddApplication()) {
      futureList.add(executorService.submit(
          ()
              -> doApplicationsYamlTree(accountId, directoryPath.clone(),
                  yamlDirectoryFetchPayload.isApplyPermissions(), yamlDirectoryFetchPayload.getUserPermissionInfo(),
                  yamlDirectoryFetchPayload.isAppLevelYamlTreeOnly(), yamlDirectoryFetchPayload.getAppId())));
    }

    futureList.add(executorService.submit(() -> {
      try (UserThreadLocal.Guard guard = userGuard(user)) {
        return doCloudProviders(accountId, directoryPath.clone());
      }
    }));

    futureList.add(executorService.submit(() -> {
      try (UserThreadLocal.Guard guard = userGuard(user)) {
        return doArtifactServers(accountId, directoryPath.clone());
      }
    }));

    futureList.add(executorService.submit(() -> {
      try (UserThreadLocal.Guard guard = userGuard(user)) {
        return doCollaborationProviders(accountId, directoryPath.clone());
      }
    }));

    futureList.add(executorService.submit(() -> {
      try (UserThreadLocal.Guard guard = userGuard(user)) {
        return doSourceRepoProviders(accountId, directoryPath.clone());
      }
    }));

    futureList.add(executorService.submit(() -> {
      try (UserThreadLocal.Guard guard = userGuard(user)) {
        return doVerificationProviders(accountId, directoryPath.clone());
      }
    }));

    futureList.add(executorService.submit(() -> {
      try (UserThreadLocal.Guard guard = userGuard(user)) {
        return doNotificationGroups(accountId, directoryPath.clone());
      }
    }));

    futureList.add(executorService.submit(() -> {
      try (UserThreadLocal.Guard guard = userGuard(user)) {
        return doTemplateLibrary(accountId, directoryPath.clone(), GLOBAL_APP_ID, GLOBAL_TEMPLATE_LIBRARY_FOLDER,
            Type.GLOBAL_TEMPLATE_LIBRARY, false, Collections.EMPTY_SET);
      }
    }));

    if (isNewDeploymentFreezeFFenabled(accountId)) {
      // Governance Folder
      futureList.add(executorService.submit(() -> {
        try (UserThreadLocal.Guard guard = userGuard(user)) {
          return doGovernance(accountId, yamlDirectoryFetchPayload.getAppId(), directoryPath.clone());
        }
      }));
    }

    // collect results to this map so we can rebuild the correct order
    Map<String, FolderNode> map = new HashMap<>();

    for (Future<FolderNode> future : futureList) {
      try {
        final FolderNode fn = future.get();

        if (fn == null) {
          log.info("********* failure in completionService");
        } else {
          map.put(fn.getName(), fn);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (ExecutionException e) {
        log.error(ExceptionUtils.getMessage(e), e);
      }
    }

    // this controls the returned order
    if (yamlDirectoryFetchPayload.isAddApplication()) {
      configFolder.addChild(map.get(APPLICATIONS_FOLDER));
    }
    configFolder.addChild(map.get(CLOUD_PROVIDERS_FOLDER));
    configFolder.addChild(map.get(ARTIFACT_SOURCES_FOLDER));
    configFolder.addChild(map.get(COLLABORATION_PROVIDERS_FOLDER));
    configFolder.addChild(map.get(SOURCE_REPO_PROVIDERS_FOLDER));
    configFolder.addChild(map.get(VERIFICATION_PROVIDERS_FOLDER));
    configFolder.addChild(map.get(NOTIFICATION_GROUPS_FOLDER));
    if (map.containsKey(YamlConstants.GOVERNANCE_FOLDER)) {
      configFolder.addChild(map.get(YamlConstants.GOVERNANCE_FOLDER));
    }

    if (map.containsKey(GLOBAL_TEMPLATE_LIBRARY_FOLDER)) {
      configFolder.addChild(map.get(GLOBAL_TEMPLATE_LIBRARY_FOLDER));
    }
    //--------------------------------------

    long endTime = System.nanoTime();
    double elapsedTime = (endTime - startTime) / 1e6;

    log.info("********* ELAPSED_TIME: " + elapsedTime + " *********");

    return configFolder;
  }

  @VisibleForTesting
  FolderNode doGovernance(String accountId, String entityId, DirectoryPath directoryPath) {
    FolderNode governanceFolder = new FolderNode(accountId, YamlConstants.GOVERNANCE_FOLDER, GovernanceConfig.class,
        directoryPath.add(YamlConstants.GOVERNANCE_FOLDER));

    doGovernanceConfig(accountId, directoryPath, governanceFolder, entityId);
    return governanceFolder;
  }

  private void doGovernanceConfig(
      String accountId, DirectoryPath directoryPath, FolderNode parentFolder, String entityId) {
    String yamlFileName = "Deployment Governance" + YAML_EXTENSION;
    DirectoryPath cpPath = directoryPath.clone();

    // get entityId for the YAML file
    GovernanceConfig governanceConfig = governanceConfigService.get(accountId);

    // if governance config exists
    if (isNotEmpty(governanceConfig.getUuid())) {
      parentFolder.addChild(new AccountLevelYamlNode(accountId, governanceConfig.getUuid(), yamlFileName,
          GovernanceConfig.class, cpPath.add(yamlFileName), Type.GOVERNANCE_CONFIG));
    }
  }

  @VisibleForTesting
  FolderNode doSourceRepoProviders(String accountId, DirectoryPath directoryPath) {
    // create source repo providers
    FolderNode sourceRepoFolder = new FolderNode(accountId, SOURCE_REPO_PROVIDERS_FOLDER, SettingAttribute.class,
        directoryPath.add(YamlConstants.SOURCE_REPO_PROVIDERS_FOLDER));

    doSourceRepoProviderType(accountId, sourceRepoFolder, SettingVariableTypes.GIT, directoryPath.clone());
    sort(sourceRepoFolder.getChildren(), new DirectoryComparator());
    return sourceRepoFolder;
  }

  private void doSourceRepoProviderType(
      String accountId, FolderNode parentFolder, SettingVariableTypes type, DirectoryPath directoryPath) {
    List<SettingAttribute> settingAttributes;

    settingAttributes = settingsService.listAllSettingAttributesByType(accountId, type.name());

    if (settingAttributes != null) {
      // iterate over providers
      for (SettingAttribute settingAttribute : settingAttributes) {
        DirectoryPath cpPath = directoryPath.clone();
        String yamlFileName = getSettingAttributeYamlName(settingAttribute);
        parentFolder.addChild(new SettingAttributeYamlNode(accountId, settingAttribute.getUuid(),
            settingAttribute.getValue().getType(), yamlFileName, SettingAttribute.class, cpPath.add(yamlFileName)));
      }
    }
  }

  @Override
  public DirectoryNode getApplicationYamlFolderNode(@NotEmpty String accountId, @NotEmpty String applicationId) {
    UserPermissionInfo userPermissionInfo = getUserPermissionInfo(accountId);
    Map<String, AppPermissionSummary> appPermissionSummaryMap = null;
    if (userPermissionInfo != null) {
      appPermissionSummaryMap = userPermissionInfo.getAppPermissionMapInternal();
    }

    DirectoryPath directoryPath = new DirectoryPath(SETUP_FOLDER);

    FolderNode applicationsFolder =
        new FolderNode(accountId, APPLICATIONS_FOLDER, Application.class, directoryPath.add(APPLICATIONS_FOLDER));

    return doApplication(applicationId, true, appPermissionSummaryMap, applicationsFolder, directoryPath);
  }

  @Override
  public DirectoryNode getApplicationManifestYamlFolderNode(
      @NotEmpty String accountId, @NotEmpty String appId, @NotEmpty String serviceId) {
    Service service = serviceResourceService.getWithDetails(appId, serviceId);

    return generateManifestFileFoldeNodeForServiceView(accountId, service);
  }

  private UserPermissionInfo getUserPermissionInfo(@NotEmpty String accountId) {
    UserPermissionInfo userPermissionInfo = null;
    User user = UserThreadLocal.get();
    if (user != null) {
      UserRequestContext userRequestContext = user.getUserRequestContext();
      if (userRequestContext != null) {
        userPermissionInfo = userRequestContext.getUserPermissionInfo();
      }
    }
    return userPermissionInfo;
  }

  private FolderNode doApplicationLevelOnly(String accountId, DirectoryPath directoryPath, boolean applyPermissions,
      Map<String, AppPermissionSummary> appPermissionSummaryMap, String appId) {
    log.info("Inside doApplicationLevelOnly");
    FolderNode applicationsFolder =
        new FolderNode(accountId, APPLICATIONS_FOLDER, Application.class, directoryPath.add(APPLICATIONS_FOLDER));

    List<Application> apps = appService.getAppsByAccountId(accountId);

    Set<String> allowedAppIds = null;
    if (applyPermissions) {
      if (appPermissionSummaryMap != null) {
        allowedAppIds = appPermissionSummaryMap.keySet();
      }

      if (isEmpty(allowedAppIds)) {
        return applicationsFolder;
      }
    }

    // iterate over applications
    for (Application app : apps) {
      if (applyPermissions && !allowedAppIds.contains(app.getUuid())) {
        continue;
      }

      if (isNotEmpty(appId) && app.getUuid().equals(appId)) {
        doApplication(appId, applyPermissions, appPermissionSummaryMap, applicationsFolder, directoryPath);
      } else {
        DirectoryPath appPath = directoryPath.clone();
        FolderNode appFolder =
            new FolderNode(accountId, app.getName(), Application.class, appPath.add(app.getName()), app.getUuid());
        setApplicationGitConfig(accountId, app.getUuid(), appFolder);
        applicationsFolder.addChild(appFolder);
      }
    }

    return applicationsFolder;
  }

  /**
   * Get YamlTreeNode for a single application
   * @param applicationId
   * @param applyPermissions
   * @param appPermissionSummaryMap
   * @return
   */

  @Override
  public FolderNode doApplication(String applicationId, boolean applyPermissions,
      final Map<String, AppPermissionSummary> appPermissionSummaryMap, FolderNode applicationsFolder,
      DirectoryPath directoryPath) {
    Application app = appService.get(applicationId);
    String accountId = app.getAccountId();
    Set<String> allowedAppIds = null;
    if (applyPermissions) {
      if (appPermissionSummaryMap != null) {
        allowedAppIds = appPermissionSummaryMap.keySet();
      }

      if (isEmpty(allowedAppIds) || !allowedAppIds.contains(applicationId)) {
        return applicationsFolder;
      }
    }

    DirectoryPath appPath = directoryPath.clone();
    FolderNode appFolder =
        new FolderNode(accountId, app.getName(), Application.class, appPath.add(app.getName()), app.getUuid());

    setApplicationGitConfig(accountId, applicationId, appFolder);

    applicationsFolder.addChild(appFolder);
    String yamlFileName = INDEX_YAML;
    appFolder.addChild(new YamlNode(
        accountId, app.getUuid(), yamlFileName, Application.class, appPath.clone().add(yamlFileName), Type.APP));

    String defaultVarsYamlFileName = DEFAULTS_YAML;
    appFolder.addChild(new AppLevelYamlNode(accountId, app.getUuid(), app.getUuid(), defaultVarsYamlFileName,
        Defaults.class, appPath.clone().add(defaultVarsYamlFileName), Type.APPLICATION_DEFAULTS));

    AppPermissionSummary appPermissionSummary =
        appPermissionSummaryMap != null ? appPermissionSummaryMap.get(app.getUuid()) : null;

    Set<String> serviceSet = null;
    Set<String> provisionerSet = null;
    Set<String> envSet = null;
    Set<String> workflowSet = null;
    Set<String> pipelineSet = null;
    Set<String> templateSet = null;

    if (applyPermissions && appPermissionSummary != null) {
      Map<Action, Set<String>> servicePermissions = appPermissionSummary.getServicePermissions();
      if (servicePermissions != null) {
        serviceSet = servicePermissions.get(Action.READ);
      }

      Map<Action, Set<String>> provisionerPermissions = appPermissionSummary.getProvisionerPermissions();
      if (provisionerPermissions != null) {
        provisionerSet = provisionerPermissions.get(Action.READ);
      }

      Map<Action, Set<EnvInfo>> envPermissions = appPermissionSummary.getEnvPermissions();

      if (envPermissions != null) {
        Set<EnvInfo> envInfos = envPermissions.get(Action.READ);
        if (isEmpty(envInfos)) {
          envSet = emptySet();
        } else {
          envSet = envInfos.stream().map(EnvInfo::getEnvId).collect(Collectors.toSet());
        }
      }

      Map<Action, Set<String>> workflowPermissions = appPermissionSummary.getWorkflowPermissions();
      if (workflowPermissions != null) {
        workflowSet = workflowPermissions.get(Action.READ);
      }

      Map<Action, Set<String>> pipelinePermissions = appPermissionSummary.getPipelinePermissions();
      if (pipelinePermissions != null) {
        pipelineSet = pipelinePermissions.get(Action.READ);
      }

      Map<Action, Set<String>> templatePermissions = appPermissionSummary.getTemplatePermissions();
      if (templatePermissions != null) {
        templateSet = templatePermissions.get(Action.READ);
      }
    }

    Set<String> allowedServices = serviceSet;
    Set<String> allowedPipelines = pipelineSet;
    Set<String> allowedProvisioners = provisionerSet;
    Set<String> allowedEnvs = envSet;
    Set<String> allowedWorkflows = workflowSet;
    Set<String> allowedTemplates = templateSet;

    //--------------------------------------
    // parallelization using CompletionService (part 2)
    List<Future<FolderNode>> futureResponseList = new ArrayList<>();
    futureResponseList.add(
        executorService.submit(() -> doServices(app, appPath.clone(), applyPermissions, allowedServices)));

    futureResponseList.add(
        executorService.submit(() -> doEnvironments(app, appPath.clone(), applyPermissions, allowedEnvs)));

    futureResponseList.add(
        executorService.submit(() -> doWorkflows(app, appPath.clone(), applyPermissions, allowedWorkflows)));

    futureResponseList.add(
        executorService.submit(() -> doPipelines(app, appPath.clone(), applyPermissions, allowedPipelines)));

    futureResponseList.add(
        executorService.submit(() -> doProvisioners(app, appPath.clone(), applyPermissions, allowedProvisioners)));

    futureResponseList.add(executorService.submit(() -> doEventConfigs(app, appPath.clone())));

    futureResponseList.add(executorService.submit(
        () -> doTemplateLibraryForApp(app, appPath.clone(), applyPermissions, allowedTemplates)));

    if (isTriggerYamlEnabled(accountId)) {
      futureResponseList.add(executorService.submit(() -> doTriggers(app, appPath.clone())));
    }
    //      completionService.submit(() -> doTriggers(app, appPath.clone()));

    // collect results to this map so we can rebuild the correct order
    Map<String, FolderNode> map = new HashMap<>();

    for (Future<FolderNode> future : futureResponseList) {
      try {
        final FolderNode fn = future.get();

        if (fn == null) {
          log.info("********* failure in completionService");
        } else {
          map.put(fn.getName(), fn);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (ExecutionException e) {
        log.warn(ExceptionUtils.getMessage(e), e);
      }
    }

    // this controls the returned order
    appFolder.addChild(map.get(SERVICES_FOLDER));
    appFolder.addChild(map.get(ENVIRONMENTS_FOLDER));
    appFolder.addChild(map.get(WORKFLOWS_FOLDER));
    appFolder.addChild(map.get(PIPELINES_FOLDER));
    appFolder.addChild(map.get(PROVISIONERS_FOLDER));
    appFolder.addChild(map.get(CG_EVENT_CONFIG_FOLDER));
    if (isTriggerYamlEnabled(accountId)) {
      appFolder.addChild(map.get(TRIGGER_FOLDER));
    }
    if (map.containsKey(APPLICATION_TEMPLATE_LIBRARY_FOLDER)) {
      appFolder.addChild(map.get(APPLICATION_TEMPLATE_LIBRARY_FOLDER));
    }

    //--------------------------------------

    return applicationsFolder;
  }

  @Override
  public FolderNode doTemplateLibraryForApp(
      Application app, DirectoryPath directoryPath, boolean applyPermissions, Set<String> allowedTemplates) {
    return doTemplateLibrary(app.getAccountId(), directoryPath, app.getAppId(), APPLICATION_TEMPLATE_LIBRARY_FOLDER,
        Type.APPLICATION_TEMPLATE_LIBRARY, applyPermissions, allowedTemplates);
  }

  private boolean isTriggerYamlEnabled(String accountId) {
    return featureFlagService.isEnabled(FeatureName.TRIGGER_YAML, accountId);
  }

  private boolean isNewDeploymentFreezeFFenabled(String accountId) {
    return featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, accountId);
  }

  private FolderNode doApplicationsYamlTree(String accountId, DirectoryPath directoryPath, boolean applyPermissions,
      UserPermissionInfo userPermissionInfo, boolean appLevelYamlTreeOnly, String appId) {
    Map<String, AppPermissionSummary> appPermissionMap = null;

    if (applyPermissions && userPermissionInfo != null) {
      appPermissionMap = userPermissionInfo.getAppPermissionMapInternal();
    }

    if (appLevelYamlTreeOnly) {
      return doApplicationLevelOnly(accountId, directoryPath, applyPermissions, appPermissionMap, appId);
    } else {
      return doApplications(accountId, directoryPath, applyPermissions, appPermissionMap);
    }
  }

  @VisibleForTesting
  FolderNode doApplications(String accountId, DirectoryPath directoryPath, boolean applyPermissions,
      Map<String, AppPermissionSummary> appPermissionSummaryMap) {
    FolderNode applicationsFolder =
        new FolderNode(accountId, APPLICATIONS_FOLDER, Application.class, directoryPath.add(APPLICATIONS_FOLDER));

    Set<String> allowedAppIds = null;
    if (applyPermissions) {
      if (appPermissionSummaryMap != null) {
        allowedAppIds = appPermissionSummaryMap.keySet();
      }

      if (isEmpty(allowedAppIds)) {
        return applicationsFolder;
      }
    }

    List<Application> apps = appService.getAppsByAccountId(accountId);
    // iterate over applications
    for (Application app : apps) {
      doApplication(app.getUuid(), applyPermissions, appPermissionSummaryMap, applicationsFolder, directoryPath);
    }
    return applicationsFolder;
  }

  private FolderNode doServices(
      Application app, DirectoryPath directoryPath, boolean applyPermissions, Set<String> allowedServices) {
    String accountId = app.getAccountId();
    FolderNode servicesFolder =
        new FolderNode(accountId, SERVICES_FOLDER, Service.class, directoryPath.add(SERVICES_FOLDER), app.getUuid());

    if (applyPermissions && isEmpty(allowedServices)) {
      return servicesFolder;
    }
    PageRequest<Service> pageRequest = aPageRequest()
                                           .addFilter(ServiceKeys.appId, EQ, app.getAppId())
                                           .addOrder(ServiceKeys.name, SortOrder.OrderType.ASC)
                                           .build();
    List<Service> services = serviceResourceService.list(pageRequest, false, false, false, null).getResponse();

    if (services != null) {
      // iterate over services
      for (Service service : services) {
        if (applyPermissions && !allowedServices.contains(service.getUuid())) {
          continue;
        }

        DirectoryPath servicePath = directoryPath.clone();
        String yamlFileName = INDEX_YAML;
        FolderNode serviceFolder = new FolderNode(
            accountId, service.getName(), Service.class, servicePath.add(service.getName()), service.getAppId());
        servicesFolder.addChild(serviceFolder);
        serviceFolder.addChild(new AppLevelYamlNode(accountId, service.getUuid(), service.getAppId(), yamlFileName,
            Service.class, servicePath.clone().add(yamlFileName), Type.SERVICE));

        // ------------------- SERVICE COMMANDS SECTION -----------------------

        if (!serviceResourceService.hasInternalCommands(service)) {
          DirectoryPath serviceCommandPath = servicePath.clone().add(COMMANDS_FOLDER);
          FolderNode serviceCommandsFolder =
              new FolderNode(accountId, COMMANDS_FOLDER, ServiceCommand.class, serviceCommandPath, service.getAppId());
          serviceFolder.addChild(serviceCommandsFolder);

          List<ServiceCommand> serviceCommands =
              serviceResourceService.getServiceCommands(service.getAppId(), service.getUuid());

          // iterate over service commands
          for (ServiceCommand serviceCommand : serviceCommands) {
            String commandYamlFileName = serviceCommand.getName() + YAML_EXTENSION;
            serviceCommandsFolder.addChild(new ServiceLevelYamlNode(accountId, serviceCommand.getUuid(),
                serviceCommand.getAppId(), serviceCommand.getServiceId(), commandYamlFileName, ServiceCommand.class,
                serviceCommandPath.clone().add(commandYamlFileName), Type.SERVICE_COMMAND));
          }
        }

        // ------------------- END SERVICE COMMANDS SECTION -----------------------

        // ------------------- DEPLOYMENT SPECIFICATION SECTION -----------------------

        DirectoryPath deploymentSpecsPath = servicePath.clone().add(DEPLOYMENT_SPECIFICATION_FOLDER);
        if (service.getArtifactType() == ArtifactType.DOCKER) {
          FolderNode deploymentSpecsFolder = new FolderNode(
              accountId, DEPLOYMENT_SPECIFICATION_FOLDER, ContainerTask.class, deploymentSpecsPath, service.getAppId());
          serviceFolder.addChild(deploymentSpecsFolder);

          ContainerTask kubernetesContainerTask = serviceResourceService.getContainerTaskByDeploymentType(
              service.getAppId(), service.getUuid(), DeploymentType.KUBERNETES.name());
          if (kubernetesContainerTask != null) {
            String kubernetesSpecFileName = YamlConstants.KUBERNETES_CONTAINER_TASK_YAML_FILE_NAME + YAML_EXTENSION;
            deploymentSpecsFolder.addChild(new ServiceLevelYamlNode(accountId, kubernetesContainerTask.getUuid(),
                kubernetesContainerTask.getAppId(), service.getUuid(), kubernetesSpecFileName, ContainerTask.class,
                deploymentSpecsPath.clone().add(kubernetesSpecFileName), Type.DEPLOYMENT_SPEC));
          }

          ContainerTask ecsContainerTask = serviceResourceService.getContainerTaskByDeploymentType(
              service.getAppId(), service.getUuid(), DeploymentType.ECS.name());
          if (ecsContainerTask != null) {
            String ecsSpecFileName = YamlConstants.ECS_CONTAINER_TASK_YAML_FILE_NAME + YAML_EXTENSION;
            deploymentSpecsFolder.addChild(new ServiceLevelYamlNode(accountId, ecsContainerTask.getUuid(),
                ecsContainerTask.getAppId(), service.getUuid(), ecsSpecFileName, ContainerTask.class,
                deploymentSpecsPath.clone().add(ecsSpecFileName), Type.DEPLOYMENT_SPEC));
          }

          // This is Service json spec for ECS
          EcsServiceSpecification serviceSpecification =
              serviceResourceService.getEcsServiceSpecification(service.getAppId(), service.getUuid());
          if (serviceSpecification != null) {
            String ecsServiceSpecFileName = YamlConstants.ECS_SERVICE_SPEC_YAML_FILE_NAME + YAML_EXTENSION;
            deploymentSpecsFolder.addChild(
                new ServiceLevelYamlNode(accountId, serviceSpecification.getUuid(), serviceSpecification.getAppId(),
                    service.getUuid(), ecsServiceSpecFileName, EcsServiceSpecification.class,
                    deploymentSpecsPath.clone().add(ecsServiceSpecFileName), Type.DEPLOYMENT_SPEC));
          }

          HelmChartSpecification helmChartSpecification =
              serviceResourceService.getHelmChartSpecification(service.getAppId(), service.getUuid());
          if (helmChartSpecification != null) {
            String helmChartFileName = YamlConstants.HELM_CHART_YAML_FILE_NAME + YAML_EXTENSION;
            deploymentSpecsFolder.addChild(new ServiceLevelYamlNode(accountId, helmChartSpecification.getUuid(),
                helmChartSpecification.getAppId(), service.getUuid(), helmChartFileName, HelmChartSpecification.class,
                deploymentSpecsPath.clone().add(helmChartFileName), Type.DEPLOYMENT_SPEC));
          }
        } else if (service.getArtifactType() == ArtifactType.AWS_LAMBDA) {
          FolderNode deploymentSpecsFolder = new FolderNode(accountId, DEPLOYMENT_SPECIFICATION_FOLDER,
              LambdaSpecification.class, deploymentSpecsPath, service.getAppId());
          serviceFolder.addChild(deploymentSpecsFolder);

          LambdaSpecification lambdaSpecification =
              serviceResourceService.getLambdaSpecification(service.getAppId(), service.getUuid());
          if (lambdaSpecification != null) {
            String lambdaSpecFileName = YamlConstants.LAMBDA_SPEC_YAML_FILE_NAME + YAML_EXTENSION;
            deploymentSpecsFolder.addChild(new ServiceLevelYamlNode(accountId, lambdaSpecification.getUuid(),
                lambdaSpecification.getAppId(), service.getUuid(), lambdaSpecFileName, LambdaSpecification.class,
                deploymentSpecsPath.clone().add(lambdaSpecFileName), Type.DEPLOYMENT_SPEC));
          }
        } else if (service.getArtifactType() == ArtifactType.AMI) {
          FolderNode deploymentSpecsFolder = new FolderNode(accountId, DEPLOYMENT_SPECIFICATION_FOLDER,
              UserDataSpecification.class, deploymentSpecsPath, service.getAppId());
          serviceFolder.addChild(deploymentSpecsFolder);

          UserDataSpecification userDataSpecification =
              serviceResourceService.getUserDataSpecification(service.getAppId(), service.getUuid());
          if (userDataSpecification != null) {
            String userDataSpecFileName = YamlConstants.USER_DATA_SPEC_YAML_FILE_NAME + YAML_EXTENSION;
            deploymentSpecsFolder.addChild(new ServiceLevelYamlNode(accountId, userDataSpecification.getUuid(),
                userDataSpecification.getAppId(), service.getUuid(), userDataSpecFileName, UserDataSpecification.class,
                deploymentSpecsPath.clone().add(userDataSpecFileName), Type.DEPLOYMENT_SPEC));
          }
        } else if (service.getArtifactType() == ArtifactType.PCF) {
          FolderNode deploymentSpecsFolder = new FolderNode(accountId, DEPLOYMENT_SPECIFICATION_FOLDER,
              PcfServiceSpecification.class, deploymentSpecsPath, service.getAppId());
          serviceFolder.addChild(deploymentSpecsFolder);

          PcfServiceSpecification pcfServiceSpecification =
              serviceResourceService.getPcfServiceSpecification(service.getAppId(), service.getUuid());
          if (pcfServiceSpecification != null) {
            String pcfServiceSpecificationFileName = YamlConstants.PCF_MANIFEST_YAML_FILE_NAME + YAML_EXTENSION;
            deploymentSpecsFolder.addChild(new ServiceLevelYamlNode(accountId, pcfServiceSpecification.getUuid(),
                pcfServiceSpecification.getAppId(), service.getUuid(), pcfServiceSpecificationFileName,
                PcfServiceSpecification.class, deploymentSpecsPath.clone().add(pcfServiceSpecificationFileName),
                Type.DEPLOYMENT_SPEC));
          }
        }

        // ------------------- END DEPLOYMENT SPECIFICATION SECTION -----------------------

        // ------------------- ARTIFACT STREAMS SECTION -----------------------
        DirectoryPath artifactStreamsPath = servicePath.clone().add(ARTIFACT_SOURCES_FOLDER);
        FolderNode artifactStreamsFolder = new FolderNode(
            accountId, ARTIFACT_SOURCES_FOLDER, ArtifactStream.class, artifactStreamsPath, service.getAppId());
        serviceFolder.addChild(artifactStreamsFolder);

        List<ArtifactStream> artifactStreamList =
            artifactStreamService.getArtifactStreamsForService(service.getAppId(), service.getUuid());
        artifactStreamList.forEach(artifactStream -> {
          String artifactYamlFileName = artifactStream.getName() + YAML_EXTENSION;
          artifactStreamsFolder.addChild(new ServiceLevelYamlNode(accountId, artifactStream.getUuid(),
              artifactStream.fetchAppId(), service.getUuid(), artifactYamlFileName, ArtifactStream.class,
              artifactStreamsPath.clone().add(artifactYamlFileName), Type.ARTIFACT_STREAM));
        });

        // ------------------- END ARTIFACT STREAMS SECTION -----------------------

        // ------------------- CONFIG FILES SECTION -----------------------
        DirectoryPath configFilesPath = servicePath.clone().add(CONFIG_FILES_FOLDER);
        FolderNode configFilesFolder =
            new FolderNode(accountId, CONFIG_FILES_FOLDER, ConfigFile.class, configFilesPath, service.getAppId());
        serviceFolder.addChild(configFilesFolder);

        List<ConfigFile> configFiles =
            configService.getConfigFilesForEntity(service.getAppId(), DEFAULT_TEMPLATE_ID, service.getUuid());
        configFiles.forEach(configFile -> {
          String configFileName = Utils.normalize(configFile.getRelativeFilePath()) + YAML_EXTENSION;
          configFilesFolder.addChild(
              new ServiceLevelYamlNode(accountId, configFile.getUuid(), configFile.getAppId(), configFile.getEntityId(),
                  configFileName, ConfigFile.class, configFilesPath.clone().add(configFileName), Type.CONFIG_FILE));
        });

        // ------------------- END CONFIG FILES SECTION -----------------------

        // ------------------- APPLICATION MANIFEST FILES SECTION -----------------------

        FolderNode applicationManifestFolder =
            generateApplicationManifestNodeForService(accountId, service, servicePath);
        if (applicationManifestFolder != null) {
          serviceFolder.addChild(applicationManifestFolder);
        }
        // ------------------- END APPLICATION MANIFEST FILES SECTION -----------------------

        // ------------------- VALUES YAML OVERRIDE SECTION -----------------------

        FolderNode valuesFolder = generateValuesFolder(accountId, service, servicePath);
        if (valuesFolder != null) {
          serviceFolder.addChild(valuesFolder);
        }
        // ------------------- END VALUES YAML OVERRIDE SECTION -----------------------

        // ------------------- KUSTOMIZE PATCHES OVERRIDE SECTION -----------------------

        FolderNode kustomizePatchesFolder = generateKustomizePatchesFolder(accountId, service, servicePath);
        if (kustomizePatchesFolder != null) {
          serviceFolder.addChild(kustomizePatchesFolder);
        }
        // ------------------- END KUSTOMIZE PATCHES OVERRIDE SECTION -----------------------

        // ------------------- OC PARAMS OVERRIDE SECTION -----------------------

        FolderNode ocParamsFolder = generateOcParamsFolder(accountId, service, servicePath);
        if (ocParamsFolder != null) {
          serviceFolder.addChild(ocParamsFolder);
        }
        // ------------------- END OC PARAMS OVERRIDE SECTION -----------------------
      }
    }

    return servicesFolder;
  }

  private FolderNode generateValuesFolder(String accountId, Service service, DirectoryPath servicePath) {
    return generateKindBasedFolder(accountId, service, servicePath, AppManifestKind.VALUES);
  }

  private FolderNode generateKustomizePatchesFolder(String accountId, Service service, DirectoryPath servicePath) {
    ApplicationManifest appManifest =
        applicationManifestService.getAppManifest(service.getAppId(), null, service.getUuid(), K8S_MANIFEST);
    if (appManifest == null) {
      return null;
    }
    if (appManifest.getStoreType() != StoreType.KustomizeSourceRepo) {
      return null;
    }
    return generateKindBasedFolder(accountId, service, servicePath, AppManifestKind.KUSTOMIZE_PATCHES);
  }

  private FolderNode generateOcParamsFolder(String accountId, Service service, DirectoryPath servicePath) {
    // Hiding OC Params Folder when not OPEN_SHIFT_TEMPLATES
    ApplicationManifest appManifest =
        applicationManifestService.getAppManifest(service.getAppId(), null, service.getUuid(), K8S_MANIFEST);
    if (appManifest == null) {
      return null;
    }
    if (appManifest.getStoreType() != StoreType.OC_TEMPLATES
        && appManifest.getStoreType() != StoreType.CUSTOM_OPENSHIFT_TEMPLATE) {
      return null;
    }
    return generateKindBasedFolder(accountId, service, servicePath, AppManifestKind.OC_PARAMS);
  }

  private FolderNode generateKindBasedFolder(
      String accountId, Service service, DirectoryPath servicePath, AppManifestKind appManifestKind) {
    ApplicationManifest appManifest =
        applicationManifestService.getByServiceId(service.getAppId(), service.getUuid(), appManifestKind);
    if (appManifest == null) {
      return null;
    }
    DirectoryPath folderPath = servicePath.clone().add(appManifestKind.getYamlFolderName());
    FolderNode folder = new FolderNode(
        accountId, appManifestKind.getYamlFolderName(), ApplicationManifest.class, folderPath, service.getAppId());
    folder.addChild(new ServiceLevelYamlNode(accountId, appManifest.getUuid(), service.getAppId(), service.getUuid(),
        INDEX_YAML, ApplicationManifest.class, folderPath.clone().add(INDEX_YAML), Type.APPLICATION_MANIFEST));
    if (appManifest.getStoreType() == StoreType.Local) {
      List<ManifestFile> manifestFiles =
          applicationManifestService.getManifestFilesByAppManifestId(service.getAppId(), appManifest.getUuid());
      if (isNotEmpty(manifestFiles)) {
        ManifestFile valuesFile = manifestFiles.get(0);
        folder.addChild(new ServiceLevelYamlNode(accountId, valuesFile.getUuid(), service.getAppId(), service.getUuid(),
            valuesFile.getFileName(), ManifestFile.class, folderPath.clone().add(valuesFile.getFileName()),
            Type.APPLICATION_MANIFEST_FILE));
      }
    }
    return folder;
  }

  @VisibleForTesting
  FolderNode generateApplicationManifestNodeForService(String accountId, Service service, DirectoryPath servicePath) {
    DirectoryPath applicationManifestPath = getApplicationManifestDirectoryPath(service, servicePath);

    String manifestFolderName = getApplicationManifestFolderName(service);

    FolderNode applicationManifestFolder = new FolderNode(
        accountId, manifestFolderName, ApplicationManifest.class, applicationManifestPath, service.getAppId());

    DirectoryPath manifestFilePath = applicationManifestPath.clone().add(MANIFEST_FILE_FOLDER);

    if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, accountId)) {
      List<ApplicationManifest> applicationManifests =
          applicationManifestService.getManifestsByServiceId(service.getAppId(), service.getUuid(),
              isAzureAppServiceDeploymentType(service) ? AZURE_APP_SERVICE_MANIFEST : K8S_MANIFEST);
      if (isNotEmpty(applicationManifests)) {
        for (ApplicationManifest applicationManifest : applicationManifests) {
          if (applicationManifest != null) {
            String yamlFileName = getApplicationManifestYamlName(applicationManifest);
            applicationManifestFolder.addChild(new ServiceLevelYamlNode(accountId, applicationManifest.getUuid(),
                service.getAppId(), service.getUuid(), yamlFileName, ApplicationManifest.class,
                applicationManifestPath.clone().add(yamlFileName), Type.APPLICATION_MANIFEST));

            if (StoreType.Local == applicationManifest.getStoreType()) {
              FolderNode manifestFileFolder =
                  generateManifestFileFolderNode(accountId, service, applicationManifest, manifestFilePath);
              applicationManifestFolder.addChild(manifestFileFolder);
            }
          }
        }
      }

      if (isNotEmpty(applicationManifestFolder.getChildren())) {
        return applicationManifestFolder;
      }
    } else {
      ApplicationManifest applicationManifest = getApplicationManifestByService(service);
      if (applicationManifest != null) {
        String yamlFileName = getApplicationManifestYamlName(applicationManifest);
        applicationManifestFolder.addChild(new ServiceLevelYamlNode(accountId, applicationManifest.getUuid(),
            service.getAppId(), service.getUuid(), yamlFileName, ApplicationManifest.class,
            applicationManifestPath.clone().add(yamlFileName), Type.APPLICATION_MANIFEST));

        if (StoreType.Local == applicationManifest.getStoreType()) {
          FolderNode manifestFileFolder =
              generateManifestFileFolderNode(accountId, service, applicationManifest, manifestFilePath);
          applicationManifestFolder.addChild(manifestFileFolder);
        }
        return applicationManifestFolder;
      }
    }

    return null;
  }

  @NotNull
  private String getApplicationManifestYamlName(ApplicationManifest applicationManifest) {
    return applicationManifest.getName() != null ? applicationManifest.getName() + YAML_EXTENSION : INDEX_YAML;
  }

  @NotNull
  private DirectoryPath getApplicationManifestDirectoryPath(Service service, DirectoryPath servicePath) {
    return isAzureAppServiceDeploymentType(service) ? servicePath.clone().add(MANIFEST_FOLDER_APP_SERVICE)
                                                    : servicePath.clone().add(MANIFEST_FOLDER);
  }

  @NotNull
  private String getApplicationManifestFolderName(Service service) {
    return isAzureAppServiceDeploymentType(service) ? MANIFEST_FOLDER_APP_SERVICE : MANIFEST_FOLDER;
  }

  private ApplicationManifest getApplicationManifestByService(Service service) {
    AppManifestKind appManifestKind =
        isAzureAppServiceDeploymentType(service) ? AZURE_APP_SERVICE_MANIFEST : K8S_MANIFEST;
    return applicationManifestService.getByServiceId(service.getAppId(), service.getUuid(), appManifestKind);
  }

  private boolean isAzureAppServiceDeploymentType(Service service) {
    return service != null && DeploymentType.AZURE_WEBAPP == service.getDeploymentType();
  }

  private FolderNode generateManifestFileFolderNode(
      String accountId, Service service, ApplicationManifest applicationManifest, DirectoryPath manifestFilePath) {
    if (applicationManifest != null) {
      List<ManifestFile> manifestFiles =
          applicationManifestService.getManifestFilesByAppManifestId(service.getAppId(), applicationManifest.getUuid());
      return generateManifestFileFolderNode(accountId, service, manifestFiles, manifestFilePath);
    }

    return null;
  }

  @Override
  public FolderNode generateManifestFileFolderNode(
      String accountId, Service service, List<ManifestFile> manifestFiles, DirectoryPath manifestFilePath) {
    FolderNode manifestFileFolder =
        new FolderNode(accountId, MANIFEST_FILE_FOLDER, ManifestFile.class, manifestFilePath, service.getAppId());

    List<YamlManifestFileNode> manifestFilesDirectUnderFiles = new ArrayList<>();
    Map<String, YamlManifestFileNode> map = new HashMap<>();

    processManifestFiles(manifestFiles, map, manifestFilesDirectUnderFiles);

    if (isNotEmpty(map)) {
      for (Map.Entry<String, YamlManifestFileNode> entry : map.entrySet()) {
        addYamlDirectoryNode(
            accountId, service.getAppId(), service.getUuid(), manifestFileFolder, entry.getValue(), manifestFilePath);
      }
    }

    manifestFilesDirectUnderFiles.forEach(yamlManifestFileNode
        -> manifestFileFolder.addChild(new ServiceLevelYamlNode(accountId, yamlManifestFileNode.getUuId(),
            service.getAppId(), service.getUuid(), yamlManifestFileNode.getName(), ManifestFile.class,
            manifestFilePath.clone().add(yamlManifestFileNode.getName()), Type.APPLICATION_MANIFEST_FILE)));

    return manifestFileFolder;
  }

  private FolderNode generateManifestFileFoldeNodeForServiceView(String accountId, Service service) {
    ApplicationManifest applicationManifest = getApplicationManifestByService(service);
    DirectoryPath manifestFilePath = new DirectoryPath(MANIFEST_FILE_FOLDER);
    return generateManifestFileFolderNode(accountId, service, applicationManifest, manifestFilePath);
  }

  private void addYamlDirectoryNode(String accountId, String appId, String serviceId, FolderNode parentFolder,
      YamlManifestFileNode node, DirectoryPath parentPath) {
    DirectoryPath directoryPath = parentPath.clone().add(node.getName());
    FolderNode direcotryFolder = new FolderNode(accountId, node.getName(), ManifestFile.class, directoryPath, appId);
    parentFolder.addChild(direcotryFolder);

    for (YamlManifestFileNode childNode : node.getChildNodesMap().values()) {
      if (childNode.isDir()) {
        addYamlDirectoryNode(accountId, appId, serviceId, direcotryFolder, childNode, directoryPath);
      } else {
        direcotryFolder.addChild(
            new ServiceLevelYamlNode(accountId, childNode.getUuId(), appId, serviceId, childNode.getName(),
                ManifestFile.class, directoryPath.clone().add(childNode.getName()), Type.APPLICATION_MANIFEST_FILE));
      }
    }
  }

  private void sortManifestFiles(List<ManifestFile> manifestFiles) {
    Collections.sort(manifestFiles, new Comparator<ManifestFile>() {
      @Override
      public int compare(ManifestFile lhs, ManifestFile rhs) {
        String[] lhsNames = lhs.getFileName().split("/");
        String[] rhsNames = rhs.getFileName().split("/");

        if (lhsNames.length != rhsNames.length) {
          return rhsNames.length - lhsNames.length;
        }

        for (int i = 0; i < lhsNames.length; i++) {
          if (!lhsNames[i].equals(rhsNames[i])) {
            return lhsNames[i].compareTo(rhsNames[i]);
          }
        }
        return -1;
      }
    });
  }

  private void processManifestFiles(List<ManifestFile> manifestFiles, Map<String, YamlManifestFileNode> map,
      List<YamlManifestFileNode> fileNodesUnderFiles) {
    if (isNotEmpty(manifestFiles)) {
      sortManifestFiles(manifestFiles);

      manifestFiles.forEach(manifestFile -> {
        String name = manifestFile.getFileName();
        String[] names = name.split("/");

        if (names.length == 1) {
          fileNodesUnderFiles.add(YamlManifestFileNode.builder()
                                      .isDir(false)
                                      .name(names[0])
                                      .content(manifestFile.getFileContent())
                                      .uuId(manifestFile.getUuid())
                                      .build());
        } else {
          YamlManifestFileNode previousNode = null;
          for (int index = 0; index < names.length - 1; index++) {
            YamlManifestFileNode node = YamlManifestFileNode.builder()
                                            .isDir(true)
                                            .name(names[index])
                                            .childNodesMap(new LinkedHashMap<>())
                                            .build();

            if (previousNode == null) {
              YamlManifestFileNode startingNode = map.putIfAbsent(node.getName(), node);
              // It means it was in the map
              if (startingNode != null) {
                node = startingNode;
              }
            } else {
              previousNode.getChildNodesMap().putIfAbsent(names[index], node);
              node = previousNode.getChildNodesMap().get(names[index]);
            }

            previousNode = node;
          }

          // Add Actual File Node
          if (previousNode != null) {
            previousNode.getChildNodesMap().put(names[names.length - 1],
                YamlManifestFileNode.builder()
                    .isDir(false)
                    .name(names[names.length - 1])
                    .content(manifestFile.getFileContent())
                    .uuId(manifestFile.getUuid())
                    .build());
          }
        }
      });
    }
  }

  @VisibleForTesting
  FolderNode doEnvironments(
      Application app, DirectoryPath directoryPath, boolean applyPermissions, Set<String> allowedEnvs) {
    String accountId = app.getAccountId();
    FolderNode environmentsFolder = new FolderNode(
        accountId, ENVIRONMENTS_FOLDER, Environment.class, directoryPath.add(ENVIRONMENTS_FOLDER), app.getUuid());

    if (applyPermissions && isEmpty(allowedEnvs)) {
      return environmentsFolder;
    }
    PageRequest<Environment> pageRequestEnv = aPageRequest()
                                                  .addFilter(EnvironmentKeys.appId, EQ, app.getAppId())
                                                  .addOrder(EnvironmentKeys.name, SortOrder.OrderType.ASC)
                                                  .build();
    List<Environment> environments = environmentService.list(pageRequestEnv, false, null, false).getResponse();

    if (environments != null) {
      // iterate over environments
      for (Environment environment : environments) {
        if (applyPermissions && !allowedEnvs.contains(environment.getUuid())) {
          continue;
        }

        DirectoryPath envPath = directoryPath.clone();

        String yamlFileName = INDEX_YAML;
        FolderNode envFolder = new FolderNode(accountId, environment.getName(), Environment.class,
            envPath.add(environment.getName()), environment.getAppId());
        environmentsFolder.addChild(envFolder);
        envFolder.addChild(new AppLevelYamlNode(accountId, environment.getUuid(), environment.getAppId(), yamlFileName,
            Environment.class, envPath.clone().add(yamlFileName), Type.ENVIRONMENT));

        // ------------------- INFRA DEFINITION SECTION ------------------------
        DirectoryPath infraDefinitionPath = envPath.clone().add(INFRA_DEFINITION_FOLDER);
        FolderNode infraDefinitionFolder = new FolderNode(accountId, INFRA_DEFINITION_FOLDER,
            InfrastructureDefinition.class, infraDefinitionPath, environment.getAppId());
        envFolder.addChild(infraDefinitionFolder);
        PageRequest<InfrastructureDefinition> infrastructureDefinitionPageRequest =
            aPageRequest()
                .addFilter(InfrastructureDefinitionKeys.appId, Operator.EQ, environment.getAppId())
                .addFilter(InfrastructureDefinitionKeys.envId, Operator.EQ, environment.getUuid())
                .build();
        PageResponse<InfrastructureDefinition> infrastructureDefinitionsList =
            infrastructureDefinitionService.list(infrastructureDefinitionPageRequest);

        infrastructureDefinitionsList.forEach(infraDefinition -> {
          String infraDefinitionYamlFileName = infraDefinition.getName() + YAML_EXTENSION;
          infraDefinitionFolder.addChild(
              new EnvLevelYamlNode(accountId, infraDefinition.getUuid(), infraDefinition.getAppId(),
                  infraDefinition.getEnvId(), infraDefinitionYamlFileName, InfrastructureDefinition.class,
                  infraDefinitionPath.clone().add(infraDefinitionYamlFileName), Type.INFRA_DEFINITION));
        });
        // ------------------- END DEFINITION SECTION ------------------------

        // ------------------- CV CONFIG SECTION -----------------------

        DirectoryPath cvConfigPath = envPath.clone().add(CV_CONFIG_FOLDER);
        FolderNode cvConfigFolder =
            new FolderNode(accountId, CV_CONFIG_FOLDER, CVConfiguration.class, cvConfigPath, environment.getAppId());
        envFolder.addChild(cvConfigFolder);

        PageRequest<CVConfiguration> cvConfigPageRequest = aPageRequest()
                                                               .addFilter("appId", Operator.EQ, environment.getAppId())
                                                               .addFilter("envId", Operator.EQ, environment.getUuid())
                                                               .addFilter("isWorkflowConfig", Operator.NOT_EQ, true)
                                                               .build();
        List<CVConfiguration> cvConfigList = cvConfigurationService.listConfigurations(accountId, cvConfigPageRequest);

        // iterate over service commands
        cvConfigList.forEach(cvConfig -> {
          String cvConfigYamlFileName = cvConfig.getName() + YAML_EXTENSION;
          cvConfigFolder.addChild(new EnvLevelYamlNode(accountId, cvConfig.getUuid(), cvConfig.getAppId(),
              cvConfig.getEnvId(), cvConfigYamlFileName, CVConfiguration.class,
              cvConfigPath.clone().add(cvConfigYamlFileName), Type.SERVICE_CV_CONFIG));
        });

        // ------------------- END CV CONFIG SECTION -----------------------

        // ------------------- CONFIG FILES SECTION -----------------------
        DirectoryPath configFilesPath = envPath.clone().add(CONFIG_FILES_FOLDER);
        FolderNode configFilesFolder =
            new FolderNode(accountId, CONFIG_FILES_FOLDER, ConfigFile.class, configFilesPath, environment.getAppId());
        envFolder.addChild(configFilesFolder);

        List<ConfigFile> configFiles =
            configService.getConfigFileOverridesForEnv(environment.getAppId(), environment.getUuid());

        List<ConfigFile> globalConfigFiles =
            configFiles.stream().filter(config -> config.getEntityType() == ENVIRONMENT).collect(Collectors.toList());
        List<ConfigFile> serviceConfigFiles = configFiles.stream()
                                                  .filter(config -> config.getEntityType() == SERVICE_TEMPLATE)
                                                  .collect(Collectors.toList());

        final Map<String, List<ConfigFile>> servicesConfigFileMap =
            serviceConfigFiles.stream().collect(Collectors.groupingBy(ConfigFile::getEntityId));

        // For service overrides create folders.
        servicesConfigFileMap.forEach((serviceTemplate, configFilesOverrideForService) -> {
          String serviceName = serviceTemplateService.get(envFolder.getAppId(), serviceTemplate).getName();
          DirectoryPath servicesFolderPath = configFilesPath.clone().add(serviceName);
          FolderNode servicesFolder = new FolderNode(
              accountId, serviceName, SettingAttribute.class, servicesFolderPath, environment.getAppId());
          configFilesFolder.addChild(servicesFolder);

          configFilesOverrideForService.forEach(configFile -> {
            String configFileName = Utils.normalize(configFile.getRelativeFilePath()) + YAML_EXTENSION;
            servicesFolder.addChild(new EnvLevelYamlNode(accountId, configFile.getUuid(), configFile.getAppId(),
                environment.getUuid(), configFileName, ConfigFile.class, servicesFolderPath.clone().add(configFileName),
                Type.CONFIG_FILE_OVERRIDE));
          });
        });

        // For all service overrides create folder.
        if (!globalConfigFiles.isEmpty()) {
          DirectoryPath servicesFolderPath = configFilesPath.clone().add(GLOBAL_SERVICE_NAME_FOR_YAML);
          FolderNode servicesFolder = new FolderNode(accountId, GLOBAL_SERVICE_NAME_FOR_YAML, SettingAttribute.class,
              servicesFolderPath, environment.getAppId());
          configFilesFolder.addChild(servicesFolder);
          globalConfigFiles.forEach(configFile -> {
            String configFileName = Utils.normalize(configFile.getRelativeFilePath()) + YAML_EXTENSION;
            servicesFolder.addChild(new EnvLevelYamlNode(accountId, configFile.getUuid(), configFile.getAppId(),
                environment.getUuid(), configFileName, ConfigFile.class, servicesFolderPath.clone().add(configFileName),
                Type.CONFIG_FILE_OVERRIDE));
          });
        }

        // ------------------- END CONFIG FILES SECTION -----------------------

        // ------------------- VALUES FILES SECTION -----------------------
        FolderNode valuesFolder = generateEnvValuesFolder(accountId, environment, envPath, AppManifestKind.VALUES);
        if (valuesFolder != null) {
          envFolder.addChild(valuesFolder);
        }
        // ------------------- END VALUES FILES SECTION -----------------------

        // ------------------- PCF OVERRIDE SECTION -----------------------
        FolderNode pcfOverridesFolder = generateEnvPcfOverridesFolder(accountId, environment, envPath);
        if (pcfOverridesFolder != null) {
          envFolder.addChild(pcfOverridesFolder);
        }
        // ------------------- END PCF OVERRIDE SECTION -----------------------

        // ------------------- HELM OVERRIDE SECTION -----------------------
        FolderNode helmServiceOverridesFolder = generateEnvHelmOverridesFolder(accountId, environment, envPath);
        if (helmServiceOverridesFolder != null) {
          envFolder.addChild(helmServiceOverridesFolder);
        }
        // ------------------- END HELM OVERRIDE SECTION -----------------------

        // ------------------- KUSTOMIZE PATCHES FILES SECTION -----------------------
        FolderNode kustomizePatchesFolder =
            generateEnvValuesFolder(accountId, environment, envPath, AppManifestKind.KUSTOMIZE_PATCHES);
        if (kustomizePatchesFolder != null) {
          envFolder.addChild(kustomizePatchesFolder);
        }
        // ------------------- END KUSTOMIZE PATCHES FILES SECTION -----------------------

        // ------------------- OC PARAMS FILES SECTION -----------------------
        FolderNode ocParamsFolder = generateEnvValuesFolder(accountId, environment, envPath, AppManifestKind.OC_PARAMS);
        if (ocParamsFolder != null) {
          envFolder.addChild(ocParamsFolder);
        }
        // ------------------- END OC PARAMS FILES SECTION -----------------------

        // ------------------- AZURE APP SETTINGS FILES SECTION -----------------------
        FolderNode azureAppSettingsOverrideFolder =
            generateEnvAzureAppSettingsOverridesFolder(accountId, environment, envPath);
        if (azureAppSettingsOverrideFolder != null) {
          envFolder.addChild(azureAppSettingsOverrideFolder);
        }
        // ------------------- END AZURE APP SETTINGS FILES SECTION -----------------------

        // ------------------- AZURE CONN STRINGS FILES SECTION -----------------------
        FolderNode azureConnStringsOverrideFolder =
            generateEnvAzureConnStringsOverridesFolder(accountId, environment, envPath);
        if (azureConnStringsOverrideFolder != null) {
          envFolder.addChild(azureConnStringsOverrideFolder);
        }
        // ------------------- END AZURE APP SETTINGS FILES SECTION -----------------------
      }
    }

    return environmentsFolder;
  }

  private FolderNode generateEnvValuesFolder(
      String accountId, Environment env, DirectoryPath envPath, AppManifestKind appManifestKind) {
    List<ApplicationManifest> applicationManifests =
        applicationManifestService.getAllByEnvIdAndKind(env.getAppId(), env.getUuid(), appManifestKind);

    if (isEmpty(applicationManifests)) {
      return null;
    }

    DirectoryPath valuesPath = envPath.clone().add(appManifestKind.getYamlFolderName());
    FolderNode valuesFolder = new FolderNode(
        accountId, appManifestKind.getYamlFolderName(), ApplicationManifest.class, valuesPath, env.getAppId());
    ApplicationManifest applicationManifest =
        applicationManifestService.getByEnvId(env.getAppId(), env.getUuid(), appManifestKind);
    addValuesFolderFiles(accountId, env, valuesPath, valuesFolder, applicationManifest);

    // Fetch service specific environment value overrides
    FolderNode serviceSpecificValuesFolder =
        generateEnvServiceSpecificValuesFolder(accountId, env, valuesPath, appManifestKind);
    if (serviceSpecificValuesFolder != null) {
      valuesFolder.addChild(serviceSpecificValuesFolder);
    }

    return valuesFolder;
  }

  @VisibleForTesting
  FolderNode generateEnvPcfOverridesFolder(String accountId, Environment env, DirectoryPath envPath) {
    List<ApplicationManifest> applicationManifests =
        applicationManifestService.getAllByEnvIdAndKind(env.getAppId(), env.getUuid(), AppManifestKind.PCF_OVERRIDE);

    if (isEmpty(applicationManifests)) {
      return null;
    }

    DirectoryPath pcfOverridesPath = envPath.clone().add(PCF_OVERRIDES_FOLDER);
    FolderNode pcfOverridesFolder =
        new FolderNode(accountId, PCF_OVERRIDES_FOLDER, ApplicationManifest.class, pcfOverridesPath, env.getAppId());
    ApplicationManifest applicationManifest =
        applicationManifestService.getByEnvId(env.getAppId(), env.getUuid(), AppManifestKind.PCF_OVERRIDE);
    addPcfOverideFolderFiles(accountId, env, pcfOverridesPath, pcfOverridesFolder, applicationManifest);

    // Fetch service specific environment value overrides
    FolderNode serviceSpecificoverridesFolder =
        generateEnvServiceSpecificPcfOverridesFolder(accountId, env, pcfOverridesPath);
    if (serviceSpecificoverridesFolder != null) {
      pcfOverridesFolder.addChild(serviceSpecificoverridesFolder);
    }

    return pcfOverridesFolder;
  }

  @VisibleForTesting
  FolderNode generateEnvHelmOverridesFolder(String accountId, Environment env, DirectoryPath envPath) {
    List<ApplicationManifest> applicationManifests =
        applicationManifestService.getAllByEnvIdAndKind(env.getAppId(), env.getUuid(), HELM_CHART_OVERRIDE);

    if (isEmpty(applicationManifests)) {
      return null;
    }

    FolderNode helmOverridesFolder =
        getEnvOverrideFolderNode(envPath, accountId, env.getAppId(), HELM_CHART_OVERRIDE_FOLDER);
    ApplicationManifest envHelmChartOverride =
        applicationManifestService.getByEnvId(env.getAppId(), env.getUuid(), HELM_CHART_OVERRIDE);
    if (envHelmChartOverride != null) {
      helmOverridesFolder.addChild(new EnvLevelYamlNode(accountId, envHelmChartOverride.getUuid(), env.getAppId(),
          env.getUuid(), INDEX_YAML, ApplicationManifest.class,
          helmOverridesFolder.getDirectoryPath().clone().add(INDEX_YAML), Type.APPLICATION_MANIFEST));
    }

    // Fetch service specific environment overrides
    FolderNode serviceSpecificOverridesFolder = generateEnvServiceSpecificHelmOverridesFolder(
        accountId, env, helmOverridesFolder.getDirectoryPath(), applicationManifests);
    if (serviceSpecificOverridesFolder != null) {
      helmOverridesFolder.addChild(serviceSpecificOverridesFolder);
    }

    return helmOverridesFolder;
  }

  @VisibleForTesting
  FolderNode generateEnvAzureAppSettingsOverridesFolder(String accountId, Environment env, DirectoryPath envPath) {
    List<ApplicationManifest> applicationManifests = applicationManifestService.getAllByEnvIdAndKind(
        env.getAppId(), env.getUuid(), AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE);

    if (isEmpty(applicationManifests)) {
      return null;
    }

    DirectoryPath appSettingsOverridesPath = envPath.clone().add(AZURE_APP_SETTINGS_OVERRIDES_FOLDER);
    FolderNode appSettingsOverridesFolder = new FolderNode(accountId, AZURE_APP_SETTINGS_OVERRIDES_FOLDER,
        ApplicationManifest.class, appSettingsOverridesPath, env.getAppId());

    // add env overrides
    ApplicationManifest applicationManifest = applicationManifestService.getByEnvId(
        env.getAppId(), env.getUuid(), AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE);
    addIndexAndManifestFilesToFolderNode(
        accountId, env, appSettingsOverridesPath, appSettingsOverridesFolder, applicationManifest);

    // add service specific env overrides
    FolderNode serviceSpecificOverridesFolder = generateEnvServiceSpecificOverridesFolder(
        accountId, env, appSettingsOverridesPath, AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE);
    if (serviceSpecificOverridesFolder != null) {
      appSettingsOverridesFolder.addChild(serviceSpecificOverridesFolder);
    }

    return appSettingsOverridesFolder;
  }

  @VisibleForTesting
  FolderNode generateEnvAzureConnStringsOverridesFolder(String accountId, Environment env, DirectoryPath envPath) {
    List<ApplicationManifest> applicationManifests = applicationManifestService.getAllByEnvIdAndKind(
        env.getAppId(), env.getUuid(), AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE);

    if (isEmpty(applicationManifests)) {
      return null;
    }

    DirectoryPath connStringsOverridesPath = envPath.clone().add(AZURE_CONN_STRINGS_OVERRIDES_FOLDER);
    FolderNode connStringsOverridesFolder = new FolderNode(accountId, AZURE_CONN_STRINGS_OVERRIDES_FOLDER,
        ApplicationManifest.class, connStringsOverridesPath, env.getAppId());

    // add env overrides
    ApplicationManifest applicationManifest = applicationManifestService.getByEnvId(
        env.getAppId(), env.getUuid(), AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE);
    addIndexAndManifestFilesToFolderNode(
        accountId, env, connStringsOverridesPath, connStringsOverridesFolder, applicationManifest);

    // add service specific env overrides
    FolderNode serviceSpecificOverridesFolder = generateEnvServiceSpecificOverridesFolder(
        accountId, env, connStringsOverridesPath, AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE);
    if (serviceSpecificOverridesFolder != null) {
      connStringsOverridesFolder.addChild(serviceSpecificOverridesFolder);
    }

    return connStringsOverridesFolder;
  }

  private void addIndexAndManifestFilesToFolderNode(String accountId, Environment env, DirectoryPath directoryPath,
      FolderNode folderNode, ApplicationManifest applicationManifest) {
    if (applicationManifest != null) {
      folderNode.addChild(new EnvLevelYamlNode(accountId, applicationManifest.getUuid(), env.getAppId(), env.getUuid(),
          INDEX_YAML, ApplicationManifest.class, directoryPath.clone().add(INDEX_YAML), Type.APPLICATION_MANIFEST));

      if (StoreType.Local == applicationManifest.getStoreType()) {
        List<ManifestFile> manifestFiles =
            applicationManifestService.getManifestFilesByAppManifestId(env.getAppId(), applicationManifest.getUuid());

        if (isNotEmpty(manifestFiles)) {
          for (ManifestFile manifestFile : manifestFiles) {
            folderNode.addChild(new EnvLevelYamlNode(accountId, manifestFile.getUuid(), env.getAppId(), env.getUuid(),
                manifestFile.getFileName(), ManifestFile.class, directoryPath.clone().add(manifestFile.getFileName()),
                Type.APPLICATION_MANIFEST_FILE));
          }
        }
      }
    }
  }

  private FolderNode generateEnvServiceSpecificOverridesFolder(
      String accountId, Environment env, DirectoryPath valuesPath, AppManifestKind overrideManifestKind) {
    List<ApplicationManifest> applicationManifests =
        applicationManifestService.getAllByEnvIdAndKind(env.getAppId(), env.getUuid(), overrideManifestKind);

    if (isEmpty(applicationManifests)) {
      return null;
    }

    DirectoryPath serviceOverridesPath = valuesPath.clone().add(SERVICES_FOLDER);
    FolderNode overridesServicesFolder =
        new FolderNode(accountId, SERVICES_FOLDER, ApplicationManifest.class, serviceOverridesPath, env.getAppId());

    for (ApplicationManifest appManifest : applicationManifests) {
      Service service = isNotBlank(appManifest.getServiceId())
          ? serviceResourceService.get(env.getAppId(), appManifest.getServiceId(), false)
          : null;
      if (isNotBlank(appManifest.getEnvId()) && service != null) {
        DirectoryPath serviceFolderPath = serviceOverridesPath.clone().add(service.getName());
        FolderNode serviceFolder =
            new FolderNode(accountId, service.getName(), ApplicationManifest.class, serviceFolderPath, env.getAppId());
        overridesServicesFolder.addChild(serviceFolder);
        addIndexAndManifestFilesToFolderNode(accountId, env, serviceFolderPath, serviceFolder, appManifest);
      }
    }

    return overridesServicesFolder;
  }

  private FolderNode getEnvOverrideFolderNode(
      DirectoryPath envPath, String accountId, String appId, String folderName) {
    DirectoryPath pcfOverridesPath = envPath.clone().add(folderName);
    return new FolderNode(accountId, folderName, ApplicationManifest.class, pcfOverridesPath, appId);
  }

  private FolderNode getOverrideServiceFolder(DirectoryPath overridePath, String accountId, String appId) {
    DirectoryPath serviceOverridesPath = overridePath.clone().add(SERVICES_FOLDER);
    return new FolderNode(accountId, SERVICES_FOLDER, ApplicationManifest.class, serviceOverridesPath, appId);
  }

  private FolderNode generateEnvServiceSpecificValuesFolder(
      String accountId, Environment env, DirectoryPath valuesPath, AppManifestKind appManifestKind) {
    List<ApplicationManifest> applicationManifests =
        applicationManifestService.getAllByEnvIdAndKind(env.getAppId(), env.getUuid(), appManifestKind);

    if (isEmpty(applicationManifests)) {
      return null;
    }

    DirectoryPath serviceValuesPath = valuesPath.clone().add(SERVICES_FOLDER);
    FolderNode valuesServicesFolder =
        new FolderNode(accountId, SERVICES_FOLDER, ApplicationManifest.class, serviceValuesPath, env.getAppId());

    for (ApplicationManifest appManifest : applicationManifests) {
      Service service = isNotBlank(appManifest.getServiceId())
          ? serviceResourceService.get(env.getAppId(), appManifest.getServiceId(), false)
          : null;
      if (isNotBlank(appManifest.getEnvId()) && service != null) {
        DirectoryPath serviceFolderPath = serviceValuesPath.clone().add(service.getName());
        FolderNode serviceFolder =
            new FolderNode(accountId, service.getName(), ApplicationManifest.class, serviceFolderPath, env.getAppId());
        valuesServicesFolder.addChild(serviceFolder);
        addValuesFolderFiles(accountId, env, serviceFolderPath, serviceFolder, appManifest);
      }
    }

    return valuesServicesFolder;
  }

  private FolderNode generateEnvServiceSpecificPcfOverridesFolder(
      String accountId, Environment env, DirectoryPath valuesPath) {
    List<ApplicationManifest> applicationManifests =
        applicationManifestService.getAllByEnvIdAndKind(env.getAppId(), env.getUuid(), AppManifestKind.PCF_OVERRIDE);

    if (isEmpty(applicationManifests)) {
      return null;
    }

    DirectoryPath serviceOverridesPath = valuesPath.clone().add(SERVICES_FOLDER);
    FolderNode overridesServicesFolder =
        new FolderNode(accountId, SERVICES_FOLDER, ApplicationManifest.class, serviceOverridesPath, env.getAppId());

    for (ApplicationManifest appManifest : applicationManifests) {
      Service service = isNotBlank(appManifest.getServiceId())
          ? serviceResourceService.get(env.getAppId(), appManifest.getServiceId(), false)
          : null;
      if (isNotBlank(appManifest.getEnvId()) && service != null) {
        DirectoryPath serviceFolderPath = serviceOverridesPath.clone().add(service.getName());
        FolderNode serviceFolder =
            new FolderNode(accountId, service.getName(), ApplicationManifest.class, serviceFolderPath, env.getAppId());
        overridesServicesFolder.addChild(serviceFolder);
        addPcfOverideFolderFiles(accountId, env, serviceFolderPath, serviceFolder, appManifest);
      }
    }

    return overridesServicesFolder;
  }

  private FolderNode generateEnvServiceSpecificHelmOverridesFolder(
      String accountId, Environment env, DirectoryPath valuesPath, List<ApplicationManifest> applicationManifests) {
    if (isEmpty(applicationManifests)) {
      return null;
    }

    FolderNode overridesServicesFolder = getOverrideServiceFolder(valuesPath, accountId, env.getAppId());
    for (ApplicationManifest appManifest : applicationManifests) {
      if (isBlank(appManifest.getServiceId())) {
        continue;
      }

      Service service = isNotBlank(appManifest.getServiceId())
          ? serviceResourceService.get(env.getAppId(), appManifest.getServiceId(), false)
          : null;
      if (isNotBlank(appManifest.getEnvId()) && service != null) {
        DirectoryPath serviceFolderPath = overridesServicesFolder.getDirectoryPath().clone().add(service.getName());
        FolderNode serviceFolder =
            new FolderNode(accountId, service.getName(), ApplicationManifest.class, serviceFolderPath, env.getAppId());
        overridesServicesFolder.addChild(serviceFolder);
        serviceFolder.addChild(getEnvLevelYamlNodeForAppManifest(env, appManifest, serviceFolderPath));
      }
    }

    return overridesServicesFolder;
  }

  private EnvLevelYamlNode getEnvLevelYamlNodeForAppManifest(
      Environment env, ApplicationManifest applicationManifest, DirectoryPath serviceFolderPath) {
    return new EnvLevelYamlNode(env.getAccountId(), applicationManifest.getUuid(), env.getAppId(), env.getUuid(),
        INDEX_YAML, ApplicationManifest.class, serviceFolderPath.clone().add(INDEX_YAML), Type.APPLICATION_MANIFEST);
  }

  private void addValuesFolderFiles(String accountId, Environment env, DirectoryPath valuesPath,
      FolderNode valuesFolder, ApplicationManifest applicationManifest) {
    if (applicationManifest != null) {
      valuesFolder.addChild(
          new EnvLevelYamlNode(accountId, applicationManifest.getUuid(), env.getAppId(), env.getUuid(), INDEX_YAML,
              ApplicationManifest.class, valuesPath.clone().add(INDEX_YAML), Type.APPLICATION_MANIFEST));

      if (StoreType.Local == applicationManifest.getStoreType()) {
        List<ManifestFile> manifestFiles =
            applicationManifestService.getManifestFilesByAppManifestId(env.getAppId(), applicationManifest.getUuid());

        if (isNotEmpty(manifestFiles)) {
          for (ManifestFile manifestFile : manifestFiles) {
            valuesFolder.addChild(new EnvLevelYamlNode(accountId, manifestFile.getUuid(), env.getAppId(), env.getUuid(),
                manifestFile.getFileName(), ManifestFile.class, valuesPath.clone().add(manifestFile.getFileName()),
                Type.APPLICATION_MANIFEST_FILE));
          }
        }
      }
    }
  }

  private void addPcfOverideFolderFiles(String accountId, Environment env, DirectoryPath pcfOverridesPath,
      FolderNode valuesFolder, ApplicationManifest applicationManifest) {
    if (applicationManifest != null) {
      valuesFolder.addChild(
          new EnvLevelYamlNode(accountId, applicationManifest.getUuid(), env.getAppId(), env.getUuid(), INDEX_YAML,
              ApplicationManifest.class, pcfOverridesPath.clone().add(INDEX_YAML), Type.APPLICATION_MANIFEST));

      if (StoreType.Local == applicationManifest.getStoreType()) {
        List<ManifestFile> manifestFiles =
            applicationManifestService.getManifestFilesByAppManifestId(env.getAppId(), applicationManifest.getUuid());

        if (isNotEmpty(manifestFiles)) {
          for (ManifestFile manifestFile : manifestFiles) {
            valuesFolder.addChild(new EnvLevelYamlNode(accountId, manifestFile.getUuid(), env.getAppId(), env.getUuid(),
                manifestFile.getFileName(), ManifestFile.class,
                pcfOverridesPath.clone().add(manifestFile.getFileName()), Type.APPLICATION_MANIFEST_FILE));
          }
        }
      }
    }
  }

  private FolderNode doWorkflows(
      Application app, DirectoryPath directoryPath, boolean applyPermissions, Set<String> allowedWorkflows) {
    String accountId = app.getAccountId();
    FolderNode workflowsFolder =
        new FolderNode(accountId, WORKFLOWS_FOLDER, Workflow.class, directoryPath.add(WORKFLOWS_FOLDER), app.getUuid());

    if (applyPermissions && isEmpty(allowedWorkflows)) {
      return workflowsFolder;
    }

    PageRequest<Workflow> pageRequest =
        aPageRequest()
            .addFilter("appId", Operator.EQ, app.getAppId())
            .addOrder(Workflow.NAME_KEY, SortOrder.OrderType.ASC)
            .addFieldsIncluded(Pipeline.UUID_KEY, Pipeline.NAME_KEY, Pipeline.APP_ID_KEY2)
            .build();
    List<Workflow> workflows = workflowService.listWorkflowsWithoutOrchestration(pageRequest).getResponse();

    if (workflows != null) {
      // iterate over workflows
      for (Workflow workflow : workflows) {
        if (applyPermissions && !allowedWorkflows.contains(workflow.getUuid())) {
          continue;
        }

        DirectoryPath workflowPath = directoryPath.clone();
        String workflowYamlFileName = workflow.getName() + YAML_EXTENSION;
        workflowsFolder.addChild(new AppLevelYamlNode(accountId, workflow.getUuid(), workflow.getAppId(),
            workflowYamlFileName, Workflow.class, workflowPath.add(workflowYamlFileName), Type.WORKFLOW));
      }
    }

    return workflowsFolder;
  }

  private FolderNode doPipelines(
      Application app, DirectoryPath directoryPath, boolean applyPermissions, Set<String> allowedPipelines) {
    String accountId = app.getAccountId();
    FolderNode pipelinesFolder =
        new FolderNode(accountId, PIPELINES_FOLDER, Pipeline.class, directoryPath.add(PIPELINES_FOLDER), app.getUuid());

    if (applyPermissions && isEmpty(allowedPipelines)) {
      return pipelinesFolder;
    }

    PageRequest<Pipeline> pageRequest =
        aPageRequest()
            .addFilter("appId", Operator.EQ, app.getAppId())
            .addOrder(Pipeline.NAME_KEY, SortOrder.OrderType.ASC)
            .addFieldsIncluded(Pipeline.UUID_KEY, Pipeline.NAME_KEY, Pipeline.APP_ID_KEY2)
            .build();
    if (featureFlagService.isEnabled(FeatureName.SPG_2K_DEFAULT_PAGE_SIZE, accountId)) {
      pageRequest.setLimit(PageRequest.LIMIT_2K_PAGE_SIZE);
    }

    List<Pipeline> pipelines = pipelineService.listPipelines(pageRequest).getResponse();

    if (pipelines != null) {
      // iterate over pipelines
      for (Pipeline pipeline : pipelines) {
        if (applyPermissions && !allowedPipelines.contains(pipeline.getUuid())) {
          continue;
        }

        DirectoryPath pipelinePath = directoryPath.clone();
        String pipelineYamlFileName = pipeline.getName() + YAML_EXTENSION;
        pipelinesFolder.addChild(new AppLevelYamlNode(accountId, pipeline.getUuid(), pipeline.getAppId(),
            pipelineYamlFileName, Pipeline.class, pipelinePath.add(pipelineYamlFileName), Type.PIPELINE));
      }
    }

    return pipelinesFolder;
  }

  private FolderNode doEventConfigs(Application app, DirectoryPath directoryPath) {
    String accountId = app.getAccountId();
    FolderNode eventsFolder = new FolderNode(accountId, CG_EVENT_CONFIG_FOLDER, CgEventConfig.class,
        directoryPath.add(CG_EVENT_CONFIG_FOLDER), app.getUuid());

    List<CgEventConfig> cgEventConfigs = eventConfigService.listAllEventsConfig(accountId, app.getAppId());
    if (cgEventConfigs != null) {
      for (CgEventConfig cgEventConfig : cgEventConfigs) {
        DirectoryPath eventConfigPath = directoryPath.clone();
        String eventConfigFileName = cgEventConfig.getName() + YAML_EXTENSION;
        eventsFolder.addChild(new AppLevelYamlNode(accountId, cgEventConfig.getUuid(), cgEventConfig.getAppId(),
            eventConfigFileName, CgEventConfig.class, eventConfigPath.add(eventConfigFileName), Type.EVENT_RULE));
      }
    }
    return eventsFolder;
  }

  private FolderNode doTriggers(Application app, DirectoryPath directoryPath) {
    String accountId = app.getAccountId();

    FolderNode triggersFolder =
        new FolderNode(accountId, TRIGGER_FOLDER, Trigger.class, directoryPath.add(TRIGGER_FOLDER), app.getUuid());
    PageRequest<Trigger> pageRequest = aPageRequest().addFilter("appId", Operator.EQ, app.getAppId()).build();
    List<Trigger> triggers = triggerService.list(pageRequest, false, null).getResponse();

    if (triggers != null) {
      for (Trigger trigger : triggers) {
        DirectoryPath triggerPath = directoryPath.clone();
        String triggerYamlFileName = trigger.getName() + YAML_EXTENSION;
        triggersFolder.addChild(new AppLevelYamlNode(accountId, trigger.getUuid(), trigger.getAppId(),
            triggerYamlFileName, Trigger.class, triggerPath.add(triggerYamlFileName), Type.TRIGGER));
      }
    }

    return triggersFolder;
  }

  private FolderNode doProvisioners(
      Application app, DirectoryPath directoryPath, boolean applyPermissions, Set<String> allowedProvisioners) {
    String accountId = app.getAccountId();
    FolderNode provisionersFolder = new FolderNode(accountId, PROVISIONERS_FOLDER, InfrastructureProvisioner.class,
        directoryPath.add(PROVISIONERS_FOLDER), app.getUuid());

    if (applyPermissions && isEmpty(allowedProvisioners)) {
      return provisionersFolder;
    }

    PageRequest<InfrastructureProvisioner> pageRequest =
        aPageRequest().addFilter("appId", Operator.EQ, app.getAppId()).build();
    List<InfrastructureProvisioner> infrastructureProvisioners =
        infrastructureProvisionerService.list(pageRequest).getResponse();

    if (infrastructureProvisioners != null) {
      for (InfrastructureProvisioner provisioner : infrastructureProvisioners) {
        if (applyPermissions && !allowedProvisioners.contains(provisioner.getUuid())) {
          continue;
        }

        DirectoryPath provisionerPath = directoryPath.clone();
        String provisionerYamlFileName = provisioner.getName() + YAML_EXTENSION;
        provisionersFolder.addChild(
            new AppLevelYamlNode(accountId, provisioner.getUuid(), provisioner.getAppId(), provisionerYamlFileName,
                InfrastructureProvisioner.class, provisionerPath.add(provisionerYamlFileName), Type.PROVISIONER));
      }
    }

    return provisionersFolder;
  }

  private boolean shouldLoadSettingAttributes(
      boolean applyPermissions, AccountPermissionSummary accountPermissionSummary) {
    if (applyPermissions) {
      if (accountPermissionSummary != null) {
        Set<PermissionType> accountPermissions = accountPermissionSummary.getPermissions();
        if (isNotEmpty(accountPermissions)) {
          if (!accountPermissions.contains(PermissionType.ACCOUNT_MANAGEMENT)) {
            return false;
          }
        } else {
          return false;
        }
      } else {
        return false;
      }
    }
    return true;
  }

  @VisibleForTesting
  FolderNode doCloudProviders(String accountId, DirectoryPath directoryPath) {
    // create cloud providers (and physical data centers)
    FolderNode cloudProvidersFolder = new FolderNode(accountId, CLOUD_PROVIDERS_FOLDER, SettingAttribute.class,
        directoryPath.add(YamlConstants.CLOUD_PROVIDERS_FOLDER));

    // TODO - should these use AwsConfig GcpConfig, etc. instead?
    doCloudProviderType(accountId, cloudProvidersFolder, SettingVariableTypes.AWS, directoryPath.clone());
    doCloudProviderType(accountId, cloudProvidersFolder, SettingVariableTypes.GCP, directoryPath.clone());
    doCloudProviderType(accountId, cloudProvidersFolder, SettingVariableTypes.AZURE, directoryPath.clone());
    doCloudProviderType(
        accountId, cloudProvidersFolder, SettingVariableTypes.KUBERNETES_CLUSTER, directoryPath.clone());
    doCloudProviderType(accountId, cloudProvidersFolder, PHYSICAL_DATA_CENTER, directoryPath.clone());
    doCloudProviderType(accountId, cloudProvidersFolder, SettingVariableTypes.PCF, directoryPath.clone());
    doCloudProviderType(accountId, cloudProvidersFolder, SettingVariableTypes.SPOT_INST, directoryPath.clone());
    doCloudProviderType(accountId, cloudProvidersFolder, SettingVariableTypes.RANCHER, directoryPath.clone());
    sort(cloudProvidersFolder.getChildren(), new DirectoryComparator());
    return cloudProvidersFolder;
  }

  private void doCloudProviderType(
      String accountId, FolderNode parentFolder, SettingVariableTypes type, DirectoryPath directoryPath) {
    List<SettingAttribute> settingAttributes;
    settingAttributes = settingsService.listAllSettingAttributesByType(accountId, type.name());
    if (settingAttributes != null) {
      // iterate over providers
      for (SettingAttribute settingAttribute : settingAttributes) {
        DirectoryPath cpPath = directoryPath.clone();
        String yamlFileName = getSettingAttributeYamlName(settingAttribute);
        parentFolder.addChild(new SettingAttributeYamlNode(accountId, settingAttribute.getUuid(),
            settingAttribute.getValue().getType(), yamlFileName, SettingAttribute.class, cpPath.add(yamlFileName)));
      }
    }
  }

  private String getDeploymentFreezeConfigName(GovernanceFreezeConfig governanceFreezeConfig) {
    return governanceFreezeConfig.getName() + YAML_EXTENSION;
  }

  private String getSettingAttributeYamlName(SettingAttribute settingAttribute) {
    return settingAttribute.getName() + YAML_EXTENSION;
  }

  private String getArtifactStreamYamlName(ArtifactStream artifactStream) {
    return artifactStream.getName() + YAML_EXTENSION;
  }

  @VisibleForTesting
  FolderNode doArtifactServers(String accountId, DirectoryPath directoryPath) {
    // create artifact servers
    FolderNode artifactServersFolder = new FolderNode(accountId, ARTIFACT_SOURCES_FOLDER, SettingAttribute.class,
        directoryPath.add(YamlConstants.ARTIFACT_SERVERS_FOLDER));

    doArtifactServerType(accountId, artifactServersFolder, SettingVariableTypes.JENKINS, directoryPath.clone());
    doArtifactServerType(accountId, artifactServersFolder, SettingVariableTypes.BAMBOO, directoryPath.clone());
    doArtifactServerType(accountId, artifactServersFolder, SettingVariableTypes.DOCKER, directoryPath.clone());
    doArtifactServerType(accountId, artifactServersFolder, SettingVariableTypes.NEXUS, directoryPath.clone());
    doArtifactServerType(accountId, artifactServersFolder, SettingVariableTypes.ARTIFACTORY, directoryPath.clone());
    doArtifactServerType(accountId, artifactServersFolder, SettingVariableTypes.HTTP_HELM_REPO, directoryPath.clone());
    doArtifactServerType(accountId, artifactServersFolder, SettingVariableTypes.OCI_HELM_REPO, directoryPath.clone());
    doArtifactServerType(
        accountId, artifactServersFolder, SettingVariableTypes.AMAZON_S3_HELM_REPO, directoryPath.clone());
    doArtifactServerType(accountId, artifactServersFolder, SettingVariableTypes.GCS_HELM_REPO, directoryPath.clone());
    doArtifactServerType(
        accountId, artifactServersFolder, SettingVariableTypes.AZURE_ARTIFACTS_PAT, directoryPath.clone());
    doArtifactServerType(accountId, artifactServersFolder, SettingVariableTypes.SMB, directoryPath.clone());
    doArtifactServerType(accountId, artifactServersFolder, SettingVariableTypes.SFTP, directoryPath.clone());
    sort(artifactServersFolder.getChildren(), new DirectoryComparator());
    return artifactServersFolder;
  }

  private void doArtifactServerType(
      String accountId, FolderNode parentFolder, SettingVariableTypes type, DirectoryPath directoryPath) {
    List<SettingAttribute> settingAttributes;
    settingAttributes = settingsService.listAllSettingAttributesByType(accountId, type.name());
    if (settingAttributes != null) {
      // iterate over providers
      for (SettingAttribute settingAttribute : settingAttributes) {
        DirectoryPath asPath = directoryPath.clone();
        String yamlFileName = getSettingAttributeYamlName(settingAttribute);
        parentFolder.addChild(new SettingAttributeYamlNode(accountId, settingAttribute.getUuid(),
            settingAttribute.getValue().getType(), yamlFileName, SettingAttribute.class, asPath.add(yamlFileName)));
      }
    }
  }

  @VisibleForTesting
  FolderNode doCollaborationProviders(String accountId, DirectoryPath directoryPath) {
    // create collaboration providers
    FolderNode collaborationProvidersFolder = new FolderNode(accountId, COLLABORATION_PROVIDERS_FOLDER,
        SettingAttribute.class, directoryPath.add(YamlConstants.COLLABORATION_PROVIDERS_FOLDER));

    doCollaborationProviderType(
        accountId, collaborationProvidersFolder, SettingVariableTypes.SMTP, directoryPath.clone());
    doCollaborationProviderType(
        accountId, collaborationProvidersFolder, SettingVariableTypes.SLACK, directoryPath.clone());
    doCollaborationProviderType(
        accountId, collaborationProvidersFolder, SettingVariableTypes.JIRA, directoryPath.clone());
    doCollaborationProviderType(
        accountId, collaborationProvidersFolder, SettingVariableTypes.SERVICENOW, directoryPath.clone());
    sort(collaborationProvidersFolder.getChildren(), new DirectoryComparator());
    return collaborationProvidersFolder;
  }

  private void doCollaborationProviderType(
      String accountId, FolderNode parentFolder, SettingVariableTypes type, DirectoryPath directoryPath) {
    List<SettingAttribute> settingAttributes;
    settingAttributes = settingsService.listAllSettingAttributesByType(accountId, type.name());

    if (settingAttributes != null) {
      // iterate over providers
      for (SettingAttribute settingAttribute : settingAttributes) {
        DirectoryPath cpPath = directoryPath.clone();
        String yamlFileName = getSettingAttributeYamlName(settingAttribute);
        parentFolder.addChild(new SettingAttributeYamlNode(accountId, settingAttribute.getUuid(),
            settingAttribute.getValue().getType(), yamlFileName, SettingAttribute.class, cpPath.add(yamlFileName)));
      }
    }
  }

  private boolean userHasNotificationPermissions(String accountId, User user) {
    UserPermissionInfo userPermissionInfo = authService.getUserPermissionInfo(accountId, user, false);
    Set<PermissionType> accountPermissions = userPermissionInfo.getAccountPermissionSummary().getPermissions();
    return accountPermissions.contains(PermissionType.USER_PERMISSION_READ);
  }

  private FolderNode doNotificationGroups(String accountId, DirectoryPath directoryPath) {
    // create notification groups
    FolderNode notificationGroupsFolder = new FolderNode(
        accountId, NOTIFICATION_GROUPS_FOLDER, NotificationGroup.class, directoryPath.add(NOTIFICATION_GROUPS_FOLDER));
    if (!userHasNotificationPermissions(accountId, UserThreadLocal.get())) {
      return notificationGroupsFolder;
    }

    List<NotificationGroup> notificationGroups = notificationSetupService.listNotificationGroups(accountId);

    if (isNotEmpty(notificationGroups)) {
      // iterate over notification groups
      notificationGroups.forEach(notificationGroup -> {
        DirectoryPath notificationGroupPath = directoryPath.clone();
        String notificationGroupYamlFileName = notificationGroup.getName() + YAML_EXTENSION;
        notificationGroupsFolder.addChild(new AccountLevelYamlNode(accountId, notificationGroup.getUuid(),
            notificationGroupYamlFileName, NotificationGroup.class,
            notificationGroupPath.add(notificationGroupYamlFileName), Type.NOTIFICATION_GROUP));
      });
    }

    return notificationGroupsFolder;
  }

  @VisibleForTesting
  FolderNode doVerificationProviders(String accountId, DirectoryPath directoryPath) {
    // create verification providers
    FolderNode verificationProvidersFolder = new FolderNode(accountId, VERIFICATION_PROVIDERS_FOLDER,
        SettingAttribute.class, directoryPath.add(YamlConstants.VERIFICATION_PROVIDERS_FOLDER));

    doVerificationProviderType(
        accountId, verificationProvidersFolder, SettingVariableTypes.JENKINS, directoryPath.clone());
    doVerificationProviderType(
        accountId, verificationProvidersFolder, SettingVariableTypes.APP_DYNAMICS, directoryPath.clone());
    doVerificationProviderType(
        accountId, verificationProvidersFolder, SettingVariableTypes.SPLUNK, directoryPath.clone());
    doVerificationProviderType(accountId, verificationProvidersFolder, SettingVariableTypes.ELK, directoryPath.clone());
    doVerificationProviderType(
        accountId, verificationProvidersFolder, SettingVariableTypes.LOGZ, directoryPath.clone());
    doVerificationProviderType(
        accountId, verificationProvidersFolder, SettingVariableTypes.SUMO, directoryPath.clone());
    doVerificationProviderType(
        accountId, verificationProvidersFolder, SettingVariableTypes.NEW_RELIC, directoryPath.clone());
    doVerificationProviderType(
        accountId, verificationProvidersFolder, SettingVariableTypes.DYNA_TRACE, directoryPath.clone());
    doVerificationProviderType(
        accountId, verificationProvidersFolder, SettingVariableTypes.PROMETHEUS, directoryPath.clone());
    doVerificationProviderType(
        accountId, verificationProvidersFolder, SettingVariableTypes.APM_VERIFICATION, directoryPath.clone());
    doVerificationProviderType(
        accountId, verificationProvidersFolder, SettingVariableTypes.DATA_DOG, directoryPath.clone());
    doVerificationProviderType(
        accountId, verificationProvidersFolder, SettingVariableTypes.BUG_SNAG, directoryPath.clone());
    sort(verificationProvidersFolder.getChildren(), new DirectoryComparator());
    return verificationProvidersFolder;
  }

  private void doVerificationProviderType(
      String accountId, FolderNode parentFolder, SettingVariableTypes type, DirectoryPath directoryPath) {
    List<SettingAttribute> settingAttributes;
    settingAttributes = settingsService.listAllSettingAttributesByType(accountId, type.name());

    if (settingAttributes != null) {
      // iterate over providers
      for (SettingAttribute settingAttribute : settingAttributes) {
        DirectoryPath vpPath = directoryPath.clone();
        String yamlFileName = getSettingAttributeYamlName(settingAttribute);
        parentFolder.addChild(new SettingAttributeYamlNode(accountId, settingAttribute.getUuid(),
            settingAttribute.getValue().getType(), yamlFileName, SettingAttribute.class, vpPath.add(yamlFileName)));
      }
    }
  }

  @Override
  public FolderNode doTemplateLibrary(String accountId, DirectoryPath directoryPath, String appId,
      String templateLibraryFolderName, Type type, boolean applyPermissions, Set<String> allowedTemplates) {
    final FolderNode templateLibraryFolder = new FolderNode(
        accountId, templateLibraryFolderName, SettingAttribute.class, directoryPath.add(templateLibraryFolderName));

    if (applyPermissions && isEmpty(allowedTemplates)) {
      return templateLibraryFolder;
    }

    // get the whole template folder tree  structure
    final TemplateFolder templateTree =
        templateService.getTemplateTree(accountId, appId, null, TEMPLATE_TYPES_WITH_YAML_SUPPORT);
    // get all the templates and group them by folderId
    final PageRequestBuilder pageRequestBuilder = aPageRequest()
                                                      .addFilter(Template.APP_ID_KEY, EQ, appId)
                                                      .addFilter(Template.ACCOUNT_ID_KEY2, EQ, accountId)
                                                      .withLimit(UNLIMITED);
    if (applyPermissions) {
      pageRequestBuilder.addFilter(TemplateKeys.uuid, IN, allowedTemplates.toArray());
    }
    final List<Template> templates = ListUtils.emptyIfNull(
        templateService
            .list(pageRequestBuilder.build(),
                Collections.singletonList(templateGalleryService.getAccountGalleryKey().name()), accountId, false)
            .getResponse());

    final Map<String, List<Template>> folderIdTemplateMap =
        templates.stream().collect(Collectors.groupingBy(Template::getFolderId));

    // walk through the folder structure and assign the templates to the respective folders by folderId
    // while walking the folder, add it to relevant folder node as well.
    final FolderNode templatesRootNode =
        processFolder(templateTree, folderIdTemplateMap, directoryPath, accountId, appId, type);
    if (templatesRootNode != null) {
      templateLibraryFolder.addChild(templatesRootNode);
    }
    return templateLibraryFolder;
  }

  private FolderNode processFolder(TemplateFolder rootFolder, Map<String, List<Template>> folderIdTemplateMap,
      DirectoryPath directoryPath, String accountId, String appId, Type type) {
    if (rootFolder == null) {
      return null;
    }
    final DirectoryPath dirClone = directoryPath.clone();
    final FolderNode rootYamlFolder =
        new FolderNode(accountId, rootFolder.getName(), SettingAttribute.class, dirClone.add(rootFolder.getName()));
    // process child folders
    ListUtils.emptyIfNull(rootFolder.getChildren()).forEach(childTemplateFolder -> {
      rootYamlFolder.addChild(
          processFolder(childTemplateFolder, folderIdTemplateMap, dirClone, accountId, appId, type));
    });
    // add all the template nodes here
    folderIdTemplateMap.getOrDefault(rootFolder.getUuid(), Collections.emptyList())
        .stream()
        .filter(template -> TEMPLATE_TYPES_WITH_YAML_SUPPORT.contains(template.getType()))
        .forEach(template -> {
          final DirectoryPath templatePath = dirClone.clone();
          final String templateYamlFileName = template.getName() + YAML_EXTENSION;
          rootYamlFolder.addChild(type == Type.GLOBAL_TEMPLATE_LIBRARY
                  ? new AccountLevelYamlNode(accountId, template.getUuid(), templateYamlFileName, Template.class,
                      templatePath.add(templateYamlFileName), type)
                  : new AppLevelYamlNode(accountId, template.getUuid(), appId, templateYamlFileName, Template.class,
                      templatePath.add(templateYamlFileName), type));
        });

    return rootYamlFolder;
  }

  @Override
  public String getRootPath() {
    return SETUP_FOLDER;
  }

  @Override
  public String getRootPathByApp(Application app) {
    return getRootPath() + PATH_DELIMITER + APPLICATIONS_FOLDER + PATH_DELIMITER + app.getName();
  }

  @Override
  public String getRootPathByService(Service service) {
    Application app = appService.get(service.getAppId());
    return getRootPathByService(service, getRootPathByApp(app));
  }

  @Override
  public String getRootPathByApplicationManifest(ApplicationManifest applicationManifest) {
    return getRootPathForAppManifest(applicationManifest, false);
  }

  @Override
  public String getRootPathByManifestFile(ManifestFile manifestFile, ApplicationManifest applicationManifest) {
    if (applicationManifest == null) {
      applicationManifest =
          applicationManifestService.getById(manifestFile.getAppId(), manifestFile.getApplicationManifestId());
    }

    return getRootPathForAppManifest(applicationManifest, true);
  }

  private String getRootPathForAppManifest(ApplicationManifest applicationManifest, boolean fromManifestFile) {
    Application application = appService.get(applicationManifest.getAppId());

    Service service = null;
    Environment environment = null;
    if (isNotBlank(applicationManifest.getServiceId())) {
      service =
          serviceResourceService.getWithDetails(applicationManifest.getAppId(), applicationManifest.getServiceId());
    }
    if (isNotBlank(applicationManifest.getEnvId())) {
      environment = environmentService.get(applicationManifest.getAppId(), applicationManifest.getEnvId(), true);
    }

    AppManifestSource appManifestSource = applicationManifestService.getAppManifestType(applicationManifest);
    switch (appManifestSource) {
      case ENV_SERVICE:
        notNullCheck("Environment not found", environment);
        return new StringBuilder(getRootPathByEnvironment(environment, getRootPathByApp(application)))
            .append(PATH_DELIMITER)
            .append(fetchManifestEnvOverrideFolderName(applicationManifest))
            .append(PATH_DELIMITER)
            .append(SERVICES_FOLDER)
            .append(PATH_DELIMITER)
            .append(service.getName())
            .toString();
      case ENV:
        notNullCheck("Environment not found", environment);
        return new StringBuilder(getRootPathByEnvironment(environment, getRootPathByApp(application)))
            .append(PATH_DELIMITER)
            .append(fetchManifestEnvOverrideFolderName(applicationManifest))
            .toString();
      case SERVICE:
        notNullCheck("Service not found", service);

        StringBuilder builder = new StringBuilder(getRootPathByService(service, getRootPathByApp(application)))
                                    .append(PATH_DELIMITER)
                                    .append(getServiceFolderForManifest(applicationManifest.getKind()));

        if (shouldGoInsideManifestFiles(fromManifestFile, applicationManifest.getKind())) {
          builder.append(PATH_DELIMITER).append(MANIFEST_FILE_FOLDER);
        }

        return builder.toString();
      default:
        unhandled(appManifestSource);
        throw new InvalidRequestException("Invalid application manifest type");
    }
  }

  private boolean shouldGoInsideManifestFiles(boolean fromManifestFile, AppManifestKind kind) {
    return fromManifestFile && (kind != VALUES && kind != OC_PARAMS && kind != KUSTOMIZE_PATCHES);
  }

  private String getServiceFolderForManifest(AppManifestKind kind) {
    switch (kind) {
      case VALUES:
        return VALUES_FOLDER;
      case KUSTOMIZE_PATCHES:
        return KUSTOMIZE_PATCHES_FOLDER;
      case OC_PARAMS:
        return OC_PARAMS_FOLDER;
      case AZURE_APP_SERVICE_MANIFEST:
        return MANIFEST_FOLDER_APP_SERVICE;
      default:
        return MANIFEST_FOLDER;
    }
  }

  String fetchManifestEnvOverrideFolderName(ApplicationManifest applicationManifest) {
    String folderName;
    switch (applicationManifest.getKind()) {
      case PCF_OVERRIDE:
        folderName = PCF_OVERRIDES_FOLDER;
        break;

      case HELM_CHART_OVERRIDE:
        folderName = HELM_CHART_OVERRIDE_FOLDER;
        break;
      case OC_PARAMS:
        folderName = OC_PARAMS_FOLDER;
        break;
      case KUSTOMIZE_PATCHES:
        folderName = KUSTOMIZE_PATCHES_FOLDER;
        break;
      case AZURE_APP_SETTINGS_OVERRIDE:
        folderName = AZURE_APP_SETTINGS_OVERRIDES_FOLDER;
        break;
      case AZURE_CONN_STRINGS_OVERRIDE:
        folderName = AZURE_CONN_STRINGS_OVERRIDES_FOLDER;
        break;

      default:
        folderName = VALUES_FOLDER;
    }

    return folderName;
  }

  @Override
  public String getRootPathByService(Service service, String applicationPath) {
    return applicationPath + PATH_DELIMITER + SERVICES_FOLDER + PATH_DELIMITER + service.getName();
  }

  @Override
  public String getRootPathByServiceCommand(Service service, ServiceCommand serviceCommand) {
    return getRootPathByService(service) + PATH_DELIMITER + COMMANDS_FOLDER;
  }

  @Override
  public String getRootPathByContainerTask(Service service, ContainerTask containerTask) {
    return getRootPathByService(service) + PATH_DELIMITER + DEPLOYMENT_SPECIFICATION_FOLDER;
  }

  @Override
  public String getRootPathByHelmChartSpecification(Service service, HelmChartSpecification helmChartSpecification) {
    return getRootPathByService(service) + PATH_DELIMITER + DEPLOYMENT_SPECIFICATION_FOLDER;
  }

  @Override
  public String getRootPathByPcfServiceSpecification(Service service, PcfServiceSpecification pcfServiceSpecification) {
    return getRootPathByService(service) + PATH_DELIMITER + DEPLOYMENT_SPECIFICATION_FOLDER;
  }

  @Override
  public String getRootPathByLambdaSpec(Service service, LambdaSpecification lambdaSpecification) {
    return getRootPathByService(service) + PATH_DELIMITER + DEPLOYMENT_SPECIFICATION_FOLDER;
  }

  @Override
  public String getRootPathByUserDataSpec(Service service, UserDataSpecification userDataSpecification) {
    return getRootPathByService(service) + PATH_DELIMITER + DEPLOYMENT_SPECIFICATION_FOLDER;
  }

  @Override
  public <H, T> String getRootPathByConfigFile(H helperEntity, T entity) {
    if (helperEntity instanceof Service) {
      return getRootPathByService((Service) helperEntity) + PATH_DELIMITER + CONFIG_FILES_FOLDER;
    } else if (helperEntity instanceof Environment) {
      return getRootPathByEnvironment((Environment) helperEntity) + PATH_DELIMITER + CONFIG_FILES_FOLDER
          + appendServiceNameToEnvironementConfigFile(entity);
    }

    throw new InvalidRequestException(
        "Unhandled case while getting yaml config file root path for entity type " + entity.getClass().getSimpleName());
  }

  private <T> String appendServiceNameToEnvironementConfigFile(T entity) {
    String serviceName = null;
    if (((ConfigFile) entity).getEntityType() == SERVICE_TEMPLATE) {
      serviceName =
          serviceTemplateService.get(((ConfigFile) entity).getAppId(), ((ConfigFile) entity).getEntityId()).getName();
    }

    if (((ConfigFile) entity).getEntityType() == ENVIRONMENT) {
      serviceName = GLOBAL_SERVICE_NAME_FOR_YAML;
    }
    if (serviceName == null) {
      throw new InvalidRequestException("Config file type not found");
    }
    return PATH_DELIMITER + serviceName;
  }

  @Override
  public String getRootPathByConfigFileOverride(Environment environment) {
    return getRootPathByEnvironment(environment) + PATH_DELIMITER + CONFIG_FILES_FOLDER;
  }

  @Override
  public String getRootPathByEnvironment(Environment environment) {
    Application app = appService.get(environment.getAppId());
    return getRootPathByEnvironment(environment, getRootPathByApp(app));
  }

  @Override
  public String getRootPathByEnvironment(Environment environment, String appPath) {
    return appPath + PATH_DELIMITER + ENVIRONMENTS_FOLDER + PATH_DELIMITER + environment.getName();
  }

  @Override
  public String getRootPathByInfraMapping(InfrastructureMapping infraMapping) {
    Environment environment = environmentService.get(infraMapping.getAppId(), infraMapping.getEnvId(), false);
    notNullCheck("Environment is null", environment);
    return getRootPathByEnvironment(environment) + PATH_DELIMITER + INFRA_MAPPING_FOLDER;
  }

  @Override
  public String getRootPathByInfraDefinition(InfrastructureDefinition infrastructureDefinition) {
    Environment environment =
        environmentService.get(infrastructureDefinition.getAppId(), infrastructureDefinition.getEnvId(), false);
    notNullCheck("Environment is null", environment);
    return getRootPathByEnvironment(environment) + PATH_DELIMITER + INFRA_DEFINITION_FOLDER;
  }

  @Override
  public String getRootPathByCVConfiguration(CVConfiguration cvConfiguration) {
    Environment environment = environmentService.get(cvConfiguration.getAppId(), cvConfiguration.getEnvId(), false);
    notNullCheck("Environment is null", environment);
    return getRootPathByEnvironment(environment) + PATH_DELIMITER + CV_CONFIG_FOLDER;
  }

  @Override
  public String getRootPathByTrigger(Trigger trigger) {
    Application app = appService.get(trigger.getAppId());
    return getRootPathByApp(app) + PATH_DELIMITER + TRIGGER_FOLDER;
  }

  @Override
  public String getRootPathByPipeline(Pipeline pipeline) {
    Application app = appService.get(pipeline.getAppId());
    return getRootPathByApp(app) + PATH_DELIMITER + PIPELINES_FOLDER;
  }

  @Override
  public String getRootPathByWorkflow(Workflow workflow) {
    Application app = appService.get(workflow.getAppId());
    return getRootPathByApp(app) + PATH_DELIMITER + WORKFLOWS_FOLDER;
  }

  @Override
  public String getRootPathByInfraProvisioner(InfrastructureProvisioner provisioner) {
    Application app = appService.get(provisioner.getAppId());
    return getRootPathByApp(app) + PATH_DELIMITER + PROVISIONERS_FOLDER;
  }

  @Override
  public String getRootPathByArtifactStream(ArtifactStream artifactStream) {
    String appId = artifactStream.fetchAppId();
    if (!GLOBAL_APP_ID.equals(appId)) {
      // TODO: ASR: IMP: hack to make yaml push work as yaml changes require binding info but the binding info is
      // deleted in parallel
      Service service;
      if (artifactStream.getService() != null) {
        service = artifactStream.getService();
      } else {
        service = artifactStreamServiceBindingService.getService(appId, artifactStream.getUuid(), true);
      }

      return getRootPathByService(service) + PATH_DELIMITER + ARTIFACT_SOURCES_FOLDER;
    } else {
      SettingAttribute settingAttribute = settingsService.get(artifactStream.getSettingId());
      return getRootPathBySettingAttribute(settingAttribute) + PATH_DELIMITER + ARTIFACT_STREAMS_FOLDER;
    }
  }

  @Override
  public String getRootPathByEventConfig(CgEventConfig cgEventConfig) {
    Application app = appService.get(cgEventConfig.getAppId());
    return getRootPathByApp(app) + PATH_DELIMITER + CG_EVENT_CONFIG_FOLDER;
  }

  @Override
  public String getRootPathBySettingAttribute(
      SettingAttribute settingAttribute, SettingVariableTypes settingVariableType) {
    StringBuilder sb = new StringBuilder();
    sb.append(getRootPath()).append(PATH_DELIMITER);

    switch (settingVariableType) {
      // cloud providers
      case AWS:
      case GCP:
      case AZURE:
      case KUBERNETES_CLUSTER:
      case PHYSICAL_DATA_CENTER:
      case SPOT_INST:
      case PCF:
      case RANCHER:
        sb.append(CLOUD_PROVIDERS_FOLDER);
        break;

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
        sb.append(ARTIFACT_SOURCES_FOLDER);
        break;

      // collaboration providers
      case SMTP:
      case SLACK:
      case JIRA:
      case SERVICENOW:
        sb.append(COLLABORATION_PROVIDERS_FOLDER);
        break;

      // load balancers
      case ELB:
        sb.append(LOAD_BALANCERS_FOLDER);
        break;

        // source repo providers
      case GIT:
        sb.append(SOURCE_REPO_PROVIDERS_FOLDER);
        break;

      // verification providers
      // JENKINS is also a (logical) part of this group
      case APP_DYNAMICS:
      case SPLUNK:
      case ELK:
      case LOGZ:
      case SUMO:
      case NEW_RELIC:
      case DYNA_TRACE:
      case PROMETHEUS:
        sb.append(VERIFICATION_PROVIDERS_FOLDER);
        break;
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
        break;
      case STRING:
        String path = getRootPathByDefaultVariable(settingAttribute);
        if (path != null) {
          sb.append(path);
        }
        break;
      default:
        log.warn("Unknown SettingVariable type:" + settingVariableType);
    }
    return sb.toString();
  }

  @Override
  public String getRootPathByNotificationGroup(NotificationGroup notificationGroup) {
    return getRootPath() + PATH_DELIMITER + NOTIFICATION_GROUPS_FOLDER;
  }

  @Override
  public String getRootPathByTemplate(Template template) {
    if (template.getAppId().equals(GLOBAL_APP_ID)) {
      return getRootPath() + PATH_DELIMITER + GLOBAL_TEMPLATE_LIBRARY_FOLDER + PATH_DELIMITER
          + templateService.getTemplateFolderPathString(template);
    }
    Application app = appService.get(template.getAppId());
    return getRootPathByApp(app) + PATH_DELIMITER + APPLICATION_TEMPLATE_LIBRARY_FOLDER + PATH_DELIMITER
        + templateService.getTemplateFolderPathString(template);
  }

  @Override
  public String getRootPathBySettingAttribute(SettingAttribute settingAttribute) {
    return getRootPathBySettingAttribute(settingAttribute, settingAttribute.getValue().getSettingType());
  }

  private String getRootPathByDefaultVariable(SettingAttribute settingAttribute) {
    if (GLOBAL_APP_ID.equals(settingAttribute.getAppId())) {
      // If its global app id, returning null since the defaults.yaml should be put in the root path (Setup)
      return null;
    } else {
      Application application = appService.get(settingAttribute.getAppId());
      return APPLICATIONS_FOLDER + PATH_DELIMITER + application.getName();
    }
  }

  @Override
  public <R, T> String obtainEntityRootPath(R helperEntity, T entity) {
    // Special handling for few entities
    // Don't change the order
    if (entity instanceof ConfigFile) {
      return getRootPathByConfigFile(helperEntity, entity);
    } else if (helperEntity instanceof Service) {
      if (entity instanceof ServiceCommand) {
        return getRootPathByServiceCommand((Service) helperEntity, (ServiceCommand) entity);
      }

      return getEntitySpecPathByService((Service) helperEntity);
    }

    if (entity instanceof Environment) {
      return getRootPathByEnvironment((Environment) entity);
    } else if (entity instanceof NotificationGroup) {
      return getRootPathByNotificationGroup((NotificationGroup) entity);
    } else if (entity instanceof Pipeline) {
      return getRootPathByPipeline((Pipeline) entity);
    } else if (entity instanceof Application) {
      return getRootPathByApp((Application) entity);
    } else if (entity instanceof InfrastructureMapping) {
      return getRootPathByInfraMapping((InfrastructureMapping) entity);
    } else if (entity instanceof InfrastructureDefinition) {
      return getRootPathByInfraDefinition((InfrastructureDefinition) entity);
    } else if (entity instanceof Workflow) {
      return getRootPathByWorkflow((Workflow) entity);
    } else if (entity instanceof ArtifactStream) {
      return getRootPathByArtifactStream((ArtifactStream) entity);
    } else if (entity instanceof InfrastructureProvisioner) {
      return getRootPathByInfraProvisioner((InfrastructureProvisioner) entity);
    } else if (entity instanceof Service) {
      return getRootPathByService((Service) entity);
    } else if (entity instanceof SettingAttribute) {
      return getRootPathBySettingAttribute((SettingAttribute) entity);
    } else if (entity instanceof ApplicationManifest) {
      return getRootPathByApplicationManifest((ApplicationManifest) entity);
    } else if (entity instanceof ManifestFile) {
      return getRootPathByManifestFile((ManifestFile) entity, (ApplicationManifest) helperEntity);
    } else if (entity instanceof CVConfiguration) {
      return getRootPathByCVConfiguration((CVConfiguration) entity);
    } else if (entity instanceof Trigger) {
      return getRootPathByTrigger((Trigger) entity);
    } else if (entity instanceof HarnessTag) {
      return getRootPathForTags();
    } else if (entity instanceof Template) {
      return getRootPathByTemplate((Template) entity);
    } else if (entity instanceof GovernanceConfig) {
      return getRootPathForGovernanceConfig();
    } else if (entity instanceof CgEventConfig) {
      return getRootPathByEventConfig((CgEventConfig) entity);
    } else if (entity instanceof UserGroup) {
      return SETUP_FOLDER;
    } else if (entity instanceof ApiKeyEntry) {
      return SETUP_FOLDER;
    } else if (entity instanceof Account) {
      return SETUP_FOLDER;
    }
    throw new InvalidRequestException(
        "Unhandled case while obtaining yaml entity root path for entity type " + entity.getClass().getSimpleName());
  }

  private String getEntitySpecPathByService(Service service) {
    return getRootPathByService(service) + PATH_DELIMITER + DEPLOYMENT_SPECIFICATION_FOLDER;
  }

  private void setApplicationGitConfig(String accountId, String applicationId, FolderNode appFolder) {
    // Setting app git sync
    YamlGitConfig yamlGitConfig = yamlGitService.get(accountId, applicationId, EntityType.APPLICATION);
    appFolder.setYamlGitConfig(yamlGitConfig);
  }

  private String getRootPathForTags() {
    return getRootPath() + PATH_DELIMITER;
  }

  private String getRootPathForGovernanceConfig() {
    return getRootPath() + PATH_DELIMITER + GOVERNANCE_FOLDER + PATH_DELIMITER;
  }

  class DirectoryComparator implements Comparator<DirectoryNode> {
    @Override
    public int compare(DirectoryNode o1, DirectoryNode o2) {
      return StringUtils.compare(o1.getName(), o2.getName());
    }
  }
}

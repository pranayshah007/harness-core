/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.unhandled;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.validation.PersistenceValidator.duplicateCheck;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.appmanifest.AppManifestKind.HELM_CHART_OVERRIDE;
import static software.wings.beans.appmanifest.AppManifestKind.K8S_MANIFEST;
import static software.wings.beans.appmanifest.AppManifestKind.VALUES;
import static software.wings.beans.appmanifest.ManifestFile.VALUES_YAML_KEY;
import static software.wings.beans.appmanifest.StoreType.CUSTOM;
import static software.wings.beans.appmanifest.StoreType.HelmChartRepo;
import static software.wings.beans.appmanifest.StoreType.HelmSourceRepo;
import static software.wings.beans.appmanifest.StoreType.KustomizeSourceRepo;
import static software.wings.beans.appmanifest.StoreType.Remote;
import static software.wings.beans.appmanifest.StoreType.VALUES_YAML_FROM_HELM_REPO;
import static software.wings.beans.yaml.YamlConstants.MANIFEST_FILE_FOLDER;
import static software.wings.delegatetasks.GitFetchFilesTask.GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT;
import static software.wings.helpers.ext.k8s.K8sManagerHelper.manifestFilesFromGitFetchFilesResult;
import static software.wings.settings.SettingVariableTypes.OCI_HELM_REPO;

import static java.lang.String.format;
import static java.lang.String.join;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.manifests.request.ManifestCollectionPTaskClientParams.ManifestCollectionPTaskClientParamsKeys;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.HelmVersion;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.Subject;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecord.PerpetualTaskRecordKeys;
import io.harness.queue.QueuePublisher;
import io.harness.reflection.ReflectionUtils;
import io.harness.validation.SuppressValidation;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.Base;
import software.wings.beans.Event.Type;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.GitFileConfig;
import software.wings.beans.GitFileConfig.GitFileConfigKeys;
import software.wings.beans.HelmChartConfig;
import software.wings.beans.HelmChartConfig.HelmChartConfigKeys;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ApplicationManifest.AppManifestSource;
import software.wings.beans.appmanifest.ApplicationManifest.ApplicationManifestKeys;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.ManifestFile.ManifestFileKeys;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.beans.settings.helm.HelmRepoConfig;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitCommandExecutionResponse.GitCommandStatus;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.helm.HelmHelper;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.prune.PruneEntityListener;
import software.wings.prune.PruneEvent;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.applicationmanifest.ApplicationManifestServiceObserver;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.service.intfc.ownership.OwnedByApplicationManifest;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.utils.ApplicationManifestUtils;
import software.wings.utils.CommandFlagConfigUtils;
import software.wings.yaml.directory.DirectoryNode;
import software.wings.yaml.directory.DirectoryPath;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jooq.tools.StringUtils;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateResults;

@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ApplicationManifestServiceImpl implements ApplicationManifestService {
  private static final int ALLOWED_SIZE_IN_BYTES = 1024 * 1024; // 1 MiB
  public static final String CHART_URL = "url";
  public static final String CHART_NAME = "chartName";
  private static final String BASE_PATH = "basePath";
  private static final String REPOSITORY_NAME = "repositoryName";
  private static final String BUCKET_NAME = "bucketName";
  public static final String VARIABLE_EXPRESSIONS_ERROR = "Variable expressions are not allowed in app manifest name";
  private static final String APP_MANIFEST_NAME = "appManifestName";

  @Inject private WingsPersistence wingsPersistence;
  @Inject private AppService appService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private YamlPushService yamlPushService;
  @Inject private DelegateService delegateService;
  @Inject private YamlDirectoryService yamlDirectoryService;
  @Inject private ApplicationManifestUtils applicationManifestUtils;
  @Inject private GitFileConfigHelperService gitFileConfigHelperService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject
  @Getter(onMethod = @__(@SuppressValidation))
  private Subject<ApplicationManifestServiceObserver> subject = new Subject<>();
  @Inject private HelmChartService helmChartService;
  @Inject private SettingsService settingsService;
  @Inject private HelmHelper helmHelper;
  @Inject private QueuePublisher<PruneEvent> pruneQueue;
  @Inject private TriggerService triggerService; // do not remove, needed for pruning logic.
  @Inject private RemoteObserverInformer remoteObserverInformer;

  private static long MAX_MANIFEST_FILES_PER_APPLICATION_MANIFEST = 50L;

  private static final Set<StoreType> STORE_TYPES_WITH_GIT_CONNECTOR =
      EnumSet.of(Remote, HelmSourceRepo, KustomizeSourceRepo);
  private static final Set<StoreType> STORE_TYPES_WITH_HELM_CONNECTOR = EnumSet.of(HelmChartRepo);

  @Override
  public ApplicationManifest create(ApplicationManifest applicationManifest) {
    return upsertApplicationManifest(applicationManifest, true);
  }

  @Override
  public ManifestFile createManifestFileByServiceId(ManifestFile manifestFile, String serviceId) {
    return createManifestFileByServiceId(manifestFile, serviceId, false);
  }

  @Override
  public ManifestFile createManifestFileByServiceId(
      ManifestFile manifestFile, String serviceId, boolean removeNamespace) {
    doFileValidations(manifestFile);
    if (removeNamespace) {
      removeNamespace(manifestFile);
    }
    return upsertManifestFileForService(manifestFile, serviceId, true);
  }

  @Override
  public ManifestFile createManifestFileByServiceId(ManifestFile manifestFile, String serviceId, AppManifestKind kind) {
    doFileValidations(manifestFile);

    String appId = manifestFile.getAppId();
    if (!serviceResourceService.exist(appId, serviceId)) {
      throw new InvalidRequestException(format("Service doesn't exist, service id: %s, app id: %s", serviceId, appId));
    }

    return upsertApplicationManifestFile(manifestFile, getByServiceId(appId, serviceId, kind), true);
  }

  @Override
  public ManifestFile updateManifestFileByServiceId(ManifestFile manifestFile, String serviceId) {
    return updateManifestFileByServiceId(manifestFile, serviceId, false);
  }

  @Override
  public ManifestFile updateManifestFileByServiceId(
      ManifestFile manifestFile, String serviceId, boolean removeNamespace) {
    doFileValidations(manifestFile);
    if (removeNamespace) {
      removeNamespace(manifestFile);
    }

    if (isAzureAppServiceManifestKind(serviceId)) {
      return upsertApplicationManifestFile(manifestFile,
          getByServiceId(manifestFile.getAppId(), serviceId, AppManifestKind.AZURE_APP_SERVICE_MANIFEST), false);
    }

    return upsertManifestFileForService(manifestFile, serviceId, false);
  }

  private boolean isAzureAppServiceManifestKind(String serviceId) {
    Service service = serviceResourceService.get(serviceId);
    return service != null && service.getDeploymentType() == DeploymentType.AZURE_WEBAPP;
  }

  @Override
  public ApplicationManifest update(ApplicationManifest applicationManifest) {
    return upsertApplicationManifest(applicationManifest, false);
  }

  @Override
  public void deleteAppManifest(String appId, String appManifestId) {
    deleteAppManifest(getById(appId, appManifestId));
  }

  @Override
  public void deleteAppManifest(ApplicationManifest applicationManifest) {
    if (applicationManifest == null) {
      return;
    }

    String accountId = appService.getAccountIdByAppId(applicationManifest.getAppId());
    if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, accountId)
        && Boolean.TRUE.equals(applicationManifest.getPollForChanges())) {
      pruneQueue.send(
          new PruneEvent(ApplicationManifest.class, applicationManifest.getAppId(), applicationManifest.getUuid()));
      deletePerpetualTask(applicationManifest);
    }

    deleteManifestFiles(applicationManifest.getAppId(), applicationManifest, applicationManifest.isSyncFromGit());

    wingsPersistence.delete(applicationManifest);

    yamlPushService.pushYamlChangeSet(
        accountId, applicationManifest, null, Type.DELETE, applicationManifest.isSyncFromGit(), false);
  }

  @Override
  public void pruneDescendingEntities(String appId, String applicationManifestId) {
    List<OwnedByApplicationManifest> services = ServiceClassLocator.descendingServices(
        this, ApplicationManifestServiceImpl.class, OwnedByApplicationManifest.class);
    PruneEntityListener.pruneDescendingEntities(
        services, descending -> descending.pruneByApplicationManifest(appId, applicationManifestId));
  }

  @Override
  public ApplicationManifest getAppManifestByName(
      String appId, String envId, String serviceId, String appManifestName) {
    Query<ApplicationManifest> query = wingsPersistence.createQuery(ApplicationManifest.class)
                                           .filter(ApplicationKeys.appId, appId)
                                           .filter(ApplicationManifestKeys.serviceId, serviceId)
                                           .filter(ApplicationManifestKeys.envId, null)
                                           .filter(ApplicationManifestKeys.name, appManifestName);

    return query.get();
  }

  @Override
  public Map<String, String> getNamesForIds(String appId, Set<String> appManifestIds) {
    List<ApplicationManifest> appManifests = wingsPersistence.createQuery(ApplicationManifest.class)
                                                 .filter(ApplicationKeys.appId, appId)
                                                 .field(ApplicationManifest.ID)
                                                 .in(appManifestIds)
                                                 .project(ApplicationManifestKeys.name, true)
                                                 .asList();

    if (isEmpty(appManifests)) {
      return new HashMap<>();
    }

    return appManifests.stream().collect(Collectors.toMap(Base::getUuid, ApplicationManifest::getName));
  }

  @Override
  public List<ApplicationManifest> getApplicationManifestByIds(String appId, Set<String> appManifestIds) {
    return wingsPersistence.createQuery(ApplicationManifest.class)
        .filter(ApplicationKeys.appId, appId)
        .field(ApplicationManifest.ID)
        .in(appManifestIds)
        .asList();
  }

  @Override
  public ApplicationManifest getManifestByServiceId(String appId, String serviceId) {
    List<ApplicationManifest> applicationManifests = getManifestsByServiceId(appId, serviceId, K8S_MANIFEST);
    if (isNotEmpty(applicationManifests)) {
      return applicationManifests.get(0);
    }
    return null;
  }

  @Override
  public List<ApplicationManifest> getManifestsByServiceId(String appId, String serviceId, AppManifestKind kind) {
    Query<ApplicationManifest> query = wingsPersistence.createQuery(ApplicationManifest.class)
                                           .filter(ApplicationKeys.appId, appId)
                                           .filter(ApplicationManifestKeys.serviceId, serviceId)
                                           .filter(ApplicationManifestKeys.envId, null)
                                           .filter(ApplicationManifestKeys.kind, kind)
                                           .order(Sort.descending(ApplicationManifest.CREATED_AT));

    return query.asList();
  }

  @Override
  public ApplicationManifest getByEnvId(String appId, String envId, AppManifestKind kind) {
    Query<ApplicationManifest> query = wingsPersistence.createQuery(ApplicationManifest.class)
                                           .filter(ApplicationKeys.appId, appId)
                                           .filter(ApplicationManifestKeys.envId, envId)
                                           .filter(ApplicationManifestKeys.kind, kind)
                                           .filter(ApplicationManifestKeys.serviceId, null);
    return query.get();
  }

  @Override
  public List<ApplicationManifest> getAllByEnvId(String appId, String envId) {
    Query<ApplicationManifest> query = wingsPersistence.createQuery(ApplicationManifest.class)
                                           .filter(ApplicationKeys.appId, appId)
                                           .filter(ApplicationManifestKeys.envId, envId);
    return query.asList();
  }

  @Override
  @Nonnull
  public List<ApplicationManifest> getAllByConnectorId(
      @Nonnull String accountId, @Nonnull String connectorId, @Nonnull Set<StoreType> storeTypes) {
    Query<ApplicationManifest> query =
        wingsPersistence.createQuery(ApplicationManifest.class).filter(ApplicationManifestKeys.accountId, accountId);
    if (!Sets.intersection(STORE_TYPES_WITH_GIT_CONNECTOR, storeTypes).isEmpty()) {
      query.filter(join(".", ApplicationManifestKeys.gitFileConfig, GitFileConfigKeys.connectorId), connectorId);
    } else if (!Sets.intersection(STORE_TYPES_WITH_HELM_CONNECTOR, storeTypes).isEmpty()) {
      query.filter(join(".", ApplicationManifestKeys.helmChartConfig, HelmChartConfigKeys.connectorId), connectorId);
    }
    return query.project(ApplicationManifestKeys.accountId, true)
        .project(ApplicationManifestKeys.envId, true)
        .project(ApplicationManifestKeys.serviceId, true)
        .asList();
  }

  @Override
  public List<ApplicationManifest> getAllByEnvIdAndKind(String appId, String envId, AppManifestKind kind) {
    Query<ApplicationManifest> query = wingsPersistence.createQuery(ApplicationManifest.class)
                                           .filter(ApplicationKeys.appId, appId)
                                           .filter(ApplicationManifestKeys.envId, envId)
                                           .filter(ApplicationManifestKeys.kind, kind);
    return query.asList();
  }

  @Override
  public ApplicationManifest getByEnvAndServiceId(String appId, String envId, String serviceId, AppManifestKind kind) {
    Query<ApplicationManifest> query = wingsPersistence.createQuery(ApplicationManifest.class)
                                           .filter(ApplicationKeys.appId, appId)
                                           .filter(ApplicationManifestKeys.envId, envId)
                                           .filter(ApplicationManifestKeys.serviceId, serviceId)
                                           .filter(ApplicationManifestKeys.kind, kind);
    return query.get();
  }

  @Override
  public ApplicationManifest getById(String appId, String id) {
    Query<ApplicationManifest> query = wingsPersistence.createQuery(ApplicationManifest.class)
                                           .filter(ApplicationManifest.ID, id)
                                           .filter(ApplicationKeys.appId, appId);
    return query.get();
  }

  @Override
  public List<ManifestFile> getManifestFilesByAppManifestId(String appId, String applicationManifestId) {
    return wingsPersistence.createQuery(ManifestFile.class)
        .filter(ManifestFileKeys.applicationManifestId, applicationManifestId)
        .filter(ApplicationKeys.appId, appId)
        .asList();
  }

  @Override
  public ManifestFile getManifestFileById(String appId, String id) {
    Query<ManifestFile> query = wingsPersistence.createQuery(ManifestFile.class)
                                    .filter(ApplicationKeys.appId, appId)
                                    .filter(ApplicationManifest.ID, id);
    return query.get();
  }

  @Override
  public ManifestFile getManifestFileByEnvId(String appId, String envId, AppManifestKind kind) {
    ApplicationManifest appManifest = getByEnvId(appId, envId, kind);
    return getManifestFileByFileName(appManifest.getUuid(), VALUES_YAML_KEY);
  }

  @Override
  public List<ManifestFile> getOverrideManifestFilesByEnvId(String appId, String envId) {
    Query<ApplicationManifest> query = wingsPersistence.createQuery(ApplicationManifest.class)
                                           .filter(ApplicationKeys.appId, appId)
                                           .filter(ApplicationManifestKeys.envId, envId)
                                           .filter(ApplicationManifestKeys.serviceId, null);
    List<ApplicationManifest> applicationManifests = query.asList();

    List<ManifestFile> manifestFiles = new ArrayList<>();
    for (ApplicationManifest applicationManifest : applicationManifests) {
      List<ManifestFile> manifestFilesByAppManifestId =
          getManifestFilesByAppManifestId(applicationManifest.getAppId(), applicationManifest.getUuid());

      if (isNotEmpty(manifestFilesByAppManifestId)) {
        manifestFiles.addAll(manifestFilesByAppManifestId);
      }
    }

    return manifestFiles;
  }

  @Override
  public List<ApplicationManifest> listAppManifests(String appId, String serviceId) {
    Query<ApplicationManifest> query = wingsPersistence.createQuery(ApplicationManifest.class)
                                           .filter(ApplicationKeys.appId, appId)
                                           .filter(ApplicationManifestKeys.serviceId, serviceId)
                                           .filter(ApplicationManifestKeys.envId, null);
    return query.asList();
  }

  @Override
  public List<ManifestFile> listManifestFiles(String appManifestId, String appId) {
    Query<ManifestFile> query = wingsPersistence.createQuery(ManifestFile.class)
                                    .filter(ApplicationKeys.appId, appId)
                                    .filter(ManifestFileKeys.applicationManifestId, appManifestId);
    return query.asList();
  }

  @Override
  public ManifestFile getManifestFileByFileName(String applicationManifestId, String fileName) {
    Query<ManifestFile> query = wingsPersistence.createQuery(ManifestFile.class)
                                    .filter(ManifestFileKeys.applicationManifestId, applicationManifestId)
                                    .filter(ManifestFileKeys.fileName, fileName);
    return query.get();
  }

  @Override
  public boolean detachPerpetualTask(String perpetualTaskId, String accountId) {
    Query<ApplicationManifest> query = wingsPersistence.createQuery(ApplicationManifest.class, excludeAuthority)
                                           .filter(ApplicationManifestKeys.accountId, accountId)
                                           .filter(ApplicationManifestKeys.perpetualTaskId, perpetualTaskId);
    UpdateOperations<ApplicationManifest> updateOperations =
        wingsPersistence.createUpdateOperations(ApplicationManifest.class)
            .unset(ApplicationManifestKeys.perpetualTaskId);
    UpdateResults updateResults = wingsPersistence.update(query, updateOperations);

    return updateResults.getUpdatedCount() >= 1;
  }

  @Override
  public boolean attachPerpetualTask(String accountId, String appManifestId, String perpetualTaskId) {
    // NOTE: Before using this method, ensure that perpetualTaskId does not exist for application manifest, otherwise
    // we'll have an extra perpetual task running for this.
    Query<ApplicationManifest> query = wingsPersistence.createQuery(ApplicationManifest.class)
                                           .filter(ApplicationManifestKeys.accountId, accountId)
                                           .filter(ApplicationManifest.ID, appManifestId)
                                           .field(ApplicationManifestKeys.perpetualTaskId)
                                           .doesNotExist();
    UpdateOperations<ApplicationManifest> updateOperations =
        wingsPersistence.createUpdateOperations(ApplicationManifest.class)
            .set(ApplicationManifestKeys.perpetualTaskId, perpetualTaskId);
    UpdateResults updateResults = wingsPersistence.update(query, updateOperations);

    return updateResults.getUpdatedCount() == 1;
  }

  @Override
  public Map<String, String> fetchAppManifestProperties(String appId, String applicationManifestId) {
    ApplicationManifest applicationManifest = getById(appId, applicationManifestId);
    Map<String, String> properties = new HashMap<>();
    if (applicationManifest == null || applicationManifest.getHelmChartConfig() == null) {
      return properties;
    }

    HelmChartConfig helmChartConfig = applicationManifest.getHelmChartConfig();
    SettingAttribute settingAttribute = settingsService.get(helmChartConfig.getConnectorId());
    notNullCheck("Helm repo config not found with id " + helmChartConfig.getConnectorId(), settingAttribute);

    HelmRepoConfig helmRepoConfig = (HelmRepoConfig) settingAttribute.getValue();
    properties.put(CHART_URL,
        helmHelper.getRepoUrlForHelmRepoConfig(HelmChartConfigParams.builder()
                                                   .basePath(helmChartConfig.getBasePath())
                                                   .chartName(helmChartConfig.getChartName())
                                                   .helmRepoConfig(helmRepoConfig)
                                                   .build()));
    properties.put(BASE_PATH, helmChartConfig.getBasePath());
    properties.put(REPOSITORY_NAME, settingAttribute.getName());
    properties.put(BUCKET_NAME, getBucketName(helmRepoConfig));
    properties.put(CHART_NAME, helmChartConfig.getChartName());
    properties.put(APP_MANIFEST_NAME, applicationManifest.getName());
    return properties;
  }

  private String getBucketName(HelmRepoConfig helmRepoConfig) {
    if (helmRepoConfig instanceof GCSHelmRepoConfig) {
      return ((GCSHelmRepoConfig) helmRepoConfig).getBucketName();
    } else if (helmRepoConfig instanceof AmazonS3HelmRepoConfig) {
      return ((AmazonS3HelmRepoConfig) helmRepoConfig).getBucketName();
    }
    return null;
  }

  void createPerpetualTask(@NotNull ApplicationManifest applicationManifest) {
    try {
      // Both subject and remote Observer are needed since in few places DMS might not be present
      subject.fireInform(ApplicationManifestServiceObserver::onSaved, applicationManifest);
      remoteObserverInformer.sendEvent(
          ReflectionUtils.getMethod(ApplicationManifestServiceObserver.class, "onSaved", ApplicationManifest.class),
          ApplicationManifestServiceImpl.class, applicationManifest);
    } catch (Exception e) {
      log.error(
          "Encountered exception while informing the observers of Application Manifest on save for app manifest: {}",
          applicationManifest.getUuid(), e);
    }
  }

  void resetPerpetualTask(@NotNull ApplicationManifest applicationManifest) {
    try {
      subject.fireInform(ApplicationManifestServiceObserver::onUpdated, applicationManifest);
      remoteObserverInformer.sendEvent(
          ReflectionUtils.getMethod(ApplicationManifestServiceObserver.class, "onUpdated", ApplicationManifest.class),
          ApplicationManifestServiceImpl.class, applicationManifest);
    } catch (Exception e) {
      log.error(
          "Encountered exception while informing the observers of Application Manifest on resetfor app manifest: {}",
          applicationManifest.getUuid(), e);
    }
  }

  void deletePerpetualTask(@NotNull ApplicationManifest applicationManifest) {
    try {
      subject.fireInform(ApplicationManifestServiceObserver::onDeleted, applicationManifest);
      remoteObserverInformer.sendEvent(
          ReflectionUtils.getMethod(ApplicationManifestServiceObserver.class, "onDeleted", ApplicationManifest.class),
          ApplicationManifestServiceImpl.class, applicationManifest);
    } catch (Exception e) {
      log.error(
          "Encountered exception while informing the observers of Application Manifest on delete for app manifest: {}",
          applicationManifest.getUuid(), e);
    }
  }

  @VisibleForTesting
  boolean isHelmRepoOrChartNameChanged(ApplicationManifest oldAppManifest, ApplicationManifest newAppManifest) {
    if (newAppManifest.getStoreType() == HelmChartRepo) {
      HelmChartConfig oldHelmChartConfig = oldAppManifest.getHelmChartConfig();
      HelmChartConfig newHelmChartConfig = newAppManifest.getHelmChartConfig();
      return !oldHelmChartConfig.getConnectorId().equals(newHelmChartConfig.getConnectorId())
          || !oldHelmChartConfig.getChartName().equals(newHelmChartConfig.getChartName())
          || !oldHelmChartConfig.getBasePath().equals(newHelmChartConfig.getBasePath());
    }
    return false;
  }

  @VisibleForTesting
  void checkForUpdates(ApplicationManifest oldAppManifest, ApplicationManifest applicationManifest) {
    Boolean oldPollForChanges = oldAppManifest.getPollForChanges();
    Boolean curPollForChanges = applicationManifest.getPollForChanges();

    if (curPollForChanges == null) {
      if (Boolean.TRUE.equals(oldPollForChanges)) {
        helmChartService.deleteByAppManifest(oldAppManifest.getAppId(), oldAppManifest.getUuid());
        deletePerpetualTask(oldAppManifest);
      }
    } else if (Boolean.TRUE.equals(curPollForChanges)) {
      if (oldPollForChanges == null || Boolean.FALSE.equals(oldPollForChanges)) {
        createPerpetualTask(applicationManifest);
      } else {
        if (isHelmRepoOrChartNameChanged(oldAppManifest, applicationManifest)) {
          helmChartService.deleteByAppManifest(oldAppManifest.getAppId(), oldAppManifest.getUuid());
          resetPerpetualTask(applicationManifest);
        } else {
          if (oldAppManifest.getPerpetualTaskId() == null) {
            createPerpetualTask(applicationManifest);
          }
        }
      }
    } else {
      // default behavior for null is now changed to true
      if (Boolean.TRUE.equals(oldPollForChanges) && Boolean.FALSE.equals(curPollForChanges)) {
        deletePerpetualTask(oldAppManifest);
      }
    }
  }

  @VisibleForTesting
  void handlePollForChangesToggle(ApplicationManifest oldApplicationManifest, ApplicationManifest applicationManifest,
      boolean isCreate, String accountId) {
    if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, accountId)) {
      if (Boolean.TRUE.equals(applicationManifest.getPollForChanges())
          && applicationManifest.getStoreType() != HelmChartRepo) {
        throw new InvalidRequestException(
            applicationManifest.getStoreType() + " Manifest format doesn't support poll for changes option.");
      }
      if (Boolean.TRUE.equals(applicationManifest.getPollForChanges())
          && isNotEmpty(applicationManifest.getHelmChartConfig().getChartVersion())) {
        throw new InvalidRequestException(
            "No Helm Chart version is required when Poll for Manifest option is enabled.");
      }
      if (isCreate && Boolean.TRUE.equals(applicationManifest.getPollForChanges())) {
        createPerpetualTask(applicationManifest);
      }
      if (!isCreate) {
        checkForUpdates(oldApplicationManifest, applicationManifest);
      }
    }
  }

  @Override
  public boolean updateFailedAttempts(String accountId, String appManifestId, int failedAttempts) {
    Query<ApplicationManifest> query = wingsPersistence.createQuery(ApplicationManifest.class)
                                           .filter(ApplicationManifestKeys.accountId, accountId)
                                           .filter(ApplicationManifest.ID, appManifestId);

    UpdateOperations<ApplicationManifest> updateOperations =
        wingsPersistence.createUpdateOperations(ApplicationManifest.class)
            .set(ApplicationManifestKeys.failedAttempts, failedAttempts);
    return wingsPersistence.update(query, updateOperations).getUpdatedCount() == 1;
  }

  private ApplicationManifest upsertApplicationManifest(ApplicationManifest applicationManifest, boolean isCreate) {
    validateApplicationManifest(applicationManifest);
    sanitizeApplicationManifestConfigs(applicationManifest);

    final String appId = applicationManifest.getAppId();
    final String accountId = appService.getAccountIdByAppId(appId);
    Service service = null;
    if (isNotEmpty(applicationManifest.getServiceId())) {
      service = serviceResourceService.get(applicationManifest.getAppId(), applicationManifest.getServiceId(), false);
      notNullCheck(
          "Service" + applicationManifest.getServiceId() + " linked with the app manifest doesn't exist", service);
    }

    if (service != null && featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, accountId)
        && Boolean.TRUE.equals(service.getArtifactFromManifest())) {
      if (!(applicationManifest.getStoreType() == HelmChartRepo || applicationManifest.getKind() == VALUES)) {
        throw new InvalidRequestException(
            "Application Manifest should be of kind Helm Chart from Helm Repo for Service with artifact from manifest enabled",
            USER);
      }

      if (applicationManifest.getHelmChartConfig() != null
          && ExpressionEvaluator.containsVariablePattern(applicationManifest.getHelmChartConfig().getChartName())) {
        throw new InvalidRequestException(
            "Chart name cannot contain expression when artifactFromManifest is enabled", USER);
      }
    }

    if (service != null && Boolean.TRUE.equals(service.getArtifactFromManifest())
        && applicationManifest.getStoreType() == HelmChartRepo) {
      // By default enable collection should be true and needs to be set by BE till UI starts sending default value as
      // true
      if (isCreate && !Boolean.FALSE.equals(applicationManifest.getEnableCollection())) {
        applicationManifest.setEnableCollection(true);
      }
      applicationManifest.setPollForChanges(
          applicationManifest.getEnableCollection() == null || applicationManifest.getEnableCollection());
    } else if (Boolean.TRUE.equals(applicationManifest.getPollForChanges())) {
      throw new InvalidRequestException(
          "Collection can be enabled only for helm chart from helm repo manifest type", USER);
    }

    if (isCreate && exists(applicationManifest)) {
      if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, accountId) && service != null
          && Boolean.TRUE.equals(service.getArtifactFromManifest())) {
        if (existsWithName(applicationManifest)) {
          throw new InvalidRequestException(
              String.format("Application Manifest with name %s already exists in Service %s",
                  applicationManifest.getName(), service.getName()),
              USER);
        }
      } else {
        StringBuilder builder = new StringBuilder();
        builder.append("App Manifest already exists for app ")
            .append(applicationManifest.getAppId())
            .append(" with kind ")
            .append(applicationManifest.getKind());

        if (isNotBlank(applicationManifest.getServiceId())) {
          builder.append(", serviceId ").append(applicationManifest.getServiceId());
        }

        if (isNotBlank(applicationManifest.getEnvId())) {
          builder.append(", envId ").append(applicationManifest.getEnvId());
        }
        throw new InvalidRequestException(builder.toString(), USER);
      }
    }
    if (!isCreate) {
      resetReadOnlyProperties(applicationManifest);
    }

    if (isEmpty(applicationManifest.getAccountId())) {
      applicationManifest.setAccountId(accountId);
    }

    ApplicationManifest oldAppManifest = isCreate ? null : getById(appId, applicationManifest.getUuid());
    ApplicationManifest savedApplicationManifest =
        wingsPersistence.saveAndGet(ApplicationManifest.class, applicationManifest);

    handlePollForChangesToggle(oldAppManifest, applicationManifest, isCreate, accountId);

    Type type = isCreate ? Type.CREATE : Type.UPDATE;
    yamlPushService.pushYamlChangeSet(accountId, isCreate ? null : savedApplicationManifest, savedApplicationManifest,
        type, applicationManifest.isSyncFromGit(), false);

    return savedApplicationManifest;
  }

  @VisibleForTesting
  boolean exists(ApplicationManifest applicationManifest) {
    ApplicationManifest appManifest = getAppManifest(applicationManifest.getAppId(), applicationManifest.getEnvId(),
        applicationManifest.getServiceId(), applicationManifest.getKind());

    return appManifest != null;
  }

  @VisibleForTesting
  boolean existsWithName(ApplicationManifest applicationManifest) {
    if (applicationManifest.getName() == null) {
      return false;
    }
    ApplicationManifest appManifest = getAppManifestByName(applicationManifest.getAppId(),
        applicationManifest.getEnvId(), applicationManifest.getServiceId(), applicationManifest.getName());

    return appManifest != null;
  }

  @VisibleForTesting
  void resetReadOnlyProperties(ApplicationManifest applicationManifest) {
    ApplicationManifest savedAppManifest = getById(applicationManifest.getAppId(), applicationManifest.getUuid());
    applicationManifest.setKind(savedAppManifest.getKind());
    applicationManifest.setPerpetualTaskId(savedAppManifest.getPerpetualTaskId());
  }

  private void validateManifestFileName(ManifestFile manifestFile) {
    String manifestFileName = manifestFile.getFileName();

    if (isBlank(manifestFileName)) {
      throw new InvalidRequestException("Manifest file name cannot be empty", USER);
    }

    if (manifestFileName.charAt(0) == '/' || manifestFileName.charAt(manifestFileName.length() - 1) == '/') {
      throw new InvalidRequestException("Manifest file name should not begin or end with /", USER);
    }

    String[] filePathComponents = manifestFileName.split("/");
    for (String filePathComponent : filePathComponents) {
      if (isBlank(filePathComponent)) {
        throw new InvalidRequestException("Manifest file path component cannot be empty", USER);
      }

      if (!filePathComponent.trim().equals(filePathComponent)) {
        throw new InvalidRequestException(
            "Manifest file path component cannot contain leading or trailing whitespaces", USER);
      }

      // We dont allow this as it gets saved outside manifest files folder in git
      if (filePathComponent.equals("..")) {
        throw new InvalidRequestException("Manifest file path component cannot contain [..]", USER);
      }
    }
  }

  private void validateFileNamePrefixForDirectory(ManifestFile manifestFile) {
    List<ManifestFile> manifestFiles =
        wingsPersistence.createQuery(ManifestFile.class)
            .filter(ApplicationKeys.appId, manifestFile.getAppId())
            .filter(ManifestFileKeys.applicationManifestId, manifestFile.getApplicationManifestId())
            .project(ManifestFileKeys.fileName, true)
            .asList();

    if (isEmpty(manifestFiles)) {
      return;
    }

    Pattern pattern = Pattern.compile("^" + manifestFile.getFileName() + "/");
    boolean match = manifestFiles.stream().anyMatch(manifest -> pattern.matcher(manifest.getFileName()).find());
    if (match) {
      throw new InvalidRequestException(
          format("Cannot create manifest file with name %s. There exists a directory with same name",
              manifestFile.getFileName()),
          USER);
    }

    // Below handles case like testValidateManifestFileName4
    Set<String> manifestFilesSet = manifestFiles.stream().map(ManifestFile::getFileName).collect(Collectors.toSet());

    // To handle the edit of the existing file
    if (manifestFilesSet.contains(manifestFile.getFileName())) {
      return;
    }

    String[] filePathParts = manifestFile.getFileName().split("/");
    StringBuilder fileNamePrefixBuilder = new StringBuilder();

    for (int i = 0; i < filePathParts.length; i++) {
      fileNamePrefixBuilder.append(filePathParts[i]);
      String fileNamePrefix = fileNamePrefixBuilder.toString();

      if (manifestFilesSet.contains(fileNamePrefixBuilder.toString())) {
        throw new InvalidRequestException(
            format("Cannot create manifest file with name %s. There exists a file with path %s",
                manifestFile.getFileName(), fileNamePrefix),
            USER);
      }

      fileNamePrefixBuilder.append('/');
    }
  }

  @Override
  public ManifestFile upsertApplicationManifestFile(
      ManifestFile manifestFile, ApplicationManifest applicationManifest, boolean isCreate) {
    notNullCheck("applicationManifest", applicationManifest, USER);
    manifestFile.setApplicationManifestId(applicationManifest.getUuid());
    manifestFile.setAccountId(appService.getAccountIdByAppId(manifestFile.getAppId()));

    validateManifestFileName(manifestFile);
    validateFileNamePrefixForDirectory(manifestFile);

    ManifestFile oldManifestFile = null;
    if (!isCreate) {
      oldManifestFile = wingsPersistence.createQuery(ManifestFile.class)
                            .field(ManifestFileKeys.accountId)
                            .equal(manifestFile.getAccountId())
                            .field("_id")
                            .equal(manifestFile.getUuid())
                            .get();
    }

    if (isCreate
        && getManifestFilesCount(applicationManifest.getUuid()) >= MAX_MANIFEST_FILES_PER_APPLICATION_MANIFEST) {
      throw new InvalidRequestException("Cannot add more manifest files. Maximum manifest files supported are "
          + MAX_MANIFEST_FILES_PER_APPLICATION_MANIFEST);
    }

    ManifestFile savedManifestFile =
        duplicateCheck(()
                           -> wingsPersistence.saveAndGet(ManifestFile.class, manifestFile),
            ManifestFileKeys.fileName, manifestFile.getFileName());

    String appId = applicationManifest.getAppId();
    String accountId = appService.getAccountIdByAppId(appId);

    Type type = isCreate ? Type.CREATE : Type.UPDATE;
    boolean isRename = oldManifestFile != null && !oldManifestFile.getFileName().equals(manifestFile.getFileName());
    yamlPushService.pushYamlChangeSet(
        accountId, isCreate ? null : oldManifestFile, savedManifestFile, type, manifestFile.isSyncFromGit(), isRename);

    return savedManifestFile;
  }

  @Override
  public void pruneByService(String appId, String serviceId) {
    List<ApplicationManifest> applicationManifests = wingsPersistence.createQuery(ApplicationManifest.class)
                                                         .filter(ApplicationKeys.appId, appId)
                                                         .filter(ApplicationManifestKeys.serviceId, serviceId)
                                                         .asList();
    for (ApplicationManifest appManifest : applicationManifests) {
      deleteAppManifest(appId, appManifest.getUuid());
    }
  }

  private void deleteManifestFiles(String appId, ApplicationManifest applicationManifest, boolean isSyncFromGit) {
    List<ManifestFile> manifestFiles = getManifestFilesByAppManifestId(appId, applicationManifest.getUuid());
    if (isEmpty(manifestFiles)) {
      return;
    }

    for (ManifestFile manifestFile : manifestFiles) {
      manifestFile.setSyncFromGit(isSyncFromGit);
      deleteManifestFileUtility(appId, manifestFile, applicationManifest);
    }
  }

  private void deleteManifestFileUtility(
      String appId, ManifestFile manifestFile, ApplicationManifest applicationManifest) {
    wingsPersistence.delete(manifestFile);
    String accountId = appService.getAccountIdByAppId(appId);
    yamlPushService.pushYamlChangeSet(
        accountId, applicationManifest, manifestFile, Type.DELETE, manifestFile.isSyncFromGit());
  }

  @Override
  public void deleteManifestFileById(String appId, String manifestFileId) {
    deleteManifestFile(appId, getManifestFileById(appId, manifestFileId));
  }

  @Override
  public void deleteManifestFile(String appId, ManifestFile manifestFile) {
    if (manifestFile == null) {
      return;
    }

    ApplicationManifest applicationManifest = getById(appId, manifestFile.getApplicationManifestId());
    if (applicationManifest == null) {
      return;
    }

    if (isNotBlank(applicationManifest.getEnvId()) || applicationManifest.getKind() == AppManifestKind.VALUES) {
      applicationManifest.setSyncFromGit(manifestFile.isSyncFromGit());
      deleteAppManifest(applicationManifest);
    } else {
      deleteManifestFileUtility(appId, manifestFile, applicationManifest);
    }
  }

  @VisibleForTesting
  void validateAppManifestForEnvironment(ApplicationManifest appManifest) {
    if (isNotBlank(appManifest.getEnvId())) {
      if (K8S_MANIFEST == appManifest.getKind()) {
        throw new InvalidRequestException("Environment override not supported for K8s Manifest", USER);
      }
      if (HELM_CHART_OVERRIDE == appManifest.getKind()) {
        validateStoreTypeForHelmChartOverride(appManifest.getStoreType(), getAppManifestType(appManifest));
      } else {
        if (StoreType.Local != appManifest.getStoreType() && Remote != appManifest.getStoreType()
            && CUSTOM != appManifest.getStoreType() && VALUES_YAML_FROM_HELM_REPO != appManifest.getStoreType()) {
          throw new InvalidRequestException(
              "Only local, remote, values yaml from helm repo and custom store types are allowed for values.yaml in environment");
        }
      }
    }
  }

  @VisibleForTesting
  void validateAppManifestForValuesInHelmRepo(ApplicationManifest appManifest) {
    if (!appManifest.getKind().equals(VALUES)) {
      throw new InvalidRequestException("Only ApplicationManifest Kind VALUES is supported", USER);
    }
    if (appManifest.getKind().equals(VALUES) && appManifest.getStoreType().equals(VALUES_YAML_FROM_HELM_REPO)
        && StringUtils.isEmpty(appManifest.getHelmValuesYamlFilePaths())) {
      throw new InvalidRequestException(
          "If ApplicationManifest with Kind VALUES and storetype ValuesYamlFromHelmRepo is given HelmValuesYamlFilePaths can not be null or empty",
          USER);
    }
  }

  void validateStoreTypeForHelmChartOverride(StoreType storeType, AppManifestSource appManifestSource) {
    if (appManifestSource == AppManifestSource.ENV) {
      if (HelmChartRepo != storeType) {
        throw new InvalidRequestException(
            "Only HelmChartRepo store type are allowed for Helm Chart Override for all Services in environment");
      }
    } else if (HelmChartRepo != storeType && HelmSourceRepo != storeType) {
      throw new InvalidRequestException(
          "Only HelmChartRepo and HelmSourceRepo store types are allowed for Helm Chart Override in environment");
    }
  }

  @VisibleForTesting
  void validateHelmChartRepoAppManifest(ApplicationManifest applicationManifest) {
    if (applicationManifest.getGitFileConfig() != null) {
      throw new InvalidRequestException(
          "gitFileConfig cannot be used with HelmChartRepo. Use helmChartConfig instead.", USER);
    }

    if (applicationManifest.getCustomSourceConfig() != null) {
      throw new InvalidRequestException(
          "customSourceConfig cannot be used with HelmChartRepo. Use helmChartConfig instead", USER);
    }

    if (isEnvOverrideForAllServices(applicationManifest)) {
      validateHelmChartAppManifestForAllServiceOverride(applicationManifest);
      return;
    }

    if (shouldValidateHelmChartRepoAppManifestForK8sv2(applicationManifest)) {
      validateHelmChartRepoAppManifestForK8sv2Service(applicationManifest);
      return;
    }

    validateHelmChartRepoAppManifestForHelmService(applicationManifest);
  }

  private void validateHelmChartAppManifestForAllServiceOverride(ApplicationManifest applicationManifest) {
    HelmChartConfig helmChartConfig = applicationManifest.getHelmChartConfig();
    notNullCheck("helmChartConfig has to be specified for HelmChartRepo.", helmChartConfig, USER);
    if (isBlank(helmChartConfig.getConnectorId())) {
      throw new InvalidRequestException("Helm repository cannot be empty.", USER);
    }

    /*
    For Helm Chart override in environment for all services, only the helm connector should be present
     */
    if (isNotBlank(helmChartConfig.getChartName())) {
      throw new InvalidRequestException("Helm chart name cannot be given for all services helm chart override", USER);
    }

    if (isNotBlank(helmChartConfig.getChartUrl())) {
      throw new InvalidRequestException("Helm chart url cannot be given for all services helm chart override", USER);
    }

    if (isNotBlank(helmChartConfig.getChartVersion())) {
      throw new InvalidRequestException(
          "Helm chart version cannot be given for all services helm chart override", USER);
    }
  }

  private boolean isEnvOverrideForAllServices(ApplicationManifest applicationManifest) {
    return AppManifestSource.ENV == getAppManifestType(applicationManifest);
  }

  private boolean shouldValidateHelmChartRepoAppManifestForK8sv2(ApplicationManifest applicationManifest) {
    if (HELM_CHART_OVERRIDE == applicationManifest.getKind()) {
      return true;
    }

    Service service =
        serviceResourceService.get(applicationManifest.getAppId(), applicationManifest.getServiceId(), false);
    return service.isK8sV2();
  }

  private void validateHelmChartRepoAppManifestForHelmService(ApplicationManifest applicationManifest) {
    HelmChartConfig helmChartConfig = applicationManifest.getHelmChartConfig();
    if (helmChartConfig == null) {
      return;
    }

    if (isNotBlank(helmChartConfig.getConnectorId())) {
      if (isBlank(helmChartConfig.getChartName())) {
        throw new InvalidRequestException("Chart name cannot be empty when helm repository is selected", USER);
      }

      if (isNotBlank(helmChartConfig.getChartUrl())) {
        throw new InvalidRequestException("Chart url cannot be used when helm repository is selected", USER);
      }
      ociInvalidTagLatestCheck(helmChartConfig);
    }
  }

  public void ociInvalidTagLatestCheck(HelmChartConfig helmChartConfig) {
    SettingAttribute settingAttribute = settingsService.get(helmChartConfig.getConnectorId());
    if (settingAttribute != null && settingAttribute.getValue() != null
        && settingAttribute.getValue().getType().equals(OCI_HELM_REPO.toString())) {
      if (helmChartConfig.getChartVersion() != null && helmChartConfig.getChartVersion().equals("latest")) {
        throw new InvalidArgumentsException("latest tag as chart version is not support by OCI Registry", USER);
      }
    }
  }

  private void validateHelmChartRepoAppManifestForK8sv2Service(ApplicationManifest applicationManifest) {
    HelmChartConfig helmChartConfig = applicationManifest.getHelmChartConfig();

    notNullCheck("helmChartConfig has to be specified for HelmChartRepo.", helmChartConfig, USER);

    if (isBlank(helmChartConfig.getConnectorId())) {
      throw new InvalidRequestException("Helm repository cannot be empty.", USER);
    }

    if (applicationManifest.getEnvId() == null && isBlank(helmChartConfig.getChartName())) {
      throw new InvalidRequestException("Chart name cannot be empty when helm repository is selected", USER);
    }

    if (isNotBlank(helmChartConfig.getChartUrl())) {
      throw new InvalidRequestException("Chart url cannot be used.", USER);
    }
  }

  @VisibleForTesting
  public void validateLocalAppManifest(ApplicationManifest applicationManifest) {
    if (applicationManifest.getGitFileConfig() != null) {
      throw new InvalidRequestException("gitFileConfig cannot be used for Local storeType.", USER);
    }

    if (applicationManifest.getHelmChartConfig() != null) {
      throw new InvalidRequestException("helmChartConfig cannot be used for Local storeType.", USER);
    }

    if (applicationManifest.getCustomSourceConfig() != null) {
      throw new InvalidRequestException("customSourceConfig cannot be used for Local storeType.", USER);
    }

    if (applicationManifest.getKind() != null && applicationManifest.getKind() == AppManifestKind.K8S_MANIFEST
        && applicationManifest.getServiceId() != null) {
      Service service =
          serviceResourceService.getWithDetails(applicationManifest.getAppId(), applicationManifest.getServiceId());
      if (service != null && service.getDeploymentType() != null
          && service.getDeploymentType() == DeploymentType.HELM) {
        throw new InvalidRequestException("Local app manifest is not supported for helm service", USER);
      }
    }
  }

  @VisibleForTesting
  void validateRemoteAppManifest(ApplicationManifest applicationManifest) {
    if (applicationManifest.getHelmChartConfig() != null) {
      throw new InvalidRequestException("helmChartConfig cannot be used with Remote. Use gitFileConfig instead.", USER);
    }

    if (applicationManifest.getCustomSourceConfig() != null) {
      throw new InvalidRequestException(
          "customSourceConfig cannot be used with Remote. Use gitFileConfig instead.", USER);
    }

    gitFileConfigHelperService.validate(applicationManifest.getGitFileConfig());

    if (isNotEmpty(applicationManifest.getAppId()) && isNotEmpty(applicationManifest.getServiceId())) {
      Service service =
          serviceResourceService.getWithDetails(applicationManifest.getAppId(), applicationManifest.getServiceId());

      if (service != null && service.getDeploymentType() == DeploymentType.ECS
          && applicationManifest.getStoreType() == Remote) {
        gitFileConfigHelperService.validateEcsGitfileConfig(applicationManifest.getGitFileConfig());
      }
    }
  }

  private void validateKustomizeAppManifest(ApplicationManifest applicationManifest) {
    if (applicationManifest.getKustomizeConfig() == null) {
      throw new InvalidRequestException("KustomizeConfig must be present for Kustomize Manifests", USER);
    }
    GitFileConfig gitFileConfig = applicationManifest.getGitFileConfig();
    gitFileConfigHelperService.validate(gitFileConfig);
    if (isNotEmpty(gitFileConfig.getFilePath())) {
      throw new InvalidRequestException("File Path has to be empty for Git Config for Kustomize Manifests", USER);
    }
  }

  @VisibleForTesting
  void validateApplicationManifest(ApplicationManifest applicationManifest) {
    if (isBlank(applicationManifest.getServiceId()) && isBlank(applicationManifest.getEnvId())) {
      throw new InvalidRequestException("Both envId and serviceId cannot be empty for application manifest", USER);
    }

    if (applicationManifest.getKind() == null) {
      throw new InvalidRequestException("Application manifest kind cannot be empty", USER);
    }

    if (ExpressionEvaluator.containsVariablePattern(applicationManifest.getName())) {
      throw new InvalidRequestException("Name shouldn't contain expressions", USER);
    }

    validateCommandFlags(applicationManifest);
    validateAppManifestForEnvironment(applicationManifest);

    switch (applicationManifest.getStoreType()) {
      case Remote:
      case HelmSourceRepo:
        validateRemoteAppManifest(applicationManifest);
        break;
      case KustomizeSourceRepo:
        validateKustomizeAppManifest(applicationManifest);
        break;

      case Local:
        validateLocalAppManifest(applicationManifest);
        break;

      case VALUES_YAML_FROM_HELM_REPO:
        validateAppManifestForValuesInHelmRepo(applicationManifest);
        break;

      case HelmChartRepo:
        validateHelmChartRepoAppManifest(applicationManifest);
        break;

      case OC_TEMPLATES:
        validateOpenShiftSourceRepoAppManifest(applicationManifest);
        break;

      case CUSTOM:
      case CUSTOM_OPENSHIFT_TEMPLATE:
        validateCustomAppManifest(applicationManifest);
        break;

      default:
        unhandled(applicationManifest.getStoreType());
    }
  }

  private void validateCommandFlags(ApplicationManifest applicationManifest) {
    if (isBlank(applicationManifest.getServiceId()) || isBlank(applicationManifest.getAppId())) {
      return;
    }
    HelmVersion helmVersion = serviceResourceService.getHelmVersionWithDefault(
        applicationManifest.getAppId(), applicationManifest.getServiceId());
    if (null != helmVersion) {
      CommandFlagConfigUtils.validateHelmCommandFlags(applicationManifest.getHelmCommandFlag(), helmVersion);
    }
  }

  void validateOpenShiftSourceRepoAppManifest(@Nonnull ApplicationManifest applicationManifest) {
    if (applicationManifest.getGitFileConfig() == null) {
      throw new InvalidRequestException("Git File Config is mandatory for OpenShift Source Repository Type", USER);
    }
    gitFileConfigHelperService.validate(applicationManifest.getGitFileConfig());

    if (isEmpty(applicationManifest.getGitFileConfig().getFilePath())) {
      throw new InvalidRequestException("Template File Path can't be empty", USER);
    }
  }

  @VisibleForTesting
  void validateCustomAppManifest(@Nonnull ApplicationManifest applicationManifest) {
    String accountId = appService.getAccountIdByAppId(applicationManifest.getAppId());
    if (!featureFlagService.isEnabled(FeatureName.CUSTOM_MANIFEST, accountId)) {
      throw new InvalidRequestException("Custom Manifest feature is not enabled. Please contact Harness support", USER);
    }
    if (applicationManifest.getCustomSourceConfig() == null) {
      throw new InvalidRequestException("Custom Source Config is mandatory for Custom type", USER);
    }

    if (isEmpty(applicationManifest.getCustomSourceConfig().getPath())) {
      throw new InvalidRequestException("Path can't be empty", USER);
    }
  }

  @VisibleForTesting
  void sanitizeApplicationManifestConfigs(ApplicationManifest applicationManifest) {
    if (isEmpty(applicationManifest.getAccountId())) {
      applicationManifest.setAccountId(appService.getAccountIdByAppId(applicationManifest.getAppId()));
    }
    switch (applicationManifest.getStoreType()) {
      case Local:
      case Remote:
      case HelmSourceRepo:
        break;
      case KustomizeSourceRepo:
        applicationManifest.getKustomizeConfig().setKustomizeDirPath(
            defaultString(applicationManifest.getKustomizeConfig().getKustomizeDirPath()));
        break;

      case VALUES_YAML_FROM_HELM_REPO:
        applicationManifest.setHelmValuesYamlFilePaths(applicationManifest.getHelmValuesYamlFilePaths().trim());
        break;

      case HelmChartRepo:
        HelmChartConfig helmChartConfig = applicationManifest.getHelmChartConfig();

        if (helmChartConfig == null) {
          return;
        }

        if (helmChartConfig.getConnectorId() != null) {
          helmChartConfig.setConnectorId(helmChartConfig.getConnectorId().trim());
        }

        if (helmChartConfig.getChartName() != null) {
          helmChartConfig.setChartName(helmChartConfig.getChartName().trim());
        }

        if (helmChartConfig.getChartVersion() != null) {
          helmChartConfig.setChartVersion(helmChartConfig.getChartVersion().trim());
        }

        if (helmChartConfig.getChartUrl() != null) {
          helmChartConfig.setChartUrl(helmChartConfig.getChartUrl().trim());
        }

        break;
      case CUSTOM:
      case CUSTOM_OPENSHIFT_TEMPLATE:
        applicationManifest.getCustomSourceConfig().setPath(
            applicationManifest.getCustomSourceConfig().getPath().trim());
        break;

      default:
        unhandled(applicationManifest.getStoreType());
    }
  }

  @Override
  public DirectoryNode getManifestFilesFromGit(String appId, String appManifestId) {
    Application app = appService.get(appId);
    ApplicationManifest appManifest = getById(appId, appManifestId);
    if (appManifest == null) {
      throw new InvalidRequestException("Application manifest doesn't exist with id " + appManifestId, USER);
    }

    if (Remote != appManifest.getStoreType()) {
      throw new InvalidRequestException(
          "Manifest files from git should only be requested when store type is remote", USER);
    }

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    appManifestMap.put(K8sValuesLocation.Service, appManifest);

    GitFetchFilesTaskParams fetchFilesTaskParams =
        applicationManifestUtils.createGitFetchFilesTaskParams(null, app, appManifestMap);
    fetchFilesTaskParams.setActivityId(generateUuid());
    fetchFilesTaskParams.setAppManifestKind(AppManifestKind.K8S_MANIFEST);

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(app.getAccountId())
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, app.getUuid())
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.GIT_FETCH_FILES_TASK.name())
                                              .parameters(new Object[] {fetchFilesTaskParams})
                                              .timeout(TimeUnit.MINUTES.toMillis(GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT))
                                              .build())
                                    .build();

    DelegateResponseData notifyResponseData;
    try {
      notifyResponseData = delegateService.executeTask(delegateTask);
    } catch (InterruptedException ex) {
      throw new InvalidRequestException(ex.getMessage(), WingsException.USER);
    }

    if (notifyResponseData instanceof ErrorNotifyResponseData) {
      throw new WingsException(((ErrorNotifyResponseData) notifyResponseData).getErrorMessage());
    } else if ((notifyResponseData instanceof RemoteMethodReturnValueData)
        && (((RemoteMethodReturnValueData) notifyResponseData).getException() instanceof InvalidRequestException)) {
      throw(InvalidRequestException)((RemoteMethodReturnValueData) notifyResponseData).getException();
    } else if (!(notifyResponseData instanceof GitCommandExecutionResponse)) {
      throw new WingsException(ErrorCode.GENERAL_ERROR)
          .addParam("message", "Unknown Response from delegate")
          .addContext(DelegateResponseData.class, notifyResponseData);
    }

    GitCommandExecutionResponse executionResponse = (GitCommandExecutionResponse) notifyResponseData;
    if (executionResponse.getGitCommandStatus() != GitCommandStatus.SUCCESS) {
      throw new InvalidRequestException(executionResponse.getErrorMessage());
    }

    String prefixPath = fetchFilesTaskParams.getGitFetchFilesConfigMap()
                            .get(K8sValuesLocation.Service.name())
                            .getGitFileConfig()
                            .getFilePath();
    GitFetchFilesFromMultipleRepoResult filesFromMultipleGitResult =
        (GitFetchFilesFromMultipleRepoResult) executionResponse.getGitCommandResult();
    GitFetchFilesResult gitFetchFilesResult =
        filesFromMultipleGitResult.getFilesFromMultipleRepo().get(K8sValuesLocation.Service.name());

    List<ManifestFile> manifestFiles = manifestFilesFromGitFetchFilesResult(gitFetchFilesResult, prefixPath);

    Service service = serviceResourceService.getWithDetails(appId, appManifest.getServiceId());
    return yamlDirectoryService.generateManifestFileFolderNode(
        app.getAccountId(), service, manifestFiles, new DirectoryPath(MANIFEST_FILE_FOLDER));
  }

  private ManifestFile upsertManifestFileForService(ManifestFile manifestFile, String serviceId, boolean isCreate) {
    if (!serviceResourceService.exist(manifestFile.getAppId(), serviceId)) {
      throw new InvalidRequestException("Service doesn't exist");
    }

    return upsertApplicationManifestFile(
        manifestFile, getManifestByServiceId(manifestFile.getAppId(), serviceId), isCreate);
  }

  @Override
  public ApplicationManifest getAppManifest(String appId, String envId, String serviceId, AppManifestKind kind) {
    AppManifestSource appManifestSource = getAppManifestSource(envId, serviceId);

    switch (appManifestSource) {
      case SERVICE:
        return getByServiceId(appId, serviceId, kind);
      case ENV:
        return getByEnvId(appId, envId, kind);
      case ENV_SERVICE:
        return getByEnvAndServiceId(appId, envId, serviceId, kind);
      default:
        unhandled(appManifestSource);
        throw new WingsException("Invalid application manifest type");
    }
  }

  @Override
  public ApplicationManifest getByServiceId(String appId, String serviceId, AppManifestKind kind) {
    List<ApplicationManifest> applicationManifests = getManifestsByServiceId(appId, serviceId, kind);
    if (isNotEmpty(applicationManifests)) {
      return applicationManifests.get(0);
    }
    return null;
  }

  @Override
  public void cloneManifestFiles(
      String appId, ApplicationManifest applicationManifestOld, ApplicationManifest applicationManifestNew) {
    List<ManifestFile> manifestFiles = getManifestFilesByAppManifestId(appId, applicationManifestOld.getUuid());

    if (isEmpty(manifestFiles)) {
      return;
    }

    for (ManifestFile manifestFile : manifestFiles) {
      ManifestFile manifestFileNew = manifestFile.cloneInternal();
      upsertApplicationManifestFile(manifestFileNew, applicationManifestNew, true);
    }
  }

  @VisibleForTesting
  public void removeNamespace(ManifestFile manifestFile) {
    if (manifestFile == null) {
      return;
    }
    String fileContent = manifestFile.getFileContent();
    if (isEmpty(fileContent)) {
      return;
    }
    String[] fileContentByLine = fileContent.split("\\r?\\n");

    StringBuilder builder = new StringBuilder(fileContent.length());

    boolean inMetadataSection = false;
    boolean firstLine = true;

    for (String line : fileContentByLine) {
      boolean append = true;

      if (line.startsWith("metadata:")) {
        inMetadataSection = true;
      } else if (inMetadataSection && line.charAt(0) != ' ') {
        inMetadataSection = false;
      } else if (inMetadataSection && line.startsWith("  namespace:")) {
        append = false;
      }

      if (append) {
        if (!firstLine) {
          builder.append(System.lineSeparator());
        }
        builder.append(line);
      }

      firstLine = false;
    }
    manifestFile.setFileContent(builder.toString());
  }

  @Override
  public AppManifestSource getAppManifestType(ApplicationManifest applicationManifest) {
    String serviceId = applicationManifest.getServiceId();
    String envId = applicationManifest.getEnvId();

    return getAppManifestSource(envId, serviceId);
  }

  private AppManifestSource getAppManifestSource(String envId, String serviceId) {
    if (isNotBlank(envId) && isNotBlank(serviceId)) {
      return AppManifestSource.ENV_SERVICE;
    } else if (isNotBlank(envId)) {
      return AppManifestSource.ENV;
    } else if (isNotBlank(serviceId)) {
      return AppManifestSource.SERVICE;
    } else {
      throw new WingsException("App manifest is invalid with empty envId and serviceId");
    }
  }

  @Override
  public void pruneByEnvironment(String appId, String envId) {
    List<ApplicationManifest> appManifests = getAllByEnvId(appId, envId);

    if (isEmpty(appManifests)) {
      return;
    }

    for (ApplicationManifest applicationManifest : appManifests) {
      deleteAppManifest(appId, applicationManifest.getUuid());
    }
  }

  private void doFileValidations(ManifestFile manifestFile) {
    doFileSizeValidation(manifestFile, ALLOWED_SIZE_IN_BYTES);
  }

  @VisibleForTesting
  public void doFileSizeValidation(ManifestFile manifestFile, int allowedSizeInBytes) {
    if (isEmpty(manifestFile.getFileContent())) {
      return;
    }
    byte[] bytes;
    try {
      bytes = manifestFile.getFileContent().getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new InvalidRequestException("Unable to read manifest file", e, USER);
    }
    if (bytes.length > allowedSizeInBytes) {
      throw new InvalidRequestException(format("File size: %s bytes exceeded the limit", bytes.length), USER);
    }
  }

  private long getManifestFilesCount(String appManifestId) {
    return wingsPersistence.createQuery(ManifestFile.class)
        .filter(ManifestFileKeys.applicationManifestId, appManifestId)
        .count();
  }

  @Override
  public void deleteAllManifestFilesByAppManifestId(String appId, String appManifestId) {
    ApplicationManifest applicationManifest = getById(appId, appManifestId);

    if (applicationManifest == null) {
      return;
    }

    deleteManifestFiles(applicationManifest.getAppId(), applicationManifest, false);
  }

  @Override
  public PageResponse<ApplicationManifest> listPollingEnabled(
      PageRequest<ApplicationManifest> pageRequest, String appId) {
    List<String> artifactFromManifestServices = serviceResourceService.getIdsWithArtifactFromManifest(appId);
    pageRequest.addFilter(
        ApplicationManifestKeys.serviceId, SearchFilter.Operator.IN, artifactFromManifestServices.toArray());
    return wingsPersistence.query(ApplicationManifest.class, pageRequest);
  }

  @Override
  public PageResponse<ApplicationManifest> list(PageRequest<ApplicationManifest> pageRequest) {
    return wingsPersistence.query(ApplicationManifest.class, pageRequest);
  }

  @Override
  public boolean deletePerpetualTaskByAppManifest(String accountId, String appManifestId) {
    log.info("Deleting perpetual task associated with app manifest " + appManifestId);
    Query<PerpetualTaskRecord> query =
        wingsPersistence.createQuery(PerpetualTaskRecord.class)
            .field(PerpetualTaskRecordKeys.accountId)
            .equal(accountId)
            .field(PerpetualTaskRecordKeys.client_params + "." + ManifestCollectionPTaskClientParamsKeys.appManifestId)
            .equal(appManifestId);
    return wingsPersistence.delete(query);
  }

  @Override
  public List<ApplicationManifest> listHelmChartSourceBySettingId(String accountId, String settingAttributeUuid) {
    Query<ApplicationManifest> query =
        wingsPersistence.createQuery(ApplicationManifest.class)
            .filter(ApplicationKeys.accountId, accountId)
            .filter(join(".", ApplicationManifestKeys.helmChartConfig, HelmChartConfigKeys.connectorId),
                settingAttributeUuid);
    return query.asList();
  }
}

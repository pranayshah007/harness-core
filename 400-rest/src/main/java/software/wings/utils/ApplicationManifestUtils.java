/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.beans.FeatureName.GIT_HOST_CONNECTIVITY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.manifest.ManifestHelper.getMapFromValuesFileContent;
import static io.harness.k8s.manifest.ManifestHelper.getValuesExpressionKeysFromMap;
import static io.harness.k8s.manifest.ManifestHelper.getValuesYamlGitFilePath;
import static io.harness.k8s.manifest.ManifestHelper.values_filename;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.appmanifest.AppManifestKind.HELM_CHART_OVERRIDE;
import static software.wings.beans.appmanifest.AppManifestKind.OC_PARAMS;
import static software.wings.beans.appmanifest.StoreType.CUSTOM;
import static software.wings.beans.appmanifest.StoreType.CUSTOM_OPENSHIFT_TEMPLATE;
import static software.wings.beans.appmanifest.StoreType.HelmChartRepo;
import static software.wings.beans.appmanifest.StoreType.HelmSourceRepo;
import static software.wings.beans.appmanifest.StoreType.KustomizeSourceRepo;
import static software.wings.beans.appmanifest.StoreType.Local;
import static software.wings.beans.yaml.YamlConstants.VALUES_YAML_KEY;
import static software.wings.sm.ExecutionContextImpl.PHASE_PARAM;
import static software.wings.utils.Utils.splitCommaSeparatedFilePath;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.context.ContextElementType;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.delegate.task.manifests.request.CustomManifestFetchConfig;
import io.harness.delegate.task.manifests.request.CustomManifestValuesFetchParams;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.GitFile;
import io.harness.helm.HelmSubCommandType;
import io.harness.manifest.CustomManifestSource;
import io.harness.manifest.CustomSourceConfig;
import io.harness.manifest.CustomSourceFile;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.beans.Application;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFetchFilesConfig;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.GitFileConfig;
import software.wings.beans.HelmCommandFlagConfig;
import software.wings.beans.HelmCommandFlagConstants.HelmSubCommand;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateExecutionContext;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ApplicationManifestUtils {
  @Inject private AppService appService;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private SecretManager secretManager;
  @Inject private SettingsService settingsService;
  @Inject private GitFileConfigHelperService gitFileConfigHelperService;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private GitConfigHelperService gitConfigHelperService;

  public Map<K8sValuesLocation, ApplicationManifest> getOverrideApplicationManifests(
      ExecutionContext context, AppManifestKind appManifestKind) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PHASE_PARAM);
    Application app = appService.get(context.getAppId());
    ServiceElement serviceElement = phaseElement.getServiceElement();

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new EnumMap<>(K8sValuesLocation.class);

    ApplicationManifest applicationManifest =
        applicationManifestService.getByServiceId(context.getAppId(), serviceElement.getUuid(), appManifestKind);
    if (applicationManifest != null) {
      appManifestMap.put(K8sValuesLocation.ServiceOverride, applicationManifest);
    }

    InfrastructureMapping infraMapping = infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());
    if (infraMapping == null) {
      throw new InvalidRequestException(format(
          "Infra mapping not found for appId %s infraMappingId %s", app.getUuid(), context.fetchInfraMappingId()));
    }

    applicationManifest =
        applicationManifestService.getByEnvId(app.getUuid(), infraMapping.getEnvId(), appManifestKind);
    if (applicationManifest != null) {
      appManifestMap.put(K8sValuesLocation.EnvironmentGlobal, applicationManifest);
    }

    applicationManifest = applicationManifestService.getByEnvAndServiceId(
        app.getUuid(), infraMapping.getEnvId(), serviceElement.getUuid(), appManifestKind);
    if (applicationManifest != null) {
      appManifestMap.put(K8sValuesLocation.Environment, applicationManifest);
    }

    // Need service manifest for openshift deployments in case if any of the params overrides is reusing script from
    // service manifest
    if (featureFlagService.isEnabled(FeatureName.CUSTOM_MANIFEST, context.getAccountId()) && isNotEmpty(appManifestMap)
        && OC_PARAMS == appManifestKind) {
      applicationManifest = getApplicationManifestForService(context);
      if (applicationManifest != null && applicationManifest.getStoreType() == CUSTOM_OPENSHIFT_TEMPLATE) {
        appManifestMap.put(K8sValuesLocation.Service, applicationManifest);
      }
    }

    return appManifestMap;
  }

  public GitFetchFilesTaskParams createGitFetchFilesTaskParams(
      ExecutionContext context, Application app, Map<K8sValuesLocation, ApplicationManifest> appManifestMap) {
    Map<String, GitFetchFilesConfig> gitFetchFileConfigMap =
        getGitFetchFileConfigMap(context, app.getUuid(), appManifestMap);

    ContainerServiceParams containerServiceParams = null;
    String infrastructureMappingId = context == null ? null : context.fetchInfraMappingId();

    if (infrastructureMappingId != null) {
      InfrastructureMapping infraMapping =
          infrastructureMappingService.get(context.getAppId(), infrastructureMappingId);
      if (infraMapping instanceof ContainerInfrastructureMapping) {
        containerServiceParams = containerDeploymentManagerHelper.getContainerServiceParams(
            (ContainerInfrastructureMapping) infraMapping, "", context);
      }
    }

    boolean isBindTaskFeatureSet =
        featureFlagService.isEnabled(FeatureName.BIND_FETCH_FILES_TASK_TO_DELEGATE, app.getAccountId());

    return GitFetchFilesTaskParams.builder()
        .accountId(app.getAccountId())
        .appId(app.getUuid())
        .isFinalState(isRemoteFetchRequiredForManifest(appManifestMap))
        .gitFetchFilesConfigMap(gitFetchFileConfigMap)
        .containerServiceParams(containerServiceParams)
        .isBindTaskFeatureSet(isBindTaskFeatureSet)
        .isGitHostConnectivityCheck(featureFlagService.isEnabled(GIT_HOST_CONNECTIVITY, context.getAccountId()))
        .build();
  }

  public Map<String, GitFetchFilesConfig> getGitFetchFileConfigMap(
      ExecutionContext context, String appId, Map<K8sValuesLocation, ApplicationManifest> appManifestMap) {
    Map<String, GitFetchFilesConfig> gitFetchFileConfigMap = new HashMap<>();

    for (Entry<K8sValuesLocation, ApplicationManifest> entry : appManifestMap.entrySet()) {
      K8sValuesLocation k8sValuesLocation = entry.getKey();
      ApplicationManifest applicationManifest = entry.getValue();

      if (StoreType.Remote == applicationManifest.getStoreType()
          || StoreType.HelmSourceRepo == applicationManifest.getStoreType()) {
        // use env override if available. We do not support merge config at service and env for HelmChartConfig
        Service service = fetchServiceFromContext(context);
        if (service.isK8sV2() && StoreType.HelmSourceRepo == applicationManifest.getStoreType()) {
          applicationManifest = getAppManifestByApplyingHelmChartOverride(context);
        }

        GitFileConfig gitFileConfig =
            gitFileConfigHelperService.renderGitFileConfig(context, applicationManifest.getGitFileConfig());
        GitConfig gitConfig = settingsService.fetchGitConfigFromConnectorId(gitFileConfig.getConnectorId());
        notNullCheck("Git config not found", gitConfig);
        gitConfigHelperService.convertToRepoGitConfig(gitConfig, gitFileConfig.getRepoName());
        List<EncryptedDataDetail> encryptionDetails =
            secretManager.getEncryptionDetails(gitConfig, appId, context.getWorkflowExecutionId());

        GitFetchFilesConfig gitFetchFileConfig = GitFetchFilesConfig.builder()
                                                     .gitConfig(gitConfig)
                                                     .gitFileConfig(gitFileConfig)
                                                     .encryptedDataDetails(encryptionDetails)
                                                     .build();

        gitFetchFileConfigMap.put(k8sValuesLocation.name(), gitFetchFileConfig);
      }
    }

    return gitFetchFileConfigMap;
  }

  public Map<K8sValuesLocation, List<String>> getMapK8sValuesLocationToNonEmptyContents(
      Map<String, List<String>> mapK8sValuesLocationToContents) {
    Map<K8sValuesLocation, List<String>> mapK8sValuesLocationToNonEmptyContents = new HashMap<>();

    for (Map.Entry entry : mapK8sValuesLocationToContents.entrySet()) {
      K8sValuesLocation k8sValueLocation = K8sValuesLocation.valueOf((String) entry.getKey());
      List<String> contents = (List<String>) entry.getValue();

      List<String> nonEmptyContents =
          contents.stream().filter(content -> isNotBlank(content)).collect(Collectors.toList());

      if (isNotEmpty(nonEmptyContents)) {
        mapK8sValuesLocationToNonEmptyContents.put(k8sValueLocation, nonEmptyContents);
      }
    }

    return mapK8sValuesLocationToNonEmptyContents;
  }

  public Map<String, List<String>> getHelmFetchTaskMapK8sValuesLocationToFilePaths(
      ExecutionContext context, Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap) {
    Map<String, List<String>> mapK8sValuesLocationToFilePaths = new HashMap<>();

    for (Entry<K8sValuesLocation, ApplicationManifest> entry : applicationManifestMap.entrySet()) {
      K8sValuesLocation k8sValuesLocation = entry.getKey();
      ApplicationManifest applicationManifest = entry.getValue();

      if (StoreType.VALUES_YAML_FROM_HELM_REPO == applicationManifest.getStoreType()) {
        if (isNotEmpty(applicationManifest.getHelmValuesYamlFilePaths())) {
          String renderedValuesYamlFilePaths =
              context.renderExpression(applicationManifest.getHelmValuesYamlFilePaths());
          List<String> filePaths = Arrays.asList(renderedValuesYamlFilePaths.split(","))
                                       .stream()
                                       .map(path -> path.trim())
                                       .collect(Collectors.toList());
          mapK8sValuesLocationToFilePaths.put(k8sValuesLocation.name(), filePaths);
        }
      }
    }

    return mapK8sValuesLocationToFilePaths;
  }

  private boolean isRemoteFetchRequiredForManifest(Map<K8sValuesLocation, ApplicationManifest> appManifestMap) {
    if (!appManifestMap.containsKey(K8sValuesLocation.Service)) {
      return true;
    }

    ApplicationManifest applicationManifest = appManifestMap.get(K8sValuesLocation.Service);
    return Local == applicationManifest.getStoreType();
  }

  public boolean isValuesInGit(Map<K8sValuesLocation, ApplicationManifest> appManifestMap) {
    for (Entry<K8sValuesLocation, ApplicationManifest> entry : appManifestMap.entrySet()) {
      ApplicationManifest applicationManifest = entry.getValue();
      if (StoreType.Remote == applicationManifest.getStoreType()
          || StoreType.HelmSourceRepo == applicationManifest.getStoreType()) {
        return true;
      }
    }

    return false;
  }

  public ApplicationManifest getAppManifestByApplyingHelmChartOverride(ExecutionContext context) {
    ApplicationManifest manifestAtService = getApplicationManifestForService(context);
    if (manifestAtService == null
        || (HelmChartRepo != manifestAtService.getStoreType() && HelmSourceRepo != manifestAtService.getStoreType())) {
      return null;
    }

    Map<K8sValuesLocation, ApplicationManifest> manifestsMap =
        getOverrideApplicationManifests(context, HELM_CHART_OVERRIDE);

    // Priority: service override in env > global override in env > service
    // chart name chart repo should not come from overrides of any kind
    applyHelmChartOverride(manifestAtService, manifestsMap);

    return manifestAtService;
  }

  public void applyHelmChartOverride(
      ApplicationManifest manifestAtService, Map<K8sValuesLocation, ApplicationManifest> manifestsMap) {
    final K8sValuesLocation overrideEnvironmentSelected = manifestsMap.containsKey(K8sValuesLocation.Environment)
        ? K8sValuesLocation.Environment
        : K8sValuesLocation.EnvironmentGlobal;
    if (HelmChartRepo == manifestAtService.getStoreType()) {
      if (overrideEnvironmentSelected == K8sValuesLocation.Environment
          || overrideEnvironmentSelected == K8sValuesLocation.EnvironmentGlobal) {
        applyK8sValuesLocationBasedHelmChartOverride(manifestAtService, manifestsMap, overrideEnvironmentSelected);
      }
    }
  }

  public void applyK8sValuesLocationBasedHelmChartOverride(ApplicationManifest manifestAtService,
      Map<K8sValuesLocation, ApplicationManifest> manifestsMap, K8sValuesLocation k8sValuesLocation) {
    ApplicationManifest applicationManifestAtK8sLocation = manifestsMap.get(k8sValuesLocation);
    if (applicationManifestAtK8sLocation != null) {
      throwExceptionIfStoreTypesDontMatch(applicationManifestAtK8sLocation, manifestAtService);
      // only override helm connector
      manifestAtService.getHelmChartConfig().setConnectorId(
          applicationManifestAtK8sLocation.getHelmChartConfig().getConnectorId());
    }
  }

  private void throwExceptionIfStoreTypesDontMatch(
      ApplicationManifest manifest, ApplicationManifest manifestAtService) {
    if (manifest.getStoreType() != manifestAtService.getStoreType()) {
      throw new InvalidRequestException(new StringBuilder("Environment Override should not change Manifest Format. ")
                                            .append(getManifestFormatName(manifestAtService.getStoreType()))
                                            .append(" is mentioned at Service, but mentioned as ")
                                            .append(getManifestFormatName(manifest.getStoreType()))
                                            .append(" at Environment Global Override")
                                            .toString());
    }
  }

  private String getManifestFormatName(StoreType storeType) {
    StringBuilder stringBuilder = new StringBuilder(128).append('"');
    if (HelmChartRepo == storeType) {
      stringBuilder.append("Helm Chart from Helm Repository");
    } else {
      stringBuilder.append("Helm Chart from Source Repository");
    }

    stringBuilder.append('"');
    return stringBuilder.toString();
  }

  public Map<K8sValuesLocation, Collection<String>> getValuesFilesFromGitFetchFilesResponse(
      Map<K8sValuesLocation, ApplicationManifest> appManifest, GitCommandExecutionResponse response) {
    GitFetchFilesFromMultipleRepoResult gitCommandResult =
        (GitFetchFilesFromMultipleRepoResult) response.getGitCommandResult();

    Multimap<K8sValuesLocation, String> valuesFiles = ArrayListMultimap.create();
    if (gitCommandResult == null || isEmpty(gitCommandResult.getFilesFromMultipleRepo())) {
      return valuesFiles.asMap();
    }

    for (Entry<String, GitFetchFilesResult> entry : gitCommandResult.getFilesFromMultipleRepo().entrySet()) {
      GitFetchFilesResult gitFetchFilesResult = entry.getValue();
      Map<String, GitFile> namedGitFiles = new LinkedHashMap<>();
      K8sValuesLocation k8sValuesLocation = K8sValuesLocation.valueOf(entry.getKey());
      for (GitFile file : gitFetchFilesResult.getFiles()) {
        namedGitFiles.put(file.getFilePath(), file);
      }

      ApplicationManifest manifest = appManifest.get(k8sValuesLocation);
      valuesFiles.putAll(k8sValuesLocation, getGitOrderedFiles(k8sValuesLocation, manifest, namedGitFiles));
    }

    return valuesFiles.asMap();
  }

  private Collection<String> getGitOrderedFiles(
      K8sValuesLocation location, ApplicationManifest appManifest, Map<String, GitFile> gitFiles) {
    // Will always expect to get only single file from application Service manifest or none if doesn't exists
    // In other cases will get the first file from the root repo
    if (K8sValuesLocation.Service == location || isEmpty(appManifest.getGitFileConfig().getFilePathList())) {
      return isNotEmpty(gitFiles) ? singletonList(gitFiles.values().iterator().next().getFileContent()) : emptyList();
    }

    return appManifest.getGitFileConfig()
        .getFilePathList()
        .stream()
        .map(filePath -> getFileOrFirstFileFromDirectory(filePath, gitFiles))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(GitFile::getFileContent)
        .collect(Collectors.toList());
  }

  private Optional<GitFile> getFileOrFirstFileFromDirectory(String filePath, Map<String, GitFile> gitFiles) {
    if (gitFiles.containsKey(filePath)) {
      return Optional.of(gitFiles.get(filePath));
    }

    return gitFiles.keySet().stream().filter(file -> file.startsWith(filePath)).map(gitFiles::get).findFirst();
  }

  public void populateValuesFilesFromAppManifest(Map<K8sValuesLocation, ApplicationManifest> appManifestMap,
      Map<K8sValuesLocation, Collection<String>> multipleValuesFiles) {
    for (Entry<K8sValuesLocation, ApplicationManifest> entry : appManifestMap.entrySet()) {
      K8sValuesLocation k8sValuesLocation = entry.getKey();
      ApplicationManifest applicationManifest = entry.getValue();
      getManifestFileContentIfExists(applicationManifest)
          .ifPresent(fileContent -> multipleValuesFiles.put(k8sValuesLocation, singletonList(fileContent)));
    }
  }

  private Optional<String> getManifestFileContentIfExists(ApplicationManifest manifest) {
    if (Local == manifest.getStoreType()) {
      ManifestFile manifestFile = applicationManifestService.getManifestFileByFileName(
          manifest.getUuid(), manifest.getKind().getDefaultFileName());
      if (manifestFile != null && isNotBlank(manifestFile.getFileContent())) {
        return Optional.of(manifestFile.getFileContent());
      }
    }

    return Optional.empty();
  }

  public void setValuesPathInGitFetchFilesTaskParams(GitFetchFilesTaskParams gitFetchFilesTaskParams) {
    Map<String, GitFetchFilesConfig> gitFetchFileConfigMap = gitFetchFilesTaskParams.getGitFetchFilesConfigMap();

    for (Entry<String, GitFetchFilesConfig> entry : gitFetchFileConfigMap.entrySet()) {
      if (K8sValuesLocation.Service.name().equals(entry.getKey())) {
        GitFetchFilesConfig gitFetchFileConfig = entry.getValue();
        gitFetchFileConfig.getGitFileConfig().setFilePath(
            getValuesYamlGitFilePath(gitFetchFileConfig.getGitFileConfig().getFilePath(), VALUES_YAML_KEY));
      }
    }
  }

  public List<String> getHelmValuesYamlFiles(String appId, String templateId) {
    ServiceTemplate serviceTemplate = serviceTemplateService.get(appId, templateId);
    if (serviceTemplate == null) {
      return new ArrayList<>();
    }

    Map<K8sValuesLocation, Collection<String>> valuesFiles = new EnumMap<>(K8sValuesLocation.class);
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new EnumMap<>(K8sValuesLocation.class);

    ApplicationManifest serviceAppManifest =
        applicationManifestService.getByServiceId(appId, serviceTemplate.getServiceId(), AppManifestKind.VALUES);
    if (serviceAppManifest != null) {
      appManifestMap.put(K8sValuesLocation.ServiceOverride, serviceAppManifest);
    }

    ApplicationManifest appManifest =
        applicationManifestService.getByEnvId(appId, serviceTemplate.getEnvId(), AppManifestKind.VALUES);
    if (appManifest != null) {
      appManifestMap.put(K8sValuesLocation.EnvironmentGlobal, appManifest);
    }

    appManifest = applicationManifestService.getByEnvAndServiceId(
        appId, serviceTemplate.getEnvId(), serviceTemplate.getServiceId(), AppManifestKind.VALUES);
    if (appManifest != null) {
      appManifestMap.put(K8sValuesLocation.Environment, appManifest);
    }

    populateValuesFilesFromAppManifest(appManifestMap, valuesFiles);

    return valuesFiles.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
  }

  public Set<String> listExpressionsFromValuesForService(String appId, String serviceId) {
    Set<String> expressionSet = new HashSet<>();

    ApplicationManifest applicationManifest =
        applicationManifestService.getByServiceId(appId, serviceId, AppManifestKind.K8S_MANIFEST);
    if (applicationManifest == null || Local != applicationManifest.getStoreType()) {
      return expressionSet;
    }

    ManifestFile manifestFile =
        applicationManifestService.getManifestFileByFileName(applicationManifest.getUuid(), values_filename);
    if (manifestFile == null) {
      return expressionSet;
    }

    Map map = getMapFromValuesFileContent(manifestFile.getFileContent());
    if (map == null) {
      return expressionSet;
    }

    return getValuesExpressionKeysFromMap(map, "", 0);
  }

  public Map<K8sValuesLocation, ApplicationManifest> getApplicationManifests(
      ExecutionContext context, AppManifestKind appManifestKind) {
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new EnumMap<>(K8sValuesLocation.class);

    ApplicationManifest serviceAppManifest = getApplicationManifestForService(context);
    if (serviceAppManifest != null) {
      appManifestMap.put(K8sValuesLocation.Service, serviceAppManifest);
    }
    appManifestMap.putAll(getOverrideApplicationManifests(context, appManifestKind));
    return appManifestMap;
  }

  public ApplicationManifest getApplicationManifestForService(ExecutionContext context) {
    Service service = fetchServiceFromContext(context);
    ApplicationManifest applicationManifest =
        applicationManifestService.getManifestByServiceId(context.getAppId(), service.getUuid());

    if (service.isK8sV2() && applicationManifest == null) {
      throw new InvalidRequestException("Manifests not found for service.");
    }

    // replacing app manifest in case artifact is from manifest
    if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, context.getAccountId())
        && Boolean.TRUE.equals(service.getArtifactFromManifest())) {
      return getAppManifestFromFromExecutionContextHelmChart(context, service.getUuid());
    }

    return applicationManifest;
  }

  public Service fetchServiceFromContext(ExecutionContext context) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    Application app = appService.get(context.getAppId());
    ServiceElement serviceElement = phaseElement.getServiceElement();
    return serviceResourceService.get(app.getUuid(), serviceElement.getUuid(), false);
  }

  public boolean isValuesInHelmChartRepo(ExecutionContext context) {
    ApplicationManifest applicationManifest = getApplicationManifestForService(context);
    return applicationManifest != null && StoreType.HelmChartRepo == applicationManifest.getStoreType()
        && applicationManifest.getHelmChartConfig() != null
        && isNotBlank(applicationManifest.getHelmChartConfig().getChartName());
  }

  public boolean isCustomManifest(ExecutionContext context) {
    ApplicationManifest applicationManifest = getApplicationManifestForService(context);
    return applicationManifest != null
        && (CUSTOM == applicationManifest.getStoreType()
            || CUSTOM_OPENSHIFT_TEMPLATE == applicationManifest.getStoreType());
  }

  public boolean isKustomizeSource(ExecutionContext context) {
    ApplicationManifest appManifest = getApplicationManifestForService(context);
    return appManifest != null && appManifest.getStoreType() == KustomizeSourceRepo;
  }

  public void populateRemoteGitConfigFilePathList(
      ExecutionContext context, Map<K8sValuesLocation, ApplicationManifest> appManifestMap) {
    appManifestMap.entrySet()
        .stream()
        // Should support multiple files only for Values YAML overrides and not Service manifest values.yaml file
        .filter(entry -> K8sValuesLocation.Service != entry.getKey())
        .filter(entry -> StoreType.Remote == entry.getValue().getStoreType())
        .map(Entry::getValue)
        .map(ApplicationManifest::getGitFileConfig)
        .filter(Objects::nonNull)
        .forEach(gitFileConfig -> {
          gitFileConfigHelperService.renderGitFileConfig(context, gitFileConfig);
          splitGitFileConfigFilePath(gitFileConfig);
        });
  }

  private void splitGitFileConfigFilePath(GitFileConfig gitFileConfig) {
    String filePath = gitFileConfig.getFilePath();
    gitFileConfig.setFilePath(null);
    gitFileConfig.setFilePathList(new ArrayList<>());
    if (filePath != null) {
      List<String> multipleFiles = splitCommaSeparatedFilePath(filePath);
      gitFileConfig.setFilePathList(multipleFiles);
    }
  }

  public void renderGitConfigForApplicationManifest(
      ExecutionContext context, Map<K8sValuesLocation, ApplicationManifest> appManifestMap) {
    appManifestMap.forEach((location, manifest) -> {
      if (manifest.getGitFileConfig() != null) {
        GitFileConfig gitFileConfig = manifest.getGitFileConfig();
        gitFileConfigHelperService.renderGitFileConfig(context, gitFileConfig);
      }
    });
  }

  @NonNull
  public ApplicationManifest getAppManifestFromFromExecutionContextHelmChart(
      ExecutionContext context, String serviceId) {
    HelmChart helmChart = ((DeploymentExecutionContext) context).getHelmChartForService(serviceId);
    if (helmChart == null) {
      throw new InvalidArgumentsException(Pair.of("helmChart", "to use helm chart as artifact"));
    }
    ApplicationManifest applicationManifest =
        applicationManifestService.getById(context.getAppId(), helmChart.getApplicationManifestId());
    if (applicationManifest == null) {
      throw new InvalidArgumentsException(Pair.of("applicationManifest", "required to use helm chart as artifact"));
    }
    applicationManifest.getHelmChartConfig().setChartVersion(helmChart.getVersion());
    return applicationManifest;
  }

  public void applyHelmChartFromExecutionContext(
      ApplicationManifest applicationManifest, ExecutionContext context, String serviceId) {
    if (HelmChartRepo == applicationManifest.getStoreType()) {
      HelmChart helmChart = ((DeploymentExecutionContext) context).getHelmChartForService(serviceId);
      if (helmChart == null) {
        throw new InvalidArgumentsException(Pair.of("helmChart", "required when poll for changes enabled"));
      }
      applicationManifest.getHelmChartConfig().setChartVersion(helmChart.getVersion());
    }
  }

  public boolean isPollForChangesEnabled(ApplicationManifest applicationManifest) {
    return applicationManifest != null && applicationManifest.getPollForChanges() != null
        && applicationManifest.getPollForChanges();
  }

  public static HelmCommandFlag getHelmCommandFlags(HelmCommandFlagConfig helmCommandFlagConfig) {
    if (helmCommandFlagConfig == null) {
      return null;
    }

    Map<HelmSubCommandType, String> resultValueMap = new HashMap<>();
    if (isNotEmpty(helmCommandFlagConfig.getValueMap())) {
      for (Map.Entry<HelmSubCommand, String> entry : helmCommandFlagConfig.getValueMap().entrySet()) {
        resultValueMap.put(entry.getKey().getSubCommandType(), entry.getValue());
      }
    }

    return HelmCommandFlag.builder().valueMap(resultValueMap).build();
  }

  public CustomManifestValuesFetchParams createCustomManifestValuesFetchParams(
      ExecutionContext context, Map<K8sValuesLocation, ApplicationManifest> appManifestMap, String varFileKey) {
    return CustomManifestValuesFetchParams.builder()
        .fetchFilesList(getCustomManifestFetchFilesList(context, appManifestMap, varFileKey))
        .accountId(context.getAccountId())
        .appId(context.getAppId())
        .build();
  }

  private List<String> getCustomSourceFilePathList(
      ExecutionContext context, K8sValuesLocation location, ApplicationManifest manifest, String varFileKey) {
    StateExecutionContext stateExecutionContext =
        StateExecutionContext
            .builder()
            // this should prevent us from rendering any secrets and using overall any secrets in path
            .adoptDelegateDecryption(true)
            .build();
    String filesPath = context.renderExpression(manifest.getCustomSourceConfig().getPath(), stateExecutionContext);
    List<String> filesPathList;
    if (K8sValuesLocation.Service == location) {
      // Don't want to fetch any files for Openshift, just use the script output for params overrides that is reusing
      // service manifest script
      String valuesYamlGitFilePath = getValuesYamlGitFilePath(filesPath, varFileKey);
      filesPathList =
          CUSTOM_OPENSHIFT_TEMPLATE == manifest.getStoreType() ? emptyList() : Arrays.asList(valuesYamlGitFilePath);
    } else {
      filesPathList = splitCommaSeparatedFilePath(filesPath);
    }

    return filesPathList;
  }

  private List<CustomManifestFetchConfig> getCustomManifestFetchFilesList(
      ExecutionContext context, Map<K8sValuesLocation, ApplicationManifest> appManifestMap, String varFileKey) {
    List<CustomManifestFetchConfig> customManifestFetchFilesList = new ArrayList<>();
    for (Map.Entry<K8sValuesLocation, ApplicationManifest> entry : appManifestMap.entrySet()) {
      if (CUSTOM != entry.getValue().getStoreType() && CUSTOM_OPENSHIFT_TEMPLATE != entry.getValue().getStoreType()) {
        continue;
      }

      List<String> filesPathList = getCustomSourceFilePathList(context, entry.getKey(), entry.getValue(), varFileKey);
      CustomSourceConfig customSourceConfig = entry.getValue().getCustomSourceConfig();
      CustomManifestSource customManifestSource =
          CustomManifestSource.builder().script(customSourceConfig.getScript()).filePaths(filesPathList).build();

      customManifestFetchFilesList.add(CustomManifestFetchConfig.builder()
                                           .key(entry.getKey().name())
                                           .defaultSource(K8sValuesLocation.Service == entry.getKey())
                                           .required(K8sValuesLocation.Service != entry.getKey())
                                           .customManifestSource(customManifestSource)
                                           .build());
    }

    return customManifestFetchFilesList;
  }

  public Map<K8sValuesLocation, Collection<String>> getValuesFilesFromCustomFetchValuesResponse(
      ExecutionContext context, Map<K8sValuesLocation, ApplicationManifest> appManifestMap,
      CustomManifestValuesFetchResponse response, String varFileKey) {
    Multimap<K8sValuesLocation, String> valuesFiles = ArrayListMultimap.create();
    if (isEmpty(response.getValuesFilesContentMap())) {
      return valuesFiles.asMap();
    }

    for (Entry<String, Collection<CustomSourceFile>> entry : response.getValuesFilesContentMap().entrySet()) {
      Map<String, CustomSourceFile> namedCustomSourceFiles = new HashMap<>();
      for (CustomSourceFile customSourceFile : entry.getValue()) {
        namedCustomSourceFiles.put(customSourceFile.getFilePath(), customSourceFile);
      }

      K8sValuesLocation valuesLocation = K8sValuesLocation.valueOf(entry.getKey());
      ApplicationManifest manifest = appManifestMap.get(valuesLocation);
      valuesFiles.putAll(
          valuesLocation, getOrderedCustomFiles(context, valuesLocation, manifest, namedCustomSourceFiles, varFileKey));
    }

    return valuesFiles.asMap();
  }

  private Collection<String> getOrderedCustomFiles(ExecutionContext context, K8sValuesLocation location,
      ApplicationManifest appManifest, Map<String, CustomSourceFile> sourceFiles, String varFileKey) {
    List<String> filePathList = getCustomSourceFilePathList(context, location, appManifest, varFileKey);
    if (K8sValuesLocation.Service == location || isEmpty(filePathList)) {
      return isNotEmpty(sourceFiles) ? singletonList(sourceFiles.values().iterator().next().getFileContent())
                                     : emptyList();
    }

    return filePathList.stream()
        .map(sourceFiles::get)
        .map(Optional::ofNullable)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(CustomSourceFile::getFileContent)
        .collect(Collectors.toList());
  }
}

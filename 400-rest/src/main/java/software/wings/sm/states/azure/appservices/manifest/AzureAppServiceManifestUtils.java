/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure.appservices.manifest;

import static io.harness.azure.model.AzureConstants.IS_SETTING_SECRET_REGEX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.yaml.YamlConstants.APP_SETTINGS_FILE;
import static software.wings.beans.yaml.YamlConstants.CONN_STRINGS_FILE;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConfiguration;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.exception.InvalidArgumentsException;
import io.harness.git.model.GitFile;
import io.harness.serializer.JsonUtils;

import software.wings.beans.Service;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.states.azure.appservices.AzureAppServiceSlotSetupExecutionData;
import software.wings.utils.ApplicationManifestUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@Singleton
public class AzureAppServiceManifestUtils {
  @Inject private ApplicationManifestUtils applicationManifestUtils;
  @Inject private ApplicationManifestService applicationManifestService;

  public AzureAppServiceConfiguration getAzureAppServiceConfiguration(ExecutionContext context) {
    AzureAppServiceConfiguration azureAppServiceConfiguration = new AzureAppServiceConfiguration();
    ApplicationManifest applicationManifest = getServiceApplicationManifest(context);
    populateAzureAppServiceConfiguration(applicationManifest, azureAppServiceConfiguration);

    Optional<ApplicationManifest> appSettingsOp =
        getLatestEnvOverrideByKind(context, AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE);
    appSettingsOp.ifPresent(
        appManifest -> populateAzureAppServiceConfiguration(appManifest, azureAppServiceConfiguration));

    Optional<ApplicationManifest> connStringsOp =
        getLatestEnvOverrideByKind(context, AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE);
    connStringsOp.ifPresent(
        appManifest -> populateAzureAppServiceConfiguration(appManifest, azureAppServiceConfiguration));

    return azureAppServiceConfiguration;
  }

  public AzureAppServiceConfiguration getAzureAppServiceConfigurationGit(
      AzureAppServiceSlotSetupExecutionData setupExecutionData) {
    AzureAppServiceConfiguration azureAppServiceConfiguration = new AzureAppServiceConfiguration();

    updateAppServiceConfigurations(
        setupExecutionData, azureAppServiceConfiguration, AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE.name());
    updateAppServiceConfigurations(
        setupExecutionData, azureAppServiceConfiguration, AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE.name());

    return azureAppServiceConfiguration;
  }

  private void updateAppServiceConfigurations(AzureAppServiceSlotSetupExecutionData setupExecutionData,
      AzureAppServiceConfiguration azureAppServiceConfiguration, String fileType) {
    Map<String, ApplicationManifest> appServiceConfigurationManifests =
        setupExecutionData.getAppServiceConfigurationManifests();

    if (appServiceConfigurationManifests.containsKey(fileType)) {
      updateConfigurationDefinedAtEnvironmentLevel(
          setupExecutionData, azureAppServiceConfiguration, fileType, appServiceConfigurationManifests);
      return;
    }

    if (appServiceConfigurationManifests.containsKey(K8sValuesLocation.Service.name())) {
      updateConfigurationDefinedAtServiceLevel(
          setupExecutionData, azureAppServiceConfiguration, fileType, appServiceConfigurationManifests);
    }
  }

  private void updateConfigurationDefinedAtEnvironmentLevel(AzureAppServiceSlotSetupExecutionData setupExecutionData,
      AzureAppServiceConfiguration azureAppServiceConfiguration, String fileType,
      Map<String, ApplicationManifest> appServiceConfigurationManifests) {
    ApplicationManifest applicationManifest = appServiceConfigurationManifests.get(fileType);

    if (StoreType.Local == applicationManifest.getStoreType()) {
      populateAzureAppServiceConfiguration(applicationManifest, azureAppServiceConfiguration);
      return;
    }

    List<GitFile> gitFiles = getGitFiles(setupExecutionData, fileType);
    if (isNotEmpty(gitFiles)) {
      if (AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE.name().equalsIgnoreCase(fileType)) {
        azureAppServiceConfiguration.setAppSettingsJSON(gitFiles.get(0).getFileContent());
      } else if (AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE.name().equalsIgnoreCase(fileType)) {
        azureAppServiceConfiguration.setConnStringsJSON(gitFiles.get(0).getFileContent());
      }
    }
  }

  private void updateConfigurationDefinedAtServiceLevel(AzureAppServiceSlotSetupExecutionData setupExecutionData,
      AzureAppServiceConfiguration azureAppServiceConfiguration, String fileType,
      Map<String, ApplicationManifest> appServiceConfigurationManifests) {
    ApplicationManifest applicationManifest = appServiceConfigurationManifests.get(K8sValuesLocation.Service.name());

    if (StoreType.Local == applicationManifest.getStoreType()) {
      populateAzureAppServiceConfiguration(applicationManifest, azureAppServiceConfiguration, fileType);
      return;
    }

    List<GitFile> gitFiles = getGitFiles(setupExecutionData, K8sValuesLocation.Service.name());
    if (isNotEmpty(gitFiles)) {
      String fileContent = getFileContent(gitFiles, fileType);
      if (AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE.name().equalsIgnoreCase(fileType)) {
        azureAppServiceConfiguration.setAppSettingsJSON(fileContent);
      } else if (AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE.name().equalsIgnoreCase(fileType)) {
        azureAppServiceConfiguration.setConnStringsJSON(fileContent);
      }
    }
  }

  private String getFileContent(List<GitFile> gitFiles, String fileType) {
    String fileContent = "";
    for (GitFile file : gitFiles) {
      if (fileType.equalsIgnoreCase(getFileType(file))) {
        fileContent = file.getFileContent();
      }
    }
    return fileContent;
  }

  private String getFileType(GitFile file) {
    String fileContent = file.getFileContent();

    List<AzureAppServiceConnectionString> settings =
        JsonUtils.asObject(fileContent, new TypeReference<List<AzureAppServiceConnectionString>>() {});

    if (isNotEmpty(settings)) {
      AzureAppServiceConnectionString connectionString = settings.get(0);
      if (connectionString.getType() != null) {
        return AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE.name();
      }
      return AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE.name();
    }
    return "unknown";
  }

  private List<GitFile> getGitFiles(AzureAppServiceSlotSetupExecutionData stateExecutionData, String key) {
    GitFetchFilesFromMultipleRepoResult fetchFilesResult = stateExecutionData.getFetchFilesResult();
    if (fetchFilesResult == null) {
      return Collections.emptyList();
    }
    Map<String, GitFetchFilesResult> filesFromMultipleRepo = fetchFilesResult.getFilesFromMultipleRepo();
    if (isEmpty(filesFromMultipleRepo) || (!filesFromMultipleRepo.containsKey(key))) {
      return Collections.emptyList();
    }

    List<GitFile> files = filesFromMultipleRepo.get(key).getFiles();
    if (isEmpty(files)) {
      return Collections.emptyList();
    }

    if ((AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE.name().equalsIgnoreCase(key)
            || AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE.name().equalsIgnoreCase(key))
        && files.size() > 1) {
      throw new InvalidArgumentsException(format("Found %s JSON files, required one file, key: %s", files.size(), key));
    }
    return files;
  }

  private ApplicationManifest getServiceApplicationManifest(ExecutionContext context) {
    Service service = applicationManifestUtils.fetchServiceFromContext(context);
    return applicationManifestService.getByServiceId(
        context.getAppId(), service.getUuid(), AppManifestKind.AZURE_APP_SERVICE_MANIFEST);
  }

  @NotNull
  private Optional<ApplicationManifest> getLatestEnvOverrideByKind(ExecutionContext context, AppManifestKind kind) {
    // Map is EnumMap and items should be ordered like enum defined in K8sValuesLocation class
    Map<K8sValuesLocation, ApplicationManifest> manifests =
        applicationManifestUtils.getApplicationManifests(context, kind);
    return manifests.values().stream().findFirst();
  }

  private void populateAzureAppServiceConfiguration(
      ApplicationManifest appManifest, AzureAppServiceConfiguration azureAppServiceConfiguration, String fileType) {
    List<ManifestFile> manifestFiles =
        applicationManifestService.getManifestFilesByAppManifestId(appManifest.getAppId(), appManifest.getUuid());

    for (ManifestFile manifestFile : manifestFiles) {
      if (AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE.name().equalsIgnoreCase(fileType)
          && manifestFile.getFileName().equalsIgnoreCase(APP_SETTINGS_FILE)) {
        azureAppServiceConfiguration.setAppSettingsJSON(manifestFile.getFileContent());
      }

      if (AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE.name().equalsIgnoreCase(fileType)
          && manifestFile.getFileName().equalsIgnoreCase(CONN_STRINGS_FILE)) {
        azureAppServiceConfiguration.setConnStringsJSON(manifestFile.getFileContent());
      }
    }
  }

  private void populateAzureAppServiceConfiguration(
      ApplicationManifest appManifest, AzureAppServiceConfiguration configuration) {
    List<ManifestFile> manifestFiles =
        applicationManifestService.getManifestFilesByAppManifestId(appManifest.getAppId(), appManifest.getUuid());
    manifestFiles.forEach(mf -> updateAppServiceConfiguration(configuration, mf));
  }

  private void updateAppServiceConfiguration(
      AzureAppServiceConfiguration serviceConfiguration, ManifestFile manifestFile) {
    if (manifestFile.getFileName().equals(CONN_STRINGS_FILE)) {
      serviceConfiguration.setConnStringsJSON(manifestFile.getFileContent());
    }

    if (manifestFile.getFileName().equals(APP_SETTINGS_FILE)) {
      serviceConfiguration.setAppSettingsJSON(manifestFile.getFileContent());
    }
  }

  public ImmutableList<String> getAppSettingSecretsImmutableList(List<AzureAppServiceApplicationSetting> appSettings) {
    return appSettings.stream()
        .filter(setting -> IS_SETTING_SECRET_REGEX.asPredicate().test(setting.getValue()))
        .map(AzureAppServiceApplicationSetting::getName)
        .collect(Collectors.collectingAndThen(toList(), ImmutableList::copyOf));
  }

  public ImmutableList<String> getConnStringSecretsImmutableList(List<AzureAppServiceConnectionString> connStrings) {
    return connStrings.stream()
        .filter(setting -> IS_SETTING_SECRET_REGEX.asPredicate().test(setting.getValue()))
        .map(AzureAppServiceConnectionString::getName)
        .collect(Collectors.collectingAndThen(toList(), ImmutableList::copyOf));
  }

  public Map<String, ApplicationManifest> getAppServiceConfigurationManifests(ExecutionContext context) {
    Map<String, ApplicationManifest> appManifestMap = new HashMap<>();

    Map<K8sValuesLocation, ApplicationManifest> appSettingsOverride =
        applicationManifestUtils.getApplicationManifests(context, AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE);
    if (!appSettingsOverride.isEmpty()) {
      Optional<K8sValuesLocation> appSettingOverrideLevel = appSettingsOverride.keySet().stream().findFirst();
      appSettingOverrideLevel.ifPresent(overrideLevel
          -> appManifestMap.put(
              AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE.name(), appSettingsOverride.get(overrideLevel)));
    }

    Map<K8sValuesLocation, ApplicationManifest> connStringsOverride =
        applicationManifestUtils.getApplicationManifests(context, AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE);
    if (!connStringsOverride.isEmpty()) {
      Optional<K8sValuesLocation> connStringsOverrideLevel = connStringsOverride.keySet().stream().findFirst();
      connStringsOverrideLevel.ifPresent(overrideLevel
          -> appManifestMap.put(
              AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE.name(), connStringsOverride.get(overrideLevel)));
    }

    if (!appManifestMap.containsKey(AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE.name())
        || !appManifestMap.containsKey(AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE.name())) {
      ApplicationManifest applicationManifest = getServiceApplicationManifest(context);
      appManifestMap.put(K8sValuesLocation.Service.name(), applicationManifest);
    }

    return appManifestMap;
  }

  public Map<String, ApplicationManifest> filterOutRemoteManifest(Map<String, ApplicationManifest> appManifestMap) {
    if (isEmpty(appManifestMap)) {
      return appManifestMap;
    }

    return appManifestMap.entrySet()
        .stream()
        .filter(entry -> entry.getValue().getStoreType() == StoreType.Remote)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}

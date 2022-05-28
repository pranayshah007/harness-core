/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.helm.CustomManifestFetchTaskHelper.unzipManifestFiles;
import static io.harness.delegate.task.helm.HelmTaskHelperBase.getChartDirectory;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;
import static io.harness.helm.HelmConstants.CHARTS_YAML_KEY;
import static io.harness.helm.HelmConstants.CHART_VERSION;
import static io.harness.helm.HelmConstants.HELM_CACHE_HOME_PLACEHOLDER;
import static io.harness.helm.HelmConstants.REPO_NAME;
import static io.harness.helm.HelmConstants.V3Commands.HELM_CACHE_HOME;
import static io.harness.helm.HelmConstants.V3Commands.HELM_CACHE_HOME_PATH;
import static io.harness.helm.HelmConstants.V3Commands.HELM_CHART_VERSION_FLAG;
import static io.harness.helm.HelmConstants.VALUES_YAML;
import static io.harness.helm.HelmConstants.WORKING_DIR_BASE;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FileData;
import io.harness.chartmuseum.ChartMuseumServer;
import io.harness.chartmuseum.ChartmuseumClient;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.chartmuseum.CgChartmuseumClientFactory;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.delegate.task.helm.HelmTaskHelperBase;
import io.harness.exception.HelmClientException;
import io.harness.exception.HelmClientRuntimeException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.helm.HelmCliCommandType;
import io.harness.k8s.model.HelmVersion;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.dto.HelmChartSpecification;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.beans.settings.helm.HelmRepoConfig;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.beans.settings.helm.OciHelmRepoConfig;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.helm.request.HelmCommandRequest;
import software.wings.helpers.ext.helm.request.HelmInstallCommandRequest;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.settings.SettingValue;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class HelmTaskHelper {
  private static final long DEFAULT_TIMEOUT_IN_MILLIS = Duration.ofMinutes(DEFAULT_STEADY_STATE_TIMEOUT).toMillis();
  public static final String RESOURCE_DIR_BASE = "./repository/helm/resources/";
  public static final String REGISTRY_URL = "${REGISTRY_URL}";
  public static final String REGISTRY_URL_PREFIX = "oci://%s";

  @Inject private EncryptionService encryptionService;
  @Inject private CgChartmuseumClientFactory cgChartmuseumClientFactory;
  @Inject private HelmTaskHelperBase helmTaskHelperBase;
  @Inject private DelegateFileManagerBase delegateFileManagerBase;

  public static void copyManifestFilesToWorkingDir(File src, File dest) throws IOException {
    FileUtils.copyDirectory(src, dest);
    deleteDirectoryAndItsContentIfExists(src.getAbsolutePath());
    waitForDirectoryToBeAccessibleOutOfProcess(dest.getPath(), 10);
  }

  public static void handleIncorrectConfiguration(K8sDelegateManifestConfig sourceRepoConfig) {
    if (sourceRepoConfig == null) {
      throw new InvalidRequestException("Source Config can not be null", USER);
    }
    if (!sourceRepoConfig.isCustomManifestEnabled()) {
      throw new InvalidRequestException("Can not use store type: CUSTOM, with feature flag off", USER);
    }
    if (sourceRepoConfig.getCustomManifestSource() == null) {
      throw new InvalidRequestException("Custom Manifest Source can not be null", USER);
    }
  }

  private void fetchChartFiles(HelmChartConfigParams helmChartConfigParams, String destinationDirectory,
      long timeoutInMillis, HelmCommandFlag helmCommandFlag) throws Exception {
    HelmRepoConfig helmRepoConfig = helmChartConfigParams.getHelmRepoConfig();

    initHelm(destinationDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis);

    if (helmRepoConfig == null) {
      fetchChartFromEmptyHelmRepoConfig(helmChartConfigParams, destinationDirectory, timeoutInMillis, helmCommandFlag);
    } else {
      decryptConnectorConfig(helmChartConfigParams);

      if (helmRepoConfig instanceof AmazonS3HelmRepoConfig || helmRepoConfig instanceof GCSHelmRepoConfig) {
        fetchChartUsingChartMuseumServer(helmChartConfigParams, helmChartConfigParams.getConnectorConfig(),
            destinationDirectory, timeoutInMillis, helmCommandFlag);
      } else if (helmRepoConfig instanceof HttpHelmRepoConfig) {
        fetchChartFromHttpServer(helmChartConfigParams, destinationDirectory, timeoutInMillis, helmCommandFlag);
      } else if (helmRepoConfig instanceof OciHelmRepoConfig) {
        fetchChartFromOciRegistry(helmChartConfigParams, destinationDirectory, timeoutInMillis, helmCommandFlag);
      }
    }
  }

  public void decryptConnectorConfig(HelmChartConfigParams helmChartConfigParams) {
    encryptionService.decrypt(
        helmChartConfigParams.getHelmRepoConfig(), helmChartConfigParams.getEncryptedDataDetails(), false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        helmChartConfigParams.getHelmRepoConfig(), helmChartConfigParams.getEncryptedDataDetails());

    SettingValue connectorConfig = helmChartConfigParams.getConnectorConfig();
    if (connectorConfig != null) {
      encryptionService.decrypt(
          (EncryptableSetting) connectorConfig, helmChartConfigParams.getConnectorEncryptedDataDetails(), false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          (EncryptableSetting) connectorConfig, helmChartConfigParams.getConnectorEncryptedDataDetails());
    }
  }

  public void downloadChartFiles(HelmChartConfigParams helmChartConfigParams, String destinationDirectory,
      long timeoutInMillis, HelmCommandFlag helmCommandFlag) throws Exception {
    String workingDirectory = createDirectory(Paths.get(destinationDirectory).toString());

    fetchChartFiles(helmChartConfigParams, workingDirectory, timeoutInMillis, helmCommandFlag);
  }

  public void downloadChartFiles(HelmChartSpecification helmChartSpecification, String destinationDirectory,
      HelmCommandRequest helmCommandRequest, long timeoutInMillis, HelmCommandFlag helmCommandFlag) throws Exception {
    String workingDirectory = createDirectory(Paths.get(destinationDirectory).toString());
    HelmChartConfigParams helmChartConfigParams =
        HelmChartConfigParams.builder()
            .chartName(helmChartSpecification.getChartName())
            .chartVersion(helmChartSpecification.getChartVersion())
            .chartUrl(helmChartSpecification.getChartUrl())
            .helmVersion(helmCommandRequest.getHelmVersion())
            .useLatestChartMuseumVersion(helmCommandRequest.isUseLatestChartMuseumVersion())
            .build();
    if (isNotBlank(helmChartSpecification.getChartUrl())) {
      helmChartConfigParams.setRepoName(helmCommandRequest.getRepoName());
    }
    fetchChartFiles(helmChartConfigParams, workingDirectory, timeoutInMillis, helmCommandFlag);
  }

  public void downloadAndUnzipCustomSourceManifestFiles(
      String workingDirectory, String zippedManifestFileId, String accountId) throws IOException {
    InputStream inputStream =
        delegateFileManagerBase.downloadByFileId(FileBucket.CUSTOM_MANIFEST, zippedManifestFileId, accountId);
    ZipInputStream zipInputStream = new ZipInputStream(inputStream);

    File destDir = new File(workingDirectory);
    unzipManifestFiles(destDir, zipInputStream);
  }

  public Map<String, List<String>> getValuesYamlFromChart(HelmChartConfigParams helmChartConfigParams,
      long timeoutInMillis, HelmCommandFlag helmCommandFlag, Map<String, List<String>> mapK8sValuesLocationToFilePaths)
      throws Exception {
    String workingDirectory = createNewDirectoryAtPath(Paths.get(WORKING_DIR_BASE).toString());
    Map<String, List<String>> mapK8sValuesLocationToContents = new HashMap<>();
    try {
      fetchChartFiles(helmChartConfigParams, workingDirectory, timeoutInMillis, helmCommandFlag);
    } catch (HelmClientException ex) {
      throw new HelmClientRuntimeException((HelmClientException) ExceptionMessageSanitizer.sanitizeException(ex));
    }

    try {
      // Fetch chart version in case it is not specified in service to display in execution logs
      if (isBlank(helmChartConfigParams.getChartVersion())) {
        try {
          helmChartConfigParams.setChartVersion(getHelmChartInfoFromChartsYamlFile(
              Paths.get(workingDirectory, helmChartConfigParams.getChartName(), CHARTS_YAML_KEY).toString())
                                                    .getVersion());
        } catch (Exception e) {
          log.info("Unable to fetch chart version", ExceptionMessageSanitizer.sanitizeException(e));
        }
      }

      if (isEmpty(mapK8sValuesLocationToFilePaths)) {
        String fileContent = new String(
            Files.readAllBytes(
                Paths.get(getChartDirectory(workingDirectory, helmChartConfigParams.getChartName()), VALUES_YAML)),
            StandardCharsets.UTF_8);
        mapK8sValuesLocationToContents.put(K8sValuesLocation.Service.name(), singletonList(fileContent));
        return mapK8sValuesLocationToContents;
      }

      mapK8sValuesLocationToFilePaths.forEach((key, value) -> {
        final List<String> valuesYamlContents = mapK8sValuesLocationToContents.containsKey(key)
            ? mapK8sValuesLocationToContents.get(key)
            : new ArrayList<>();

        value.forEach(filePath -> {
          try {
            String fileContent = new String(
                Files.readAllBytes(
                    Paths.get(getChartDirectory(workingDirectory, helmChartConfigParams.getChartName()), filePath)),
                StandardCharsets.UTF_8);
            valuesYamlContents.add(fileContent);
          } catch (Exception ex) {
            Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
            String msg = format("Required values yaml file with path %s not found", filePath);
            log.error(msg, sanitizedException);
            throw new InvalidArgumentsException(msg, sanitizedException, USER);
          }
          mapK8sValuesLocationToContents.put(key, valuesYamlContents);
        });
      });

      if (mapK8sValuesLocationToFilePaths.entrySet().stream().anyMatch(
              entry -> mapK8sValuesLocationToContents.get(entry.getKey()).size() != entry.getValue().size())) {
        throw new InvalidArgumentsException("Could not find all required values yaml files in helm repo");
      }

      return mapK8sValuesLocationToContents;
    } catch (InvalidArgumentsException ex) {
      throw ExceptionMessageSanitizer.sanitizeException(ex);
    } catch (Exception ex) {
      log.info("values yaml file not found", ExceptionMessageSanitizer.sanitizeException(ex));
      return null;
    } finally {
      cleanup(workingDirectory);
    }
  }

  private void fetchChartUsingChartMuseumServer(HelmChartConfigParams helmChartConfigParams,
      SettingValue connectorConfig, String chartDirectory, long timeoutInMillis, HelmCommandFlag helmCommandFlag)
      throws Exception {
    ChartmuseumClient chartmuseumClient = null;
    ChartMuseumServer chartMuseumServer = null;
    String resourceDirectory = null;
    boolean useRepoFlags = false;
    /*
     repoName will be a combination of the connectorId and bucket name;
     this way, parallel deployments with charts in different buckets will work fine
     */
    final String modifiedRepoName =
        helmChartConfigParams.getRepoName() + "-" + helmChartConfigParams.getHelmRepoConfig().getBucketName();

    String cacheDir = getCacheDir(helmChartConfigParams, modifiedRepoName);

    try {
      resourceDirectory = createNewDirectoryAtPath(RESOURCE_DIR_BASE);
      chartmuseumClient = cgChartmuseumClientFactory.createClient(helmChartConfigParams.getHelmRepoConfig(),
          connectorConfig, resourceDirectory, helmChartConfigParams.getBasePath(),
          helmChartConfigParams.isUseLatestChartMuseumVersion());
      chartMuseumServer = chartmuseumClient.start();

      helmTaskHelperBase.addChartMuseumRepo(modifiedRepoName, helmChartConfigParams.getRepoDisplayName(),
          chartMuseumServer.getPort(), chartDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis,
          cacheDir);
      helmTaskHelperBase.fetchChartFromRepo(modifiedRepoName, helmChartConfigParams.getRepoDisplayName(),
          helmChartConfigParams.getChartName(), helmChartConfigParams.getChartVersion(), chartDirectory,
          helmChartConfigParams.getHelmVersion(), helmCommandFlag, timeoutInMillis, false, cacheDir);
    } finally {
      if (chartmuseumClient != null && chartMuseumServer != null) {
        chartmuseumClient.stop(chartMuseumServer);
      }
      removeRepo(modifiedRepoName, chartDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis,
          useRepoFlags, EMPTY);
      if (helmChartConfigParams.isUseRepoFlags() && helmChartConfigParams.isDeleteRepoCacheDir()) {
        try {
          FileUtils.forceDelete(new File(cacheDir));
        } catch (IOException ie) {
          log.error("Deletion of charts folder failed due to : {}",
              ExceptionMessageSanitizer.sanitizeException(ie).getMessage());
        }
      }
      cleanup(resourceDirectory);
    }
  }

  public void initHelm(String workingDirectory, HelmVersion helmVersion, long timeoutInMillis) throws IOException {
    helmTaskHelperBase.initHelm(workingDirectory, helmVersion, timeoutInMillis);
  }

  public String createNewDirectoryAtPath(String directoryBase) throws IOException {
    return helmTaskHelperBase.createNewDirectoryAtPath(directoryBase);
  }

  public String createDirectory(String directoryBase) throws IOException {
    String workingDirectory = Paths.get(directoryBase).normalize().toAbsolutePath().toString();

    createDirectoryIfDoesNotExist(workingDirectory);
    waitForDirectoryToBeAccessibleOutOfProcess(workingDirectory, 10);

    return workingDirectory;
  }

  public List<FileData> getFilteredFiles(List<FileData> files, List<String> filesToBeFetched) {
    List<FileData> filteredFiles = new ArrayList<>();

    if (isEmpty(files)) {
      log.info("Files list is empty");
      return filteredFiles;
    }

    Set<String> filesToBeFetchedSet = new HashSet<>(filesToBeFetched);
    for (FileData file : files) {
      if (filesToBeFetchedSet.contains(file.getFilePath())) {
        filteredFiles.add(file);
      }
    }

    return filteredFiles;
  }

  public void cleanup(String workingDirectory) {
    helmTaskHelperBase.cleanup(workingDirectory);
  }

  public void printHelmChartInfoInExecutionLogs(
      HelmChartConfigParams helmChartConfigParams, ExecutionLogCallback executionLogCallback) {
    if (isNotBlank(helmChartConfigParams.getRepoDisplayName())) {
      executionLogCallback.saveExecutionLog("Helm repository: " + helmChartConfigParams.getRepoDisplayName());
    }

    if (isNotBlank(helmChartConfigParams.getBasePath())) {
      executionLogCallback.saveExecutionLog("Base Path: " + helmChartConfigParams.getBasePath());
    }

    if (isNotBlank(helmChartConfigParams.getChartName())) {
      executionLogCallback.saveExecutionLog("Chart name: " + helmChartConfigParams.getChartName());
    }

    if (isNotBlank(helmChartConfigParams.getChartVersion())) {
      executionLogCallback.saveExecutionLog("Chart version: " + helmChartConfigParams.getChartVersion());
    }

    if (isNotBlank(helmChartConfigParams.getChartUrl())) {
      executionLogCallback.saveExecutionLog("Chart url: " + helmChartConfigParams.getChartUrl());
    }

    if (helmChartConfigParams.getHelmVersion() != null) {
      executionLogCallback.saveExecutionLog("Helm version: " + helmChartConfigParams.getHelmVersion());
    }

    if (helmChartConfigParams.getHelmRepoConfig() instanceof AmazonS3HelmRepoConfig) {
      AmazonS3HelmRepoConfig amazonS3HelmRepoConfig =
          (AmazonS3HelmRepoConfig) helmChartConfigParams.getHelmRepoConfig();
      executionLogCallback.saveExecutionLog("Chart bucket: " + amazonS3HelmRepoConfig.getBucketName());
    } else if (helmChartConfigParams.getHelmRepoConfig() instanceof HttpHelmRepoConfig) {
      executionLogCallback.saveExecutionLog(
          "Repo url: " + ((HttpHelmRepoConfig) helmChartConfigParams.getHelmRepoConfig()).getChartRepoUrl());
    }
  }

  public void addRepo(String repoName, String repoDisplayName, String chartRepoUrl, String username, char[] password,
      String chartDirectory, HelmVersion helmVersion, long timeoutInMillis, String tempDir) {
    helmTaskHelperBase.addRepoInternal(repoName, repoDisplayName, chartRepoUrl, username, password, chartDirectory,
        helmVersion, timeoutInMillis, tempDir);
  }

  public void addRepo(String repoName, String repoDisplayName, String chartRepoUrl, String username, char[] password,
      String chartDirectory, HelmVersion helmVersion, long timeoutInMillis) {
    helmTaskHelperBase.addRepo(
        repoName, repoDisplayName, chartRepoUrl, username, password, chartDirectory, helmVersion, timeoutInMillis, "");
  }

  public void loginOciRegistry(
      OciHelmRepoConfig repoConfig, HelmVersion helmVersion, long timeoutInMillis, String destinationDirectory) {
    helmTaskHelperBase.loginOciRegistry(repoConfig.getChartRepoUrl(), repoConfig.getUsername(),
        repoConfig.getPassword(), helmVersion, timeoutInMillis, destinationDirectory);
  }

  private void fetchChartFromOciRegistry(HelmChartConfigParams helmChartConfigParams, String chartDirectory,
      long timeoutInMillis, HelmCommandFlag helmCommandFlag) {
    String cacheDir = getCacheDir(helmChartConfigParams, helmChartConfigParams.getRepoName());

    if (!(helmChartConfigParams.getHelmRepoConfig() instanceof OciHelmRepoConfig)) {
      log.error("Invalid repo config passed for OCI Registry based Helm Repo");
      throw new HelmClientException("Invalid config for OCI Registry based Helm Repo", USER, HelmCliCommandType.FETCH);
    }
    OciHelmRepoConfig repoConfig = (OciHelmRepoConfig) helmChartConfigParams.getHelmRepoConfig();
    try {
      String repoName = String.format(REGISTRY_URL_PREFIX, Paths.get(repoConfig.getChartRepoUrl()).normalize());
      helmTaskHelperBase.fetchChartFromRepo(repoName, helmChartConfigParams.getRepoDisplayName(),
          helmChartConfigParams.getChartName(), helmChartConfigParams.getChartVersion(), chartDirectory,
          helmChartConfigParams.getHelmVersion(), helmCommandFlag, timeoutInMillis,
          helmChartConfigParams.isCheckIncorrectChartVersion(), cacheDir);
    } finally {
      if (helmChartConfigParams.isUseRepoFlags() && helmChartConfigParams.isDeleteRepoCacheDir()) {
        try {
          FileUtils.forceDelete(new File(cacheDir));
        } catch (IOException ie) {
          log.error("Deletion of charts folder failed due to : {}",
              ExceptionMessageSanitizer.sanitizeException(ie).getMessage());
        }
      }
    }
  }

  private String getCacheDir(HelmChartConfigParams helmChartConfigParams, String repoName) {
    String cacheDir = "";
    if (helmChartConfigParams.isUseRepoFlags()) {
      if (helmChartConfigParams.isDeleteRepoCacheDir()) {
        cacheDir = Paths
                       .get(RESOURCE_DIR_BASE, repoName, RandomStringUtils.randomAlphabetic(5).toLowerCase(Locale.ROOT),
                           "cache")
                       .toAbsolutePath()
                       .normalize()
                       .toString();
      } else {
        cacheDir = Paths.get(RESOURCE_DIR_BASE, repoName, "cache").toAbsolutePath().normalize().toString();
      }
    }
    return cacheDir;
  }

  private void fetchChartFromHttpServer(HelmChartConfigParams helmChartConfigParams, String chartDirectory,
      long timeoutInMillis, HelmCommandFlag helmCommandFlag) {
    HttpHelmRepoConfig httpHelmRepoConfig = (HttpHelmRepoConfig) helmChartConfigParams.getHelmRepoConfig();

    String cacheDir = getCacheDir(helmChartConfigParams, helmChartConfigParams.getRepoName());
    try {
      helmTaskHelperBase.addRepo(helmChartConfigParams.getRepoName(), helmChartConfigParams.getRepoDisplayName(),
          httpHelmRepoConfig.getChartRepoUrl(), httpHelmRepoConfig.getUsername(), httpHelmRepoConfig.getPassword(),
          chartDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis, cacheDir);
      helmTaskHelperBase.fetchChartFromRepo(helmChartConfigParams.getRepoName(),
          helmChartConfigParams.getRepoDisplayName(), helmChartConfigParams.getChartName(),
          helmChartConfigParams.getChartVersion(), chartDirectory, helmChartConfigParams.getHelmVersion(),
          helmCommandFlag, timeoutInMillis, helmChartConfigParams.isCheckIncorrectChartVersion(), cacheDir);
    } finally {
      if (helmChartConfigParams.isUseRepoFlags() && helmChartConfigParams.isDeleteRepoCacheDir()) {
        try {
          FileUtils.forceDelete(new File(cacheDir));
        } catch (IOException ie) {
          log.error("Deletion of charts folder failed due to : {}",
              ExceptionMessageSanitizer.sanitizeException(ie).getMessage());
        }
      }
    }
  }

  public void addHelmRepo(HelmRepoConfig helmRepoConfig, SettingValue connectorConfig, String repoName,
      String repoDisplayName, String workingDirectory, String basePath, HelmVersion helmVersion,
      boolean useLatestChartMuseumVersion) throws Exception {
    ChartMuseumServer chartMuseumServer = null;
    ChartmuseumClient chartmuseumClient = null;
    String resourceDirectory = null;
    try {
      resourceDirectory = createNewDirectoryAtPath(RESOURCE_DIR_BASE);
      chartmuseumClient = cgChartmuseumClientFactory.createClient(
          helmRepoConfig, connectorConfig, resourceDirectory, basePath, useLatestChartMuseumVersion);
      chartMuseumServer = chartmuseumClient.start();

      helmTaskHelperBase.addChartMuseumRepo(repoName, repoDisplayName, chartMuseumServer.getPort(), workingDirectory,
          helmVersion, DEFAULT_TIMEOUT_IN_MILLIS, "");
    } finally {
      if (chartmuseumClient != null && chartMuseumServer != null) {
        chartmuseumClient.stop(chartMuseumServer);
      }
      cleanup(resourceDirectory);
    }
  }

  public void removeRepo(String repoName, String workingDirectory, HelmVersion helmVersion, long timeoutInMillis,
      boolean useRepoFlag, String tempDir) {
    helmTaskHelperBase.removeRepo(repoName, workingDirectory, helmVersion, timeoutInMillis, useRepoFlag, tempDir);
  }

  public void removeRepo(String repoName, String workingDirectory, HelmVersion helmVersion, long timeoutInMillis) {
    helmTaskHelperBase.removeRepo(repoName, workingDirectory, helmVersion, timeoutInMillis, false, EMPTY);
  }

  public void updateRepo(String repoName, String workingDirectory, HelmVersion helmVersion, long timeoutInMillis) {
    helmTaskHelperBase.updateRepo(repoName, workingDirectory, helmVersion, timeoutInMillis, EMPTY);
  }

  /*
  This method is called in case the helm has empty repository connector and the chartName has <REPO_NAME/CHART_NAME>
  value. In that case, we want to use the default "$HELM_HOME" path. That is why :-
  1.) We are not adding repo if the URL is empty
  2.) Passing null directoryPath in the helmFetchCommand so that it picks up default helm
  Ruckus is one of the customer that is using this mechanism
   */
  private void fetchChartFromEmptyHelmRepoConfig(HelmChartConfigParams helmChartConfigParams, String chartDirectory,
      long timeoutInMillis, HelmCommandFlag helmCommandFlag) {
    try {
      String helmFetchCommand;
      if (isNotBlank(helmChartConfigParams.getChartUrl())) {
        addRepo(helmChartConfigParams.getRepoName(), null, helmChartConfigParams.getChartUrl(), null, null,
            chartDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis);
        helmFetchCommand = helmTaskHelperBase.getHelmFetchCommand(helmChartConfigParams.getChartName(),
            helmChartConfigParams.getChartVersion(), helmChartConfigParams.getRepoName(), chartDirectory,
            helmChartConfigParams.getHelmVersion(), helmCommandFlag);
      } else {
        helmFetchCommand = helmTaskHelperBase.getHelmFetchCommand(helmChartConfigParams.getChartName(),
            helmChartConfigParams.getChartVersion(), helmChartConfigParams.getRepoName(), null,
            helmChartConfigParams.getHelmVersion(), helmCommandFlag);
      }
      helmTaskHelperBase.executeFetchChartFromRepo(helmChartConfigParams.getChartName(), chartDirectory,
          helmChartConfigParams.getRepoDisplayName(), helmFetchCommand, timeoutInMillis, "", false);

    } finally {
      if (isNotBlank(helmChartConfigParams.getChartUrl())) {
        removeRepo(helmChartConfigParams.getRepoName(), chartDirectory, helmChartConfigParams.getHelmVersion(),
            timeoutInMillis);
      }
    }
  }

  /**
   * Method to extract Helm Chart info like Chart version and Chart name from the downloaded Chart files.
   * @param chartYamlPath - Path of the Chart.yaml file
   * @return HelmChartInfo - This contains details about the Helm chart
   * @throws IOException
   */
  public HelmChartInfo getHelmChartInfoFromChartsYamlFile(String chartYamlPath) throws IOException {
    return helmTaskHelperBase.getHelmChartInfoFromChartsYamlFile(chartYamlPath);
  }

  public HelmChartInfo getHelmChartInfoFromChartsYamlFile(HelmInstallCommandRequest request) throws IOException {
    return getHelmChartInfoFromChartsYamlFile(Paths.get(request.getWorkingDir(), CHARTS_YAML_KEY).toString());
  }

  public HelmChartInfo getHelmChartInfoFromChartDirectory(String chartDirectory) throws IOException {
    return getHelmChartInfoFromChartsYamlFile(Paths.get(chartDirectory, CHARTS_YAML_KEY).toString());
  }

  public List<HelmChart> fetchChartVersions(HelmChartCollectionParams helmChartCollectionParams,
      String destinationDirectory, long timeoutInMillis) throws Exception {
    HelmChartConfigParams helmChartConfigParams = helmChartCollectionParams.getHelmChartConfigParams();
    HelmRepoConfig helmRepoConfig = helmChartConfigParams.getHelmRepoConfig();
    String workingDirectory = createDirectory(Paths.get(destinationDirectory).toString());
    initHelm(workingDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis);
    decryptConnectorConfig(helmChartConfigParams);

    if (helmRepoConfig instanceof HttpHelmRepoConfig) {
      return fetchVersionsFromHttp(helmChartCollectionParams, destinationDirectory, timeoutInMillis, workingDirectory);
    } else {
      return fetchVersionsUsingChartMuseumServer(helmChartCollectionParams, destinationDirectory, timeoutInMillis);
    }
  }

  private List<HelmChart> fetchVersionsFromHttp(HelmChartCollectionParams helmChartCollectionParams,
      String destinationDirectory, long timeoutInMillis, String workingDirectory) throws IOException {
    HelmChartConfigParams helmChartConfigParams = helmChartCollectionParams.getHelmChartConfigParams();
    HttpHelmRepoConfig httpHelmRepoConfig = (HttpHelmRepoConfig) helmChartConfigParams.getHelmRepoConfig();
    boolean useRepoFlags = helmChartCollectionParams.isUseRepoFlags();
    Map<String, String> environment = new HashMap<>();
    String tempDir = EMPTY;
    if (useRepoFlags) {
      tempDir = Files.createTempDirectory("charts").toAbsolutePath().toString();
    }
    String commandOutput;

    try {
      removeRepo(helmChartConfigParams.getRepoName(), workingDirectory, helmChartConfigParams.getHelmVersion(),
          timeoutInMillis, useRepoFlags, tempDir);
      addRepo(helmChartConfigParams.getRepoName(), helmChartConfigParams.getRepoDisplayName(),
          httpHelmRepoConfig.getChartRepoUrl(), httpHelmRepoConfig.getUsername(), httpHelmRepoConfig.getPassword(),
          destinationDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis, tempDir);

      String command = fetchHelmChartVersionsCommand(helmChartConfigParams.getHelmVersion(),
          helmChartConfigParams.getChartName(), helmChartConfigParams.getRepoName(), destinationDirectory);
      if (useRepoFlags) {
        environment.putIfAbsent(HELM_CACHE_HOME,
            HELM_CACHE_HOME_PATH.replace(REPO_NAME, helmChartConfigParams.getRepoName())
                .replace(HELM_CACHE_HOME_PLACEHOLDER, tempDir));
        command = fetchHelmChartVersionsCommandWithRepoFlags(helmChartConfigParams.getHelmVersion(),
            helmChartConfigParams.getChartName(), helmChartConfigParams.getRepoName(), destinationDirectory, tempDir);
      }
      if (isNotEmpty(helmChartConfigParams.getChartVersion())) {
        command =
            command + HELM_CHART_VERSION_FLAG.replace(CHART_VERSION, helmChartConfigParams.getChartVersion().trim());
      }
      commandOutput = executeCommandWithLogOutput(environment, command, workingDirectory,
          "Helm chart fetch versions command failed ", HelmCliCommandType.FETCH_ALL_VERSIONS);
      if (log.isDebugEnabled()) {
        log.debug("Result of the helm repo search command: {}, chart name: {}", commandOutput,
            helmChartCollectionParams.getHelmChartConfigParams().getChartName());
      }
    } finally {
      // We do remove repo only when the useFlags FF is on.
      if (useRepoFlags) {
        deleteQuietlyWithErrorLog(tempDir);
      }
    }

    return parseHelmVersionFetchOutput(commandOutput, helmChartCollectionParams);
  }

  private void deleteQuietlyWithErrorLog(String tempDir) {
    try {
      FileUtils.forceDelete(new File(tempDir));
    } catch (IOException ie) {
      log.error(
          "Deletion of charts folder failed due to : {}", ExceptionMessageSanitizer.sanitizeException(ie).getMessage());
    }
  }

  private List<HelmChart> parseHelmVersionFetchOutput(
      String commandOutput, HelmChartCollectionParams manifestCollectionParams) throws IOException {
    String errorMessage = "No chart with the given name found. Chart might be deleted at source";
    if (isEmpty(commandOutput) || commandOutput.contains("No results found")) {
      throw new InvalidRequestException(errorMessage);
    }

    CSVFormat csvFormat = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter('\t').withTrim();
    List<CSVRecord> records = CSVParser.parse(commandOutput, csvFormat).getRecords();
    if (isEmpty(records)) {
      throw new InvalidRequestException(errorMessage);
    }
    List<HelmChart> charts =
        records.stream()
            .filter(record
                -> record.size() > 1
                    && matchesChartName(
                        manifestCollectionParams.getHelmChartConfigParams().getChartName(), record.get(0)))
            .map(record
                -> HelmChart.builder()
                       .appId(manifestCollectionParams.getAppId())
                       .accountId(manifestCollectionParams.getAccountId())
                       .applicationManifestId(manifestCollectionParams.getAppManifestId())
                       .serviceId(manifestCollectionParams.getServiceId())
                       .name(manifestCollectionParams.getHelmChartConfigParams().getChartName())
                       .version(record.get(1))
                       .displayName(
                           manifestCollectionParams.getHelmChartConfigParams().getChartName() + "-" + record.get(1))
                       .appVersion(record.size() > 2 ? record.get(2) : null)
                       .description(record.size() > 3 ? record.get(3) : null)
                       .build())
            .collect(Collectors.toList());

    if (isEmpty(charts)) {
      throw new InvalidRequestException(errorMessage);
    }

    return charts;
  }

  private List<HelmChart> fetchVersionsUsingChartMuseumServer(HelmChartCollectionParams helmChartCollectionParams,
      String chartDirectory, long timeoutInMillis) throws Exception {
    HelmChartConfigParams helmChartConfigParams = helmChartCollectionParams.getHelmChartConfigParams();
    String resourceDirectory = createNewDirectoryAtPath(RESOURCE_DIR_BASE);

    ChartmuseumClient chartmuseumClient = cgChartmuseumClientFactory.createClient(
        helmChartConfigParams.getHelmRepoConfig(), helmChartConfigParams.getConnectorConfig(), resourceDirectory,
        helmChartConfigParams.getBasePath(), helmChartConfigParams.isUseLatestChartMuseumVersion());
    ChartMuseumServer chartMuseumServer = chartmuseumClient.start();

    try {
      helmTaskHelperBase.addChartMuseumRepo(helmChartConfigParams.getRepoName(),
          helmChartConfigParams.getRepoDisplayName(), chartMuseumServer.getPort(), chartDirectory,
          helmChartConfigParams.getHelmVersion(), timeoutInMillis, "");

      String command = fetchHelmChartVersionsCommand(helmChartConfigParams.getHelmVersion(),
          helmChartConfigParams.getChartName(), helmChartConfigParams.getRepoName(), chartDirectory);

      // fetch specific version
      if (isNotEmpty(helmChartConfigParams.getChartVersion())) {
        command =
            command + HELM_CHART_VERSION_FLAG.replace(CHART_VERSION, helmChartConfigParams.getChartVersion().trim());
      }
      String commandOutput = executeCommandWithLogOutput(Collections.emptyMap(), command, chartDirectory,
          "Helm chart fetch versions command failed ", HelmCliCommandType.FETCH_ALL_VERSIONS);
      return parseHelmVersionFetchOutput(commandOutput, helmChartCollectionParams);
    } finally {
      chartmuseumClient.stop(chartMuseumServer);
    }
  }

  private String fetchHelmChartVersionsCommand(
      HelmVersion helmVersion, String chartName, String repoName, String workingDirectory) {
    return helmTaskHelperBase.fetchHelmChartVersionsCommand(helmVersion, chartName, repoName, workingDirectory);
  }

  private String fetchHelmChartVersionsCommandWithRepoFlags(
      HelmVersion helmVersion, String chartName, String repoName, String workingDirectory, String tempDir) {
    return helmTaskHelperBase.fetchHelmChartVersionsCommandWithRepoFlags(
        helmVersion, chartName, repoName, workingDirectory, tempDir);
  }

  String executeCommandWithLogOutput(Map<String, String> environment, String command, String chartDirectory,
      String errorMessage, HelmCliCommandType helmCliCommandType) {
    StringBuilder sb = new StringBuilder();
    ProcessExecutor processExecutor = createProcessExecutorWithRedirectOutput(environment, command, chartDirectory, sb);
    return executeCommandWithLogOutput(command, errorMessage, helmCliCommandType, sb, processExecutor);
  }

  @NotNull
  private String executeCommandWithLogOutput(String command, String errorMessage, HelmCliCommandType helmCliCommandType,
      StringBuilder sb, ProcessExecutor processExecutor) {
    log.info("Helm command executed on delegate: {}", command);

    try {
      ProcessResult processResult = processExecutor.execute();
      if (processResult.getExitValue() != 0) {
        log.warn("Command failed with following result: {}", sb.toString());
      }
      return sb.toString();
    } catch (IOException e) {
      throw new HelmClientException(format("[IO exception] %s", errorMessage), USER,
          ExceptionMessageSanitizer.sanitizeException(e), helmCliCommandType);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HelmClientException(format("[Interrupted] %s", errorMessage), USER, e, helmCliCommandType);
    } catch (TimeoutException | UncheckedTimeoutException e) {
      throw new HelmClientException(format("[Timed out] %s", errorMessage), USER, e, helmCliCommandType);
    }
  }

  ProcessExecutor createProcessExecutorWithRedirectOutput(
      Map<String, String> environment, String helmFetchCommand, String chartDirectory, StringBuilder sb) {
    return new ProcessExecutor()
        .commandSplit(helmFetchCommand)
        .directory(new File(chartDirectory))
        .readOutput(true)
        .environment(environment)
        .redirectOutput(new LogOutputStream() {
          @Override
          protected void processLine(String line) {
            sb.append(line).append('\n');
          }
        });
  }

  private boolean matchesChartName(String chartName, String recordName) {
    return Arrays.asList(recordName.split("/")).contains(chartName);
  }

  public void cleanupAfterCollection(HelmChartCollectionParams helmChartCollectionParams, String destinationDirectory,
      long timeoutInMillis) throws Exception {
    HelmChartConfigParams helmChartConfigParams = helmChartCollectionParams.getHelmChartConfigParams();
    String workingDirectory = Paths.get(destinationDirectory).toString();

    removeRepo(
        helmChartConfigParams.getRepoName(), workingDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis);
    cleanup(workingDirectory);
  }
}
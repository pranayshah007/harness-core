/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.chartmuseum.ChartMuseumConstants.CHART_MUSEUM_SERVER_URL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.GCS_HELM;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.HTTP_HELM;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.OCI_HELM;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.S3_HELM;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.checkIfFileExist;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;
import static io.harness.helm.HelmCommandType.RELEASE_HISTORY;
import static io.harness.helm.HelmConstants.ADD_COMMAND_FOR_REPOSITORY;
import static io.harness.helm.HelmConstants.CHARTS_YAML_KEY;
import static io.harness.helm.HelmConstants.HELM_CACHE_HOME_PLACEHOLDER;
import static io.harness.helm.HelmConstants.HELM_FETCH_OLD_WORKING_DIR_BASE;
import static io.harness.helm.HelmConstants.HELM_HOME_PATH_FLAG;
import static io.harness.helm.HelmConstants.HELM_PATH_PLACEHOLDER;
import static io.harness.helm.HelmConstants.PASSWORD;
import static io.harness.helm.HelmConstants.REPO_NAME;
import static io.harness.helm.HelmConstants.REPO_URL;
import static io.harness.helm.HelmConstants.USERNAME;
import static io.harness.helm.HelmConstants.V3Commands.HELM_CACHE_HOME;
import static io.harness.helm.HelmConstants.V3Commands.HELM_CACHE_HOME_PATH;
import static io.harness.helm.HelmConstants.V3Commands.HELM_REPO_ADD_FORCE_UPDATE;
import static io.harness.helm.HelmConstants.V3Commands.HELM_REPO_FLAGS;
import static io.harness.helm.HelmConstants.V3Commands.REGISTRY_CONFIG;
import static io.harness.helm.HelmConstants.V3Commands.REGISTRY_CONFIG_SUFFIX;
import static io.harness.k8s.kubectl.Utils.encloseWithQuotesIfNeeded;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;
import static software.wings.delegatetasks.helm.constants.HelmConstants.REGISTRY_URL_PREFIX;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.chartmuseum.ChartMuseumServer;
import io.harness.chartmuseum.ChartmuseumClient;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthType;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmUsernamePasswordDTO;
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmUsernamePasswordDTO;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.OciHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.chartmuseum.NgChartmuseumClientFactory;
import io.harness.delegate.exception.ManifestCollectionException;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.encryption.FieldWithPlainTextOrSecretValueHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HelmClientException;
import io.harness.exception.HelmClientRuntimeException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.helm.HelmCliCommandType;
import io.harness.helm.HelmCommandFlagsUtils;
import io.harness.helm.HelmCommandTemplateFactory;
import io.harness.helm.HelmCommandType;
import io.harness.helm.HelmSubCommandType;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.utils.ObjectYamlUtils;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.beans.settings.helm.HelmRepoConfig;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.helm.response.ReleaseInfo;

import com.esotericsoftware.yamlbeans.YamlException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class HelmTaskHelperBase {
  public static final String RESOURCE_DIR_BASE = "./repository/helm/resources/";
  public static final String VERSION_KEY = "version:";
  public static final String NAME_KEY = "name:";
  public static final String REGISTRY_URL = "${REGISTRY_URL}";
  private static final String CHMOD = "chmod go-r ";
  private static final int DEFAULT_PORT = 443;
  private static final String PROCESS_RESULT_OUTPUT_FORMAT = "Output: [%s]";

  private static final String OCI_PREFIX = "oci://";
  private static final String REGISTRY_CONFIG_DIR = "registry-config-files";
  private static final String REGISTRY_CONFIG_JSON = "reg-config.json";

  @Inject private K8sGlobalConfigService k8sGlobalConfigService;
  @Inject private NgChartmuseumClientFactory ngChartmuseumClientFactory;
  @Inject private SecretDecryptionService decryptionService;

  public void modifyRepoNameToIncludeBucket(HelmChartConfigParams helmChartConfigParams) {
    HelmRepoConfig helmRepoConfig = helmChartConfigParams.getHelmRepoConfig();
    if (helmRepoConfig == null) {
      return;
    }
    /*
     repoName will be a combination of the connectorId and bucket name;
     this way, parallel deployments with charts in different buckets will work fine
     */
    if (helmRepoConfig instanceof AmazonS3HelmRepoConfig || helmRepoConfig instanceof GCSHelmRepoConfig) {
      String modifiedRepoName =
          helmChartConfigParams.getRepoName() + "-" + helmChartConfigParams.getHelmRepoConfig().getBucketName();
      helmChartConfigParams.setRepoName(modifiedRepoName);
    }
  }

  public boolean isHelmLocalRepoSet() {
    return isNotEmpty(getHelmLocalRepositoryPath());
  }

  public String getHelmLocalRepositoryPath() {
    return System.getenv("HELM_LOCAL_REPOSITORY");
  }
  public String getHelmLocalRepositoryCompletePath(String repoName, String chartName, String chartVersion) {
    if (isEmpty(chartVersion)) {
      chartVersion = "latest";
    }
    if (!isHelmLocalRepoSet()) {
      throw new InvalidRequestException("HELM_LOCAL_REPOSITORY is not set in env, can't get local repo \n");
    }
    return Paths.get(getHelmLocalRepositoryPath(), repoName, chartName, chartVersion)
        .toAbsolutePath()
        .normalize()
        .toString();
  }

  public boolean doesChartExistInLocalRepo(String repoName, String chartName, String chartVersion) {
    if (isEmpty(chartVersion)) {
      chartVersion = "latest";
    }
    String workingDir = Paths.get(getHelmLocalRepositoryPath(), repoName, chartName, chartVersion)
                            .toAbsolutePath()
                            .normalize()
                            .toString();
    String chartDir = getChartDirectory(workingDir, chartName);
    try {
      if (checkIfFileExist(chartDir + "/"
              + "Chart.yaml")) {
        return true;
      }
    } catch (IOException e) {
      log.error("Unable to check if Chart.yaml file exists in " + chartDir, e);
    }
    return false;
  }

  public void initHelm(String workingDirectory, HelmVersion helmVersion, long timeoutInMillis) throws IOException {
    String helmHomePath = getHelmHomePath(workingDirectory);
    if (HelmVersion.V2.equals(helmVersion)) {
      createNewDirectoryAtPath(helmHomePath);
    }

    // Helm init command would be blank for helmV3
    String helmInitCommand = HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.INIT, helmVersion)
                                 .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion));
    if (isNotBlank(helmHomePath) && isNotBlank(helmInitCommand)) {
      helmInitCommand = applyHelmHomePath(helmInitCommand, workingDirectory);
      log.info("Initing helm. Command " + helmInitCommand);

      ProcessResult processResult = executeCommand(Collections.emptyMap(), helmInitCommand, workingDirectory,
          "Initing helm Command " + helmInitCommand, timeoutInMillis, HelmCliCommandType.INIT);
      int exitCode = processResult.getExitValue();
      if (exitCode != 0) {
        String processOutput =
            processResult.hasOutput() ? format(PROCESS_RESULT_OUTPUT_FORMAT, processResult.outputUTF8()) : EMPTY;
        String exceptionMessage = format("Failed to init helm. Exit Code = [%s]. Executed command = [%s]. %s", exitCode,
            helmInitCommand, processOutput);
        throw new HelmClientException(exceptionMessage, USER, HelmCliCommandType.INIT);
      }
    }
  }

  public List<ReleaseInfo> parseHelmReleaseCommandOutput(String listReleaseOutput, HelmCommandType helmCommandType)
      throws IOException {
    if (isEmpty(listReleaseOutput)) {
      return new ArrayList<>();
    }
    CSVFormat csvFormat = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter('\t').withTrim();
    return CSVParser.parse(listReleaseOutput, csvFormat)
        .getRecords()
        .stream()
        .map(helmCommandType == RELEASE_HISTORY ? this::releaseHistoryCsvRecordToReleaseInfo
                                                : this::listReleaseCsvRecordToReleaseInfo)
        .collect(Collectors.toList());
  }

  private ReleaseInfo listReleaseCsvRecordToReleaseInfo(CSVRecord releaseRecord) {
    return ReleaseInfo.builder()
        .name(releaseRecord.get("NAME"))
        .revision(releaseRecord.get("REVISION"))
        .status(releaseRecord.get("STATUS"))
        .chart(releaseRecord.get("CHART"))
        .namespace(releaseRecord.get("NAMESPACE"))
        .build();
  }

  private ReleaseInfo releaseHistoryCsvRecordToReleaseInfo(CSVRecord releaseRecord) {
    return ReleaseInfo.builder()
        .revision(releaseRecord.get("REVISION"))
        .status(releaseRecord.get("STATUS"))
        .chart(releaseRecord.get("CHART"))
        .description(releaseRecord.get("DESCRIPTION"))
        .build();
  }

  public void processHelmReleaseHistOutput(ReleaseInfo releaseInfo, boolean ignoreHelmHistFailure) {
    String msg = "Release History command passed but there is an issue with latest release, details: \n"
        + releaseInfo.getDescription();
    if (checkIfFailed(releaseInfo.getDescription()) || checkIfFailed(releaseInfo.getStatus())) {
      if (!ignoreHelmHistFailure) {
        throw new HelmClientException(msg, USER, HelmCliCommandType.RELEASE_HISTORY);
      }
      log.error(msg);
      log.info("Deliberately ignoring this error and proceeding with install/upgrade \n");
    }
  }

  public boolean checkIfFailed(String str) {
    if (!isEmpty(str)) {
      return str.toLowerCase().contains("failed");
    }
    return false;
  }

  public void loginOciRegistry(String repoUrl, String userName, char[] password, HelmVersion helmVersion,
      long timeoutInMillis, String destinationDirectory, String registryConfigFilePath) {
    if (!HelmVersion.isHelmV3(helmVersion)) {
      throw new HelmClientException(
          "OCI Registry is supported only for Helm V3", USER, HelmCliCommandType.OCI_REGISTRY_LOGIN);
    }

    Map<String, String> environment = new HashMap<>();
    String registryLoginCmd =
        HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.OCI_REGISTRY_LOGIN, helmVersion)
            .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion))
            .replace(REGISTRY_URL, repoUrl)
            .replace(USERNAME, getUsername(userName))
            .replace(PASSWORD, getPassword(password));

    if (isNotEmpty(registryConfigFilePath)) {
      registryLoginCmd = addRegistryConfig(registryLoginCmd, registryConfigFilePath);
    }

    String evaluatedPassword = isEmpty(getPassword(password)) ? StringUtils.EMPTY : "--password *******";
    String registryLoginCmdForLogging =
        HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.OCI_REGISTRY_LOGIN, helmVersion)
            .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion))
            .replace(REGISTRY_URL, repoUrl)
            .replace(USERNAME, getUsername(userName))
            .replace(PASSWORD, evaluatedPassword);

    if (isNotEmpty(registryConfigFilePath)) {
      registryLoginCmdForLogging = addRegistryConfig(registryLoginCmdForLogging, registryConfigFilePath);
    }

    ProcessResult processResult = executeCommand(environment, registryLoginCmd, destinationDirectory,
        "Attempt Login to OCI Registry. Command Executed: " + registryLoginCmdForLogging, timeoutInMillis,
        HelmCliCommandType.OCI_REGISTRY_LOGIN);
    int exitCode = processResult.getExitValue();
    if (exitCode != 0) {
      String processOutput =
          processResult.hasOutput() ? format(PROCESS_RESULT_OUTPUT_FORMAT, processResult.outputUTF8()) : EMPTY;
      String exceptionMessage =
          format("Failed to login to the helm OCI Registry repo. Exit Code = [%s]. Executed command = [%s]. %s",
              exitCode, registryLoginCmdForLogging, processOutput);
      throw new HelmClientException(exceptionMessage, USER, HelmCliCommandType.OCI_REGISTRY_LOGIN);
    }
  }

  public String addRegistryConfig(String cmd, String regConfigFilePath) {
    String cmdWithRegConfig = cmd + " " + REGISTRY_CONFIG_SUFFIX;
    return cmdWithRegConfig.replace(REGISTRY_CONFIG, regConfigFilePath);
  }

  public void addRepoInternal(String repoName, String repoDisplayName, String chartRepoUrl, String username,
      char[] password, String chartDirectory, HelmVersion helmVersion, long timeoutInMillis, String tempDir,
      HelmCommandFlag helmCommandFlag) {
    Map<String, String> environment = new HashMap<>();
    String repoAddCommand =
        getHttpRepoAddCommand(repoName, chartRepoUrl, username, password, chartDirectory, helmVersion, helmCommandFlag);
    String repoAddCommandForLogging = getHttpRepoAddCommandForLogging(
        repoName, chartRepoUrl, username, password, chartDirectory, helmVersion, helmCommandFlag);
    if (!isEmpty(tempDir)) {
      environment.putIfAbsent(HELM_CACHE_HOME,
          HELM_CACHE_HOME_PATH.replace(REPO_NAME, repoName).replace(HELM_CACHE_HOME_PLACEHOLDER, tempDir));
      repoAddCommand = addRepoFlags(repoAddCommand, repoName, tempDir);
      repoAddCommandForLogging = addRepoFlags(repoAddCommandForLogging, repoName, tempDir);
    }
    log.info(repoAddCommandForLogging);
    log.info(ADD_COMMAND_FOR_REPOSITORY + repoDisplayName);

    ProcessResult processResult = executeAddRepo(
        repoAddCommand, environment, chartDirectory, timeoutInMillis, repoAddCommandForLogging, helmVersion);
    int exitCode = processResult.getExitValue();
    if (exitCode != 0) {
      String processOutput =
          processResult.hasOutput() ? format(PROCESS_RESULT_OUTPUT_FORMAT, processResult.outputUTF8()) : EMPTY;
      String exceptionMessage = format("Failed to add helm repo. Exit Code = [%s]. Executed command = [%s]. %s",
          exitCode, repoAddCommandForLogging, processOutput);
      throw new HelmClientException(exceptionMessage, USER, HelmCliCommandType.REPO_ADD);
    }
  }

  public void addRepo(String repoName, String repoDisplayName, String chartRepoUrl, String username, char[] password,
      String chartDirectory, HelmVersion helmVersion, long timeoutInMillis, String cacheDir,
      HelmCommandFlag helmCommandFlag) {
    if (isEmpty(cacheDir)) {
      addRepoInternal(repoName, repoDisplayName, chartRepoUrl, username, password, chartDirectory, helmVersion,
          timeoutInMillis, EMPTY, helmCommandFlag);
      if (HelmVersion.V380.equals(helmVersion)) {
        updateRepo(repoName, chartDirectory, helmVersion, timeoutInMillis, EMPTY, helmCommandFlag);
      }
      return;
    }

    addRepoInternal(repoName, repoDisplayName, chartRepoUrl, username, password, chartDirectory, helmVersion,
        timeoutInMillis, cacheDir, helmCommandFlag);
    updateRepo(repoName, chartDirectory, helmVersion, timeoutInMillis, cacheDir, helmCommandFlag);
  }

  private ProcessResult executeAddRepo(String addCommand, Map<String, String> env, String chartDirectory,
      long timeoutInMillis, String addCommandLogging, HelmVersion helmVersion) {
    ProcessResult processResult = executeCommand(env, addCommand, chartDirectory,
        "add helm repo. Executed command" + addCommandLogging, timeoutInMillis, HelmCliCommandType.REPO_ADD);
    if (HelmVersion.isHelmV3(helmVersion) && processResult.getExitValue() != 0) {
      String output = processResult.hasOutput() ? processResult.getOutput().getUTF8() : null;
      // Starting from helm 3.3.4, when --force-update not enabled and there is an update in repo configuration
      // (for example, password is updated) helm repo add will fail with: repository name (repo-name) already exists,
      // please specify a different name. For this case will try again with --force-update
      if (isNotEmpty(output) && output.contains("already exists")) {
        String forceAddRepoCommand = addCommand + HELM_REPO_ADD_FORCE_UPDATE;
        String forceAddCommandLogging = addCommandLogging + HELM_REPO_ADD_FORCE_UPDATE;
        log.info("Detected repository configuration change after executing: {}. Try again using: {}", addCommandLogging,
            forceAddCommandLogging);
        return executeCommand(env, forceAddRepoCommand, chartDirectory,
            "add helm repo. Executed command" + forceAddCommandLogging, timeoutInMillis, HelmCliCommandType.REPO_ADD);
      }
    }

    return processResult;
  }

  private String getHttpRepoAddCommand(String repoName, String chartRepoUrl, String username, char[] password,
      String workingDirectory, HelmVersion helmVersion, HelmCommandFlag helmCommandFlag) {
    String addRepoCommand =
        getHttpRepoAddCommandWithoutPassword(repoName, chartRepoUrl, username, workingDirectory, helmVersion);
    Map<HelmSubCommandType, String> commandFlagValueMap =
        helmCommandFlag != null ? helmCommandFlag.getValueMap() : null;
    addRepoCommand = HelmCommandFlagsUtils.applyHelmCommandFlags(
        addRepoCommand, HelmCliCommandType.REPO_ADD.name(), commandFlagValueMap, helmVersion);
    return addRepoCommand.replace(PASSWORD, getPassword(password));
  }

  private String getHttpRepoAddCommandForLogging(String repoName, String chartRepoUrl, String username, char[] password,
      String workingDirectory, HelmVersion helmVersion, HelmCommandFlag helmCommandFlag) {
    String repoAddCommand =
        getHttpRepoAddCommandWithoutPassword(repoName, chartRepoUrl, username, workingDirectory, helmVersion);
    String evaluatedPassword = isEmpty(getPassword(password)) ? StringUtils.EMPTY : "--password *******";

    Map<HelmSubCommandType, String> commandFlagValueMap =
        helmCommandFlag != null ? helmCommandFlag.getValueMap() : null;
    repoAddCommand = HelmCommandFlagsUtils.applyHelmCommandFlags(
        repoAddCommand, HelmCliCommandType.REPO_ADD.name(), commandFlagValueMap, helmVersion);

    return repoAddCommand.replace(PASSWORD, evaluatedPassword);
  }

  private String getHttpRepoAddCommandWithoutPassword(
      String repoName, String chartRepoUrl, String username, String workingDirectory, HelmVersion helmVersion) {
    String command = HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_ADD_HTTP, helmVersion)
                         .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion))
                         .replace(REPO_NAME, repoName)
                         .replace(REPO_URL, chartRepoUrl)
                         .replace(USERNAME, getUsername(username));

    return applyHelmHomePath(command, workingDirectory);
  }

  public String applyHelmHomePath(String command, String workingDirectory) {
    if (isBlank(workingDirectory)) {
      return command.replace(HELM_HOME_PATH_FLAG, "");
    } else {
      String helmHomePath = getHelmHomePath(workingDirectory);
      return command.replace(HELM_HOME_PATH_FLAG, "--home " + helmHomePath);
    }
  }

  public String addRepoFlags(String command, String repoName, String tempDir) {
    if (isNotBlank(repoName) && isNotEmpty(tempDir)) {
      return command + HELM_REPO_FLAGS.replace(HELM_CACHE_HOME_PLACEHOLDER, tempDir).replace(REPO_NAME, repoName);
    }
    return command;
  }

  public String getHelmHomePath(String workingDirectory) {
    return Paths.get(workingDirectory, "helm").normalize().toAbsolutePath().toString();
  }

  public String getHelmPath(HelmVersion helmVersion) {
    return encloseWithQuotesIfNeeded(k8sGlobalConfigService.getHelmPath(helmVersion));
  }

  public String getUsername(String username) {
    return isBlank(username) ? "" : "--username " + username;
  }

  public String getPassword(char[] password) {
    if (password == null) {
      return "";
    }

    String passwordAsString = new String(password);
    return isBlank(passwordAsString) ? "" : "--password " + passwordAsString;
  }

  public ProcessResult executeCommand(Map<String, String> envVars, String command, String directoryPath,
      String errorMessage, long timeoutInMillis, HelmCliCommandType helmCliCommandType) {
    ProcessExecutor processExecutor = createProcessExecutor(command, directoryPath, timeoutInMillis, envVars);
    return executeCommand(processExecutor, errorMessage, helmCliCommandType);
  }

  private ProcessResult executeCommand(
      ProcessExecutor processExecutor, String errorMessage, HelmCliCommandType helmCliCommandType) {
    errorMessage = isEmpty(errorMessage) ? "" : errorMessage;
    try {
      return processExecutor.execute();
    } catch (IOException e) {
      // Not setting the cause here because it carries forward the commands which can contain passwords
      throw new HelmClientException(format("[IO exception] %s", errorMessage), USER, helmCliCommandType);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HelmClientException(format("[Interrupted] %s", errorMessage), USER, helmCliCommandType);
    } catch (TimeoutException | UncheckedTimeoutException e) {
      throw new HelmClientException(format("[Timed out] %s", errorMessage), USER, helmCliCommandType);
    }
  }

  public ProcessExecutor createProcessExecutor(
      String command, String directoryPath, long timeoutInMillis, Map<String, String> envVars) {
    return new ProcessExecutor()
        .directory(isNotBlank(directoryPath) ? new File(directoryPath) : null)
        .timeout(timeoutInMillis, TimeUnit.MILLISECONDS)
        .commandSplit(command)
        .environment(envVars)
        .readOutput(true);
  }

  public String createNewDirectoryAtPath(String directoryBase) throws IOException {
    String workingDirectory = Paths.get(directoryBase, convertBase64UuidToCanonicalForm(generateUuid()))
                                  .normalize()
                                  .toAbsolutePath()
                                  .toString();
    createAndWaitForDir(workingDirectory);
    return workingDirectory;
  }

  public void createAndWaitForDir(String dir) throws IOException {
    createDirectoryIfDoesNotExist(dir);
    waitForDirectoryToBeAccessibleOutOfProcess(dir, 10);
  }

  public void removeRepo(String repoName, String workingDirectory, HelmVersion helmVersion, long timeoutInMillis) {
    removeRepo(repoName, workingDirectory, helmVersion, timeoutInMillis, EMPTY);
  }

  public void removeRepo(
      String repoName, String workingDirectory, HelmVersion helmVersion, long timeoutInMillis, String cacheDir) {
    try {
      Map<String, String> environment = new HashMap<>();
      String repoRemoveCommand = getRepoRemoveCommand(repoName, workingDirectory, helmVersion);
      if (!isEmpty(cacheDir)) {
        environment.putIfAbsent(HELM_CACHE_HOME,
            HELM_CACHE_HOME_PATH.replace(REPO_NAME, repoName).replace(HELM_CACHE_HOME_PLACEHOLDER, cacheDir));
        repoRemoveCommand = addRepoFlags(repoRemoveCommand, repoName, cacheDir);
      }
      ProcessResult processResult = executeCommand(environment, repoRemoveCommand, null,
          format("remove helm repo %s", repoName), timeoutInMillis, HelmCliCommandType.REPO_REMOVE);
      if (processResult.getExitValue() != 0) {
        log.warn("Failed to remove helm repo {}. {}", repoName, processResult.getOutput().getUTF8());
      }
    } catch (Exception ex) {
      log.warn(ExceptionUtils.getMessage(ex));
    }
  }

  private String getRepoRemoveCommand(String repoName, String workingDirectory, HelmVersion helmVersion) {
    String repoRemoveCommand =
        HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_REMOVE, helmVersion)
            .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion))
            .replace(REPO_NAME, repoName);

    return applyHelmHomePath(repoRemoveCommand, workingDirectory);
  }

  public void cleanup(String workingDirectory) {
    try {
      log.info("Cleaning up directory " + workingDirectory);
      deleteDirectoryAndItsContentIfExists(workingDirectory);
    } catch (Exception ex) {
      log.warn("Exception in directory cleanup.", ex);
    }
  }

  public String getHelmFetchCommand(String chartName, String chartVersion, String repoName, String workingDirectory,
      HelmVersion helmVersion, HelmCommandFlag helmCommandFlag, String regFileConfig) {
    HelmCliCommandType commandType = HelmCliCommandType.FETCH;
    String helmFetchCommand = HelmCommandTemplateFactory.getHelmCommandTemplate(commandType, helmVersion)
                                  .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion))
                                  .replace("${CHART_NAME}", chartName)
                                  .replace("${CHART_VERSION}", getChartVersion(chartVersion));

    if (isNotEmpty(regFileConfig)) {
      helmFetchCommand = addRegistryConfig(helmFetchCommand, regFileConfig);
    }

    if (isNotBlank(repoName)) {
      helmFetchCommand = helmFetchCommand.replace(REPO_NAME, repoName);
    } else {
      helmFetchCommand = helmFetchCommand.replace(REPO_NAME + "/", "");
    }

    Map<HelmSubCommandType, String> commandFlagValueMap =
        helmCommandFlag != null ? helmCommandFlag.getValueMap() : null;
    helmFetchCommand = HelmCommandFlagsUtils.applyHelmCommandFlags(
        helmFetchCommand, commandType.name(), commandFlagValueMap, helmVersion);
    return applyHelmHomePath(helmFetchCommand, workingDirectory);
  }

  public void fetchChartFromRepo(String repoName, String repoDisplayName, String chartName, String chartVersion,
      String chartDirectory, HelmVersion helmVersion, HelmCommandFlag helmCommandFlag, long timeoutInMillis,
      String cacheDir, String registryFileConfig) {
    String helmFetchCommand = getHelmFetchCommand(
        chartName, chartVersion, repoName, chartDirectory, helmVersion, helmCommandFlag, registryFileConfig);
    if (isEmpty(cacheDir)) {
      executeFetchChartFromRepo(
          chartName, chartDirectory, repoDisplayName, helmFetchCommand, timeoutInMillis, chartVersion);
      return;
    }

    executeFetchChartFromRepoUseRepoFlag(chartName, chartDirectory, repoDisplayName, helmFetchCommand, timeoutInMillis,
        repoName, cacheDir, chartVersion);
  }

  public void executeFetchChartFromRepoUseRepoFlag(String chartName, String chartDirectory, String repoDisplayName,
      String helmFetchCommand, long timeoutInMillis, String repoName, String dir, String chartVersion) {
    Map<String, String> environment = new HashMap<>();
    environment.put(
        HELM_CACHE_HOME, HELM_CACHE_HOME_PATH.replace(REPO_NAME, repoName).replace(HELM_CACHE_HOME_PLACEHOLDER, dir));
    helmFetchCommand = addRepoFlags(helmFetchCommand, repoName, dir);

    log.info(helmFetchCommand);

    ProcessResult processResult = executeCommand(environment, helmFetchCommand, chartDirectory,
        format("fetch chart %s", chartName), timeoutInMillis, HelmCliCommandType.FETCH);

    int exitCode = processResult.getExitValue();
    if (exitCode != 0) {
      StringBuilder builder = new StringBuilder().append("Failed to fetch chart \"").append(chartName).append("\" ");
      if (isNotBlank(repoDisplayName)) {
        builder.append(" from repo \"").append(repoDisplayName).append("\". ");
      }
      builder.append(format("Exit code: [%s]. ", exitCode)).append("Please check if the chart is present in the repo.");
      if (processResult.hasOutput()) {
        builder.append(" Details: ").append(processResult.outputUTF8());
      }
      throw new HelmClientException(builder.toString(), HelmCliCommandType.FETCH);
    }

    if (!checkChartVersion(chartVersion, chartDirectory, chartName)) {
      throw new HelmClientException(
          "Chart version specified and fetched don't match. Please check the input chart version",
          HelmCliCommandType.FETCH);
    }
  }

  public void executeFetchChartFromRepo(String chartName, String chartDirectory, String repoDisplayName,
      String helmFetchCommand, long timeoutInMillis, String chartVersion) {
    log.info(helmFetchCommand);

    ProcessResult processResult = executeCommand(Collections.emptyMap(), helmFetchCommand, chartDirectory,
        format("fetch chart %s", chartName), timeoutInMillis, HelmCliCommandType.FETCH);
    int exitCode = processResult.getExitValue();
    if (exitCode != 0) {
      StringBuilder builder = new StringBuilder().append("Failed to fetch chart \"").append(chartName).append("\" ");

      if (isNotBlank(repoDisplayName)) {
        builder.append(" from repo \"").append(repoDisplayName).append("\". ");
      }
      builder.append(format("Exit code: [%s]. ", exitCode)).append("Please check if the chart is present in the repo.");
      if (processResult.hasOutput()) {
        builder.append(" Details: ").append(processResult.outputUTF8());
      }
      throw new HelmClientException(builder.toString(), HelmCliCommandType.FETCH);
    }

    if (!checkChartVersion(chartVersion, chartDirectory, chartName)) {
      throw new HelmClientException(
          "Chart version specified and fetched don't match. Please check the input chart version",
          HelmCliCommandType.FETCH);
    }
  }

  public boolean checkChartVersion(String chartVersion, String chartDirectory, String chartName) {
    if (isEmpty(chartVersion)) {
      return true;
    }
    if (chartVersion.indexOf('+') == -1) {
      // this means the version doesn't contain any metadata
      return true;
    }
    String chart = "";
    try {
      chart = new String(Files.readAllBytes(Paths.get(chartDirectory, chartName, "Chart.yaml")));
    } catch (IOException e) {
      throw new HelmClientException(
          format("[IO exception] %s", "Failed to fetch pulled chart version"), USER, HelmCliCommandType.FETCH);
    }

    try {
      return ObjectYamlUtils.getField(ObjectYamlUtils.readYaml(chart).get(0), "version").equals(chartVersion);
    } catch (YamlException e) {
      throw new HelmClientException(
          format("[Yaml Exception] %s", "Failed to read Chart.yaml"), USER, HelmCliCommandType.FETCH);
    }
  }

  public void downloadChartFilesFromHttpRepo(
      HelmChartManifestDelegateConfig manifest, String destinationDirectory, long timeoutInMillis) {
    if (!(manifest.getStoreDelegateConfig() instanceof HttpHelmStoreDelegateConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("storeDelegateConfig", "Must be instance of HttpHelmStoreDelegateConfig"));
    }

    HttpHelmStoreDelegateConfig storeDelegateConfig = (HttpHelmStoreDelegateConfig) manifest.getStoreDelegateConfig();
    HttpHelmConnectorDTO httpHelmConnector = storeDelegateConfig.getHttpHelmConnector();

    String username = getHttpHelmUsername(httpHelmConnector);
    char[] password = getHttpHelmPassword(httpHelmConnector);

    String cacheDir = getCacheDir(manifest, storeDelegateConfig.getRepoName(), manifest.getHelmVersion());

    try {
      addRepo(storeDelegateConfig.getRepoName(), storeDelegateConfig.getRepoDisplayName(),
          httpHelmConnector.getHelmRepoUrl(), username, password, destinationDirectory, manifest.getHelmVersion(),
          timeoutInMillis, cacheDir, manifest.getHelmCommandFlag());
      fetchChartFromRepo(storeDelegateConfig.getRepoName(), storeDelegateConfig.getRepoDisplayName(),
          manifest.getChartName(), manifest.getChartVersion(), destinationDirectory, manifest.getHelmVersion(),
          manifest.getHelmCommandFlag(), timeoutInMillis, cacheDir, "");
    } finally {
      if (isNotEmpty(cacheDir) && !manifest.isUseCache()) {
        try {
          deleteDirectoryAndItsContentIfExists(Paths.get(cacheDir).getParent().toString());
        } catch (IOException ie) {
          log.error(
              "Deletion of folder failed due to : {}", ExceptionMessageSanitizer.sanitizeException(ie).getMessage());
        }
      }
    }
  }

  public void downloadChartFilesFromOciRepo(
      HelmChartManifestDelegateConfig manifest, String destinationDirectory, long timeoutInMillis) throws Exception {
    if (!(manifest.getStoreDelegateConfig() instanceof OciHelmStoreDelegateConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("storeDelegateConfig", "Must be instance of OciHelmStoreDelegateConfig"));
    }

    OciHelmStoreDelegateConfig storeDelegateConfig = (OciHelmStoreDelegateConfig) manifest.getStoreDelegateConfig();
    OciHelmConnectorDTO ociHelmConnector = storeDelegateConfig.getOciHelmConnector();

    String cacheDir = getCacheDir(manifest, storeDelegateConfig.getRepoName(), HelmVersion.V380);

    // create registry-config per deployment and pass this along to getRepoName
    String registryConfigFilePath = getRegFileConfigPath();

    try {
      String repoName = getRepoName(ociHelmConnector, storeDelegateConfig.getBasePath(), timeoutInMillis,
          destinationDirectory, registryConfigFilePath);
      fetchChartFromRepo(repoName, storeDelegateConfig.getRepoDisplayName(), manifest.getChartName(),
          manifest.getChartVersion(), destinationDirectory, HelmVersion.V380, manifest.getHelmCommandFlag(),
          timeoutInMillis, cacheDir, registryConfigFilePath);
    } finally {
      if (!manifest.isUseCache()) {
        try {
          deleteDirectoryAndItsContentIfExists(Paths.get(cacheDir).getParent().toString());
        } catch (IOException ie) {
          log.error(
              "Deletion of folder failed due to : {}", ExceptionMessageSanitizer.sanitizeException(ie).getMessage());
        }
      }
      // delete registry-config file
      FileIo.deleteFileIfExists(registryConfigFilePath);
    }
  }

  public String getRegFileConfigPath() {
    return Paths
        .get(RESOURCE_DIR_BASE, REGISTRY_CONFIG_DIR,
            RandomStringUtils.randomAlphabetic(5).toLowerCase(Locale.ROOT) + "-" + REGISTRY_CONFIG_JSON)
        .toAbsolutePath()
        .normalize()
        .toString();
  }

  private String getRepoName(OciHelmConnectorDTO ociHelmConnectorDTO, String basePath, long timeoutInMillis,
      String destinationDirectory, String registryConfigFilePath) throws Exception {
    String repoName;
    if (OciHelmAuthType.USER_PASSWORD.equals(ociHelmConnectorDTO.getAuth().getAuthType())) {
      String repoUrl = getParsedUrlForUserNamePwd(ociHelmConnectorDTO.getHelmRepoUrl());
      loginOciRegistry(repoUrl, getOciHelmUsername(ociHelmConnectorDTO), getOciHelmPassword(ociHelmConnectorDTO),
          HelmVersion.V380, timeoutInMillis, destinationDirectory, registryConfigFilePath);
      repoName = format(REGISTRY_URL_PREFIX, Paths.get(repoUrl, basePath).normalize());
    } else if (OciHelmAuthType.ANONYMOUS.equals(ociHelmConnectorDTO.getAuth().getAuthType())) {
      String ociUrl = getParsedURI(ociHelmConnectorDTO.getHelmRepoUrl()).toString();
      repoName = addBasePathToOciUrl(ociUrl, basePath);
    } else {
      throw new InvalidArgumentsException(
          format("Invalid oci auth type  %s", ociHelmConnectorDTO.getAuth().getAuthType()));
    }
    return repoName;
  }

  private String getCacheDir(HelmChartManifestDelegateConfig manifest, String repoName, HelmVersion version) {
    if (HelmVersion.V2.equals(version)) {
      return EMPTY;
    }
    if (manifest.isUseCache()) {
      return Paths.get(RESOURCE_DIR_BASE, repoName, "cache").toAbsolutePath().normalize().toString();
    }
    return Paths
        .get(RESOURCE_DIR_BASE, repoName, RandomStringUtils.randomAlphabetic(5).toLowerCase(Locale.ROOT), "cache")
        .toAbsolutePath()
        .normalize()
        .toString();
  }

  public void downloadChartFilesUsingChartMuseum(
      HelmChartManifestDelegateConfig manifest, String destinationDirectory, long timeoutInMillis) throws Exception {
    String resourceDirectory = null;
    ChartmuseumClient chartmuseumClient = null;
    ChartMuseumServer chartMuseumServer = null;
    String repoName = null;
    String repoDisplayName = null;
    String bucketName = null;
    StoreDelegateConfig storeDelegateConfig = manifest.getStoreDelegateConfig();
    if (S3_HELM == storeDelegateConfig.getType()) {
      S3HelmStoreDelegateConfig s3StoreDelegateConfig = (S3HelmStoreDelegateConfig) storeDelegateConfig;
      repoName = s3StoreDelegateConfig.getRepoName();
      repoDisplayName = s3StoreDelegateConfig.getRepoDisplayName();
      bucketName = s3StoreDelegateConfig.getBucketName();
    } else if (GCS_HELM == storeDelegateConfig.getType()) {
      GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig = (GcsHelmStoreDelegateConfig) storeDelegateConfig;
      repoName = gcsHelmStoreDelegateConfig.getRepoName();
      repoDisplayName = gcsHelmStoreDelegateConfig.getRepoDisplayName();
      bucketName = gcsHelmStoreDelegateConfig.getBucketName();
    }

    repoName = repoName + "-" + bucketName;

    String cacheDir = getCacheDir(manifest, repoName, manifest.getHelmVersion());

    try {
      resourceDirectory = createNewDirectoryAtPath(RESOURCE_DIR_BASE);
      chartmuseumClient = ngChartmuseumClientFactory.createClient(manifest.getStoreDelegateConfig(), resourceDirectory);
      chartMuseumServer = chartmuseumClient.start();

      addChartMuseumRepo(repoName, repoDisplayName, chartMuseumServer.getPort(), destinationDirectory,
          manifest.getHelmVersion(), timeoutInMillis, cacheDir, manifest.getHelmCommandFlag());
      fetchChartFromRepo(repoName, repoDisplayName, manifest.getChartName(), manifest.getChartVersion(),
          destinationDirectory, manifest.getHelmVersion(), manifest.getHelmCommandFlag(), timeoutInMillis, cacheDir,
          "");

    } finally {
      if (chartmuseumClient != null && chartMuseumServer != null) {
        chartmuseumClient.stop(chartMuseumServer);
      }

      if (repoName != null) {
        removeRepo(repoName, destinationDirectory, manifest.getHelmVersion(), timeoutInMillis);
      }

      cleanup(resourceDirectory);

      if (isNotEmpty(cacheDir) && !manifest.isUseCache()) {
        try {
          deleteDirectoryAndItsContentIfExists(Paths.get(cacheDir).getParent().toString());
        } catch (IOException ie) {
          log.error(
              "Deletion of folder failed due to : {}", ExceptionMessageSanitizer.sanitizeException(ie).getMessage());
        }
      }
    }
  }

  public void addChartMuseumRepo(String repoName, String repoDisplayName, int port, String chartDirectory,
      HelmVersion helmVersion, long timeoutInMillis, String cacheDir, HelmCommandFlag helmCommandFlag) {
    String repoAddCommand = getChartMuseumRepoAddCommand(repoName, port, chartDirectory, helmVersion, helmCommandFlag);

    Map<String, String> environment = new HashMap<>();
    if (!isEmpty(cacheDir)) {
      environment.putIfAbsent(HELM_CACHE_HOME,
          HELM_CACHE_HOME_PATH.replace(REPO_NAME, repoName).replace(HELM_CACHE_HOME_PLACEHOLDER, cacheDir));
      repoAddCommand = addRepoFlags(repoAddCommand, repoName, cacheDir);
    }

    log.info(repoAddCommand);
    log.info(ADD_COMMAND_FOR_REPOSITORY + repoDisplayName);

    ProcessResult processResult =
        executeAddRepo(repoAddCommand, environment, chartDirectory, timeoutInMillis, repoAddCommand, helmVersion);

    int exitCode = processResult.getExitValue();
    if (exitCode != 0) {
      String processOutput =
          processResult.hasOutput() ? format(PROCESS_RESULT_OUTPUT_FORMAT, processResult.outputUTF8()) : EMPTY;
      String exceptionMessage = format("Failed to add helm repo. Exit Code = [%s]. Executed command = [%s]. %s",
          exitCode, repoAddCommand, processOutput);
      throw new HelmClientException(exceptionMessage, USER, HelmCliCommandType.REPO_ADD);
    }

    if (isEmpty(cacheDir)) {
      return;
    }

    updateRepo(repoName, chartDirectory, helmVersion, timeoutInMillis, cacheDir, null);
  }

  private String getChartMuseumRepoAddCommand(
      String repoName, int port, String workingDirectory, HelmVersion helmVersion, HelmCommandFlag helmCommandFlag) {
    String repoUrl = CHART_MUSEUM_SERVER_URL.replace("${PORT}", Integer.toString(port));

    String repoAddCommand =
        HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_ADD_CHART_MUSEUM, helmVersion)
            .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion))
            .replace(REPO_NAME, repoName)
            .replace(REPO_URL, repoUrl);

    Map<HelmSubCommandType, String> commandFlagValueMap =
        helmCommandFlag != null ? helmCommandFlag.getValueMap() : null;
    repoAddCommand = HelmCommandFlagsUtils.applyHelmCommandFlags(
        repoAddCommand, HelmCliCommandType.REPO_ADD.name(), commandFlagValueMap, helmVersion);

    return applyHelmHomePath(repoAddCommand, workingDirectory);
  }

  public void printHelmChartInfoWithVersionInExecutionLogs(String workingDirectory,
      HelmChartManifestDelegateConfig manifestDelegateConfig, LogCallback executionLogCallback) {
    if (isNotBlank(manifestDelegateConfig.getChartVersion())) {
      printHelmChartInfoInExecutionLogs(manifestDelegateConfig, executionLogCallback);
      return;
    }

    try {
      HelmChartInfo helmChartInfo = getHelmChartInfoFromChartsYamlFile(
          Paths.get(workingDirectory, manifestDelegateConfig.getChartName(), CHARTS_YAML_KEY).toString());

      HelmChartManifestDelegateConfig helmChartManifestDelegateConfigWithChartVersion =
          HelmChartManifestDelegateConfig.builder()
              .storeDelegateConfig(manifestDelegateConfig.getStoreDelegateConfig())
              .chartName(manifestDelegateConfig.getChartName())
              .helmVersion(manifestDelegateConfig.getHelmVersion())
              .chartVersion(helmChartInfo.getVersion())
              .helmCommandFlag(manifestDelegateConfig.getHelmCommandFlag())
              .build();
      printHelmChartInfoInExecutionLogs(helmChartManifestDelegateConfigWithChartVersion, executionLogCallback);
    } catch (Exception e) {
      log.info("Unable to fetch chart version", e);
    }
  }

  public void printHelmChartInfoInExecutionLogs(
      HelmChartManifestDelegateConfig manifestDelegateConfig, LogCallback executionLogCallback) {
    String repoDisplayName = "";
    String basePath = "";
    String chartName = manifestDelegateConfig.getChartName();
    String chartVersion = manifestDelegateConfig.getChartVersion();
    String chartBucket = "";
    String region = "";
    String chartRepoUrl = "";

    switch (manifestDelegateConfig.getStoreDelegateConfig().getType()) {
      case HTTP_HELM:
        HttpHelmStoreDelegateConfig httpStoreDelegateConfig =
            (HttpHelmStoreDelegateConfig) manifestDelegateConfig.getStoreDelegateConfig();
        repoDisplayName = httpStoreDelegateConfig.getRepoDisplayName();
        chartRepoUrl = httpStoreDelegateConfig.getHttpHelmConnector().getHelmRepoUrl();
        break;

      case OCI_HELM:
        OciHelmStoreDelegateConfig ociStoreDelegateConfig =
            (OciHelmStoreDelegateConfig) manifestDelegateConfig.getStoreDelegateConfig();
        repoDisplayName = ociStoreDelegateConfig.getRepoDisplayName();
        basePath = ociStoreDelegateConfig.getBasePath();
        chartRepoUrl = ociStoreDelegateConfig.getOciHelmConnector().getHelmRepoUrl();
        break;

      case S3_HELM:
        S3HelmStoreDelegateConfig s3HelmStoreDelegateConfig =
            (S3HelmStoreDelegateConfig) manifestDelegateConfig.getStoreDelegateConfig();
        repoDisplayName = s3HelmStoreDelegateConfig.getRepoDisplayName();
        basePath = s3HelmStoreDelegateConfig.getFolderPath();
        chartBucket = s3HelmStoreDelegateConfig.getBucketName();
        region = s3HelmStoreDelegateConfig.getRegion();
        break;

      case GCS_HELM:
        GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig =
            (GcsHelmStoreDelegateConfig) manifestDelegateConfig.getStoreDelegateConfig();
        repoDisplayName = gcsHelmStoreDelegateConfig.getRepoDisplayName();
        basePath = gcsHelmStoreDelegateConfig.getFolderPath();
        chartBucket = gcsHelmStoreDelegateConfig.getBucketName();
        break;

      default:
    }

    if (isNotBlank(repoDisplayName)) {
      executionLogCallback.saveExecutionLog("Helm repository: " + repoDisplayName);
    }

    if (isNotBlank(basePath)) {
      executionLogCallback.saveExecutionLog("Base Path: " + basePath);
    }

    if (isNotBlank(chartName)) {
      executionLogCallback.saveExecutionLog("Chart name: " + chartName);
    }

    if (isNotBlank(chartVersion)) {
      executionLogCallback.saveExecutionLog("Chart version: " + chartVersion);
    }

    if (manifestDelegateConfig.getHelmVersion() != null) {
      executionLogCallback.saveExecutionLog("Helm version: " + manifestDelegateConfig.getHelmVersion());
    }

    if (isNotBlank(chartBucket)) {
      executionLogCallback.saveExecutionLog("Chart bucket: " + chartBucket);
    }

    if (isNotBlank(region)) {
      executionLogCallback.saveExecutionLog("Region: " + region);
    }

    if (isNotBlank(chartRepoUrl)) {
      executionLogCallback.saveExecutionLog("Repo url: " + chartRepoUrl);
    }
  }

  private String getChartVersion(String chartVersion) {
    return isBlank(chartVersion) ? StringUtils.EMPTY : "--version " + chartVersion;
  }

  public static String getChartDirectory(String parentDir, String chartName) {
    int lastIndex = chartName.lastIndexOf('/');
    if (lastIndex != -1) {
      return Paths.get(parentDir, chartName.substring(lastIndex + 1)).toString();
    }
    return Paths.get(parentDir, chartName).toString();
  }

  public String getRepoNameNG(StoreDelegateConfig storeDelegateConfig) {
    if (storeDelegateConfig instanceof S3HelmStoreDelegateConfig) {
      return ((S3HelmStoreDelegateConfig) storeDelegateConfig).getRepoName() + "-"
          + ((S3HelmStoreDelegateConfig) storeDelegateConfig).getBucketName();
    } else if (storeDelegateConfig instanceof GcsHelmStoreDelegateConfig) {
      return ((GcsHelmStoreDelegateConfig) storeDelegateConfig).getRepoName() + "-"
          + ((GcsHelmStoreDelegateConfig) storeDelegateConfig).getBucketName();
    } else if (storeDelegateConfig instanceof OciHelmStoreDelegateConfig) {
      return ((OciHelmStoreDelegateConfig) storeDelegateConfig).getRepoName();
    }
    return ((HttpHelmStoreDelegateConfig) storeDelegateConfig).getRepoName();
  }

  public Map<String, HelmFetchFileResult> fetchValuesYamlFromChart(
      HelmChartManifestDelegateConfig helmChartManifestDelegateConfig, long timeoutInMillis, LogCallback logCallback,
      List<HelmFetchFileConfig> helmFetchFileConfigList) throws Exception {
    logCallback.saveExecutionLog(color("\nStarting fetching Helm values", White, Bold));
    String workingDirectory;
    String repoName = getRepoNameNG(helmChartManifestDelegateConfig.getStoreDelegateConfig());
    logCallback.saveExecutionLog(color("\nFetching values.yaml from helm chart repo", White, Bold));
    if (!isHelmLocalRepoSet()) {
      workingDirectory = createNewDirectoryAtPath(
          Paths.get(HELM_FETCH_OLD_WORKING_DIR_BASE.replace("${REPO_NAME}", repoName)).toString());
      downloadHelmChart(helmChartManifestDelegateConfig, timeoutInMillis, logCallback, workingDirectory);
    } else {
      workingDirectory = getHelmLocalRepositoryCompletePath(
          repoName, helmChartManifestDelegateConfig.getChartName(), helmChartManifestDelegateConfig.getChartVersion());
      createAndWaitForDir(workingDirectory);
      populateChartToLocalHelmRepo(helmChartManifestDelegateConfig, timeoutInMillis, logCallback, workingDirectory);
    }

    try {
      return getFetchFileResult(
          helmChartManifestDelegateConfig, logCallback, helmFetchFileConfigList, workingDirectory);
    } catch (Exception e) {
      logCallback.saveExecutionLog("Failed to fetch chart. Reason: " + ExceptionUtils.getMessage(e), WARN);
      throw e;
    } finally {
      if (!isHelmLocalRepoSet()) {
        cleanup(workingDirectory);
      }
    }
  }

  public void populateChartToLocalHelmRepo(HelmChartManifestDelegateConfig helmChartConfig, long timeoutInMillis,
      LogCallback logCallback, String workingDirectory) throws Exception {
    try {
      String repoName = getRepoNameNG(helmChartConfig.getStoreDelegateConfig());
      if (!doesChartExistInLocalRepo(repoName, helmChartConfig.getChartName(), helmChartConfig.getChartVersion())) {
        synchronized (this) {
          if (!doesChartExistInLocalRepo(repoName, helmChartConfig.getChartName(), helmChartConfig.getChartVersion())) {
            logCallback.saveExecutionLog("Did not find the chart and version in local repo: " + workingDirectory);
            downloadHelmChart(helmChartConfig, timeoutInMillis, logCallback, workingDirectory);
          } else {
            logCallback.saveExecutionLog("Found the chart at local repo at path: " + workingDirectory);
          }
        }
      }
    } catch (Exception e) {
      logCallback.saveExecutionLog("Failed to fetch chart. Reason: " + ExceptionUtils.getMessage(e), WARN);
      throw e;
    }
  }

  private void downloadHelmChart(HelmChartManifestDelegateConfig helmChartManifestDelegateConfig, long timeoutInMillis,
      LogCallback logCallback, String workingDirectory) throws Exception {
    try {
      downloadHelmChartFiles(helmChartManifestDelegateConfig, workingDirectory, timeoutInMillis);
      printHelmChartInfoWithVersionInExecutionLogs(workingDirectory, helmChartManifestDelegateConfig, logCallback);
      logCallback.saveExecutionLog(color("\nFollowing were fetched successfully :", White, Bold));
    } catch (HelmClientException ex) {
      String errorMsg = format("Failed to fetch values yaml from %s repo. ",
          helmChartManifestDelegateConfig.getStoreDelegateConfig().getType());
      logCallback.saveExecutionLog(errorMsg + ExceptionUtils.getMessage(ex), WARN);
      throw new HelmClientRuntimeException(ex);
    } catch (Exception ex) {
      String errorMsg = format("Failed to fetch values yaml from %s repo. ",
          helmChartManifestDelegateConfig.getStoreDelegateConfig().getType());
      logCallback.saveExecutionLog(errorMsg + ExceptionUtils.getMessage(ex), WARN);
      throw ex;
    }
  }

  @NotNull
  private Map<String, HelmFetchFileResult> getFetchFileResult(
      HelmChartManifestDelegateConfig helmChartManifestDelegateConfig, LogCallback logCallback,
      List<HelmFetchFileConfig> helmFetchFileConfigList, String workingDirectory) throws Exception {
    String chartDirectory = getChartDirectory(workingDirectory, helmChartManifestDelegateConfig.getChartName());
    Map<String, HelmFetchFileResult> helmValueFetchFilesResultMap = new HashMap<>();
    if (isNotEmpty(helmFetchFileConfigList)) {
      for (HelmFetchFileConfig helmFetchFileConfig : helmFetchFileConfigList) {
        try {
          HelmFetchFileResult valuesFileContentList =
              readValuesYamlFromChartFiles(chartDirectory, helmFetchFileConfig, logCallback);
          String identifier = helmFetchFileConfig.getIdentifier();
          if (helmValueFetchFilesResultMap.containsKey(identifier)) {
            helmValueFetchFilesResultMap.get(identifier).addAllFrom(valuesFileContentList);
          } else {
            helmValueFetchFilesResultMap.put(identifier, valuesFileContentList);
          }
        } catch (Exception ex) {
          String errorMsg = format("Failed to fetch yaml file from %s manifest", helmFetchFileConfig.getIdentifier());
          logCallback.saveExecutionLog(errorMsg + ExceptionUtils.getMessage(ex), WARN);
          if (ex instanceof NoSuchFileException && helmFetchFileConfig.isSucceedIfFileNotFound()) {
            continue;
          }
          throw ex;
        }
      }
    }
    return helmValueFetchFilesResultMap;
  }

  private HelmFetchFileResult readValuesYamlFromChartFiles(
      String chartDirectory, HelmFetchFileConfig helmFetchFileConfig, LogCallback logCallback) throws Exception {
    List<String> valueFileContentList = new ArrayList<>();
    try {
      for (String path : helmFetchFileConfig.getFilePaths()) {
        String valueFileContent =
            new String(Files.readAllBytes(Paths.get(chartDirectory, path)), StandardCharsets.UTF_8);
        valueFileContentList.add(valueFileContent);
        logCallback.saveExecutionLog(format("- %s", path));
      }
    } catch (Exception ex) {
      String exceptionMsg = ex.getMessage();

      // Values.yaml in service spec is optional.
      if (ex.getCause() instanceof NoSuchFileException && helmFetchFileConfig.isSucceedIfFileNotFound()) {
        log.info("file not found. " + exceptionMsg, ex);
        logCallback.saveExecutionLog(
            color(format("No values.yaml found for manifest with identifier: %s.", helmFetchFileConfig.getIdentifier()),
                White));
      } else {
        String msg = "Exception in processing HelmValuesFetchTask. " + exceptionMsg;
        log.error(msg, ex);
        logCallback.saveExecutionLog(msg, WARN);
        throw ex;
      }
    }
    return HelmFetchFileResult.builder().valuesFileContents(valueFileContentList).build();
  }

  private void downloadHelmChartFiles(HelmChartManifestDelegateConfig helmChartManifestDelegateConfig,
      String destinationDirectory, long timeoutInMillis) throws Exception {
    StoreDelegateConfig helmStoreDelegateConfig = helmChartManifestDelegateConfig.getStoreDelegateConfig();
    initHelm(destinationDirectory, helmChartManifestDelegateConfig.getHelmVersion(), timeoutInMillis);

    if (HTTP_HELM == helmStoreDelegateConfig.getType()) {
      downloadChartFilesFromHttpRepo(helmChartManifestDelegateConfig, destinationDirectory, timeoutInMillis);
    } else if (OCI_HELM == helmStoreDelegateConfig.getType()) {
      downloadChartFilesFromOciRepo(helmChartManifestDelegateConfig, destinationDirectory, timeoutInMillis);
    } else {
      downloadChartFilesUsingChartMuseum(helmChartManifestDelegateConfig, destinationDirectory, timeoutInMillis);
    }
  }

  public String getHttpHelmUsername(final HttpHelmConnectorDTO httpHelmConnectorDTO) {
    if (httpHelmConnectorDTO.getAuth().getAuthType() == HttpHelmAuthType.ANONYMOUS) {
      return null;
    }

    HttpHelmUsernamePasswordDTO creds = (HttpHelmUsernamePasswordDTO) httpHelmConnectorDTO.getAuth().getCredentials();
    return FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
        creds.getUsername(), creds.getUsernameRef());
  }

  public char[] getHttpHelmPassword(final HttpHelmConnectorDTO httpHelmConnectorDTO) {
    if (httpHelmConnectorDTO.getAuth().getAuthType() == HttpHelmAuthType.ANONYMOUS) {
      return null;
    }

    HttpHelmUsernamePasswordDTO creds = (HttpHelmUsernamePasswordDTO) httpHelmConnectorDTO.getAuth().getCredentials();
    return creds.getPasswordRef().getDecryptedValue();
  }

  public String getOciHelmUsername(final OciHelmConnectorDTO ociHelmConnectorDTO) {
    if (ociHelmConnectorDTO.getAuth().getAuthType() == OciHelmAuthType.ANONYMOUS) {
      return null;
    }

    OciHelmUsernamePasswordDTO creds = (OciHelmUsernamePasswordDTO) ociHelmConnectorDTO.getAuth().getCredentials();
    return FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
        creds.getUsername(), creds.getUsernameRef());
  }

  public char[] getOciHelmPassword(final OciHelmConnectorDTO ociHelmConnectorDTO) {
    if (ociHelmConnectorDTO.getAuth().getAuthType() == OciHelmAuthType.ANONYMOUS) {
      return null;
    }

    OciHelmUsernamePasswordDTO creds = (OciHelmUsernamePasswordDTO) ociHelmConnectorDTO.getAuth().getCredentials();
    return creds.getPasswordRef().getDecryptedValue();
  }

  /**
   * Method to extract Helm Chart info like Chart version and Chart name from the downloaded Chart files.
   * @param chartYamlPath - Path of the Chart.yaml file
   * @return HelmChartInfo - This contains details about the Helm chart
   * @throws IOException
   */
  public HelmChartInfo getHelmChartInfoFromChartsYamlFile(String chartYamlPath) throws IOException {
    String chartVersion = null;
    String chartName = null;
    boolean versionFound = false;
    boolean nameFound = false;

    try (BufferedReader br =
             new BufferedReader(new InputStreamReader(new FileInputStream(chartYamlPath), StandardCharsets.UTF_8))) {
      String line;

      while ((line = br.readLine()) != null) {
        if (!versionFound && line.startsWith(VERSION_KEY)) {
          chartVersion = line.substring(VERSION_KEY.length() + 1);
          versionFound = true;
        }

        if (!nameFound && line.startsWith(NAME_KEY)) {
          chartName = line.substring(NAME_KEY.length() + 1);
          nameFound = true;
        }

        if (versionFound && nameFound) {
          break;
        }
      }
    }

    return HelmChartInfo.builder().version(chartVersion).name(chartName).build();
  }

  public List<String> fetchChartVersions(HelmChartManifestDelegateConfig helmChartManifestDelegateConfig,
      long timeoutInMillis, String destinationDirectory) throws Exception {
    StoreDelegateConfig helmStoreDelegateConfig = helmChartManifestDelegateConfig.getStoreDelegateConfig();
    String workingDirectory = createDirectoryIfNotExist(destinationDirectory);
    initHelm(workingDirectory, helmChartManifestDelegateConfig.getHelmVersion(), timeoutInMillis);

    if (HTTP_HELM == helmStoreDelegateConfig.getType()) {
      return fetchVersionsFromHttp(helmChartManifestDelegateConfig, workingDirectory, timeoutInMillis);
    } else {
      return fetchVersionsUsingChartMuseumServer(helmChartManifestDelegateConfig, workingDirectory, timeoutInMillis);
    }
  }

  public String createDirectoryIfNotExist(String directoryBase) throws IOException {
    String workingDirectory = Paths.get(directoryBase).normalize().toAbsolutePath().toString();
    createAndWaitForDir(workingDirectory);
    return workingDirectory;
  }

  public String getCacheDirForManifestCollection(HelmVersion helmVersion, String repoName, boolean useCache)
      throws IOException {
    if (!HelmVersion.isHelmV3(helmVersion)) {
      return EMPTY;
    }
    if (useCache) {
      return Paths.get(RESOURCE_DIR_BASE, repoName, "cache").toAbsolutePath().normalize().toString();
    }
    return Files.createTempDirectory("charts").toAbsolutePath().toString();
  }

  public void deleteQuietlyWithErrorLog(String tempDir) {
    try {
      if (isNotEmpty(tempDir)) {
        /*
          adding this check as deleting an empty directory causes delegate to behave erratically
          i.e. it deletes root folder and shuts down
         */
        log.info("Deleting directory at path(deleteQuietlyWithErrorLog) " + tempDir);
        FileUtils.forceDelete(new File(tempDir));
      }
    } catch (IOException ie) {
      log.error(
          "Deletion of charts folder failed due to : {}", ExceptionMessageSanitizer.sanitizeException(ie).getMessage());
    }
  }

  private List<String> fetchVersionsFromHttp(
      HelmChartManifestDelegateConfig manifest, String destinationDirectory, long timeoutInMillis) throws IOException {
    if (!(manifest.getStoreDelegateConfig() instanceof HttpHelmStoreDelegateConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("storeDelegateConfig", "Must be instance of HttpHelmStoreDelegateConfig"));
    }

    HttpHelmStoreDelegateConfig storeDelegateConfig = (HttpHelmStoreDelegateConfig) manifest.getStoreDelegateConfig();
    HttpHelmConnectorDTO httpHelmConnector = storeDelegateConfig.getHttpHelmConnector();
    Map<String, String> environment = new HashMap<>();
    String commandOutput = "";
    String cacheDir = getCacheDirForManifestCollection(
        manifest.getHelmVersion(), storeDelegateConfig.getRepoName(), manifest.isUseCache());

    String username = getHttpHelmUsername(httpHelmConnector);
    char[] password = getHttpHelmPassword(httpHelmConnector);
    try {
      removeRepo(storeDelegateConfig.getRepoName(), destinationDirectory, manifest.getHelmVersion(), timeoutInMillis,
          cacheDir);
      addRepo(storeDelegateConfig.getRepoName(), storeDelegateConfig.getRepoDisplayName(),
          httpHelmConnector.getHelmRepoUrl(), username, password, destinationDirectory, manifest.getHelmVersion(),
          timeoutInMillis, cacheDir, manifest.getHelmCommandFlag());

      String command = fetchHelmChartVersionsCommand(
          manifest.getHelmVersion(), manifest.getChartName(), storeDelegateConfig.getRepoName(), destinationDirectory);

      if (!HelmVersion.V2.equals(manifest.getHelmVersion())) {
        // repo flags are supported only from helm v3
        environment.putIfAbsent(HELM_CACHE_HOME,
            HELM_CACHE_HOME_PATH.replace(REPO_NAME, storeDelegateConfig.getRepoName())
                .replace(HELM_CACHE_HOME_PLACEHOLDER, cacheDir));
        command = fetchHelmChartVersionsCommandWithRepoFlags(manifest.getHelmVersion(), manifest.getChartName(),
            storeDelegateConfig.getRepoName(), destinationDirectory, cacheDir);
      }

      updateRepo(storeDelegateConfig.getRepoName(), destinationDirectory, manifest.getHelmVersion(), timeoutInMillis,
          cacheDir, manifest.getHelmCommandFlag());

      ProcessResult processResult = executeCommand(environment, command, destinationDirectory,
          "Helm chart fetch versions command failed ", timeoutInMillis, HelmCliCommandType.FETCH_ALL_VERSIONS);

      if (processResult != null && processResult.getOutput() != null) {
        commandOutput = processResult.getOutput().getString();
      }
    } finally {
      deleteDirectoryAndItsContentIfExists(destinationDirectory + "/helm");
      if (!manifest.isUseCache() && isNotEmpty(cacheDir)) {
        deleteQuietlyWithErrorLog(cacheDir);
      }
    }

    return parseHelmVersionsFromOutput(commandOutput, manifest);
  }

  public void updateRepo(String repoName, String workingDirectory, HelmVersion helmVersion, long timeoutInMillis,
      String cacheDir, HelmCommandFlag helmCommandFlag) {
    try {
      String repoUpdateCommand = getRepoUpdateCommand(repoName, workingDirectory, helmVersion, helmCommandFlag);
      Map<String, String> environment = new HashMap<>();

      if (!isEmpty(cacheDir)) {
        environment.put(HELM_CACHE_HOME,
            HELM_CACHE_HOME_PATH.replace(REPO_NAME, repoName).replace(HELM_CACHE_HOME_PLACEHOLDER, cacheDir));
        repoUpdateCommand = addRepoFlags(repoUpdateCommand, repoName, cacheDir);
      }

      ProcessResult processResult = executeCommand(environment, repoUpdateCommand, workingDirectory,
          format("update helm repo %s", repoName), timeoutInMillis, HelmCliCommandType.REPO_UPDATE);

      log.info("Repo update command executed on delegate: {}", repoUpdateCommand);
      if (processResult.getExitValue() != 0) {
        log.warn("Failed to update helm repo {}. {}", repoName, processResult.getOutput().getUTF8());
      }
    } catch (Exception ex) {
      log.warn(ExceptionUtils.getMessage(ex));
    }
  }

  private String getRepoUpdateCommand(
      String repoName, String workingDirectory, HelmVersion helmVersion, HelmCommandFlag helmCommandFlag) {
    String repoUpdateCommand =
        HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_UPDATE, helmVersion)
            .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion))
            .replace("KUBECONFIG=${KUBECONFIG_PATH}", "")
            .replace(REPO_NAME, repoName);

    Map<HelmSubCommandType, String> commandFlagValueMap =
        helmCommandFlag != null ? helmCommandFlag.getValueMap() : null;
    repoUpdateCommand = HelmCommandFlagsUtils.applyHelmCommandFlags(
        repoUpdateCommand, HelmCliCommandType.REPO_UPDATE.name(), commandFlagValueMap, helmVersion);

    return applyHelmHomePath(repoUpdateCommand, workingDirectory);
  }

  public String fetchHelmChartVersionsCommand(
      HelmVersion helmVersion, String chartName, String repoName, String workingDirectory) {
    String helmFetchCommand =
        HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.FETCH_ALL_VERSIONS, helmVersion)
            .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion))
            .replace("${CHART_NAME}", chartName);

    if (isNotBlank(repoName)) {
      helmFetchCommand = helmFetchCommand.replace(REPO_NAME, repoName);
    } else {
      helmFetchCommand = helmFetchCommand.replace(REPO_NAME + "/", "");
    }
    return applyHelmHomePath(helmFetchCommand, workingDirectory);
  }

  public String fetchHelmChartVersionsCommandWithRepoFlags(
      HelmVersion helmVersion, String chartName, String repoName, String workingDirectory, String tempDir) {
    return addRepoFlags(
        fetchHelmChartVersionsCommand(helmVersion, chartName, repoName, workingDirectory), repoName, tempDir);
  }

  private List<String> parseHelmVersionsFromOutput(String commandOutput, HelmChartManifestDelegateConfig manifestConfig)
      throws IOException {
    String errorMessage = "No chart with the given name found. Chart might be deleted at source";
    if (isEmpty(commandOutput) || commandOutput.contains("No results found")) {
      throw new InvalidRequestException(errorMessage);
    }

    CSVFormat csvFormat = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter('\t').withTrim();
    List<CSVRecord> records = CSVParser.parse(commandOutput, csvFormat).getRecords();
    if (isEmpty(records)) {
      throw new InvalidRequestException(errorMessage);
    }

    List<String> chartVersions =
        records.stream()
            .filter(record -> record.size() > 1 && matchesChartName(manifestConfig.getChartName(), record.get(0)))
            .map(record -> record.get(1))
            .collect(Collectors.toList());

    if (isEmpty(chartVersions)) {
      throw new InvalidRequestException(errorMessage);
    }

    return chartVersions;
  }

  private boolean matchesChartName(String chartName, String recordName) {
    return Arrays.asList(recordName.split("/")).contains(chartName);
  }

  public List<String> fetchVersionsUsingChartMuseumServer(
      HelmChartManifestDelegateConfig manifest, String destinationDirectory, long timeoutInMillis) throws Exception {
    String resourceDirectory = null;
    ChartmuseumClient chartmuseumClient = null;
    ChartMuseumServer chartMuseumServer = null;
    String repoName = null;
    String repoDisplayName = null;
    StoreDelegateConfig storeDelegateConfig = manifest.getStoreDelegateConfig();
    if (S3_HELM == storeDelegateConfig.getType()) {
      S3HelmStoreDelegateConfig s3StoreDelegateConfig = (S3HelmStoreDelegateConfig) storeDelegateConfig;
      repoName = s3StoreDelegateConfig.getRepoName();
      repoDisplayName = s3StoreDelegateConfig.getRepoDisplayName();
    } else if (GCS_HELM == storeDelegateConfig.getType()) {
      GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig = (GcsHelmStoreDelegateConfig) storeDelegateConfig;
      repoName = gcsHelmStoreDelegateConfig.getRepoName();
      repoDisplayName = gcsHelmStoreDelegateConfig.getRepoDisplayName();
    }

    try {
      if (manifest != null) {
        decryptEncryptedDetails(manifest);
      }
      resourceDirectory = createNewDirectoryAtPath(RESOURCE_DIR_BASE);
      chartmuseumClient = ngChartmuseumClientFactory.createClient(manifest.getStoreDelegateConfig(), resourceDirectory);
      chartMuseumServer = chartmuseumClient.start();

      addChartMuseumRepo(repoName, repoDisplayName, chartMuseumServer.getPort(), destinationDirectory,
          manifest.getHelmVersion(), timeoutInMillis, "", manifest.getHelmCommandFlag());
      ProcessResult processResult = executeCommand(Collections.emptyMap(),
          fetchHelmChartVersionsCommand(
              manifest.getHelmVersion(), manifest.getChartName(), repoName, destinationDirectory),
          destinationDirectory, "Helm chart fetch versions command failed ", timeoutInMillis,
          HelmCliCommandType.FETCH_ALL_VERSIONS);
      String commandOutput = processResult.getOutput().getUTF8();
      return parseHelmVersionsFromOutput(commandOutput, manifest);
    } finally {
      if (chartmuseumClient != null && chartMuseumServer != null) {
        chartmuseumClient.stop(chartMuseumServer);
      }
    }
  }

  public void cleanupAfterCollection(
      HelmChartManifestDelegateConfig config, String destinationDirectory, long timeoutInMillis) {
    String workingDirectory = Paths.get(destinationDirectory).toString();
    StoreDelegateConfig storeDelegateConfig = config.getStoreDelegateConfig();
    String repoName;
    switch (storeDelegateConfig.getType()) {
      case HTTP_HELM:
        HttpHelmStoreDelegateConfig helmStoreConfig = (HttpHelmStoreDelegateConfig) storeDelegateConfig;
        repoName = helmStoreConfig.getRepoName();
        break;
      case OCI_HELM:
        OciHelmStoreDelegateConfig ociHelmStoreConfig = (OciHelmStoreDelegateConfig) storeDelegateConfig;
        repoName = ociHelmStoreConfig.getRepoName();
        break;
      case S3_HELM:
        S3HelmStoreDelegateConfig s3StoreConfig = (S3HelmStoreDelegateConfig) storeDelegateConfig;
        repoName = s3StoreConfig.getRepoName();
        break;
      case GCS_HELM:
        GcsHelmStoreDelegateConfig gcsStoreConfig = (GcsHelmStoreDelegateConfig) storeDelegateConfig;
        repoName = gcsStoreConfig.getRepoName();
        break;
      default:
        throw new ManifestCollectionException("Manifest collection not supported for other helm repos");
    }
    removeRepo(repoName, workingDirectory, config.getHelmVersion(), timeoutInMillis);
    cleanup(workingDirectory);
  }

  public void decryptEncryptedDetails(HelmChartManifestDelegateConfig helmChartManifestDelegateConfig) {
    StoreDelegateConfig helmStoreDelegateConfig = helmChartManifestDelegateConfig.getStoreDelegateConfig();
    switch (helmStoreDelegateConfig.getType()) {
      case S3_HELM:
        S3HelmStoreDelegateConfig s3HelmStoreConfig = (S3HelmStoreDelegateConfig) helmStoreDelegateConfig;
        List<DecryptableEntity> s3DecryptableEntityList = s3HelmStoreConfig.getAwsConnector().getDecryptableEntities();
        if (isNotEmpty(s3DecryptableEntityList)) {
          for (DecryptableEntity entity : s3HelmStoreConfig.getAwsConnector().getDecryptableEntities()) {
            decryptionService.decrypt(entity, s3HelmStoreConfig.getEncryptedDataDetails());
            ExceptionMessageSanitizer.storeAllSecretsForSanitizing(entity, s3HelmStoreConfig.getEncryptedDataDetails());
          }
        }
        break;
      case GCS_HELM:
        GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig = (GcsHelmStoreDelegateConfig) helmStoreDelegateConfig;
        List<DecryptableEntity> gcsDecryptableEntityList =
            gcsHelmStoreDelegateConfig.getGcpConnector().getDecryptableEntities();
        if (isNotEmpty(gcsDecryptableEntityList)) {
          for (DecryptableEntity entity : gcsDecryptableEntityList) {
            decryptionService.decrypt(entity, gcsHelmStoreDelegateConfig.getEncryptedDataDetails());
            ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
                entity, gcsHelmStoreDelegateConfig.getEncryptedDataDetails());
          }
        }
        break;
      case HTTP_HELM:
        HttpHelmStoreDelegateConfig httpHelmStoreConfig = (HttpHelmStoreDelegateConfig) helmStoreDelegateConfig;
        for (DecryptableEntity entity : httpHelmStoreConfig.getHttpHelmConnector().getDecryptableEntities()) {
          decryptionService.decrypt(entity, httpHelmStoreConfig.getEncryptedDataDetails());
          ExceptionMessageSanitizer.storeAllSecretsForSanitizing(entity, httpHelmStoreConfig.getEncryptedDataDetails());
        }
        break;
      case OCI_HELM:
        OciHelmStoreDelegateConfig ociHelmStoreConfig = (OciHelmStoreDelegateConfig) helmStoreDelegateConfig;
        for (DecryptableEntity entity : ociHelmStoreConfig.getOciHelmConnector().getDecryptableEntities()) {
          decryptionService.decrypt(entity, ociHelmStoreConfig.getEncryptedDataDetails());
          ExceptionMessageSanitizer.storeAllSecretsForSanitizing(entity, ociHelmStoreConfig.getEncryptedDataDetails());
        }
        break;
      default:
        throw new InvalidRequestException(
            format("Store type: %s not supported for helm values fetch task NG", helmStoreDelegateConfig.getType()));
    }
  }

  public void revokeReadPermission(String filePath) {
    String cmd = CHMOD + filePath;

    ProcessExecutor processExecutor = new ProcessExecutor().command("/bin/sh", "-c", cmd);
    try {
      processExecutor.execute();
    } catch (Exception e) {
      log.error("Unable to revoke the readable permissions for KubeConfig file ", e);
    }
  }

  public int skipDefaultHelmValuesYaml(
      String chartDir, List<String> valuesYamlList, boolean skipDefaultValuesYaml, HelmVersion helmVersion) {
    if (HelmVersion.V2.equals(helmVersion) || !skipDefaultValuesYaml || isEmpty(valuesYamlList)) {
      return -1;
    }
    try {
      String defaultValuesYaml = new String(Files.readAllBytes(Paths.get(chartDir, "values.yaml")));
      if (isEmpty(defaultValuesYaml)) {
        return -1;
      }
      String valuesYaml;
      for (int i = 0; i < valuesYamlList.size(); i++) {
        valuesYaml = valuesYamlList.get(i);
        if (isNotEmpty(valuesYaml) && valuesYaml.equals(defaultValuesYaml)) {
          return i;
        }
      }
    } catch (FileNotFoundException e) {
      log.error("Unable to find default values.yaml " + e.getMessage());
      return -1;
    } catch (IOException e) {
      log.error("Unable to read default values.yaml " + e.getMessage());
      return -1;
    }
    return -1;
  }
  public int checkForDependencyUpdateFlag(Map<HelmSubCommandType, String> helmCmdFlags, String response) {
    /*
      if we pass --dependency-update flag with helm template cmd, this causes extra lines to be present in o/p
      hence we trim this and take only the rendered manifests, which start after "---"
     */
    if (helmCmdFlags != null) {
      String templateFlag = helmCmdFlags.get(HelmSubCommandType.TEMPLATE);
      if (isNotEmpty(templateFlag) && templateFlag.contains("--dependency-update")) {
        return response.indexOf("---");
      }
    }
    return -1;
  }

  @VisibleForTesting
  URI getParsedURI(String ociUrl) throws URISyntaxException {
    /*
    If the ociUrl string does not start with '://'
    then the URI() method fails to correctly read the appended port number
     */
    if (!ociUrl.contains("://")) {
      ociUrl = OCI_PREFIX + ociUrl;
    }
    URI uri = new URI(ociUrl);
    if (uri.getPort() < 0) {
      uri = URI.create(uri + ":" + DEFAULT_PORT);
    }
    return uri;
  }

  @VisibleForTesting
  String getParsedUrlForUserNamePwd(String ociUrl) {
    /*
    Ensure that OCI url does not start with oci://
    Reason: helm registry login command fails if the registry url contains prefix: 'oci://'
     */
    if (ociUrl.startsWith(OCI_PREFIX)) {
      ociUrl = ociUrl.substring(OCI_PREFIX.length());
    }
    return ociUrl;
  }

  private String addBasePathToOciUrl(String ociUrl, String basePath) {
    if (isNotEmpty(basePath) && basePath.charAt(0) == '/') {
      return ociUrl + basePath;
    } else if (isNotEmpty(basePath) && basePath.charAt(0) != '/') {
      return ociUrl + "/" + basePath;
    } else {
      throw new InvalidArgumentsException("Invalid oci base path cannot be empty");
    }
  }

  public String getChartName(HelmChartManifestDelegateConfig manifestDelegateConfig) {
    if (isNotEmpty(manifestDelegateConfig.getChartName())) {
      return manifestDelegateConfig.getChartName();
    }

    if (manifestDelegateConfig.getStoreDelegateConfig() instanceof GitStoreDelegateConfig) {
      String folderPath = ((GitStoreDelegateConfig) manifestDelegateConfig.getStoreDelegateConfig()).getPaths().get(0);
      String modifiedPath = (folderPath.lastIndexOf('/') == folderPath.length() - 1)
          ? folderPath.substring(0, folderPath.length() - 1)
          : folderPath;
      return modifiedPath.substring(modifiedPath.lastIndexOf('/') + 1);
    }

    log.warn("Chart name not found");
    return "";
  }
}
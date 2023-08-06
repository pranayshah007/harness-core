/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helm;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.helm.HelmConstants.HELM_COMMAND_FLAG_PLACEHOLDER;
import static io.harness.helm.HelmConstants.V3Commands.HELM_REPO_ADD_FORCE_UPDATE;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;

import static com.google.common.base.Charsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HelmClientException;
import io.harness.exception.HelmClientRetryableException;
import io.harness.exception.HelmClientRuntimeException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.vavr.CheckedFunction0;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

/**
 * Created by anubhaw on 3/22/18.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_FIRST_GEN, HarnessModuleComponent.CDS_K8S})
@Singleton
@Slf4j
@TargetModule(HarnessModule._960_API_SERVICES)
@OwnedBy(CDP)
public class HelmClientImpl implements HelmClient {
  @Inject private K8sGlobalConfigService k8sGlobalConfigService;

  private static final int CLI_RETRY_ATTEMPTS = 3;
  private static final String CLI_RETRY_NAME = "helm-cli";
  private static final String OVERRIDE_FILE_PATH = "./repository/helm/overrides/${CONTENT_HASH}.yaml";
  public static final String DEBUG_COMMAND_FLAG = "--debug";
  public static final LoadingCache<String, Object> lockObjects =
      CacheBuilder.newBuilder().expireAfterAccess(30, TimeUnit.MINUTES).build(CacheLoader.from(Object::new));

  public final Retry retry = Retry.of(CLI_RETRY_NAME,
      RetryConfig.custom().retryExceptions(HelmClientRetryableException.class).maxAttempts(CLI_RETRY_ATTEMPTS).build());

  @Override
  public HelmCliResponse install(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled) throws Exception {
    String keyValueOverrides = constructValueOverrideFile(helmCommandData.getYamlFiles());
    String chartReference = getChartReference(helmCommandData.isRepoConfigNull(), helmCommandData.getChartName(),
        helmCommandData.getChartVersion(), helmCommandData.getChartUrl(), helmCommandData.getWorkingDir());
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    HelmCliCommandType commandType = HelmCliCommandType.INSTALL;

    String installCommand = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                                .replace("${OVERRIDE_VALUES}", keyValueOverrides)
                                .replace("${RELEASE_NAME}", helmCommandData.getReleaseName())
                                .replace("${NAMESPACE}", getNamespaceFlag(helmCommandData.getNamespace()))
                                .replace("${CHART_REFERENCE}", chartReference);

    installCommand = applyCommandFlags(installCommand, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(installCommand, helmCommandData.getLogCallback());
    installCommand = applyKubeConfigToCommand(installCommand, kubeConfigLocation);
    String errorMessagePrefix = "Failed to install helm chart. Executed command: ";

    return executeHelmCLICommand(
        helmCommandData, isErrorFrameworkEnabled, installCommand, commandType, errorMessagePrefix);
  }

  @Override
  public HelmCliResponse upgrade(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled) throws Exception {
    String keyValueOverrides = constructValueOverrideFile(helmCommandData.getYamlFiles());
    String chartReference = getChartReference(helmCommandData.isRepoConfigNull(), helmCommandData.getChartName(),
        helmCommandData.getChartVersion(), helmCommandData.getChartUrl(), helmCommandData.getWorkingDir());
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();

    HelmCliCommandType commandType = HelmCliCommandType.UPGRADE;
    String upgradeCommand = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                                .replace("${RELEASE_NAME}", helmCommandData.getReleaseName())
                                .replace("${CHART_REFERENCE}", chartReference)
                                .replace("${OVERRIDE_VALUES}", keyValueOverrides);

    upgradeCommand = applyCommandFlags(upgradeCommand, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(upgradeCommand, helmCommandData.getLogCallback());
    upgradeCommand = applyKubeConfigToCommand(upgradeCommand, kubeConfigLocation);
    String errorMessagePrefix = "Failed to upgrade helm chart. Executed command: ";

    return executeHelmCLICommand(
        helmCommandData, isErrorFrameworkEnabled, upgradeCommand, commandType, errorMessagePrefix);
  }

  @Override
  public HelmCliResponse rollback(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled) throws Exception {
    String kubeConfigLocation = Optional.ofNullable(helmCommandData.getKubeConfigLocation()).orElse("");
    HelmCliCommandType commandType = HelmCliCommandType.ROLLBACK;

    String command = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                         .replace("${RELEASE}", helmCommandData.getReleaseName())
                         .replace("${REVISION}", helmCommandData.getPrevReleaseVersion().toString());

    command = applyCommandFlags(command, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(command, helmCommandData.getLogCallback());
    command = applyKubeConfigToCommand(command, kubeConfigLocation);
    String errorMessagePrefix = "Failed to rollback helm chart. Executed command: ";

    return executeHelmCLICommand(helmCommandData, isErrorFrameworkEnabled, command, commandType, errorMessagePrefix);
  }

  @Override
  public HelmCliResponse releaseHistory(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled)
      throws Exception {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    String releaseName = helmCommandData.getReleaseName();
    HelmCliCommandType commandType = HelmCliCommandType.RELEASE_HISTORY;

    String releaseHistory = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                                .replace("${FLAGS}", "--max 5")
                                .replace("${RELEASE_NAME}", releaseName);

    releaseHistory = applyCommandFlags(releaseHistory, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());

    if (HelmVersion.V2.equals(helmCommandData.getHelmVersion())) {
      releaseHistory = StringUtils.replaceIgnoreCase(releaseHistory, DEBUG_COMMAND_FLAG, EMPTY);
    }

    logHelmCommandInExecutionLogs(releaseHistory, helmCommandData.getLogCallback());
    releaseHistory = applyKubeConfigToCommand(releaseHistory, kubeConfigLocation);
    String errorMessagePrefix = "Failed in release history step. Executed command: ";

    return executeHelmCLICommand(
        helmCommandData, isErrorFrameworkEnabled, releaseHistory, commandType, errorMessagePrefix);
  }

  @Override
  public HelmCliResponse listReleases(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled)
      throws Exception {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    HelmCliCommandType commandType = HelmCliCommandType.LIST_RELEASE;

    String listRelease = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                             .replace("${RELEASE_NAME}", helmCommandData.getReleaseName());

    listRelease = applyCommandFlags(listRelease, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());

    if (HelmVersion.V2.equals(helmCommandData.getHelmVersion())) {
      listRelease = StringUtils.replaceIgnoreCase(listRelease, DEBUG_COMMAND_FLAG, EMPTY);
    }

    logHelmCommandInExecutionLogs(listRelease, helmCommandData.getLogCallback());
    listRelease = applyKubeConfigToCommand(listRelease, kubeConfigLocation);
    String errorMessagePrefix = "Failed in list releases step. Executed command: ";

    return executeHelmCLICommand(
        helmCommandData, isErrorFrameworkEnabled, listRelease, commandType, errorMessagePrefix);
  }

  @Override
  public HelmCliResponse getClientAndServerVersion(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled)
      throws Exception {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    HelmCliCommandType commandType = HelmCliCommandType.VERSION;
    String command = getHelmCommandTemplateWithHelmPath(commandType, HelmVersion.V2);

    command = applyCommandFlags(command, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(command, helmCommandData.getLogCallback());
    command = applyKubeConfigToCommand(command, kubeConfigLocation);
    String errorMessagePrefix = "Failed in helm version command. ";

    return executeHelmCLICommand(helmCommandData, isErrorFrameworkEnabled, command, commandType, errorMessagePrefix);
  }

  @Override
  public HelmCliResponse addPublicRepo(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled)
      throws Exception {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    HelmCliCommandType commandType = HelmCliCommandType.REPO_ADD;

    String command = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                         .replace("${REPO_URL}", helmCommandData.getChartUrl())
                         .replace("${REPO_NAME}", helmCommandData.getRepoName());

    command = applyCommandFlags(command, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(command, helmCommandData.getLogCallback());
    command = applyKubeConfigToCommand(command, kubeConfigLocation);

    HelmCliResponse response;
    String errorMessagePrefix = "Failed to add helm repo. ";

    if (!isErrorFrameworkEnabled) {
      response = executeHelmCLICommand(command);
    }

    else {
      response =
          fetchCliResponseWithExceptionHandling(command, commandType, errorMessagePrefix, null, Collections.emptyMap());
    }

    if (HelmVersion.isHelmV3(helmCommandData.getHelmVersion())) {
      // Starting from helm 3.3.4, when --force-update not enabled and there is an update in repo configuration
      // (for example, password is updated) helm repo add will fail with: repository name (repo-name) already exists,
      // please specify a different name. For this case will try again with --force-update
      if (response.getCommandExecutionStatus() == FAILURE && isNotEmpty(response.getOutputWithErrorStream())
          && response.getOutputWithErrorStream().contains("already exists")) {
        String forceAddRepoCommand = command + HELM_REPO_ADD_FORCE_UPDATE;
        log.info(
            "Detected repository configuration change after executing. Trying again with --force-update command flag");
        if (isErrorFrameworkEnabled) {
          return fetchCliResponseWithExceptionHandling(
              forceAddRepoCommand, commandType, errorMessagePrefix, null, Collections.emptyMap());
        }
        return executeHelmCLICommand(forceAddRepoCommand);
      }
    }

    return response;
  }

  @Override
  public HelmCliResponse repoUpdate(HelmCommandData helmCommandData)
      throws InterruptedException, TimeoutException, IOException {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    HelmCliCommandType commandType = HelmCliCommandType.REPO_UPDATE;
    String command = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion());

    command = applyCommandFlags(command, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(command, helmCommandData.getLogCallback());
    command = applyKubeConfigToCommand(command, kubeConfigLocation);

    return executeHelmCLICommand(command);
  }

  @Override
  public HelmCliResponse getHelmRepoList(HelmCommandData helmCommandData)
      throws InterruptedException, TimeoutException, IOException {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    HelmCliCommandType commandType = HelmCliCommandType.REPO_LIST;
    String command = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion());

    command = applyCommandFlags(command, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(command, helmCommandData.getLogCallback());
    command = applyKubeConfigToCommand(command, kubeConfigLocation);

    return executeHelmCLICommand(command);
  }

  @Override
  public HelmCliResponse deleteHelmRelease(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled)
      throws Exception {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    /*
    In Helm3, release ledger is purged by default unlike helm2
     */
    HelmCliCommandType commandType = HelmCliCommandType.DELETE_RELEASE;
    String command = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                         .replace("${RELEASE_NAME}", helmCommandData.getReleaseName())
                         .replace("${FLAGS}", "--purge");

    command = applyCommandFlags(command, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(command, helmCommandData.getLogCallback());
    command = applyKubeConfigToCommand(command, kubeConfigLocation);
    String errorMessagePrefix = "Failed to delete helm release. ";

    return executeHelmCLICommand(helmCommandData, isErrorFrameworkEnabled, command, commandType, errorMessagePrefix);
  }

  @Override
  public HelmCliResponse renderChart(HelmCommandData helmCommandData, String chartLocation, String namespace,
      List<String> valuesOverrides, boolean isErrorFrameworkEnabled) throws Exception {
    String kubeConfigLocation = Optional.ofNullable(helmCommandData.getKubeConfigLocation()).orElse("");
    String keyValueOverrides = constructValueOverrideFile(valuesOverrides);

    HelmCliCommandType commandType = HelmCliCommandType.RENDER_CHART;
    String command = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                         .replace("${CHART_LOCATION}", chartLocation)
                         .replace("${RELEASE_NAME}", helmCommandData.getReleaseName())
                         .replace("${NAMESPACE}", namespace)
                         .replace("${OVERRIDE_VALUES}", keyValueOverrides);

    command = applyCommandFlags(command, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    command = applyKubeConfigToCommand(command, kubeConfigLocation);
    logHelmCommandInExecutionLogs(command, helmCommandData.getLogCallback());
    String errorMessagePrefix = "Failed to render helm chart. ";

    return executeHelmCLICommand(helmCommandData, isErrorFrameworkEnabled, command, commandType, errorMessagePrefix);
  }

  @Override
  public HelmCliResponse getManifest(HelmCommandData helmCommandData, String namespace) throws Exception {
    if (isEmpty(helmCommandData.getKubeConfigLocation())) {
      throw new InvalidArgumentsException(Pair.of("kubeconfig", "Kubeconfig path is required"));
    }

    HelmCliCommandType commandType = HelmCliCommandType.GET_MANIFEST;
    String command = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                         .replace("${RELEASE_NAME}", helmCommandData.getReleaseName());
    if (isNotEmpty(namespace)) {
      command = command.replace("${NAMESPACE}", namespace);
    }

    command = applyKubeConfigToCommand(command, helmCommandData.getKubeConfigLocation());
    command = applyCommandFlags(command, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(command, helmCommandData.getLogCallback());
    return executeHelmCLICommandWithRetry(helmCommandData, command, commandType, "Failed to retrieve helm manifest");
  }

  /**
   * The type Helm cli response.
   */
  @Data
  @Builder
  public static class HelmCliResponse {
    private CommandExecutionStatus commandExecutionStatus;
    private String output;
    private String errorStreamOutput;

    public String getOutputWithErrorStream() {
      return (isEmpty(this.getErrorStreamOutput())) ? this.getOutput()
                                                    : this.getErrorStreamOutput() + " " + this.getOutput();
    }
  }

  @Override
  public HelmCliResponse searchChart(HelmCommandData helmCommandData, String chartInfo)
      throws InterruptedException, TimeoutException, IOException {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    HelmCliCommandType commandType = HelmCliCommandType.SEARCH_REPO;
    String searchChartCommand = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                                    .replace("${CHART_INFO}", chartInfo);

    searchChartCommand = applyCommandFlags(searchChartCommand, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(searchChartCommand, helmCommandData.getLogCallback());
    searchChartCommand = applyKubeConfigToCommand(searchChartCommand, kubeConfigLocation);

    return executeHelmCLICommand(searchChartCommand);
  }

  public HelmCliResponse executeHelmCLICommand(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled,
      String command, HelmCliCommandType commandType, String errorMessagePrefix)
      throws IOException, InterruptedException, TimeoutException {
    try (LogOutputStream errorStream = helmCommandData.getLogCallback() != null
            ? new ErrorActivityOutputStream(helmCommandData.getLogCallback())
            : new LogErrorStream()) {
      Map<String, String> env = new HashMap<>();
      if (!isEmpty(helmCommandData.getGcpKeyPath())) {
        env.put("GOOGLE_APPLICATION_CREDENTIALS", helmCommandData.getGcpKeyPath());
      }
      return isErrorFrameworkEnabled
          ? fetchCliResponseWithExceptionHandling(command, commandType, errorMessagePrefix, errorStream, env)
          : executeHelmCLICommand(command, errorStream, env);
    }
  }

  public HelmCliResponse executeHelmCLICommandWithRetry(
      HelmCommandData helmCommandData, String command, HelmCliCommandType commandType, String errorMessagePrefix) {
    try (LogOutputStream errorStream = helmCommandData.getLogCallback() != null
            ? new ErrorActivityOutputStream(helmCommandData.getLogCallback())
            : new LogErrorStream()) {
      Map<String, String> env = new HashMap<>();
      if (!isEmpty(helmCommandData.getGcpKeyPath())) {
        env.put("GOOGLE_APPLICATION_CREDENTIALS", helmCommandData.getGcpKeyPath());
      }

      CheckedFunction0<HelmCliResponse> responseSupplier = Retry.decorateCheckedSupplier(retry, () -> {
        try {
          return executeWithExceptionHandling(command, commandType, errorMessagePrefix, errorStream, env);
        } catch (InterruptedException e) {
          throw e;
        } catch (Exception e) {
          throw new HelmClientRetryableException(e);
        }
      });

      // PMD issue: Converting checked exception to unchecked exception. Not a good practice to do this.
      return responseSupplier.unchecked().apply();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @VisibleForTesting
  HelmCliResponse executeHelmCLICommand(String command, OutputStream errorStream, Map<String, String> env)
      throws IOException, InterruptedException, TimeoutException {
    return executeHelmCLICommandNoDefaultTimeout(command, errorStream, env);
  }

  @VisibleForTesting
  HelmCliResponse executeHelmCLICommand(String command) throws IOException, InterruptedException, TimeoutException {
    return executeHelmCLICommandNoDefaultTimeout(command, null, Collections.emptyMap());
  }

  @VisibleForTesting
  HelmCliResponse executeHelmCLICommandNoDefaultTimeout(String command, OutputStream errorStream,
      Map<String, String> env) throws IOException, InterruptedException, TimeoutException {
    ProcessExecutor processExecutor = new ProcessExecutor()
                                          .environment(env)
                                          .command("/bin/sh", "-c", command)
                                          .readOutput(true)
                                          .redirectOutput(new LogOutputStream() {
                                            @Override
                                            protected void processLine(String line) {
                                              log.info(line);
                                            }
                                          });

    if (errorStream != null) {
      processExecutor.redirectError(errorStream);
    }

    ProcessResult processResult = processExecutor.execute();
    CommandExecutionStatus status = processResult.getExitValue() == 0 ? SUCCESS : FAILURE;

    String streamOutput = "";
    if (errorStream != null) {
      if (errorStream instanceof LogErrorStream) {
        streamOutput = ((LogErrorStream) errorStream).getOutput();
      }

      if (errorStream instanceof ErrorActivityOutputStream) {
        streamOutput = ((ErrorActivityOutputStream) errorStream).getOutput();
      }
    }

    String output = processResult.outputUTF8();

    return HelmCliResponse.builder()
        .commandExecutionStatus(status)
        .output(output)
        .errorStreamOutput(streamOutput)
        .build();
  }

  private String getNamespaceFlag(String namespace) {
    return "--namespace " + namespace;
  }

  private String getChartReference(String chartName, String chartVersion, String chartUrl) {
    if (isNotEmpty(chartUrl)) {
      chartName = chartName + " --repo " + chartUrl;
    }

    if (isNotEmpty(chartVersion)) {
      chartName = chartName + " --version " + chartVersion;
    }

    return chartName;
  }

  private String constructValueOverrideFile(List<String> valuesYamlFiles) throws IOException, ExecutionException {
    StringBuilder fileOverrides = new StringBuilder();
    if (isNotEmpty(valuesYamlFiles)) {
      for (String yamlFileContent : valuesYamlFiles) {
        String md5Hash = DigestUtils.md5Hex(yamlFileContent);
        String overrideFilePath = OVERRIDE_FILE_PATH.replace("${CONTENT_HASH}", md5Hash);

        synchronized (lockObjects.get(md5Hash)) {
          File overrideFile = new File(overrideFilePath);
          if (!overrideFile.exists()) {
            FileUtils.forceMkdir(overrideFile.getParentFile());
            FileUtils.writeStringToFile(overrideFile, yamlFileContent, UTF_8);
          }
          fileOverrides.append(" -f ").append(overrideFilePath);
        }
      }
    }
    return fileOverrides.toString();
  }

  private void logHelmCommandInExecutionLogs(String helmCommand, LogCallback logCallback) {
    if (logCallback != null) {
      String msg = "Executing command - " + helmCommand + "\n";
      log.info(msg);
      logCallback.saveExecutionLog(msg, LogLevel.INFO);
    }
  }

  private String applyKubeConfigToCommand(String command, String kubeConfigLocation) {
    if (isNotEmpty(kubeConfigLocation)) {
      return command.replace("${KUBECONFIG_PATH}", kubeConfigLocation);
    } else {
      return command.replace("KUBECONFIG=${KUBECONFIG_PATH}", EMPTY).trim();
    }
  }

  private String applyCommandFlags(String command, HelmCliCommandType commandType, String commandFlags,
      boolean isHelmCmdFlagsNull, Map<HelmSubCommandType, String> valueMap, HelmVersion helmVersion) {
    String flags = isBlank(commandFlags) ? "" : commandFlags;
    if (!isHelmCmdFlagsNull && isNotEmpty(valueMap)) {
      return HelmCommandFlagsUtils.applyHelmCommandFlags(command, commandType.name(), valueMap, helmVersion);
    }
    return command.replace(HELM_COMMAND_FLAG_PLACEHOLDER, flags);
  }

  private String getChartReference(
      boolean isRepoConfigNull, String chartName, String chartVersion, String chartUrl, String workingDir) {
    String chartReference;
    if (isRepoConfigNull) {
      chartReference = getChartReference(chartName, chartVersion, chartUrl);
    } else {
      chartReference = Paths.get(workingDir).toString();
    }
    return chartReference;
  }

  private String getHelmCommandTemplateWithHelmPath(HelmCliCommandType commandType, HelmVersion helmVersion) {
    String helmPath = getHelmPath(helmVersion);
    return HelmCommandTemplateFactory.getHelmCommandTemplate(commandType, helmVersion)
        .replace(HelmConstants.HELM_PATH_PLACEHOLDER, helmPath);
  }

  @Override
  public String getHelmPath(HelmVersion helmVersion) {
    if (helmVersion == HelmVersion.V2 || helmVersion == null) {
      return "helm";
    }
    return k8sGlobalConfigService.getHelmPath(helmVersion);
  }

  public HelmCliResponse fetchCliResponseWithExceptionHandling(String command, HelmCliCommandType commandType,
      String errorMessagePrefix, OutputStream errorStream, Map<String, String> env) {
    try {
      return executeWithExceptionHandling(command, commandType, errorMessagePrefix, errorStream, env);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HelmClientRuntimeException(new HelmClientException(ExceptionUtils.getMessage(e), USER, commandType));
    } catch (Exception e) {
      throw new HelmClientRuntimeException(new HelmClientException(ExceptionUtils.getMessage(e), USER, commandType));
    }
  }

  public HelmCliResponse executeWithExceptionHandling(String command, HelmCliCommandType commandType,
      String errorMessagePrefix, OutputStream errorStream, Map<String, String> env)
      throws InterruptedException, IOException, TimeoutException {
    HelmCliResponse helmCliResponse = executeHelmCLICommandNoDefaultTimeout(command, errorStream, env);

    if (helmCliResponse.getCommandExecutionStatus() != SUCCESS) {
      // if helm hist fails due to 'release not found' -- then we don't fail/ throw exception
      // (because for first time deployment, release history cmd fails with release not found)
      String outputMessage = helmCliResponse.getOutputWithErrorStream();
      if (commandType == HelmCliCommandType.RELEASE_HISTORY
          && (outputMessage.contains("not found") && outputMessage.contains("release"))) {
        return helmCliResponse;
      }
      throw new HelmClientRuntimeException(ExceptionMessageSanitizer.sanitizeException(
          new HelmClientException(errorMessagePrefix + command + ". " + outputMessage, USER, commandType)));
    }
    return helmCliResponse;
  }

  @RequiredArgsConstructor
  static class ErrorActivityOutputStream extends LogOutputStream {
    @NonNull private final LogCallback logCallback;
    @Getter private String output;
    @Override
    protected void processLine(String s) {
      if (isEmpty(output)) {
        output = "\n" + s;
      } else {
        output = output + "\n" + s;
      }
      logCallback.saveExecutionLog(s, ERROR);
    }
  }

  static class LogErrorStream extends LogOutputStream {
    @Getter private String output;
    @Override
    protected void processLine(String s) {
      if (isEmpty(output)) {
        output = "\n" + s;
      } else {
        output = output + "\n" + s;
      }
      log.error(s);
    }
  }
}
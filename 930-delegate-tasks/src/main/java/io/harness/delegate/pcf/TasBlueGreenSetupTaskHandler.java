/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.pcf;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INVALID_INFRA_STATE;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.pcf.CfCommandUnitConstants.Wrapup;
import static io.harness.pcf.PcfUtils.encodeColor;
import static io.harness.pcf.model.PcfConstants.APPLICATION_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.CREATE_SERVICE_MANIFEST_ELEMENT;
import static io.harness.pcf.model.PcfConstants.DELIMITER;
import static io.harness.pcf.model.PcfConstants.DOCKER_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.ENV_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.HARNESS__INACTIVE__IDENTIFIER;
import static io.harness.pcf.model.PcfConstants.HARNESS__STATUS__IDENTIFIER;
import static io.harness.pcf.model.PcfConstants.IMAGE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.INACTIVE_APP_NAME_SUFFIX;
import static io.harness.pcf.model.PcfConstants.INSTANCE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.NAME_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.NO_ROUTE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.PATH_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX;
import static io.harness.pcf.model.PcfConstants.PROCESSES_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.PROCESSES_TYPE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.RANDOM_ROUTE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.ROUTES_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.ROUTE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.USERNAME_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.WEB_PROCESS_TYPE_MANIFEST_YML_ELEMENT;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.cf.TasArtifactDownloadContext;
import io.harness.delegate.task.cf.TasArtifactDownloadResponse;
import io.harness.delegate.task.cf.artifact.TasArtifactCreds;
import io.harness.delegate.task.cf.artifact.TasRegistrySettingsAdapter;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;
import io.harness.delegate.task.pcf.artifact.TasPackageArtifactConfig;
import io.harness.delegate.task.pcf.exception.InvalidPcfStateException;
import io.harness.delegate.task.pcf.request.CfBlueGreenSetupRequestNG;
import io.harness.delegate.task.pcf.request.CfCommandRequestNG;
import io.harness.delegate.task.pcf.request.TasManifestsPackage;
import io.harness.delegate.task.pcf.response.CfBlueGreenSetupResponseNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logging.Misc;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfCreateApplicationRequestData;
import io.harness.pcf.model.CfManifestFileData;
import io.harness.pcf.model.CfRenameRequest;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;
import io.harness.pcf.model.PcfConstants;

import software.wings.delegatetasks.pcf.PcfCommandTaskHelper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;

@NoArgsConstructor
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TasBlueGreenSetupTaskHandler extends CfCommandTaskNGHandler {
  @Inject PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper;
  @Inject TasNgConfigMapper tasNgConfigMapper;
  @Inject protected CfCommandTaskHelperNG cfCommandTaskHelperNG;
  @Inject CfDeploymentManager cfDeploymentManager;
  @Inject protected PcfCommandTaskHelper pcfCommandTaskHelper;
  @Inject TasTaskHelperBase tasTaskHelperBase;
  @Inject private TasRegistrySettingsAdapter tasRegistrySettingsAdapter;

  @Override
  protected CfCommandResponseNG executeTaskInternal(CfCommandRequestNG cfCommandRequestNG,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(cfCommandRequestNG instanceof CfBlueGreenSetupRequestNG)) {
      throw new InvalidArgumentsException(
          Pair.of("cfCommandRequestNG", "Must be instance of CfBlueGreenSetupRequestNG"));
    }

    LogCallback logCallback = tasTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, cfCommandRequestNG.getCommandName(), true, commandUnitsProgress);
    CfManifestFileData pcfManifestFileData = CfManifestFileData.builder().varFiles(new ArrayList<>()).build();

    CfBlueGreenSetupRequestNG blueGreenSetupRequestNG = (CfBlueGreenSetupRequestNG) cfCommandRequestNG;
    TasInfraConfig tasInfraConfig = blueGreenSetupRequestNG.getTasInfraConfig();
    CloudFoundryConfig cfConfig = tasNgConfigMapper.mapTasConfigWithDecryption(
        tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());
    CfRequestConfig cfRequestConfig = getCfRequestConfig(blueGreenSetupRequestNG, cfConfig);

    File artifactFile = null;
    File workingDirectory = null;
    List<ApplicationSummary> previousReleases =
        cfDeploymentManager.getPreviousReleases(cfRequestConfig, blueGreenSetupRequestNG.getReleaseNamePrefix());

    TasApplicationInfo activeApplicationInfo = null;
    TasApplicationInfo inActiveApplicationInfo = null;

    List<TasApplicationInfo> renames = null;
    try {
      workingDirectory = generateWorkingDirectoryOnDelegate(blueGreenSetupRequestNG);
      cfRequestConfig.setCfHomeDirPath(workingDirectory.getAbsolutePath());
      cfRequestConfig.setApplicationName(blueGreenSetupRequestNG.getReleaseNamePrefix());
      activeApplicationInfo = getActiveApplicationInfo(previousReleases, cfRequestConfig, logCallback, workingDirectory,
          blueGreenSetupRequestNG.getTimeoutIntervalInMin());
      inActiveApplicationInfo = getInActiveApplicationInfo(activeApplicationInfo, previousReleases, cfRequestConfig,
          logCallback, workingDirectory, blueGreenSetupRequestNG.getTimeoutIntervalInMin());

      CfAppAutoscalarRequestData cfAppAutoscalarRequestData =
          CfAppAutoscalarRequestData.builder()
              .cfRequestConfig(cfRequestConfig)
              .configPathVar(workingDirectory.getAbsolutePath())
              .timeoutInMins(blueGreenSetupRequestNG.getTimeoutIntervalInMin())
              .build();

      logCallback.saveExecutionLog("\n# Fetching all existing applications ");

      // Print Existing applications information
      printExistingApplicationsDetails(logCallback, previousReleases);

      artifactFile = downloadArtifactFile(blueGreenSetupRequestNG, workingDirectory, logCallback);

      deleteOlderApplications(previousReleases, cfRequestConfig, blueGreenSetupRequestNG, cfAppAutoscalarRequestData,
          logCallback, activeApplicationInfo, inActiveApplicationInfo);

      renames = renameInActiveApplication(previousReleases, blueGreenSetupRequestNG, cfRequestConfig, logCallback,
          activeApplicationInfo, inActiveApplicationInfo);

      boolean varsYmlPresent = checkIfVarsFilePresent(blueGreenSetupRequestNG);
      CfCreateApplicationRequestData requestData =
          CfCreateApplicationRequestData.builder()
              .cfRequestConfig(updatePcfRequestConfig(blueGreenSetupRequestNG, cfRequestConfig,
                  blueGreenSetupRequestNG.getReleaseNamePrefix() + INACTIVE_APP_NAME_SUFFIX))
              .artifactPath(artifactFile == null ? null : artifactFile.getAbsolutePath())
              .configPathVar(workingDirectory.getAbsolutePath())
              .newReleaseName(blueGreenSetupRequestNG.getReleaseNamePrefix() + INACTIVE_APP_NAME_SUFFIX)
              .pcfManifestFileData(pcfManifestFileData)
              .varsYmlFilePresent(varsYmlPresent)
              .dockerBasedDeployment(isDockerArtifact(blueGreenSetupRequestNG.getTasArtifactConfig()))
              .build();

      requestData.setFinalManifestYaml(generateManifestYamlForPush(blueGreenSetupRequestNG, requestData));
      // Create manifest.yaml file
      prepareManifestYamlFile(requestData);

      if (varsYmlPresent) {
        prepareVarsYamlFile(requestData, blueGreenSetupRequestNG);
      }

      logCallback.saveExecutionLog(color("\n# Creating new Application", White, Bold));

      ApplicationDetail newApplication = createAppAndPrintDetails(logCallback, requestData);

      CfBlueGreenSetupResponseNG cfSetupCommandResponse =
          CfBlueGreenSetupResponseNG.builder()
              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
              .newApplicationInfo(TasApplicationInfo.builder()
                                      .applicationGuid(newApplication.getId())
                                      .applicationName(newApplication.getName())
                                      .oldName(newApplication.getName())
                                      .attachedRoutes(new ArrayList<>(newApplication.getUrls()))
                                      .runningCount(0)
                                      .build())
              .activeApplicationInfo(activeApplicationInfo)
              .inActiveApplicationInfo(inActiveApplicationInfo)
              .build();

      logCallback.saveExecutionLog("\n ----------  PCF Setup process completed successfully", INFO, SUCCESS);
      return cfSetupCommandResponse;

    } catch (RuntimeException | PivotalClientApiException | IOException e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error(PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX + "Exception in processing PCF Setup task [{}]",
          blueGreenSetupRequestNG, sanitizedException);
      Misc.logAllMessages(sanitizedException, logCallback);
      handleRollbackForSetup(blueGreenSetupRequestNG.getReleaseNamePrefix(), renames, cfRequestConfig, logCallback);
      logCallback.saveExecutionLog(
          "\n\n ----------  PCF Setup process failed to complete successfully", ERROR, CommandExecutionStatus.FAILURE);

      return CfBlueGreenSetupResponseNG.builder()
          .inActiveApplicationInfo(inActiveApplicationInfo)
          .activeApplicationInfo(activeApplicationInfo)
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(ExceptionUtils.getMessage(sanitizedException))
          .build();
    } finally {
      logCallback = tasTaskHelperBase.getLogCallback(iLogStreamingTaskClient, Wrapup, true, commandUnitsProgress);
      removeTempFilesCreated(blueGreenSetupRequestNG, logCallback, artifactFile, workingDirectory, pcfManifestFileData);
      logCallback.saveExecutionLog("#----------  Cleaning up temporary files completed", INFO, SUCCESS);
    }
  }

  private void handleRollbackForSetup(String releaseNamePrefix, List<TasApplicationInfo> renames,
      CfRequestConfig cfRequestConfig, LogCallback logCallback) throws PivotalClientApiException {
    try {
      if (!isNull(renames)) {
        deleteCurrentInactiveAppIfExist(releaseNamePrefix, cfRequestConfig, logCallback);
        renamePreviousInactiveApp(renames, cfRequestConfig, logCallback);
      } else {
        logCallback.saveExecutionLog(
            "Revert not required as no application got created or renamed", ERROR, CommandExecutionStatus.FAILURE);
      }
    } catch (Exception e) {
      log.error(CLOUD_FOUNDRY_LOG_PREFIX + "Exception in reverting app names", e);
      logCallback.saveExecutionLog(format("\n\n ----------  Failed to revert app names : %s",
                                       ExceptionMessageSanitizer.sanitizeMessage(e.getMessage())),
          ERROR, CommandExecutionStatus.FAILURE);
    }
  }

  private void renamePreviousInactiveApp(List<TasApplicationInfo> renames, CfRequestConfig cfRequestConfig,
      LogCallback logCallback) throws PivotalClientApiException {
    if (renames.isEmpty()) {
      return;
    }
    TasApplicationInfo applicationInfo = renames.get(0);
    cfDeploymentManager.renameApplication(new CfRenameRequest(cfRequestConfig, applicationInfo.getApplicationGuid(),
                                              applicationInfo.getApplicationName(), applicationInfo.getOldName()),
        logCallback);
  }

  private void deleteCurrentInactiveAppIfExist(String releaseNamePrefix, CfRequestConfig cfRequestConfig,
      LogCallback logCallback) throws PivotalClientApiException {
    List<ApplicationSummary> releases = cfDeploymentManager.getPreviousReleases(cfRequestConfig, releaseNamePrefix);
    String inactiveAppName = releaseNamePrefix + INACTIVE_APP_NAME_SUFFIX;

    if (releases.stream().anyMatch(release -> release.getName().equals(inactiveAppName))) {
      cfRequestConfig.setApplicationName(inactiveAppName);
      logCallback.saveExecutionLog(format("\n Deleting the newly created App: %s", inactiveAppName));
      cfDeploymentManager.deleteApplication(cfRequestConfig);
      logCallback.saveExecutionLog("App deleted successfully");
    }
  }

  private CfRequestConfig updatePcfRequestConfig(
      CfBlueGreenSetupRequestNG cfBlueGreenSetupRequestNG, CfRequestConfig cfRequestConfig, String newReleaseName) {
    return CfRequestConfig.builder()
        .orgName(cfRequestConfig.getOrgName())
        .spaceName(cfRequestConfig.getSpaceName())
        .userName(cfRequestConfig.getUserName())
        .password(cfRequestConfig.getPassword())
        .endpointUrl(cfRequestConfig.getEndpointUrl())
        .manifestYaml(cfRequestConfig.getManifestYaml())
        .desiredCount(cfRequestConfig.getDesiredCount())
        .timeOutIntervalInMins(cfRequestConfig.getTimeOutIntervalInMins())
        .useCFCLI(cfRequestConfig.isUseCFCLI())
        .cfCliPath(cfRequestConfig.getCfCliPath())
        .cfCliVersion(cfRequestConfig.getCfCliVersion())
        .cfHomeDirPath(cfRequestConfig.getCfHomeDirPath())
        .loggedin(cfRequestConfig.isLoggedin())
        .limitPcfThreads(cfRequestConfig.isLimitPcfThreads())
        .useNumbering(cfRequestConfig.isUseNumbering())
        .applicationName(newReleaseName)
        .routeMaps(cfBlueGreenSetupRequestNG.getRouteMaps())
        .build();
  }

  private CfRequestConfig getCfRequestConfig(
      CfBlueGreenSetupRequestNG blueGreenSetupRequest, CloudFoundryConfig cfConfig) {
    return CfRequestConfig.builder()
        .userName(String.valueOf(cfConfig.getUserName()))
        .password(String.valueOf(cfConfig.getPassword()))
        .endpointUrl(cfConfig.getEndpointUrl())
        .orgName(blueGreenSetupRequest.getTasInfraConfig().getOrganization())
        .spaceName(blueGreenSetupRequest.getTasInfraConfig().getSpace())
        .timeOutIntervalInMins(blueGreenSetupRequest.getTimeoutIntervalInMin())
        .useCFCLI(blueGreenSetupRequest.isUseCfCLI())
        .cfCliPath(cfCommandTaskHelperNG.getCfCliPathOnDelegate(
            blueGreenSetupRequest.isUseCfCLI(), blueGreenSetupRequest.getCfCliVersion()))
        .cfCliVersion(blueGreenSetupRequest.getCfCliVersion())
        .build();
  }

  private TasApplicationInfo getActiveApplicationInfo(List<ApplicationSummary> previousReleases,
      CfRequestConfig cfRequestConfig, LogCallback logCallback, File workingDirectory, int timeoutInMins)
      throws PivotalClientApiException {
    ApplicationSummary currentActiveApplication =
        pcfCommandTaskBaseHelper.findCurrentActiveApplicationNG(previousReleases, cfRequestConfig, logCallback);
    if (currentActiveApplication == null) {
      return null;
    }
    CfAppAutoscalarRequestData cfAppAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                                .cfRequestConfig(cfRequestConfig)
                                                                .configPathVar(workingDirectory.getAbsolutePath())
                                                                .timeoutInMins(timeoutInMins)
                                                                .applicationName(currentActiveApplication.getName())
                                                                .applicationGuid(currentActiveApplication.getId())
                                                                .build();
    boolean isAutoScalarEnabled = false;
    try {
      isAutoScalarEnabled = cfDeploymentManager.checkIfAppHasAutoscalarEnabled(cfAppAutoscalarRequestData, logCallback);
    } catch (PivotalClientApiException e) {
      logCallback.saveExecutionLog(
          "Failed while fetching autoscalar state: " + encodeColor(currentActiveApplication.getName()), LogLevel.ERROR);
    }
    return TasApplicationInfo.builder()
        .applicationName(currentActiveApplication.getName())
        .applicationGuid(currentActiveApplication.getId())
        .attachedRoutes(currentActiveApplication.getUrls())
        .runningCount(currentActiveApplication.getRunningInstances())
        .oldName(currentActiveApplication.getName())
        .isAutoScalarEnabled(isAutoScalarEnabled)
        .build();
  }

  private TasApplicationInfo getInActiveApplicationInfo(TasApplicationInfo activeApplicationInfo,
      List<ApplicationSummary> previousReleases, CfRequestConfig cfRequestConfig, LogCallback logCallback,
      File workingDirectory, int timeoutInMins) throws PivotalClientApiException {
    if (activeApplicationInfo == null) {
      return null;
    }
    ApplicationSummary inActiveApplication =
        findCurrentInActiveApplication(activeApplicationInfo, previousReleases, cfRequestConfig, logCallback);
    if (inActiveApplication == null) {
      return null;
    }
    CfAppAutoscalarRequestData cfAppAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                                .cfRequestConfig(cfRequestConfig)
                                                                .configPathVar(workingDirectory.getAbsolutePath())
                                                                .timeoutInMins(timeoutInMins)
                                                                .applicationName(inActiveApplication.getName())
                                                                .applicationGuid(inActiveApplication.getId())
                                                                .build();
    boolean isAutoScalarEnabled = false;
    try {
      isAutoScalarEnabled = cfDeploymentManager.checkIfAppHasAutoscalarEnabled(cfAppAutoscalarRequestData, logCallback);
    } catch (PivotalClientApiException e) {
      logCallback.saveExecutionLog(
          "Failed while fetching autoscalar state: " + encodeColor(inActiveApplication.getName()), LogLevel.ERROR);
    }
    return TasApplicationInfo.builder()
        .applicationName(inActiveApplication.getName())
        .applicationGuid(inActiveApplication.getId())
        .attachedRoutes(inActiveApplication.getUrls())
        .runningCount(inActiveApplication.getRunningInstances())
        .isAutoScalarEnabled(isAutoScalarEnabled)
        .build();
  }

  public ApplicationSummary findCurrentInActiveApplication(TasApplicationInfo activeApplicationInfo,
      List<ApplicationSummary> previousReleases, CfRequestConfig cfRequestConfig, LogCallback logCallback)
      throws PivotalClientApiException {
    if (isEmpty(previousReleases)) {
      return null;
    }
    List<ApplicationSummary> inActiveVersions = new ArrayList<>();
    ApplicationSummary inActiveApplication = null;
    for (ApplicationSummary applicationSummary : previousReleases) {
      if (activeApplicationInfo.getApplicationName().equalsIgnoreCase(applicationSummary.getName())) {
        // in active app will always be found earlier than active app
        break;
      }
      cfRequestConfig.setApplicationName(applicationSummary.getName());
      if (cfDeploymentManager.isInActiveApplicationNG(cfRequestConfig)) {
        inActiveApplication = applicationSummary;
        inActiveVersions.add(applicationSummary);
      }
    }
    if (inActiveApplication == null) {
      inActiveVersions = previousReleases.stream()
                             .filter(app -> app.getName().endsWith(PcfConstants.INACTIVE_APP_NAME_SUFFIX))
                             .collect(toList());
      inActiveApplication = isEmpty(inActiveVersions) ? null : inActiveVersions.get(0);
    }
    if (isNotEmpty(inActiveVersions) && inActiveVersions.size() > 1) {
      StringBuilder msgBuilder =
          new StringBuilder(256)
              .append("Invalid PCF Deployment State. Found Multiple applications having Env variable as ")
              .append(HARNESS__STATUS__IDENTIFIER)
              .append(
                  ": STAGE' identifier. Cant Determine actual in active version.\n Only 1 is expected to have this Status. In Active versions found are: \n");
      inActiveVersions.forEach(activeVersion -> msgBuilder.append(activeVersion.getName()).append(' '));
      logCallback.saveExecutionLog(msgBuilder.toString(), ERROR);
      throw new InvalidPcfStateException(msgBuilder.toString(), INVALID_INFRA_STATE, USER_SRE);
    }
    return inActiveApplication;
  }
  private File generateWorkingDirectoryOnDelegate(CfBlueGreenSetupRequestNG cfCommandSetupRequest)
      throws PivotalClientApiException, IOException {
    File workingDirectory = pcfCommandTaskBaseHelper.generateWorkingDirectoryForDeployment();
    if (cfCommandSetupRequest.isUseCfCLI() || cfCommandSetupRequest.isUseAppAutoScalar()) {
      if (workingDirectory == null) {
        throw new PivotalClientApiException("Failed to generate CF-CLI Working directory");
      }
    }
    return workingDirectory;
  }

  private File downloadArtifactFile(
      CfBlueGreenSetupRequestNG blueGreenSetupRequestNG, File workingDirectory, LogCallback logCallback) {
    File artifactFile = null;
    if (isPackageArtifact(blueGreenSetupRequestNG.getTasArtifactConfig())) {
      TasArtifactDownloadResponse tasArtifactDownloadResponse = cfCommandTaskHelperNG.downloadPackageArtifact(
          TasArtifactDownloadContext.builder()
              .artifactConfig((TasPackageArtifactConfig) blueGreenSetupRequestNG.getTasArtifactConfig())
              .workingDirectory(workingDirectory)
              .build(),
          logCallback);
      artifactFile = tasArtifactDownloadResponse.getArtifactFile();
    }
    return artifactFile;
  }

  void deleteOlderApplications(List<ApplicationSummary> previousReleases, CfRequestConfig cfRequestConfig,
      CfBlueGreenSetupRequestNG setupRequestNG, CfAppAutoscalarRequestData appAutoscalarRequestData,
      LogCallback logCallback, TasApplicationInfo currentProdInfo, TasApplicationInfo inActiveApplicationInfo) {
    if (isEmpty(previousReleases)) {
      return;
    }
    if (previousReleases.size() == 1) {
      ApplicationSummary applicationSummary = previousReleases.get(0);
      if (PcfConstants.isInterimApp(applicationSummary.getName())) {
        logCallback.saveExecutionLog(
            "# Deleting previous deployment interim app: " + encodeColor(applicationSummary.getName()));
        deleteApplication(applicationSummary, cfRequestConfig, logCallback);
        previousReleases.remove(applicationSummary);
      }
      return;
    }

    int olderVersionCountToKeep = setupRequestNG.getOlderActiveVersionCountToKeep() == null
        ? MAX_RELEASE_VERSIONS_TO_KEEP
        : setupRequestNG.getOlderActiveVersionCountToKeep();

    logCallback.saveExecutionLog("# Existing applications to Keep: " + olderVersionCountToKeep);

    int olderValidAppsFound = 1;
    for (int index = previousReleases.size() - 1; index >= 0; index--) {
      ApplicationSummary applicationSummary = previousReleases.get(index);

      if (PcfConstants.isInterimApp(applicationSummary.getName())) {
        logCallback.saveExecutionLog(
            "# Deleting previous deployment interim app: " + encodeColor(applicationSummary.getName()));
        deleteApplication(applicationSummary, cfRequestConfig, logCallback);
        previousReleases.remove(applicationSummary);
        continue;
      }

      if (currentProdInfo != null && applicationSummary.getName().equals(currentProdInfo.getApplicationName())) {
        continue;
      }

      if ((olderValidAppsFound < olderVersionCountToKeep)
          || ((inActiveApplicationInfo != null && isNotEmpty(inActiveApplicationInfo.getApplicationName())
              && applicationSummary.getName().equals(inActiveApplicationInfo.getApplicationName())))) {
        olderValidAppsFound++;
        downsizeApplicationToZero(
            applicationSummary, cfRequestConfig, setupRequestNG, appAutoscalarRequestData, logCallback);
      } else {
        logCallback.saveExecutionLog("# Older application being deleted: " + encodeColor(applicationSummary.getName()));
        deleteApplication(applicationSummary, cfRequestConfig, logCallback);
        previousReleases.remove(applicationSummary);
      }
    }
  }
  private void deleteApplication(
      ApplicationSummary applicationSummary, CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) {
    cfRequestConfig.setApplicationName(applicationSummary.getName());
    try {
      cfDeploymentManager.deleteApplication(cfRequestConfig);
      //      appsDeleted.add(applicationSummary.getName());
    } catch (PivotalClientApiException e) {
      executionLogCallback.saveExecutionLog("Failed while deleting application: "
              + encodeColor(applicationSummary.getName()) + ", Continuing for next one",
          LogLevel.ERROR);
    }
  }
  void downsizeApplicationToZero(ApplicationSummary applicationSummary, CfRequestConfig cfRequestConfig,
      CfBlueGreenSetupRequestNG setupRequestNG, CfAppAutoscalarRequestData appAutoscalarRequestData,
      LogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog(
        "# Application Being Downsized To 0: " + encodeColor(applicationSummary.getName()));

    if (setupRequestNG.isUseAppAutoScalar()) {
      appAutoscalarRequestData.setApplicationName(applicationSummary.getName());
      appAutoscalarRequestData.setApplicationGuid(applicationSummary.getId());
      appAutoscalarRequestData.setExpectedEnabled(true);
      pcfCommandTaskBaseHelper.disableAutoscalarSafe(appAutoscalarRequestData, executionLogCallback);
    }

    cfRequestConfig.setApplicationName(applicationSummary.getName());
    cfRequestConfig.setDesiredCount(0);

    unMapRoutes(cfRequestConfig, executionLogCallback);
    unsetEnvVariables(cfRequestConfig, executionLogCallback);
    downsizeApplication(cfRequestConfig, executionLogCallback);
  }

  private void unMapRoutes(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) {
    try {
      ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);
      // Unmap routes from application having 0 instances
      if (isNotEmpty(applicationDetail.getUrls())) {
        cfRequestConfig.setLoggedin(false);
        cfDeploymentManager.unmapRouteMapForApplication(
            cfRequestConfig, applicationDetail.getUrls(), executionLogCallback);
      }
    } catch (PivotalClientApiException exception) {
      log.warn(ExceptionMessageSanitizer.sanitizeException(exception).getMessage());
    }
  }

  private void unsetEnvVariables(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) {
    try {
      // TODO this only for BG
      // Remove Env Variable "HARNESS__STATUS__IDENTIFIER"
      cfDeploymentManager.unsetEnvironmentVariableForAppStatus(cfRequestConfig, executionLogCallback);
    } catch (PivotalClientApiException exception) {
      log.warn(ExceptionMessageSanitizer.sanitizeException(exception).getMessage());
    }
  }

  private void downsizeApplication(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) {
    try {
      cfDeploymentManager.resizeApplication(cfRequestConfig, executionLogCallback);
    } catch (PivotalClientApiException exception) {
      log.warn(ExceptionMessageSanitizer.sanitizeException(exception).getMessage());
    }
  }

  private void removeTempFilesCreated(CfBlueGreenSetupRequestNG setupRequestNG, LogCallback executionLogCallback,
      File artifactFile, File workingDirectory, CfManifestFileData pcfManifestFileData) {
    try {
      executionLogCallback.saveExecutionLog("# Deleting any temporary files created");
      List<File> filesToBeRemoved = new ArrayList<>();

      // Delete all manifests created.
      File manifestYamlFile = pcfManifestFileData.getManifestFile();
      if (manifestYamlFile != null) {
        filesToBeRemoved.add(pcfManifestFileData.getManifestFile());
      }
      filesToBeRemoved.addAll(pcfManifestFileData.getVarFiles());

      if (artifactFile != null) {
        filesToBeRemoved.add(artifactFile);
      }

      if (setupRequestNG.isUseCfCLI() && manifestYamlFile != null) {
        filesToBeRemoved.add(
            new File(pcfCommandTaskBaseHelper.generateFinalManifestFilePath(manifestYamlFile.getAbsolutePath())));
      }

      pcfCommandTaskBaseHelper.deleteCreatedFile(filesToBeRemoved);

      if (workingDirectory != null) {
        FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory.getAbsolutePath());
      }
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.warn("Failed to remove temp files created", sanitizedException);
    }
  }

  private List<TasApplicationInfo> renameInActiveApplication(List<ApplicationSummary> previousReleases,
      CfBlueGreenSetupRequestNG blueGreenSetupRequestNG, CfRequestConfig cfRequestConfig, LogCallback logCallback,
      TasApplicationInfo activeApplicationInfo, TasApplicationInfo inActiveApplicationInfo)
      throws PivotalClientApiException {
    if (inActiveApplicationInfo == null || isEmpty(inActiveApplicationInfo.getApplicationGuid())
        || previousReleases.size() == 1) {
      return Collections.emptyList();
    }
    ApplicationSummary inActiveApplication =
        findCurrentInActiveApplication(activeApplicationInfo, previousReleases, cfRequestConfig, logCallback);
    int highestVersionUsed = getHighestVersionAppName(previousReleases);
    String appNamePrefix = blueGreenSetupRequestNG.getReleaseNamePrefix();
    String newName = appNamePrefix + DELIMITER + (highestVersionUsed + 1);
    pcfCommandTaskBaseHelper.renameApp(inActiveApplication, cfRequestConfig, logCallback, newName);
    inActiveApplicationInfo.setOldName(inActiveApplicationInfo.getApplicationName());
    inActiveApplicationInfo.setApplicationName(newName);
    return Collections.singletonList(inActiveApplicationInfo);
  }

  private int getHighestVersionAppName(List<ApplicationSummary> previousReleases) {
    int maxVersion = -1;
    int latestVersionUsed = -1;
    for (ApplicationSummary previousApp : previousReleases) {
      latestVersionUsed = pcfCommandTaskBaseHelper.getRevisionFromReleaseName(previousApp.getName());
      maxVersion = Math.max(latestVersionUsed, maxVersion);
    }
    return maxVersion;
  }

  boolean checkIfVarsFilePresent(CfBlueGreenSetupRequestNG setupRequest) {
    if (setupRequest.getTasManifestsPackage() == null) {
      return false;
    }

    List<String> varFiles = setupRequest.getTasManifestsPackage().getVariableYmls();
    if (isNotEmpty(varFiles)) {
      varFiles = varFiles.stream().filter(StringUtils::isNotBlank).collect(toList());
    }

    return isNotEmpty(varFiles);
  }

  public String generateManifestYamlForPush(CfBlueGreenSetupRequestNG setupRequestNG,
      CfCreateApplicationRequestData requestData) throws PivotalClientApiException {
    // Substitute name,
    String manifestYaml = setupRequestNG.getTasManifestsPackage().getManifestYml();

    Map<String, Object> map;
    try {
      ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
      map = (Map<String, Object>) mapper.readValue(manifestYaml, Map.class);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new UnexpectedException("Failed to get Yaml Map", sanitizedException);
    }

    List<Map> applicationMaps = (List<Map>) map.get(APPLICATION_YML_ELEMENT);

    if (isEmpty(applicationMaps)) {
      throw new InvalidArgumentsException(
          Pair.of("Manifest.yml does not have any elements under \'applications\'", manifestYaml));
    }

    Map mapForUpdate = applicationMaps.get(0);
    TreeMap<String, Object> applicationToBeUpdated = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    applicationToBeUpdated.putAll(mapForUpdate);

    applicationToBeUpdated.put(NAME_MANIFEST_YML_ELEMENT, requestData.getNewReleaseName());

    // TODO - verify how this can be done
    updateArtifactDetails(requestData, setupRequestNG, applicationToBeUpdated);

    applicationToBeUpdated.put(INSTANCE_MANIFEST_YML_ELEMENT, 0);

    if (applicationToBeUpdated.containsKey(PROCESSES_MANIFEST_YML_ELEMENT)) {
      Object processes = applicationToBeUpdated.get(PROCESSES_MANIFEST_YML_ELEMENT);
      if (processes instanceof ArrayList<?>) {
        ArrayList<Map<String, Object>> allProcesses = (ArrayList<Map<String, Object>>) processes;
        for (Map<String, Object> process : allProcesses) {
          Object p = process.get(PROCESSES_TYPE_MANIFEST_YML_ELEMENT);
          if ((p instanceof String) && (p.toString().equals(WEB_PROCESS_TYPE_MANIFEST_YML_ELEMENT))) {
            process.put(INSTANCE_MANIFEST_YML_ELEMENT, 0);
          }
        }
      }
    }
    updateConfigWithRoutesIfRequired(applicationToBeUpdated, setupRequestNG);
    // We do not want to change order

    // remove "create-services" elements as it would have been used by cf cli plugin to create services.
    // This elements is not needed for cf push
    map.remove(CREATE_SERVICE_MANIFEST_ELEMENT);
    addInActiveIdentifierToManifest(applicationToBeUpdated);
    Map<String, Object> applicationMapForYamlDump =
        pcfCommandTaskBaseHelper.generateFinalMapForYamlDump(applicationToBeUpdated);

    // replace map for first application that we are deploying
    applicationMaps.set(0, applicationMapForYamlDump);
    try {
      return yaml.dump(map);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new PivotalClientApiException(
          "Failed to generate final version of  Manifest.yml file. " + manifestYaml, sanitizedException);
    }
  }

  void updateArtifactDetails(CfCreateApplicationRequestData requestData,
      CfBlueGreenSetupRequestNG cfCommandSetupRequest, TreeMap<String, Object> applicationToBeUpdated) {
    if (isPackageArtifact(cfCommandSetupRequest.getTasArtifactConfig())) {
      applicationToBeUpdated.put(PATH_MANIFEST_YML_ELEMENT, requestData.getArtifactPath());
    } else {
      TasContainerArtifactConfig tasContainerArtifactConfig =
          (TasContainerArtifactConfig) cfCommandSetupRequest.getTasArtifactConfig();
      TasArtifactCreds tasArtifactCreds = tasRegistrySettingsAdapter.getContainerSettings(tasContainerArtifactConfig);
      Map<String, Object> dockerDetails = new HashMap<>();
      String dockerImagePath = tasContainerArtifactConfig.getImage();
      dockerDetails.put(IMAGE_MANIFEST_YML_ELEMENT, dockerImagePath);
      if (!isEmpty(tasArtifactCreds.getUsername())) {
        dockerDetails.put(USERNAME_MANIFEST_YML_ELEMENT, tasArtifactCreds.getUsername());
      }
      if (!isEmpty(tasArtifactCreds.getPassword())) {
        requestData.setPassword(tasArtifactCreds.getPassword().toCharArray());
      }
      applicationToBeUpdated.put(DOCKER_MANIFEST_YML_ELEMENT, dockerDetails);
    }
  }

  // Add Env Variable marking this deployment version as Inactive
  private void addInActiveIdentifierToManifest(Map<String, Object> map) {
    Map<String, Object> envMap = null;
    if (map.containsKey(ENV_MANIFEST_YML_ELEMENT)) {
      envMap = (Map<String, Object>) map.get(ENV_MANIFEST_YML_ELEMENT);
    }

    if (envMap == null) {
      envMap = new HashMap<>();
    }

    envMap.put(HARNESS__STATUS__IDENTIFIER, HARNESS__INACTIVE__IDENTIFIER);
    map.put(ENV_MANIFEST_YML_ELEMENT, envMap);
  }
  void prepareManifestYamlFile(CfCreateApplicationRequestData requestData) throws IOException {
    File manifestYamlFile = pcfCommandTaskBaseHelper.createManifestYamlFileLocally(requestData);
    requestData.setManifestFilePath(manifestYamlFile.getAbsolutePath());
    requestData.getPcfManifestFileData().setManifestFile(manifestYamlFile);
  }

  void prepareVarsYamlFile(CfCreateApplicationRequestData requestData, CfBlueGreenSetupRequestNG setupRequest)
      throws IOException {
    if (!requestData.isVarsYmlFilePresent()) {
      return;
    }

    TasManifestsPackage tasManifestsPackage = setupRequest.getTasManifestsPackage();
    AtomicInteger varFileIndex = new AtomicInteger(0);
    if (tasManifestsPackage.getVariableYmls() != null) {
      tasManifestsPackage.getVariableYmls().forEach(varFileYml -> {
        File varsYamlFile =
            pcfCommandTaskBaseHelper.createManifestVarsYamlFileLocally(requestData, varFileYml, varFileIndex.get());
        if (varsYamlFile != null) {
          varFileIndex.incrementAndGet();
          requestData.getPcfManifestFileData().getVarFiles().add(varsYamlFile);
        }
      });
    }
  }

  private void updateConfigWithRoutesIfRequired(
      TreeMap applicationToBeUpdated, CfBlueGreenSetupRequestNG setupRequest) {
    applicationToBeUpdated.remove(ROUTES_MANIFEST_YML_ELEMENT);

    // 1. Check and handle no-route scenario
    boolean isNoRoute = applicationToBeUpdated.containsKey(NO_ROUTE_MANIFEST_YML_ELEMENT)
        && (boolean) applicationToBeUpdated.get(NO_ROUTE_MANIFEST_YML_ELEMENT);
    if (isNoRoute) {
      applicationToBeUpdated.remove(ROUTES_MANIFEST_YML_ELEMENT);
      return;
    }

    // 2. Check if random-route config is needed. This happens if random-route=true in manifest or
    // user has not provided any route value.
    if (pcfCommandTaskBaseHelper.shouldUseRandomRoute(applicationToBeUpdated, setupRequest.getRouteMaps())) {
      applicationToBeUpdated.put(RANDOM_ROUTE_MANIFEST_YML_ELEMENT, true);
      return;
    }

    // 3. Insert routes provided by user.
    List<String> routesForUse = setupRequest.getRouteMaps();
    List<Map<String, String>> routeMapList = new ArrayList<>();
    routesForUse.forEach(routeString -> {
      Map<String, String> mapEntry = Collections.singletonMap(ROUTE_MANIFEST_YML_ELEMENT, routeString);
      routeMapList.add(mapEntry);
    });

    // Add this route config to applicationConfig
    applicationToBeUpdated.put(ROUTES_MANIFEST_YML_ELEMENT, routeMapList);
  }

  ApplicationDetail createAppAndPrintDetails(
      LogCallback executionLogCallback, CfCreateApplicationRequestData requestData) throws PivotalClientApiException {
    requestData.getCfRequestConfig().setLoggedin(false);
    ApplicationDetail newApplication = cfDeploymentManager.createApplication(requestData, executionLogCallback);
    executionLogCallback.saveExecutionLog(color("# Application created successfully", White, Bold));
    executionLogCallback.saveExecutionLog("# App Details: ");
    pcfCommandTaskBaseHelper.printApplicationDetail(newApplication, executionLogCallback);
    return newApplication;
  }
}

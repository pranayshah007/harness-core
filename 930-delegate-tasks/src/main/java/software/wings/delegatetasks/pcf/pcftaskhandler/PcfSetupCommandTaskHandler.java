/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.pcf.pcftaskhandler;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.cf.PcfCommandTaskBaseHelper.constructInActiveAppName;
import static io.harness.delegate.cf.PcfCommandTaskBaseHelper.getMaxVersion;
import static io.harness.delegate.cf.PcfCommandTaskBaseHelper.getVersionChangeMessage;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.pcf.CfCommandUnitConstants.CheckExistingApps;
import static io.harness.pcf.CfCommandUnitConstants.PcfSetup;
import static io.harness.pcf.CfCommandUnitConstants.Wrapup;
import static io.harness.pcf.PcfUtils.encodeColor;
import static io.harness.pcf.PcfUtils.getRevisionFromServiceName;
import static io.harness.pcf.model.PcfConstants.HARNESS__INACTIVE__IDENTIFIER;
import static io.harness.pcf.model.PcfConstants.INACTIVE_APP_NAME_SUFFIX;
import static io.harness.pcf.model.PcfConstants.PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfAppRenameInfo;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.cf.PcfCommandTaskHandler;
import io.harness.delegate.cf.apprenaming.AppNamingStrategy;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.PcfManifestsPackage;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfSetupCommandResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logging.Misc;
import io.harness.pcf.PcfUtils;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfCreateApplicationRequestData;
import io.harness.pcf.model.CfManifestFileData;
import io.harness.pcf.model.CfRenameRequest;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.PcfConstants;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.pcf.PcfCommandTaskHelper;
import software.wings.helpers.ext.pcf.request.CfCommandSetupRequest;
import software.wings.settings.SettingValue;
import software.wings.utils.ServiceVersionConvention;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@NoArgsConstructor
@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(HarnessTeam.CDP)
public class PcfSetupCommandTaskHandler extends PcfCommandTaskHandler {
  @Inject protected PcfCommandTaskHelper pcfCommandTaskHelper;
  private int MAX_RELEASE_VERSIONS_TO_KEEP = 3;

  /**
   * This method is responsible for fetching previous release version information
   * like, previous releaseNames with Running instances, All existing previous releaseNames.
   */
  @Override
  public CfCommandExecutionResponse executeTaskInternal(CfCommandRequest cfCommandRequest,
      List<EncryptedDataDetail> encryptedDataDetails, ILogStreamingTaskClient logStreamingTaskClient,
      boolean isInstanceSync) {
    if (!(cfCommandRequest instanceof CfCommandSetupRequest)) {
      throw new InvalidArgumentsException(Pair.of("cfCommandRequest", "Must be instance of CfCommandSetupRequest"));
    }
    LogCallback executionLogCallback = logStreamingTaskClient.obtainLogCallback(cfCommandRequest.getCommandName());
    CfManifestFileData pcfManifestFileData = CfManifestFileData.builder().varFiles(new ArrayList<>()).build();
    CfInternalConfig pcfConfig = cfCommandRequest.getPcfConfig();
    secretDecryptionService.decrypt(pcfConfig, encryptedDataDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(pcfConfig, encryptedDataDetails);
    CfCommandSetupRequest cfCommandSetupRequest = (CfCommandSetupRequest) cfCommandRequest;
    decryptArtifactRepositoryPassword(cfCommandSetupRequest);
    File artifactFile = null;
    File workingDirectory = null;
    Deque<CfAppRenameInfo> renames = new ArrayDeque<>();
    CfRequestConfig cfRequestConfig = null;
    String newReleaseName = null;

    try {
      executionLogCallback = logStreamingTaskClient.obtainLogCallback(CheckExistingApps);

      workingDirectory = generateWorkingDirectoryOnDelegate(cfCommandSetupRequest);

      cfRequestConfig = CfRequestConfig.builder()
                            .orgName(cfCommandSetupRequest.getOrganization())
                            .spaceName(cfCommandSetupRequest.getSpace())
                            .userName(String.valueOf(pcfConfig.getUsername()))
                            .password(String.valueOf(pcfConfig.getPassword()))
                            .endpointUrl(pcfConfig.getEndpointUrl())
                            .timeOutIntervalInMins(cfCommandSetupRequest.getTimeoutIntervalInMin())
                            .useCFCLI(cfCommandSetupRequest.isUseCfCLI())
                            .cfCliPath(pcfCommandTaskBaseHelper.getCfCliPathOnDelegate(
                                cfCommandRequest.isUseCfCLI(), cfCommandRequest.getCfCliVersion()))
                            .cfCliVersion(cfCommandRequest.getCfCliVersion())
                            .cfHomeDirPath(workingDirectory.getAbsolutePath())
                            .limitPcfThreads(cfCommandSetupRequest.isLimitPcfThreads())
                            .build();

      CfAppAutoscalarRequestData pcfAppAutoscalarRequestData =
          CfAppAutoscalarRequestData.builder()
              .cfRequestConfig(cfRequestConfig)
              .configPathVar(workingDirectory.getAbsolutePath())
              .timeoutInMins(cfCommandSetupRequest.getTimeoutIntervalInMin())
              .build();

      executionLogCallback.saveExecutionLog("\n# Fetching all existing applications ");

      // Get all previous release names in ascending order of version number
      List<ApplicationSummary> previousReleases =
          pcfDeploymentManager.getPreviousReleases(cfRequestConfig, cfCommandSetupRequest.getReleaseNamePrefix());

      // Print Existing applications information
      printExistingApplicationsDetails(executionLogCallback, previousReleases);

      boolean nonVersioning = cfCommandSetupRequest.isNonVersioning();
      String existingAppNamingStrategy = getExistingAppNamingStrategy(previousReleases, cfCommandSetupRequest);
      boolean versioningChanged =
          isVersioningChanged(nonVersioning, previousReleases, cfCommandSetupRequest.getReleaseNamePrefix());
      int activeAppRevision = -1;
      String inActiveAppOldName = "";
      CfAppSetupTimeDetails mostRecentInactiveAppVersionDetails = CfAppSetupTimeDetails.builder().build();

      if (!cfCommandSetupRequest.isBlueGreen()) {
        mostRecentInactiveAppVersionDetails = pcfCommandTaskBaseHelper.getInActiveApplicationDetailsBeforeNewExecution(
            previousReleases, cfRequestConfig, executionLogCallback);
        inActiveAppOldName = mostRecentInactiveAppVersionDetails.getOldName();
      }

      if (cfCommandSetupRequest.isBlueGreen()) {
        mostRecentInactiveAppVersionDetails =
            pcfCommandTaskBaseHelper.renameInActiveAppDuringBGDeployment(previousReleases, cfRequestConfig,
                cfCommandSetupRequest.getReleaseNamePrefix(), executionLogCallback, existingAppNamingStrategy, renames);
        inActiveAppOldName = isNotEmpty(mostRecentInactiveAppVersionDetails.getOldName())
            ? mostRecentInactiveAppVersionDetails.getOldName()
            : "";
      } else if (versioningChanged && !cfCommandSetupRequest.isBlueGreen()) {
        executionLogCallback.saveExecutionLog(getVersionChangeMessage(nonVersioning));
        activeAppRevision = executeVersioningChange(
            previousReleases, cfRequestConfig, cfCommandSetupRequest, nonVersioning, renames, executionLogCallback);
        executionLogCallback.saveExecutionLog(getVersionChangeMessage(nonVersioning) + " completed");
      } else if (cfCommandSetupRequest.isNonVersioningInactiveRollbackEnabled()) {
        cleanupUnusedApps(previousReleases, cfRequestConfig, cfCommandSetupRequest, executionLogCallback);
      }

      previousReleases =
          renameApps(cfRequestConfig, cfCommandSetupRequest, nonVersioning, renames, executionLogCallback);

      // currently Active version is stamped for BG only.
      cfRequestConfig.setApplicationName(((CfCommandSetupRequest) cfCommandRequest).getReleaseNamePrefix());
      ApplicationSummary activeApplication = pcfCommandTaskBaseHelper.findActiveApplication(
          executionLogCallback, cfCommandSetupRequest.isBlueGreen(), cfRequestConfig, previousReleases);

      mostRecentInactiveAppVersionDetails =
          getInActiveApplicationDetails(executionLogCallback, cfCommandSetupRequest.isBlueGreen(), activeApplication,
              previousReleases, cfRequestConfig, inActiveAppOldName, mostRecentInactiveAppVersionDetails);
      if (cfCommandSetupRequest.isBlueGreen() && (nonVersioning || versioningChanged)) {
        executionLogCallback.saveExecutionLog(getInActiveAppMessage(mostRecentInactiveAppVersionDetails));
      }

      // Get new Revision version
      String releaseRevision =
          getReleaseRevisionForNewApplication(previousReleases, versioningChanged, cfCommandSetupRequest);

      // Delete any older application excpet most recent 1.
      deleteOlderApplications(previousReleases, cfRequestConfig, cfCommandSetupRequest, pcfAppAutoscalarRequestData,
          activeApplication, mostRecentInactiveAppVersionDetails, executionLogCallback);
      executionLogCallback.saveExecutionLog("Completed Checking Existing Application", INFO, SUCCESS);

      // Fetch apps again, as apps may have been deleted/downsized
      executionLogCallback = logStreamingTaskClient.obtainLogCallback(PcfSetup);
      executionLogCallback.saveExecutionLog(color("---------- Starting PCF App Setup Command", White, Bold));
      previousReleases =
          pcfDeploymentManager.getPreviousReleases(cfRequestConfig, cfCommandSetupRequest.getReleaseNamePrefix());

      Integer totalPreviousInstanceCount = CollectionUtils.isEmpty(previousReleases)
          ? 0
          : previousReleases.stream().mapToInt(ApplicationSummary::getInstances).sum();

      Integer instanceCountForMostRecentVersion = CollectionUtils.isEmpty(previousReleases)
          ? Integer.valueOf(0)
          : previousReleases.get(previousReleases.size() - 1).getRunningInstances();

      // New appName to be created
      newReleaseName =
          ServiceVersionConvention.getServiceName(cfCommandSetupRequest.getReleaseNamePrefix(), releaseRevision);
      if (!cfCommandSetupRequest.getArtifactStreamAttributes().isDockerBasedDeployment()) {
        artifactFile = fetchArtifactFileForDeployment(cfCommandSetupRequest, workingDirectory, executionLogCallback);
      }

      boolean varsYmlPresent = checkIfVarsFilePresent(cfCommandSetupRequest);
      CfCreateApplicationRequestData requestData =
          CfCreateApplicationRequestData.builder()
              .cfRequestConfig(cfRequestConfig)
              .artifactPath(artifactFile == null ? null : artifactFile.getAbsolutePath())
              .configPathVar(workingDirectory.getAbsolutePath())
              .password(pcfCommandTaskHelper.getPassword(cfCommandSetupRequest.getArtifactStreamAttributes()))
              .newReleaseName(newReleaseName)
              .pcfManifestFileData(pcfManifestFileData)
              .varsYmlFilePresent(varsYmlPresent)
              .dockerBasedDeployment(cfCommandSetupRequest.getArtifactStreamAttributes().isDockerBasedDeployment())
              .build();

      // Generate final manifest Yml needed for push.
      requestData.setFinalManifestYaml(
          pcfCommandTaskHelper.generateManifestYamlForPush(cfCommandSetupRequest, requestData));
      // Create manifest.yaml file
      prepareManifestYamlFile(requestData);
      // create vars file if needed
      if (varsYmlPresent) {
        prepareVarsYamlFile(requestData, cfCommandSetupRequest);
      }

      // Create new Application
      executionLogCallback.saveExecutionLog(color("\n# Creating new Application", White, Bold));
      // Update pcfRequestConfig with details to create application
      updatePcfRequestConfig(cfCommandSetupRequest, cfRequestConfig, newReleaseName);
      // create PCF Application
      ApplicationDetail newApplication = createAppAndPrintDetails(executionLogCallback, requestData);
      renames.clear();

      List<CfAppSetupTimeDetails> downsizeAppDetails =
          pcfCommandTaskBaseHelper.generateDownsizeDetails(activeApplication);

      CfSetupCommandResponse cfSetupCommandResponse =
          CfSetupCommandResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
              .output(StringUtils.EMPTY)
              .newApplicationDetails(CfAppSetupTimeDetails.builder()
                                         .applicationGuid(newApplication.getId())
                                         .applicationName(newApplication.getName())
                                         .oldName(newApplication.getName())
                                         .urls(new ArrayList<>(newApplication.getUrls()))
                                         .initialInstanceCount(0)
                                         .build())
              .totalPreviousInstanceCount(totalPreviousInstanceCount)
              .instanceCountForMostRecentVersion(instanceCountForMostRecentVersion)
              .mostRecentInactiveAppVersion(mostRecentInactiveAppVersionDetails)
              .downsizeDetails(downsizeAppDetails)
              .versioningChanged(versioningChanged)
              .nonVersioning(cfCommandSetupRequest.isNonVersioning())
              .existingAppNamingStrategy(existingAppNamingStrategy)
              .activeAppRevision(activeAppRevision)
              .build();

      executionLogCallback.saveExecutionLog("\n ----------  PCF Setup process completed successfully", INFO, SUCCESS);
      return CfCommandExecutionResponse.builder()
          .commandExecutionStatus(cfSetupCommandResponse.getCommandExecutionStatus())
          .errorMessage(cfSetupCommandResponse.getOutput())
          .pcfCommandResponse(cfSetupCommandResponse)
          .build();

    } catch (RuntimeException | PivotalClientApiException | IOException | ExecutionException e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error(PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX + "Exception in processing PCF Setup task [{}]", cfCommandSetupRequest,
          sanitizedException);
      executionLogCallback.saveExecutionLog(
          "\n\n ----------  PCF Setup process failed to complete successfully", ERROR, CommandExecutionStatus.FAILURE);

      handleAppRenameRevert(
          newReleaseName, renames, cfRequestConfig, cfCommandSetupRequest.getReleaseNamePrefix(), executionLogCallback);

      Misc.logAllMessages(sanitizedException, executionLogCallback);
      return CfCommandExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(ExceptionUtils.getMessage(sanitizedException))
          .build();
    } finally {
      executionLogCallback = logStreamingTaskClient.obtainLogCallback(Wrapup);
      // Delete downloaded artifact and generated manifest.yaml file
      removeTempFilesCreated((CfCommandSetupRequest) cfCommandRequest, executionLogCallback, artifactFile,
          workingDirectory, pcfManifestFileData);
      executionLogCallback.saveExecutionLog("#----------  Cleaning up temporary files completed", INFO, SUCCESS);
    }
  }

  private String getExistingAppNamingStrategy(
      List<ApplicationSummary> previousReleases, CfCommandSetupRequest setupRequest) {
    return isNonVersionReleaseExist(previousReleases, setupRequest.getReleaseNamePrefix())
        ? AppNamingStrategy.APP_NAME_WITH_VERSIONING.name()
        : AppNamingStrategy.VERSIONING.name();
  }

  private String getInActiveAppMessage(CfAppSetupTimeDetails mostRecentInactiveAppVersionDetails) {
    if (mostRecentInactiveAppVersionDetails == null
        || isEmpty(mostRecentInactiveAppVersionDetails.getApplicationName())) {
      return "No in-active app found";
    }
    Integer initialInstanceCount = mostRecentInactiveAppVersionDetails.getInitialInstanceCount();
    List<String> urls = mostRecentInactiveAppVersionDetails.getUrls();
    return String.format("Considering [%s] as in-active app. Instance count - [%d]. Routes - %s",
        PcfUtils.encodeColor(mostRecentInactiveAppVersionDetails.getApplicationName()),
        initialInstanceCount != null ? initialInstanceCount : 0, isEmpty(urls) ? Collections.emptyList() : urls);
  }

  private void deleteNewAppIfExist(CfRequestConfig cfRequestConfig, String releaseNamePrefix, String newReleaseName,
      LogCallback logCallback) throws PivotalClientApiException {
    if (EmptyPredicate.isNotEmpty(newReleaseName)) {
      List<ApplicationSummary> releases = pcfDeploymentManager.getPreviousReleases(cfRequestConfig, releaseNamePrefix);
      if (releases.stream().anyMatch(release -> release.getName().equals(newReleaseName))) {
        String cfRequestConfigAppName = cfRequestConfig.getApplicationName();
        cfRequestConfig.setApplicationName(newReleaseName);
        logCallback.saveExecutionLog(new StringBuilder()
                                         .append("\n\n Deleting the newly created App: ")
                                         .append(encodeColor(newReleaseName))
                                         .toString());
        pcfDeploymentManager.deleteApplication(cfRequestConfig);
        logCallback.saveExecutionLog("App deleted successfully");
        cfRequestConfig.setApplicationName(cfRequestConfigAppName);
      }
    }
  }

  private void handleAppRenameRevert(String newReleaseName, Deque<CfAppRenameInfo> renames,
      CfRequestConfig cfRequestConfig, String releaseNamePrefix, LogCallback logCallback) {
    try {
      if (null != cfRequestConfig && !renames.isEmpty()) {
        logCallback.saveExecutionLog("\n\n Reverting App names");
        deleteNewAppIfExist(cfRequestConfig, releaseNamePrefix, newReleaseName, logCallback);
        List<ApplicationSummary> releases =
            pcfDeploymentManager.getPreviousReleases(cfRequestConfig, releaseNamePrefix);
        Set<String> ids = releases.stream().map(ApplicationSummary::getId).collect(Collectors.toSet());
        while (!renames.isEmpty()) {
          CfAppRenameInfo renameInfo = renames.removeLast();
          if (ids.contains(renameInfo.getGuid())) {
            pcfDeploymentManager.renameApplication(new CfRenameRequest(cfRequestConfig, renameInfo.getGuid(),
                                                       renameInfo.getNewName(), renameInfo.getName()),
                logCallback);
          }
        }
        logCallback.saveExecutionLog("App names reverted successfully", ERROR, CommandExecutionStatus.FAILURE);
      }
    } catch (Exception e) {
      log.error(PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX + "Exception in reverting app names", e);
      logCallback.saveExecutionLog(new StringBuilder()
                                       .append("\n\n ----------  Failed to revert app names")
                                       .append(ExceptionUtils.getMessage(e))
                                       .toString(),
          ERROR, CommandExecutionStatus.FAILURE);
    }
  }

  private List<ApplicationSummary> renameApps(CfRequestConfig cfRequestConfig,
      CfCommandSetupRequest cfCommandSetupRequest, boolean nonVersioning, Deque<CfAppRenameInfo> renames,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    List<ApplicationSummary> releases =
        pcfDeploymentManager.getPreviousReleases(cfRequestConfig, cfCommandSetupRequest.getReleaseNamePrefix());
    if (cfCommandSetupRequest.isBlueGreen()) {
      return releases;
    }

    if (nonVersioning) {
      executionLogCallback.saveExecutionLog("\n# Initiating renaming of apps");
      cfRequestConfig.setApplicationName(cfCommandSetupRequest.getReleaseNamePrefix());
      ApplicationSummary activeApp = pcfCommandTaskBaseHelper.findActiveApplication(
          executionLogCallback, cfCommandSetupRequest.isBlueGreen(), cfRequestConfig, releases);
      ApplicationSummary inActiveApp = pcfCommandTaskBaseHelper.getMostRecentInactiveApplication(
          executionLogCallback, cfCommandSetupRequest.isBlueGreen(), activeApp, releases, cfRequestConfig);
      Integer maxVersion = getMaxVersion(releases);
      boolean appRenamed = false;

      if (null != inActiveApp) {
        String newName = constructInActiveAppName(cfCommandSetupRequest.getReleaseNamePrefix(), maxVersion, false);
        pcfCommandTaskBaseHelper.renameApp(inActiveApp, cfRequestConfig, executionLogCallback, newName);
        renames.add(
            CfAppRenameInfo.builder().guid(inActiveApp.getId()).name(inActiveApp.getName()).newName(newName).build());
        appRenamed = true;
      }

      if (null != activeApp) {
        String newName = constructInActiveAppName(cfCommandSetupRequest.getReleaseNamePrefix(), maxVersion, true);
        pcfCommandTaskBaseHelper.renameApp(activeApp, cfRequestConfig, executionLogCallback, newName);
        renames.add(
            CfAppRenameInfo.builder().guid(activeApp.getId()).name(activeApp.getName()).newName(newName).build());
        appRenamed = true;
      }

      if (appRenamed) {
        releases =
            pcfDeploymentManager.getPreviousReleases(cfRequestConfig, cfCommandSetupRequest.getReleaseNamePrefix());
        executionLogCallback.saveExecutionLog("# Renaming of apps completed");
      } else {
        executionLogCallback.saveExecutionLog("# No apps to rename");
      }
    }

    return releases;
  }

  private void cleanupUnusedApps(List<ApplicationSummary> previousReleases, CfRequestConfig cfRequestConfig,
      CfCommandSetupRequest cfCommandSetupRequest, LogCallback executionLogCallback) throws PivotalClientApiException {
    ApplicationSummary activeApplication = pcfCommandTaskBaseHelper.findActiveApplication(
        executionLogCallback, cfCommandSetupRequest.isBlueGreen(), cfRequestConfig, previousReleases);
    ApplicationSummary inactiveApplication =
        pcfCommandTaskBaseHelper.getMostRecentInactiveApplication(executionLogCallback,
            cfCommandSetupRequest.isBlueGreen(), activeApplication, previousReleases, cfRequestConfig);
    cleanupUnusedApps(previousReleases, cfRequestConfig, executionLogCallback, activeApplication, inactiveApplication);
  }

  private boolean isVersioningChanged(
      boolean nonVersioning, List<ApplicationSummary> releases, String releaseNamePrefix) {
    boolean nonVersionReleaseExist = isNonVersionReleaseExist(releases, releaseNamePrefix);
    return isNotEmpty(releases)
        && ((nonVersioning && !nonVersionReleaseExist) || (!nonVersioning && nonVersionReleaseExist));
  }

  private boolean isNonVersionReleaseExist(List<ApplicationSummary> releases, String releaseNamePrefix) {
    String inActiveAppName = releaseNamePrefix + INACTIVE_APP_NAME_SUFFIX;
    return releases.stream().anyMatch(
        app -> app.getName().equalsIgnoreCase(releaseNamePrefix) || app.getName().equalsIgnoreCase(inActiveAppName));
  }

  /**
   * @param previousReleases      All existing releases
   * @param cfRequestConfig       CfRequest config
   * @param cfCommandSetupRequest SetupRequest
   * @param nonVersioning         nonVersioning enabled
   * @param renames               Renames stack
   * @param executionLogCallback  log callback
   * @return Revision of active App if any (else -1)
   * @throws PivotalClientApiException exception
   */
  private int executeVersioningChange(List<ApplicationSummary> previousReleases, CfRequestConfig cfRequestConfig,
      CfCommandSetupRequest cfCommandSetupRequest, boolean nonVersioning, Deque<CfAppRenameInfo> renames,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    int activeAppRevision = -1;
    if (isEmpty(previousReleases)) {
      return -1;
    }

    ApplicationSummary activeApplication = pcfCommandTaskBaseHelper.findActiveApplication(
        executionLogCallback, cfCommandSetupRequest.isBlueGreen(), cfRequestConfig, previousReleases);
    if (null != activeApplication) {
      activeAppRevision = getRevisionFromServiceName(activeApplication.getName());
    }
    ApplicationSummary inactiveApplication =
        pcfCommandTaskBaseHelper.getMostRecentInactiveApplication(executionLogCallback,
            cfCommandSetupRequest.isBlueGreen(), activeApplication, previousReleases, cfRequestConfig);

    cleanupUnusedApps(previousReleases, cfRequestConfig, executionLogCallback, activeApplication, inactiveApplication);
    previousReleases =
        pcfDeploymentManager.getPreviousReleases(cfRequestConfig, cfCommandSetupRequest.getReleaseNamePrefix());

    pcfCommandTaskBaseHelper.resetState(previousReleases, activeApplication, inactiveApplication,
        cfCommandSetupRequest.getReleaseNamePrefix(), cfRequestConfig, nonVersioning, renames, -1, executionLogCallback,
        CfInBuiltVariablesUpdateValues.builder().build());
    return activeAppRevision;
  }

  private Set<String> cleanupUnusedApps(List<ApplicationSummary> previousReleases, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback, ApplicationSummary activeApplication, ApplicationSummary inactiveApplication) {
    boolean foundInactive = false;
    Set<String> appsDeleted = new HashSet<>();

    for (ApplicationSummary application : previousReleases) {
      if (null != inactiveApplication && application.getName().equals(inactiveApplication.getName())) {
        foundInactive = true;
        continue;
      }
      if ((foundInactive && !application.equals(activeApplication))
          || PcfConstants.isInterimApp(application.getName())) {
        executionLogCallback.saveExecutionLog(
            "# Unused application being deleted: " + encodeColor(application.getName()));
        deleteApplication(application, cfRequestConfig, appsDeleted, executionLogCallback);
      }
    }

    return appsDeleted;
  }

  private CfAppSetupTimeDetails getInActiveApplicationDetails(LogCallback executionLogCallback, boolean blueGreen,
      ApplicationSummary activeApplication, List<ApplicationSummary> previousReleases, CfRequestConfig cfRequestConfig,
      String inActiveAppOldName, CfAppSetupTimeDetails inActiveAppDetails) throws PivotalClientApiException {
    ApplicationSummary mostRecentInactiveApplication = pcfCommandTaskBaseHelper.getMostRecentInactiveApplication(
        executionLogCallback, blueGreen, activeApplication, previousReleases, cfRequestConfig);
    if (mostRecentInactiveApplication == null) {
      return inActiveAppDetails;
    }
    return CfAppSetupTimeDetails.builder()
        .applicationGuid(mostRecentInactiveApplication.getId())
        .applicationName(mostRecentInactiveApplication.getName())
        .oldName(isEmpty(inActiveAppOldName) ? mostRecentInactiveApplication.getName() : inActiveAppOldName)
        .initialInstanceCount(mostRecentInactiveApplication.getRunningInstances())
        .urls(new ArrayList<>(mostRecentInactiveApplication.getUrls()))
        .build();
  }

  private void decryptArtifactRepositoryPassword(CfCommandSetupRequest cfCommandSetupRequest) {
    ArtifactStreamAttributes artifactStreamAttributes = cfCommandSetupRequest.getArtifactStreamAttributes();
    if (artifactStreamAttributes.isDockerBasedDeployment()) {
      SettingValue settingValue = artifactStreamAttributes.getServerSetting().getValue();
      List<EncryptedDataDetail> artifactServerEncryptedDataDetails =
          cfCommandSetupRequest.getArtifactStreamAttributes().getArtifactServerEncryptedDataDetails();
      secretDecryptionService.decrypt((EncryptableSetting) settingValue, artifactServerEncryptedDataDetails, false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          (EncryptableSetting) settingValue, artifactServerEncryptedDataDetails);
    }
  }

  private File generateWorkingDirectoryOnDelegate(CfCommandSetupRequest cfCommandSetupRequest)
      throws PivotalClientApiException, IOException {
    // This path represents location where artifact will be downloaded, manifest file will be created and
    // config.json file will be generated with login details by cf cli, for current task.
    // This value is set to CF_HOME env variable when process executor is created.
    File workingDirectory = pcfCommandTaskBaseHelper.generateWorkingDirectoryForDeployment();
    if (cfCommandSetupRequest.isUseCfCLI() || cfCommandSetupRequest.isUseAppAutoscalar()) {
      if (workingDirectory == null) {
        throw new PivotalClientApiException("Failed to generate CF-CLI Working directory");
      }
    }

    return workingDirectory;
  }

  private File fetchArtifactFileForDeployment(CfCommandSetupRequest cfCommandSetupRequest, File workingDirectory,
      LogCallback executionLogCallback) throws IOException, ExecutionException {
    File artifactFile;
    if (cfCommandSetupRequest.getArtifactStreamAttributes().isMetadataOnly()) {
      executionLogCallback.saveExecutionLog(
          color("--------- artifact will be downloaded for only-meta feature", White));
      artifactFile =
          pcfCommandTaskHelper.downloadArtifact(cfCommandSetupRequest, workingDirectory, executionLogCallback);
    } else {
      // Download artifact on delegate from manager
      artifactFile = pcfCommandTaskHelper.downloadArtifactFromManager(
          executionLogCallback, cfCommandSetupRequest, workingDirectory);
    }

    return artifactFile;
  }

  private void printExistingApplicationsDetails(
      LogCallback executionLogCallback, List<ApplicationSummary> previousReleases) {
    if (EmptyPredicate.isEmpty(previousReleases)) {
      executionLogCallback.saveExecutionLog("# No Existing applications found");
    } else {
      StringBuilder appNames = new StringBuilder(color("# Existing applications: ", White, Bold));
      previousReleases.forEach(
          applicationSummary -> appNames.append("\n").append(encodeColor(applicationSummary.getName())));
      executionLogCallback.saveExecutionLog(appNames.toString());
    }
  }

  @VisibleForTesting
  ApplicationDetail createAppAndPrintDetails(
      LogCallback executionLogCallback, CfCreateApplicationRequestData requestData) throws PivotalClientApiException {
    requestData.getCfRequestConfig().setLoggedin(false);
    ApplicationDetail newApplication = pcfDeploymentManager.createApplication(requestData, executionLogCallback);
    executionLogCallback.saveExecutionLog(color("# Application created successfully", White, Bold));
    executionLogCallback.saveExecutionLog("# App Details: ");
    pcfCommandTaskBaseHelper.printApplicationDetail(newApplication, executionLogCallback);
    return newApplication;
  }

  @VisibleForTesting
  boolean checkIfVarsFilePresent(CfCommandSetupRequest setupRequest) {
    if (setupRequest.getPcfManifestsPackage() == null) {
      return false;
    }

    List<String> varFiles = setupRequest.getPcfManifestsPackage().getVariableYmls();
    if (isNotEmpty(varFiles)) {
      varFiles = varFiles.stream().filter(StringUtils::isNotBlank).collect(toList());
    }

    return isNotEmpty(varFiles);
  }

  @VisibleForTesting
  void prepareManifestYamlFile(CfCreateApplicationRequestData requestData) throws IOException {
    File manifestYamlFile = pcfCommandTaskBaseHelper.createManifestYamlFileLocally(requestData);
    requestData.setManifestFilePath(manifestYamlFile.getAbsolutePath());
    requestData.getPcfManifestFileData().setManifestFile(manifestYamlFile);
  }

  @VisibleForTesting
  void prepareVarsYamlFile(CfCreateApplicationRequestData requestData, CfCommandSetupRequest setupRequest)
      throws IOException {
    if (!requestData.isVarsYmlFilePresent()) {
      return;
    }

    PcfManifestsPackage pcfManifestsPackage = setupRequest.getPcfManifestsPackage();
    AtomicInteger varFileIndex = new AtomicInteger(0);
    pcfManifestsPackage.getVariableYmls().forEach(varFileYml -> {
      File varsYamlFile =
          pcfCommandTaskBaseHelper.createManifestVarsYamlFileLocally(requestData, varFileYml, varFileIndex.get());
      if (varsYamlFile != null) {
        varFileIndex.incrementAndGet();
        requestData.getPcfManifestFileData().getVarFiles().add(varsYamlFile);
      }
    });
  }

  private void updatePcfRequestConfig(
      CfCommandSetupRequest cfCommandSetupRequest, CfRequestConfig cfRequestConfig, String newReleaseName) {
    cfRequestConfig.setApplicationName(newReleaseName);
    cfRequestConfig.setRouteMaps(cfCommandSetupRequest.getRouteMaps());
    cfRequestConfig.setServiceVariables(cfCommandSetupRequest.getServiceVariables());
    cfRequestConfig.setSafeDisplayServiceVariables(cfCommandSetupRequest.getSafeDisplayServiceVariables());
  }

  private String getReleaseRevisionForNewApplication(List<ApplicationSummary> previousReleases,
      boolean versioningChanged, CfCommandSetupRequest cfCommandSetupRequest) {
    boolean nonVersioning = cfCommandSetupRequest.isNonVersioning();

    if (cfCommandSetupRequest.isBlueGreen() && (nonVersioning || versioningChanged)) {
      return HARNESS__INACTIVE__IDENTIFIER;
    }

    String revision = StringUtils.EMPTY;
    if (!nonVersioning) {
      revision = CollectionUtils.isEmpty(previousReleases)
          ? "0"
          : String.valueOf(pcfCommandTaskBaseHelper.getRevisionFromReleaseName(
                               previousReleases.get(previousReleases.size() - 1).getName())
              + 1);
    }

    return revision;
  }

  private void removeTempFilesCreated(CfCommandSetupRequest cfCommandSetupRequest, LogCallback executionLogCallback,
      File artifactFile, File workingDirectory, CfManifestFileData pcfManifestFileData) {
    try {
      executionLogCallback.saveExecutionLog("# Deleting any temporary files created");
      List<File> filesToBeRemoved = new ArrayList<>();

      // Delete all manifests created.
      File manifestYamlFile = pcfManifestFileData.getManifestFile();
      if (manifestYamlFile != null) {
        filesToBeRemoved.add(pcfManifestFileData.getManifestFile());
      }
      pcfManifestFileData.getVarFiles().forEach(filesToBeRemoved::add);

      if (artifactFile != null) {
        filesToBeRemoved.add(artifactFile);
      }

      if (cfCommandSetupRequest.isUseCfCLI() && manifestYamlFile != null) {
        filesToBeRemoved.add(
            new File(pcfCommandTaskBaseHelper.generateFinalManifestFilePath(manifestYamlFile.getAbsolutePath())));
      }

      pcfCommandTaskBaseHelper.deleteCreatedFile(filesToBeRemoved);

      if (workingDirectory != null) {
        FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory.getAbsolutePath());
      }
    } catch (Exception e) {
      log.warn("Failed to remove temp files created", e);
    }
  }

  /**
   * 1. First Delete all apps  with 0 instance count
   * 2. Now process apps with non-zero apps.
   * 3. Based on count "LastVersopAppsToKeep" provided by user, (default is 3)
   * 4. Keep most recent app as is, and (last LastVersopAppsToKeep - 1) apps will be downsized to 0
   * 5. All apps older than that will be deleted
   *
   * @param previousReleases
   * @param cfRequestConfig
   * @param activeApplication
   * @param inactiveAppVersionDetails
   */
  @VisibleForTesting
  void deleteOlderApplications(List<ApplicationSummary> previousReleases, CfRequestConfig cfRequestConfig,
      CfCommandSetupRequest cfCommandSetupRequest, CfAppAutoscalarRequestData appAutoscalarRequestData,
      ApplicationSummary activeApplication, CfAppSetupTimeDetails inactiveAppVersionDetails,
      LogCallback executionLogCallback) {
    if (EmptyPredicate.isEmpty(previousReleases)) {
      return;
    }

    Integer olderVersionCountToKeep = cfCommandSetupRequest.getOlderActiveVersionCountToKeep() == null
        ? Integer.valueOf(MAX_RELEASE_VERSIONS_TO_KEEP)
        : cfCommandSetupRequest.getOlderActiveVersionCountToKeep();

    executionLogCallback.saveExecutionLog("# Existing applications to Keep: " + olderVersionCountToKeep);
    Set<String> appsDeleted = new HashSet<>();

    // Now, we need to keep "olderVersionCountToKeep" no of apps.
    // We will keep most recent/active one as is, and downsize olderActiveVersionCountToKeep - 1
    // apps to 0, so they will be deleted in next deployment.
    int olderValidAppsFound = 1;
    if (isNotEmpty(previousReleases) && previousReleases.size() > 1) {
      for (int index = previousReleases.size() - 1; index >= 0; index--) {
        ApplicationSummary applicationSummary = previousReleases.get(index);
        if (activeApplication != null && applicationSummary.getName().equals(activeApplication.getName())) {
          continue;
        } else if (PcfConstants.isInterimApp(applicationSummary.getName())) {
          executionLogCallback.saveExecutionLog(
              "# Deleting previous deployment interim app: " + encodeColor(applicationSummary.getName()));
          deleteApplication(applicationSummary, cfRequestConfig, appsDeleted, executionLogCallback);
          appsDeleted.add(applicationSummary.getName());
        } else if (olderValidAppsFound < olderVersionCountToKeep
            || (inactiveAppVersionDetails != null && isNotEmpty(inactiveAppVersionDetails.getApplicationName())
                && applicationSummary.getName().equals(inactiveAppVersionDetails.getApplicationName()))) {
          olderValidAppsFound++;
          downsizeApplicationToZero(applicationSummary, cfRequestConfig, cfCommandSetupRequest,
              appAutoscalarRequestData, executionLogCallback);
        } else {
          executionLogCallback.saveExecutionLog(
              "# Older application being deleted: " + encodeColor(applicationSummary.getName()));
          deleteApplication(applicationSummary, cfRequestConfig, appsDeleted, executionLogCallback);
          appsDeleted.add(applicationSummary.getName());
        }
      }
    }

    if (isNotEmpty(appsDeleted)) {
      executionLogCallback.saveExecutionLog("# Done Deleting older applications. "
          + "Deleted Total " + appsDeleted.size() + " applications");
      executionLogCallback.saveExecutionLog(String.format("Apps deleted - [%s]", String.join(",", appsDeleted)));
    } else {
      executionLogCallback.saveExecutionLog("# No older applications were eligible for deletion\n");
    }
  }

  private void deleteApplication(ApplicationSummary applicationSummary, CfRequestConfig cfRequestConfig,
      Set<String> appsDeleted, LogCallback executionLogCallback) {
    cfRequestConfig.setApplicationName(applicationSummary.getName());
    try {
      pcfDeploymentManager.deleteApplication(cfRequestConfig);
      appsDeleted.add(applicationSummary.getName());
    } catch (PivotalClientApiException e) {
      executionLogCallback.saveExecutionLog(new StringBuilder(128)
                                                .append("Failed while deleting application: ")
                                                .append(encodeColor(applicationSummary.getName()))
                                                .append(", Continuing for next one")
                                                .toString(),
          LogLevel.ERROR);
    }
  }

  @VisibleForTesting
  void downsizeApplicationToZero(ApplicationSummary applicationSummary, CfRequestConfig cfRequestConfig,
      CfCommandSetupRequest cfCommandSetupRequest, CfAppAutoscalarRequestData appAutoscalarRequestData,
      LogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog(new StringBuilder()
                                              .append("# Application Being Downsized To 0: ")
                                              .append(encodeColor(applicationSummary.getName()))
                                              .toString());

    if (cfCommandSetupRequest.isUseAppAutoscalar()) {
      appAutoscalarRequestData.setApplicationName(applicationSummary.getName());
      appAutoscalarRequestData.setApplicationGuid(applicationSummary.getId());
      appAutoscalarRequestData.setExpectedEnabled(true);
      pcfCommandTaskBaseHelper.disableAutoscalarSafe(appAutoscalarRequestData, executionLogCallback);
    }

    cfRequestConfig.setApplicationName(applicationSummary.getName());
    cfRequestConfig.setDesiredCount(0);

    unMapRoutes(cfRequestConfig, executionLogCallback);
    unsetEnvVariables(cfRequestConfig, cfCommandSetupRequest, executionLogCallback);
    downsizeApplication(cfRequestConfig, executionLogCallback);
  }

  private void unMapRoutes(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) {
    try {
      ApplicationDetail applicationDetail = pcfDeploymentManager.getApplicationByName(cfRequestConfig);
      // Unmap routes from application having 0 instances
      if (isNotEmpty(applicationDetail.getUrls())) {
        pcfDeploymentManager.unmapRouteMapForApplication(
            cfRequestConfig, applicationDetail.getUrls(), executionLogCallback);
      }
    } catch (PivotalClientApiException exception) {
      log.warn(ExceptionMessageSanitizer.sanitizeException(exception).getMessage());
    }
  }

  private void unsetEnvVariables(
      CfRequestConfig cfRequestConfig, CfCommandSetupRequest cfCommandSetupRequest, LogCallback executionLogCallback) {
    if (!cfCommandSetupRequest.isBlueGreen()) {
      return;
    }

    try {
      // Remove Env Variable "HARNESS__STATUS__IDENTIFIER"
      pcfDeploymentManager.unsetEnvironmentVariableForAppStatus(cfRequestConfig, executionLogCallback);
    } catch (PivotalClientApiException exception) {
      log.warn(ExceptionMessageSanitizer.sanitizeException(exception).getMessage());
    }
  }

  private void downsizeApplication(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) {
    try {
      pcfDeploymentManager.resizeApplication(cfRequestConfig, executionLogCallback);
    } catch (PivotalClientApiException exception) {
      log.warn(ExceptionMessageSanitizer.sanitizeException(exception).getMessage());
    }
  }
}

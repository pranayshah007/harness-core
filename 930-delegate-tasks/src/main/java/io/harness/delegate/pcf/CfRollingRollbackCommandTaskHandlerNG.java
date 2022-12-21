/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.pcf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.cf.TasArtifactDownloadContext;
import io.harness.delegate.task.cf.TasArtifactDownloadResponse;
import io.harness.delegate.task.cf.artifact.TasArtifactCreds;
import io.harness.delegate.task.cf.artifact.TasRegistrySettingsAdapter;
import io.harness.delegate.task.pcf.PcfManifestsPackage;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;
import io.harness.delegate.task.pcf.artifact.TasPackageArtifactConfig;
import io.harness.delegate.task.pcf.request.CfBasicSetupRequestNG;
import io.harness.delegate.task.pcf.request.CfCommandRequestNG;
import io.harness.delegate.task.pcf.request.CfDeployCommandRequestNG;
import io.harness.delegate.task.pcf.request.CfRollingDeployRequestNG;
import io.harness.delegate.task.pcf.request.CfRollingRollbackRequestNG;
import io.harness.delegate.task.pcf.response.CfBasicSetupResponseNG;
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
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CfRevertApplicationRequestData;
import io.harness.pcf.model.CloudFoundryConfig;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.pcf.CfCommandUnitConstants.Wrapup;
import static io.harness.pcf.PcfUtils.encodeColor;
import static io.harness.pcf.model.PcfConstants.APPLICATION_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.CREATE_SERVICE_MANIFEST_ELEMENT;
import static io.harness.pcf.model.PcfConstants.DOCKER_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.IMAGE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.NAME_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.NO_ROUTE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.PATH_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX;
import static io.harness.pcf.model.PcfConstants.RANDOM_ROUTE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.ROUTES_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.ROUTE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.USERNAME_MANIFEST_YML_ELEMENT;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

@NoArgsConstructor
@Singleton
@Slf4j
@OwnedBy(CDP)
public class CfRollingRollbackCommandTaskHandlerNG extends CfCommandTaskNGHandler {
  @Inject TasTaskHelperBase tasTaskHelperBase;
  @Inject PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper;
  @Inject TasNgConfigMapper tasNgConfigMapper;
  @Inject CfDeploymentManager cfDeploymentManager;
  @Inject protected CfCommandTaskHelperNG cfCommandTaskHelperNG;

  @Override
  protected CfCommandResponseNG executeTaskInternal(CfCommandRequestNG cfCommandRequestNG,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(cfCommandRequestNG instanceof CfDeployCommandRequestNG)) {
      throw new InvalidArgumentsException(
          Pair.of("cfCommandRequestNG", "Must be instance of CfDeployCommandRequestNG"));
    }

    LogCallback logCallback = tasTaskHelperBase.getLogCallback(
            iLogStreamingTaskClient, cfCommandRequestNG.getCommandName(), true, commandUnitsProgress);
    CfManifestFileData pcfManifestFileData = CfManifestFileData.builder().varFiles(new ArrayList<>()).build();

    CfRollingRollbackRequestNG cfRollingRollbackRequestNG = (CfRollingRollbackRequestNG) cfCommandRequestNG;
    TasInfraConfig tasInfraConfig = cfRollingRollbackRequestNG.getTasInfraConfig();
    CloudFoundryConfig cfConfig = tasNgConfigMapper.mapTasConfigWithDecryption(
            tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());
    CfRequestConfig cfRequestConfig = getCfRequestConfig(cfRollingRollbackRequestNG, cfConfig);

    File artifactFile = null;
    File workingDirectory = null;
    try {
      workingDirectory = generateWorkingDirectoryOnDelegate(cfRollingRollbackRequestNG);
      cfRequestConfig.setApplicationName(cfRollingRollbackRequestNG.getApplicationName());
      CfAppAutoscalarRequestData cfAppAutoscalarRequestData =
              CfAppAutoscalarRequestData.builder()
                      .cfRequestConfig(cfRequestConfig)
                      .configPathVar(workingDirectory.getAbsolutePath())
                      .timeoutInMins(cfRollingRollbackRequestNG.getTimeoutIntervalInMin())
                      .build();

      logCallback.saveExecutionLog("\n# Fetching Details of Application ");

      CfRevertApplicationRequestData requestData =
              CfRevertApplicationRequestData.builder()
                      .cfRequestConfig(clonePcfRequestConfig(cfRequestConfig)
                              .applicationName(cfRollingRollbackRequestNG.getReleaseNamePrefix())
                              .routeMaps(cfRollingRollbackRequestNG.getRouteMaps())
                              .build())
                      .applicationId(cfRollingRollbackRequestNG.getApplicationId())
                      .revisionId(cfRollingRollbackRequestNG.getRevisionId())
                      .strategy("rolling")
                      .build();

      logCallback.saveExecutionLog(color("\n# Reverting Application", White, Bold));

      cfAppAutoscalarRequestData.setApplicationName(cfRollingRollbackRequestNG.getApplicationName());
      cfAppAutoscalarRequestData.setApplicationGuid(cfRollingRollbackRequestNG.getApplicationId());
      cfAppAutoscalarRequestData.setExpectedEnabled(true);
      pcfCommandTaskBaseHelper.disableAutoscalarSafe(cfAppAutoscalarRequestData, logCallback);

      if(cfRollingRollbackRequestNG.isFirstDeployment()) {
        // Deleting
        cfCommandTaskHelperNG.unmapRouteMaps(cfRollingRollbackRequestNG.getApplicationName(), cfRollingRollbackRequestNG.getRouteMaps(), cfRequestConfig, logCallback);
        cfCommandTaskHelperNG.deleteNewApp(cfRequestConfig, cfRollingRollbackRequestNG.getApplicationName(),
                cfRollingRollbackRequestNG.getApplicationInfo(), logCallback);
      } else {
        ApplicationDetail applicationDetail = rollbackAppAndPrintDetails(logCallback, requestData);

        if (cfRollingRollbackRequestNG.isUseAppAutoScalar()) {
          cfCommandTaskHelperNG.enableAutoscalerIfNeeded(applicationDetail, cfAppAutoscalarRequestData, logCallback);
        }
      }

      CfBasicSetupResponseNG cfSetupCommandResponse =
              CfBasicSetupResponseNG.builder()
                      .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                      .build();

      logCallback.saveExecutionLog("\n ----------  PCF Setup process completed successfully", INFO, SUCCESS);
      return cfSetupCommandResponse;


    } catch (RuntimeException | PivotalClientApiException | IOException e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error(PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX + "Exception in processing PCF Setup task [{}]", cfRollingRollbackRequestNG,
              sanitizedException);
      logCallback.saveExecutionLog(
              "\n\n ----------  PCF Setup process failed to complete successfully", ERROR, CommandExecutionStatus.FAILURE);

      Misc.logAllMessages(sanitizedException, logCallback);
      return CfBasicSetupResponseNG.builder()
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .errorMessage(ExceptionUtils.getMessage(sanitizedException))
              .build();
    } finally {
      logCallback = tasTaskHelperBase.getLogCallback(iLogStreamingTaskClient, Wrapup, true, commandUnitsProgress);
      removeTempFilesCreated(cfRollingRollbackRequestNG, logCallback, artifactFile, workingDirectory, pcfManifestFileData);
      logCallback.saveExecutionLog("#----------  Cleaning up temporary files completed", INFO, SUCCESS);
    }
  }

  // Remove downloaded artifact and generated yaml files
  private void removeTempFilesCreated(CfRollingRollbackRequestNG cfRollingRollbackRequestNG, LogCallback executionLogCallback,
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

      if (cfRollingRollbackRequestNG.isUseCfCLI() && manifestYamlFile != null) {
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

  private ApplicationDetail rollbackAppAndPrintDetails(
          LogCallback executionLogCallback, CfRevertApplicationRequestData requestData) throws PivotalClientApiException, InterruptedException {
    requestData.getCfRequestConfig().setLoggedin(false);
    ApplicationDetail newApplication = cfDeploymentManager.rollbackRollingApplicationWithSteadyStateCheck(requestData, executionLogCallback);
    executionLogCallback.saveExecutionLog(color("# Application created successfully", White, Bold));
    executionLogCallback.saveExecutionLog("# App Details: ");
    pcfCommandTaskBaseHelper.printApplicationDetail(newApplication, executionLogCallback);
    return newApplication;
  }

  private CfRequestConfig.CfRequestConfigBuilder clonePcfRequestConfig(CfRequestConfig cfRequestConfig) {
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
            .applicationName(cfRequestConfig.getApplicationName())
            .routeMaps(cfRequestConfig.getRouteMaps());
  }

  private ApplicationSummary getCurrentProdApplicationSummary(List<ApplicationSummary> previousReleases) {
    if (EmptyPredicate.isEmpty(previousReleases)) {
      return null;
    }

    ApplicationSummary currentActiveApplication =
            previousReleases.stream()
                    .filter(applicationSummary -> applicationSummary.getInstances() > 0)
                    .reduce((first, second) -> second)
                    .orElse(null);

    // if not found, get Most recent version with non-zero count.
    if (currentActiveApplication == null) {
      currentActiveApplication = previousReleases.get(previousReleases.size() - 1);
    }
    return currentActiveApplication;
  }

  private File generateWorkingDirectoryOnDelegate(CfRollingRollbackRequestNG cfRollingRollbackRequestNG)
          throws PivotalClientApiException, IOException {
    File workingDirectory = pcfCommandTaskBaseHelper.generateWorkingDirectoryForDeployment();
    if (cfRollingRollbackRequestNG.isUseCfCLI() || cfRollingRollbackRequestNG.isUseAppAutoScalar()) {
      if (workingDirectory == null) {
        throw new PivotalClientApiException("Failed to generate CF-CLI Working directory");
      }
    }
    return workingDirectory;
  }

  private CfRequestConfig getCfRequestConfig(CfRollingRollbackRequestNG cfRollingRollbackRequestNG, CloudFoundryConfig cfConfig) {
    return CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .password(String.valueOf(cfConfig.getPassword()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .orgName(cfRollingRollbackRequestNG.getTasInfraConfig().getOrganization())
            .spaceName(cfRollingRollbackRequestNG.getTasInfraConfig().getSpace())
            .timeOutIntervalInMins(cfRollingRollbackRequestNG.getTimeoutIntervalInMin())
            .useCFCLI(cfRollingRollbackRequestNG.isUseCfCLI())
            .cfCliPath(cfCommandTaskHelperNG.getCfCliPathOnDelegate(
                    cfRollingRollbackRequestNG.isUseCfCLI(), cfRollingRollbackRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollingRollbackRequestNG.getCfCliVersion())
            .build();
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.pcf.CfCommandUnitConstants.RouteMapping;
import static io.harness.pcf.CfCommandUnitConstants.Wrapup;
import static io.harness.pcf.model.PcfConstants.PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX;

import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.cf.artifact.TasRegistrySettingsAdapter;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.request.CfCommandRequestNG;
import io.harness.delegate.task.pcf.request.CfRouteMappingRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfRouteMappingResponseNG;
import io.harness.delegate.task.pcf.response.CfRouteMappingResponseNG.CfRouteMappingResponseNGBuilder;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@NoArgsConstructor
@Singleton
@Slf4j
@OwnedBy(CDP)
public class CfRouteMappingCommandTaskHandlerNG extends CfCommandTaskNGHandler {
  @Inject TasTaskHelperBase tasTaskHelperBase;
  @Inject PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper;
  @Inject TasNgConfigMapper tasNgConfigMapper;
  @Inject CfDeploymentManager cfDeploymentManager;
  @Inject protected CfCommandTaskHelperNG cfCommandTaskHelperNG;
  @Inject private TasRegistrySettingsAdapter tasRegistrySettingsAdapter;

  @Override
  public CfCommandResponseNG executeTaskInternal(CfCommandRequestNG cfCommandRequestNG,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(cfCommandRequestNG instanceof CfRouteMappingRequestNG)) {
      throw new InvalidArgumentsException(Pair.of("cfCommandRequestNG", "Must be instance of CfRouteMappingRequestNG"));
    }

    LogCallback logCallback =
        tasTaskHelperBase.getLogCallback(iLogStreamingTaskClient, RouteMapping, true, commandUnitsProgress);

    CfRouteMappingRequestNG cfRouteMappingRequestNG = (CfRouteMappingRequestNG) cfCommandRequestNG;
    TasInfraConfig tasInfraConfig = cfRouteMappingRequestNG.getTasInfraConfig();
    CloudFoundryConfig cfConfig = tasNgConfigMapper.mapTasConfigWithDecryption(
        tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());
    CfRequestConfig cfRequestConfig = getCfRequestConfig(cfRouteMappingRequestNG, cfConfig);

    File workingDirectory = null;
    CfRouteMappingResponseNGBuilder cfRouteMappingResponseNGBuilder = CfRouteMappingResponseNG.builder();
    try {
      List<ApplicationSummary> previousReleases = cfDeploymentManager.getPreviousReleasesForRolling(
          cfRequestConfig, ((CfRouteMappingRequestNG) cfCommandRequestNG).getApplicationName());
      workingDirectory = generateWorkingDirectoryOnDelegate(cfRouteMappingRequestNG);
      cfRequestConfig.setCfHomeDirPath(workingDirectory.getAbsolutePath());
      ApplicationDetail detailsBeforeDeployment = isEmpty(previousReleases)
          ? null
          : cfCommandTaskHelperNG.getApplicationDetails(cfRequestConfig, cfDeploymentManager);

      if (!isEmpty(previousReleases)) {
        try {
          if (cfRouteMappingRequestNG.isAttachRoutes()) {
            pcfCommandTaskBaseHelper.mapRouteMaps(cfRouteMappingRequestNG.getApplicationName(),
                cfRouteMappingRequestNG.getRoutes(), cfRequestConfig, logCallback);
          } else {
            pcfCommandTaskBaseHelper.unmapRouteMaps(cfRouteMappingRequestNG.getApplicationName(),
                cfRouteMappingRequestNG.getRoutes(), cfRequestConfig, logCallback);
          }
          CfRouteMappingResponseNG cfRouteMappingResponseNG =
              cfRouteMappingResponseNGBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

          logCallback.saveExecutionLog("TAS Route Map Step completed successfully", INFO, SUCCESS);
          return cfRouteMappingResponseNG;
        } catch (RuntimeException | PivotalClientApiException e) {
          Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
          log.error(PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX + "Exception in processing Tas Route Map task [{}]",
              cfRouteMappingRequestNG, sanitizedException);

          logCallback.saveExecutionLog(color("TAS Route Map Task failed to complete successfully\n", Red, Bold), INFO);
          logCallback.saveExecutionLog("\n# Error: " + sanitizedException.getMessage(), INFO);
          logCallback.saveExecutionLog(color("\n\nStarting Rollback -------", White, Bold), INFO);

          ApplicationDetail applicationDetail =
              cfCommandTaskHelperNG.getApplicationDetails(cfRequestConfig, cfDeploymentManager);

          List<String> routes = new ArrayList<>();
          if (cfRouteMappingRequestNG.isAttachRoutes()) {
            if (applicationDetail != null && detailsBeforeDeployment != null
                && !EmptyPredicate.isEmpty(applicationDetail.getUrls())
                && !EmptyPredicate.isEmpty(detailsBeforeDeployment.getUrls())) {
              for (String url : applicationDetail.getUrls()) {
                if (!detailsBeforeDeployment.getUrls().contains(url)) {
                  routes.add(url);
                }
              }
            }
            if (EmptyPredicate.isEmpty(routes)) {
              logCallback.saveExecutionLog("No routes to unmap in rollback\n", INFO);
            } else {
              pcfCommandTaskBaseHelper.unmapRouteMaps(
                  cfRouteMappingRequestNG.getApplicationName(), routes, cfRequestConfig, logCallback);
            }
          } else {
            if (applicationDetail != null && detailsBeforeDeployment != null
                && !EmptyPredicate.isEmpty(applicationDetail.getUrls())
                && !EmptyPredicate.isEmpty(detailsBeforeDeployment.getUrls())) {
              for (String url : detailsBeforeDeployment.getUrls()) {
                if (!applicationDetail.getUrls().contains(url)) {
                  routes.add(url);
                }
              }
            }
            if (EmptyPredicate.isEmpty(routes)) {
              logCallback.saveExecutionLog("No routes to map in rollback\n", INFO);
            } else {
              pcfCommandTaskBaseHelper.mapRouteMaps(
                  cfRouteMappingRequestNG.getApplicationName(), routes, cfRequestConfig, logCallback);
            }
          }

          logCallback.saveExecutionLog(
              color("Rollback for TAS Route Map Task completed successfully\n", White, Bold), INFO, FAILURE);
          return cfRouteMappingResponseNGBuilder.commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .errorMessage(ExceptionUtils.getMessage(sanitizedException))
              .build();
        }
      } else {
        CfRouteMappingResponseNG cfRouteMappingResponseNG =
            cfRouteMappingResponseNGBuilder.commandExecutionStatus(CommandExecutionStatus.FAILURE)
                .errorMessage(String.format("No App found with name %s", cfRouteMappingRequestNG.getApplicationName()))
                .build();
        logCallback.saveExecutionLog(
            String.format("No App found with name %s", cfRouteMappingRequestNG.getApplicationName()), ERROR,
            CommandExecutionStatus.FAILURE);

        return cfRouteMappingResponseNG;
      }
    } catch (RuntimeException | PivotalClientApiException | IOException e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error(PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX + "Exception in processing TAS Route Mapping task [{}]",
          cfRouteMappingRequestNG, sanitizedException);

      logCallback.saveExecutionLog(color("Failed", Red, Bold), ERROR);
      logCallback.saveExecutionLog("# Error: " + sanitizedException.getMessage(), ERROR, FAILURE);
      return cfRouteMappingResponseNGBuilder.commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(ExceptionUtils.getMessage(sanitizedException))
          .build();
    } finally {
      logCallback = tasTaskHelperBase.getLogCallback(iLogStreamingTaskClient, Wrapup, true, commandUnitsProgress);
      removeTempFilesCreated(cfRouteMappingRequestNG, logCallback, workingDirectory);
      logCallback.saveExecutionLog("#----------  Cleaning up temporary files completed", INFO, SUCCESS);
    }
  }

  // Remove downloaded artifact and generated yaml files
  private void removeTempFilesCreated(
      CfRouteMappingRequestNG cfRouteMappingRequestNG, LogCallback executionLogCallback, File workingDirectory) {
    try {
      executionLogCallback.saveExecutionLog("# Deleting any temporary files created");
      List<File> filesToBeRemoved = new ArrayList<>();

      pcfCommandTaskBaseHelper.deleteCreatedFile(filesToBeRemoved);

      if (workingDirectory != null) {
        FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory.getAbsolutePath());
      }
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.warn("Failed to remove temp files created", sanitizedException);
    }
  }

  private File generateWorkingDirectoryOnDelegate(CfRouteMappingRequestNG cfRouteMappingRequestNG)
      throws PivotalClientApiException, IOException {
    File workingDirectory = pcfCommandTaskBaseHelper.generateWorkingDirectoryForDeployment();
    if (cfRouteMappingRequestNG.isUseCfCLI()) {
      if (workingDirectory == null) {
        throw new PivotalClientApiException("Failed to generate CF-CLI Working directory");
      }
    }
    return workingDirectory;
  }

  private CfRequestConfig getCfRequestConfig(
      CfRouteMappingRequestNG cfRouteMappingRequestNG, CloudFoundryConfig cfConfig) {
    return CfRequestConfig.builder()
        .userName(String.valueOf(cfConfig.getUserName()))
        .password(String.valueOf(cfConfig.getPassword()))
        .endpointUrl(cfConfig.getEndpointUrl())
        .orgName(cfRouteMappingRequestNG.getTasInfraConfig().getOrganization())
        .spaceName(cfRouteMappingRequestNG.getTasInfraConfig().getSpace())
        .timeOutIntervalInMins(cfRouteMappingRequestNG.getTimeoutIntervalInMin())
        .useCFCLI(cfRouteMappingRequestNG.isUseCfCLI())
        .cfCliPath(cfCommandTaskHelperNG.getCfCliPathOnDelegate(
            cfRouteMappingRequestNG.isUseCfCLI(), cfRouteMappingRequestNG.getCfCliVersion()))
        .cfCliVersion(cfRouteMappingRequestNG.getCfCliVersion())
        .applicationName(cfRouteMappingRequestNG.getApplicationName())
        .build();
  }
}

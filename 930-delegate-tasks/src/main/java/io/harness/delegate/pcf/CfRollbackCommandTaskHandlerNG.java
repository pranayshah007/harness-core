package io.harness.delegate.pcf;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfDeployCommandResult;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.request.CfCommandRequestNG;
import io.harness.delegate.task.pcf.request.CfRollbackCommandRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfDeployCommandResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.Misc;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfRenameRequest;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.cf.PcfCommandTaskBaseHelper.constructActiveAppName;
import static io.harness.delegate.cf.PcfCommandTaskBaseHelper.constructInActiveAppName;
import static io.harness.delegate.cf.PcfCommandTaskBaseHelper.getVersionChangeMessage;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;
import static io.harness.pcf.CfCommandUnitConstants.Downsize;
import static io.harness.pcf.CfCommandUnitConstants.Wrapup;
import static io.harness.pcf.PcfUtils.encodeColor;
import static io.harness.pcf.model.PcfConstants.PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

@NoArgsConstructor
@Singleton
@Slf4j
@OwnedBy(CDP)
public class CfRollbackCommandTaskHandlerNG extends  CfCommandTaskNGHandler{
    @Inject
    TasTaskHelperBase tasTaskHelperBase;
    @Inject
    TasNgConfigMapper tasNgConfigMapper;
    @Inject
    CfDeploymentManager cfDeploymentManager;
    @Inject protected CfCommandTaskHelperNG cfCommandTaskHelperNG;
    @Override
    protected CfCommandResponseNG executeTaskInternal(CfCommandRequestNG cfCommandRequestNG,
                                                      ILogStreamingTaskClient iLogStreamingTaskClient,
                                                      CommandUnitsProgress commandUnitsProgress) throws Exception {
        if (!(cfCommandRequestNG instanceof CfRollbackCommandRequestNG)) {
            throw new InvalidArgumentsException(Pair.of("cfCommandRequestNG", "Must be instance of CfRollbackCommandRequestNG"));
        }
        CfInBuiltVariablesUpdateValues updateValues = CfInBuiltVariablesUpdateValues.builder().build();
        LogCallback executionLogCallback = tasTaskHelperBase.getLogCallback(
                iLogStreamingTaskClient, cfCommandRequestNG.getCommandName(), true, commandUnitsProgress);
        executionLogCallback.saveExecutionLog(color("--------- Starting Rollback deployment", White, Bold));
        List<CfServiceData> cfServiceDataUpdated = new ArrayList<>();
        CfDeployCommandResult cfDeployCommandResult = CfDeployCommandResult.builder().build();
        CfDeployCommandResponseNG cfDeployCommandResponseNG =
                CfDeployCommandResponseNG.builder().build();

        CfRollbackCommandRequestNG cfRollbackCommandRequestNG = (CfRollbackCommandRequestNG) cfCommandRequestNG;

        File workingDirectory = null;
        Exception exception;
        try {
            // This will be CF_HOME for any cli related operations
            workingDirectory = cfCommandTaskHelperNG.generateWorkingDirectoryForDeployment();
            TasInfraConfig tasInfraConfig = cfRollbackCommandRequestNG.getTasInfraConfig();
            CloudFoundryConfig cfConfig = tasNgConfigMapper.mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(),
                    tasInfraConfig.getEncryptionDataDetails());
            CfRequestConfig cfRequestConfig =
                    buildCfRequestConfig(cfRollbackCommandRequestNG, workingDirectory, cfConfig);

            // Will be used if app autoscalar is configured
            CfAppAutoscalarRequestData autoscalarRequestData =
                    CfAppAutoscalarRequestData.builder()
                            .cfRequestConfig(cfRequestConfig)
                            .configPathVar(workingDirectory.getAbsolutePath())
                            .timeoutInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin() != null
                                    ? cfRollbackCommandRequestNG.getTimeoutIntervalInMin()
                                    : 10)
                            .build();
            // get Upsize Instance data
            List<CfServiceData> upsizeList =
                    cfRollbackCommandRequestNG.getInstanceData()
                            .stream()
                            .filter(cfServiceData -> {
                                if (cfServiceData.getDesiredCount() > cfServiceData.getPreviousCount()) {
                                    return true;
                                } else if (cfServiceData.getDesiredCount() == cfServiceData.getPreviousCount()) {
                                    String newApplicationName = null;
                                    if (!isNull(cfRollbackCommandRequestNG.getNewApplicationDetails())) {
                                        newApplicationName = cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName();
                                    }
                                    return cfServiceData.getDesiredCount() == 0 && (!cfServiceData.getName().equals(newApplicationName));
                                }
                                return false;
                            })
                            .collect(toList());

            // get Downsize Instance data
            List<CfServiceData> downSizeList =
                    cfRollbackCommandRequestNG.getInstanceData()
                            .stream()
                            .filter(cfServiceData -> cfServiceData.getDesiredCount() < cfServiceData.getPreviousCount())
                            .collect(toList());

            List<CfInternalInstanceElement> cfInstanceElements = new ArrayList<>();
            // During rollback, always upsize old ones
            cfCommandTaskHelperNG.upsizeListOfInstances(executionLogCallback, cfDeploymentManager, cfServiceDataUpdated,
                    cfRequestConfig, upsizeList, cfInstanceElements);
            restoreRoutesForOldApplication(cfRollbackCommandRequestNG, cfRequestConfig, executionLogCallback);
            // Enable autoscalar for older app, if it was disabled during deploy
            enableAutoscalarIfNeeded(upsizeList, autoscalarRequestData, executionLogCallback);
            executionLogCallback.saveExecutionLog("#---------- Upsize Application Successfully Completed", INFO, SUCCESS);

            executionLogCallback = tasTaskHelperBase.getLogCallback(
                    iLogStreamingTaskClient, Downsize, true, commandUnitsProgress);
            cfCommandTaskHelperNG.downSizeListOfInstances(executionLogCallback, cfServiceDataUpdated, cfRequestConfig,
                    updateNewAppName(cfRequestConfig, cfRollbackCommandRequestNG, downSizeList), cfRollbackCommandRequestNG,
                    autoscalarRequestData);
            unmapRoutesFromNewAppAfterDownsize(executionLogCallback, cfRollbackCommandRequestNG, cfRequestConfig);

            cfDeployCommandResponseNG.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
            cfDeployCommandResult.setInstanceDataUpdated(cfServiceDataUpdated);
            cfDeployCommandResult.getCfInstanceElements().addAll(cfInstanceElements);

            if (cfRollbackCommandRequestNG.isStandardBlueGreenWorkflow()) {
                deleteNewApp(cfRequestConfig, cfRollbackCommandRequestNG, executionLogCallback);
            } else {
                // for basic & canary
                if (isRollbackCompleted(cfRollbackCommandRequestNG, cfRequestConfig)) {
                    deleteNewApp(cfRequestConfig, cfRollbackCommandRequestNG, executionLogCallback);
                    updateValues = renameApps(cfRequestConfig, cfRollbackCommandRequestNG, executionLogCallback);
                }
            }
            cfDeployCommandResult.setUpdatedValues(updateValues);
            executionLogCallback.saveExecutionLog("\n\n--------- PCF Rollback completed successfully", INFO, SUCCESS);

        } catch (Exception e) {
            exception = e;
            logExceptionMessage(executionLogCallback, cfRollbackCommandRequestNG, exception);
            cfDeployCommandResponseNG.setCommandExecutionStatus(FAILURE);
            cfDeployCommandResult.setInstanceDataUpdated(cfServiceDataUpdated);
            cfDeployCommandResponseNG.setErrorMessage(ExceptionUtils.getMessage(exception));

        } finally {
            executionLogCallback = tasTaskHelperBase.getLogCallback(
                    iLogStreamingTaskClient, Wrapup, true, commandUnitsProgress);
            executionLogCallback.saveExecutionLog("#------- Deleting Temporary Files");
            if (workingDirectory != null) {
                try {
                    FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory.getAbsolutePath());
                    executionLogCallback.saveExecutionLog("Temporary Files Successfully deleted", INFO, SUCCESS);
                } catch (IOException e) {
                    log.warn("Failed to delete temp cf home folder", e);
                }
            }
        }
        cfDeployCommandResponseNG.setCfDeployCommandResult(cfDeployCommandResult);
        return cfDeployCommandResponseNG;
    }

    private CfRequestConfig buildCfRequestConfig(CfRollbackCommandRequestNG cfRollbackCommandRequestNG,
                                                 File workingDirectory, CloudFoundryConfig cfConfig) {
        return CfRequestConfig.builder()
                .userName(String.valueOf(cfConfig.getUserName()))
                .password(String.valueOf(cfConfig.getPassword()))
                .endpointUrl(cfConfig.getEndpointUrl())
                .orgName(cfRollbackCommandRequestNG.getTasInfraConfig().getOrganization())
                .spaceName(cfRollbackCommandRequestNG.getTasInfraConfig().getSpace())
                .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin() == null
                        ? 10
                        : cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
                .cfHomeDirPath(workingDirectory.getAbsolutePath())
                .useCFCLI(cfRollbackCommandRequestNG.isUseCfCLI())
                .cfCliPath(cfCommandTaskHelperNG.getCfCliPathOnDelegate(
                        cfRollbackCommandRequestNG.isUseCfCLI(), cfRollbackCommandRequestNG.getCfCliVersion()))
                .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
                .build();
    }

    private void deleteNewApp(CfRequestConfig cfRequestConfig, CfRollbackCommandRequestNG commandRollbackRequest,
                              LogCallback logCallback) throws PivotalClientApiException {
        // app downsized - to be deleted
        String cfAppNamePrefix = commandRollbackRequest.getCfAppNamePrefix();
        CfAppSetupTimeDetails newApp = commandRollbackRequest.getNewApplicationDetails();
        String newAppGuid = newApp.getApplicationGuid();
        String newAppName = newApp.getApplicationName();
        List<String> newApps = cfCommandTaskHelperNG.getAppNameBasedOnGuid(cfRequestConfig, cfAppNamePrefix, newAppGuid);

        if (newApps.isEmpty()) {
            logCallback.saveExecutionLog(
                    String.format("No new app found to delete with id - [%s] and name - [%s]", newAppGuid, newAppName));
        } else if (newApps.size() == 1) {
            String newAppToDelete = newApps.get(0);
            cfRequestConfig.setApplicationName(newAppToDelete);
            logCallback.saveExecutionLog("Deleting application " + encodeColor(newAppToDelete));
            cfDeploymentManager.deleteApplication(cfRequestConfig);
        } else {
            String newAppToDelete = newApps.get(0);
            String message = String.format(
                    "Found [%d] applications with with id - [%s] and name - [%s]. Skipping new app deletion. Kindly delete the invalid app manually",
                    newApps.size(), newAppGuid, newAppToDelete);
            logCallback.saveExecutionLog(message, WARN);
        }
    }

    private List<CfServiceData> updateNewAppName(CfRequestConfig cfRequestConfig,
                                                 CfRollbackCommandRequestNG commandRollbackRequest, List<CfServiceData> downSizeList)
            throws PivotalClientApiException {
        String cfAppNamePrefix = commandRollbackRequest.getCfAppNamePrefix();

        for (CfServiceData data : downSizeList) {
            List<String> apps =
                    cfCommandTaskHelperNG.getAppNameBasedOnGuid(cfRequestConfig, cfAppNamePrefix, data.getId());
            data.setName(isEmpty(apps) ? data.getName() : apps.get(0));
        }
        return downSizeList;
    }

    private boolean isRollbackCompleted(CfRollbackCommandRequestNG commandRollbackRequest, CfRequestConfig cfRequestConfig)
            throws PivotalClientApiException {
        // app downsized - to be deleted
        CfAppSetupTimeDetails newApp = commandRollbackRequest.getNewApplicationDetails();
        boolean rollbackCompleted =
                instanceCountMatches(newApp.getApplicationName(), newApp.getInitialInstanceCount(), cfRequestConfig);
        // app upsized - to be renamed
        List<CfAppSetupTimeDetails> prevActiveApps = commandRollbackRequest.getAppsToBeDownSized();
        if (!EmptyPredicate.isEmpty(prevActiveApps)) {
            CfAppSetupTimeDetails prevActiveApp = prevActiveApps.get(0);
            rollbackCompleted = rollbackCompleted
                    && instanceCountMatches(
                    prevActiveApp.getApplicationName(), prevActiveApp.getInitialInstanceCount(), cfRequestConfig);
        }

        return rollbackCompleted;
    }

    private boolean instanceCountMatches(String applicationName, Integer expectedInstanceCount,
                                         CfRequestConfig cfRequestConfig) throws PivotalClientApiException {
        cfRequestConfig.setApplicationName(applicationName);
        ApplicationDetail application = cfDeploymentManager.getApplicationByName(cfRequestConfig);
        return null != application && application.getInstances().equals(expectedInstanceCount);
    }

    private CfInBuiltVariablesUpdateValues renameApps(CfRequestConfig cfRequestConfig,
                                                      CfRollbackCommandRequestNG commandRollbackRequest, LogCallback logCallback) throws PivotalClientApiException {
        CfInBuiltVariablesUpdateValues updateValues = CfInBuiltVariablesUpdateValues.builder().build();

        if (commandRollbackRequest.isNonVersioning()) {
            logCallback.saveExecutionLog("\n# Reverting app names");
            // app upsized - to be renamed
            List<CfAppSetupTimeDetails> prevActiveApps = commandRollbackRequest.getAppsToBeDownSized();
            // previous inactive app - to be marked inactive again
            CfAppSetupTimeDetails prevInactive = commandRollbackRequest.getExistingInActiveApplicationDetails();

            if (!EmptyPredicate.isEmpty(prevActiveApps)) {
                CfAppSetupTimeDetails prevActiveApp = prevActiveApps.get(0);
                // todo: changing new name
                String newName = constructActiveAppName(commandRollbackRequest.getCfAppNamePrefix(), -1, true);
                cfDeploymentManager.renameApplication(new CfRenameRequest(cfRequestConfig, prevActiveApp.getApplicationGuid(),
                                prevActiveApp.getApplicationName(), newName),
                        logCallback);
                updateValues.setOldAppGuid(prevActiveApp.getApplicationGuid());
                updateValues.setOldAppName(newName);
                updateValues.setActiveAppName(newName);
            }

            if (null != prevInactive && isNotEmpty(prevInactive.getApplicationName())) {
                String newName = constructInActiveAppName(commandRollbackRequest.getCfAppNamePrefix(), -1, true);
                cfDeploymentManager.renameApplication(new CfRenameRequest(cfRequestConfig, prevInactive.getApplicationGuid(),
                                prevInactive.getApplicationName(), newName),
                        logCallback);
                updateValues.setInActiveAppName(newName);
            }

            logCallback.saveExecutionLog("# App names reverted successfully");
        }

        if (commandRollbackRequest.isVersioningChanged()) {
            logCallback.saveExecutionLog(getVersionChangeMessage(!commandRollbackRequest.isNonVersioning()));

            List<ApplicationSummary> releases =
                    cfDeploymentManager.getPreviousReleases(cfRequestConfig, commandRollbackRequest.getCfAppNamePrefix());
            ApplicationSummary activeApplication = cfCommandTaskHelperNG.findActiveApplication(
                    logCallback, commandRollbackRequest.isStandardBlueGreenWorkflow(), cfRequestConfig, releases);
            ApplicationSummary inactiveApplication = cfCommandTaskHelperNG.getMostRecentInactiveApplication(logCallback,
                    commandRollbackRequest.isStandardBlueGreenWorkflow(), activeApplication, releases, cfRequestConfig);
            cfCommandTaskHelperNG.resetState(releases, activeApplication, inactiveApplication,
                    commandRollbackRequest.getCfAppNamePrefix(), cfRequestConfig, !commandRollbackRequest.isNonVersioning(), null,
                    commandRollbackRequest.getActiveAppRevision(), logCallback, updateValues);

            logCallback.saveExecutionLog(getVersionChangeMessage(!commandRollbackRequest.isNonVersioning()) + " completed");
        }
        return updateValues;
    }

    private void logExceptionMessage(
            LogCallback executionLogCallback, CfRollbackCommandRequestNG commandRollbackRequest, Exception exception) {
        log.error(PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX + "Exception in processing CF Rollback task [{}]",
                commandRollbackRequest, exception);
        executionLogCallback.saveExecutionLog("\n\n--------- CF Rollback failed to complete successfully", ERROR, FAILURE);
        Misc.logAllMessages(exception, executionLogCallback);
    }

    @VisibleForTesting
    void enableAutoscalarIfNeeded(List<CfServiceData> upsizeList, CfAppAutoscalarRequestData autoscalarRequestData,
                                  LogCallback logCallback) throws PivotalClientApiException {
        for (CfServiceData cfServiceData : upsizeList) {
            if (!cfServiceData.isDisableAutoscalarPerformed()) {
                continue;
            }

            autoscalarRequestData.setApplicationName(cfServiceData.getName());
            autoscalarRequestData.setApplicationGuid(cfServiceData.getId());
            autoscalarRequestData.setExpectedEnabled(false);
            cfDeploymentManager.changeAutoscalarState(autoscalarRequestData, logCallback, true);
        }
    }

    /**
     * This is for non BG deployment.
     * Older app will be mapped to routes it was originally mapped to.
     * In deploy state, once older app is downsized to 0, we remove routeMaps,
     * this step will restore them.
     */
    @VisibleForTesting
    void restoreRoutesForOldApplication(CfRollbackCommandRequestNG commandRollbackRequest, CfRequestConfig cfRequestConfig,
                                        LogCallback executionLogCallback) throws PivotalClientApiException {
        if (commandRollbackRequest.isStandardBlueGreenWorkflow()
                || EmptyPredicate.isEmpty(commandRollbackRequest.getAppsToBeDownSized())) {
            return;
        }

        CfAppSetupTimeDetails cfAppSetupTimeDetails = commandRollbackRequest.getAppsToBeDownSized().get(0);

        if (cfAppSetupTimeDetails != null) {
            cfRequestConfig.setApplicationName(cfAppSetupTimeDetails.getApplicationName());
            ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);

            if (EmptyPredicate.isEmpty(cfAppSetupTimeDetails.getUrls())) {
                return;
            }

            if (EmptyPredicate.isEmpty(applicationDetail.getUrls())
                    || !cfAppSetupTimeDetails.getUrls().containsAll(applicationDetail.getUrls())) {
                cfCommandTaskHelperNG.mapRouteMaps(cfAppSetupTimeDetails.getApplicationName(),
                        cfAppSetupTimeDetails.getUrls(), cfRequestConfig, executionLogCallback);
            }
        }
    }

    @VisibleForTesting
    void unmapRoutesFromNewAppAfterDownsize(LogCallback executionLogCallback,
                                            CfRollbackCommandRequestNG commandRollbackRequest, CfRequestConfig cfRequestConfig)
            throws PivotalClientApiException {
        if (commandRollbackRequest.isStandardBlueGreenWorkflow()
                || commandRollbackRequest.getNewApplicationDetails() == null
                || isBlank(commandRollbackRequest.getNewApplicationDetails().getApplicationName())) {
            return;
        }

        cfRequestConfig.setApplicationName(commandRollbackRequest.getNewApplicationDetails().getApplicationName());
        ApplicationDetail appDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);

        if (appDetail.getInstances() == 0) {
            cfCommandTaskHelperNG.unmapExistingRouteMaps(appDetail, cfRequestConfig, executionLogCallback);
        }
    }
}


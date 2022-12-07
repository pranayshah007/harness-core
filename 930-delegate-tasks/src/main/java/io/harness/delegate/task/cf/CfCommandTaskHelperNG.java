package io.harness.delegate.task.cf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.WARN;
import static io.harness.pcf.PcfUtils.encodeColor;
import static io.harness.pcf.model.CfConstants.CF_ARTIFACT_DOWNLOAD_DIR_PATH;
import static io.harness.pcf.model.CfConstants.REPOSITORY_DIR_PATH;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.pcf.CfAppRenameInfo;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.cf.apprenaming.AppRenamingOperator;
import io.harness.delegate.task.pcf.request.CfDeployCommandRequestNG;
import io.harness.delegate.task.pcf.request.CfRollbackCommandRequestNG;
import io.harness.delegate.utils.CFLogCallbackFormatter;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.CfRequestConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.Deque;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;
@OwnedBy(CDP)
@Singleton
@Slf4j
public class CfCommandTaskHelperNG {
    public static final String DELIMITER = "__";
    public static final String APPLICATION = "APPLICATION: ";

    @Inject
    PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper;
    @Inject
    CfDeploymentManager cfDeploymentManager;

    public int getRevisionFromReleaseName(String name) {
        return pcfCommandTaskBaseHelper.getRevisionFromReleaseName(name);
    }


    public File generateWorkingDirectoryForDeployment() throws IOException {
      String workingDirecotry = UUIDGenerator.generateUuid();
      createDirectoryIfDoesNotExist(REPOSITORY_DIR_PATH);
      createDirectoryIfDoesNotExist(CF_ARTIFACT_DOWNLOAD_DIR_PATH);
      String workingDir = CF_ARTIFACT_DOWNLOAD_DIR_PATH + "/" + workingDirecotry;
      createDirectoryIfDoesNotExist(workingDir);
      return new File(workingDir);
    }

    public ApplicationDetail getNewlyCreatedApplication(CfRequestConfig cfRequestConfig,
                                                        CfDeployCommandRequestNG cfDeployCommandRequestNG,
                                                        CfDeploymentManager cfDeploymentManager) throws PivotalClientApiException {
        cfRequestConfig.setApplicationName(cfDeployCommandRequestNG.getNewReleaseName());
        cfRequestConfig.setDesiredCount(cfDeployCommandRequestNG.getUpsizeCount());
        return cfDeploymentManager.getApplicationByName(cfRequestConfig);
    }

    public void downsizePreviousReleases(CfDeployCommandRequestNG cfDeployCommandRequestNG,
                                         CfRequestConfig cfRequestConfig,
                                         LogCallback executionLogCallback,
                                         List<CfServiceData> cfServiceDataUpdated,
                                         Integer updateCount, List<CfInternalInstanceElement> pcfInstanceElements,
                                         CfAppAutoscalarRequestData appAutoscalarRequestData) throws PivotalClientApiException {
        if (cfDeployCommandRequestNG.isStandardBlueGreen()) {
            executionLogCallback.saveExecutionLog("# BG Deployment. Old Application will not be downsized.");
            return;
        }

        executionLogCallback.saveExecutionLog("# Downsizing previous application version/s");

        CfAppSetupTimeDetails downsizeAppDetails = cfDeployCommandRequestNG.getDownsizeAppDetail();
        if (downsizeAppDetails == null) {
            executionLogCallback.saveExecutionLog("# No Application is available for downsize");
            return;
        }

        cfRequestConfig.setApplicationName(downsizeAppDetails.getApplicationName());
        ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);
        executionLogCallback.saveExecutionLog(CFLogCallbackFormatter.formatAppInstancesState(
                applicationDetail.getName(), applicationDetail.getInstances(), updateCount));

        CfServiceData cfServiceData = CfServiceData.builder()
                .name(applicationDetail.getName())
                .id(applicationDetail.getId())
                .previousCount(applicationDetail.getInstances())
                .desiredCount(updateCount)
                .build();

        cfServiceDataUpdated.add(cfServiceData);

        // We want to downsize the app if the update count is equal to zero(in case web process is zero)
        if (updateCount >= applicationDetail.getInstances() && updateCount != 0) {
            executionLogCallback.saveExecutionLog("# No Downsize was required.\n");
            return;
        }

        // First disable App Auto scalar if attached with application
        if (cfDeployCommandRequestNG.isUseAppAutoscalar()) {
            appAutoscalarRequestData.setApplicationName(applicationDetail.getName());
            appAutoscalarRequestData.setApplicationGuid(applicationDetail.getId());
            appAutoscalarRequestData.setExpectedEnabled(true);
            boolean autoscalarStateChanged = pcfCommandTaskBaseHelper.disableAutoscalarSafe(appAutoscalarRequestData, executionLogCallback);
            cfServiceData.setDisableAutoscalarPerformed(autoscalarStateChanged);
        }

        ApplicationDetail applicationDetailAfterResize =
                downSize(cfServiceData, executionLogCallback, cfRequestConfig, cfDeploymentManager);

        // Application that is downsized
        if (EmptyPredicate.isNotEmpty(applicationDetailAfterResize.getInstanceDetails())) {
            applicationDetailAfterResize.getInstanceDetails().forEach(instance
                    -> pcfInstanceElements.add(CfInternalInstanceElement.builder()
                    .applicationId(applicationDetailAfterResize.getId())
                    .displayName(applicationDetailAfterResize.getName())
                    .instanceIndex(instance.getIndex())
                    .isUpsize(false)
                    .build()));
        }
        unmapRoutesIfAppDownsizedToZero(cfDeployCommandRequestNG, cfRequestConfig, executionLogCallback);

    }

    ApplicationDetail downSize(CfServiceData cfServiceData, LogCallback executionLogCallback,
                               CfRequestConfig cfRequestConfig, CfDeploymentManager pcfDeploymentManager) throws PivotalClientApiException {
        cfRequestConfig.setApplicationName(cfServiceData.getName());
        cfRequestConfig.setDesiredCount(cfServiceData.getDesiredCount());

        ApplicationDetail applicationDetail = pcfDeploymentManager.resizeApplication(cfRequestConfig);

        executionLogCallback.saveExecutionLog("# Downsizing successful");
        executionLogCallback.saveExecutionLog("\n# App details after downsize:");
        pcfCommandTaskBaseHelper.printApplicationDetail(applicationDetail, executionLogCallback);
        return applicationDetail;
    }
    void unmapRoutesIfAppDownsizedToZero(CfDeployCommandRequestNG cfCommandDeployRequest, CfRequestConfig cfRequestConfig,
                                         LogCallback executionLogCallback) throws PivotalClientApiException {
        if (cfCommandDeployRequest.isStandardBlueGreen() || cfCommandDeployRequest.getDownsizeAppDetail() == null
                || isBlank(cfCommandDeployRequest.getDownsizeAppDetail().getApplicationName())) {
            return;
        }

        cfRequestConfig.setApplicationName(cfCommandDeployRequest.getDownsizeAppDetail().getApplicationName());
        ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);

        if (applicationDetail.getInstances() == 0) {
            pcfCommandTaskBaseHelper.unmapExistingRouteMaps(applicationDetail, cfRequestConfig, executionLogCallback);
        }
    }

    public void upsizeNewApplication(LogCallback executionLogCallback, CfDeployCommandRequestNG cfCommandDeployRequest, List<CfServiceData> cfServiceDataUpdated,
                                     CfRequestConfig cfRequestConfig, ApplicationDetail details, List<CfInternalInstanceElement> pcfInstanceElements,
                                     CfAppAutoscalarRequestData cfAppAutoscalarRequestData) throws PivotalClientApiException, IOException {
        executionLogCallback.saveExecutionLog(color("# Upsizing new application:", White, Bold));

        executionLogCallback.saveExecutionLog(CFLogCallbackFormatter.formatAppInstancesState(
            details.getName(), details.getInstances(), cfCommandDeployRequest.getUpsizeCount()));

        // Upscale new app
        cfRequestConfig.setApplicationName(cfCommandDeployRequest.getNewReleaseName());
        cfRequestConfig.setDesiredCount(cfCommandDeployRequest.getUpsizeCount());

        // perform upsize
        pcfCommandTaskBaseHelper.upsizeInstance(
                cfRequestConfig, cfDeploymentManager, executionLogCallback, cfServiceDataUpdated, pcfInstanceElements);
        configureAutoscalarIfNeeded(cfCommandDeployRequest, details, cfAppAutoscalarRequestData, executionLogCallback);
    }

    public void createYamlFileLocally(String filePath, String autoscalarManifestYml) throws IOException {
        pcfCommandTaskBaseHelper.createYamlFileLocally(filePath, autoscalarManifestYml);
    }
    private void configureAutoscalarIfNeeded(CfDeployCommandRequestNG cfCommandDeployRequest, ApplicationDetail applicationDetail,
                                             CfAppAutoscalarRequestData appAutoscalarRequestData, LogCallback executionLogCallback)
            throws PivotalClientApiException, IOException {
      if (cfCommandDeployRequest.isUseAppAutoscalar() && cfCommandDeployRequest.getPcfManifestsPackage() != null
          && isNotEmpty(cfCommandDeployRequest.getPcfManifestsPackage().getAutoscalarManifestYml())
          && cfCommandDeployRequest.getMaxCount() <= cfCommandDeployRequest.getUpsizeCount()) {
        // This is autoscalar file inside workingDirectory
        String filePath =
            appAutoscalarRequestData.getConfigPathVar() + "/autoscalar_" + System.currentTimeMillis() + ".yml";
        createYamlFileLocally(filePath, cfCommandDeployRequest.getPcfManifestsPackage().getAutoscalarManifestYml());

        // upload autoscalar config
        appAutoscalarRequestData.setApplicationName(applicationDetail.getName());
        appAutoscalarRequestData.setApplicationGuid(applicationDetail.getId());
        appAutoscalarRequestData.setTimeoutInMins(cfCommandDeployRequest.getTimeoutIntervalInMin());
        appAutoscalarRequestData.setAutoscalarFilePath(filePath);
        cfDeploymentManager.performConfigureAutoscalar(appAutoscalarRequestData, executionLogCallback);
      }
    }

    public String getCfCliPathOnDelegate(boolean useCfCLI, CfCliVersion cfCliVersion) {
        return pcfCommandTaskBaseHelper.getCfCliPathOnDelegate(useCfCLI, cfCliVersion);
    }

    public void upsizeListOfInstancesAndRestoreRoutes(LogCallback executionLogCallback,
        CfDeploymentManager cfDeploymentManager, List<CfServiceData> cfServiceDataUpdated,
        CfRequestConfig cfRequestConfig, List<CfServiceData> upsizeList,
        List<CfInternalInstanceElement> cfInstanceElements, CfRollbackCommandRequestNG cfRollbackCommandRequestNG)
        throws PivotalClientApiException {
      pcfCommandTaskBaseHelper.upsizeListOfInstances(executionLogCallback, cfDeploymentManager, cfServiceDataUpdated,
          cfRequestConfig, upsizeList, cfInstanceElements);
      restoreRoutesForOldApplication(cfRollbackCommandRequestNG, cfRequestConfig, executionLogCallback);
    }

    public void upsizeListOfInstances(LogCallback executionLogCallback,
                                                      CfDeploymentManager cfDeploymentManager, List<CfServiceData> cfServiceDataUpdated,
                                                      CfRequestConfig cfRequestConfig, List<CfServiceData> upsizeList,
                                                      List<CfInternalInstanceElement> cfInstanceElements, CfRollbackCommandRequestNG cfRollbackCommandRequestNG)
            throws PivotalClientApiException {
        pcfCommandTaskBaseHelper.upsizeListOfInstances(executionLogCallback, cfDeploymentManager, cfServiceDataUpdated,
                cfRequestConfig, upsizeList, cfInstanceElements);
    }

    public List<String> getAppNameBasedOnGuid(CfRequestConfig cfRequestConfig, String cfAppNamePrefix, String appGuid)
        throws PivotalClientApiException {
      List<ApplicationSummary> previousReleases =
          cfDeploymentManager.getPreviousReleasesBasicAndCanaryNG(cfRequestConfig, cfAppNamePrefix);
      return previousReleases.stream()
          .filter(app -> app.getId().equalsIgnoreCase(appGuid))
          .map(ApplicationSummary::getName)
          .collect(toList());
    }

    public List<String> getAppNameBasedOnGuidForBlueGreenDeployment(CfRequestConfig cfRequestConfig, String cfAppNamePrefix, String appGuid)
            throws PivotalClientApiException {
        List<ApplicationSummary> previousReleases =
                cfDeploymentManager.getPreviousReleases(cfRequestConfig, cfAppNamePrefix);
        return previousReleases.stream()
                .filter(app -> app.getId().equalsIgnoreCase(appGuid))
                .map(ApplicationSummary::getName)
                .collect(toList());
    }

    public void downSizeListOfInstances(LogCallback executionLogCallback,
                                                      List<CfServiceData> cfServiceDataUpdated, CfRequestConfig cfRequestConfig, List<CfServiceData> downSizeList,
                                                      CfRollbackCommandRequestNG cfRollbackCommandRequestNG, CfAppAutoscalarRequestData autoscalarRequestData)
            throws PivotalClientApiException {
        executionLogCallback.saveExecutionLog("\n");
        for (CfServiceData cfServiceData : downSizeList) {
            executionLogCallback.saveExecutionLog(color("# Downsizing application:", White, Bold));
            executionLogCallback.saveExecutionLog(CFLogCallbackFormatter.formatAppInstancesState(
                    cfServiceData.getName(), cfServiceData.getPreviousCount(), cfServiceData.getDesiredCount()));

            cfRequestConfig.setApplicationName(cfServiceData.getName());
            cfRequestConfig.setDesiredCount(cfServiceData.getDesiredCount());

            if (cfRollbackCommandRequestNG.isUseAppAutoscalar()) {
                ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);
                autoscalarRequestData.setApplicationName(applicationDetail.getName());
                autoscalarRequestData.setApplicationGuid(applicationDetail.getId());
                autoscalarRequestData.setExpectedEnabled(true);
                pcfCommandTaskBaseHelper.disableAutoscalarSafe(autoscalarRequestData, executionLogCallback);
            }

            downSize(cfServiceData, executionLogCallback, cfRequestConfig, cfDeploymentManager);

            cfServiceDataUpdated.add(cfServiceData);
        }
    }

    public void downSizeListOfInstancesAndUnmapRoutes(LogCallback executionLogCallback,
        List<CfServiceData> cfServiceDataUpdated, CfRequestConfig cfRequestConfig, List<CfServiceData> downSizeList,
        CfRollbackCommandRequestNG cfRollbackCommandRequestNG, CfAppAutoscalarRequestData autoscalarRequestData)
        throws PivotalClientApiException {
      executionLogCallback.saveExecutionLog("\n");
      for (CfServiceData cfServiceData : downSizeList) {
        executionLogCallback.saveExecutionLog(color("# Downsizing application:", White, Bold));
        executionLogCallback.saveExecutionLog(CFLogCallbackFormatter.formatAppInstancesState(
            cfServiceData.getName(), cfServiceData.getPreviousCount(), cfServiceData.getDesiredCount()));

        cfRequestConfig.setApplicationName(cfServiceData.getName());
        cfRequestConfig.setDesiredCount(cfServiceData.getDesiredCount());

        if (cfRollbackCommandRequestNG.isUseAppAutoscalar()) {
          ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);
          autoscalarRequestData.setApplicationName(applicationDetail.getName());
          autoscalarRequestData.setApplicationGuid(applicationDetail.getId());
          autoscalarRequestData.setExpectedEnabled(true);
          pcfCommandTaskBaseHelper.disableAutoscalarSafe(autoscalarRequestData, executionLogCallback);
        }

        downSize(cfServiceData, executionLogCallback, cfRequestConfig, cfDeploymentManager);

        cfServiceDataUpdated.add(cfServiceData);
      }
      unmapRoutesFromNewAppAfterDownsize(executionLogCallback, cfRollbackCommandRequestNG, cfRequestConfig);
    }

    public ApplicationSummary findActiveApplication(LogCallback logCallback,
                                                    boolean standardBlueGreenWorkflow,
                                                    CfRequestConfig cfRequestConfig,
                                                    List<ApplicationSummary> releases) throws PivotalClientApiException {
        return pcfCommandTaskBaseHelper.findActiveApplication(logCallback, standardBlueGreenWorkflow, cfRequestConfig, releases);
    }

    public void mapRouteMaps(String applicationName, List<String> urls, CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) throws PivotalClientApiException {
        pcfCommandTaskBaseHelper.mapRouteMaps(applicationName, urls, cfRequestConfig, executionLogCallback);
    }

    public void unmapRouteMaps(String applicationName, List<String> urls, CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) throws PivotalClientApiException {
        pcfCommandTaskBaseHelper.unmapRouteMaps(applicationName, urls, cfRequestConfig, executionLogCallback);
    }

    public ApplicationSummary getMostRecentInactiveApplication(LogCallback logCallback, boolean standardBlueGreenWorkflow, ApplicationSummary activeApplication,
                                                               List<ApplicationSummary> releases, CfRequestConfig cfRequestConfig) throws PivotalClientApiException {
        return pcfCommandTaskBaseHelper.getMostRecentInactiveApplication(logCallback,standardBlueGreenWorkflow, activeApplication, releases, cfRequestConfig);
    }

    public void resetState(List<ApplicationSummary> releases, ApplicationSummary activeApplication,
        ApplicationSummary inactiveApplication, String cfAppNamePrefix, CfRequestConfig cfRequestConfig, boolean b,
        Object o, Integer activeAppRevision, LogCallback logCallback, CfInBuiltVariablesUpdateValues updateValues)
        throws PivotalClientApiException {
      pcfCommandTaskBaseHelper.resetState(releases, activeApplication, inactiveApplication, cfAppNamePrefix,
          cfRequestConfig, b, (Deque<CfAppRenameInfo>) o, activeAppRevision, logCallback, updateValues);
    }

    public boolean disableAutoscalar(CfAppAutoscalarRequestData pcfAppAutoscalarRequestData,
                                     LogCallback executionLogCallback) throws PivotalClientApiException {
        return cfDeploymentManager.changeAutoscalarState(pcfAppAutoscalarRequestData, executionLogCallback, false);
    }

    public boolean disableAutoscalarSafe(
            CfAppAutoscalarRequestData pcfAppAutoscalarRequestData, LogCallback executionLogCallback) {
        boolean autoscalarStateChanged = false;
        try {
            autoscalarStateChanged = disableAutoscalar(pcfAppAutoscalarRequestData, executionLogCallback);
        } catch (PivotalClientApiException e) {
            executionLogCallback.saveExecutionLog(
                    new StringBuilder()
                            .append("# Error while disabling autoscaling for: ")
                            .append(encodeColor(pcfAppAutoscalarRequestData.getApplicationName()))
                            .append(", ")
                            .append(e)
                            .append(", Continuing with the deployment, please disable autoscaler from the pcf portal\n")
                            .toString(),
                    ERROR);
        }
        return autoscalarStateChanged;
    }

    public CfInBuiltVariablesUpdateValues performAppRenaming(AppRenamingOperator.NamingTransition transition,
                                                              CfRouteUpdateRequestConfigData cfRouteUpdateConfigData, CfRequestConfig cfRequestConfig,
                                                              LogCallback executionLogCallback) throws PivotalClientApiException {
        AppRenamingOperator renamingOperator = AppRenamingOperator.of(transition);
        return renamingOperator.renameApp(
                cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback, cfDeploymentManager, pcfCommandTaskBaseHelper);
    }

    public List<CfServiceData> getUpsizeListForRollback(CfRollbackCommandRequestNG cfRollbackCommandRequestNG) {
      return cfRollbackCommandRequestNG.getInstanceData()
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
    }

    public void restoreRoutesForOldApplication(CfRollbackCommandRequestNG commandRollbackRequest,
        CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) throws PivotalClientApiException {
      if (isNull(commandRollbackRequest.getOldApplicationDetails())) {
        return;
      }

      CfAppSetupTimeDetails cfAppSetupTimeDetails = commandRollbackRequest.getOldApplicationDetails();
      cfRequestConfig.setApplicationName(cfAppSetupTimeDetails.getApplicationName());
      ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);

      if (EmptyPredicate.isEmpty(cfAppSetupTimeDetails.getUrls())) {
        return;
      }

      if (EmptyPredicate.isEmpty(applicationDetail.getUrls())
          || !cfAppSetupTimeDetails.getUrls().containsAll(applicationDetail.getUrls())) {
        mapRouteMaps(cfAppSetupTimeDetails.getApplicationName(), cfAppSetupTimeDetails.getUrls(), cfRequestConfig,
            executionLogCallback);
      }
    }
    void unmapRoutesFromNewAppAfterDownsize(LogCallback executionLogCallback,
        CfRollbackCommandRequestNG commandRollbackRequest, CfRequestConfig cfRequestConfig)
        throws PivotalClientApiException {
      if (commandRollbackRequest.getNewApplicationDetails() == null
          || isBlank(commandRollbackRequest.getNewApplicationDetails().getApplicationName())) {
        return;
      }

      cfRequestConfig.setApplicationName(commandRollbackRequest.getNewApplicationDetails().getApplicationName());
      ApplicationDetail appDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);

      if (appDetail.getInstances() == 0) {
        pcfCommandTaskBaseHelper.unmapExistingRouteMaps(appDetail, cfRequestConfig, executionLogCallback);
      }
    }
    public void enableAutoscalerIfNeeded(List<CfServiceData> upsizeList,
        CfAppAutoscalarRequestData autoscalarRequestData, LogCallback logCallback) throws PivotalClientApiException {
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

    public void deleteNewApp(CfRequestConfig cfRequestConfig, CfRollbackCommandRequestNG commandRollbackRequest,
        LogCallback logCallback) throws PivotalClientApiException {
      // app downsized - to be deleted
      String cfAppNamePrefix = commandRollbackRequest.getCfAppNamePrefix();
      CfAppSetupTimeDetails newApp = commandRollbackRequest.getNewApplicationDetails();
      String newAppGuid = newApp.getApplicationGuid();
      String newAppName = newApp.getApplicationName();
      List<String> newApps = getAppNameBasedOnGuid(cfRequestConfig, cfAppNamePrefix, newAppGuid);

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
}

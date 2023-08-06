/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INVALID_INFRA_STATE;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.pcf.PcfUtils.encodeColor;
import static io.harness.pcf.PcfUtils.getRevisionFromServiceName;
import static io.harness.pcf.model.PcfConstants.BUILDPACKS_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.BUILDPACK_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.COMMAND_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.DISK_QUOTA_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.DOCKER_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.DOMAINS_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.DOMAIN_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.ENV_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.HARNESS__INACTIVE__IDENTIFIER;
import static io.harness.pcf.model.PcfConstants.HARNESS__STATUS__IDENTIFIER;
import static io.harness.pcf.model.PcfConstants.HEALTH_CHECK_HTTP_ENDPOINT_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.HEALTH_CHECK_INVOCATION_TIMEOUT_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.HEALTH_CHECK_TYPE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.HOSTS_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.HOST_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.INSTANCE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.MEMORY_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.METADATA_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.NAME_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.NO_HOSTNAME_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.NO_ROUTE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.PATH_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.PCF_ARTIFACT_DOWNLOAD_DIR_PATH;
import static io.harness.pcf.model.PcfConstants.PROCESSES_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.RANDOM_ROUTE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.REPOSITORY_DIR_PATH;
import static io.harness.pcf.model.PcfConstants.ROUTES_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.ROUTE_PATH_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.SERVICES_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.SIDE_CARS_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.STACK_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.TIMEOUT_MANIFEST_YML_ELEMENT;

import static software.wings.beans.LogColor.Gray;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.pcf.CfAppRenameInfo;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails.CfAppSetupTimeDetailsBuilder;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.cf.apprenaming.AppNamingStrategy;
import io.harness.delegate.task.pcf.exception.InvalidPcfStateException;
import io.harness.delegate.task.pcf.request.CfCommandDeployRequest;
import io.harness.delegate.task.pcf.request.CfCommandRollbackRequest;
import io.harness.delegate.utils.CFLogCallbackFormatter;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.FileCreationException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfCliDelegateResolver;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PcfUtils;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.CfCreateApplicationRequestData;
import io.harness.pcf.model.CfRenameRequest;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.PcfConstants;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.jetbrains.annotations.NotNull;

/**
 * Stateles helper class
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PCF, HarnessModuleComponent.CDS_GITX, HarnessModuleComponent.CDS_FIRST_GEN,
        HarnessModuleComponent.CDS_SERVERLESS})
@Singleton
@Slf4j
@OwnedBy(CDP)
public class PcfCommandTaskBaseHelper {
  public static final String DELIMITER = "__";
  public static final String APPLICATION = "APPLICATION: ";

  @Inject private CfDeploymentManager pcfDeploymentManager;
  @Inject private CfCliDelegateResolver cfCliDelegateResolver;

  public void unmapExistingRouteMaps(ApplicationDetail applicationDetail, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    executionLogCallback.saveExecutionLog(color("\n# Unmapping routes", White, Bold));
    executionLogCallback.saveExecutionLog(APPLICATION + encodeColor(applicationDetail.getName()));
    executionLogCallback.saveExecutionLog("ROUTE: \n[" + getRouteString(applicationDetail.getUrls()));
    // map
    cfRequestConfig.setApplicationName(applicationDetail.getName());
    pcfDeploymentManager.unmapRouteMapForApplication(
        cfRequestConfig, applicationDetail.getUrls(), executionLogCallback);
  }

  public File createYamlFileLocally(String filePath, String content) throws IOException {
    File file = new File(filePath);
    return writeToManifestFile(content, file);
  }

  /**
   * This is called from Deploy (Resize) phase.
   */
  public void upsizeNewApplication(LogCallback executionLogCallback, CfCommandDeployRequest cfCommandDeployRequest,
      List<CfServiceData> cfServiceDataUpdated, CfRequestConfig cfRequestConfig, ApplicationDetail details,
      List<CfInternalInstanceElement> pcfInstanceElements) throws PivotalClientApiException {
    executionLogCallback.saveExecutionLog(color("# Upsizing new application:", White, Bold));

    executionLogCallback.saveExecutionLog(CFLogCallbackFormatter.formatAppInstancesState(
        details.getName(), details.getInstances(), cfCommandDeployRequest.getUpdateCount()));

    // Upscale new app
    cfRequestConfig.setApplicationName(cfCommandDeployRequest.getNewReleaseName());
    cfRequestConfig.setDesiredCount(cfCommandDeployRequest.getUpdateCount());

    // perform upsize
    upsizeInstance(
        cfRequestConfig, pcfDeploymentManager, executionLogCallback, cfServiceDataUpdated, pcfInstanceElements);
  }

  public void upsizeInstance(CfRequestConfig cfRequestConfig, CfDeploymentManager pcfDeploymentManager,
      LogCallback executionLogCallback, List<CfServiceData> cfServiceDataUpdated,
      List<CfInternalInstanceElement> pcfInstanceElements) throws PivotalClientApiException {
    // Get application details before upsize
    ApplicationDetail detailsBeforeUpsize = pcfDeploymentManager.getApplicationByName(cfRequestConfig);
    StringBuilder sb = new StringBuilder();

    // create pcfServiceData having all details of this upsize operation
    cfServiceDataUpdated.add(CfServiceData.builder()
                                 .previousCount(detailsBeforeUpsize.getInstances())
                                 .desiredCount(cfRequestConfig.getDesiredCount())
                                 .name(cfRequestConfig.getApplicationName())
                                 .id(detailsBeforeUpsize.getId())
                                 .build());

    // upsize application
    ApplicationDetail detailsAfterUpsize =
        pcfDeploymentManager.upsizeApplicationWithSteadyStateCheck(cfRequestConfig, executionLogCallback);
    executionLogCallback.saveExecutionLog(sb.append("# Application upsized successfully ").toString());

    List<InstanceDetail> newUpsizedInstances = filterNewUpsizedAppInstances(detailsBeforeUpsize, detailsAfterUpsize);
    newUpsizedInstances.forEach(instance
        -> pcfInstanceElements.add(CfInternalInstanceElement.builder()
                                       .uuid(detailsAfterUpsize.getId() + instance.getIndex())
                                       .applicationId(detailsAfterUpsize.getId())
                                       .displayName(detailsAfterUpsize.getName())
                                       .instanceIndex(instance.getIndex())
                                       .isUpsize(true)
                                       .build()));

    // Instance token is ApplicationGuid:InstanceIndex, that can be used to connect to instance from outside world
    List<InstanceDetail> instancesAfterUpsize = new ArrayList<>(detailsAfterUpsize.getInstanceDetails());
    executionLogCallback.saveExecutionLog("\n# Application state details after upsize:  ");
    printApplicationDetail(detailsAfterUpsize, executionLogCallback);
    printInstanceDetails(executionLogCallback, instancesAfterUpsize);
  }

  public void printInstanceDetails(LogCallback executionLogCallback, List<InstanceDetail> instances) {
    StringBuilder builder = new StringBuilder("Instance Details:");
    instances.forEach(instance
        -> builder.append("\nIndex: ")
               .append(instance.getIndex())
               .append("\nState: ")
               .append(instance.getState())
               .append("\nDisk Usage: ")
               .append(instance.getDiskUsage())
               .append("\nCPU: ")
               .append(instance.getCpu())
               .append("\nMemory Usage: ")
               .append(instance.getMemoryUsage())
               .append("\n"));
    executionLogCallback.saveExecutionLog(builder.toString());
  }

  public ApplicationDetail printApplicationDetail(
      ApplicationDetail applicationDetail, LogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog(CFLogCallbackFormatter.formatAppInstancesRoutes(
        applicationDetail.getName(), applicationDetail.getInstances(), applicationDetail.getUrls()));
    return applicationDetail;
  }

  /**
   * e.g. Downsize by 5,
   * Find out previous apps with non zero instances.
   * Process apps in descending order of versions.
   * keep processing till total counts taken down become 5
   * e.g. app_serv_env__5 is new app created,
   * app_serv_env__4   : 3
   * app_serv_env__3   : 3
   * app_serv_env__2   : 1
   * <p>
   * After this method, it will be
   * app_serv_env__4   : 0
   * app_serv_env__3   : 1
   * app_serv_env__2   : 1
   */
  public void downsizePreviousReleases(CfCommandDeployRequest cfCommandDeployRequest, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback, List<CfServiceData> cfServiceDataUpdated, Integer updateCount,
      List<CfInternalInstanceElement> pcfInstanceElements, CfAppAutoscalarRequestData appAutoscalarRequestData)
      throws PivotalClientApiException {
    if (cfCommandDeployRequest.isStandardBlueGreen()) {
      executionLogCallback.saveExecutionLog("# BG Deployment. Old Application will not be downsized.");
      return;
    }

    executionLogCallback.saveExecutionLog("# Downsizing previous application version/s");

    CfAppSetupTimeDetails downsizeAppDetails = cfCommandDeployRequest.getDownsizeAppDetail();
    if (downsizeAppDetails == null) {
      executionLogCallback.saveExecutionLog("# No Application is available for downsize");
      return;
    }

    cfRequestConfig.setApplicationName(downsizeAppDetails.getApplicationName());
    ApplicationDetail applicationDetail = pcfDeploymentManager.getApplicationByName(cfRequestConfig);
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
    if (cfCommandDeployRequest.isUseAppAutoscalar()) {
      appAutoscalarRequestData.setApplicationName(applicationDetail.getName());
      appAutoscalarRequestData.setApplicationGuid(applicationDetail.getId());
      appAutoscalarRequestData.setExpectedEnabled(true);
      boolean autoscalarStateChanged = disableAutoscalarSafe(appAutoscalarRequestData, executionLogCallback);
      cfServiceData.setDisableAutoscalarPerformed(autoscalarStateChanged);
    }

    ApplicationDetail applicationDetailAfterResize =
        downSize(cfServiceData, executionLogCallback, cfRequestConfig, pcfDeploymentManager);

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
  }

  public void upsizeListOfInstances(LogCallback executionLogCallback, CfDeploymentManager pcfDeploymentManager,
      List<CfServiceData> cfServiceDataUpdated, CfRequestConfig cfRequestConfig, List<CfServiceData> upsizeList,
      List<CfInternalInstanceElement> pcfInstanceElements) throws PivotalClientApiException {
    if (isEmpty(upsizeList)) {
      executionLogCallback.saveExecutionLog("No application To Upsize");
      return;
    }

    for (CfServiceData cfServiceData : upsizeList) {
      executionLogCallback.saveExecutionLog(color("# Upsizing application:", White, Bold));
      executionLogCallback.saveExecutionLog(CFLogCallbackFormatter.formatAppInstancesState(
          cfServiceData.getName(), cfServiceData.getPreviousCount(), cfServiceData.getDesiredCount()));
      cfRequestConfig.setApplicationName(cfServiceData.getName());
      cfRequestConfig.setDesiredCount(cfServiceData.getDesiredCount());
      upsizeInstance(
          cfRequestConfig, pcfDeploymentManager, executionLogCallback, cfServiceDataUpdated, pcfInstanceElements);
      cfServiceDataUpdated.add(cfServiceData);
    }
  }

  public void downSizeListOfInstances(LogCallback executionLogCallback, List<CfServiceData> cfServiceDataUpdated,
      CfRequestConfig cfRequestConfig, List<CfServiceData> downSizeList,
      CfCommandRollbackRequest commandRollbackRequest, CfAppAutoscalarRequestData appAutoscalarRequestData)
      throws PivotalClientApiException {
    executionLogCallback.saveExecutionLog("\n");
    for (CfServiceData cfServiceData : downSizeList) {
      executionLogCallback.saveExecutionLog(color("# Downsizing application:", White, Bold));
      executionLogCallback.saveExecutionLog(CFLogCallbackFormatter.formatAppInstancesState(
          cfServiceData.getName(), cfServiceData.getPreviousCount(), cfServiceData.getDesiredCount()));

      cfRequestConfig.setApplicationName(cfServiceData.getName());
      cfRequestConfig.setDesiredCount(cfServiceData.getDesiredCount());

      if (commandRollbackRequest.isUseAppAutoscalar()) {
        ApplicationDetail applicationDetail = pcfDeploymentManager.getApplicationByName(cfRequestConfig);
        appAutoscalarRequestData.setApplicationName(applicationDetail.getName());
        appAutoscalarRequestData.setApplicationGuid(applicationDetail.getId());
        appAutoscalarRequestData.setExpectedEnabled(true);
        disableAutoscalarSafe(appAutoscalarRequestData, executionLogCallback);
      }

      downSize(cfServiceData, executionLogCallback, cfRequestConfig, pcfDeploymentManager);

      cfServiceDataUpdated.add(cfServiceData);
    }
  }

  public void mapRouteMaps(String applicationName, List<String> routes, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    executionLogCallback.saveExecutionLog(color("\n# Adding Routes", White, Bold));
    executionLogCallback.saveExecutionLog(APPLICATION + encodeColor(applicationName));
    executionLogCallback.saveExecutionLog("ROUTE: \n[" + getRouteString(routes));
    // map
    cfRequestConfig.setApplicationName(applicationName);
    pcfDeploymentManager.mapRouteMapForApplication(cfRequestConfig, routes, executionLogCallback);
  }

  public void unmapRouteMaps(String applicationName, List<String> routes, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    executionLogCallback.saveExecutionLog(color("\n# Unmapping Routes", White, Bold));
    executionLogCallback.saveExecutionLog(APPLICATION + encodeColor(applicationName));
    executionLogCallback.saveExecutionLog("ROUTES: \n[" + getRouteString(routes));
    // unmap
    cfRequestConfig.setApplicationName(applicationName);
    pcfDeploymentManager.unmapRouteMapForApplication(cfRequestConfig, routes, executionLogCallback);
    executionLogCallback.saveExecutionLog("# Unmapping Routes was successfully completed");
  }

  public void printFileNamesInExecutionLogs(List<String> filePathList, LogCallback executionLogCallback) {
    if (EmptyPredicate.isEmpty(filePathList)) {
      return;
    }

    StringBuilder sb = new StringBuilder(1024);
    filePathList.forEach(filePath -> sb.append(color(format("- %s", filePath), Gray)).append(System.lineSeparator()));

    executionLogCallback.saveExecutionLog(sb.toString());
  }

  public String getCfCliPathOnDelegate(boolean useCli, CfCliVersion version) {
    if (!useCli) {
      return null;
    }

    if (version == null) {
      throw new InvalidArgumentsException("Requested CF CLI version on delegate cannot be null");
    }

    return cfCliDelegateResolver.getAvailableCfCliPathOnDelegate(version).orElseThrow(
        ()
            -> new InvalidArgumentsException(
                format("Unable to find CF CLI version on delegate, requested version: %s", version)));
  }

  public File generateWorkingDirectoryForDeployment() throws IOException {
    String workingDirecotry = UUIDGenerator.generateUuid();
    createDirectoryIfDoesNotExist(REPOSITORY_DIR_PATH);
    createDirectoryIfDoesNotExist(PCF_ARTIFACT_DOWNLOAD_DIR_PATH);
    String workingDir = PCF_ARTIFACT_DOWNLOAD_DIR_PATH + "/" + workingDirecotry;
    createDirectoryIfDoesNotExist(workingDir);
    return new File(workingDir);
  }

  public ApplicationDetail getNewlyCreatedApplication(CfRequestConfig cfRequestConfig,
      CfCommandDeployRequest cfCommandDeployRequest, CfDeploymentManager pcfDeploymentManager)
      throws PivotalClientApiException {
    cfRequestConfig.setApplicationName(cfCommandDeployRequest.getNewReleaseName());
    cfRequestConfig.setDesiredCount(cfCommandDeployRequest.getUpdateCount());
    return pcfDeploymentManager.getApplicationByName(cfRequestConfig);
  }

  @VisibleForTesting
  ApplicationDetail downSize(CfServiceData cfServiceData, LogCallback executionLogCallback,
      CfRequestConfig cfRequestConfig, CfDeploymentManager pcfDeploymentManager) throws PivotalClientApiException {
    cfRequestConfig.setApplicationName(cfServiceData.getName());
    cfRequestConfig.setDesiredCount(cfServiceData.getDesiredCount());

    ApplicationDetail applicationDetail = pcfDeploymentManager.resizeApplication(cfRequestConfig, executionLogCallback);

    executionLogCallback.saveExecutionLog("# Downsizing successful");
    executionLogCallback.saveExecutionLog("\n# App details after downsize:");
    printApplicationDetail(applicationDetail, executionLogCallback);
    return applicationDetail;
  }

  public boolean disableAutoscalar(CfAppAutoscalarRequestData pcfAppAutoscalarRequestData,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    return pcfDeploymentManager.changeAutoscalarState(pcfAppAutoscalarRequestData, executionLogCallback, false);
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

  private List<InstanceDetail> filterNewUpsizedAppInstances(
      ApplicationDetail appDetailsBeforeUpsize, ApplicationDetail appDetailsAfterUpsize) {
    if (isEmpty(appDetailsBeforeUpsize.getInstanceDetails()) || isEmpty(appDetailsAfterUpsize.getInstanceDetails())) {
      return appDetailsAfterUpsize.getInstanceDetails();
    }

    List<String> alreadyUpsizedInstances =
        appDetailsBeforeUpsize.getInstanceDetails().stream().map(InstanceDetail::getIndex).collect(toList());

    return appDetailsAfterUpsize.getInstanceDetails()
        .stream()
        .filter(instanceDetail -> !alreadyUpsizedInstances.contains(instanceDetail.getIndex()))
        .collect(Collectors.toList());
  }

  @NotNull
  private File writeToManifestFile(String content, File manifestFile) throws IOException {
    if (!manifestFile.createNewFile()) {
      throw new FileCreationException("Failed to create file " + manifestFile.getCanonicalPath(), null,
          ErrorCode.FILE_CREATE_ERROR, Level.ERROR, USER, null);
    }

    FileUtils.writeStringToFile(manifestFile, content, StandardCharsets.UTF_8);
    return manifestFile;
  }

  private String getRouteString(List<String> routeMaps) {
    if (EmptyPredicate.isEmpty(routeMaps)) {
      return StringUtils.EMPTY;
    }

    StringBuilder builder = new StringBuilder();
    routeMaps.forEach(routeMap -> builder.append("\n").append(routeMap));
    builder.append("\n]");
    return builder.toString();
  }

  public int getRevisionFromReleaseName(String name) {
    if (name != null) {
      int index = name.lastIndexOf(DELIMITER);
      if (index >= 0) {
        try {
          return Integer.parseInt(name.substring(index + DELIMITER.length()));
        } catch (NumberFormatException e) {
          // Ignore
        }
      }
    }
    return -1;
  }

  /**
   * Returns Application that will be downsized in deployment process
   */
  public List<CfAppSetupTimeDetails> generateDownsizeDetails(ApplicationSummary activeApplicationSummamry) {
    List<CfAppSetupTimeDetails> downSizeUpdate = new ArrayList<>();
    if (activeApplicationSummamry != null) {
      List<String> urls = new ArrayList<>(activeApplicationSummamry.getUrls());
      downSizeUpdate.add(CfAppSetupTimeDetails.builder()
                             .applicationGuid(activeApplicationSummamry.getId())
                             .applicationName(activeApplicationSummamry.getName())
                             .oldName(activeApplicationSummamry.getName())
                             .urls(urls)
                             .initialInstanceCount(activeApplicationSummamry.getInstances())
                             .build());
    }

    return downSizeUpdate;
  }

  public ApplicationSummary findCurrentActiveApplication(List<ApplicationSummary> previousReleases,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) throws PivotalClientApiException {
    if (isEmpty(previousReleases)) {
      return null;
    }
    String releaseNamePrefix = cfRequestConfig.getApplicationName();

    ApplicationSummary activeApplication =
        findActiveBasedOnEnvironmentVariable(previousReleases, cfRequestConfig, executionLogCallback);

    if (activeApplication == null) {
      activeApplication = findActiveBasedOnServiceName(previousReleases, releaseNamePrefix, executionLogCallback);
    }

    if (activeApplication == null) {
      StringBuilder msgBuilder =
          new StringBuilder(256)
              .append("Invalid PCF Deployment State. No applications were found having Env variable as ")
              .append(HARNESS__STATUS__IDENTIFIER)
              .append(
                  ": ACTIVE' identifier and no applications were found having same name as release name as specified by customer.");
      executionLogCallback.saveExecutionLog(msgBuilder.toString(), ERROR);
      throw new InvalidPcfStateException(msgBuilder.toString(), INVALID_INFRA_STATE, USER_SRE);
    }

    return activeApplication;
  }

  public ApplicationSummary findCurrentActiveApplicationNG(List<ApplicationSummary> previousReleases,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) throws PivotalClientApiException {
    if (isEmpty(previousReleases)) {
      return null;
    }
    String releaseNamePrefix = cfRequestConfig.getApplicationName();

    ApplicationSummary activeApplication =
        findActiveBasedOnEnvironmentVariable(previousReleases, cfRequestConfig, executionLogCallback);

    if (activeApplication == null) {
      activeApplication = findActiveBasedOnServiceName(previousReleases, releaseNamePrefix, executionLogCallback);
    }

    return activeApplication;
  }

  private ApplicationSummary findActiveBasedOnEnvironmentVariable(List<ApplicationSummary> previousReleases,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) throws PivotalClientApiException {
    // For existing
    List<ApplicationSummary> activeVersions = new ArrayList<>();
    ApplicationSummary activeApplication = null;
    for (int i = previousReleases.size() - 1; i >= 0; i--) {
      ApplicationSummary applicationSummary = previousReleases.get(i);
      cfRequestConfig.setApplicationName(applicationSummary.getName());

      if (pcfDeploymentManager.isActiveApplication(cfRequestConfig, executionLogCallback)) {
        activeApplication = applicationSummary;
        activeVersions.add(applicationSummary);
        executionLogCallback.saveExecutionLog(
            String.format("Found current Active App: [%s], as it has HARNESS__STATUS__IDENTIFIER set as ACTIVE",
                PcfUtils.encodeColor(applicationSummary.getName())));
      }
    }

    if (isNotEmpty(activeVersions) && activeVersions.size() > 1) {
      StringBuilder msgBuilder =
          new StringBuilder(256)
              .append("Invalid PCF Deployment State. Found Multiple applications having Env variable as ")
              .append(HARNESS__STATUS__IDENTIFIER)
              .append(
                  ": ACTIVE' identifier. Cant Determine actual active version.\n Only 1 is expected to have this Status. Active versions found are: \n");
      activeVersions.forEach(activeVersion -> msgBuilder.append(activeVersion.getName()).append(' '));
      executionLogCallback.saveExecutionLog(msgBuilder.toString(), ERROR);
      throw new InvalidPcfStateException(msgBuilder.toString(), INVALID_INFRA_STATE, USER_SRE);
    }

    return activeApplication;
  }

  public ApplicationSummary findActiveBasedOnServiceName(List<ApplicationSummary> previousReleases,
      String releaseNamePrefix, LogCallback executionLogCallback) throws PivotalClientApiException {
    ApplicationSummary activeApplication = null;
    for (int i = previousReleases.size() - 1; i >= 0; i--) {
      ApplicationSummary applicationSummary = previousReleases.get(i);

      if (releaseNamePrefix.equals(applicationSummary.getName())) {
        activeApplication = applicationSummary;
        executionLogCallback.saveExecutionLog(
            String.format("Found current Active App: [%s], as it has same name as release name specified by user",
                PcfUtils.encodeColor(activeApplication.getName())));
      }
    }
    return activeApplication;
  }

  public ApplicationSummary findCurrentInActiveApplication(ApplicationSummary activeApplicationSummary,
      List<ApplicationSummary> previousReleases, CfRequestConfig cfRequestConfig, LogCallback executionLogCallback)
      throws PivotalClientApiException {
    if (isEmpty(previousReleases)) {
      return null;
    }
    List<ApplicationSummary> inActiveVersions = new ArrayList<>();
    ApplicationSummary inActiveApplication = null;
    for (ApplicationSummary applicationSummary : previousReleases) {
      if (activeApplicationSummary.getName().equalsIgnoreCase(applicationSummary.getName())) {
        // in active app will always be found earlier than active app
        break;
      }
      cfRequestConfig.setApplicationName(applicationSummary.getName());
      if (pcfDeploymentManager.isInActiveApplication(cfRequestConfig)) {
        inActiveApplication = applicationSummary;
        inActiveVersions.add(applicationSummary);
        executionLogCallback.saveExecutionLog(
            String.format("Found current In-active App: [%s], as it has HARNESS__STATUS__IDENTIFIER set as STAGE",
                PcfUtils.encodeColor(applicationSummary.getName())));
      }
    }
    if (inActiveApplication == null) {
      inActiveVersions = previousReleases.stream()
                             .filter(app -> app.getName().endsWith(PcfConstants.INACTIVE_APP_NAME_SUFFIX))
                             .collect(toList());
      inActiveApplication = isEmpty(inActiveVersions) ? null : inActiveVersions.get(0);
      if (!isNull(inActiveApplication)) {
        executionLogCallback.saveExecutionLog(
            String.format("Found current In-active App: [%s], as it has __INACTIVE suffix in the name",
                PcfUtils.encodeColor(inActiveApplication.getName())));
      }
    }
    if (isNotEmpty(inActiveVersions) && inActiveVersions.size() > 1) {
      StringBuilder msgBuilder =
          new StringBuilder(256)
              .append("Invalid PCF Deployment State. Found Multiple applications having Env variable as ")
              .append(HARNESS__STATUS__IDENTIFIER)
              .append(
                  ": STAGE' identifier. Cant Determine actual in active version.\n Only 1 is expected to have this Status. In Active versions found are: \n");
      inActiveVersions.forEach(activeVersion -> msgBuilder.append(activeVersion.getName()).append(' '));
      executionLogCallback.saveExecutionLog(msgBuilder.toString(), ERROR);
      throw new InvalidPcfStateException(msgBuilder.toString(), INVALID_INFRA_STATE, USER_SRE);
    }
    return inActiveApplication;
  }

  public File createManifestYamlFileLocally(CfCreateApplicationRequestData requestData) throws IOException {
    File manifestFile = getManifestFile(requestData);
    return writeToManifestFile(requestData.getFinalManifestYaml(), manifestFile);
  }

  public File getManifestFile(CfCreateApplicationRequestData requestData) {
    return new File(format("%s/%s.yml", requestData.getConfigPathVar(), requestData.getNewReleaseName()));
  }

  public File createManifestVarsYamlFileLocally(
      CfCreateApplicationRequestData requestData, String varsContent, int index) {
    try {
      if (isBlank(varsContent)) {
        return null;
      }

      File manifestFile = getManifestVarsFile(requestData, index);
      return writeToManifestFile(varsContent, manifestFile);
    } catch (IOException e) {
      throw new UnexpectedException("Failed while writting manifest file on disk", e);
    }
  }

  public File getManifestVarsFile(CfCreateApplicationRequestData requestData, int index) {
    return new File(new StringBuilder(128)
                        .append(requestData.getConfigPathVar())
                        .append('/')
                        .append(requestData.getNewReleaseName())
                        .append("_vars_")
                        .append(index)
                        .append(".yml")
                        .toString());
  }

  public void deleteCreatedFile(List<File> files) {
    files.forEach(File::delete);
  }

  public String generateFinalManifestFilePath(String path) {
    return path.replace(".yml", "_1.yml");
  }

  /**
   * Sources:
   * <a href="https://docs.huihoo.com/cloudfoundry/documentation/devguide/deploy-apps/manifest.html">Link1</a>
   * <a href="https://docs.cloudfoundry.org/devguide/deploy-apps/manifest-attributes.html">Link2</a>
   * <a href="https://docs.pivotal.io/application-service/2-13/devguide/deploy-apps/manifest-attributes.html">Link3</a>
   */
  public Map<String, Object> generateFinalMapForYamlDump(Map<String, Object> applicationToBeUpdated) {
    Map<String, Object> yamlMap = new LinkedHashMap<>();

    addToMapIfExists(yamlMap, applicationToBeUpdated, NAME_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, MEMORY_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, INSTANCE_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, BUILDPACK_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, BUILDPACKS_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, PATH_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, COMMAND_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, DISK_QUOTA_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, DOCKER_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, DOMAINS_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, DOMAIN_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, ENV_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, HEALTH_CHECK_HTTP_ENDPOINT_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, HEALTH_CHECK_TYPE_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, HEALTH_CHECK_INVOCATION_TIMEOUT_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, HOSTS_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, HOST_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, METADATA_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, NO_HOSTNAME_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, NO_ROUTE_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, PROCESSES_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, RANDOM_ROUTE_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, ROUTE_PATH_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, ROUTES_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, SIDE_CARS_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, SERVICES_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, STACK_MANIFEST_YML_ELEMENT);
    addToMapIfExists(yamlMap, applicationToBeUpdated, TIMEOUT_MANIFEST_YML_ELEMENT);

    return yamlMap;
  }

  private void addToMapIfExists(Map<String, Object> destMap, Map<String, Object> sourceMap, String element) {
    if (sourceMap.containsKey(element)) {
      destMap.put(element, sourceMap.get(element));
    }
  }

  @VisibleForTesting
  public void handleManifestWithNoRoute(Map<String, Object> applicationToBeUpdated, boolean isBlueGreen) {
    // No route is not allowed for BG
    if (isBlueGreen) {
      throw new InvalidRequestException("Invalid Config. \"no-route\" can not be used with BG deployment");
    }

    // If no-route = true, then route element is not needed.
    applicationToBeUpdated.remove(ROUTES_MANIFEST_YML_ELEMENT);
  }

  public boolean shouldUseRandomRoute(Map<String, Object> applicationToBeUpdated, List<String> routeMaps) {
    return manifestContainsRandomRouteElement(applicationToBeUpdated) || isEmpty(routeMaps);
  }

  private boolean manifestContainsRandomRouteElement(Map<String, Object> applicationToBeUpdated) {
    return applicationToBeUpdated.containsKey(RANDOM_ROUTE_MANIFEST_YML_ELEMENT)
        && (boolean) applicationToBeUpdated.get(RANDOM_ROUTE_MANIFEST_YML_ELEMENT);
  }

  public ApplicationSummary findActiveApplication(LogCallback executionLogCallback, boolean blueGreen,
      CfRequestConfig cfRequestConfig, List<ApplicationSummary> previousReleases) throws PivotalClientApiException {
    if (isEmpty(previousReleases)) {
      return null;
    }

    ApplicationSummary currentActiveApplication = null;
    // For BG, check for Environment Variable stamped to denote active version, "HARNESS__STATUS__INDENTIFIER: ACTIVE"
    if (blueGreen) {
      currentActiveApplication = findCurrentActiveApplication(previousReleases, cfRequestConfig, executionLogCallback);
    }

    // If not found, get Most recent version with non-zero count.
    if (currentActiveApplication == null) {
      currentActiveApplication = previousReleases.stream()
                                     .filter(applicationSummary -> applicationSummary.getInstances() > 0)
                                     .reduce((first, second) -> second)
                                     .orElse(null);
      if (!isNull(currentActiveApplication)) {
        executionLogCallback.saveExecutionLog(
            String.format("Found current Active App: [%s]", PcfUtils.encodeColor(currentActiveApplication.getName())));
      }
    }

    // All applications have 0 instances
    if (currentActiveApplication == null && !blueGreen) {
      currentActiveApplication = previousReleases.get(previousReleases.size() - 1);
    }

    return currentActiveApplication;
  }

  public ApplicationSummary getMostRecentInactiveApplication(LogCallback executionLogCallback, boolean blueGreen,
      ApplicationSummary activeApplicationSummary, List<ApplicationSummary> previousReleases,
      CfRequestConfig cfRequestConfig) throws PivotalClientApiException {
    if (isEmpty(previousReleases) || activeApplicationSummary == null) {
      return null;
    }

    ApplicationSummary inActiveApplication = null;
    if (blueGreen) {
      return findCurrentInActiveApplication(
          activeApplicationSummary, previousReleases, cfRequestConfig, executionLogCallback);
    }

    int activeAppIndex = -1;
    for (int index = 0; index < previousReleases.size(); index++) {
      ApplicationSummary applicationSummary = previousReleases.get(index);
      if (applicationSummary.getId().equals(activeApplicationSummary.getId())) {
        activeAppIndex = index;
        break; // in active app will always be found earlier than active app
      }
      if (applicationSummary.getInstances() > 0) {
        inActiveApplication = applicationSummary;
      }
    }

    if (inActiveApplication == null && (activeAppIndex - 1) >= 0) {
      inActiveApplication = previousReleases.get(activeAppIndex - 1);
    }

    return inActiveApplication;
  }

  public void resetState(List<ApplicationSummary> previousReleases, ApplicationSummary activeApplication,
      ApplicationSummary inactiveApplication, String releaseNamePrefix, CfRequestConfig cfRequestConfig,
      boolean nonVersioning, @Nullable Deque<CfAppRenameInfo> renames, Integer activeAppRevision,
      LogCallback executionLogCallback, CfInBuiltVariablesUpdateValues updateValues) throws PivotalClientApiException {
    Integer maxVersion = getMaxVersion(previousReleases, activeAppRevision);

    String activeAppName = constructActiveAppName(releaseNamePrefix, maxVersion, nonVersioning);
    if (null != activeApplication && !activeApplication.getName().equals(activeAppName)) {
      renameApp(activeApplication, cfRequestConfig, executionLogCallback, activeAppName);
      updateValues.setOldAppGuid(activeApplication.getId());
      updateValues.setOldAppName(activeAppName);
      updateValues.setActiveAppName(activeAppName);
      if (null != renames) {
        renames.add(CfAppRenameInfo.builder()
                        .guid(activeApplication.getId())
                        .name(activeApplication.getName())
                        .newName(activeAppName)
                        .build());
      }
    }

    String inActiveAppName = constructInActiveAppName(releaseNamePrefix, maxVersion, nonVersioning);
    if (null != inactiveApplication && !inactiveApplication.getName().equals(inActiveAppName)) {
      renameApp(inactiveApplication, cfRequestConfig, executionLogCallback, inActiveAppName);
      updateValues.setInActiveAppName(inActiveAppName);
      if (null != renames) {
        renames.add(CfAppRenameInfo.builder()
                        .guid(inactiveApplication.getId())
                        .name(inactiveApplication.getName())
                        .newName(inActiveAppName)
                        .build());
      }
    }
  }

  public CfAppSetupTimeDetails renameInActiveAppDuringBGDeployment(List<ApplicationSummary> previousReleases,
      CfRequestConfig cfRequestConfig, String releaseNamePrefix, LogCallback executionLogCallback,
      String existingAppNamingStrategy, Deque<CfAppRenameInfo> renames) throws PivotalClientApiException {
    CfAppSetupTimeDetailsBuilder detailsBuilder = CfAppSetupTimeDetails.builder();

    if (isEmpty(previousReleases) || previousReleases.size() == 1) {
      return detailsBuilder.build();
    }
    if (AppNamingStrategy.VERSIONING.name().equalsIgnoreCase(existingAppNamingStrategy)) {
      // there are 4 cases
      // case 1 : for version to version deployment. In this scenario we don't need renaming
      // case 2 : for version to non-version deployment. The in-active app renaming is not required
      // as it will be already correctly named
      return detailsBuilder.build();
    }
    // case 3 : for non-version -> non-version, rename the inactive app to <name-prefix>__<max_version+1>
    // case 4 : for non-version -> version, rename the inactive app to <name-prefix>__<max_version+1>
    cfRequestConfig.setApplicationName(releaseNamePrefix);
    ApplicationSummary activeApplication =
        findActiveApplication(executionLogCallback, true, cfRequestConfig, previousReleases);

    ApplicationSummary inActiveApplication = getMostRecentInactiveApplication(
        executionLogCallback, true, activeApplication, previousReleases, cfRequestConfig);

    if (inActiveApplication == null) {
      return detailsBuilder.build();
    }

    Integer maxVersion = getMaxVersion(previousReleases, -1);
    String newInActiveAppName = constructInActiveAppName(releaseNamePrefix, maxVersion, false);

    if (!inActiveApplication.getName().equals(newInActiveAppName)) {
      renameApp(inActiveApplication, cfRequestConfig, executionLogCallback, newInActiveAppName);
      if (null != renames) {
        renames.add(CfAppRenameInfo.builder()
                        .guid(inActiveApplication.getId())
                        .name(inActiveApplication.getName())
                        .newName(newInActiveAppName)
                        .build());
      }
    }
    return detailsBuilder.applicationGuid(inActiveApplication.getId())
        .applicationName(newInActiveAppName)
        .oldName(inActiveApplication.getName())
        .initialInstanceCount(inActiveApplication.getRunningInstances())
        .urls(new ArrayList<>(inActiveApplication.getUrls()))
        .build();
  }

  public CfAppSetupTimeDetails getInActiveApplicationDetailsBeforeNewExecution(
      List<ApplicationSummary> previousReleases, CfRequestConfig cfRequestConfig, LogCallback executionLogCallback)
      throws PivotalClientApiException {
    CfAppSetupTimeDetailsBuilder detailsBuilder = CfAppSetupTimeDetails.builder();
    if (isEmpty(previousReleases) || previousReleases.size() == 1) {
      return detailsBuilder.build();
    }

    ApplicationSummary activeApplication =
        findActiveApplication(executionLogCallback, false, cfRequestConfig, previousReleases);

    ApplicationSummary inActiveApplication = getMostRecentInactiveApplication(
        executionLogCallback, false, activeApplication, previousReleases, cfRequestConfig);

    if (inActiveApplication == null) {
      return detailsBuilder.build();
    }

    return detailsBuilder.applicationGuid(inActiveApplication.getId())
        .applicationName(inActiveApplication.getName())
        .oldName(inActiveApplication.getName())
        .initialInstanceCount(inActiveApplication.getRunningInstances())
        .urls(new ArrayList<>(inActiveApplication.getUrls()))
        .build();
  }

  private static Integer getMaxVersion(List<ApplicationSummary> previousReleases, Integer activeAppRevision) {
    Integer maxVersion = getMaxVersion(previousReleases);
    if (null != activeAppRevision && maxVersion == -1 && activeAppRevision != -1) {
      maxVersion = activeAppRevision - 2;
    }
    return maxVersion;
  }

  public static Integer getMaxVersion(List<ApplicationSummary> previousReleases) {
    Optional<Integer> maxVersion = previousReleases.stream()
                                       .map(r -> getRevisionFromServiceName(r.getName()))
                                       .filter(rev -> rev >= 0)
                                       .max(Integer::compare);

    return maxVersion.orElse(-1);
  }

  public void renameApp(ApplicationSummary app, CfRequestConfig cfRequestConfig, LogCallback executionLogCallback,
      @NotNull String newName, @NotNull String oldName) throws PivotalClientApiException {
    pcfDeploymentManager.renameApplication(
        new CfRenameRequest(cfRequestConfig, app.getId(), oldName, newName), executionLogCallback);
  }

  public void renameApp(ApplicationSummary app, CfRequestConfig cfRequestConfig, LogCallback executionLogCallback,
      @NotNull String newName) throws PivotalClientApiException {
    pcfDeploymentManager.renameApplication(
        new CfRenameRequest(cfRequestConfig, app.getId(), app.getName(), newName), executionLogCallback);
  }

  public static String constructActiveAppName(String releaseNamePrefix, Integer maxVersion, boolean nonVersioning) {
    if (nonVersioning) {
      return releaseNamePrefix;
    } else {
      return releaseNamePrefix + DELIMITER + (maxVersion + 2);
    }
  }

  public static String constructInActiveAppName(String releaseNamePrefix, Integer maxVersion, boolean nonVersioning) {
    if (nonVersioning) {
      return releaseNamePrefix + DELIMITER + HARNESS__INACTIVE__IDENTIFIER;
    } else {
      return releaseNamePrefix + DELIMITER + (maxVersion + 1);
    }
  }

  public static String getVersionChangeMessage(boolean nonVersioning) {
    if (nonVersioning) {
      return "# Changing apps from Versioned to Non-versioned";
    } else {
      return "# Changing apps from Non-versioned to Versioned";
    }
  }

  public List<String> getAppNameBasedOnGuid(CfRequestConfig cfRequestConfig, String cfAppNamePrefix, String appGuid)
      throws PivotalClientApiException {
    List<ApplicationSummary> previousReleases =
        pcfDeploymentManager.getPreviousReleases(cfRequestConfig, cfAppNamePrefix);
    return previousReleases.stream()
        .filter(app -> app.getId().equalsIgnoreCase(appGuid))
        .map(ApplicationSummary::getName)
        .collect(toList());
  }
}

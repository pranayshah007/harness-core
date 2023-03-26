/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.pcf.CfCommandUnitConstants.Rollback;
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

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
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
import io.harness.delegate.task.pcf.request.CfCommandRequestNG;
import io.harness.delegate.task.pcf.request.CfRollingRollbackRequestNG;
import io.harness.delegate.task.pcf.request.TasManifestsPackage;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfRollingRollbackResponseNG;
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
import io.harness.pcf.model.CfRequestConfig.CfRequestConfigBuilder;
import io.harness.pcf.model.CloudFoundryConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.InstanceDetail;

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
  @Inject private TasRegistrySettingsAdapter tasRegistrySettingsAdapter;

  @Override
  protected CfCommandResponseNG executeTaskInternal(CfCommandRequestNG cfCommandRequestNG,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(cfCommandRequestNG instanceof CfRollingRollbackRequestNG)) {
      throw new InvalidArgumentsException(
          Pair.of("cfCommandRequestNG", "Must be instance of CfRollingRollbackRequestNG"));
    }

    LogCallback logCallback =
        tasTaskHelperBase.getLogCallback(iLogStreamingTaskClient, Rollback, true, commandUnitsProgress);
    CfManifestFileData pcfManifestFileData = CfManifestFileData.builder().varFiles(new ArrayList<>()).build();

    CfRollingRollbackRequestNG cfRollingRollbackRequestNG = (CfRollingRollbackRequestNG) cfCommandRequestNG;
    TasInfraConfig tasInfraConfig = cfRollingRollbackRequestNG.getTasInfraConfig();
    CloudFoundryConfig cfConfig = tasNgConfigMapper.mapTasConfigWithDecryption(
        tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());
    CfRequestConfig cfRequestConfig = getCfRequestConfig(cfRollingRollbackRequestNG, cfConfig);

    File artifactFile = null;
    File workingDirectory = null;
    TasApplicationInfo currentProdInfo = null;
    try {
      List<ApplicationSummary> previousReleases = cfDeploymentManager.getPreviousReleasesForRolling(
          cfRequestConfig, ((CfRollingRollbackRequestNG) cfCommandRequestNG).getApplicationName());
      workingDirectory = generateWorkingDirectoryOnDelegate(cfRollingRollbackRequestNG);
      cfRequestConfig.setCfHomeDirPath(workingDirectory.getAbsolutePath());
      currentProdInfo = getCurrentProdInfo(previousReleases, clonePcfRequestConfig(cfRequestConfig).build(),
          workingDirectory, ((CfRollingRollbackRequestNG) cfCommandRequestNG).getTimeoutIntervalInMin(), logCallback);
      ApplicationDetail detailsBeforeDeployment = isEmpty(previousReleases)
          ? null
          : cfCommandTaskHelperNG.getApplicationDetails(cfRequestConfig, cfDeploymentManager);

      CfAppAutoscalarRequestData cfAppAutoscalarRequestData =
          CfAppAutoscalarRequestData.builder()
              .cfRequestConfig(cfRequestConfig)
              .configPathVar(workingDirectory.getAbsolutePath())
              .timeoutInMins(cfRollingRollbackRequestNG.getTimeoutIntervalInMin())
              .build();

      CfRollingRollbackResponseNG cfRollingRollbackResponseNG;

      if (currentProdInfo != null && currentProdInfo.isAutoScalarEnabled()) {
        cfAppAutoscalarRequestData.setApplicationName(currentProdInfo.getApplicationName());
        cfAppAutoscalarRequestData.setApplicationGuid(currentProdInfo.getApplicationGuid());
        cfAppAutoscalarRequestData.setExpectedEnabled(true);
        pcfCommandTaskBaseHelper.disableAutoscalarSafe(cfAppAutoscalarRequestData, logCallback);
      }

      if (currentProdInfo != null && cfRollingRollbackRequestNG.isFirstDeployment()) {
        // Deleting
        logCallback.saveExecutionLog(color("\n# Deleting the app which was created ", White, Bold));
        cfDeploymentManager.deleteApplication(cfRequestConfig);
        cfRollingRollbackResponseNG = CfRollingRollbackResponseNG.builder()
                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                          .newApplicationInfo(null)
                                          .currentProdInfo(null)
                                          .build();
        logCallback.saveExecutionLog(color("\n# Deleted successfully", White, Bold));
        logCallback.saveExecutionLog("\n ----------  PCF Rolling Rollback completed successfully", INFO, SUCCESS);
        return cfRollingRollbackResponseNG;
      }

      artifactFile = downloadArtifactFile(cfRollingRollbackRequestNG, workingDirectory, logCallback);

      boolean varsYmlPresent = checkIfVarsFilePresent(cfRollingRollbackRequestNG);
      CfCreateApplicationRequestData requestData =
          CfCreateApplicationRequestData.builder()
              .cfRequestConfig(clonePcfRequestConfig(cfRequestConfig)
                                   .applicationName(cfRollingRollbackRequestNG.getApplicationName())
                                   .routeMaps(cfRollingRollbackRequestNG.getRouteMaps())
                                   .build())
              .artifactPath(artifactFile == null ? null : artifactFile.getAbsolutePath())
              .configPathVar(workingDirectory.getAbsolutePath())
              .newReleaseName(cfRollingRollbackRequestNG.getApplicationName())
              .pcfManifestFileData(pcfManifestFileData)
              .varsYmlFilePresent(varsYmlPresent)
              .dockerBasedDeployment(isDockerArtifact(cfRollingRollbackRequestNG.getTasArtifactConfig()))
              .strategy("rolling")
              .build();

      requestData.setFinalManifestYaml(generateManifestYamlForPush(cfRollingRollbackRequestNG, requestData));
      // Create manifest.yaml file
      prepareManifestYamlFile(requestData);

      if (varsYmlPresent) {
        prepareVarsYamlFile(requestData, cfRollingRollbackRequestNG);
      }

      logCallback.saveExecutionLog(color("\n# Starting Rollback", White, Bold));

      ApplicationDetail applicationDetail = createAppAndPrintDetails(logCallback, requestData);
      List<CfInternalInstanceElement> cfInternalInstanceElements = new ArrayList<>();
      List<InstanceDetail> newUpsizedInstances =
          filterNewUpsizedAppInstances(detailsBeforeDeployment, applicationDetail);
      newUpsizedInstances.forEach(instance
          -> cfInternalInstanceElements.add(CfInternalInstanceElement.builder()
                                                .uuid(applicationDetail.getId() + instance.getIndex())
                                                .applicationId(applicationDetail.getId())
                                                .displayName(applicationDetail.getName())
                                                .instanceIndex(instance.getIndex())
                                                .isUpsize(true)
                                                .build()));

      if (applicationDetail.getUrls() != null) {
        List<String> routesToBeRemoved = new ArrayList<>();
        for (String route : applicationDetail.getUrls()) {
          if (!cfRollingRollbackRequestNG.getRouteMaps().contains(route)) {
            routesToBeRemoved.add(route);
          }
        }
        cfCommandTaskHelperNG.unmapRouteMaps(
            cfRollingRollbackRequestNG.getApplicationName(), routesToBeRemoved, cfRequestConfig, logCallback);
      }

      configureAutoscalarIfNeeded(
          cfRollingRollbackRequestNG, applicationDetail, cfAppAutoscalarRequestData, logCallback);
      if (cfRollingRollbackRequestNG.isUseAppAutoScalar()) {
        cfCommandTaskHelperNG.enableAutoscalerIfNeeded(applicationDetail, cfAppAutoscalarRequestData, logCallback);
      }

      cfRollingRollbackResponseNG =
          CfRollingRollbackResponseNG.builder()
              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
              .newApplicationInfo(TasApplicationInfo.builder()
                                      .applicationGuid(applicationDetail.getId())
                                      .applicationName(applicationDetail.getName())
                                      .attachedRoutes(new ArrayList<>(applicationDetail.getUrls()))
                                      .runningCount(applicationDetail.getRunningInstances())
                                      .build())
              .currentProdInfo(currentProdInfo)
              .newAppInstances(cfInternalInstanceElements)
              .build();

      logCallback.saveExecutionLog("\n ----------  PCF Rolling Rollback completed successfully", INFO, SUCCESS);
      return cfRollingRollbackResponseNG;

    } catch (RuntimeException | PivotalClientApiException | IOException e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error(PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX + "Exception in processing PCF Rolling Rollback task [{}]",
          cfRollingRollbackRequestNG, sanitizedException);
      logCallback.saveExecutionLog("\n\n ----------  PCF Rolling Rollback failed to complete successfully", ERROR,
          CommandExecutionStatus.FAILURE);

      Misc.logAllMessages(sanitizedException, logCallback);
      return CfRollingRollbackResponseNG.builder()
          .currentProdInfo(currentProdInfo)
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(ExceptionUtils.getMessage(sanitizedException))
          .build();
    } finally {
      logCallback = tasTaskHelperBase.getLogCallback(iLogStreamingTaskClient, Wrapup, true, commandUnitsProgress);
      removeTempFilesCreated(
          cfRollingRollbackRequestNG, logCallback, artifactFile, workingDirectory, pcfManifestFileData);
      logCallback.saveExecutionLog("#----------  Cleaning up temporary files completed", INFO, SUCCESS);
    }
  }

  private List<InstanceDetail> filterNewUpsizedAppInstances(
      ApplicationDetail appDetailsBeforeUpsize, ApplicationDetail appDetailsAfterUpsize) {
    if (appDetailsBeforeUpsize == null || isEmpty(appDetailsBeforeUpsize.getInstanceDetails())
        || isEmpty(appDetailsAfterUpsize.getInstanceDetails())) {
      return appDetailsAfterUpsize.getInstanceDetails();
    }

    List<String> alreadyUpsizedInstances =
        appDetailsBeforeUpsize.getInstanceDetails().stream().map(InstanceDetail::getIndex).collect(toList());

    return appDetailsAfterUpsize.getInstanceDetails()
        .stream()
        .filter(instanceDetail -> !alreadyUpsizedInstances.contains(instanceDetail.getIndex()))
        .collect(Collectors.toList());
  }

  private void configureAutoscalarIfNeeded(CfRollingRollbackRequestNG cfRollingRollbackRequestNG,
      ApplicationDetail applicationDetail, CfAppAutoscalarRequestData appAutoscalarRequestData,
      LogCallback executionLogCallback) throws PivotalClientApiException, IOException {
    if (cfRollingRollbackRequestNG.isUseAppAutoScalar() && cfRollingRollbackRequestNG.getTasManifestsPackage() != null
        && isNotEmpty(cfRollingRollbackRequestNG.getTasManifestsPackage().getAutoscalarManifestYml())) {
      // This is autoscalar file inside workingDirectory
      String filePath =
          appAutoscalarRequestData.getConfigPathVar() + "/autoscalar_" + System.currentTimeMillis() + ".yml";
      cfCommandTaskHelperNG.createYamlFileLocally(
          filePath, cfRollingRollbackRequestNG.getTasManifestsPackage().getAutoscalarManifestYml());

      // upload autoscalar config
      appAutoscalarRequestData.setApplicationName(applicationDetail.getName());
      appAutoscalarRequestData.setApplicationGuid(applicationDetail.getId());
      appAutoscalarRequestData.setTimeoutInMins(cfRollingRollbackRequestNG.getTimeoutIntervalInMin());
      appAutoscalarRequestData.setAutoscalarFilePath(filePath);
      cfDeploymentManager.performConfigureAutoscalar(appAutoscalarRequestData, executionLogCallback);
    }
  }

  // Remove downloaded artifact and generated yaml files
  private void removeTempFilesCreated(CfRollingRollbackRequestNG cfRollingRollbackRequestNG,
      LogCallback executionLogCallback, File artifactFile, File workingDirectory,
      CfManifestFileData pcfManifestFileData) {
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

  ApplicationDetail createAppAndPrintDetails(LogCallback executionLogCallback,
      CfCreateApplicationRequestData requestData) throws PivotalClientApiException, InterruptedException {
    requestData.getCfRequestConfig().setLoggedin(false);
    ApplicationDetail newApplication =
        cfDeploymentManager.createRollingApplicationWithSteadyStateCheck(requestData, executionLogCallback);
    executionLogCallback.saveExecutionLog(color("# Application created successfully", White, Bold));
    executionLogCallback.saveExecutionLog("# App Details: ");
    pcfCommandTaskBaseHelper.printApplicationDetail(newApplication, executionLogCallback);
    return newApplication;
  }

  void prepareManifestYamlFile(CfCreateApplicationRequestData requestData) throws IOException {
    File manifestYamlFile = pcfCommandTaskBaseHelper.createManifestYamlFileLocally(requestData);
    requestData.setManifestFilePath(manifestYamlFile.getAbsolutePath());
    requestData.getPcfManifestFileData().setManifestFile(manifestYamlFile);
  }

  void prepareVarsYamlFile(CfCreateApplicationRequestData requestData,
      CfRollingRollbackRequestNG cfRollingRollbackRequestNG) throws IOException {
    if (!requestData.isVarsYmlFilePresent()) {
      return;
    }

    TasManifestsPackage tasManifestsPackage = cfRollingRollbackRequestNG.getTasManifestsPackage();
    AtomicInteger varFileIndex = new AtomicInteger(0);
    tasManifestsPackage.getVariableYmls().forEach(varFileYml -> {
      File varsYamlFile =
          pcfCommandTaskBaseHelper.createManifestVarsYamlFileLocally(requestData, varFileYml, varFileIndex.get());
      if (varsYamlFile != null) {
        varFileIndex.incrementAndGet();
        requestData.getPcfManifestFileData().getVarFiles().add(varsYamlFile);
      }
    });
  }

  public String generateManifestYamlForPush(CfRollingRollbackRequestNG cfRollingRollbackRequestNG,
      CfCreateApplicationRequestData requestData) throws PivotalClientApiException {
    // Substitute name,
    String manifestYaml = cfRollingRollbackRequestNG.getTasManifestsPackage().getManifestYml();

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

    updateArtifactDetails(requestData, cfRollingRollbackRequestNG, applicationToBeUpdated);

    // Update routes.
    updateConfigWithRoutesIfRequired(requestData, applicationToBeUpdated, cfRollingRollbackRequestNG);
    // We do not want to change order

    // remove "create-services" elements as it would have been used by cf cli plugin to create services.
    // This elements is not needed for cf push
    map.remove(CREATE_SERVICE_MANIFEST_ELEMENT);
    Map<String, Object> applicationMapForYamlDump =
        pcfCommandTaskBaseHelper.generateFinalMapForYamlDump(applicationToBeUpdated);

    // replace map for first application that we are deploying
    applicationMaps.set(0, applicationMapForYamlDump);
    try {
      return yaml.dump(map);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new PivotalClientApiException(new StringBuilder()
                                              .append("Failed to generate final version of  Manifest.yml file. ")
                                              .append(manifestYaml)
                                              .toString(),
          sanitizedException);
    }
  }

  private void updateConfigWithRoutesIfRequired(CfCreateApplicationRequestData requestData,
      TreeMap applicationToBeUpdated, CfRollingRollbackRequestNG cfRollingRollbackRequestNG) {
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
    if (pcfCommandTaskBaseHelper.shouldUseRandomRoute(
            applicationToBeUpdated, cfRollingRollbackRequestNG.getRouteMaps())) {
      applicationToBeUpdated.put(RANDOM_ROUTE_MANIFEST_YML_ELEMENT, true);
      return;
    }

    // 3. Insert routes provided by user.
    List<String> routesForUse = cfRollingRollbackRequestNG.getRouteMaps();
    List<Map<String, String>> routeMapList = new ArrayList<>();
    routesForUse.forEach(routeString -> {
      Map<String, String> mapEntry = Collections.singletonMap(ROUTE_MANIFEST_YML_ELEMENT, routeString);
      routeMapList.add(mapEntry);
    });

    // Add this route config to applicationConfig
    applicationToBeUpdated.put(ROUTES_MANIFEST_YML_ELEMENT, routeMapList);
  }

  void updateArtifactDetails(CfCreateApplicationRequestData requestData,
      CfRollingRollbackRequestNG cfRollingRollbackRequestNG, TreeMap<String, Object> applicationToBeUpdated) {
    if (isPackageArtifact(cfRollingRollbackRequestNG.getTasArtifactConfig())) {
      if (!isNull(requestData.getArtifactPath())) {
        applicationToBeUpdated.put(PATH_MANIFEST_YML_ELEMENT, requestData.getArtifactPath());
      }
    } else {
      TasContainerArtifactConfig tasContainerArtifactConfig =
          (TasContainerArtifactConfig) cfRollingRollbackRequestNG.getTasArtifactConfig();
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

  boolean checkIfVarsFilePresent(CfRollingRollbackRequestNG setupRequest) {
    if (setupRequest.getTasManifestsPackage() == null) {
      return false;
    }

    List<String> varFiles = setupRequest.getTasManifestsPackage().getVariableYmls();
    if (isNotEmpty(varFiles)) {
      varFiles = varFiles.stream().filter(StringUtils::isNotBlank).collect(toList());
    }

    return isNotEmpty(varFiles);
  }

  private File downloadArtifactFile(
      CfRollingRollbackRequestNG cfRollingRollbackRequestNG, File workingDirectory, LogCallback logCallback) {
    File artifactFile = null;
    if (isPackageArtifact(cfRollingRollbackRequestNG.getTasArtifactConfig())) {
      TasArtifactDownloadResponse tasArtifactDownloadResponse = cfCommandTaskHelperNG.downloadPackageArtifact(
          TasArtifactDownloadContext.builder()
              .artifactConfig((TasPackageArtifactConfig) cfRollingRollbackRequestNG.getTasArtifactConfig())
              .workingDirectory(workingDirectory)
              .build(),
          logCallback);
      artifactFile = tasArtifactDownloadResponse.getArtifactFile();
    }
    return artifactFile;
  }

  private CfRequestConfigBuilder clonePcfRequestConfig(CfRequestConfig cfRequestConfig) {
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

  private TasApplicationInfo getCurrentProdInfo(List<ApplicationSummary> previousReleases,
      CfRequestConfig cfRequestConfig, File workingDirectory, int timeoutInMins, LogCallback logCallback)
      throws PivotalClientApiException {
    if (isEmpty(previousReleases)) {
      return null;
    }
    ApplicationDetail currentActiveApplication =
        cfCommandTaskHelperNG.getApplicationDetails(cfRequestConfig, cfDeploymentManager);
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
        .oldName(currentActiveApplication.getName())
        .applicationGuid(currentActiveApplication.getId())
        .attachedRoutes(currentActiveApplication.getUrls())
        .runningCount(currentActiveApplication.getRunningInstances())
        .isAutoScalarEnabled(isAutoScalarEnabled)
        .build();
  }

  private File generateWorkingDirectoryOnDelegate(CfRollingRollbackRequestNG cfCommandSetupRequest)
      throws PivotalClientApiException, IOException {
    File workingDirectory = pcfCommandTaskBaseHelper.generateWorkingDirectoryForDeployment();
    if (cfCommandSetupRequest.isUseCfCLI() || cfCommandSetupRequest.isUseAppAutoScalar()) {
      if (workingDirectory == null) {
        throw new PivotalClientApiException("Failed to generate CF-CLI Working directory");
      }
    }
    return workingDirectory;
  }

  private CfRequestConfig getCfRequestConfig(
      CfRollingRollbackRequestNG cfRollingRollbackRequestNG, CloudFoundryConfig cfConfig) {
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
        .applicationName(cfRollingRollbackRequestNG.getApplicationName())
        .desiredCount(cfRollingRollbackRequestNG.getDesiredCount())
        .build();
  }
}

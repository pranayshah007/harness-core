/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.LogCallback;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfConfig;
import io.harness.pcf.model.CfCreateApplicationRequestData;
import io.harness.pcf.model.CfRenameRequest;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CfRunPluginScriptRequestData;

import java.util.List;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;

@OwnedBy(CDP)
public interface CfDeploymentManager {
  List<String> getOrganizations(CfRequestConfig cfRequestConfig) throws PivotalClientApiException;

  List<String> getSpacesForOrganization(CfRequestConfig cfRequestConfig) throws PivotalClientApiException;

  ApplicationDetail createApplication(CfCreateApplicationRequestData requestData, LogCallback logCallback)
      throws PivotalClientApiException;

  ApplicationDetail resizeApplication(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback)
      throws PivotalClientApiException;

  ApplicationDetail upsizeApplication(CfRequestConfig pcfRequestConfig, LogCallback executionLogCallback)
      throws PivotalClientApiException;

  List<ApplicationSummary> getPreviousReleasesBasicAndCanaryNG(CfRequestConfig cfRequestConfig, String prefix)
      throws PivotalClientApiException;

  void deleteApplication(CfRequestConfig cfRequestConfig) throws PivotalClientApiException;

  void renameApplication(CfRenameRequest cfRenameRequest, LogCallback logCallback) throws PivotalClientApiException;

  String stopApplication(CfRequestConfig cfRequestConfig) throws PivotalClientApiException;

  ApplicationDetail getApplicationByName(CfRequestConfig cfRequestConfig) throws PivotalClientApiException;

  void unmapRouteMapForApplication(CfRequestConfig cfRequestConfig, List<String> paths, LogCallback logCallback)
      throws PivotalClientApiException;

  void mapRouteMapForApplication(CfRequestConfig cfRequestConfig, List<String> paths, LogCallback logCallback)
      throws PivotalClientApiException;

  List<ApplicationSummary> getDeployedServicesWithNonZeroInstances(CfRequestConfig cfRequestConfig, String prefix)
      throws PivotalClientApiException;

  List<ApplicationSummary> getPreviousReleases(CfRequestConfig cfRequestConfig, String prefix)
      throws PivotalClientApiException;

  List<String> getRouteMaps(CfRequestConfig cfRequestConfig) throws PivotalClientApiException;

  String checkConnectivity(CfConfig cfConfig, boolean limitPcfThreads);

  String createRouteMap(CfRequestConfig cfRequestConfig, String host, String domain, String path, boolean tcpRoute,
      boolean useRandomPort, Integer port) throws PivotalClientApiException, InterruptedException;

  void performConfigureAutoscalar(io.harness.pcf.model.CfAppAutoscalarRequestData appAutoscalarRequestData,
      LogCallback logCallback) throws PivotalClientApiException;

  boolean changeAutoscalarState(io.harness.pcf.model.CfAppAutoscalarRequestData appAutoscalarRequestData,
      LogCallback logCallback, boolean enable) throws PivotalClientApiException;

  boolean checkIfAppHasAutoscalarAttached(CfAppAutoscalarRequestData appAutoscalarRequestData, LogCallback logCallback)
      throws PivotalClientApiException;

  boolean checkIfAppHasAutoscalarEnabled(CfAppAutoscalarRequestData appAutoscalarRequestData, LogCallback logCallback)
      throws PivotalClientApiException;

  ApplicationDetail upsizeApplicationWithSteadyStateCheck(CfRequestConfig cfRequestConfig, LogCallback logCallback)
      throws PivotalClientApiException;

  boolean isActiveApplication(CfRequestConfig cfRequestConfig, LogCallback logCallback)
      throws PivotalClientApiException;

  boolean isInActiveApplication(CfRequestConfig cfRequestConfig) throws PivotalClientApiException;

  boolean isInActiveApplicationNG(CfRequestConfig cfRequestConfig) throws PivotalClientApiException;

  void setEnvironmentVariableForAppStatus(
      CfRequestConfig cfRequestConfig, boolean activeStatus, LogCallback logCallback) throws PivotalClientApiException;

  void setEnvironmentVariableForAppStatusNG(CfRequestConfig cfRequestConfig, boolean activeStatus,
      LogCallback executionLogCallback) throws PivotalClientApiException;

  void unsetEnvironmentVariableForAppStatus(CfRequestConfig cfRequestConfig, LogCallback logCallback)
      throws PivotalClientApiException;

  void runPcfPluginScript(CfRunPluginScriptRequestData requestData, LogCallback logCallback)
      throws PivotalClientApiException;

  ApplicationDetail createRollingApplicationWithSteadyStateCheck(CfCreateApplicationRequestData requestData,
      LogCallback executionLogCallback) throws PivotalClientApiException, InterruptedException;

  List<ApplicationSummary> getPreviousReleasesForRolling(CfRequestConfig cfRequestConfig, String prefix)
      throws PivotalClientApiException;

  void checkUnsettingEnvironmentVariableForAppStatus(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback)
      throws PivotalClientApiException;

  void checkSettingEnvironmentVariableForAppStatusNG(CfRequestConfig cfRequestConfig, boolean activeStatus,
      LogCallback executionLogCallback) throws PivotalClientApiException;

  void checkSettingEnvironmentVariableForAppStatus(CfRequestConfig cfRequestConfig, boolean activeStatus,
      LogCallback executionLogCallback) throws PivotalClientApiException;
}

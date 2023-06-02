/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfConfig;
import io.harness.pcf.model.CfCreateApplicationRequestData;
import io.harness.pcf.model.CfRenameRequest;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CfRunPluginScriptRequestData;

import com.google.inject.Singleton;
import java.util.List;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;

@Singleton
@OwnedBy(CDP)
public class CfDeploymentManagerUnsupported implements CfDeploymentManager {
  public static final String DELIMITER = "__";

  @Override
  public List<String> getOrganizations(CfRequestConfig cfRequestConfig) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported.");
  }

  @Override
  public List<String> getSpacesForOrganization(CfRequestConfig cfRequestConfig) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public ApplicationDetail createApplication(
      CfCreateApplicationRequestData requestData, LogCallback executionLogCallback) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public ApplicationDetail resizeApplication(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback)
      throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public ApplicationDetail upsizeApplication(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback)
      throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public List<ApplicationSummary> getPreviousReleasesBasicAndCanaryNG(CfRequestConfig cfRequestConfig, String prefix)
      throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public void deleteApplication(CfRequestConfig cfRequestConfig) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public void renameApplication(CfRenameRequest cfRenameRequest, LogCallback logCallback)
      throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public String stopApplication(CfRequestConfig cfRequestConfig) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public ApplicationDetail getApplicationByName(CfRequestConfig cfRequestConfig) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public void unmapRouteMapForApplication(CfRequestConfig cfRequestConfig, List<String> paths, LogCallback logCallback)
      throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public void mapRouteMapForApplication(CfRequestConfig cfRequestConfig, List<String> paths, LogCallback logCallback)
      throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public List<ApplicationSummary> getDeployedServicesWithNonZeroInstances(
      CfRequestConfig cfRequestConfig, String prefix) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public List<ApplicationSummary> getPreviousReleases(CfRequestConfig cfRequestConfig, String prefix)
      throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public List<String> getRouteMaps(CfRequestConfig cfRequestConfig) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public String checkConnectivity(CfConfig pcfConfig, boolean limitPcfThreads) {
    return "FAILED: connection timed out";
  }

  @Override
  public String createRouteMap(CfRequestConfig cfRequestConfig, String host, String domain, String path,
      boolean tcpRoute, boolean useRandomPort, Integer port) throws PivotalClientApiException, InterruptedException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public void performConfigureAutoscalar(CfAppAutoscalarRequestData appAutoscalarRequestData,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public boolean changeAutoscalarState(CfAppAutoscalarRequestData appAutoscalarRequestData,
      LogCallback executionLogCallback, boolean enable) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public boolean checkIfAppHasAutoscalarAttached(CfAppAutoscalarRequestData appAutoscalarRequestData,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public boolean checkIfAppHasAutoscalarEnabled(
      CfAppAutoscalarRequestData appAutoscalarRequestData, LogCallback logCallback) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public ApplicationDetail upsizeApplicationWithSteadyStateCheck(
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public boolean isActiveApplication(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback)
      throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public boolean isInActiveApplication(CfRequestConfig cfRequestConfig) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public boolean isInActiveApplicationNG(CfRequestConfig cfRequestConfig) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public void setEnvironmentVariableForAppStatus(CfRequestConfig cfRequestConfig, boolean activeStatus,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public void setEnvironmentVariableForAppStatusNG(CfRequestConfig cfRequestConfig, boolean activeStatus,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public void unsetEnvironmentVariableForAppStatus(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback)
      throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public void runPcfPluginScript(CfRunPluginScriptRequestData requestData, LogCallback logCallback)
      throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public ApplicationDetail createRollingApplicationWithSteadyStateCheck(CfCreateApplicationRequestData requestData,
      LogCallback executionLogCallback) throws PivotalClientApiException, InterruptedException {
    throw new PivotalClientApiException("TAS operations not supported by this API.");
  }

  @Override
  public List<ApplicationSummary> getPreviousReleasesForRolling(CfRequestConfig cfRequestConfig, String prefix)
      throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public void checkUnsettingEnvironmentVariableForAppStatus(
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public void checkSettingEnvironmentVariableForAppStatusNG(CfRequestConfig cfRequestConfig, boolean activeStatus,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }

  @Override
  public void checkSettingEnvironmentVariableForAppStatus(CfRequestConfig cfRequestConfig, boolean activeStatus,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    throw new PivotalClientApiException("PCF operations not supported by this API.");
  }
}

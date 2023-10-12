/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.LogCallback;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfCreateApplicationRequestData;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CfRunPluginScriptRequestData;

import java.util.List;
import java.util.Map;
import org.zeroturnaround.exec.StartedProcess;

@OwnedBy(HarnessTeam.CDP)
public interface CfCliClient {
  /**
   * Push application.
   *
   * @param requestData request data
   * @param logCallback log callback
   * @throws PivotalClientApiException
   */
  void pushAppByCli(CfCreateApplicationRequestData requestData, LogCallback logCallback)
      throws PivotalClientApiException;

  /**
   * Start application.
   *
   * @param cfRequestConfig pcf request config
   * @param logCallback log callback
   * @throws PivotalClientApiException
   */
  void startAppByCli(CfRequestConfig cfRequestConfig, LogCallback logCallback) throws PivotalClientApiException;

  /**
   * Configure Autoscaler service.
   *
   * @param appAutoscalarRequestData application autoscaler request data
   * @param logCallback log callback
   * @throws PivotalClientApiException
   */
  void performConfigureAutoscaler(CfAppAutoscalarRequestData appAutoscalarRequestData, LogCallback logCallback)
      throws PivotalClientApiException;

  /**
   * Change Autoscaler service state.
   *
   * @param appAutoscalarRequestData application autoscaler request data
   * @param logCallback log callback
   * @param enable enable autoscaler
   * @throws PivotalClientApiException
   */
  void changeAutoscalerState(CfAppAutoscalarRequestData appAutoscalarRequestData, LogCallback logCallback,
      boolean enable) throws PivotalClientApiException;

  /**
   * Check whether Autoscaler service attached.
   *
   * @param appAutoscalarRequestData application Autoscaler request data
   * @param logCallback log callback
   * @return is Autoscaler attached
   * @throws PivotalClientApiException
   */
  boolean checkIfAppHasAutoscalerAttached(CfAppAutoscalarRequestData appAutoscalarRequestData, LogCallback logCallback)
      throws PivotalClientApiException;

  /**
   * Check whether Autoscaler service is in expected state.
   *
   * @param appAutoscalarRequestData application Autoscaler request data
   * @param logCallback log callback
   * @return is Autoscaler in expected state
   * @throws PivotalClientApiException
   */
  boolean checkIfAppHasAutoscalerWithExpectedState(
      CfAppAutoscalarRequestData appAutoscalarRequestData, LogCallback logCallback) throws PivotalClientApiException;

  /**
   * Unmap application routes.
   *
   * @param pcfRequestConfig request config
   * @param routes application routes
   * @param logCallback log callback
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void unmapRoutesForApplicationUsingCli(CfRequestConfig pcfRequestConfig, List<String> routes, LogCallback logCallback)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Map application routes.
   *
   * @param pcfRequestConfig request config
   * @param routes application routes
   * @param logCallback log callback
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void mapRoutesForApplicationUsingCli(CfRequestConfig pcfRequestConfig, List<String> routes, LogCallback logCallback)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Run plugin script.
   *
   * @param cfRunPluginScriptRequestData plugin script request data
   * @param logCallback log callback
   * @throws PivotalClientApiException
   */
  void runPcfPluginScript(CfRunPluginScriptRequestData cfRunPluginScriptRequestData, LogCallback logCallback)
      throws PivotalClientApiException;

  Map<String, String> runPcfPluginScriptWithEnvironmentVarInputsAndOutputs(
      CfRunPluginScriptRequestData cfRunPluginScriptRequestData, LogCallback logCallback,
      Map<String, String> inputVariables, List<String> outputVariables) throws PivotalClientApiException;

  /**
   * Set application env variables.
   *
   * @param envVars environment variables
   * @param pcfRequestConfig request config
   * @param logCallback log callback
   * @throws PivotalClientApiException
   */
  void setEnvVariablesForApplication(Map<String, Object> envVars, CfRequestConfig pcfRequestConfig,
      LogCallback logCallback) throws PivotalClientApiException;

  /**
   * Unset application env variables.
   *
   * @param varNames variable names
   * @param pcfRequestConfig request config
   * @param logCallback log callback
   * @throws PivotalClientApiException
   */
  void unsetEnvVariablesForApplication(List<String> varNames, CfRequestConfig pcfRequestConfig, LogCallback logCallback)
      throws PivotalClientApiException;

  /**
   * Stream application logging information.
   *
   * @param pcfRequestConfig request config
   * @param logCallback log callback
   * @return {@link StartedProcess}
   * @throws PivotalClientApiException
   */
  StartedProcess tailLogsForPcf(CfRequestConfig pcfRequestConfig, LogCallback logCallback)
      throws PivotalClientApiException;
}

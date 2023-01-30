/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pcf;

import static io.harness.logging.LogLevel.INFO;
import static io.harness.pcf.model.PcfConstants.PIVOTAL_CLOUD_FOUNDRY_CLIENT_EXCEPTION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.LogCallback;
import io.harness.pcf.model.CfCreateApplicationRequestData;
import io.harness.pcf.model.CfRequestConfig;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import org.cloudfoundry.operations.applications.ApplicationDetail;

@OwnedBy(HarnessTeam.CDP)
public class CreateApplicationTask implements Callable<ApplicationDetail> {
  private final CfCliClient cfCliClient;
  private final CfSdkClient cfSdkClient;
  private final CfCreateApplicationRequestData requestData;
  private final LogCallback createApplicationLogCallback;

  public CreateApplicationTask(CfCliClient cfCliClient, CfSdkClient cfSdkClient,
      CfCreateApplicationRequestData requestData, LogCallback createApplicationLogCallback) {
    this.cfCliClient = cfCliClient;
    this.cfSdkClient = cfSdkClient;
    this.requestData = requestData;
    this.createApplicationLogCallback = createApplicationLogCallback;
  }

  @Override
  public ApplicationDetail call() throws PivotalClientApiException {
    try {
      CfRequestConfig cfRequestConfig = requestData.getCfRequestConfig();
      if (cfRequestConfig.isUseCFCLI()) {
        cfCliClient.pushAppByCli(requestData, createApplicationLogCallback);
        createApplicationLogCallback.saveExecutionLog("Push Command Ran Successfully", INFO);
      } else {
        Path path = Paths.get(requestData.getManifestFilePath());
        cfSdkClient.pushAppBySdk(cfRequestConfig, path, createApplicationLogCallback);
        createApplicationLogCallback.saveExecutionLog("Push Command Ran Successfully", INFO);
      }
      return cfSdkClient.getApplicationByName(requestData.getCfRequestConfig());
    } catch (Exception e) {
      throw new PivotalClientApiException(PIVOTAL_CLOUD_FOUNDRY_CLIENT_EXCEPTION + ExceptionUtils.getMessage(e), e);
    }
  }
}

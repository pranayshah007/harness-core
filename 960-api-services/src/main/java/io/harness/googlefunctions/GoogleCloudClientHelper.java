/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.googlefunctions;

import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.cloud.functions.v1.CloudFunctionsServiceClient;
import com.google.cloud.functions.v1.CloudFunctionsServiceSettings;
import com.google.cloud.functions.v2.FunctionServiceClient;
import com.google.cloud.functions.v2.FunctionServiceSettings;
import com.google.cloud.run.v2.RevisionsClient;
import com.google.cloud.run.v2.RevisionsSettings;
import com.google.cloud.run.v2.ServicesClient;
import com.google.cloud.run.v2.ServicesSettings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class GoogleCloudClientHelper {
  @Inject private GcpCredentialsHelper gcpCredentialsHelper;

  public FunctionServiceClient getFunctionsClient(GcpInternalConfig gcpInternalConfig) throws IOException {
    CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(gcpCredentialsHelper.getGoogleCredentials(
        gcpInternalConfig.getServiceAccountKeyFileContent(), gcpInternalConfig.isUseDelegate));
    FunctionServiceSettings.Builder functionServiceSettingsBuilder =
        FunctionServiceSettings.newBuilder().setCredentialsProvider(credentialsProvider);
    return FunctionServiceClient.create(functionServiceSettingsBuilder.build());
  }

  public CloudFunctionsServiceClient getFunctionGenOneClient(GcpInternalConfig gcpInternalConfig) throws IOException {
    CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(gcpCredentialsHelper.getGoogleCredentials(
        gcpInternalConfig.getServiceAccountKeyFileContent(), gcpInternalConfig.isUseDelegate));
    CloudFunctionsServiceSettings.Builder cloudFunctionsServiceSettingsBuilder =
        CloudFunctionsServiceSettings.newBuilder().setCredentialsProvider(credentialsProvider);
    return CloudFunctionsServiceClient.create(cloudFunctionsServiceSettingsBuilder.build());
  }

  public RevisionsClient getRevisionsClient(GcpInternalConfig gcpInternalConfig) throws IOException {
    CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(gcpCredentialsHelper.getGoogleCredentials(
        gcpInternalConfig.getServiceAccountKeyFileContent(), gcpInternalConfig.isUseDelegate));
    RevisionsSettings.Builder revisionSettingBuilder =
        RevisionsSettings.newBuilder().setCredentialsProvider(credentialsProvider);
    return RevisionsClient.create(revisionSettingBuilder.build());
  }

  public ServicesClient getServicesClient(GcpInternalConfig gcpInternalConfig) throws IOException {
    CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(gcpCredentialsHelper.getGoogleCredentials(
        gcpInternalConfig.getServiceAccountKeyFileContent(), gcpInternalConfig.isUseDelegate));
    ServicesSettings.Builder serviceSettingBuilder =
        ServicesSettings.newBuilder().setCredentialsProvider(credentialsProvider);
    return ServicesClient.create(serviceSettingBuilder.build());
  }

  public void logCall(String client, String method) {
    log.info("Google Cloud Call: client: {}, method: {}", client, method);
  }

  public void logError(String client, String method, String errorMessage) {
    log.error("Google Cloud Call: client: {}, method: {}, error: {}", client, method, errorMessage);
  }

  public void handleException(Exception exception) {
    // todo: add more cases
    throw new InvalidRequestException(exception.getMessage(), exception, USER);
  }
}

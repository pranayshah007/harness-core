/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gcpcli;

import static io.harness.k8s.K8sConstants.GCP_AUTH_CMD;
import static io.harness.k8s.kubectl.Utils.encloseWithQuotesIfNeeded;

import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.k8s.model.kubeconfig.KubeConfigAuthPluginHelper;
import io.harness.logging.LogCallback;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class GcpCliClient {
  private String gcloudPath;

  private GcpCliClient(String gcloudPath) {
    this.gcloudPath = gcloudPath;
  }

  public static GcpCliClient client(String gcloudPath) {
    return new GcpCliClient(gcloudPath);
  }

  public VersionCommand version() {
    return new VersionCommand(this);
  }

  public AuthCommand auth() {
    return new AuthCommand(this);
  }

  public String command() {
    StringBuilder command = new StringBuilder(128);
    if (StringUtils.isNotBlank(gcloudPath)) {
      command.append(encloseWithQuotesIfNeeded(gcloudPath)).append(' ');
    } else {
      command.append("gcloud ");
    }
    return command.toString();
  }

  public static String option(Option type, String value) {
    return type.toString() + " " + value + " ";
  }

  public static String option(Option type) {
    return type.toString() + " ";
  }

  public static void loginToGcpCluster(String keyFile, Map<String, String> env, LogCallback logCallback) {
    String gcpCliClientVersionCommand = GcpCliClient.client(GCP_AUTH_CMD).version().command();
    if (KubeConfigAuthPluginHelper.runCommand(gcpCliClientVersionCommand, logCallback, env)) {
      boolean isGCloudLoginSuccess = KubeConfigAuthPluginHelper.runCommand(getAuthCommand(keyFile), logCallback, env);
      if (!isGCloudLoginSuccess) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Install google-cloud-cli on delegate and configure gcloud as path variable https://cloud.google.com/sdk/docs/install#rpm",
            "gke-gcloud-auth-plugin requires to authorize gcloud to access the Cloud Platform with Google user credentials ",
            new InvalidRequestException("%s binary not found. Please install google-cloud-cli on delegate."));
      }
    }
  }

  private static String getAuthCommand(String keyFile) {
    GcpCliClient gcpCliClient = GcpCliClient.client(GCP_AUTH_CMD);
    return gcpCliClient.auth().keyFile(keyFile).command();
  }
}

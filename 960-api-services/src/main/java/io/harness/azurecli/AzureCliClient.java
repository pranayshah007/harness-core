/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azurecli;

import static io.harness.azure.model.AzureConstants.AZURE_CLI_CMD;
import static io.harness.azure.model.AzureConstants.AZURE_CONFIG_DIR;
import static io.harness.azure.model.AzureConstants.AZURE_LOGIN_CONFIG_DIR_PATH;
import static io.harness.k8s.kubectl.Utils.encloseWithQuotesIfNeeded;

import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.k8s.model.kubeconfig.KubeConfigAuthPluginHelper;
import io.harness.logging.LogCallback;

import java.nio.file.Paths;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class AzureCliClient {
  private final String azPath;

  private AzureCliClient(String azPath) {
    this.azPath = azPath;
  }

  public static AzureCliClient client(String azPath) {
    return new AzureCliClient(azPath);
  }

  public VersionCommand version() {
    return new VersionCommand(this);
  }

  public AuthCommand auth() {
    return new AuthCommand(this);
  }

  public String command() {
    StringBuilder command = new StringBuilder(128);
    if (StringUtils.isNotBlank(azPath)) {
      command.append(encloseWithQuotesIfNeeded(azPath)).append(' ');
    } else {
      command.append("az ");
    }
    return command.toString();
  }

  public static String option(Option type, String value) {
    return type.toString() + " " + value + " ";
  }

  public static String flag(AuthType type) {
    return "--" + type.toString() + " ";
  }

  public static void loginToAksCluster(
      AzureConfig azureConfig, Map<String, String> env, String workingDirectory, LogCallback logCallback) {
    String azureCliClientVersionCommand = AzureCliClient.client(AZURE_CLI_CMD).version().command();
    if (KubeConfigAuthPluginHelper.runCommand(azureCliClientVersionCommand, logCallback, env)
        && StringUtils.isNotEmpty(workingDirectory)) {
      env.put(AZURE_CONFIG_DIR,
          Paths.get(workingDirectory, AZURE_LOGIN_CONFIG_DIR_PATH).normalize().toAbsolutePath().toString());
      boolean isAzureCliInstalled =
          KubeConfigAuthPluginHelper.runCommand(getAuthCommand(azureConfig), logCallback, env);
      if (!isAzureCliInstalled) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Install Azure CLI on delegate and configure az as path variable https://learn.microsoft.com/en-us/cli/azure/install-azure-cli",
            "Kubelogin does not support cert in PEM format so we require azure cli to login to AKS cluster.",
            new InvalidRequestException("%s binary not found. Please install Azure CLI on delegate."));
      }
    }
  }

  private static String getAuthCommand(AzureConfig azureConfig) {
    AzureCliClient azureCliClient = AzureCliClient.client(AZURE_CLI_CMD);
    if (AzureAuthenticationType.SERVICE_PRINCIPAL_CERT == azureConfig.getAzureAuthenticationType()) {
      return azureCliClient.auth()
          .authType(AuthType.SERVICE_PRINCIPAL)
          .clientId(azureConfig.getClientId())
          .certPath(azureConfig.getCertFilePath())
          .tenantId(azureConfig.getTenantId())
          .command();
    }
    throw new UnsupportedOperationException(
        String.format("%s auth type is not supported for AzureCli login.", azureConfig.getAzureAuthenticationType()));
  }
}

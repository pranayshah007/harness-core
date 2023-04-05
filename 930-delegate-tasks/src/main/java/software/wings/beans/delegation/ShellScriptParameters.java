/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.delegation;

import static io.harness.expression.Expression.ALLOW_SECRETS;
import static io.harness.expression.Expression.DISALLOW_SECRETS;
import static io.harness.k8s.K8sConstants.HARNESS_KUBE_CONFIG_PATH;
import static io.harness.shell.SshSessionConfig.Builder.aSshSessionConfig;

import static java.lang.Boolean.FALSE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.mixin.ProcessExecutorCapabilityGenerator;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.AccessType;
import io.harness.shell.AuthenticationScheme;
import io.harness.shell.KerberosConfig;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutorConfig;
import io.harness.shell.SshSessionConfig;

import software.wings.beans.AzureConfig;
import software.wings.beans.ConnectionType;
import software.wings.beans.GcpConfig;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.beans.dto.SettingAttribute;
import software.wings.delegatetasks.validation.capabilities.ShellConnectionCapability;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManagementDelegateService;
import software.wings.settings.SettingValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.apache.commons.lang3.StringUtils;

@Value
@Builder
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class ShellScriptParameters implements TaskParameters, ActivityAccess, ExecutionCapabilityDemander {
  public static final String CommandUnit = "Execute";

  private String accountId;
  private final String appId;
  private final String activityId;
  @Expression(DISALLOW_SECRETS) final String host;
  private final String userName;
  private final ConnectionType connectionType;
  private final List<EncryptedDataDetail> keyEncryptedDataDetails;
  private final WinRmConnectionAttributes winrmConnectionAttributes;
  private final List<EncryptedDataDetail> winrmConnectionEncryptedDataDetails;
  private final ContainerServiceParams containerServiceParams;
  private final Map<String, String> serviceVariables;
  private final Map<String, String> safeDisplayServiceVariables;
  private final Map<String, String> environment;
  private final String workingDirectory;
  private final ScriptType scriptType;
  @Expression(ALLOW_SECRETS) @NonFinal @Setter String script;
  private final boolean executeOnDelegate;
  private final String outputVars;
  private final String secretOutputVars;
  private final HostConnectionAttributes hostConnectionAttributes;
  private final String keyPath;
  private final boolean keyless;
  private final Integer port;
  private final AccessType accessType;
  private final AuthenticationScheme authenticationScheme;
  private final KerberosConfig kerberosConfig;
  private final String keyName;
  private final boolean localOverrideFeatureFlag;
  private final boolean saveExecutionLogs;
  boolean disableWinRMCommandEncodingFFSet; // DISABLE_WINRM_COMMAND_ENCODING
  boolean winrmScriptCommandSplit; // WINRM_SCRIPT_COMMAND_SPLIT
  boolean disableWinRMEnvVariables; // stop passing service variables as env variables
  private boolean isVaultSSH;
  private String role;
  private String publicKey;
  private SSHVaultConfig sshVaultConfig;
  private Boolean includeInfraSelectors;
  private boolean enableJSchLogs;
  private Integer sshTimeOut;

  private Map<String, String> getResolvedEnvironmentVariables() {
    Map<String, String> resolvedEnvironment = new HashMap<>();

    if (environment != null) {
      resolvedEnvironment.putAll(environment);
    }

    if (serviceVariables != null) {
      resolvedEnvironment.putAll(serviceVariables);
    }

    return resolvedEnvironment;
  }

  public SshSessionConfig sshSessionConfig(EncryptionService encryptionService,
      SecretManagementDelegateService secretManagementDelegateService) throws IOException {
    encryptionService.decrypt(hostConnectionAttributes, keyEncryptedDataDetails, false);
    if (isVaultSSH) {
      secretManagementDelegateService.signPublicKey(hostConnectionAttributes, sshVaultConfig);
    }
    SshSessionConfig.Builder sshSessionConfigBuilder = aSshSessionConfig();
    sshSessionConfigBuilder.withAccountId(accountId)
        .withAppId(appId)
        .withExecutionId(activityId)
        .withHost(host)
        .withUserName(userName)
        .withKeyPath(keyPath)
        .withKeyLess(keyless)
        .withWorkingDirectory(workingDirectory)
        .withCommandUnitName(CommandUnit)
        .withPort(port)
        .withKeyName(keyName)
        .withAccessType(accessType)
        .withAuthenticationScheme(authenticationScheme)
        .withKerberosConfig(kerberosConfig)
        .withKey(hostConnectionAttributes.getKey())
        .withKeyPassphrase(hostConnectionAttributes.getPassphrase())
        .withSshPassword(hostConnectionAttributes.getSshPassword())
        .withPassword(hostConnectionAttributes.getKerberosPassword())
        .withVaultSSH(isVaultSSH)
        .withSignedPublicKey(hostConnectionAttributes.getSignedPublicKey())
        .withPublicKey(hostConnectionAttributes.getPublicKey())
        .withUseSshj(hostConnectionAttributes.isUseSshj())
        .withUseSshClient(hostConnectionAttributes.isUseSshClient());
    return sshSessionConfigBuilder.build();
  }

  public WinRmSessionConfig winrmSessionConfig(EncryptionService encryptionService) throws IOException {
    encryptionService.decrypt(winrmConnectionAttributes, winrmConnectionEncryptedDataDetails, false);

    return WinRmSessionConfig.builder()
        .accountId(accountId)
        .appId(appId)
        .executionId(activityId)
        .commandUnitName(CommandUnit)
        .hostname(host)
        .authenticationScheme(winrmConnectionAttributes.getAuthenticationScheme())
        .domain(winrmConnectionAttributes.getDomain())
        .username(winrmConnectionAttributes.getUsername())
        .password(winrmConnectionAttributes.isUseKeyTab() ? StringUtils.EMPTY
                                                          : String.valueOf(winrmConnectionAttributes.getPassword()))
        .useKeyTab(winrmConnectionAttributes.isUseKeyTab())
        .keyTabFilePath(winrmConnectionAttributes.getKeyTabFilePath())
        .port(winrmConnectionAttributes.getPort())
        .useSSL(winrmConnectionAttributes.isUseSSL())
        .skipCertChecks(winrmConnectionAttributes.isSkipCertChecks())
        .workingDirectory(workingDirectory)
        .environment(disableWinRMEnvVariables ? Collections.emptyMap() : getResolvedEnvironmentVariables())
        .useNoProfile(winrmConnectionAttributes.isUseNoProfile())
        .commandParameters(winrmConnectionAttributes.getParameters())
        .build();
  }

  public ShellExecutorConfig processExecutorConfig(
      ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper, EncryptionService encryptionService) {
    String kubeConfigContent = (containerServiceParams != null) && containerServiceParams.isKubernetesClusterConfig()
        ? containerDeploymentDelegateHelper.getKubeConfigFileContent(containerServiceParams)
        : "";
    char[] serviceAccountKeyFileContent = null;
    if (isGcpDeployment(containerServiceParams)) {
      GcpConfig gcpConfig = (GcpConfig) containerServiceParams.getSettingAttribute().getValue();
      List<EncryptedDataDetail> encryptionDetails = containerServiceParams.getEncryptionDetails();
      encryptionService.decrypt(gcpConfig, encryptionDetails, false);
      serviceAccountKeyFileContent = gcpConfig.getServiceAccountKeyFileContent();
    }
    return ShellExecutorConfig.builder()
        .accountId(accountId)
        .appId(appId)
        .executionId(activityId)
        .commandUnitName(CommandUnit)
        .workingDirectory(workingDirectory)
        .environment(getResolvedEnvironmentVariables())
        .kubeConfigContent(kubeConfigContent)
        .scriptType(scriptType)
        .gcpKeyFileContent(serviceAccountKeyFileContent)
        .build();
  }

  private boolean isGcpDeployment(ContainerServiceParams containerServiceParams) {
    return containerServiceParams != null && containerServiceParams.getSettingAttribute() != null
        && (containerServiceParams.getSettingAttribute().getValue() instanceof GcpConfig);
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();

    if (executeOnDelegate) {
      if (containerServiceParams != null && includeInfraSelectors != FALSE) {
        SettingAttribute settingAttribute = containerServiceParams.getSettingAttribute();
        if (settingAttribute != null) {
          SettingValue value = settingAttribute.getValue();
          boolean isKubernetes =
              value instanceof GcpConfig || value instanceof AzureConfig || value instanceof KubernetesClusterConfig;
          boolean useKubernetesDelegate =
              value instanceof KubernetesClusterConfig && ((KubernetesClusterConfig) value).isUseKubernetesDelegate();
          if (useKubernetesDelegate || (isKubernetes && script.contains(HARNESS_KUBE_CONFIG_PATH))) {
            return containerServiceParams.fetchRequiredExecutionCapabilities(maskingEvaluator);
          }
        }
      }

      if (scriptType == ScriptType.POWERSHELL) {
        executionCapabilities.add(ProcessExecutorCapabilityGenerator.buildProcessExecutorCapability(
            "DELEGATE_POWERSHELL", Arrays.asList("/bin/sh", "-c", "pwsh -Version")));
      }
      return executionCapabilities;
    }
    executionCapabilities.add(ShellConnectionCapability.builder().shellScriptParameters(this).build());
    return executionCapabilities;
  }
}

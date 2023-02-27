/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability;
import static io.harness.govern.Switch.unhandled;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_SIMPLE_HTTP_CONNECTIVITY_URL;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.CommandExecutionData;

import software.wings.api.DeploymentType;
import software.wings.beans.AppContainer;
import software.wings.beans.ExecutionCredential;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SSHExecutionCredential;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactFile;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.dto.SettingAttribute;
import software.wings.beans.infrastructure.Host;
import software.wings.delegatetasks.validation.capabilities.BasicValidationInfo;
import software.wings.delegatetasks.validation.capabilities.SSHHostValidationCapability;
import software.wings.delegatetasks.validation.capabilities.WinrmHostValidationCapability;
import software.wings.settings.SettingValue;
import software.wings.utils.MappingUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@Slf4j
@OwnedBy(CDC)
public class CommandExecutionContext implements ExecutionCapabilityDemander {
  private String accountId;
  private String envId;
  private Host host;
  private String appId;
  private String activityId;
  private String serviceName;
  private String runtimePath;
  private String stagingPath;
  private String backupPath;
  private String windowsRuntimePath;
  private String serviceTemplateId;
  private ExecutionCredential executionCredential;
  private AppContainer appContainer;
  private List<ArtifactFile> artifactFiles;
  private Map<String, String> serviceVariables = new HashMap<>();
  private Map<String, String> safeDisplayServiceVariables = new HashMap<>();
  private Map<String, String> envVariables = new HashMap<>();
  private SettingAttribute hostConnectionAttributes;
  private List<EncryptedDataDetail> hostConnectionCredentials;
  private SettingAttribute bastionConnectionAttributes;
  private List<EncryptedDataDetail> bastionConnectionCredentials;
  private WinRmConnectionAttributes winrmConnectionAttributes;
  private List<EncryptedDataDetail> winrmConnectionEncryptedDataDetails;
  private ArtifactStreamAttributes artifactStreamAttributes;
  private SettingAttribute cloudProviderSetting;
  private List<EncryptedDataDetail> cloudProviderCredentials;
  private CodeDeployParams codeDeployParams;
  private ContainerSetupParams containerSetupParams;
  private ContainerResizeParams containerResizeParams;
  private Map<String, String> metadata = new HashMap<>();
  private CommandExecutionData commandExecutionData;
  private Integer timeout;
  private String deploymentType;
  private List<EncryptedDataDetail> artifactServerEncryptedDataDetails;
  private boolean inlineSshCommand;
  private boolean executeOnDelegate;
  private boolean disableWinRMCommandEncodingFFSet; // DISABLE_WINRM_COMMAND_ENCODING
  private boolean winrmScriptCommandSplit; // WINRM_SCRIPT_COMMAND_SPLIT
  private boolean disableWinRMEnvVariables; // stop passing service variables as env variables
  private boolean useWinRMKerberosUniqueCacheFile;
  private List<String> delegateSelectors;

  // new fields for multi artifact
  private Map<String, Artifact> multiArtifactMap;
  private Map<String, ArtifactStreamAttributes> artifactStreamAttributesMap;
  private boolean multiArtifact;
  private Map<String, List<EncryptedDataDetail>> artifactServerEncryptedDataDetailsMap;
  private String artifactFileName;
  private SSHVaultConfig sshVaultConfig;

  public CommandExecutionContext() {}

  /**
   * Instantiates a new Command execution context.
   *
   * @param other the other
   */
  public CommandExecutionContext(CommandExecutionContext other) {
    accountId = other.accountId;
    envId = other.envId;
    host = other.host;
    appId = other.appId;
    activityId = other.activityId;
    runtimePath = other.runtimePath;
    stagingPath = other.stagingPath;
    backupPath = other.backupPath;
    windowsRuntimePath = other.windowsRuntimePath;
    serviceTemplateId = other.serviceTemplateId;
    executionCredential = other.executionCredential;
    appContainer = other.appContainer;
    artifactFiles = other.artifactFiles;
    serviceVariables = other.serviceVariables;
    safeDisplayServiceVariables = other.safeDisplayServiceVariables;
    envVariables = other.envVariables;
    hostConnectionAttributes = other.hostConnectionAttributes;
    hostConnectionCredentials = other.hostConnectionCredentials;
    bastionConnectionAttributes = other.bastionConnectionAttributes;
    bastionConnectionCredentials = other.bastionConnectionCredentials;
    winrmConnectionAttributes = other.winrmConnectionAttributes;
    winrmConnectionEncryptedDataDetails = other.winrmConnectionEncryptedDataDetails;
    artifactStreamAttributes = other.artifactStreamAttributes;
    cloudProviderSetting = other.cloudProviderSetting;
    cloudProviderCredentials = other.cloudProviderCredentials;
    codeDeployParams = other.codeDeployParams;
    containerSetupParams = other.containerSetupParams;
    containerResizeParams = other.containerResizeParams;
    metadata = other.metadata;
    commandExecutionData = other.commandExecutionData;
    timeout = other.timeout;
    deploymentType = other.deploymentType;
    artifactServerEncryptedDataDetails = other.artifactServerEncryptedDataDetails;
    inlineSshCommand = other.inlineSshCommand;
    executeOnDelegate = other.executeOnDelegate;
    artifactStreamAttributesMap = other.artifactStreamAttributesMap;
    multiArtifactMap = other.multiArtifactMap;
    multiArtifact = other.multiArtifact;
    artifactServerEncryptedDataDetailsMap = other.artifactServerEncryptedDataDetailsMap;
    artifactFileName = other.artifactFileName;
    delegateSelectors = other.delegateSelectors;
    sshVaultConfig = other.sshVaultConfig;
  }

  public CommandExecutionContext(String accountId, String envId, Host host, String appId, String activityId,
      String serviceName, String runtimePath, String stagingPath, String backupPath, String windowsRuntimePath,
      String serviceTemplateId, ExecutionCredential executionCredential, AppContainer appContainer,
      List<ArtifactFile> artifactFiles, Map<String, String> serviceVariables,
      Map<String, String> safeDisplayServiceVariables, Map<String, String> envVariables,
      SettingAttribute hostConnectionAttributes, List<EncryptedDataDetail> hostConnectionCredentials,
      SettingAttribute bastionConnectionAttributes, List<EncryptedDataDetail> bastionConnectionCredentials,
      WinRmConnectionAttributes winrmConnectionAttributes,
      List<EncryptedDataDetail> winrmConnectionEncryptedDataDetails, ArtifactStreamAttributes artifactStreamAttributes,
      SettingAttribute cloudProviderSetting, List<EncryptedDataDetail> cloudProviderCredentials,
      CodeDeployParams codeDeployParams, ContainerSetupParams containerSetupParams,
      ContainerResizeParams containerResizeParams, Map<String, String> metadata,
      CommandExecutionData commandExecutionData, Integer timeout, String deploymentType,
      List<EncryptedDataDetail> artifactServerEncryptedDataDetails, boolean inlineSshCommand, boolean executeOnDelegate,
      boolean disableWinRMCommandEncodingFFSet, boolean winrmScriptCommandSplit, boolean disableWinRMEnvVariables,
      boolean useWinRMKerberosUniqueCacheFile, List<String> delegateSelectors, Map<String, Artifact> multiArtifactMap,
      Map<String, ArtifactStreamAttributes> artifactStreamAttributesMap, boolean multiArtifact,
      Map<String, List<EncryptedDataDetail>> artifactServerEncryptedDataDetailsMap, String artifactFileName,
      SSHVaultConfig sshVaultConfig) {
    this.accountId = accountId;
    this.envId = envId;
    this.host = host;
    this.appId = appId;
    this.activityId = activityId;
    this.serviceName = serviceName;
    this.runtimePath = runtimePath;
    this.stagingPath = stagingPath;
    this.backupPath = backupPath;
    this.windowsRuntimePath = windowsRuntimePath;
    this.serviceTemplateId = serviceTemplateId;
    this.executionCredential = executionCredential;
    this.appContainer = appContainer;
    this.artifactFiles = artifactFiles;
    this.serviceVariables = serviceVariables == null ? new HashMap<>() : serviceVariables;
    this.safeDisplayServiceVariables =
        safeDisplayServiceVariables == null ? new HashMap<>() : safeDisplayServiceVariables;
    this.envVariables = envVariables == null ? new HashMap<>() : envVariables;
    this.hostConnectionAttributes = hostConnectionAttributes;
    this.hostConnectionCredentials = hostConnectionCredentials;
    this.bastionConnectionAttributes = bastionConnectionAttributes;
    this.bastionConnectionCredentials = bastionConnectionCredentials;
    this.winrmConnectionAttributes = winrmConnectionAttributes;
    this.winrmConnectionEncryptedDataDetails = winrmConnectionEncryptedDataDetails;
    this.artifactStreamAttributes = artifactStreamAttributes;
    this.cloudProviderSetting = cloudProviderSetting;
    this.cloudProviderCredentials = cloudProviderCredentials;
    this.codeDeployParams = codeDeployParams;
    this.containerSetupParams = containerSetupParams;
    this.containerResizeParams = containerResizeParams;
    this.metadata = metadata;
    this.commandExecutionData = commandExecutionData;
    this.timeout = timeout;
    this.deploymentType = deploymentType;
    this.artifactServerEncryptedDataDetails = artifactServerEncryptedDataDetails;
    this.inlineSshCommand = inlineSshCommand;
    this.executeOnDelegate = executeOnDelegate;
    this.disableWinRMCommandEncodingFFSet = disableWinRMCommandEncodingFFSet;
    this.winrmScriptCommandSplit = winrmScriptCommandSplit;
    this.disableWinRMEnvVariables = disableWinRMEnvVariables;
    this.useWinRMKerberosUniqueCacheFile = useWinRMKerberosUniqueCacheFile;
    this.delegateSelectors = delegateSelectors;
    this.multiArtifactMap = multiArtifactMap;
    this.artifactStreamAttributesMap = artifactStreamAttributesMap;
    this.multiArtifact = multiArtifact;
    this.artifactServerEncryptedDataDetailsMap = artifactServerEncryptedDataDetailsMap;
    this.artifactFileName = artifactFileName;
    this.sshVaultConfig = sshVaultConfig;
  }

  /**
   * Add env variables.
   *
   * @param envVariables the env variables
   */
  public void addEnvVariables(Map<String, String> envVariables) {
    for (Entry<String, String> envVariable : envVariables.entrySet()) {
      this.envVariables.put(envVariable.getKey(), evaluateVariable(envVariable.getValue()));
    }
  }

  /**
   * Evaluate variable string.
   *
   * @param text the text
   * @return the string
   */
  protected String evaluateVariable(String text) {
    if (isNotBlank(text)) {
      for (Entry<String, String> entry : envVariables.entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        try {
          text = text.replaceAll("\\$\\{" + key + "}", value);
          text = text.replaceAll("\\$" + key, value);
        } catch (IllegalArgumentException exception) {
          log.warn(format("ENV variable evaluation failed for %s with error: %s. Skipping evaluation.", key,
              exception.getMessage()));
        }
      }
    }
    return text;
  }

  public WinRmSessionConfig winrmSessionConfig(String commandUnitName, String commandPath) {
    return WinRmSessionConfig.builder()
        .accountId(accountId)
        .appId(appId)
        .executionId(activityId)
        .commandUnitName(commandUnitName)
        .hostname(host.getPublicDns())
        .authenticationScheme(winrmConnectionAttributes.getAuthenticationScheme())
        .domain(winrmConnectionAttributes.getDomain())
        .username(winrmConnectionAttributes.getUsername())
        .password(winrmConnectionAttributes.isUseKeyTab() ? StringUtils.EMPTY
                                                          : String.valueOf(winrmConnectionAttributes.getPassword()))
        .timeout(timeout)
        .port(winrmConnectionAttributes.getPort())
        .useSSL(winrmConnectionAttributes.isUseSSL())
        .skipCertChecks(winrmConnectionAttributes.isSkipCertChecks())
        .workingDirectory(commandPath)
        .environment(envVariables == null || disableWinRMEnvVariables ? Collections.emptyMap() : envVariables)
        .useKeyTab(winrmConnectionAttributes.isUseKeyTab())
        .keyTabFilePath(winrmConnectionAttributes.getKeyTabFilePath())
        .useNoProfile(winrmConnectionAttributes.isUseNoProfile())
        .useKerberosUniqueCacheFile(useWinRMKerberosUniqueCacheFile)
        .commandParameters(winrmConnectionAttributes.getParameters())
        .build();
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    String region = null;
    DeploymentType dType = DeploymentType.valueOf(getDeploymentType());
    List<ExecutionCapability> capabilities = new ArrayList<>();
    switch (dType) {
      case KUBERNETES:
        SettingValue config = cloudProviderSetting.getValue();
        if (config instanceof KubernetesClusterConfig && ((KubernetesClusterConfig) config).isUseKubernetesDelegate()) {
          capabilities.addAll(config.fetchRequiredExecutionCapabilities(maskingEvaluator));
        }

        String masterUrl = null;
        if (containerSetupParams instanceof KubernetesSetupParams) {
          masterUrl = ((KubernetesSetupParams) containerSetupParams).getMasterUrl();
        } else if (containerResizeParams instanceof KubernetesResizeParams) {
          masterUrl = ((KubernetesResizeParams) containerResizeParams).getMasterUrl();
        }
        if (isNotBlank(masterUrl)) {
          capabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
              masterUrl, maskingEvaluator));
        }

        return capabilities;
      case WINRM:
        capabilities.add(WinrmHostValidationCapability.builder()
                             .validationInfo(BasicValidationInfo.builder()
                                                 .accountId(accountId)
                                                 .appId(appId)
                                                 .activityId(activityId)
                                                 .executeOnDelegate(executeOnDelegate)
                                                 .publicDns(host == null ? null : host.getPublicDns())
                                                 .build())
                             .winRmConnectionAttributes(winrmConnectionAttributes)
                             .winrmConnectionEncryptedDataDetails(winrmConnectionEncryptedDataDetails)
                             .build());
        if (isNotEmpty(delegateSelectors)) {
          capabilities.add(
              SelectorCapability.builder().selectors(delegateSelectors.stream().collect(Collectors.toSet())).build());
        }
        return capabilities;
      case SSH:
        capabilities.add(SSHHostValidationCapability.builder()
                             .validationInfo(BasicValidationInfo.builder()
                                                 .accountId(accountId)
                                                 .appId(appId)
                                                 .activityId(activityId)
                                                 .executeOnDelegate(executeOnDelegate)
                                                 .publicDns(host == null ? null : host.getPublicDns())
                                                 .build())
                             .hostConnectionAttributes(hostConnectionAttributes)
                             .bastionConnectionAttributes(bastionConnectionAttributes)
                             .hostConnectionCredentials(hostConnectionCredentials)
                             .bastionConnectionCredentials(bastionConnectionCredentials)
                             .sshExecutionCredential((SSHExecutionCredential) executionCredential)
                             .sshVaultConfig(sshVaultConfig)
                             .build());
        if (isNotEmpty(delegateSelectors)) {
          capabilities.add(
              SelectorCapability.builder().selectors(delegateSelectors.stream().collect(Collectors.toSet())).build());
        }
        return capabilities;
      case AWS_CODEDEPLOY:
        return singletonList(
            buildHttpConnectionExecutionCapability(AWS_SIMPLE_HTTP_CONNECTIVITY_URL, maskingEvaluator));
      case ECS:
      case AMI:
      case AWS_LAMBDA:
        return emptyList();
      default:
        unhandled(deploymentType);
        throw new WingsException(ErrorCode.INVALID_ARGUMENT)
            .addParam("args", "deploymentType is not handled: " + dType.name());
    }
  }

  public static final class Builder {
    private String accountId;
    private String envId;
    private Host host;
    private String appId;
    private String activityId;
    private String serviceName;
    private String runtimePath;
    private String stagingPath;
    private String backupPath;
    private String windowsRuntimePath;
    private String serviceTemplateId;
    private ExecutionCredential executionCredential;
    private AppContainer appContainer;
    private List<ArtifactFile> artifactFiles;
    private Map<String, String> serviceVariables = new HashMap<>();
    private Map<String, String> safeDisplayServiceVariables = new HashMap<>();
    private Map<String, String> envVariables = new HashMap<>();
    private SettingAttribute hostConnectionAttributes;
    private List<EncryptedDataDetail> hostConnectionCredentials;
    private SettingAttribute bastionConnectionAttributes;
    private List<EncryptedDataDetail> bastionConnectionCredentials;
    private WinRmConnectionAttributes winrmConnectionAttributes;
    private List<EncryptedDataDetail> winrmConnectionEncryptedDataDetails;
    private ArtifactStreamAttributes artifactStreamAttributes;
    private SettingAttribute cloudProviderSetting;
    private List<EncryptedDataDetail> cloudProviderCredentials;
    private CodeDeployParams codeDeployParams;
    private ContainerSetupParams containerSetupParams;
    private ContainerResizeParams containerResizeParams;
    private Map<String, String> metadata = new HashMap<>();
    private CommandExecutionData commandExecutionData;
    private Integer timeout;
    private String deploymentType;
    private List<EncryptedDataDetail> artifactServerEncryptedDataDetails;
    private boolean inlineSshCommand;
    private boolean executeOnDelegate;
    private boolean disableWinRMCommandEncodingFFSet; // DISABLE_WINRM_COMMAND_ENCODING
    private boolean winrmScriptCommandSplit; // WINRM_SCRIPT_COMMAND_SPLIT
    private boolean disableWinRMEnvVariables; // stop passing service variables as env variables
    private List<String> delegateSelectors;

    // new fields for multi artifact
    private Map<String, Artifact> multiArtifactMap;
    private Map<String, ArtifactStreamAttributes> artifactStreamAttributesMap;
    private boolean multiArtifact;
    private Map<String, List<EncryptedDataDetail>> artifactServerEncryptedDataDetailsMap;
    private String artifactFileName;
    private SSHVaultConfig sshVaultConfig;

    private Builder() {}

    public static Builder aCommandExecutionContext() {
      return new Builder();
    }

    public Builder accountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public Builder sshVaultConfig(SSHVaultConfig sshVaultConfig) {
      this.sshVaultConfig = sshVaultConfig;
      return this;
    }

    public Builder envId(String envId) {
      this.envId = envId;
      return this;
    }

    public Builder host(Host host) {
      this.host = host;
      return this;
    }

    public Builder appId(String appId) {
      this.appId = appId;
      return this;
    }

    public Builder activityId(String activityId) {
      this.activityId = activityId;
      return this;
    }

    public Builder runtimePath(String runtimePath) {
      this.runtimePath = runtimePath;
      return this;
    }

    public Builder stagingPath(String stagingPath) {
      this.stagingPath = stagingPath;
      return this;
    }

    public Builder backupPath(String backupPath) {
      this.backupPath = backupPath;
      return this;
    }

    public Builder windowsRuntimePath(String windowsRuntimePath) {
      this.windowsRuntimePath = windowsRuntimePath;
      return this;
    }

    public Builder serviceTemplateId(String serviceTemplateId) {
      this.serviceTemplateId = serviceTemplateId;
      return this;
    }

    public Builder executionCredential(ExecutionCredential executionCredential) {
      this.executionCredential = executionCredential;
      return this;
    }

    public Builder appContainer(AppContainer appContainer) {
      this.appContainer = appContainer;
      return this;
    }

    public Builder artifactFiles(List<ArtifactFile> artifactFiles) {
      this.artifactFiles = artifactFiles;
      return this;
    }

    public Builder serviceVariables(Map<String, String> serviceVariables) {
      this.serviceVariables = serviceVariables;
      return this;
    }

    public Builder safeDisplayServiceVariables(Map<String, String> safeDisplayServiceVariables) {
      this.safeDisplayServiceVariables = safeDisplayServiceVariables;
      return this;
    }

    public Builder envVariables(Map<String, String> envVariables) {
      this.envVariables = envVariables;
      return this;
    }

    public Builder hostConnectionAttributes(SettingAttribute hostConnectionAttributes) {
      this.hostConnectionAttributes = hostConnectionAttributes;
      return this;
    }

    public Builder hostConnectionCredentials(List<EncryptedDataDetail> encryptedDataDetails) {
      hostConnectionCredentials = encryptedDataDetails;
      return this;
    }

    public Builder bastionConnectionAttributes(SettingAttribute bastionConnectionAttributes) {
      this.bastionConnectionAttributes = bastionConnectionAttributes;
      return this;
    }

    public Builder bastionConnectionCredentials(List<EncryptedDataDetail> encryptedDataDetails) {
      bastionConnectionCredentials = encryptedDataDetails;
      return this;
    }

    public Builder winRmConnectionAttributes(WinRmConnectionAttributes winRmConnectionAttributes) {
      winrmConnectionAttributes = winRmConnectionAttributes;
      return this;
    }

    public Builder winrmConnectionEncryptedDataDetails(List<EncryptedDataDetail> winrmConnectionEncryptedDataDetails) {
      this.winrmConnectionEncryptedDataDetails = winrmConnectionEncryptedDataDetails;
      return this;
    }

    public Builder artifactStreamAttributes(ArtifactStreamAttributes artifactStreamAttributes) {
      this.artifactStreamAttributes = artifactStreamAttributes;
      return this;
    }

    public Builder cloudProviderSetting(SettingAttribute cloudProviderSetting) {
      this.cloudProviderSetting = cloudProviderSetting;
      return this;
    }

    public Builder cloudProviderCredentials(List<EncryptedDataDetail> encryptedDataDetails) {
      cloudProviderCredentials = encryptedDataDetails;
      return this;
    }

    public Builder serviceName(String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    public Builder codeDeployParams(CodeDeployParams codeDeployParams) {
      this.codeDeployParams = codeDeployParams;
      return this;
    }

    public Builder containerSetupParams(ContainerSetupParams containerSetupParams) {
      this.containerSetupParams = containerSetupParams;
      return this;
    }

    public Builder containerResizeParams(ContainerResizeParams containerResizeParams) {
      this.containerResizeParams = containerResizeParams;
      return this;
    }

    public Builder metadata(Map<String, String> metadata) {
      this.metadata = MappingUtils.safeCopy(metadata);
      return this;
    }

    public Builder commandExecutionData(CommandExecutionData commandExecutionData) {
      this.commandExecutionData = commandExecutionData;
      return this;
    }

    public Builder timeout(Integer timeout) {
      this.timeout = timeout;
      return this;
    }

    public Builder deploymentType(String deploymentType) {
      this.deploymentType = deploymentType;
      return this;
    }

    public Builder artifactServerEncryptedDataDetails(List<EncryptedDataDetail> artifactServerEncryptedDataDetails) {
      this.artifactServerEncryptedDataDetails = artifactServerEncryptedDataDetails;
      return this;
    }

    public Builder inlineSshCommand(boolean inlineSshCommand) {
      this.inlineSshCommand = inlineSshCommand;
      return this;
    }

    public Builder disableWinRMCommandEncodingFFSet(boolean disableWinRMCommandEncodingFFSet) {
      this.disableWinRMCommandEncodingFFSet = disableWinRMCommandEncodingFFSet;
      return this;
    }

    public Builder winrmScriptCommandSplit(boolean winrmScriptCommandSplit) {
      this.winrmScriptCommandSplit = winrmScriptCommandSplit;
      return this;
    }

    public Builder disableWinRMEnvVariables(boolean disableWinRMEnvVariables) {
      this.disableWinRMEnvVariables = disableWinRMEnvVariables;
      return this;
    }

    public Builder executeOnDelegate(boolean executeOnDelegate) {
      this.executeOnDelegate = executeOnDelegate;
      return this;
    }

    public Builder artifactStreamAttributesMap(Map<String, ArtifactStreamAttributes> artifactStreamAttributesMap) {
      this.artifactStreamAttributesMap = artifactStreamAttributesMap;
      return this;
    }

    public Builder multiArtifactMap(Map<String, Artifact> multiArtifactMap) {
      this.multiArtifactMap = multiArtifactMap;
      return this;
    }

    public Builder multiArtifact(boolean multiArtifact) {
      this.multiArtifact = multiArtifact;
      return this;
    }

    public Builder artifactServerEncryptedDataDetailsMap(
        Map<String, List<EncryptedDataDetail>> artifactServerEncryptedDataDetailsMap) {
      this.artifactServerEncryptedDataDetailsMap = artifactServerEncryptedDataDetailsMap;
      return this;
    }

    public Builder artifactFileName(String artifactFileName) {
      this.artifactFileName = artifactFileName;
      return this;
    }

    public Builder delegateSelectors(List<String> tags) {
      this.delegateSelectors = tags;
      return this;
    }

    public Builder but() {
      return aCommandExecutionContext()
          .accountId(accountId)
          .envId(envId)
          .host(host)
          .appId(appId)
          .activityId(activityId)
          .runtimePath(runtimePath)
          .stagingPath(stagingPath)
          .backupPath(backupPath)
          .windowsRuntimePath(windowsRuntimePath)
          .serviceTemplateId(serviceTemplateId)
          .executionCredential(executionCredential)
          .appContainer(appContainer)
          .artifactFiles(artifactFiles)
          .serviceVariables(serviceVariables)
          .envVariables(envVariables)
          .hostConnectionAttributes(hostConnectionAttributes)
          .bastionConnectionAttributes(bastionConnectionAttributes)
          .hostConnectionCredentials(hostConnectionCredentials)
          .bastionConnectionCredentials(bastionConnectionCredentials)
          .winRmConnectionAttributes(winrmConnectionAttributes)
          .winrmConnectionEncryptedDataDetails(winrmConnectionEncryptedDataDetails)
          .artifactStreamAttributes(artifactStreamAttributes)
          .cloudProviderSetting(cloudProviderSetting)
          .cloudProviderCredentials(cloudProviderCredentials)
          .codeDeployParams(codeDeployParams)
          .metadata(metadata)
          .commandExecutionData(commandExecutionData)
          .containerSetupParams(containerSetupParams)
          .containerResizeParams(containerResizeParams)
          .safeDisplayServiceVariables(safeDisplayServiceVariables)
          .serviceName(serviceName)
          .timeout(timeout)
          .deploymentType(deploymentType)
          .artifactServerEncryptedDataDetails(artifactServerEncryptedDataDetails)
          .inlineSshCommand(inlineSshCommand)
          .executeOnDelegate(executeOnDelegate)
          .artifactStreamAttributesMap(artifactStreamAttributesMap)
          .multiArtifactMap(multiArtifactMap)
          .multiArtifact(multiArtifact)
          .artifactServerEncryptedDataDetailsMap(artifactServerEncryptedDataDetailsMap)
          .artifactFileName(artifactFileName)
          .disableWinRMCommandEncodingFFSet(disableWinRMCommandEncodingFFSet)
          .winrmScriptCommandSplit(winrmScriptCommandSplit)
          .disableWinRMEnvVariables(disableWinRMEnvVariables)
          .delegateSelectors(delegateSelectors)
          .sshVaultConfig(sshVaultConfig);
    }

    public CommandExecutionContext build() {
      CommandExecutionContext commandExecutionContext = new CommandExecutionContext();
      commandExecutionContext.setAccountId(accountId);
      commandExecutionContext.setEnvId(envId);
      commandExecutionContext.setHost(host);
      commandExecutionContext.setAppId(appId);
      commandExecutionContext.setActivityId(activityId);
      commandExecutionContext.setServiceName(serviceName);
      commandExecutionContext.setRuntimePath(runtimePath);
      commandExecutionContext.setStagingPath(stagingPath);
      commandExecutionContext.setBackupPath(backupPath);
      commandExecutionContext.setWindowsRuntimePath(windowsRuntimePath);
      commandExecutionContext.setServiceTemplateId(serviceTemplateId);
      commandExecutionContext.setExecutionCredential(executionCredential);
      commandExecutionContext.setAppContainer(appContainer);
      commandExecutionContext.setArtifactFiles(artifactFiles);
      commandExecutionContext.setServiceVariables(serviceVariables);
      commandExecutionContext.setSafeDisplayServiceVariables(safeDisplayServiceVariables);
      commandExecutionContext.setEnvVariables(envVariables);
      commandExecutionContext.setHostConnectionAttributes(hostConnectionAttributes);
      commandExecutionContext.setHostConnectionCredentials(hostConnectionCredentials);
      commandExecutionContext.setBastionConnectionAttributes(bastionConnectionAttributes);
      commandExecutionContext.setBastionConnectionCredentials(bastionConnectionCredentials);
      commandExecutionContext.setWinrmConnectionAttributes(winrmConnectionAttributes);
      commandExecutionContext.setWinrmConnectionEncryptedDataDetails(winrmConnectionEncryptedDataDetails);
      commandExecutionContext.setArtifactStreamAttributes(artifactStreamAttributes);
      commandExecutionContext.setCloudProviderSetting(cloudProviderSetting);
      commandExecutionContext.setCloudProviderCredentials(cloudProviderCredentials);
      commandExecutionContext.setCodeDeployParams(codeDeployParams);
      commandExecutionContext.setMetadata(metadata);
      commandExecutionContext.setCommandExecutionData(commandExecutionData);
      commandExecutionContext.setContainerSetupParams(containerSetupParams);
      commandExecutionContext.setContainerResizeParams(containerResizeParams);
      commandExecutionContext.setTimeout(timeout);
      commandExecutionContext.setDeploymentType(deploymentType);
      commandExecutionContext.setArtifactServerEncryptedDataDetails(artifactServerEncryptedDataDetails);
      commandExecutionContext.setInlineSshCommand(inlineSshCommand);
      commandExecutionContext.setExecuteOnDelegate(executeOnDelegate);
      commandExecutionContext.setArtifactStreamAttributesMap(artifactStreamAttributesMap);
      commandExecutionContext.setMultiArtifactMap(multiArtifactMap);
      commandExecutionContext.setMultiArtifact(multiArtifact);
      commandExecutionContext.setArtifactServerEncryptedDataDetailsMap(artifactServerEncryptedDataDetailsMap);
      commandExecutionContext.setArtifactFileName(artifactFileName);
      commandExecutionContext.setDisableWinRMCommandEncodingFFSet(disableWinRMCommandEncodingFFSet);
      commandExecutionContext.setWinrmScriptCommandSplit(winrmScriptCommandSplit);
      commandExecutionContext.setDisableWinRMEnvVariables(disableWinRMEnvVariables);
      commandExecutionContext.setDelegateSelectors(delegateSelectors);
      commandExecutionContext.setSshVaultConfig(sshVaultConfig);
      return commandExecutionContext;
    }
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.delegation;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.Expression;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.CommandExecutionData;

import software.wings.beans.AppContainer;
import software.wings.beans.ExecutionCredential;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactFile;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.command.CodeDeployParams;
import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.command.ContainerResizeParams;
import software.wings.beans.command.ContainerSetupParams;
import software.wings.beans.dto.Command;
import software.wings.beans.infrastructure.Host;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDC)
public class CommandParameters extends CommandExecutionContext implements TaskParameters, ActivityAccess {
  @Expression(ALLOW_SECRETS) private Command command;

  @lombok.Builder(builderMethodName = "builderWithCommand")
  public CommandParameters(String accountId, String envId, Host host, String appId, String activityId,
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
      boolean disableWinRMCommandEncodingFFSet, boolean disableWinRMEnvVariables,
      boolean useWinRMKerberosUniqueCacheFile, List<String> delegateSelectors, Map<String, Artifact> multiArtifactMap,
      Map<String, ArtifactStreamAttributes> artifactStreamAttributesMap, boolean multiArtifact,
      Map<String, List<EncryptedDataDetail>> artifactServerEncryptedDataDetailsMap, String artifactFileName,
      SSHVaultConfig sshVaultConfig, Command command) {
    super(accountId, envId, host, appId, activityId, serviceName, runtimePath, stagingPath, backupPath,
        windowsRuntimePath, serviceTemplateId, executionCredential, appContainer, artifactFiles, serviceVariables,
        safeDisplayServiceVariables, envVariables, hostConnectionAttributes, hostConnectionCredentials,
        bastionConnectionAttributes, bastionConnectionCredentials, winrmConnectionAttributes,
        winrmConnectionEncryptedDataDetails, artifactStreamAttributes, cloudProviderSetting, cloudProviderCredentials,
        codeDeployParams, containerSetupParams, containerResizeParams, metadata, commandExecutionData, timeout,
        deploymentType, artifactServerEncryptedDataDetails, inlineSshCommand, executeOnDelegate,
        disableWinRMCommandEncodingFFSet, disableWinRMEnvVariables, useWinRMKerberosUniqueCacheFile, delegateSelectors,
        multiArtifactMap, artifactStreamAttributesMap, multiArtifact, artifactServerEncryptedDataDetailsMap,
        artifactFileName, sshVaultConfig);

    this.command = command;
  }
}

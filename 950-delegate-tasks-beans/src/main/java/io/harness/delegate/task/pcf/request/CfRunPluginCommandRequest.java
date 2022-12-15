/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.PcfConnectivityCapability;
import io.harness.delegate.beans.executioncapability.PcfInstallationCapability;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.Cd1ApplicationAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.pcf.model.CfCliVersion;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

@Data
@ToString(exclude = {"renderedScriptString", "fileDataList", "repoRoot"})
@EqualsAndHashCode(callSuper = false)
@OwnedBy(CDP)
public class CfRunPluginCommandRequest extends CfCommandRequest
    implements TaskParameters, ExecutionCapabilityDemander, ActivityAccess, Cd1ApplicationAccess {
  @Expression(ALLOW_SECRETS) private String renderedScriptString;
  private List<String> filePathsInScript;
  @Expression(ALLOW_SECRETS) private List<FileData> fileDataList;
  private List<EncryptedDataDetail> encryptedDataDetails;
  @Expression(ALLOW_SECRETS) private String repoRoot;
  @NotNull private CfCliVersion cfCliVersion;

  @Builder
  public CfRunPluginCommandRequest(String accountId, String appId, String commandName, String activityId,
      PcfCommandType pcfCommandType, String organization, String space, CfInternalConfig pcfConfig,
      String workflowExecutionId, Integer timeoutIntervalInMin, boolean useCLIForPcfAppCreation,
      boolean enforceSslValidation, boolean useAppAutoscalar, String renderedScriptString,
      List<String> filePathsInScript, List<FileData> fileDataList, List<EncryptedDataDetail> encryptedDataDetails,
      String repoRoot, boolean limitPcfThreads, @NotNull CfCliVersion cfCliVersion) {
    super(accountId, appId, commandName, activityId, pcfCommandType, organization, space, pcfConfig,
        workflowExecutionId, timeoutIntervalInMin, useCLIForPcfAppCreation, enforceSslValidation, useAppAutoscalar,
        limitPcfThreads, cfCliVersion);
    this.renderedScriptString = renderedScriptString;
    this.filePathsInScript = filePathsInScript;
    this.fileDataList = fileDataList;
    this.encryptedDataDetails = encryptedDataDetails;
    this.repoRoot = repoRoot;
    this.cfCliVersion = cfCliVersion;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Arrays.asList(PcfConnectivityCapability.builder().endpointUrl(getPcfConfig().getEndpointUrl()).build(),
        PcfInstallationCapability.builder()
            .criteria(format("Checking that CF CLI version: %s is installed", cfCliVersion))
            .version(cfCliVersion)
            .build());
  }
}

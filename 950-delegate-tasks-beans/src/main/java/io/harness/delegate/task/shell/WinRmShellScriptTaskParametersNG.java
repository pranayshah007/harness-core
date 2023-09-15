/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.mixin.ProcessExecutorCapabilityGenerator;
import io.harness.delegate.task.utils.K8sCapabilityGenerator;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.ScriptType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRADITIONAL})
@Value
@Builder
public class WinRmShellScriptTaskParametersNG implements TaskParameters, ExecutionCapabilityDemander {
  boolean executeOnDelegate;
  @Expression(ALLOW_SECRETS) String script;
  List<String> outputVars;
  List<String> secretOutputVars;
  String accountId;
  String executionId;
  String workingDirectory;
  @Expression(ALLOW_SECRETS) Map<String, String> environmentVariables;
  ScriptType scriptType;
  K8sInfraDelegateConfig k8sInfraDelegateConfig;

  // Target Host Specific fields
  SecretSpecDTO sshKeySpecDTO;
  List<EncryptedDataDetail> encryptionDetails;
  String host;

  // WinRm specific fields
  boolean disableCommandEncoding;
  boolean winrmScriptCommandSplit;
  boolean useWinRMKerberosUniqueCacheFile;
  boolean disableWinRmEnvVarEscaping;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();
    if (k8sInfraDelegateConfig != null) {
      capabilities.addAll(
          K8sCapabilityGenerator.generateExecutionCapabilities(maskingEvaluator, k8sInfraDelegateConfig));
    }
    if (scriptType == ScriptType.POWERSHELL && executeOnDelegate) {
      capabilities.add(ProcessExecutorCapabilityGenerator.buildProcessExecutorCapability(
          "DELEGATE_POWERSHELL", Arrays.asList("/bin/sh", "-c", "pwsh -Version")));
    }
    return capabilities;
  }
}

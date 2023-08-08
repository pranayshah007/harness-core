/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.encoding.EncodingUtils;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG.ShellScriptTaskParametersNGBuilder;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.security.SimpleEncryption;
import io.harness.shell.ScriptType;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDC)
public interface ShellScriptHelperService {
  // Handles ParameterField and String type Objects, else throws Exception
  Map<String, String> getEnvironmentVariables(Map<String, Object> inputVariables, Ambiance ambiance);

  List<String> getOutputVars(Map<String, Object> outputVariables, Set<String> secretOutputVariables);
  List<String> getSecretOutputVars(Map<String, Object> outputVariables, Set<String> secretOutputVariables);

  K8sInfraDelegateConfig getK8sInfraDelegateConfig(@Nonnull Ambiance ambiance, @Nonnull String shellScript);

  void prepareTaskParametersForExecutionTarget(@Nonnull Ambiance ambiance,
      @Nonnull ShellScriptStepParameters shellScriptStepParameters,
      @Nonnull ShellScriptTaskParametersNGBuilder taskParametersNGBuilder);

  String getShellScript(@Nonnull ShellScriptStepParameters stepParameters, Ambiance ambiance);

  String getWorkingDirectory(
      ParameterField<String> workingDirectory, @Nonnull ScriptType scriptType, boolean onDelegate);

  TaskParameters buildShellScriptTaskParametersNG(
      @Nonnull Ambiance ambiance, @Nonnull ShellScriptStepParameters shellScriptStepParameters);

  ShellScriptOutcome prepareShellScriptOutcome(
      Map<String, String> sweepingOutputEnvVariables, Map<String, Object> outputVariables);

  static ShellScriptOutcome prepareShellScriptOutcome(Map<String, String> sweepingOutputEnvVariables,
      Map<String, Object> outputVariables, Set<String> secretOutputVariables) {
    SimpleEncryption encryption = new SimpleEncryption();

    if (outputVariables == null || sweepingOutputEnvVariables == null) {
      return null;
    }
    Map<String, String> resolvedOutputVariables = new HashMap<>();
    outputVariables.keySet().forEach(name -> {
      Object value = ((ParameterField<?>) outputVariables.get(name)).getValue();
      if (isNotEmpty(secretOutputVariables) && secretOutputVariables.contains(name)
          && isNotEmpty(sweepingOutputEnvVariables.get(value.toString()))) {
        String encodedValue = EncodingUtils.encodeBase64(
            encryption.encrypt(sweepingOutputEnvVariables.get(value).getBytes(StandardCharsets.UTF_8)));
        String finalValue = "${sweepingOutputSecrets.obtain(\"" + name + "\",\"" + encodedValue + "\")}";
        resolvedOutputVariables.put(name, finalValue);
      } else {
        resolvedOutputVariables.put(name, sweepingOutputEnvVariables.get(value));
      }
    });
    return ShellScriptOutcome.builder().outputVariables(resolvedOutputVariables).build();
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.shellscript.ShellScriptBaseStepInfo;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("serverlessAwsLambdaGenericStepParameters")
@RecasterAlias("io.harness.cdng.serverless.ServerlessAwsLambdaGenericStepParameters")
public class ServerlessAwsLambdaGenericStepParameters
    extends ServerlessAwsLambdaGenericBaseStepInfo implements ServerlessSpecParameters {
  Map<String, Object> outputVariables;
  Map<String, Object> environmentVariables;
  @Builder(builderMethodName = "infoBuilder")
  public ServerlessAwsLambdaGenericStepParameters(
          ShellScriptBaseStepInfo serverlessShellScriptSpec, ParameterField<List<TaskSelectorYaml>> delegateSelectors, ParameterField<String> commandOptions,
          Map<String, Object> outputVariables, Map<String, Object> environmentVariables) {
    super(serverlessShellScriptSpec, delegateSelectors, commandOptions);
    this.outputVariables = outputVariables;
    this.environmentVariables = environmentVariables;
  }
}

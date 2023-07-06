/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless.container.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.cdng.serverless.ServerlessSpecParameters;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.extended.ci.container.ContainerResource;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.minidev.json.annotate.JsonIgnore;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("serverlessAwsLambdaPrepareRollbackV2StepParameters")
@RecasterAlias("io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaPrepareRollbackV2StepParameters")
public class ServerlessAwsLambdaPrepareRollbackV2StepParameters
    extends ServerlessAwsLambdaV2BaseStepInfo implements ServerlessSpecParameters, StepParameters {
  @JsonIgnore String downloadManifestsFqn;

  @Builder(builderMethodName = "infoBuilder")
  public ServerlessAwsLambdaPrepareRollbackV2StepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<String> image, ParameterField<String> connectorRef, ContainerResource resources,
      ParameterField<Map<String, String>> envVariables, ParameterField<Boolean> privileged,
      ParameterField<Integer> runAsUser, ParameterField<ImagePullPolicy> imagePullPolicy,
      ParameterField<String> serverlessVersion, String downloadManifestsFqn) {
    super(delegateSelectors, image, connectorRef, resources, envVariables, privileged, runAsUser, imagePullPolicy,
        serverlessVersion);
    this.downloadManifestsFqn = downloadManifestsFqn;
  }
}
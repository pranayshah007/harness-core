/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;

import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SweepingOutput;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.pcf.ResizeStrategy;

import software.wings.api.AwsAmiInfoVariables.AwsAmiInfoVariablesBuilder;
import software.wings.service.impl.aws.model.AwsAmiPreDeploymentData;
import software.wings.sm.ContextElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("amiServiceSetupElement")
@TargetModule(_957_CG_BEANS)
public class AmiServiceSetupElement implements ContextElement, SweepingOutput {
  private String uuid;
  private String name;
  private String commandName;
  private int instanceCount;
  private String newAutoScalingGroupName;
  private String oldAutoScalingGroupName;
  private Integer autoScalingSteadyStateTimeout;
  private Integer maxInstances;
  private int desiredInstances;
  private int minInstances;
  private List<String> oldAsgNames;
  private AwsAmiPreDeploymentData preDeploymentData;
  private boolean blueGreen;
  private ResizeStrategy resizeStrategy;
  private List<String> baseScalingPolicyJSONs;
  private List<String> baseAsgScheduledActionJSONs;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.AMI_SERVICE_SETUP;
  }

  public AwsAmiInfoVariables fetchAmiVariableInfo() {
    AwsAmiInfoVariablesBuilder builder = AwsAmiInfoVariables.builder();
    if (newAutoScalingGroupName != null) {
      builder.newAsgName(newAutoScalingGroupName);
    }
    if (oldAutoScalingGroupName != null) {
      builder.oldAsgName(oldAutoScalingGroupName);
    }
    return builder.build();
  }

  @Override
  public ContextElement cloneMin() {
    return null;
  }

  @Override
  public String getType() {
    return "amiServiceSetupElement";
  }
}

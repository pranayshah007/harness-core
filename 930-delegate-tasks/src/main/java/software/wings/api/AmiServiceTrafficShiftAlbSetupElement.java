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
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;

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
@JsonTypeName("amiServiceTrafficShiftAlbSetupElement")
@TargetModule(_957_CG_BEANS)
public class AmiServiceTrafficShiftAlbSetupElement implements ContextElement, SweepingOutput {
  private String uuid;
  private String name;
  private String commandName;
  private int instanceCount;
  private String newAutoScalingGroupName;
  private String oldAutoScalingGroupName;
  private Integer autoScalingSteadyStateTimeout;
  private Integer maxInstances;
  private Integer desiredInstances;
  private Integer minInstances;
  private List<String> oldAsgNames;
  private AwsAmiPreDeploymentData preDeploymentData;
  private List<String> baseScalingPolicyJSONs;
  private List<LbDetailsForAlbTrafficShift> detailsWithTargetGroups;
  private List<String> baseAsgScheduledActionJSONs;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.AMI_SERVICE_SETUP;
  }

  @Override
  public ContextElement cloneMin() {
    return null;
  }

  @Override
  public String getType() {
    return "amiServiceTrafficShiftAlbSetupElement";
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
}

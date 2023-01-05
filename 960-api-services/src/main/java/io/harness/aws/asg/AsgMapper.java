/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static com.google.common.collect.Lists.newArrayList;

import io.harness.annotations.dev.OwnedBy;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.LifecycleHookSpecification;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.autoscaling.model.ScalingPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class AsgMapper {
  public String createAutoScalingGroupRequestFromAutoScalingGroupConfigurationMapper(AutoScalingGroup autoScalingGroup,
      List<LifecycleHookSpecification> lifecycleHookSpecificationList) throws JsonProcessingException {
    String autoScalingGroupContent = AsgContentParser.toString(autoScalingGroup, true);
    CreateAutoScalingGroupRequest createAutoScalingGroupRequest =
        AsgContentParser.parseJson(autoScalingGroupContent, CreateAutoScalingGroupRequest.class, false);
    createAutoScalingGroupRequest.setLifecycleHookSpecificationList(lifecycleHookSpecificationList);
    return AsgContentParser.toString(createAutoScalingGroupRequest, false);
  }

  public String createAutoScalingGroupRequestFromAutoScalingGroupConfiguration(
      AutoScalingGroup autoScalingGroup, List<LifecycleHookSpecification> lifecycleHookSpecificationList) {
    try {
      return createAutoScalingGroupRequestFromAutoScalingGroupConfigurationMapper(
          autoScalingGroup, lifecycleHookSpecificationList);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public String createScalingPolicyRequestFromScalingPolicyMapper(ScalingPolicy scalingPolicy)
      throws JsonProcessingException {
    String scalingPolicyContent = AsgContentParser.toString(scalingPolicy, true);
    PutScalingPolicyRequest putScalingPolicyRequest =
        AsgContentParser.parseJson(scalingPolicyContent, PutScalingPolicyRequest.class, false);
    return AsgContentParser.toString(putScalingPolicyRequest, false);
  }

  public List<String> createScalingPolicyRequestsListFromScalingPoliciesList(List<ScalingPolicy> scalingPolicies) {
    List<String> createScalingPolicyRequestsList = newArrayList();
    scalingPolicies.forEach(scalingPolicy -> {
      try {
        createScalingPolicyRequestsList.add(createScalingPolicyRequestFromScalingPolicyMapper(scalingPolicy));
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    });
    return createScalingPolicyRequestsList;
  }
}

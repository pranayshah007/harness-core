/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.asg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static com.google.common.collect.Lists.newArrayList;

import io.harness.annotations.dev.OwnedBy;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.autoscaling.model.ScalingPolicy;
import com.amazonaws.services.autoscaling.model.Tag;
import com.amazonaws.services.autoscaling.model.TagDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class AsgMapper {
  public String createAutoScalingGroupRequestFromAutoScalingGroupConfigurationMapper(AutoScalingGroup autoScalingGroup)
      throws JsonProcessingException {
    CreateAutoScalingGroupRequest createAutoScalingGroupRequest = new CreateAutoScalingGroupRequest();
    createAutoScalingGroupRequest.setAutoScalingGroupName(autoScalingGroup.getAutoScalingGroupName());
    createAutoScalingGroupRequest.setLaunchConfigurationName(autoScalingGroup.getLaunchConfigurationName());
    createAutoScalingGroupRequest.setLaunchTemplate(autoScalingGroup.getLaunchTemplate());
    createAutoScalingGroupRequest.setMixedInstancesPolicy(autoScalingGroup.getMixedInstancesPolicy());
    createAutoScalingGroupRequest.setMinSize(autoScalingGroup.getMinSize());
    createAutoScalingGroupRequest.setMaxSize(autoScalingGroup.getMaxSize());
    createAutoScalingGroupRequest.setDesiredCapacity(autoScalingGroup.getDesiredCapacity());
    createAutoScalingGroupRequest.setDefaultCooldown(autoScalingGroup.getDefaultCooldown());
    createAutoScalingGroupRequest.setAvailabilityZones(autoScalingGroup.getAvailabilityZones());
    createAutoScalingGroupRequest.setLoadBalancerNames(autoScalingGroup.getLoadBalancerNames());
    createAutoScalingGroupRequest.setTargetGroupARNs(autoScalingGroup.getTargetGroupARNs());
    createAutoScalingGroupRequest.setHealthCheckType(autoScalingGroup.getHealthCheckType());
    createAutoScalingGroupRequest.setHealthCheckGracePeriod(autoScalingGroup.getHealthCheckGracePeriod());
    createAutoScalingGroupRequest.setPlacementGroup(autoScalingGroup.getPlacementGroup());
    createAutoScalingGroupRequest.setVPCZoneIdentifier(autoScalingGroup.getVPCZoneIdentifier());
    createAutoScalingGroupRequest.setTerminationPolicies(autoScalingGroup.getTerminationPolicies());
    createAutoScalingGroupRequest.setNewInstancesProtectedFromScaleIn(
        autoScalingGroup.getNewInstancesProtectedFromScaleIn());
    createAutoScalingGroupRequest.setCapacityRebalance(autoScalingGroup.getCapacityRebalance());
    createAutoScalingGroupRequest.setServiceLinkedRoleARN(autoScalingGroup.getServiceLinkedRoleARN());
    createAutoScalingGroupRequest.setMaxInstanceLifetime(autoScalingGroup.getMaxInstanceLifetime());
    createAutoScalingGroupRequest.setContext(autoScalingGroup.getContext());
    createAutoScalingGroupRequest.setDesiredCapacityType(autoScalingGroup.getDesiredCapacityType());
    createAutoScalingGroupRequest.setDefaultInstanceWarmup(autoScalingGroup.getDefaultInstanceWarmup());
    createAutoScalingGroupRequest.setTags(tagDescriptionToTagMapper(autoScalingGroup.getTags()));

    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    return objectMapper.writeValueAsString(createAutoScalingGroupRequest);
  }

  private List<Tag> tagDescriptionToTagMapper(List<TagDescription> tags) {
    List<Tag> tagsList = new ArrayList<>();
    if (isEmpty(tags)) {
      return null;
    }
    tags.forEach(tag -> {
      Tag tagTemp = new Tag();
      tagTemp.setKey(tag.getKey());
      tagTemp.setResourceId(tag.getResourceId());
      tagTemp.setResourceType(tag.getResourceType());
      tagTemp.setValue(tag.getValue());
      tagTemp.setPropagateAtLaunch(tag.getPropagateAtLaunch());
      tagsList.add(tagTemp);
    });
    return tagsList;
  }
  public String createAutoScalingGroupRequestFromAutoScalingGroupConfiguration(AutoScalingGroup autoScalingGroup) {
    try {
      return createAutoScalingGroupRequestFromAutoScalingGroupConfigurationMapper(autoScalingGroup);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public String createScalingPolicyRequestFromScalingPolicyMapper(ScalingPolicy scalingPolicy)
      throws JsonProcessingException {
    PutScalingPolicyRequest putScalingPolicyRequest = new PutScalingPolicyRequest();
    putScalingPolicyRequest.setAutoScalingGroupName(scalingPolicy.getAutoScalingGroupName());
    putScalingPolicyRequest.setPolicyName(scalingPolicy.getPolicyName());
    putScalingPolicyRequest.setPolicyType(scalingPolicy.getPolicyType());
    putScalingPolicyRequest.setAdjustmentType(scalingPolicy.getAdjustmentType());
    putScalingPolicyRequest.setMinAdjustmentStep(scalingPolicy.getMinAdjustmentStep());
    putScalingPolicyRequest.setMinAdjustmentMagnitude(scalingPolicy.getMinAdjustmentMagnitude());
    putScalingPolicyRequest.setScalingAdjustment(scalingPolicy.getScalingAdjustment());
    putScalingPolicyRequest.setCooldown(scalingPolicy.getCooldown());
    putScalingPolicyRequest.setMetricAggregationType(scalingPolicy.getMetricAggregationType());
    putScalingPolicyRequest.setStepAdjustments(scalingPolicy.getStepAdjustments());
    putScalingPolicyRequest.setEstimatedInstanceWarmup(scalingPolicy.getEstimatedInstanceWarmup());
    putScalingPolicyRequest.setTargetTrackingConfiguration(scalingPolicy.getTargetTrackingConfiguration());
    putScalingPolicyRequest.setEnabled(scalingPolicy.getEnabled());
    putScalingPolicyRequest.setPredictiveScalingConfiguration(scalingPolicy.getPredictiveScalingConfiguration());

    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    return objectMapper.writeValueAsString(putScalingPolicyRequest);
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

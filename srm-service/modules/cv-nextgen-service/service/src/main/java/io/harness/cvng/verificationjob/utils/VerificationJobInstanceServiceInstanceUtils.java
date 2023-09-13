/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.utils;

import io.harness.cvng.beans.cvnglog.ExecutionLogDTO;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.core.services.api.ExecutionLogger;
import io.harness.cvng.verificationjob.entities.ServiceInstanceDetails;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public class VerificationJobInstanceServiceInstanceUtils {
  public static Integer MAX_TEST_NODE_COUNT = 50;
  public static Integer MAX_CONTROL_NODE_COUNT = 50;

  public boolean isNodeRegExEnabled(VerificationJobInstance verificationJobInstance) {
    return verificationJobInstance != null && verificationJobInstance.getServiceInstanceDetails() != null
        && (StringUtils.isNotEmpty(verificationJobInstance.getServiceInstanceDetails().getTestNodeRegExPattern())
            || StringUtils.isNoneBlank(
                verificationJobInstance.getServiceInstanceDetails().getControlNodeRegExPattern()));
  }

  public boolean canUseNodesFromCD(VerificationJobInstance verificationJobInstance) {
    return canUseNodesFromCD(verificationJobInstance.getServiceInstanceDetails());
  }

  public boolean canUseNodesFromCD(ServiceInstanceDetails serviceInstanceDetails) {
    return serviceInstanceDetails != null && serviceInstanceDetails.isShouldUseNodesFromCD()
        && CollectionUtils.isNotEmpty(serviceInstanceDetails.getDeployedServiceInstances())
        && CollectionUtils.isNotEmpty(serviceInstanceDetails.getServiceInstancesAfterDeployment());
  }

  public List<String> getSampledTestNodes(VerificationJobInstance verificationJobInstance) {
    return getSampledTestNodes(verificationJobInstance.getServiceInstanceDetails());
  }

  public List<String> getSampledTestNodes(ServiceInstanceDetails serviceInstanceDetails) {
    if (!canUseNodesFromCD(serviceInstanceDetails)) {
      return null;
    }
    if (CollectionUtils.isNotEmpty(serviceInstanceDetails.getSampledTestNodes())) {
      return serviceInstanceDetails.getSampledTestNodes();
    }
    return getRandomElement(getTestNodes(serviceInstanceDetails), MAX_TEST_NODE_COUNT);
  }

  public List<String> getSampledControlNodes(VerificationJobInstance verificationJobInstance) {
    return getSampledControlNodes(
        verificationJobInstance.getResolvedJob().getType(), verificationJobInstance.getServiceInstanceDetails());
  }

  public List<String> getSampledControlNodes(
      VerificationJobType verificationJobType, ServiceInstanceDetails serviceInstanceDetails) {
    if (!canUseNodesFromCD(serviceInstanceDetails)) {
      return null;
    }
    if (CollectionUtils.isNotEmpty(serviceInstanceDetails.getSampledControlNodes())) {
      return serviceInstanceDetails.getSampledControlNodes();
    }
    return getRandomElement(getControlNodes(verificationJobType, serviceInstanceDetails), MAX_CONTROL_NODE_COUNT);
  }

  public Set<String> filterValidTestNodes(
      Set<String> testNodes, VerificationJobInstance verificationJobInstance, ExecutionLogger executionLogger) {
    if (verificationJobInstance.getServiceInstanceDetails() == null
        || StringUtils.isEmpty(verificationJobInstance.getServiceInstanceDetails().getTestNodeRegExPattern())) {
      return new HashSet<>(testNodes);
    }
    Set<String> filteredTestNodes =
        getRegExFilteredTestNodes(testNodes, verificationJobInstance.getServiceInstanceDetails());
    if (executionLogger != null) {
      executionLogger.log(ExecutionLogDTO.LogLevel.INFO,
          "Regex" + verificationJobInstance.getServiceInstanceDetails().getTestNodeRegExPattern()
              + "matched test nodes: " + String.join(",", filteredTestNodes) + ", Filtered out nodes:"
              + String.join(",", Sets.difference(SetUtils.emptyIfNull(testNodes), filteredTestNodes)));
    }
    return filteredTestNodes;
  }

  @NotNull
  private static Set<String> getRegExFilteredTestNodes(
      Set<String> testNodes, ServiceInstanceDetails serviceInstanceDetails) {
    if (StringUtils.isNotEmpty(serviceInstanceDetails.getTestNodeRegExPattern())) {
      return CollectionUtils.emptyIfNull(testNodes)
          .stream()
          .filter(str -> Pattern.matches(serviceInstanceDetails.getTestNodeRegExPattern(), str))
          .collect(Collectors.toSet());
    }
    return SetUtils.emptyIfNull(testNodes);
  }

  public Set<String> filterValidControlNodes(
      Set<String> controlNodes, VerificationJobInstance verificationJobInstance, ExecutionLogger executionLogger) {
    if (verificationJobInstance.getServiceInstanceDetails() == null
        || StringUtils.isEmpty(verificationJobInstance.getServiceInstanceDetails().getControlNodeRegExPattern())) {
      return new HashSet<>(controlNodes);
    }
    Set<String> filteredControlNodes =
        getRegExFilterdControlNodes(controlNodes, verificationJobInstance.getServiceInstanceDetails());
    if (executionLogger != null) {
      executionLogger.log(ExecutionLogDTO.LogLevel.INFO,
          "Regex " + verificationJobInstance.getServiceInstanceDetails().getControlNodeRegExPattern()
              + "matched control nodes: " + String.join(",", filteredControlNodes) + ", Filtered out nodes:"
              + String.join(",", Sets.difference(SetUtils.emptyIfNull(controlNodes), filteredControlNodes)));
    }
    return filteredControlNodes;
  }

  @NotNull
  private static Set<String> getRegExFilterdControlNodes(
      Set<String> controlNodes, ServiceInstanceDetails serviceInstanceDetails) {
    if (StringUtils.isNotEmpty(serviceInstanceDetails.getControlNodeRegExPattern())) {
      return SetUtils.emptyIfNull(controlNodes)
          .stream()
          .filter(str -> Pattern.matches(serviceInstanceDetails.getControlNodeRegExPattern(), str))
          .collect(Collectors.toSet());
    }
    return SetUtils.emptyIfNull(controlNodes);
  }

  public List<String> getTestNodes(VerificationJobInstance verificationJobInstance) {
    return getTestNodes(verificationJobInstance.getServiceInstanceDetails());
  }

  public List<String> getTestNodes(ServiceInstanceDetails serviceInstanceDetails) {
    if (!canUseNodesFromCD(serviceInstanceDetails)) {
      return null;
    }
    return new ArrayList<>(getRegExFilteredTestNodes(
        CollectionUtils.emptyIfNull(serviceInstanceDetails.getDeployedServiceInstances())
            .stream()
            .filter(si -> serviceInstanceDetails.getServiceInstancesAfterDeployment().contains(si))
            .collect(Collectors.toSet()),
        serviceInstanceDetails));
  }

  public List<String> getControlNodes(VerificationJobInstance verificationJobInstance) {
    return getControlNodes(
        verificationJobInstance.getResolvedJob().getType(), verificationJobInstance.getServiceInstanceDetails());
  }

  public List<String> getControlNodes(
      VerificationJobType verificationJobType, ServiceInstanceDetails serviceInstanceDetails) {
    if (!canUseNodesFromCD(serviceInstanceDetails)) {
      return null;
    }
    switch (verificationJobType) {
      case CANARY:
        return getControlNodesForCanaryComparison(serviceInstanceDetails);
      case ROLLING:
      case BLUE_GREEN:
        return getControlNodesForBeforeAfterComparison(serviceInstanceDetails);
      case AUTO:
        if (isValidCanaryDeployment(serviceInstanceDetails)) {
          return getControlNodesForCanaryComparison(serviceInstanceDetails);
        } else {
          return getControlNodesForBeforeAfterComparison(serviceInstanceDetails);
        }
      default:
        return null;
    }
  }

  public boolean isValidCanaryDeployment(ServiceInstanceDetails serviceInstanceDetails) {
    if (serviceInstanceDetails == null || !serviceInstanceDetails.isShouldUseNodesFromCD()
        || CollectionUtils.isEmpty(serviceInstanceDetails.getDeployedServiceInstances())) {
      return false;
    }
    // Deployed SI shouldn't be there before deployment and at-least 1 before deployment SI should be there after
    // deployment
    if (CollectionUtils.emptyIfNull(serviceInstanceDetails.getServiceInstancesBeforeDeployment())
            .stream()
            .anyMatch(siBeforeDeployment
                -> serviceInstanceDetails.getServiceInstancesAfterDeployment().contains(siBeforeDeployment))
        && CollectionUtils.emptyIfNull(serviceInstanceDetails.getDeployedServiceInstances())
               .stream()
               .noneMatch(deployedSi
                   -> CollectionUtils.emptyIfNull(serviceInstanceDetails.getServiceInstancesBeforeDeployment())
                          .contains(deployedSi))) {
      return true;
    }
    return false;
  }

  private List<String> getControlNodesForBeforeAfterComparison(ServiceInstanceDetails serviceInstanceDetails) {
    return new ArrayList<>(getRegExFilterdControlNodes(
        new HashSet<>(CollectionUtils.emptyIfNull(serviceInstanceDetails.getServiceInstancesBeforeDeployment())),
        serviceInstanceDetails));
  }

  private List<String> getControlNodesForCanaryComparison(ServiceInstanceDetails serviceInstanceDetails) {
    return new ArrayList<>(getRegExFilterdControlNodes(
        CollectionUtils.emptyIfNull(serviceInstanceDetails.getServiceInstancesAfterDeployment())
            .stream()
            .filter(si
                -> CollectionUtils.emptyIfNull(serviceInstanceDetails.getServiceInstancesBeforeDeployment())
                       .contains(si))
            .filter(si -> !serviceInstanceDetails.getDeployedServiceInstances().contains(si))
            .collect(Collectors.toSet()),
        serviceInstanceDetails));
  }

  public List<String> getRandomElement(List<String> list, int totalItems) {
    if (CollectionUtils.isEmpty(list) || list.size() < totalItems) {
      return list;
    }
    list = new ArrayList<>(list);
    Random rand = new Random();
    List<String> newList = new ArrayList<>();
    for (int i = 0; i < totalItems; i++) {
      int randomIndex = rand.nextInt(list.size());
      newList.add(list.get(randomIndex));
      list.remove(randomIndex);
    }
    return newList;
  }

  public void logExecutionLog(VerificationJobInstance verificationJobInstance, ExecutionLogger executionLogger) {
    if (verificationJobInstance.getServiceInstanceDetails() == null) {
      return;
    }
    if (isNodeRegExEnabled(verificationJobInstance)) {
      StringBuilder logMessageBuilder = new StringBuilder();
      logMessageBuilder.append("Verify step configured to filter out nodes based on Regex. \n");
      if (StringUtils.isNotEmpty(verificationJobInstance.getServiceInstanceDetails().getTestNodeRegExPattern())) {
        logMessageBuilder.append("Test node regex pattern: ")
            .append(verificationJobInstance.getServiceInstanceDetails().getTestNodeRegExPattern())
            .append("\n");
      }
      if (StringUtils.isNotEmpty(verificationJobInstance.getServiceInstanceDetails().getControlNodeRegExPattern())) {
        logMessageBuilder.append("Control node regex pattern: ")
            .append(verificationJobInstance.getServiceInstanceDetails().getControlNodeRegExPattern());
      }
      executionLogger.log(ExecutionLogDTO.LogLevel.INFO, logMessageBuilder.toString());
    }
    if (verificationJobInstance.getServiceInstanceDetails().isShouldUseNodesFromCD() == true) {
      StringBuilder logMessageBuilder = new StringBuilder();
      logMessageBuilder.append("Verify step configured to use deployed node(service instance) details from CD. \n\n");
      logMessageBuilder.append("Received Node details from CD:\n");
      logMessageBuilder.append("Deployed in this stage:")
          .append(String.join(", ",
              CollectionUtils.emptyIfNull(
                  verificationJobInstance.getServiceInstanceDetails().getDeployedServiceInstances())))
          .append("\n");
      logMessageBuilder.append("Nodes before deployment:")
          .append(String.join(", ",
              CollectionUtils.emptyIfNull(
                  verificationJobInstance.getServiceInstanceDetails().getServiceInstancesBeforeDeployment())))
          .append("\n");
      logMessageBuilder.append("Nodes after deployment:")
          .append(String.join(", ",
              CollectionUtils.emptyIfNull(
                  verificationJobInstance.getServiceInstanceDetails().getServiceInstancesAfterDeployment())))
          .append("\n\n");
      if (canUseNodesFromCD(verificationJobInstance)) {
        logMessageBuilder.append("Sampled nodes for analysis: \n");
        logMessageBuilder.append("Test(Max:")
            .append(MAX_TEST_NODE_COUNT)
            .append("): ")
            .append(String.join(", ",
                CollectionUtils.emptyIfNull(verificationJobInstance.getServiceInstanceDetails().getSampledTestNodes())))
            .append("\n");
        logMessageBuilder.append("Control(Max:")
            .append(MAX_CONTROL_NODE_COUNT)
            .append("): ")
            .append(String.join(", ",
                CollectionUtils.emptyIfNull(
                    verificationJobInstance.getServiceInstanceDetails().getSampledControlNodes())));
      } else {
        logMessageBuilder.append(
            "We couldn't find deployed node details from CD, hence falling back to default analysis based on node details from APM provider.");
      }
      executionLogger.log(ExecutionLogDTO.LogLevel.INFO, logMessageBuilder.toString());
    }
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.utils;

import io.harness.cvng.verificationjob.entities.ServiceInstanceDetails;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;

@UtilityClass
public class VerificationJobInstanceServiceInstanceUtils {
  public static Integer MAX_TEST_NODE_COUNT = 50;
  public static Integer MAX_CONTROL_NODE_COUNT = 50;

  public List<String> getSampledTestNodes(VerificationJobInstance verificationJobInstance) {
    return getRandomElement(getTestNodes(verificationJobInstance), MAX_TEST_NODE_COUNT);
  }

  public List<String> getSampledControlNodes(VerificationJobInstance verificationJobInstance) {
    return getRandomElement(getControlNodes(verificationJobInstance), MAX_CONTROL_NODE_COUNT);
  }

  public List<String> getTestNodes(VerificationJobInstance verificationJobInstance) {
    if (verificationJobInstance.getServiceInstanceDetailsFromCD() == null
        || verificationJobInstance.getServiceInstanceDetailsFromCD().isValid() == false
        || CollectionUtils.isEmpty(
            verificationJobInstance.getServiceInstanceDetailsFromCD().getServiceInstancesAfterDeployment())) {
      return null;
    }
    return CollectionUtils
        .emptyIfNull(verificationJobInstance.getServiceInstanceDetailsFromCD().getDeployedServiceInstances())
        .stream()
        .filter(si
            -> verificationJobInstance.getServiceInstanceDetailsFromCD().getServiceInstancesAfterDeployment().contains(
                si))
        .collect(Collectors.toList());
  }

  public List<String> getControlNodes(VerificationJobInstance verificationJobInstance) {
    if (verificationJobInstance.getServiceInstanceDetailsFromCD() == null
        || verificationJobInstance.getServiceInstanceDetailsFromCD().isValid() == false
        || CollectionUtils.isEmpty(
            verificationJobInstance.getServiceInstanceDetailsFromCD().getServiceInstancesAfterDeployment())) {
      return null;
    }

    switch (verificationJobInstance.getResolvedJob().getType()) {
      case CANARY:
        return getControlNodesForCanaryComparison(verificationJobInstance.getServiceInstanceDetailsFromCD());
      case ROLLING:
      case BLUE_GREEN:
        return getControlNodesForBeforeAfterComparison(verificationJobInstance.getServiceInstanceDetailsFromCD());
      case AUTO:
        if (isValidCanaryDeployment(verificationJobInstance.getServiceInstanceDetailsFromCD())) {
          return getControlNodesForCanaryComparison(verificationJobInstance.getServiceInstanceDetailsFromCD());
        } else {
          return getControlNodesForBeforeAfterComparison(verificationJobInstance.getServiceInstanceDetailsFromCD());
        }
      default:
        return null;
    }
  }

  public boolean isValidCanaryDeployment(ServiceInstanceDetails serviceInstanceDetails) {
    if (serviceInstanceDetails == null) {
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
    return serviceInstanceDetails.getServiceInstancesBeforeDeployment();
  }

  private List<String> getControlNodesForCanaryComparison(ServiceInstanceDetails serviceInstanceDetails) {
    return CollectionUtils.emptyIfNull(serviceInstanceDetails.getServiceInstancesAfterDeployment())
        .stream()
        .filter(si -> serviceInstanceDetails.getServiceInstancesBeforeDeployment().contains(si))
        .collect(Collectors.toList());
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
}

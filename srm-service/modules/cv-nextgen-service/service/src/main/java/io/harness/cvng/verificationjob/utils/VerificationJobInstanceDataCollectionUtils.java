/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.utils;

import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class VerificationJobInstanceDataCollectionUtils {
  public boolean shouldCollectPreDeploymentData(VerificationJobInstance verificationJobInstance) {
    if (verificationJobInstance.getResolvedJob().getType().equals(VerificationJobType.CANARY)
        && (VerificationJobInstanceServiceInstanceUtils.canUseNodesFromCD(verificationJobInstance)
            || VerificationJobInstanceServiceInstanceUtils.isNodeRegExEnabled(verificationJobInstance))) {
      return false;
    }
    if (verificationJobInstance.getResolvedJob().getType().equals(VerificationJobType.AUTO)
        && VerificationJobInstanceServiceInstanceUtils.canUseNodesFromCD(verificationJobInstance)) {
      return !VerificationJobInstanceServiceInstanceUtils.isValidCanaryDeployment(
          verificationJobInstance.getServiceInstanceDetails());
    }
    return true;
  }

  public List<String> validPreDeploymentNodePatterns(VerificationJobInstance verificationJobInstance) {
    if (!shouldCollectPreDeploymentData(verificationJobInstance)
        || verificationJobInstance.getServiceInstanceDetails() == null
        || StringUtils.isEmpty(verificationJobInstance.getServiceInstanceDetails().getControlNodeRegExPattern())) {
      return null;
    }
    return Arrays.asList(verificationJobInstance.getServiceInstanceDetails().getControlNodeRegExPattern());
  }

  public List<String> validPostDeploymentNodePatterns(VerificationJobInstance verificationJobInstance) {
    if (verificationJobInstance.getServiceInstanceDetails() == null
        || StringUtils.isEmpty(verificationJobInstance.getServiceInstanceDetails().getTestNodeRegExPattern())) {
      return null;
    }
    if (verificationJobInstance.getResolvedJob().getType().equals(VerificationJobType.ROLLING)
        || verificationJobInstance.getResolvedJob().getType().equals(VerificationJobType.BLUE_GREEN)) {
      return Arrays.asList(verificationJobInstance.getServiceInstanceDetails().getTestNodeRegExPattern());
    }
    if (StringUtils.isEmpty(verificationJobInstance.getServiceInstanceDetails().getControlNodeRegExPattern())) {
      return null;
    }
    return Arrays.asList(verificationJobInstance.getServiceInstanceDetails().getTestNodeRegExPattern(),
        verificationJobInstance.getServiceInstanceDetails().getControlNodeRegExPattern());
  }

  public List<String> getPreDeploymentNodesToCollect(VerificationJobInstance verificationJobInstance) {
    if (!shouldCollectPreDeploymentData(verificationJobInstance)) {
      return Collections.emptyList();
    }
    return VerificationJobInstanceServiceInstanceUtils.getSampledControlNodes(verificationJobInstance);
  }

  public List<String> getPostDeploymentNodesToCollect(VerificationJobInstance verificationJobInstance) {
    if (verificationJobInstance.getResolvedJob().getType().equals(VerificationJobType.CANARY)
        || (verificationJobInstance.getResolvedJob().getType().equals(VerificationJobType.AUTO)
            && VerificationJobInstanceServiceInstanceUtils.isValidCanaryDeployment(
                verificationJobInstance.getServiceInstanceDetails()))) {
      List<String> postDeploymentNodes = new ArrayList<>();
      postDeploymentNodes.addAll(CollectionUtils.emptyIfNull(
          VerificationJobInstanceServiceInstanceUtils.getSampledTestNodes(verificationJobInstance)));
      postDeploymentNodes.addAll(CollectionUtils.emptyIfNull(
          VerificationJobInstanceServiceInstanceUtils.getSampledControlNodes(verificationJobInstance)));
      return postDeploymentNodes;
    }
    return VerificationJobInstanceServiceInstanceUtils.getSampledTestNodes(verificationJobInstance);
  }
}

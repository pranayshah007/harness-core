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
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;

@UtilityClass
public class VerificationJobInstanceDataCollectionUtils {
  public boolean shouldCollectPreDeploymentData(VerificationJobInstance verificationJobInstance) {
    if (!shouldCollectUsingNodesFromCD(verificationJobInstance)) {
      return true;
    }
    if (verificationJobInstance.getResolvedJob().getType().equals(VerificationJobType.CANARY)) {
      return false;
    }
    if (verificationJobInstance.getResolvedJob().getType().equals(VerificationJobType.AUTO)) {
      return !VerificationJobInstanceServiceInstanceUtils.isValidCanaryDeployment(
          verificationJobInstance.getServiceInstanceDetailsFromCD());
    }
    return true;
  }

  public boolean shouldCollectUsingNodesFromCD(VerificationJobInstance verificationJobInstance) {
    return verificationJobInstance.getServiceInstanceDetailsFromCD() != null
        && verificationJobInstance.getServiceInstanceDetailsFromCD().isValid();
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
                verificationJobInstance.getServiceInstanceDetailsFromCD()))) {
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

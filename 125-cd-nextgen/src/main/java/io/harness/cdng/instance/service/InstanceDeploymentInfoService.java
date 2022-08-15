/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.instance.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.instance.InstanceDeploymentInfo;
import io.harness.cdng.instance.InstanceDeploymentInfoStatus;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.instanceinfo.InstanceInfo;

import java.util.List;

@OwnedBy(CDP)
public interface InstanceDeploymentInfoService {
  void updateStatus(ExecutionInfoKey executionInfoKey, InstanceDeploymentInfoStatus status);

  void updateStatus(ExecutionInfoKey executionInfoKey, String host, InstanceDeploymentInfoStatus status);

  List<InstanceDeploymentInfo> getByHosts(ExecutionInfoKey executionInfoKey, List<String> hosts);

  void createAndUpdate(
      ExecutionInfoKey executionInfoKey, List<InstanceInfo> instanceInfos, ArtifactDetails artifactDetails);
}

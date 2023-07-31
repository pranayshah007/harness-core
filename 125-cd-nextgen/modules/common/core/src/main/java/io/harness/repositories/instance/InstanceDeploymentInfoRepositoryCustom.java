/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.instance.InstanceDeploymentInfo;
import io.harness.cdng.instance.InstanceDeploymentInfoStatus;
import io.harness.entities.ArtifactDetails;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.List;

@OwnedBy(CDP)
public interface InstanceDeploymentInfoRepositoryCustom {
  UpdateResult updateStatus(ExecutionInfoKey executionInfoKey, InstanceDeploymentInfoStatus status);

  UpdateResult updateStatus(Scope scope, String stageExecutionId, InstanceDeploymentInfoStatus status);

  UpdateResult updateArtifactAndStatus(ExecutionInfoKey executionInfoKey, List<String> hosts,
      ArtifactDetails artifactDetails, InstanceDeploymentInfoStatus status, String stageExecutionId);

  DeleteResult deleteByExecutionInfoKey(ExecutionInfoKey executionInfoKey);

  List<InstanceDeploymentInfo> listByHosts(ExecutionInfoKey executionInfoKey, List<String> hosts);

  List<InstanceDeploymentInfo> listByHostsAndArtifact(ExecutionInfoKey executionInfoKey, List<String> hosts,
      ArtifactDetails artifactDetails, InstanceDeploymentInfoStatus... statuses);

  boolean deleteByScope(Scope scope);
}

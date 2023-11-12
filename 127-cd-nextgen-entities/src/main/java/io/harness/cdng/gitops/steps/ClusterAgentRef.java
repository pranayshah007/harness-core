/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cdng.gitops.steps;

import io.harness.annotation.RecasterAlias;

import java.util.Objects;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("clusterAgentRef")
@RecasterAlias("io.harness.cdng.gitops.steps.ClusterAgentRef")
public class ClusterAgentRef {
  private String clusterId;
  private String agentId;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ClusterAgentRef)) {
      return false;
    }
    ClusterAgentRef clusterAgentRef = (ClusterAgentRef) o;
    return Objects.equals(getClusterId(), clusterAgentRef.getClusterId())
        && Objects.equals(getAgentId(), clusterAgentRef.getAgentId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClusterId(), getAgentId());
  }

  @Override
  public String toString() {
    return "{"
        + "clusterId='" + clusterId + '\'' + ", agentId='" + agentId + '\'' + "}";
  }
}
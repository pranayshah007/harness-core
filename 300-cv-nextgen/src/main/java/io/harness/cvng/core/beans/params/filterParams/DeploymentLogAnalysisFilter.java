/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.params.filterParams;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterType;

import java.util.Collections;
import java.util.List;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Value
@NoArgsConstructor
public class DeploymentLogAnalysisFilter extends LogAnalysisFilter {
  @QueryParam("clusterTypes") List<ClusterType> clusterTypes;
  @QueryParam("hostName") @Deprecated String hostName; // TODO: need to remove it with removing old apis.
  @QueryParam("minAngle") Double minAngle;
  @QueryParam("maxAngle") Double maxAngle;
  @QueryParam("hostNames") List<String> hostNames;
  @QueryParam("clusterId") String clusterId;

  public boolean filterByClusterType() {
    return isNotEmpty(clusterTypes);
  }

  public boolean filterByAngle() {
    if (minAngle != null && maxAngle != null) {
      return true;
    }
    return false;
  }
  public List<String> getHostNames() {
    if (hostNames == null) {
      if (hostName != null) {
        return Collections.singletonList(hostName); // migration logic.
      }
      return Collections.emptyList();
    }
    return hostNames;
  }
  public boolean filterByHostNames() {
    return isNotEmpty(getHostNames());
  }

  public boolean hasClusterIdFilter() {
    return clusterId != null;
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.cluster.entities;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.cluster.entities.ClusterType.AWS_ECS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.cluster.entities.ClusterRecord.ClusterRecordKeys;

import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.morphia.query.Query;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@JsonTypeName("AWS_ECS")
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "EcsClusterKeys")
@OwnedBy(CE)
@TargetModule(HarnessModule._490_CE_COMMONS)
public class EcsCluster implements Cluster {
  String cloudProviderId;
  String region;
  String clusterName;

  public static final String cloudProviderField = ClusterRecordKeys.cluster + "." + EcsClusterKeys.cloudProviderId;
  public static final String regionField = ClusterRecordKeys.cluster + "." + EcsClusterKeys.region;
  public static final String clusterNameField = ClusterRecordKeys.cluster + "." + EcsClusterKeys.clusterName;

  @Builder
  public EcsCluster(String cloudProviderId, String region, String clusterName) {
    this.cloudProviderId = cloudProviderId;
    this.region = region;
    this.clusterName = clusterName;
  }

  @Override
  public String getClusterType() {
    return AWS_ECS;
  }

  @Override
  public void addRequiredQueryFilters(Query<ClusterRecord> query) {
    query.field(cloudProviderField)
        .equal(this.getCloudProviderId())
        .field(regionField)
        .equal(this.getRegion())
        .field(clusterNameField)
        .equal(this.getClusterName());
  }
}

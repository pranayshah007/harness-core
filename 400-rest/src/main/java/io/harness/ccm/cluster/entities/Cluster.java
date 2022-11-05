/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.cluster.entities;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.cluster.entities.ClusterType.AWS_ECS;
import static io.harness.ccm.cluster.entities.ClusterType.AZURE_KUBERNETES;
import static io.harness.ccm.cluster.entities.ClusterType.DIRECT_KUBERNETES;
import static io.harness.ccm.cluster.entities.ClusterType.GCP_KUBERNETES;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.morphia.query.Query;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EcsCluster.class, name = AWS_ECS)
  , @JsonSubTypes.Type(value = DirectKubernetesCluster.class, name = DIRECT_KUBERNETES),
      @JsonSubTypes.Type(value = GcpKubernetesCluster.class, name = GCP_KUBERNETES),
      @JsonSubTypes.Type(value = AzureKubernetesCluster.class, name = AZURE_KUBERNETES)
})
@OwnedBy(CE)
@TargetModule(HarnessModule._490_CE_COMMONS)
public interface Cluster {
  String getClusterType();
  String getCloudProviderId();
  String getClusterName();
  void addRequiredQueryFilters(Query<ClusterRecord> query);
}

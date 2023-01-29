/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.cluster.entities;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.cluster.entities.ClusterType.DIRECT_KUBERNETES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.cluster.entities.ClusterRecord.ClusterRecordKeys;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.settings.SettingValue;

import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.morphia.query.Query;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@JsonTypeName("DIRECT_KUBERNETES")
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "DirectKubernetesClusterKeys")
@OwnedBy(CE)
public class DirectKubernetesCluster implements Cluster, KubernetesCluster {
  private String cloudProviderId;
  private String clusterName;

  private static final String cloudProviderIdField =
      ClusterRecordKeys.cluster + "." + DirectKubernetesClusterKeys.cloudProviderId;
  private static final String clusterNameField =
      ClusterRecordKeys.cluster + "." + DirectKubernetesClusterKeys.clusterName;

  @Builder
  public DirectKubernetesCluster(String cloudProviderId, String clusterName) {
    this.cloudProviderId = cloudProviderId;
    this.clusterName = clusterName;
  }

  @Override
  public String getClusterType() {
    return DIRECT_KUBERNETES;
  }

  @Override
  public void addRequiredQueryFilters(Query<ClusterRecord> query) {
    query.field(cloudProviderIdField).equal(this.getCloudProviderId());
  }

  @Override
  public K8sClusterConfig toK8sClusterConfig(SettingValue cloudProvider, List<EncryptedDataDetail> encryptionDetails) {
    return K8sClusterConfig.builder()
        .cloudProvider(cloudProvider)
        .cloudProviderEncryptionDetails(encryptionDetails)
        .build();
  }
}

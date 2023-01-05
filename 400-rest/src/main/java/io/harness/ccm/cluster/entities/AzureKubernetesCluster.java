/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.cluster.entities;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.cluster.entities.ClusterType.AZURE_KUBERNETES;

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
@JsonTypeName("AZURE_KUBERNETES")
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "AzureKubernetesClusterKeys")
@OwnedBy(CE)
public class AzureKubernetesCluster implements Cluster, KubernetesCluster {
  private String cloudProviderId;
  private String clusterName;
  private String resourceGroup;
  private String subscriptionId;

  public static final String cloudProviderField =
      ClusterRecordKeys.cluster + "." + AzureKubernetesClusterKeys.cloudProviderId;
  public static final String clusterNameField =
      ClusterRecordKeys.cluster + "." + AzureKubernetesClusterKeys.clusterName;

  @Builder
  public AzureKubernetesCluster(
      String cloudProviderId, String clusterName, String resourceGroup, String subscriptionId) {
    this.cloudProviderId = cloudProviderId;
    this.clusterName = clusterName;
    this.resourceGroup = resourceGroup;
    this.subscriptionId = subscriptionId;
  }

  @Override
  public String getClusterType() {
    return AZURE_KUBERNETES;
  }

  @Override
  public void addRequiredQueryFilters(Query<ClusterRecord> query) {
    query.field(cloudProviderField)
        .equal(this.getCloudProviderId())
        .field(clusterNameField)
        .equal(this.getClusterName());
  }

  @Override
  public K8sClusterConfig toK8sClusterConfig(SettingValue cloudProvider, List<EncryptedDataDetail> encryptionDetails) {
    return K8sClusterConfig.builder()
        .cloudProvider(cloudProvider)
        .azureKubernetesCluster(software.wings.beans.AzureKubernetesCluster.builder()
                                    .subscriptionId(subscriptionId)
                                    .resourceGroup(resourceGroup)
                                    .name(clusterName)
                                    .build())
        .cloudProviderEncryptionDetails(encryptionDetails)
        .build();
  }
}

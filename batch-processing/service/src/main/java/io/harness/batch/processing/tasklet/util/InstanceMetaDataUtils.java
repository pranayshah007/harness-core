/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.util;

import io.harness.batch.processing.writer.constants.K8sCCMConstants;
import io.harness.ccm.commons.beans.billing.InstanceCategory;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;

import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class InstanceMetaDataUtils {
  private static final String AWS_SPOT_INSTANCE = "spot";
  private static final String AZURE_SPOT_INSTANCE = "spot";

  private static final String BANCO_INTER_ACCOUNTID = "aYXZz76ETU-_3LLQSzBt1Q";

  public static String getValueForKeyFromInstanceMetaData(@NotNull String metaDataKey, InstanceData instanceData) {
    return getValueForKeyFromInstanceMetaData(metaDataKey, instanceData.getMetaData());
  }

  public static String getValueForKeyFromInstanceMetaData(@NotNull String metaDataKey, Map<String, String> metaData) {
    if (null != metaData && metaData.containsKey(metaDataKey)) {
      return metaData.get(metaDataKey);
    }
    return null;
  }

  public static boolean carryUpdatedMapKeyFromTo(Map<String, String> fromMap, Map<String, String> toMap) {
    boolean updateRequired = false;
    if (fromMap != null && toMap != null) {
      for (Map.Entry<String, String> e : fromMap.entrySet()) {
        if (!Objects.equals(e.getValue(), toMap.get(e.getKey()))) {
          toMap.put(e.getKey(), e.getValue());
          updateRequired = true;
        }
      }
    }
    return updateRequired;
  }

  public static void populateNodePoolNameFromLabel(Map<String, String> labelsMap, Map<String, String> metaData) {
    String nodePoolValue = null;
    if (null != labelsMap.get(K8sCCMConstants.GKE_NODE_POOL_KEY)) {
      nodePoolValue = labelsMap.get(K8sCCMConstants.GKE_NODE_POOL_KEY);
    } else if (null != labelsMap.get(K8sCCMConstants.AKS_NODE_POOL_KEY)) {
      nodePoolValue = labelsMap.get(K8sCCMConstants.AKS_NODE_POOL_KEY);
    } else if (null != labelsMap.get(K8sCCMConstants.EKS_NODE_POOL_KEY)) {
      nodePoolValue = labelsMap.get(K8sCCMConstants.EKS_NODE_POOL_KEY);
    } else if (null != labelsMap.get(K8sCCMConstants.EKSCTL_NODE_POOL_KEY)) {
      nodePoolValue = labelsMap.get(K8sCCMConstants.EKSCTL_NODE_POOL_KEY);
    }
    if (null != nodePoolValue) {
      metaData.put(InstanceMetaDataConstants.NODE_POOL_NAME, nodePoolValue);
    }
  }

  public static boolean checkIfKeyExistsAndIsTrue(@NonNull String key, @NonNull Map<String, String> labelsMap) {
    if (labelsMap.containsKey(key)) {
      if ("true".equalsIgnoreCase(labelsMap.get(key))) {
        return true;
      } else if ("false".equalsIgnoreCase(labelsMap.get(key))) {
        return false;
      } else {
        log.warn("Fix it, got unexpected value for key {}: [{}], labelsMap: {}", key, labelsMap.get(key), labelsMap);
      }
    }

    return false;
  }

  @NotNull
  public static InstanceCategory getInstanceCategory(
      @NonNull CloudProvider k8SCloudProvider, @NonNull Map<String, String> labelsMap, @Nullable String accountId) {
    InstanceCategory instanceCategory = InstanceCategory.ON_DEMAND;

    switch (k8SCloudProvider) {
      case GCP:
        if (checkIfKeyExistsAndIsTrue(K8sCCMConstants.GKE_PREEMPTIBLE_KEY, labelsMap)
            || checkIfKeyExistsAndIsTrue(K8sCCMConstants.PREEMPTIBLE_KEY, labelsMap)
            || checkIfKeyExistsAndIsTrue(K8sCCMConstants.PREEMPTIBLE_NODE_KEY, labelsMap)) {
          instanceCategory = InstanceCategory.SPOT;
        }
        break;
      case AWS:
        boolean isPreemptiable = labelsMap.keySet()
                                     .stream()
                                     .filter(key
                                         -> key.contains(K8sCCMConstants.AWS_LIFECYCLE_KEY)
                                             || key.contains(K8sCCMConstants.AWS_CAPACITY_TYPE_KEY))
                                     .anyMatch(key -> labelsMap.get(key).toLowerCase().contains(AWS_SPOT_INSTANCE));

        if (!isPreemptiable && BANCO_INTER_ACCOUNTID.equals(accountId) && labelsMap.containsKey("node-pool-name")) {
          isPreemptiable = labelsMap.get("node-pool-name").contains(AWS_SPOT_INSTANCE);
        }

        if (isPreemptiable) {
          instanceCategory = InstanceCategory.SPOT;
        }
        break;
      case AZURE:
        if (labelsMap.containsKey(K8sCCMConstants.AZURE_LIFECYCLE_KEY)
            && labelsMap.get(K8sCCMConstants.AZURE_LIFECYCLE_KEY).toLowerCase().contains(AZURE_SPOT_INSTANCE)) {
          instanceCategory = InstanceCategory.SPOT;
        }
        break;
      default:
    }

    return instanceCategory;
  }
}

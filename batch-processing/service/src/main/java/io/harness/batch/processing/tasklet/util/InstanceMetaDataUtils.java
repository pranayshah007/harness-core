/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.util;

import static io.harness.ccm.commons.beans.InstanceType.K8S_NODE;

import io.harness.batch.processing.writer.constants.K8sCCMConstants;
import io.harness.ccm.commons.beans.InstanceType;
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
import org.apache.commons.lang3.StringUtils;

@UtilityClass
@Slf4j
public class InstanceMetaDataUtils {
  private static final String AWS_SPOT_INSTANCE = "spot";
  private static final String AZURE_SPOT_INSTANCE = "spot";

  private static final String BANCO_INTER_ACCOUNTID = "aYXZz76ETU-_3LLQSzBt1Q";

  public static String getValueForKeyFromInstanceMetaData(@NotNull String metaDataKey, InstanceData instanceData) {
    return getValueForKeyFromInstanceMetaData(metaDataKey, instanceData.getMetaData());
  }

  public static String getValueForKeyFromInstanceLabel(@NotNull String labelKey, InstanceData instanceData) {
    return getValueForKeyFromInstanceLabel(labelKey, instanceData.getLabels());
  }

  public static String getValueForKeyFromInstanceLabel(@NotNull String labelKey, Map<String, String> labels) {
    if (null != labels && labels.containsKey(labelKey)) {
      return labels.get(labelKey);
    }
    return null;
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
      if (!isSpotInstance(labelsMap)) {
        nodePoolValue = labelsMap.get(K8sCCMConstants.AKS_NODE_POOL_KEY);
      }
    } else if (null != labelsMap.get(K8sCCMConstants.EKS_NODE_POOL_KEY)) {
      nodePoolValue = labelsMap.get(K8sCCMConstants.EKS_NODE_POOL_KEY);
    } else if (null != labelsMap.get(K8sCCMConstants.EKSCTL_NODE_POOL_KEY)) {
      nodePoolValue = labelsMap.get(K8sCCMConstants.EKSCTL_NODE_POOL_KEY);
    } else if (null != labelsMap.get(K8sCCMConstants.NODE_POOL_NAME_KEY)) {
      nodePoolValue = labelsMap.get(K8sCCMConstants.NODE_POOL_NAME_KEY);
    } else if (null != labelsMap.get(K8sCCMConstants.KOPS_NODE_POOL_KEY)) {
      nodePoolValue = labelsMap.get(K8sCCMConstants.KOPS_NODE_POOL_KEY);
    }
    if (null == nodePoolValue) {
      for (Map.Entry<String, String> labelSet : labelsMap.entrySet()) {
        if (labelSet.getKey().contains(K8sCCMConstants.GENERAL_NODE_POOL_KEY)
            || labelSet.getKey().equals(K8sCCMConstants.GENERAL_NODE_GROUP_KEY)) {
          nodePoolValue = labelSet.getValue();
        }
      }
    }
    if (null != nodePoolValue) {
      metaData.put(InstanceMetaDataConstants.NODE_POOL_NAME, nodePoolValue);
    }
  }

  private static boolean isSpotInstance(Map<String, String> labelsMap) {
    return null != labelsMap.get(K8sCCMConstants.SPOT_INSTANCE_NODE_LIFECYCLE)
        && (labelsMap.get(K8sCCMConstants.SPOT_INSTANCE_NODE_LIFECYCLE).equalsIgnoreCase(K8sCCMConstants.SPOT_INSTANCE)
            || labelsMap.get(K8sCCMConstants.SPOT_INSTANCE_NODE_LIFECYCLE)
                   .equalsIgnoreCase(K8sCCMConstants.ON_DEMAND_INSTANCE));
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

  @NonNull
  public static InstanceCategory getInstanceCategoryECSFargate(String capacityProviderName) {
    InstanceCategory instanceCategory = InstanceCategory.ON_DEMAND;
    if ("FARGATE_SPOT".equals(capacityProviderName)) {
      instanceCategory = InstanceCategory.SPOT;
    }
    return instanceCategory;
  }

  @NotNull
  public static InstanceCategory getInstanceCategory(
      @NonNull CloudProvider k8SCloudProvider, @NonNull Map<String, String> labelsMap, @Nullable String accountId) {
    InstanceCategory instanceCategory = InstanceCategory.ON_DEMAND;

    switch (k8SCloudProvider) {
      case GCP:
        if (checkIfKeyExistsAndIsTrue(K8sCCMConstants.GKE_PREEMPTIBLE_KEY, labelsMap)
            || checkIfKeyExistsAndIsTrue(K8sCCMConstants.PREEMPTIBLE_KEY, labelsMap)
            || checkIfKeyExistsAndIsTrue(K8sCCMConstants.GKE_SPOT_KEY, labelsMap)
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

  public static boolean isAzureVirtualNode(InstanceData instanceData) {
    String type = InstanceMetaDataUtils.getValueForKeyFromInstanceLabel(
        InstanceMetaDataConstants.TYPE_LABEL, instanceData.getLabels());
    String instanceName = instanceData.getInstanceName();

    return isAzureVirtualNode(type, instanceName, instanceData.getInstanceType());
  }

  public static boolean isAzureVirtualNode(String type, String instanceName, InstanceType instanceType) {
    return instanceType == K8S_NODE && K8sCCMConstants.VIRTUAL_KUBELET.equalsIgnoreCase(type)
        && StringUtils.isNotBlank(instanceName)
        && instanceName.contains(InstanceMetaDataConstants.AKS_VIRTUAL_NODE_ACI_INSTANCE_NAME);
  }

  public static boolean isParentAzureVirtualNode(String nodeName, String cloudProvider) {
    return StringUtils.isNotBlank(nodeName)
        && nodeName.contains(InstanceMetaDataConstants.AKS_VIRTUAL_NODE_ACI_INSTANCE_NAME)
        && StringUtils.isNotBlank(cloudProvider) && cloudProvider.equals(CloudProvider.ON_PREM);
  }
}

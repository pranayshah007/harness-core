/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.constants;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CE)
public interface InstanceMetaDataConstants {
  String ZONE = "zone";
  String REGION = "region";
  String POD_NAME = "pod_name";
  String NODE_UID = "node_uid";
  String NODE_NAME = "node_name";
  String NODE_POOL_NAME = "node_pool_name";
  String TASK_ID = "task_id";
  String LAUNCH_TYPE = "launch_type";
  String NAMESPACE = "namespace";
  String CLUSTER_ARN = "cluster_arn";
  String COMPUTE_TYPE = "compute_type";
  String CLUSTER_TYPE = "cluster_type";
  String CLOUD_PROVIDER = "cloud_provider";
  String WORKLOAD_ID = "workload_id";
  String WORKLOAD_NAME = "workload_name";
  String WORKLOAD_TYPE = "workload_type";
  String INSTANCE_FAMILY = "instance_family";
  String EC2_INSTANCE_ID = "ec2_instance_id";
  String ECS_SERVICE_NAME = "ecs_service_name";
  String ECS_SERVICE_ARN = "ecs_service_arn";
  String OPERATING_SYSTEM = "operating_system";
  String INSTANCE_CATEGORY = "instance_category";
  String PARENT_RESOURCE_ID = "parent_resource_id";
  String PARENT_RESOURCE_CPU = "parent_resource_cpu";
  String PARENT_RESOURCE_MEMORY = "parent_resource_memory";
  String CONTAINER_INSTANCE_ARN = "container_instance_arn";
  String ACTUAL_PARENT_RESOURCE_ID = "actual_parent_resource_id";
  String CLOUD_PROVIDER_INSTANCE_ID = "cloud_provider_instance_id";
  String CLAIM_NAME = "claim_name";
  String CLAIM_NAMESPACE = "claim_namespace";
  String PV_TYPE = "pv_type";
  String GCE_STORAGE_CLASS = "type";
  String POD_CAPACITY = "pod_capacity";
  String VIRTUAL_NODE = "virtual_node";
  String SLOW_ACCOUNT = "SFByhonVQvGJX0SbY82rjA";
  String AZURE_SUBSCRIPTION_ID = "vm_subscription_id";
  String AZURE_RESOURCEGROUP_NAME = "vm_resource_group_name";
  int VM_INDEX = 2;
  String VM_INDEX_VALUE = "virtualMachines";
  int VMSS_INDEX = 4;
  String VMSS_INDEX_VALUE = "virtualMachineScaleSets";
  int SUBSCRIPTION_INDEX = 3;
  String SUBSCRIPTION_INDEX_VALUE = "subscriptions";
  int RESOURCE_GROUP_INDEX = 5;
  String RESOURCE_GROUP_INDEX_VALUE = "resourceGroups";
  String TYPE_LABEL = "type";
  String AKS_VIRTUAL_NODE_ACI_INSTANCE_NAME = "virtual-node-aci";
}

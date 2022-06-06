/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.anomaly.entities;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public enum EntityType {
  CLUSTER,
  NAMESPACE,
  WORKLOAD,
  GCP_PRODUCT,
  GCP_SKU_ID,
  GCP_PROJECT,
  GCP_REGION,
  AWS_SERVICE,
  AWS_ACCOUNT,
  AWS_INSTANCE_TYPE,
  AWS_USAGE_TYPE,
  AZURE_SUBSCRIPTION,
  AZURE_RESOURCE_GROUP,
  AZURE_METER_CATEGORY;
}

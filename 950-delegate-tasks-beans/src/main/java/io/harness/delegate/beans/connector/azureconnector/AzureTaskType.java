/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public enum AzureTaskType {
  VALIDATE,
  LIST_SUBSCRIPTIONS,
  LIST_REPOSITORIES,
  LIST_CONTAINER_REGISTRIES,
  LIST_RESOURCE_GROUPS,
  LIST_CLUSTERS,
  GET_ACR_TOKEN,
  LIST_WEBAPP_NAMES,
  LIST_DEPLOYMENT_SLOTS,
  LIST_TAGS
}

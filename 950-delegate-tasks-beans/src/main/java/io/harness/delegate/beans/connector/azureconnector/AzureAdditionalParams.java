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
public enum AzureAdditionalParams {
  CONTAINER_REGISTRY("Container registry name"),
  SUBSCRIPTION_ID("Subscription ID"),
  RESOURCE_GROUP("Resource group name"),
  WEB_APP_NAME("Web App name"),
  OS_TYPE("OS type"),
  USE_PUBLIC_DNS("Use public DNS");

  private final String resourceName;

  AzureAdditionalParams(String name) {
    resourceName = name;
  }

  public String getResourceName() {
    return this.resourceName;
  }
}

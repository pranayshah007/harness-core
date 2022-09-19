/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.runtime.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;

import lombok.Getter;

@OwnedBy(CDP)
public class AzureAppServicesDeploymentSlotNotFoundException extends AzureAppServicesRuntimeException {
  @Getter private final String slotName;
  @Getter private final String webAppName;
  @Getter private final String resourceGroup;
  @Getter private final String subscriptionId;

  public AzureAppServicesDeploymentSlotNotFoundException(
      String slotName, String webAppName, String resourceGroup, String subscriptionId) {
    super(format(
        "Unable to get deployment slot by slot name: %s, app name: %s, resource group name: %s, subscription id: %s",
        slotName, webAppName, resourceGroup, subscriptionId));
    this.slotName = slotName;
    this.webAppName = webAppName;
    this.resourceGroup = resourceGroup;
    this.subscriptionId = subscriptionId;
  }
}

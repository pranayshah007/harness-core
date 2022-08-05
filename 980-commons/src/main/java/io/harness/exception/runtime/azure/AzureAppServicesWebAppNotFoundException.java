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
public class AzureAppServicesWebAppNotFoundException extends AzureAppServicesRuntimeException {
  @Getter private final String webAppName;
  @Getter private final String resourceGroup;

  public AzureAppServicesWebAppNotFoundException(String webAppName, String resourceGroup) {
    super(format("Not found web app with name: %s, resource group name: %s", webAppName, resourceGroup));
    this.webAppName = webAppName;
    this.resourceGroup = resourceGroup;
  }
}

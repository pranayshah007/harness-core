/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureContainerArtifactConfig;

import java.util.Collections;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class AzureTestUtils {
  public static final String APP_NAME = "app-name";
  public static final String SUBSCRIPTION_ID = "subscription-id";
  public static final String RESOURCE_GROUP = "resource-group";
  public static final String DEPLOYMENT_SLOT = "deployment-slot";
  public static final String ROLLBACK = "rollback";
  public static final double TRAFFIC_WEIGHT = 20.0;
  public static final String TEST_IMAGE = "test-image";
  public static final String TEST_IMAGE_TAG = "tag-image";
  public static final String TENANT_ID = "tenant-id";
  public static final String CLIENT_ID = "client-id";
  public static final byte[] CERT = "test-cert".getBytes();

  public AzureArtifactConfig createTestContainerArtifactConfig() {
    return AzureContainerArtifactConfig.builder()
        .image(TEST_IMAGE)
        .tag(TEST_IMAGE_TAG)
        .registryType(AzureRegistryType.DOCKER_HUB_PRIVATE)
        .build();
  }

  public AzureWebAppInfraDelegateConfig createTestWebAppInfraDelegateConfig() {
    return AzureWebAppInfraDelegateConfig.builder()
        .subscription(SUBSCRIPTION_ID)
        .resourceGroup(RESOURCE_GROUP)
        .appName(APP_NAME)
        .deploymentSlot(DEPLOYMENT_SLOT)
        .build();
  }

  public AzureConfig createTestAzureConfig() {
    return AzureConfig.builder()
        .azureAuthenticationType(AzureAuthenticationType.SERVICE_PRINCIPAL_CERT)
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .cert(CERT)
        .tenantId(TENANT_ID)
        .clientId(CLIENT_ID)
        .build();
  }

  public AzureAppServicePreDeploymentData buildTestPreDeploymentData(AppServiceDeploymentProgress deploymentProgress) {
    return AzureAppServicePreDeploymentData.builder()
        .trafficWeight(TRAFFIC_WEIGHT)
        .appSettingsToAdd(Collections.emptyMap())
        .appSettingsToRemove(Collections.emptyMap())
        .connStringsToAdd(Collections.emptyMap())
        .connStringsToRemove(Collections.emptyMap())
        .dockerSettingsToAdd(Collections.emptyMap())
        .appName(APP_NAME)
        .slotName(DEPLOYMENT_SLOT)
        .imageNameAndTag("imageNameAndTag")
        .deploymentProgressMarker(deploymentProgress.name())
        .build();
  }
}

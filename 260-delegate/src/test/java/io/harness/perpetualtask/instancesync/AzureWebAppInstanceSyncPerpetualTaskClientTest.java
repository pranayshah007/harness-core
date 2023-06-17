/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesync;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.service.InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.AzureTaskParameters;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppListWebAppInstancesParameters;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureWebAppInfrastructureMapping;
import software.wings.service.impl.azure.manager.AzureTaskExecutionRequest;
import software.wings.service.impl.instance.InstanceSyncTestConstants;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.WingsTestConstants;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class AzureWebAppInstanceSyncPerpetualTaskClientTest extends WingsBaseTest {
  @Mock private InfrastructureMappingService mockInfraMappingService;
  @Mock private SettingsService mockSettingsService;
  @Mock private SecretManager mockSecretManager;

  @InjectMocks @Inject private AzureWebAppInstanceSyncPerpetualTaskClient client;

  @Before
  public void setup() {
    AzureWebAppInfrastructureMapping infrastructureMapping = AzureWebAppInfrastructureMapping.builder().build();
    infrastructureMapping.setAppId(WingsTestConstants.APP_ID);
    infrastructureMapping.setAccountId(ACCOUNT_ID);
    infrastructureMapping.setUuid(INFRA_MAPPING_ID);
    infrastructureMapping.setSubscriptionId("subscriptionId");
    infrastructureMapping.setResourceGroup("resourceGroupName");
    doReturn(infrastructureMapping).when(mockInfraMappingService).get(anyString(), anyString());
    doReturn(aSettingAttribute().withValue(AzureConfig.builder().build()).build()).when(mockSettingsService).get(any());
    doReturn(emptyList()).when(mockSecretManager).getEncryptionDetails(any(), anyString(), anyString());
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetTaskParams() {
    AzureWebAppInstanceSyncPerpetualProtoTaskParams taskParams =
        (AzureWebAppInstanceSyncPerpetualProtoTaskParams) client.getTaskParams(getPerpetualTaskClientContext());
    assertThat(taskParams).isNotNull();
    assertThat(taskParams.getAppName()).isEqualTo("appName");
    assertThat(taskParams.getSlotName()).isEqualTo("slotName");
    assertThat(taskParams.getSubscriptionId()).isEqualTo("subscriptionId");
    assertThat(taskParams.getResourceGroupName()).isEqualTo("resourceGroupName");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetValidationTask() {
    DelegateTask task = client.getValidationTask(getPerpetualTaskClientContext(), ACCOUNT_ID);
    assertThat(task).isNotNull();
    Object params = task.getData().getParameters()[0];
    assertThat(params instanceof AzureTaskExecutionRequest).isTrue();
    AzureTaskExecutionRequest commandRequest = (AzureTaskExecutionRequest) params;
    AzureTaskParameters azureTaskParameters = commandRequest.getAzureTaskParameters();
    assertThat(azureTaskParameters instanceof AzureWebAppListWebAppInstancesParameters).isTrue();
    AzureWebAppListWebAppInstancesParameters parameters =
        (AzureWebAppListWebAppInstancesParameters) azureTaskParameters;
    assertThat(parameters.getAppName()).isEqualTo("appName");
    assertThat(parameters.getSlotName()).isEqualTo("slotName");
    assertThat(parameters.getResourceGroupName()).isEqualTo("resourceGroupName");
    assertThat(parameters.getSubscriptionId()).isEqualTo("subscriptionId");
  }

  private PerpetualTaskClientContext getPerpetualTaskClientContext() {
    return PerpetualTaskClientContext.builder()
        .clientParams(
            ImmutableMap.of(HARNESS_APPLICATION_ID, InstanceSyncTestConstants.APP_ID, INFRASTRUCTURE_MAPPING_ID,
                InstanceSyncTestConstants.INFRA_MAPPING_ID, "appName", "appName", "slotName", "slotName"))
        .build();
  }
}

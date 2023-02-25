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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.request.AzureVMSSListVMDataParameters;
import io.harness.delegate.task.azure.request.AzureVMSSTaskParameters;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.service.impl.azure.manager.AzureVMSSCommandRequest;
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

public class AzureVMSSInstanceSyncPerpetualTaskClientTest extends WingsBaseTest {
  @Mock private InfrastructureMappingService mockInfraMappingService;
  @Mock private SettingsService mockSettingsService;
  @Mock private SecretManager mockSecretManager;

  @InjectMocks @Inject private AzureVMSSInstanceSyncPerpetualTaskClient client;

  // classloader() -->
  // paths.. io.hanrness.cd.
  // paths .. io.XXXXXX.ur6727.
  @Before
  public void setup() {
    AzureVMSSInfrastructureMapping infrastructureMapping = AzureVMSSInfrastructureMapping.builder().build();
    infrastructureMapping.setAppId(WingsTestConstants.APP_ID);
    infrastructureMapping.setAccountId(ACCOUNT_ID);
    infrastructureMapping.setUuid(INFRA_MAPPING_ID);
    infrastructureMapping.setSubscriptionId("subs-id");
    infrastructureMapping.setResourceGroupName("res-name");
    doReturn(infrastructureMapping).when(mockInfraMappingService).get(anyString(), anyString());
    doReturn(aSettingAttribute().withValue(AzureConfig.builder().build()).build()).when(mockSettingsService).get(any());
    doReturn(emptyList()).when(mockSecretManager).getEncryptionDetails(any(), anyString(), anyString());
  }

  @Test
  @Owner(developers = OwnerRule.SATYAM)
  @Category(UnitTests.class)
  public void testGetTaskParams() {
    AzureVmssInstanceSyncPerpetualTaskParams taskParams =
        (AzureVmssInstanceSyncPerpetualTaskParams) client.getTaskParams(getPerpetualTaskClientContext(), true);
    assertThat(taskParams).isNotNull();
    assertThat(taskParams.getVmssId()).isEqualTo("vmss-id");
    assertThat(taskParams.getSubscriptionId()).isEqualTo("subs-id");
    assertThat(taskParams.getResourceGroupName()).isEqualTo("res-name");
  }

  @Test
  @Owner(developers = OwnerRule.SATYAM)
  @Category(UnitTests.class)
  public void testGetValidationTask() {
    DelegateTask task = client.getValidationTask(getPerpetualTaskClientContext(), ACCOUNT_ID);
    assertThat(task).isNotNull();
    Object params = task.getData().getParameters()[0];
    assertThat(params instanceof AzureVMSSCommandRequest).isTrue();
    AzureVMSSCommandRequest commandRequest = (AzureVMSSCommandRequest) params;
    AzureVMSSTaskParameters azureVMSSTaskParameters = commandRequest.getAzureVMSSTaskParameters();
    assertThat(azureVMSSTaskParameters instanceof AzureVMSSListVMDataParameters).isTrue();
    AzureVMSSListVMDataParameters parameters = (AzureVMSSListVMDataParameters) azureVMSSTaskParameters;
    assertThat(parameters.getResourceGroupName()).isEqualTo("res-name");
    assertThat(parameters.getSubscriptionId()).isEqualTo("subs-id");
    assertThat(parameters.getVmssId()).isEqualTo("vmss-id");
  }

  private PerpetualTaskClientContext getPerpetualTaskClientContext() {
    return PerpetualTaskClientContext.builder()
        .clientParams(ImmutableMap.of(HARNESS_APPLICATION_ID, InstanceSyncTestConstants.APP_ID,
            INFRASTRUCTURE_MAPPING_ID, InstanceSyncTestConstants.INFRA_MAPPING_ID, "vmssId", "vmss-id"))
        .build();
  }
}

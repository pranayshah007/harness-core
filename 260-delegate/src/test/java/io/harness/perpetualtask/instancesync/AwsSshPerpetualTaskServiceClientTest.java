/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesync;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_ID;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.service.InstanceSyncConstants;
import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.model.AwsEc2ListInstancesRequest;
import software.wings.service.impl.instance.InstanceSyncTestConstants;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class AwsSshPerpetualTaskServiceClientTest extends WingsBaseTest {
  @Mock private SecretManager secretManager;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private AwsUtils awsUtils;
  @Mock private SettingsService settingsService;

  @InjectMocks @Inject private AwsSshPerpetualTaskServiceClient client;

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getTaskParams() {
    AwsConfig awsConfig = AwsConfig.builder().tag("abc").build();
    prepareTaskData(awsConfig);

    final AwsSshInstanceSyncPerpetualTaskParams taskParams =
        (AwsSshInstanceSyncPerpetualTaskParams) client.getTaskParams(
            PerpetualTaskClientContext.builder()
                .clientParams(ImmutableMap.of(InstanceSyncConstants.HARNESS_APPLICATION_ID, APP_ID,
                    InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID, INFRA_MAPPING_ID))
                .build());

    assertThat(taskParams.getRegion()).isEqualTo("us-east-1");
    assertThat(taskParams.getAwsConfig()).isNotNull();
    assertThat(taskParams.getFilter()).isNotNull();
    assertThat(taskParams.getEncryptedData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getValidationTask() {
    AwsConfig awsConfig = AwsConfig.builder().tag("abc").build();
    prepareTaskData(awsConfig);

    final DelegateTask validationTask =
        client.getValidationTask(PerpetualTaskClientContext.builder()
                                     .clientParams(ImmutableMap.of(InstanceSyncConstants.HARNESS_APPLICATION_ID, APP_ID,
                                         InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID, INFRA_MAPPING_ID))
                                     .build(),
            InstanceSyncTestConstants.ACCOUNT_ID);

    assertThat(validationTask)
        .isEqualToIgnoringGivenFields(
            DelegateTask.builder()
                .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
                .tags(singletonList("abc"))
                .data(TaskData.builder()
                          .async(false)
                          .taskType(TaskType.AWS_EC2_TASK.name())
                          .parameters(new Object[] {AwsEc2ListInstancesRequest.builder()
                                                        .awsConfig(awsConfig)
                                                        .encryptionDetails(new ArrayList<>())
                                                        .region("us-east-1")
                                                        .filters(new ArrayList<>())
                                                        .build()})
                          .timeout(TimeUnit.MINUTES.toMillis(InstanceSyncConstants.VALIDATION_TIMEOUT_MINUTES))
                          .build())
                .build(),
            DelegateTaskKeys.expiry, DelegateTaskKeys.validUntil);
  }

  private void prepareTaskData(AwsConfig awsConfig) {
    AwsInfrastructureMapping infraMapping = new AwsInfrastructureMapping();
    infraMapping.setRegion("us-east-1");
    infraMapping.setServiceId(InstanceSyncTestConstants.SERVICE_ID);
    infraMapping.setComputeProviderSettingId(InstanceSyncTestConstants.COMPUTE_PROVIDER_SETTING_ID);

    doReturn(infraMapping).when(infrastructureMappingService).get(APP_ID, INFRA_MAPPING_ID);
    doReturn(DeploymentType.SSH)
        .when(serviceResourceService)
        .getDeploymentType(infraMapping, null, infraMapping.getServiceId());
    doReturn(new ArrayList<>()).when(awsUtils).getFilters(DeploymentType.SSH, infraMapping.getAwsInstanceFilter());
    doReturn(SettingAttribute.Builder.aSettingAttribute().withValue(awsConfig).build())
        .when(settingsService)
        .get(InstanceSyncTestConstants.COMPUTE_PROVIDER_SETTING_ID);
    doReturn(new ArrayList<>()).when(secretManager).getEncryptionDetails(any(AwsConfig.class));
  }
}

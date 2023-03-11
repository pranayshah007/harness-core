/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesync;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.service.InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_SETTING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SPOTINST_CLOUD_PROVIDER;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.US_EAST;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.spotinst.request.SpotInstListElastigroupInstancesParameters;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsAmiInfrastructureMapping.Builder;
import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.TaskType;
import software.wings.service.impl.spotinst.SpotInstCommandRequest;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(CDP)
public class SpotinstAmiInstanceSyncPerpetualTaskClientTest extends WingsBaseTest {
  private static final String ELASTIGROUP_ID = "elasti-group-id";

  @Mock InfrastructureMappingService infraMappingService;
  @Mock SettingsService settingsService;
  @Mock SecretManager secretManager;

  @InjectMocks @Inject SpotinstAmiInstanceSyncPerpetualTaskClient client;

  @Before
  public void setUp() {
    SpotInstConfig spotInstConfig = SpotInstConfig.builder().build();
    AwsConfig awsConfig = AwsConfig.builder().build();

    when(infraMappingService.get(anyString(), anyString()))
        .thenReturn(Builder.anAwsAmiInfrastructureMapping()
                        .withRegion(US_EAST)
                        .withSpotinstCloudProvider(SPOTINST_CLOUD_PROVIDER)
                        .withComputeProviderName(COMPUTE_PROVIDER_NAME)
                        .withComputeProviderSettingId(COMPUTE_PROVIDER_SETTING_ID)
                        .build());
    when(settingsService.get(SPOTINST_CLOUD_PROVIDER))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withValue(spotInstConfig).build());
    when(settingsService.get(COMPUTE_PROVIDER_SETTING_ID))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withValue(awsConfig).build());
    when(secretManager.getEncryptionDetails(spotInstConfig, null, null)).thenReturn(Collections.emptyList());
    when(secretManager.getEncryptionDetails(awsConfig, null, null)).thenReturn(Collections.emptyList());
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void getTaskParams() {
    final SpotinstAmiInstanceSyncPerpetualTaskParams taskParams =
        (SpotinstAmiInstanceSyncPerpetualTaskParams) client.getTaskParams(getPerpetualTaskClientContext());

    assertThat(taskParams.getRegion()).isEqualTo(US_EAST);
    assertThat(taskParams.getElastigroupId()).isEqualTo(ELASTIGROUP_ID);
    assertThat(taskParams.getAwsConfig()).isNotEmpty();
    assertThat(taskParams.getAwsEncryptedData()).isNotEmpty();
    assertThat(taskParams.getSpotinstConfig()).isNotEmpty();
    assertThat(taskParams.getSpotinstEncryptedData()).isNotEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void getValidationTask() {
    DelegateTask delegateTask = client.getValidationTask(getPerpetualTaskClientContext(), ACCOUNT_ID);

    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(GLOBAL_APP_ID);
    assertThat(delegateTask.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(delegateTask.getData().getTaskType()).isEqualTo(TaskType.SPOTINST_COMMAND_TASK.name());
    assertThat(delegateTask.getData().getParameters()).isNotEmpty();
    assertThat(delegateTask.getData().getParameters()[0]).isInstanceOf(SpotInstCommandRequest.class);
    SpotInstCommandRequest commandRequest = (SpotInstCommandRequest) delegateTask.getData().getParameters()[0];
    assertThat(commandRequest.getAwsConfig()).isNotNull();
    assertThat(commandRequest.getAwsEncryptionDetails()).isNotNull();
    assertThat(commandRequest.getSpotInstConfig()).isNotNull();
    assertThat(commandRequest.getSpotinstEncryptionDetails()).isNotNull();
    assertThat(commandRequest.getSpotInstTaskParameters())
        .isInstanceOf(SpotInstListElastigroupInstancesParameters.class);
    SpotInstListElastigroupInstancesParameters parameters =
        (SpotInstListElastigroupInstancesParameters) commandRequest.getSpotInstTaskParameters();
    assertThat(parameters.getAwsRegion()).isEqualTo(US_EAST);
    assertThat(parameters.getElastigroupId()).isEqualTo(ELASTIGROUP_ID);
  }

  private SpotinstAmiInstanceSyncPerpetualTaskClientParams getPerpetualTaskClientParams() {
    return SpotinstAmiInstanceSyncPerpetualTaskClientParams.builder()
        .inframappingId(INFRA_MAPPING_ID)
        .appId(APP_ID)
        .elastigroupId(ELASTIGROUP_ID)
        .build();
  }

  private PerpetualTaskClientContext getPerpetualTaskClientContext() {
    return PerpetualTaskClientContext.builder()
        .clientParams(ImmutableMap.of(HARNESS_APPLICATION_ID, APP_ID, INFRASTRUCTURE_MAPPING_ID, INFRA_MAPPING_ID,
            SpotinstAmiInstanceSyncPerpetualTaskClient.ELASTIGROUP_ID, ELASTIGROUP_ID))
        .build();
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.rule.OwnerRule.ARVIND;

import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.HostReachabilityInfo;
import software.wings.beans.HostValidationTaskParameters;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.PhysicalInfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.PhysicalHostInstanceInfo;
import software.wings.beans.infrastructure.instance.key.HostInstanceKey;
import software.wings.service.PdcInstanceSyncPerpetualTaskCreator;
import software.wings.service.impl.aws.model.response.HostReachabilityResponse;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PdcInstanceHandlerTest extends WingsBaseTest {
  @Mock private DelegateService delegateService;
  @Mock private PdcInstanceSyncPerpetualTaskCreator perpetualTaskCreator;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private SettingsService settingsService;
  @Mock private SecretManager secretManager;
  @Mock private InstanceService instanceService;
  @Captor private ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor;
  @Captor private ArgumentCaptor<Set<String>> setArgumentCaptor;

  @InjectMocks @Inject PdcInstanceHandler pdcInstanceHandler;
  private static final String ERROR_MESSAGE = "Error message";
  private static final String INFRA_ID = "INFRA_ID";
  private static final String APP_ID = "APP_ID";
  private InfrastructureMapping infrastructureMapping;

  @Before
  public void setup() {
    HostConnectionAttributes hostConnectionAttributes =
        HostConnectionAttributes.Builder.aHostConnectionAttributes().build();

    infrastructureMapping = PhysicalInfrastructureMapping.Builder.aPhysicalInfrastructureMapping()
                                .withAppId(HARNESS_APPLICATION_ID)
                                .withUuid(INFRA_ID)
                                .build();
    when(infrastructureMappingService.get(APP_ID, INFRA_ID)).thenReturn(infrastructureMapping);
    when(settingsService.get(any()))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withValue(hostConnectionAttributes).build());
    when(secretManager.getEncryptionDetails(hostConnectionAttributes, null, null)).thenReturn(Collections.emptyList());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetStatusFailure() {
    HostReachabilityResponse reachabilityResponse =
        HostReachabilityResponse.builder().executionStatus(ExecutionStatus.FAILED).errorMessage(ERROR_MESSAGE).build();
    Status status = pdcInstanceHandler.getStatus(infrastructureMapping, reachabilityResponse);
    assertThat(status.getErrorMessage()).isEqualTo(ERROR_MESSAGE);
    assertThat(status.isRetryable()).isFalse();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetStatusRetryable() {
    HostReachabilityResponse reachabilityResponse =
        HostReachabilityResponse.builder()
            .hostReachabilityInfoList(
                Arrays.asList(HostReachabilityInfo.builder().hostName("h1").reachable(true).build(),
                    HostReachabilityInfo.builder().hostName("h2").reachable(false).build()))
            .executionStatus(ExecutionStatus.SUCCESS)
            .build();
    Status status = pdcInstanceHandler.getStatus(infrastructureMapping, reachabilityResponse);
    assertThat(status.getErrorMessage()).isNull();
    assertThat(status.isRetryable()).isTrue();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetStatusNonRetryable() {
    HostReachabilityResponse reachabilityResponse =
        HostReachabilityResponse.builder()
            .hostReachabilityInfoList(
                Arrays.asList(HostReachabilityInfo.builder().hostName("h1").reachable(false).build(),
                    HostReachabilityInfo.builder().hostName("h2").reachable(false).build()))
            .executionStatus(ExecutionStatus.SUCCESS)
            .build();

    Status status = pdcInstanceHandler.getStatus(infrastructureMapping, reachabilityResponse);
    assertThat(status.getErrorMessage()).isNull();
    assertThat(status.isRetryable()).isFalse();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testSyncInstancesFailure1() {
    doReturn(AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping().build())
        .when(infrastructureMappingService)
        .get(APP_ID, INFRA_ID);
    assertThatThrownBy(() -> pdcInstanceHandler.syncInstances(APP_ID, INFRA_ID, InstanceSyncFlow.PERPETUAL_TASK))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testSyncInstances() throws InterruptedException {
    List<Instance> instances = new ArrayList<>();
    instances.add(Instance.builder()
                      .uuid("h1")
                      .instanceInfo(PhysicalHostInstanceInfo.builder().hostName("h1").build())
                      .hostInstanceKey(HostInstanceKey.builder().hostName("h1").build())
                      .build());
    instances.add(Instance.builder()
                      .uuid("h2")
                      .instanceInfo(PhysicalHostInstanceInfo.builder().hostName("h2").build())
                      .hostInstanceKey(HostInstanceKey.builder().hostName("h2").build())
                      .build());
    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(APP_ID, INFRA_ID);

    when(delegateService.executeTask(delegateTaskArgumentCaptor.capture()))
        .thenReturn(HostReachabilityResponse.builder()
                        .hostReachabilityInfoList(
                            Arrays.asList(HostReachabilityInfo.builder().hostName("h1").reachable(true).build(),
                                HostReachabilityInfo.builder().hostName("h2").reachable(false).build()))
                        .build(),
            RemoteMethodReturnValueData.builder().build());

    doReturn(true).when(instanceService).delete(setArgumentCaptor.capture());

    pdcInstanceHandler.syncInstances(APP_ID, INFRA_ID, InstanceSyncFlow.MANUAL);
    assertThat(setArgumentCaptor.getValue()).contains("h2");
    assertThat(setArgumentCaptor.getValue()).hasSize(1);

    List<DelegateTask> delegateTasks = delegateTaskArgumentCaptor.getAllValues();
    assertThat(delegateTasks).hasSize(2);
    assertThat(((HostValidationTaskParameters) delegateTasks.get(0).getData().getParameters()[0]).getHostNames())
        .containsExactlyInAnyOrder("h2");
  }
}

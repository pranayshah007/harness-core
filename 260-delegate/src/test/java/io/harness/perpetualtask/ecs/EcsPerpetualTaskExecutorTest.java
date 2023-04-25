/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.ecs;

import static io.harness.ccm.CcmConstants.CLUSTER_ID_IDENTIFIER;
import static io.harness.rule.OwnerRule.AVMOHAN;
import static io.harness.rule.OwnerRule.HITESH;

import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import io.harness.DelegateTestBase;
import io.harness.category.element.UnitTests;
import io.harness.event.client.EventPublisher;
import io.harness.event.payloads.EcsUtilization;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.ecs.support.EcsMetricClient;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.time.FakeClock;

import software.wings.beans.AwsConfig;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEcsHelperServiceDelegate;

import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.Service;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(Parameterized.class)
public class EcsPerpetualTaskExecutorTest extends DelegateTestBase {
  @Mock private AwsEcsHelperServiceDelegate ecsHelperServiceDelegate;
  @Mock private AwsEc2HelperServiceDelegate ec2ServiceDelegate;
  @Mock private EcsMetricClient ecsMetricClient;
  @Mock private EventPublisher eventPublisher;
  @Inject KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Parameterized.Parameter public Clock clock;
  @Parameterized.Parameters(name = "{index}: with clock: {0}")
  public static Clock[] clocks() {
    return new Clock[] {new FakeClock(), // current time
        new FakeClock().instant(Instant.parse("2020-03-03T10:00:00.00Z")), // exact hour
        new FakeClock().instant(Instant.parse("2020-03-03T10:00:01.00Z")), // second after
        new FakeClock().instant(Instant.parse("2020-03-03T10:59:59.00Z"))}; // second before
  }

  private EcsPerpetualTaskExecutor ecsPerpetualTaskExecutor;
  private Cache<String, EcsActiveInstancesCache> cache;

  @Captor private ArgumentCaptor<Message> messageCaptor;
  @Captor ArgumentCaptor<Map<String, String>> mapArgumentCaptor;

  private final String REGION = "us-east-1";
  private final String ACCOUNT_ID = "accountId";
  private final String CLUSTER_ID = "clusterId";
  private final String SETTING_ID = "settingId";
  private final String CLUSTER_NAME = "ecs-ccm-cluster";
  private final String PERPETUAL_TASK_ID = "perpetualTaskId";
  private final String CLUSTER_ARN = "arn:aws:ecs:us-east-2:132359207506:cluster/ecs-ccm-cluster";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    ecsPerpetualTaskExecutor = new EcsPerpetualTaskExecutor(ecsHelperServiceDelegate, ec2ServiceDelegate,
        ecsMetricClient, eventPublisher, clock, kryoSerializer, referenceFalseKryoSerializer);
    cache = Reflect.on(ecsPerpetualTaskExecutor).get("cache");
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldQueryEcsMetricClientAndPublishUtilizationMessages() throws Exception {
    AwsConfig awsConfig = AwsConfig.builder().build();
    List<EncryptedDataDetail> encryptionDetails = Collections.emptyList();
    final Instant now = Instant.now(clock);
    Instant metricsCollectedTillHour = now.truncatedTo(HOURS).minus(1, HOURS);
    cache.put(CLUSTER_ID, EcsActiveInstancesCache.builder().metricsCollectedTillHour(metricsCollectedTillHour).build());
    final ImmutableList<Service> services =
        ImmutableList.of(new Service()
                             .withServiceArn("arn:aws:ecs:us-east-2:132359207506:service/ccm-test-service")
                             .withServiceName("ccm-test-service"),
            new Service()
                .withServiceArn("arn:aws:ecs:us-east-1:132359207506:service/ccm-test-service")
                .withServiceName("ccm-test-service"));
    List<EcsUtilization> utilizationMessages =
        ImmutableList.of(EcsUtilization.newBuilder().build(), EcsUtilization.newBuilder().build());

    given(ecsHelperServiceDelegate.listServicesForCluster(awsConfig, encryptionDetails, REGION, CLUSTER_ARN))
        .willReturn(services);
    final EcsPerpetualTaskParams ecsPerpetualTaskParams =
        EcsPerpetualTaskParams.newBuilder().setClusterId(CLUSTER_ID).setSettingId(SETTING_ID).setRegion(REGION).build();
    given(ecsMetricClient.getUtilizationMetrics(eq(awsConfig), eq(encryptionDetails),
              eq(Date.from(metricsCollectedTillHour)), eq(Date.from(now.truncatedTo(HOURS))),
              eq(new Cluster().withClusterName(CLUSTER_NAME).withClusterArn(CLUSTER_ARN)), eq(services),
              eq(ecsPerpetualTaskParams)))
        .willReturn(utilizationMessages);

    Instant heartbeatTime = Instant.EPOCH;
    ecsPerpetualTaskExecutor.publishUtilizationMetrics(
        ecsPerpetualTaskParams, awsConfig, encryptionDetails, CLUSTER_ARN, now, heartbeatTime);

    then(eventPublisher)
        .should(times(2))
        .publishMessage(
            messageCaptor.capture(), eq(HTimestamps.fromInstant(now.truncatedTo(HOURS))), mapArgumentCaptor.capture());
    assertThat(messageCaptor.getAllValues()).hasSize(2).containsAll(utilizationMessages);
    assertThat(mapArgumentCaptor.getValue().keySet()).contains(CLUSTER_ID_IDENTIFIER);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldQueryEcsClientAndPublishSyncMessages() {
    Set<String> activeEc2InstanceIds = new HashSet<>(Arrays.asList("instance1", "instance2"));
    Set<String> activeContainerInstanceArns = new HashSet<>(Collections.singletonList("containerInstance1"));
    Set<String> activeTaskArns = new HashSet<>(Arrays.asList("task1", "task2"));
    Instant pollTime = Instant.now(clock);

    ecsPerpetualTaskExecutor.publishEcsClusterSyncEvent(CLUSTER_ID, SETTING_ID, CLUSTER_NAME, activeEc2InstanceIds,
        activeContainerInstanceArns, activeTaskArns, pollTime);
    then(eventPublisher)
        .should(times(1))
        .publishMessage(messageCaptor.capture(), eq(HTimestamps.fromInstant(pollTime)), mapArgumentCaptor.capture());
    assertThat(messageCaptor.getAllValues()).hasSize(1);
    assertThat(mapArgumentCaptor.getValue().keySet()).contains(CLUSTER_ID_IDENTIFIER);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldRunEcsPerpetualTask() {
    Instant heartBeatTime = Instant.now(clock);

    AwsConfig awsConfig = AwsConfig.builder().accountId(ACCOUNT_ID).build();
    ByteString bytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(awsConfig));

    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>(Collections.singletonList(encryptedDataDetail));
    ByteString encryptionDetailBytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(encryptionDetails));

    EcsPerpetualTaskParams ecsPerpetualTaskParams = EcsPerpetualTaskParams.newBuilder()
                                                        .setClusterId(CLUSTER_ID)
                                                        .setRegion(REGION)
                                                        .setSettingId(SETTING_ID)
                                                        .setClusterName(CLUSTER_NAME)
                                                        .setAwsConfig(bytes)
                                                        .setEncryptionDetail(encryptionDetailBytes)
                                                        .build();

    PerpetualTaskExecutionParams params =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(ecsPerpetualTaskParams)).build();
    PerpetualTaskId perpetualTaskId = PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build();
    PerpetualTaskResponse perpetualTaskResponse =
        ecsPerpetualTaskExecutor.runOnce(perpetualTaskId, params, heartBeatTime);
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(200);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldQuerySingleHourWindowInNormalCase() throws Exception {
    final Instant now = Instant.now(clock);
    Instant heartBeatTime = now.minus(Duration.ofHours(3));
    Instant metricsCollectedTillHour = now.truncatedTo(HOURS).minus(1, HOURS);
    cache.put(CLUSTER_ID, EcsActiveInstancesCache.builder().metricsCollectedTillHour(metricsCollectedTillHour).build());
    AwsConfig awsConfig = AwsConfig.builder().accountId(ACCOUNT_ID).build();
    ByteString bytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(awsConfig));
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>(Collections.singletonList(encryptedDataDetail));
    ByteString encryptionDetailBytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(encryptionDetails));
    EcsPerpetualTaskParams ecsPerpetualTaskParams = EcsPerpetualTaskParams.newBuilder()
                                                        .setClusterId(CLUSTER_ID)
                                                        .setRegion(REGION)
                                                        .setSettingId(SETTING_ID)
                                                        .setClusterName(CLUSTER_NAME)
                                                        .setAwsConfig(bytes)
                                                        .setEncryptionDetail(encryptionDetailBytes)
                                                        .build();

    PerpetualTaskExecutionParams params =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(ecsPerpetualTaskParams)).build();
    PerpetualTaskId perpetualTaskId = PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build();
    ecsPerpetualTaskExecutor.runOnce(perpetualTaskId, params, heartBeatTime);

    then(ecsMetricClient)
        .should(times(1))
        .getUtilizationMetrics(any(AwsConfig.class), anyList(),
            eq(Date.from(now.minus(Duration.ofHours(1)).truncatedTo(HOURS))), eq(Date.from(now.truncatedTo(HOURS))),
            any(Cluster.class), anyList(), any(EcsPerpetualTaskParams.class));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldQuery24HoursIfNoLastHeartBeat() throws Exception {
    Instant heartBeatTime = Instant.ofEpochMilli(0);
    AwsConfig awsConfig = AwsConfig.builder().accountId(ACCOUNT_ID).build();
    ByteString bytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(awsConfig));
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>(Collections.singletonList(encryptedDataDetail));
    ByteString encryptionDetailBytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(encryptionDetails));
    EcsPerpetualTaskParams ecsPerpetualTaskParams = EcsPerpetualTaskParams.newBuilder()
                                                        .setClusterId(CLUSTER_ID)
                                                        .setRegion(REGION)
                                                        .setSettingId(SETTING_ID)
                                                        .setClusterName(CLUSTER_NAME)
                                                        .setAwsConfig(bytes)
                                                        .setEncryptionDetail(encryptionDetailBytes)
                                                        .build();

    PerpetualTaskExecutionParams params =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(ecsPerpetualTaskParams)).build();
    PerpetualTaskId perpetualTaskId = PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build();
    ecsPerpetualTaskExecutor.runOnce(perpetualTaskId, params, heartBeatTime);

    Instant now = Instant.now(clock);
    then(ecsMetricClient)
        .should(times(1))
        .getUtilizationMetrics(eq(awsConfig), eq(encryptionDetails),
            eq(Date.from(now.truncatedTo(HOURS).minus(Duration.ofHours(24)))), eq(Date.from(now.truncatedTo(HOURS))),
            any(Cluster.class), anyList(), any(EcsPerpetualTaskParams.class));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandleMultiHourWindowIfMissingLessThan24Hours() throws Exception {
    Instant now = Instant.now(clock);
    Instant heartBeatTime = now.minus(Duration.ofHours(7));
    AwsConfig awsConfig = AwsConfig.builder().accountId(ACCOUNT_ID).build();
    ByteString bytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(awsConfig));
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>(Collections.singletonList(encryptedDataDetail));
    ByteString encryptionDetailBytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(encryptionDetails));
    EcsPerpetualTaskParams ecsPerpetualTaskParams = EcsPerpetualTaskParams.newBuilder()
                                                        .setClusterId(CLUSTER_ID)
                                                        .setRegion(REGION)
                                                        .setSettingId(SETTING_ID)
                                                        .setClusterName(CLUSTER_NAME)
                                                        .setAwsConfig(bytes)
                                                        .setEncryptionDetail(encryptionDetailBytes)
                                                        .build();

    PerpetualTaskExecutionParams params =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(ecsPerpetualTaskParams)).build();
    PerpetualTaskId perpetualTaskId = PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build();
    ecsPerpetualTaskExecutor.runOnce(perpetualTaskId, params, heartBeatTime);

    then(ecsMetricClient)
        .should(times(1))
        .getUtilizationMetrics(eq(awsConfig), eq(encryptionDetails), eq(Date.from(heartBeatTime.truncatedTo(HOURS))),
            eq(Date.from(now.truncatedTo(HOURS))), any(Cluster.class), anyList(), any(EcsPerpetualTaskParams.class));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldNotPublishUtilizationMetricsIfAlreadyWithinLastHour() throws Exception {
    Instant now = Instant.now(clock);
    Instant heartBeatTime = now.minus(Duration.ofHours(7));
    Instant metricsCollectedTillHour = now.truncatedTo(HOURS);
    cache.put(CLUSTER_ID, EcsActiveInstancesCache.builder().metricsCollectedTillHour(metricsCollectedTillHour).build());
    AwsConfig awsConfig = AwsConfig.builder().accountId(ACCOUNT_ID).build();
    ByteString bytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(awsConfig));
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>(Collections.singletonList(encryptedDataDetail));
    ByteString encryptionDetailBytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(encryptionDetails));
    EcsPerpetualTaskParams ecsPerpetualTaskParams = EcsPerpetualTaskParams.newBuilder()
                                                        .setClusterId(CLUSTER_ID)
                                                        .setRegion(REGION)
                                                        .setSettingId(SETTING_ID)
                                                        .setClusterName(CLUSTER_NAME)
                                                        .setAwsConfig(bytes)
                                                        .setEncryptionDetail(encryptionDetailBytes)
                                                        .build();

    PerpetualTaskExecutionParams params =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(ecsPerpetualTaskParams)).build();
    PerpetualTaskId perpetualTaskId = PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build();
    ecsPerpetualTaskExecutor.runOnce(perpetualTaskId, params, heartBeatTime);

    then(ecsMetricClient)
        .should(never())
        .getUtilizationMetrics(any(AwsConfig.class), anyList(), any(Date.class), any(Date.class), any(Cluster.class),
            anyList(), any(EcsPerpetualTaskParams.class));
  }
}

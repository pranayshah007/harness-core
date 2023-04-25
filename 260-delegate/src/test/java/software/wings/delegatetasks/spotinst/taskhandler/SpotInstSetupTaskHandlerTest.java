/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.spotinst.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.spotinst.model.SpotInstConstants.ELASTI_GROUP_CREATED_AT;
import static io.harness.spotinst.model.SpotInstConstants.ELASTI_GROUP_ID;
import static io.harness.spotinst.model.SpotInstConstants.ELASTI_GROUP_UPDATED_AT;
import static io.harness.spotinst.model.SpotInstConstants.defaultSteadyStateTimeout;

import static software.wings.service.impl.aws.model.AwsConstants.FORWARD_LISTENER_ACTION;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.aws.AwsElbListener;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.spotinst.request.SpotInstSetupTaskParameters;
import io.harness.delegate.task.spotinst.response.SpotInstSetupTaskResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import io.harness.spotinst.model.ElastiGroupInstanceHealth;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.elasticloadbalancingv2.model.Action;
import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class SpotInstSetupTaskHandlerTest extends WingsBaseTest {
  @Mock private DelegateLogService mockDelegateLogService;
  @Mock private SpotInstHelperServiceDelegate mockSpotInstHelperServiceDelegate;
  @Mock private AwsElbHelperServiceDelegate mockAwsElbHelperServiceDelegate;
  @Mock private TimeLimiter mockTimeLimiter;
  @Mock private AwsEc2HelperServiceDelegate mockAwsEc2HelperServiceDelegate;

  @Spy @Inject @InjectMocks SpotInstSetupTaskHandler spotInstSetupTaskHandler;

  public final String json = "{\n"
      + "  \"group\": {\n"
      + "    \"name\": \"adwait_11\",\n"
      + "    \"capacity\": {\n"
      + "      \"minimum\": 1,\n"
      + "      \"maximum\": 3,\n"
      + "      \"target\": 1,\n"
      + "      \"unit\": \"instance\"\n"
      + "    },\n"
      + "    \"strategy\": {\n"
      + "      \"risk\": 100,\n"
      + "      \"onDemandCount\": null,\n"
      + "      \"availabilityVsCost\": \"balanced\",\n"
      + "      \"drainingTimeout\": 120,\n"
      + "      \"lifetimePeriod\": \"days\",\n"
      + "      \"fallbackToOd\": true,\n"
      + "      \"scalingStrategy\": {},\n"
      + "      \"persistence\": {},\n"
      + "      \"revertToSpot\": {\n"
      + "        \"performAt\": \"always\"\n"
      + "      }\n"
      + "    },\n"
      + "    \"compute\": {\n"
      + "      \"instanceTypes\": {\n"
      + "        \"ondemand\": \"t2.small\",\n"
      + "        \"spot\": [\n"
      + "          \"m3.medium\",\n"
      + "          \"t2.small\",\n"
      + "          \"t3.small\",\n"
      + "          \"t3a.small\",\n"
      + "          \"t2.medium\",\n"
      + "          \"t3.medium\",\n"
      + "          \"t3a.medium\",\n"
      + "          \"a1.medium\"\n"
      + "        ]\n"
      + "      },\n"
      + "      \"availabilityZones\": [\n"
      + "        {\n"
      + "          \"name\": \"us-east-1a\",\n"
      + "          \"subnetIds\": [\n"
      + "            \"subnet-1f703d78\"\n"
      + "          ]\n"
      + "        },\n"
      + "        {\n"
      + "          \"name\": \"us-east-1b\",\n"
      + "          \"subnetIds\": [\n"
      + "            \"subnet-01bdf52f\"\n"
      + "          ]\n"
      + "        },\n"
      + "        {\n"
      + "          \"name\": \"us-east-1c\",\n"
      + "          \"subnetIds\": [\n"
      + "            \"subnet-33eaf779\"\n"
      + "          ]\n"
      + "        },\n"
      + "        {\n"
      + "          \"name\": \"us-east-1d\",\n"
      + "          \"subnetIds\": [\n"
      + "            \"subnet-c1ce809d\"\n"
      + "          ]\n"
      + "        },\n"
      + "        {\n"
      + "          \"name\": \"us-east-1e\",\n"
      + "          \"subnetIds\": [\n"
      + "            \"subnet-7427b64a\"\n"
      + "          ]\n"
      + "        },\n"
      + "        {\n"
      + "          \"name\": \"us-east-1f\",\n"
      + "          \"subnetIds\": [\n"
      + "            \"subnet-11efe81e\"\n"
      + "          ]\n"
      + "        }\n"
      + "      ],\n"
      + "      \"preferredAvailabilityZones\": null,\n"
      + "      \"product\": \"Linux/UNIX\",\n"
      + "      \"launchSpecification\": {\n"
      + "        \"loadBalancerNames\": null,\n"
      + "        \"loadBalancersConfig\": {\n"
      + "          \"loadBalancers\": [\n"
      + "            {\n"
      + "              \"name\": \"someName\",\n"
      + "              \"arn\": \"arn\",\n"
      + "              \"type\": \"TARGET_GROUP\"\n"
      + "            }\n"
      + "          ]\n"
      + "        },\n"
      + "        \"healthCheckType\": null,\n"
      + "        \"securityGroupIds\": [\n"
      + "          \"sg-d748f48f\"\n"
      + "        ],\n"
      + "        \"monitoring\": false,\n"
      + "        \"ebsOptimized\": false,\n"
      + "        \"imageId\": \"ami-1234\",\n"
      + "        \"keyPair\": \"some_name\",\n"
      + "        \"userData\": null,\n"
      + "        \"shutdownScript\": null,\n"
      + "        \"tenancy\": \"default\"\n"
      + "      },\n"
      + "      \"elasticIps\": null\n"
      + "    },\n"
      + "    \"scaling\": {\n"
      + "      \"up\": null,\n"
      + "      \"down\": null\n"
      + "    },\n"
      + "    \"scheduling\": {\n"
      + "      \"tasks\": null\n"
      + "    },\n"
      + "    \"thirdPartiesIntegration\": {},\n"
      + "    \"multai\": null\n"
      + "  }\n"
      + "}";

  public final String newJsonConfig =
      "{\"group\":{\"name\":\"newName\",\"capacity\":{\"minimum\":0,\"maximum\":0,\"target\":0,"
      + "\"unit\":\"instance\"},"
      + "\"strategy\":{\"risk\":100.0,\"availabilityVsCost\":\"balanced\","
      + "\"drainingTimeout\":120.0,\"lifetimePeriod\":\"days\",\"fallbackToOd\":true,"
      + "\"scalingStrategy\":{},\"persistence\":{},\"revertToSpot\":{\"performAt\":\"always\"}},"
      + "\"compute\":"
      + "{\"instanceTypes\":"
      + "{\"ondemand\":\"t2.small\",\"spot\":[\"m3.medium\",\"t2.small\",\"t3.small\",\"t3a.small\","
      + "\"t2.medium\",\"t3.medium\",\"t3a.medium\",\"a1.medium\"]},"
      + "\"availabilityZones\":[{\"name\":\"us-east-1a\","
      + "\"subnetIds\":[\"subnet-1f703d78\"]},{\"name\":\"us-east-1b\",\"subnetIds\":[\"subnet-01bdf52f\"]}"
      + ",{\"name\":\"us-east-1c\",\"subnetIds\":[\"subnet-33eaf779\"]}"
      + ",{\"name\":\"us-east-1d\",\"subnetIds\":[\"subnet-c1ce809d\"]}"
      + ",{\"name\":\"us-east-1e\",\"subnetIds\":[\"subnet-7427b64a\"]}"
      + ",{\"name\":\"us-east-1f\",\"subnetIds\":[\"subnet-11efe81e\"]}]"
      + ",\"product\":\"Linux/UNIX\","
      + "\"launchSpecification\":"
      + "{\"loadBalancersConfig\":"
      + "{\"loadBalancers\":[{\"name\":\"stageTg1\",\"arn\":\"s_tg1\",\"type\":\"TARGET_GROUP\"},"
      + "{\"name\":\"stageTg2\",\"arn\":\"s_tg2\",\"type\":\"TARGET_GROUP\"}]},"
      + "\"securityGroupIds\":[\"sg-d748f48f\"],"
      + "\"monitoring\":false,\"ebsOptimized\":false,\"imageId\":\"img-123456\","
      + "\"keyPair\":\"some_name\",\"userData\":\"userData\",\"tenancy\":\"default\"}},\"scaling\":{},\"scheduling\":{},"
      + "\"thirdPartiesIntegration\":{}}}";

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateFinalJson() {
    SpotInstSetupTaskParameters parameters =
        SpotInstSetupTaskParameters.builder()
            .blueGreen(true)
            .image("img-123456")
            .elastiGroupJson(json)
            .userData("userData")
            .awsLoadBalancerConfigs(Arrays.asList(LoadBalancerDetailsForBGDeployment.builder()
                                                      .loadBalancerName("lb1")
                                                      .loadBalancerArn("arn1")
                                                      .prodTargetGroupName("p_tg1")
                                                      .prodTargetGroupName("prodTg1")
                                                      .stageTargetGroupName("stageTg1")
                                                      .stageTargetGroupArn("s_tg1")
                                                      .build(),
                LoadBalancerDetailsForBGDeployment.builder()
                    .loadBalancerName("lb2")
                    .loadBalancerArn("arn2")
                    .prodTargetGroupName("p_tg2")
                    .prodTargetGroupName("prodTg2")
                    .stageTargetGroupName("stageTg2")
                    .stageTargetGroupArn("s_tg2")
                    .build()))
            .build();
    assertThat(spotInstSetupTaskHandler.generateFinalJson(parameters, "newName")).isEqualTo(newJsonConfig);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalForBlueGreenThrowsException() throws Exception {
    String loadBalancerName = "LOAD_BALANCER_NAME";

    SpotInstSetupTaskParameters parameters =
        SpotInstSetupTaskParameters.builder()
            .blueGreen(true)
            .elastiGroupNamePrefix("foo")
            .elastiGroupJson("JSON")
            .image("ami-id")
            .awsLoadBalancerConfigs(Collections.singletonList(LoadBalancerDetailsForBGDeployment.builder()
                                                                  .loadBalancerName(loadBalancerName)
                                                                  .prodListenerPort("H80")
                                                                  .stageListenerPort("H80")
                                                                  .build()))
            .build();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doNothing().when(mockCallback).saveExecutionLog(anyString(), any(), any());

    SpotInstTaskExecutionResponse response = spotInstSetupTaskHandler.executeTaskInternalForBlueGreen(
        parameters, "SPOTINST_ACCOUNT_ID", "TOKEN", AwsConfig.builder().build(), mockCallback);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalForBlueGreen() throws Exception {
    String loadBalancerArn = "LOAD_BALANCER_ARN";
    String loadBalancerName = "LOAD_BALANCER_NAME";
    String protocol = "http";
    String prodListenerArn = "PROD_LISTENER_ARN";
    String prodTargetGroupArn = "PROD_TARGET_GROUP_ARN";
    int prodPort = 8080;
    String stageListenerArn = "STAGE_LISTENER_ARN";
    int stagePort = 8181;
    String stageTargetGroupArn = "STAGE_TARGET_GROUP_ARN";
    doReturn(newArrayList(AwsElbListener.builder()
                              .protocol(protocol)
                              .loadBalancerArn(loadBalancerArn)
                              .port(prodPort)
                              .listenerArn(prodListenerArn)
                              .build(),
                 AwsElbListener.builder()
                     .protocol(protocol)
                     .loadBalancerArn(loadBalancerArn)
                     .port(stagePort)
                     .listenerArn(stageListenerArn)
                     .build()))
        .when(mockAwsElbHelperServiceDelegate)
        .getElbListenersForLoadBalaner(any(), anyList(), any(), any());
    String stageElastiGroupOldId = "STAGE_ELASTI_GROUP_OLD_ID";
    String prodElastiGroupOldId = "PROD_ELASTI_GROUP_OLD_ID";
    String stageElastiGroupNewId = "STAGE_ELASTI_GROUP_NEW_ID";
    doReturn(Optional.of(ElastiGroup.builder().id(stageElastiGroupOldId).name("foo__STAGE__Harness").build()))
        .doReturn(Optional.of(ElastiGroup.builder().id(prodElastiGroupOldId).name("foo").build()))
        .when(mockSpotInstHelperServiceDelegate)
        .getElastiGroupByName(any(), any(), any());
    doReturn("JSON").when(spotInstSetupTaskHandler).generateFinalJson(any(), any());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());
    doNothing().when(mockCallback).saveExecutionLog(any(), any(), any());
    SpotInstSetupTaskParameters parameters =
        SpotInstSetupTaskParameters.builder()
            .blueGreen(true)
            .elastiGroupNamePrefix("foo")
            .elastiGroupJson("JSON")
            .image("ami-id")
            .awsLoadBalancerConfigs(Collections.singletonList(LoadBalancerDetailsForBGDeployment.builder()
                                                                  .loadBalancerName(loadBalancerName)
                                                                  .prodListenerPort(String.valueOf(prodPort))
                                                                  .stageListenerPort(String.valueOf(stagePort))
                                                                  .build()))
            .build();
    doReturn(new Listener().withDefaultActions(
                 new Action().withTargetGroupArn(prodTargetGroupArn).withType(FORWARD_LISTENER_ACTION)))
        .doReturn(new Listener().withDefaultActions(
            new Action().withTargetGroupArn(stageTargetGroupArn).withType(FORWARD_LISTENER_ACTION)))
        .when(mockAwsElbHelperServiceDelegate)
        .getElbListener(any(), any(), any(), any());
    doReturn(prodTargetGroupArn)
        .doReturn(stageTargetGroupArn)
        .when(mockAwsElbHelperServiceDelegate)
        .getTargetGroupForDefaultAction(any(), any());
    doReturn(Optional.of(new TargetGroup().withTargetGroupArn(prodTargetGroupArn).withTargetGroupName("PROD_TGT")))
        .doReturn(
            Optional.of(new TargetGroup().withTargetGroupArn(stageTargetGroupArn).withTargetGroupName("STAGE_TGT")))
        .when(mockAwsElbHelperServiceDelegate)
        .getTargetGroup(any(), anyList(), any(), any());
    doReturn(ElastiGroup.builder().id(stageElastiGroupNewId).name("foo__STAGE__Harness").build())
        .when(mockSpotInstHelperServiceDelegate)
        .createElastiGroup(any(), any(), any());
    SpotInstTaskExecutionResponse response = spotInstSetupTaskHandler.executeTaskInternalForBlueGreen(
        parameters, "SPOTINST_ACCOUNT_ID", "TOKEN", AwsConfig.builder().build(), mockCallback);
    assertThat(response).isNotNull();
    SpotInstTaskResponse spotInstTaskResponse = response.getSpotInstTaskResponse();
    assertThat(spotInstTaskResponse).isNotNull();
    assertThat(spotInstTaskResponse instanceof SpotInstSetupTaskResponse).isTrue();
    SpotInstSetupTaskResponse setupResponse = (SpotInstSetupTaskResponse) spotInstTaskResponse;
    ElastiGroup newElastiGroup = setupResponse.getNewElastiGroup();
    assertThat(newElastiGroup).isNotNull();
    assertThat(newElastiGroup.getName()).isEqualTo("foo__STAGE__Harness");
    assertThat(newElastiGroup.getId()).isEqualTo(stageElastiGroupNewId);
    List<ElastiGroup> groupToBeDownsized = setupResponse.getGroupToBeDownsized();
    assertThat(groupToBeDownsized).isNotNull();
    assertThat(groupToBeDownsized.size()).isEqualTo(1);
    assertThat(groupToBeDownsized.get(0).getId()).isEqualTo(prodElastiGroupOldId);
    assertThat(groupToBeDownsized.get(0).getName()).isEqualTo("foo");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalForCanary() throws Exception {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doNothing().when(mockCallback).saveExecutionLog(anyString(), any(), any());
    doReturn(mockCallback).when(spotInstSetupTaskHandler).getLogCallBack(any(), anyString());
    doReturn(newArrayList(ElastiGroup.builder().name("foo__1").build(),
                 ElastiGroup.builder().name("foo__2").capacity(ElastiGroupCapacity.builder().target(0).build()).build(),
                 ElastiGroup.builder().name("foo__3").capacity(ElastiGroupCapacity.builder().target(1).build()).build(),
                 ElastiGroup.builder().name("foo__4").build(), ElastiGroup.builder().name("foo__5").build(),
                 ElastiGroup.builder().name("foo__6").build()))
        .when(mockSpotInstHelperServiceDelegate)
        .listAllElastiGroups(anyString(), anyString(), anyString());
    doReturn("JSON").when(spotInstSetupTaskHandler).generateFinalJson(any(), anyString());
    doReturn(ElastiGroup.builder().id("newId").name("foo__7").build())
        .when(mockSpotInstHelperServiceDelegate)
        .createElastiGroup(anyString(), anyString(), anyString());
    SpotInstSetupTaskParameters parameters = SpotInstSetupTaskParameters.builder()
                                                 .blueGreen(false)
                                                 .elastiGroupNamePrefix("foo")
                                                 .elastiGroupJson("JSON")
                                                 .image("ami-id")
                                                 .build();
    SpotInstTaskExecutionResponse response = spotInstSetupTaskHandler.executeTaskInternal(parameters,
        SpotInstConfig.builder().spotInstAccountId("SPOTINST_ACCOUNT_ID").spotInstToken(new char[] {'a', 'b'}).build(),
        AwsConfig.builder().build());
    assertThat(response).isNotNull();
    SpotInstTaskResponse spotInstTaskResponse = response.getSpotInstTaskResponse();
    assertThat(spotInstTaskResponse).isNotNull();
    assertThat(spotInstTaskResponse instanceof SpotInstSetupTaskResponse).isTrue();
    SpotInstSetupTaskResponse setupResponse = (SpotInstSetupTaskResponse) spotInstTaskResponse;
    ElastiGroup newElastiGroup = setupResponse.getNewElastiGroup();
    assertThat(newElastiGroup).isNotNull();
    assertThat(newElastiGroup.getName()).isEqualTo("foo__7");
    assertThat(newElastiGroup.getId()).isEqualTo("newId");
    List<ElastiGroup> groupToBeDownsized = setupResponse.getGroupToBeDownsized();
    assertThat(groupToBeDownsized).isNotNull();
    assertThat(groupToBeDownsized.size()).isEqualTo(1);
    assertThat(groupToBeDownsized.get(0).getName()).isEqualTo("foo__6");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRemoveUnsupportedFieldsForCreatingNewGroup() throws Exception {
    Map<String, Object> map = newHashMap();
    map.put("foo", "bar");
    map.put(ELASTI_GROUP_ID, "id");
    map.put(ELASTI_GROUP_CREATED_AT, 10);
    map.put(ELASTI_GROUP_UPDATED_AT, 20);
    spotInstSetupTaskHandler.removeUnsupportedFieldsForCreatingNewGroup(map);
    assertThat(map.size()).isEqualTo(1);
    assertThat(map.containsKey(ELASTI_GROUP_ID)).isFalse();
    assertThat(map.containsKey(ELASTI_GROUP_CREATED_AT)).isFalse();
    assertThat(map.containsKey(ELASTI_GROUP_UPDATED_AT)).isFalse();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testAllInstancesHealthyScaleDown() throws Exception {
    doReturn(singletonList(ElastiGroupInstanceHealth.builder().healthStatus("HEALTHY").build()))
        .doReturn(emptyList())
        .when(mockSpotInstHelperServiceDelegate)
        .listElastiGroupInstancesHealth(anyString(), anyString(), anyString());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    boolean val = spotInstSetupTaskHandler.allInstancesHealthy("TOKEN", "ACCOUNT_ID", "ID", mockCallback, 0);
    assertThat(val).isFalse();
    val = spotInstSetupTaskHandler.allInstancesHealthy("TOKEN", "ACCOUNT_ID", "ID", mockCallback, 0);
    assertThat(val).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testAllInstancesHealthyScaleUp() throws Exception {
    doReturn(emptyList())
        .doReturn(singletonList(ElastiGroupInstanceHealth.builder().healthStatus("HEALTHY").build()))
        .when(mockSpotInstHelperServiceDelegate)
        .listElastiGroupInstancesHealth(anyString(), anyString(), anyString());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    boolean val = spotInstSetupTaskHandler.allInstancesHealthy("TOKEN", "ACCOUNT_ID", "ID", mockCallback, 1);
    assertThat(val).isFalse();
    val = spotInstSetupTaskHandler.allInstancesHealthy("TOKEN", "ACCOUNT_ID", "ID", mockCallback, 1);
    assertThat(val).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetTimeOut() throws Exception {
    assertThat(spotInstSetupTaskHandler.getTimeOut(0)).isEqualTo(defaultSteadyStateTimeout);
    assertThat(spotInstSetupTaskHandler.getTimeOut(10)).isEqualTo(10);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetAllEc2InstancesOfElastiGroup() throws Exception {
    doReturn(emptyList())
        .doReturn(
            singletonList(ElastiGroupInstanceHealth.builder().instanceId("id-1234").healthStatus("HEALTHY").build()))
        .when(mockSpotInstHelperServiceDelegate)
        .listElastiGroupInstancesHealth(anyString(), anyString(), anyString());
    doReturn(singletonList(new Instance().withInstanceId("id-1234")))
        .when(mockAwsEc2HelperServiceDelegate)
        .listEc2Instances(any(), anyList(), anyList(), anyString(), anyBoolean());
    List<Instance> allEc2Instances = spotInstSetupTaskHandler.getAllEc2InstancesOfElastiGroup(
        AwsConfig.builder().build(), "us-east-1", "TOKEN", "ACCOUNT_ID", "ELASTIGROUP_ID");
    assertThat(allEc2Instances.isEmpty()).isTrue();
    allEc2Instances = spotInstSetupTaskHandler.getAllEc2InstancesOfElastiGroup(
        AwsConfig.builder().build(), "us-east-1", "TOKEN", "ACCOUNT_ID", "ELASTIGROUP_ID");
    assertThat(allEc2Instances.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreateAndFinishEmptyExecutionLog() throws Exception {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString(), any(), any());
    doReturn(mockCallback).when(spotInstSetupTaskHandler).getLogCallBack(any(), anyString());
    spotInstSetupTaskHandler.createAndFinishEmptyExecutionLog(
        SpotInstSetupTaskParameters.builder().build(), "foo", "bar");
    verify(mockCallback).saveExecutionLog(eq("bar"), eq(INFO), eq(SUCCESS));
  }
}

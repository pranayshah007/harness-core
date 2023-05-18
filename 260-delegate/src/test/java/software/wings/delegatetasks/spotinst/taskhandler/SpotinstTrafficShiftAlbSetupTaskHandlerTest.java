/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.spotinst.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.SATYAM;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.delegate.task.spotinst.request.SpotinstTrafficShiftAlbSetupParameters;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.rule.Owner;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;

import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class SpotinstTrafficShiftAlbSetupTaskHandlerTest extends WingsBaseTest {
  private final String INIT_JSON = "{\n"
      + "  \"group\": {\n"
      + "    \"name\": \"adwait__11\",\n"
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
      + "              \"name\": \"spotinstTg2\",\n"
      + "              \"arn\": \"arn:aws:elasticloadbalancing:us-east-1:839162981415:targetgroup/spotinstTg2/dd1a6653fbc15631\",\n"
      + "              \"type\": \"TARGET_GROUP\"\n"
      + "            },\n"
      + "            {\n"
      + "              \"name\": \"tg1\",\n"
      + "              \"arn\": \"arn:aws:elasticloadbalancing:us-east-1:839162981415:targetgroup/tg1/10c52a57b2eb67b8\",\n"
      + "              \"type\": \"TARGET_GROUP\"\n"
      + "            },\n"
      + "            {\n"
      + "              \"name\": \"tgLb2\",\n"
      + "              \"arn\": \"arn:aws:elasticloadbalancing:us-east-1:839162981415:targetgroup/tgLb2/7cd09afaec84d28d\",\n"
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
      + "        \"imageId\": \"ami-035b3c7efe6d061d5\",\n"
      + "        \"keyPair\": \"satyam-aws-cross\",\n"
      + "        \"userData\": null,\n"
      + "        \"shutdownScript\": null,\n"
      + "        \"tenancy\": \"default\"\n"
      + "      },\n"
      + "      \"elasticIps\": null\n"
      + "    },\n"
      + "    \"scaling\": {},\n"
      + "    \"scheduling\": {},\n"
      + "    \"thirdPartiesIntegration\": {},\n"
      + "    \"multai\": null\n"
      + "  }\n"
      + "}";

  private final String FINAL_JSON =
      "{\"group\":{\"name\":\"foo__STAGE__Harness\",\"capacity\":{\"minimum\":0,\"maximum\":0,\"target\":0,\"unit\":\"instance\"},\"strategy\":{\"risk\":100.0,\"availabilityVsCost\":\"balanced\",\"drainingTimeout\":120.0,\"lifetimePeriod\":\"days\",\"fallbackToOd\":true,\"scalingStrategy\":{},\"persistence\":{},\"revertToSpot\":{\"performAt\":\"always\"}},\"compute\":{\"instanceTypes\":{\"ondemand\":\"t2.small\",\"spot\":[\"m3.medium\",\"t2.small\",\"t3.small\",\"t3a.small\",\"t2.medium\",\"t3.medium\",\"t3a.medium\",\"a1.medium\"]},\"availabilityZones\":[{\"name\":\"us-east-1a\",\"subnetIds\":[\"subnet-1f703d78\"]},{\"name\":\"us-east-1b\",\"subnetIds\":[\"subnet-01bdf52f\"]},{\"name\":\"us-east-1c\",\"subnetIds\":[\"subnet-33eaf779\"]},{\"name\":\"us-east-1d\",\"subnetIds\":[\"subnet-c1ce809d\"]},{\"name\":\"us-east-1e\",\"subnetIds\":[\"subnet-7427b64a\"]},{\"name\":\"us-east-1f\",\"subnetIds\":[\"subnet-11efe81e\"]}],\"product\":\"Linux/UNIX\",\"launchSpecification\":{\"loadBalancersConfig\":{\"loadBalancers\":[{\"name\":\"stageName\",\"arn\":\"stageArn\",\"type\":\"TARGET_GROUP\"}]},\"securityGroupIds\":[\"sg-d748f48f\"],\"monitoring\":false,\"ebsOptimized\":false,\"imageId\":\"image\",\"keyPair\":\"satyam-aws-cross\",\"userData\":\"userData\",\"tenancy\":\"default\"}},\"scaling\":{},\"scheduling\":{},\"thirdPartiesIntegration\":{}}}";

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecuteTaskInternal() throws Exception {
    SpotinstTrafficShiftAlbSetupTaskHandler handler = spy(SpotinstTrafficShiftAlbSetupTaskHandler.class);
    AwsElbHelperServiceDelegate mockElb = mock(AwsElbHelperServiceDelegate.class);
    on(handler).set("awsElbHelperServiceDelegate", mockElb);
    SpotInstHelperServiceDelegate mockSpot = mock(SpotInstHelperServiceDelegate.class);
    on(handler).set("spotInstHelperServiceDelegate", mockSpot);
    SpotinstTrafficShiftAlbSetupParameters parameters =
        SpotinstTrafficShiftAlbSetupParameters.builder()
            .userData("userData")
            .image("image")
            .elastigroupNamePrefix("foo")
            .elastigroupJson(INIT_JSON)
            .awsRegion("us-east-1")
            .lbDetails(singletonList(LbDetailsForAlbTrafficShift.builder()
                                         .loadBalancerName("lbName")
                                         .loadBalancerArn("lbArn")
                                         .listenerArn("listArn")
                                         .listenerPort("8080")
                                         .useSpecificRule(true)
                                         .ruleArn("ruleArn")
                                         .build()))
            .build();
    SpotInstConfig spotinstConfig = SpotInstConfig.builder()
                                        .spotInstAccountId("spotActId")
                                        .spotInstToken(new char[] {'t', 'o', 'k', 'e', 'n'})
                                        .build();
    AwsConfig awsConfig =
        AwsConfig.builder().accessKey("awsAccessKey".toCharArray()).secretKey(new char[] {'s', 'e'}).build();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doNothing().when(mockCallback).saveExecutionLog(anyString(), any(), any());
    doReturn(mockCallback).when(handler).getLogCallBack(any(), anyString());
    doReturn(LbDetailsForAlbTrafficShift.builder()
                 .loadBalancerName("lbName")
                 .loadBalancerArn("lbArn")
                 .listenerArn("listArn")
                 .listenerPort("8080")
                 .useSpecificRule(true)
                 .ruleArn("ruleArn")
                 .prodTargetGroupArn("prodArn")
                 .prodTargetGroupName("prodName")
                 .stageTargetGroupArn("stageArn")
                 .stageTargetGroupName("stageName")
                 .build())
        .when(mockElb)
        .loadTrafficShiftTargetGroupData(any(), anyString(), anyList(), any(), any());
    doReturn(Optional.of(ElastiGroup.builder().id("oldStageId").name("foo__STAGE__Harness").build()))
        .doReturn(Optional.of(ElastiGroup.builder().id("oldProdId").name("foo").build()))
        .when(mockSpot)
        .getElastiGroupByName(anyString(), anyString(), anyString());
    doNothing().when(mockSpot).deleteElastiGroup(anyString(), anyString(), anyString());
    doReturn(ElastiGroup.builder().id("newStageId").name("foo__STAGE__Harness").build())
        .when(mockSpot)
        .createElastiGroup(anyString(), anyString(), anyString());
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    SpotInstTaskExecutionResponse response = handler.executeTaskInternal(parameters, spotinstConfig, awsConfig);
    assertThat(response).isNotNull();
    verify(mockSpot).createElastiGroup(anyString(), anyString(), captor.capture());
    String value = captor.getValue();
    assertThat(value).isEqualTo(FINAL_JSON);
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.VITALIE;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.streaming.dtos.PutObjectResultResponse;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsConfig;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.task.aws.AwsNgConfigMapper;
import io.harness.connector.task.aws.AwsValidationHandler;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.awsconnector.AwsCFTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsCFTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsIAMRolesResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListASGInstancesTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListASGNamesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListClustersTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListElbListenerRulesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListElbListenersTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListElbTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListLoadBalancersTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListTagsTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListTagsTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListVpcTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsPutAuditBatchToBucketTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsPutAuditBatchToBucketTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsS3BucketResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.delegate.beans.connector.awsconnector.AwsValidateTaskResponse;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;
import software.wings.service.impl.aws.model.AwsVPC;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class AwsDelegateTaskTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private AwsClient awsClient;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private NGErrorHelper ngErrorHelper;
  @Mock private AwsS3DelegateTaskHelper awsS3DelegateTaskHelper;
  @Mock private AwsCFDelegateTaskHelper awsCFDelegateTaskHelper;
  @Mock private AwsIAMDelegateTaskHelper awsIAMDelegateTaskHelper;
  @Mock private AwsListEC2InstancesDelegateTaskHelper awsListEC2InstancesDelegateTaskHelper;
  @Mock private AwsASGDelegateTaskHelper awsASGDelegateTaskHelper;
  @Mock private AwsListVpcDelegateTaskHelper awsListVpcDelegateTaskHelper;
  @Mock private AwsListTagsDelegateTaskHelper awsListTagsDelegateTaskHelper;
  @Mock private AwsListLoadBalancersDelegateTaskHelper awsListLoadBalancersDelegateTaskHelper;
  @Mock private AwsECSDelegateTaskHelper awsECSDelegateTaskHelper;
  @Mock private AwsElasticLoadBalancersDelegateTaskHelper awsElasticLoadBalancersDelegateTaskHelper;

  @InjectMocks private AwsValidationHandler awsValidationHandler;

  @InjectMocks
  private AwsDelegateTask task =
      new AwsDelegateTask(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testRunWithObjectParams() {
    assertThatThrownBy(() -> task.run(new Object[10]))
        .hasMessage("Not implemented")
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldSupportErrorFramework() {
    assertThat(task.isSupportingErrorFramework()).isTrue();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldListS3Buckets() {
    AwsTaskParams awsTaskParams = AwsTaskParams.builder().awsTaskType(AwsTaskType.LIST_S3_BUCKETS).build();
    AwsS3BucketResponse response =
        AwsS3BucketResponse.builder().commandExecutionStatus(SUCCESS).buckets(Collections.emptyMap()).build();

    doReturn(response).when(awsS3DelegateTaskHelper).getS3Buckets(eq(awsTaskParams));

    DelegateResponseData result = task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsS3BucketResponse.class);
    assertThat(result).isEqualTo(response);

    verify(awsS3DelegateTaskHelper, times(1)).getS3Buckets(eq(awsTaskParams));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testShouldListCFParams() {
    AwsCFTaskParamsRequest awsTaskParams =
        AwsCFTaskParamsRequest.builder().awsTaskType(AwsTaskType.CF_LIST_PARAMS).build();
    AwsCFTaskResponse response =
        AwsCFTaskResponse.builder()
            .commandExecutionStatus(SUCCESS)
            .listOfParams(
                Arrays.asList(AwsCFTemplateParamsData.builder().paramType("param-Type").paramKey("param-Key").build()))
            .build();

    doReturn(response).when(awsCFDelegateTaskHelper).getCFParamsList(eq(awsTaskParams));

    AwsCFTaskResponse result = (AwsCFTaskResponse) task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(response);
    assertThat(result).isInstanceOf(AwsCFTaskResponse.class);
    assertThat(result.getListOfParams().get(0).getParamKey()).isEqualTo("param-Key");
    assertThat(result.getListOfParams().get(0).getParamType()).isEqualTo("param-Type");
    verify(awsCFDelegateTaskHelper, times(1)).getCFParamsList(eq(awsTaskParams));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testShouldListIAMRoles() {
    AwsTaskParams awsTaskParams = AwsTaskParams.builder().awsTaskType(AwsTaskType.LIST_IAM_ROLES).build();

    AwsIAMRolesResponse response = AwsIAMRolesResponse.builder()
                                       .commandExecutionStatus(SUCCESS)
                                       .roles(new HashMap<String, String>() {
                                         { put("iamRole-Name", "iamRole-Value"); }
                                       })
                                       .build();

    doReturn(response).when(awsIAMDelegateTaskHelper).getIAMRoleList(eq(awsTaskParams));

    AwsIAMRolesResponse result = (AwsIAMRolesResponse) task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsIAMRolesResponse.class);
    assertThat(result).isEqualTo(response);
    assertThat(result.getRoles().get("iamRole-Name")).isEqualTo("iamRole-Value");
    verify(awsIAMDelegateTaskHelper, times(1)).getIAMRoleList(eq(awsTaskParams));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldHandleValidationTask() {
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder()
                                          .credential(AwsCredentialDTO.builder()
                                                          .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
                                                          .testRegion("us-east-1")
                                                          .build())
                                          .build();
    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .awsConnector(awsConnectorDTO)
                                      .awsTaskType(AwsTaskType.VALIDATE)
                                      .encryptionDetails(Collections.emptyList())
                                      .build();

    AwsConfig awsConfig = AwsConfig.builder().build();
    doReturn(awsConfig).when(awsNgConfigMapper).mapAwsConfigWithDecryption(any(), any());
    on(task).set("awsValidationHandler", awsValidationHandler);

    DelegateResponseData result = task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsValidateTaskResponse.class);
    AwsValidateTaskResponse awsValidateTaskResponse = (AwsValidateTaskResponse) result;
    assertThat(awsValidateTaskResponse.getConnectorValidationResult().getStatus())
        .isEqualTo(ConnectivityStatus.SUCCESS);
    assertThat(awsValidateTaskResponse.getConnectorValidationResult().getTestedAt()).isNotNull();

    verify(awsNgConfigMapper, times(1)).mapAwsConfigWithDecryption(any(), any());
    verify(awsClient, times(1)).validateAwsAccountCredential(eq(awsConfig), eq("us-east-1"));
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testShouldHandleValidationTaskIRSA() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.IRSA).testRegion("us-east-1").build())
            .build();
    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .awsConnector(awsConnectorDTO)
                                      .awsTaskType(AwsTaskType.VALIDATE)
                                      .encryptionDetails(Collections.emptyList())
                                      .build();

    AwsConfig awsConfig = AwsConfig.builder().isIRSA(true).build();

    doReturn(awsConfig).when(awsNgConfigMapper).mapAwsConfigWithDecryption(any(), any());
    on(task).set("awsValidationHandler", awsValidationHandler);

    DelegateResponseData result = task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsValidateTaskResponse.class);
    AwsValidateTaskResponse awsValidateTaskResponse = (AwsValidateTaskResponse) result;
    assertThat(awsValidateTaskResponse.getConnectorValidationResult().getStatus())
        .isEqualTo(ConnectivityStatus.SUCCESS);
    assertThat(awsValidateTaskResponse.getConnectorValidationResult().getTestedAt()).isNotNull();
    assertThat(awsConfig.isIRSA()).isEqualTo(true);

    verify(awsNgConfigMapper, times(1)).mapAwsConfigWithDecryption(any(), any());
    verify(awsClient, times(1)).validateAwsAccountCredential(eq(awsConfig), eq("us-east-1"));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldThrowExceptionIfTaskTypeNoSpecified() {
    AwsTaskParams awsTaskParams = AwsTaskParams.builder().awsTaskType(null).build();

    assertThatThrownBy(() -> task.run(awsTaskParams))
        .hasMessage("Task type not provided")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldListInstances() {
    AwsListEC2InstancesTaskParamsRequest awsTaskParams =
        AwsListEC2InstancesTaskParamsRequest.builder().awsTaskType(AwsTaskType.LIST_EC2_INSTANCES).build();

    AwsListEC2InstancesTaskResponse response = AwsListEC2InstancesTaskResponse.builder()
                                                   .instances(Collections.emptyList())
                                                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                   .build();

    doReturn(response).when(awsListEC2InstancesDelegateTaskHelper).getInstances(eq(awsTaskParams));

    DelegateResponseData result = task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsListEC2InstancesTaskResponse.class);
    assertThat(result).isEqualTo(response);

    verify(awsListEC2InstancesDelegateTaskHelper, times(1)).getInstances(eq(awsTaskParams));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldListASGInstances() {
    AwsListASGInstancesTaskParamsRequest awsTaskParams =
        AwsListASGInstancesTaskParamsRequest.builder().awsTaskType(AwsTaskType.LIST_ASG_INSTANCES).build();

    AwsListEC2InstancesTaskResponse response = AwsListEC2InstancesTaskResponse.builder()
                                                   .instances(Collections.emptyList())
                                                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                   .build();

    doReturn(response).when(awsASGDelegateTaskHelper).getInstances(eq(awsTaskParams));

    DelegateResponseData result = task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsListEC2InstancesTaskResponse.class);
    assertThat(result).isEqualTo(response);

    verify(awsASGDelegateTaskHelper, times(1)).getInstances(eq(awsTaskParams));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldListAsgNames() {
    AwsTaskParams awsTaskParams = AwsTaskParams.builder().awsTaskType(AwsTaskType.LIST_ASG_NAMES).build();

    AwsListASGNamesTaskResponse response = AwsListASGNamesTaskResponse.builder()
                                               .names(Arrays.asList("asg1"))
                                               .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                               .build();

    doReturn(response).when(awsASGDelegateTaskHelper).getASGNames(eq(awsTaskParams));

    DelegateResponseData result = task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsListASGNamesTaskResponse.class);
    assertThat(result).isEqualTo(response);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldListVpcs() {
    AwsTaskParams awsTaskParams = AwsTaskParams.builder().awsTaskType(AwsTaskType.LIST_VPC).build();

    AwsListVpcTaskResponse response = AwsListVpcTaskResponse.builder()
                                          .vpcs(Arrays.asList(AwsVPC.builder().name("vpc").build()))
                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                          .build();

    doReturn(response).when(awsListVpcDelegateTaskHelper).getVpcList(eq(awsTaskParams));

    DelegateResponseData result = task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsListVpcTaskResponse.class);
    assertThat(result).isEqualTo(response);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldListTags() {
    AwsListTagsTaskParamsRequest awsTaskParams =
        AwsListTagsTaskParamsRequest.builder().awsTaskType(AwsTaskType.LIST_TAGS).build();

    AwsListTagsTaskResponse response = AwsListTagsTaskResponse.builder()
                                           .tags(Collections.singletonMap("tag", "value"))
                                           .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                           .build();

    doReturn(response).when(awsListTagsDelegateTaskHelper).getTagList(eq(awsTaskParams));

    DelegateResponseData result = task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsListTagsTaskResponse.class);
    assertThat(result).isEqualTo(response);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldListLoadBalancers() {
    AwsListTagsTaskParamsRequest awsTaskParams =
        AwsListTagsTaskParamsRequest.builder().awsTaskType(AwsTaskType.LIST_LOAD_BALANCERS).build();

    AwsListLoadBalancersTaskResponse response = AwsListLoadBalancersTaskResponse.builder()
                                                    .loadBalancers(Arrays.asList("lb1"))
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    doReturn(response).when(awsListLoadBalancersDelegateTaskHelper).getLoadBalancerList(eq(awsTaskParams));

    DelegateResponseData result = task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsListLoadBalancersTaskResponse.class);
    assertThat(result).isEqualTo(response);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldListClusters() {
    AwsTaskParams awsTaskParams = AwsTaskParams.builder().awsTaskType(AwsTaskType.LIST_ECS_CLUSTERS).build();

    AwsListClustersTaskResponse response = AwsListClustersTaskResponse.builder()
                                               .clusters(Arrays.asList("cluster"))
                                               .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                               .build();

    doReturn(response).when(awsECSDelegateTaskHelper).getEcsClustersList(eq(awsTaskParams));

    DelegateResponseData result = task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsListClustersTaskResponse.class);
    assertThat(result).isEqualTo(response);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldListElasticLoadBalancers() {
    AwsTaskParams awsTaskParams = AwsTaskParams.builder().awsTaskType(AwsTaskType.LIST_ELASTIC_LOAD_BALANCERS).build();

    AwsListElbTaskResponse response = AwsListElbTaskResponse.builder()
                                          .loadBalancerNames(Arrays.asList("elb"))
                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                          .build();

    doReturn(response).when(awsElasticLoadBalancersDelegateTaskHelper).getElbList(eq(awsTaskParams));

    DelegateResponseData result = task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsListElbTaskResponse.class);
    assertThat(result).isEqualTo(response);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldListElbListeners() {
    AwsTaskParams awsTaskParams =
        AwsTaskParams.builder().awsTaskType(AwsTaskType.LIST_ELASTIC_LOAD_BALANCER_LISTENERS).build();

    AwsListElbListenersTaskResponse response = AwsListElbListenersTaskResponse.builder()
                                                   .listenerArnMap(Collections.singletonMap("key", "value"))
                                                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                   .build();

    doReturn(response).when(awsElasticLoadBalancersDelegateTaskHelper).getElbListenerList(eq(awsTaskParams));

    DelegateResponseData result = task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsListElbListenersTaskResponse.class);
    assertThat(result).isEqualTo(response);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldListElbRules() {
    AwsTaskParams awsTaskParams =
        AwsTaskParams.builder().awsTaskType(AwsTaskType.LIST_ELASTIC_LOAD_BALANCER_LISTENER_RULE).build();

    AwsListElbListenerRulesTaskResponse response = AwsListElbListenerRulesTaskResponse.builder()
                                                       .listenerRulesArn(Arrays.asList("rule"))
                                                       .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                       .build();

    doReturn(response).when(awsElasticLoadBalancersDelegateTaskHelper).getElbListenerRulesList(eq(awsTaskParams));

    DelegateResponseData result = task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsListElbListenerRulesTaskResponse.class);
    assertThat(result).isEqualTo(response);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testShouldPutAuditBatchToBucket() {
    AwsPutAuditBatchToBucketTaskParamsRequest awsTaskParams =
        AwsPutAuditBatchToBucketTaskParamsRequest.builder().awsTaskType(AwsTaskType.PUT_AUDIT_BATCH_TO_BUCKET).build();
    AwsPutAuditBatchToBucketTaskResponse response =
        AwsPutAuditBatchToBucketTaskResponse.builder()
            .commandExecutionStatus(SUCCESS)
            .putObjectResultResponse(PutObjectResultResponse.builder().build())
            .build();

    doReturn(response).when(awsS3DelegateTaskHelper).putAuditBatchToBucket(eq(awsTaskParams));

    DelegateResponseData result = task.run(awsTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsPutAuditBatchToBucketTaskResponse.class);
    assertThat(result).isEqualTo(response);

    verify(awsS3DelegateTaskHelper, times(1)).putAuditBatchToBucket(eq(awsTaskParams));
  }
}

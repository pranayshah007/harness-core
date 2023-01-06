/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.elastigroup;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialType;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.beans.elastigroup.ElastigroupSwapRouteResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper;
import io.harness.delegate.task.elastigroup.request.AwsConnectedCloudProvider;
import io.harness.delegate.task.elastigroup.request.AwsLoadBalancerConfig;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupSwapRouteCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupSwapRouteResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.elastigroup.ElastigroupCommandUnitConstants;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ElastigroupSwapRouteCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private ElastigroupCommandTaskNGHelper elastigroupCommandTaskNGHelper;
  @Mock private LogCallback createServiceLogCallback;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock protected SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;

  @InjectMocks private ElastigroupSwapRouteCommandTaskHandler elastigroupSwapRouteCommandTaskHandler;

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalNotElastigroupSetupRequestTest() throws Exception {
    ElastigroupSetupCommandRequest elastigroupSetupCommandRequest = ElastigroupSetupCommandRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    elastigroupSwapRouteCommandTaskHandler.executeTaskInternal(
        elastigroupSetupCommandRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalElastigroupSetupRequestTest() throws Exception {
    int timeout = 10;
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    doReturn(createServiceLogCallback)
        .when(elastigroupCommandTaskNGHelper)
        .getLogCallback(iLogStreamingTaskClient, ElastigroupCommandUnitConstants.SWAP_TARGET_GROUP.toString(), true,
            commandUnitsProgress);
    doReturn(createServiceLogCallback)
        .when(elastigroupCommandTaskNGHelper)
        .getLogCallback(
            iLogStreamingTaskClient, ElastigroupCommandUnitConstants.DOWNSCALE.toString(), true, commandUnitsProgress);
    doReturn(createServiceLogCallback)
        .when(elastigroupCommandTaskNGHelper)
        .getLogCallback(iLogStreamingTaskClient, ElastigroupCommandUnitConstants.DOWNSCALE_STEADY_STATE.toString(),
            true, commandUnitsProgress);

    String awsRegion = "awsRegion";

    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder().build();
    ConnectorConfigDTO connectorConfigDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(connectorConfigDTO).connectorType(ConnectorType.AWS).build();
    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig)
        .when(elastigroupCommandTaskNGHelper)
        .getAwsInternalConfig((AwsConnectorDTO) connectorConfigDTO, awsRegion);

    List<LoadBalancerDetailsForBGDeployment> lbDetailList = Arrays.asList();

    SecretRefData spotAccountId = SecretRefData.builder().build();
    SecretRefData spotInstApiTokenRef = SecretRefData.builder().decryptedValue(new char[] {'a'}).build();
    String decryptedSpotAccountIdRef = "a";
    SpotPermanentTokenConfigSpecDTO spotPermanentTokenConfigSpecDTO = SpotPermanentTokenConfigSpecDTO.builder()
                                                                          .spotAccountId(decryptedSpotAccountIdRef)
                                                                          .spotAccountIdRef(spotAccountId)
                                                                          .apiTokenRef(spotInstApiTokenRef)
                                                                          .build();
    SpotCredentialDTO spotCredentialDTO = SpotCredentialDTO.builder()
                                              .config(spotPermanentTokenConfigSpecDTO)
                                              .spotCredentialType(SpotCredentialType.PERMANENT_TOKEN)
                                              .build();
    SpotConnectorDTO spotConnectorDTO = SpotConnectorDTO.builder().credential(spotCredentialDTO).build();
    SpotInstConfig spotInstConfig = SpotInstConfig.builder().spotConnectorDTO(spotConnectorDTO).build();

    String id = "id";
    String newStageId = "id__STAGE";
    String name = "name";
    String newStageName = name + "__STAGE__Harness";
    ElastiGroup newElastigroup = ElastiGroup.builder().name(newStageName).id(newStageId).build();
    ElastiGroup oldElastiGroup = ElastiGroup.builder().name(name).id(id).build();

    AwsLoadBalancerConfig awsLoadBalancerConfig =
        AwsLoadBalancerConfig.builder().loadBalancerDetails(lbDetailList).build();

    ElastigroupSwapRouteCommandRequest elastigroupSwapRouteCommandRequest =
        ElastigroupSwapRouteCommandRequest.builder()
            .timeoutIntervalInMin(timeout)
            .elastigroupNamePrefix(name)
            .spotInstConfig(spotInstConfig)
            .newElastigroup(newElastigroup)
            .oldElastigroup(oldElastiGroup)
            .connectedCloudProvider(AwsConnectedCloudProvider.builder()
                                        .connectorInfoDTO(connectorInfoDTO)
                                        .encryptionDetails(encryptedDataDetails)
                                        .region(awsRegion)
                                        .build())

            .loadBalancerConfig(awsLoadBalancerConfig)
            .downsizeOldElastigroup("false")
            .build();

    ElastigroupSwapRouteResult elastigroupSwapRouteResult =
        ElastigroupSwapRouteResult.builder()
            .downsizeOldElastiGroup(elastigroupSwapRouteCommandRequest.getDownsizeOldElastigroup())
            .lbDetails(awsLoadBalancerConfig.getLoadBalancerDetails())
            .oldElastiGroupId(id)
            .oldElastiGroupName(newStageName)
            .newElastiGroupId(newStageId)
            .newElastiGroupName(name)
            .ec2InstanceIdsExisting(new ArrayList<>())
            .ec2InstanceIdsAdded(new ArrayList<>())
            .build();

    ElastigroupSwapRouteResponse elastigroupSwapRouteResponse =
        (ElastigroupSwapRouteResponse) elastigroupSwapRouteCommandTaskHandler.executeTaskInternal(
            elastigroupSwapRouteCommandRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(elastigroupSwapRouteResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(elastigroupSwapRouteResponse.getElastigroupSwapRouteResult()).isEqualTo(elastigroupSwapRouteResult);
  }
}

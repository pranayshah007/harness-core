/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.elastigroup;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.spotinst.model.SpotInstConstants.STAGE_ELASTI_GROUP_NAME_SUFFIX;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
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
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.elastigroup.ElastigroupBGTaskHelper;
import io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper;
import io.harness.delegate.task.elastigroup.request.AwsConnectedCloudProvider;
import io.harness.delegate.task.elastigroup.request.AwsLoadBalancerConfig;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupSwapRouteCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupSetupResponse;
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
import io.harness.spotinst.model.ElastiGroupCapacity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ElastigroupBGStageSetupCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final String prodListenerArn = "prodListenerArn";
  private final String prodListenerRuleArn = "prodListenerRuleArn";
  private final String stageListenerArn = "stageListenerArn";
  private final String stageListenerRuleArn = "stageListenerArn";
  private final String loadBalancer = "loadBalancer";

  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private ElastigroupCommandTaskNGHelper elastigroupCommandTaskNGHelper;
  @Mock private LogCallback createServiceLogCallback;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock protected SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;
  @Mock private ElastigroupBGTaskHelper elastigroupDeployTaskHelper;

  @InjectMocks private ElastigroupBGStageSetupCommandTaskHandler elastigroupBGStageSetupCommandTaskHandler;

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalNotElastigroupSetupRequestTest() throws Exception {
    ElastigroupSwapRouteCommandRequest elastigroupSwapRouteCommandRequest =
        ElastigroupSwapRouteCommandRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    elastigroupBGStageSetupCommandTaskHandler.executeTaskInternal(
        elastigroupSwapRouteCommandRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalElastigroupSetupRequestTest() throws Exception {
    int timeout = 10;
    String elastigroupNamePrefix = "prefix";
    String region = "region";
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    doReturn(createServiceLogCallback)
        .when(elastigroupCommandTaskNGHelper)
        .getLogCallback(iLogStreamingTaskClient, ElastigroupCommandUnitConstants.CREATE_ELASTIGROUP.toString(), true,
            commandUnitsProgress);

    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder().build();
    ConnectorConfigDTO connectorConfigDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(connectorConfigDTO).connectorType(ConnectorType.AWS).build();
    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig)
        .when(elastigroupCommandTaskNGHelper)
        .getAwsInternalConfig((AwsConnectorDTO) connectorConfigDTO, region);

    List<LoadBalancerDetailsForBGDeployment> lbDetailList = new ArrayList<>();
    lbDetailList.add(LoadBalancerDetailsForBGDeployment.builder()
                         .loadBalancerName("loadbalancer")
                         .prodListenerPort("80")
                         .stageListenerPort("8080")
                         .prodListenerArn("prodListenerArn")
                         .stageListenerArn("stageListenerArn")
                         .build());
    String finalJson = "finalJson";

    SecretRefData spotAccountId = SecretRefData.builder().build();
    SecretRefData spotInstApiTokenRef = SecretRefData.builder().decryptedValue(new char[] {'a'}).build();
    String decryptedSpotAccountIdRef = "a";
    String decryptedSpotInstApiTokenRef = "a";
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
    ElastiGroupCapacity elastiGroupCapacity = ElastiGroupCapacity.builder().minimum(1).maximum(1).target(1).build();
    ElastiGroup elastigroupOriginalConfig = ElastiGroup.builder().id(id).capacity(elastiGroupCapacity).build();
    AwsLoadBalancerConfig awsLoadBalancerConfig =
        AwsLoadBalancerConfig.builder().loadBalancerDetails(lbDetailList).build();
    ElastigroupSetupCommandRequest elastigroupSetupCommandRequest =
        ElastigroupSetupCommandRequest.builder()
            .timeoutIntervalInMin(timeout)
            .elastigroupNamePrefix(elastigroupNamePrefix)
            .spotInstConfig(spotInstConfig)
            .generatedElastigroupConfig(elastigroupOriginalConfig)
            .connectedCloudProvider(AwsConnectedCloudProvider.builder()
                                        .connectorInfoDTO(connectorInfoDTO)
                                        .encryptionDetails(encryptedDataDetails)
                                        .region(region)
                                        .build())
            .loadBalancerConfig(awsLoadBalancerConfig)
            .build();
    doReturn(lbDetailList)
        .when(elastigroupCommandTaskNGHelper)
        .fetchAllLoadBalancerDetails(elastigroupSetupCommandRequest, awsInternalConfig, createServiceLogCallback);

    String stageElastiGroupName =
        format("%s__%s", elastigroupSetupCommandRequest.getElastigroupNamePrefix(), STAGE_ELASTI_GROUP_NAME_SUFFIX);

    doNothing().when(elastigroupCommandTaskNGHelper).decryptSpotInstConfig(spotInstConfig);

    ElastiGroup stageElastiGroup = ElastiGroup.builder().id(id).capacity(elastiGroupCapacity).build();

    doReturn(finalJson)
        .when(elastigroupCommandTaskNGHelper)
        .generateFinalJson(elastigroupSetupCommandRequest, stageElastiGroupName);
    doReturn(Optional.of(stageElastiGroup))
        .when(spotInstHelperServiceDelegate)
        .getElastiGroupByName(decryptedSpotInstApiTokenRef, decryptedSpotAccountIdRef, stageElastiGroupName);
    doReturn(stageElastiGroup)
        .when(spotInstHelperServiceDelegate)
        .createElastiGroup(decryptedSpotInstApiTokenRef, decryptedSpotAccountIdRef, finalJson);

    ElastiGroup prodElastigroup = ElastiGroup.builder().id(id).capacity(elastiGroupCapacity).build();
    String prodElastiGroupName = elastigroupSetupCommandRequest.getElastigroupNamePrefix();
    doReturn(Optional.of(prodElastigroup))
        .when(spotInstHelperServiceDelegate)
        .getElastiGroupByName(decryptedSpotInstApiTokenRef, decryptedSpotAccountIdRef, prodElastiGroupName);

    doReturn(timeout)
        .when(elastigroupDeployTaskHelper)
        .getTimeOut(elastigroupSetupCommandRequest.getTimeoutIntervalInMin());

    ElastigroupSetupResult elastigroupSetupResult =
        ElastigroupSetupResult.builder()
            .elastigroupNamePrefix(elastigroupSetupCommandRequest.getElastigroupNamePrefix())
            .newElastigroup(stageElastiGroup)
            .elastigroupOriginalConfig(elastigroupSetupCommandRequest.getGeneratedElastigroupConfig())
            .groupToBeDownsized(Collections.singletonList(prodElastigroup))
            .elastigroupNamePrefix(elastigroupSetupCommandRequest.getElastigroupNamePrefix())
            .isBlueGreen(elastigroupSetupCommandRequest.isBlueGreen())
            .useCurrentRunningInstanceCount(elastigroupSetupCommandRequest.isUseCurrentRunningInstanceCount())
            .maxInstanceCount(elastigroupSetupCommandRequest.getMaxInstanceCount())
            .resizeStrategy(elastigroupSetupCommandRequest.getResizeStrategy())
            .loadBalancerDetailsForBGDeployments(lbDetailList)
            .build();

    ElastigroupSetupResponse elastigroupSetupResponse =
        (ElastigroupSetupResponse) elastigroupBGStageSetupCommandTaskHandler.executeTaskInternal(
            elastigroupSetupCommandRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(elastigroupSetupResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(elastigroupSetupResponse.getElastigroupSetupResult()).isEqualTo(elastigroupSetupResult);
  }
}

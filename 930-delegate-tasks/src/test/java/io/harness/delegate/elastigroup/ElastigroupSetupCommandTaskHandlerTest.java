/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.elastigroup;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialType;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper;
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
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;

import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ElastigroupSetupCommandTaskHandlerTest extends CategoryTest {
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
  @Mock private SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;

  @InjectMocks private ElastigroupSetupCommandTaskHandler elastigroupSetupCommandTaskHandler;

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalNotElastigroupSetupRequestTest() throws Exception {
    ElastigroupSwapRouteCommandRequest elastigroupSwapRouteCommandRequest =
        ElastigroupSwapRouteCommandRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    elastigroupSetupCommandTaskHandler.executeTaskInternal(
        elastigroupSwapRouteCommandRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalElastigroupSetupRequestTest() throws Exception {
    int timeout = 10;
    int elastiGroupVersion = 1;
    String elastigroupNamePrefix = "prefix";
    String prefix = format("%s__", elastigroupNamePrefix);
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    doReturn(createServiceLogCallback)
        .when(elastigroupCommandTaskNGHelper)
        .getLogCallback(iLogStreamingTaskClient, ElastigroupCommandUnitConstants.CREATE_ELASTIGROUP.toString(), false,
            commandUnitsProgress);

    SecretRefData spotAccountIdRef = SecretRefData.builder().decryptedValue(new char[] {'a'}).build();
    SecretRefData spotInstApiTokenRef = SecretRefData.builder().decryptedValue(new char[] {'a'}).build();
    String decryptedSpotAccountIdRef = "a";
    String decryptedSpotInstApiTokenRef = "a";
    SpotPermanentTokenConfigSpecDTO spotPermanentTokenConfigSpecDTO = SpotPermanentTokenConfigSpecDTO.builder()
                                                                          .spotAccountIdRef(spotAccountIdRef)
                                                                          .apiTokenRef(spotInstApiTokenRef)
                                                                          .build();
    SpotCredentialDTO spotCredentialDTO = SpotCredentialDTO.builder()
                                              .config(spotPermanentTokenConfigSpecDTO)
                                              .spotCredentialType(SpotCredentialType.PERMANENT_TOKEN)
                                              .build();
    SpotConnectorDTO spotConnectorDTO = SpotConnectorDTO.builder().credential(spotCredentialDTO).build();
    SpotInstConfig spotInstConfig = SpotInstConfig.builder().spotConnectorDTO(spotConnectorDTO).build();
    doNothing().when(elastigroupCommandTaskNGHelper).decryptSpotInstConfig(spotInstConfig);

    doReturn(Arrays.asList())
        .when(spotInstHelperServiceDelegate)
        .listAllElastiGroups(decryptedSpotInstApiTokenRef, decryptedSpotAccountIdRef, prefix);

    String newElastiGroupName = format("%s%d", prefix, elastiGroupVersion);
    String finalJson = "finalJson";
    ElastigroupSetupCommandRequest elastigroupSetupCommandRequest = ElastigroupSetupCommandRequest.builder()
                                                                        .timeoutIntervalInMin(timeout)
                                                                        .elastigroupNamePrefix(elastigroupNamePrefix)
                                                                        .spotInstConfig(spotInstConfig)
                                                                        .build();
    doReturn(finalJson)
        .when(elastigroupCommandTaskNGHelper)
        .generateFinalJson(elastigroupSetupCommandRequest, newElastiGroupName);

    String id = "id";
    ElastiGroup elastiGroup = ElastiGroup.builder().id(id).build();
    doReturn(elastiGroup)
        .when(spotInstHelperServiceDelegate)
        .createElastiGroup(decryptedSpotInstApiTokenRef, decryptedSpotAccountIdRef, finalJson);

    ElastigroupSetupResult elastigroupSetupResult =
        ElastigroupSetupResult.builder()
            .elastigroupNamePrefix(elastigroupSetupCommandRequest.getElastigroupNamePrefix())
            .newElastigroup(elastiGroup)
            .elastigroupOriginalConfig(elastigroupSetupCommandRequest.getGeneratedElastigroupConfig())
            .groupToBeDownsized(Arrays.asList())
            .elastigroupNamePrefix(elastigroupSetupCommandRequest.getElastigroupNamePrefix())
            .isBlueGreen(elastigroupSetupCommandRequest.isBlueGreen())
            .useCurrentRunningInstanceCount(elastigroupSetupCommandRequest.isUseCurrentRunningInstanceCount())
            .maxInstanceCount(elastigroupSetupCommandRequest.getMaxInstanceCount())
            .resizeStrategy(elastigroupSetupCommandRequest.getResizeStrategy())
            .build();

    ElastigroupSetupResponse elastigroupSetupResponse =
        (ElastigroupSetupResponse) elastigroupSetupCommandTaskHandler.executeTaskInternal(
            elastigroupSetupCommandRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(elastigroupSetupResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(elastigroupSetupResponse.getElastigroupSetupResult()).isEqualTo(elastigroupSetupResult);
  }
}

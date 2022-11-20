/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.elastigroup;

import com.google.inject.Inject;
import io.harness.CategoryTest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
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
import io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper;
import io.harness.delegate.task.elastigroup.ElastigroupDeployTaskHelper;
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
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.spotinst.model.SpotInstConstants.COMPUTE;
import static io.harness.spotinst.model.SpotInstConstants.LAUNCH_SPECIFICATION;
import static io.harness.spotinst.model.SpotInstConstants.STAGE_ELASTI_GROUP_NAME_SUFFIX;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

public class ElastigroupCommandTaskNGHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock protected ElbV2Client elbV2Client;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;

  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private LogCallback createServiceLogCallback;
  @Mock protected SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;
  @Mock private ElastigroupDeployTaskHelper elastigroupDeployTaskHelper;

  @InjectMocks private ElastigroupCommandTaskNGHelper elastigroupCommandTaskNGHelper;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getAwsInternalConfigTest() throws Exception {
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().build();
    String region = "region";
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().defaultRegion(region).build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    assertThat(elastigroupCommandTaskNGHelper.getAwsInternalConfig(awsConnectorDTO, region)).isEqualTo(awsInternalConfig);
  }

  private String getAuthToken(String spotInstToken) {
    return format("Bearer %s", spotInstToken);
  }


}

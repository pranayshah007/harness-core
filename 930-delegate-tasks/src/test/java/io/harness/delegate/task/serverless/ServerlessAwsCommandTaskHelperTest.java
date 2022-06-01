/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.serverless.request.ServerlessCommandRequest;
import io.harness.delegate.task.serverless.request.ServerlessDeployRequest;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.serverless.ServerlessCliResponse;
import io.harness.serverless.ServerlessClient;
import io.harness.serverless.model.ServerlessDelegateTaskParams;

import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class ServerlessAwsCommandTaskHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private ServerlessTaskPluginHelper serverlessTaskPluginHelper;
  @Mock private AwsCFHelperServiceDelegate awsCFHelperServiceDelegate;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private AwsInternalConfig awsInternalConfig;
  @Mock private ServerlessCommandRequest serverlessCommandRequest;

  private final long timeout = 10;
  @InjectMocks private ServerlessAwsCommandTaskHelper serverlessAwsCommandTaskHelper;
  private ServerlessAwsLambdaManifestSchema serverlessAwsLambdaManifestSchema =
      ServerlessAwsLambdaManifestSchema.builder().plugins(Arrays.asList("asfd", "asfdasdf")).build();
  private ServerlessDelegateTaskParams serverlessDelegateTaskParams = ServerlessDelegateTaskParams.builder().build();
  @Mock private AwsConnectorDTO awsConnectorDTO;
  @Mock private LogCallback logCallback;
  @Mock private ServerlessClient serverlessClient;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetPreviousVersionTimeStamp() {
    String output = "Warning: Invalid configuration encountered\n"
        + "  at 'provider.tracing': must be object\n"
        + "\n"
        + "Learn more about configuration validation here: http://slss.io/configuration-validation\n"
        +

        "2022-03-11 08:48:51 UTC\n"
        + "Timestamp: 1646988531400\n"
        + "Files:\n"
        + "  - aws-node-http-api-project-1.zip\n"
        + "  - compiled-cloudformation-template.json\n"
        + "  - custom-resources.zip\n"
        + "  - serverless-state.json\n"
        + "2022-03-11 08:58:16 UTC\n"
        + "Timestamp: 1646989096845\n"
        + "Files:\n"
        + "  - aws-node-http-api-project-1.zip\n"
        + "  - compiled-cloudformation-template.json\n"
        + "  - custom-resources.zip\n"
        + "  - serverless-state.json";

    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig = ServerlessAwsLambdaInfraConfig.builder()
                                                                        .region("us-east-2")
                                                                        .stage("dev")
                                                                        .awsConnectorDTO(awsConnectorDTO)
                                                                        .build();
    String serverlessManifest = "service: ABC";
    ServerlessDeployRequest serverlessDeployRequest = ServerlessDeployRequest.builder()
                                                          .manifestContent(serverlessManifest)
                                                          .serverlessInfraConfig(serverlessAwsLambdaInfraConfig)
                                                          .build();

    List<String> timeStamps = serverlessAwsCommandTaskHelper.getDeployListTimeStamps(output);
    assertThat(timeStamps).contains("1646988531400", "1646989096845");
    doReturn("abc1646988531400xyz").when(awsCFHelperServiceDelegate).getStackBody(any(), any(), any());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());

    assertThat(serverlessAwsCommandTaskHelper.getPreviousVersionTimeStamp(timeStamps, null, serverlessDeployRequest))
        .isEqualTo(Optional.of("1646988531400"));
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void installPluginsSuccessTest() throws Exception {
    ServerlessCliResponse serverlessCliResponse =
        ServerlessCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
    ServerlessAwsLambdaManifestConfig serverlessAwsLambdaManifestConfig =
        ServerlessAwsLambdaManifestConfig.builder().configOverridePath("c").build();
    doReturn(serverlessCliResponse)
        .when(serverlessTaskPluginHelper)
        .installServerlessPlugin(serverlessDelegateTaskParams, serverlessClient, "asfd", logCallback, 10, "c");
    doReturn(serverlessCliResponse)
        .when(serverlessTaskPluginHelper)
        .installServerlessPlugin(serverlessDelegateTaskParams, serverlessClient, "asfdasdf", logCallback, 10, "c");
    serverlessAwsCommandTaskHelper.installPlugins(serverlessAwsLambdaManifestSchema, serverlessDelegateTaskParams,
        logCallback, serverlessClient, 10, serverlessAwsLambdaManifestConfig);
    verify(serverlessTaskPluginHelper)
        .installServerlessPlugin(serverlessDelegateTaskParams, serverlessClient, "asfd", logCallback, 10, "c");
    verify(serverlessTaskPluginHelper)
        .installServerlessPlugin(serverlessDelegateTaskParams, serverlessClient, "asfdasdf", logCallback, 10, "c");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void cloudFormationStackExistsTest() throws Exception {
    String serverlessManifest = "service: ABC";
    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig = ServerlessAwsLambdaInfraConfig.builder()
                                                                        .region("us-east-2")
                                                                        .stage("dev")
                                                                        .awsConnectorDTO(awsConnectorDTO)
                                                                        .build();
    when(serverlessCommandRequest.getServerlessInfraConfig()).thenReturn(serverlessAwsLambdaInfraConfig);
    String cloudFormationStackName = "ABC"
        + "-"
        + "dev";
    when(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO)).thenReturn(awsInternalConfig);
    serverlessAwsCommandTaskHelper.cloudFormationStackExists(logCallback, serverlessCommandRequest, serverlessManifest);
    verify(awsNgConfigMapper, times(1)).createAwsInternalConfig(awsConnectorDTO);
    verify(awsCFHelperServiceDelegate, times(1))
        .stackExists(awsInternalConfig, serverlessAwsLambdaInfraConfig.getRegion(), cloudFormationStackName);
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaCloudFormationSchema;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunction;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.serverless.request.ServerlessDeployRequest;
import io.harness.exception.HintException;
import io.harness.filesystem.FileIo;
import io.harness.delegate.task.serverless.request.ServerlessCommandRequest;
import io.harness.delegate.task.serverless.request.ServerlessPrepareRollbackDataRequest;
import io.harness.data.structure.UUIDGenerator;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.serverless.*;
import io.harness.serverless.model.ServerlessAwsLambdaConfig;
import io.harness.serverless.model.ServerlessDelegateTaskParams;

import io.harness.version.VersionInfoManager;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.Spy;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;
import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;

import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class ServerlessAwsCommandTaskHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ServerlessTaskPluginHelper serverlessTaskPluginHelper;
  @Mock private AwsCFHelperServiceDelegate awsCFHelperServiceDelegate;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private AwsInternalConfig awsInternalConfig;
  @Mock private AwsApiHelperService awsApiHelperService;
  @Mock private ServerlessCommandRequest serverlessCommandRequest;

  private final long timeout = 10;
  @InjectMocks private ServerlessAwsCommandTaskHelper serverlessAwsCommandTaskHelper;
  private ServerlessAwsLambdaManifestSchema serverlessAwsLambdaManifestSchema =
      ServerlessAwsLambdaManifestSchema.builder().plugins(Arrays.asList("asfd", "asfdasdf")).build();
  private ServerlessDelegateTaskParams serverlessDelegateTaskParams = ServerlessDelegateTaskParams.builder().build();
  @Mock private ListObjectsV2Result listObjectsV2Result;
  @Mock private AwsConnectorDTO awsConnectorDTO;
  @Mock private LogCallback logCallback;
  @Mock private ServerlessClient serverlessClient;
  @Mock private ServerlessUtils serverlessUtils;
  @Mock private ConfigCredentialCommand command;
  @Mock private ProcessExecutor processExecutor;
  @Mock private PluginCommand pluginCommand;

  private static String CLOUDFORMATION_UPDATE_FILE = "cloudformation-template-update-stack.json";
  private static String cloudDir = "cloudDir";

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetPreviousVersionTimeStamp() throws IOException {
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
    ServerlessPrepareRollbackDataRequest serverlessPrepareRollbackDataRequest =
        ServerlessPrepareRollbackDataRequest.builder()
            .manifestContent(serverlessManifest)
            .serverlessInfraConfig(serverlessAwsLambdaInfraConfig)
            .build();

    List<String> timeStamps = serverlessAwsCommandTaskHelper.getDeployListTimeStamps(output);
    S3ObjectSummary obj1 = new S3ObjectSummary();
    String key1 = "serverless/ABC/dev/1655701920467-2022-06-20T05:12:00.467Z/compiled-cloudformation-template.json";
    String key2 = "serverless/ABC/dev/1646988531400-2022-06-20T05:12:00.467Z/compiled-cloudformation-template.json";
    obj1.setKey(key1);
    obj1.setLastModified(new Date(1234556));

    S3ObjectSummary obj2 = new S3ObjectSummary();
    obj2.setKey(key2);
    obj2.setLastModified(new Date(1234516));
    List<S3ObjectSummary> objectSummaryList = Arrays.asList(obj1, obj2);
    assertThat(timeStamps).contains("1646988531400", "1646989096845");
    doReturn("stackBody").when(awsCFHelperServiceDelegate).getStackBody(any(), any(), any());
    doReturn("abc1646988531400xyz")
        .when(awsCFHelperServiceDelegate)
        .getPhysicalIdBasedOnLogicalId(any(), any(), any(), any());
    doReturn(false).when(listObjectsV2Result).isTruncated();
    doReturn(objectSummaryList).when(listObjectsV2Result).getObjectSummaries();
    InputStream inputStream1 = IOUtils.toInputStream("stackBody1", "UTF-8");
    InputStream inputStream2 = IOUtils.toInputStream("stackBody", "UTF-8");

    S3Object s3Object1 = new S3Object();
    s3Object1.setKey(key1);
    s3Object1.setObjectContent(inputStream1);

    S3Object s3Object2 = new S3Object();
    s3Object2.setKey(key2);
    s3Object2.setObjectContent(inputStream2);

    doReturn(s3Object1).when(awsApiHelperService).getObjectFromS3(any(), any(), any(), eq(key1));
    doReturn(s3Object2).when(awsApiHelperService).getObjectFromS3(any(), any(), any(), eq(key2));
    doReturn(listObjectsV2Result).when(awsApiHelperService).listObjectsInS3(any(), any(), any());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());

    assertThat(
        serverlessAwsCommandTaskHelper.getLastDeployedTimestamp(null, timeStamps, serverlessPrepareRollbackDataRequest))
        .isEqualTo(Optional.of("1646988531400"));
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void configCredentialTest() throws IOException, InterruptedException, TimeoutException {
    ServerlessClient serverlessClient = ServerlessClient.client("");
    ConfigCredentialCommand command = Mockito.spy(new ConfigCredentialCommand(serverlessClient));
    doReturn("serverless deploy hi").when(command).command();
//    doReturn(command).when(serverlessClient).configCredential();
    ServerlessAwsLambdaConfig serverlessAwsLambdaConfig =
            ServerlessAwsLambdaConfig.builder()
            .provider("aws").accessKey("accessKey").secretKey("secretKey")
            .build();
    ServerlessCliResponse serverlessCliResponse =
            ServerlessCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).output("output").build();
    String workingDir =
            Paths.get("workingDir").normalize().toAbsolutePath().toString();

    FileIo.createDirectoryIfDoesNotExist(workingDir);
    ServerlessDelegateTaskParams serverlessDelegateTaskParams =
                        ServerlessDelegateTaskParams.builder().workingDirectory(workingDir).build();
    //ProcessResult processResult = new ProcessResult(3,new ProcessOutput(new byte[]{0x05}));
    ServerlessCliResponse serverlessCliResponse1 = serverlessAwsCommandTaskHelper
            .configCredential(serverlessClient, serverlessAwsLambdaConfig, serverlessDelegateTaskParams,
            logCallback, true, 30000);
    System.out.println(serverlessCliResponse1);
    FileIo.deleteDirectoryAndItsContentIfExists(workingDir);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetServerlessDeploymentBucketName() throws IOException {
    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig = ServerlessAwsLambdaInfraConfig.builder()
                                                                        .region("us-east-2")
                                                                        .stage("dev")
                                                                        .awsConnectorDTO(awsConnectorDTO)
                                                                        .build();
    String serverlessManifest = "service: ABC";
    ServerlessPrepareRollbackDataRequest serverlessPrepareRollbackDataRequest =
        ServerlessPrepareRollbackDataRequest.builder()
            .manifestContent(serverlessManifest)
            .serverlessInfraConfig(serverlessAwsLambdaInfraConfig)
            .build();
    doReturn("stackBody").when(awsCFHelperServiceDelegate).getStackBody(any(), any(), any());
    doReturn("abc1646988531400xyz")
        .when(awsCFHelperServiceDelegate)
        .getPhysicalIdBasedOnLogicalId(any(), any(), any(), any());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    assertThat(serverlessAwsCommandTaskHelper.getServerlessDeploymentBucketName(logCallback,
                   serverlessPrepareRollbackDataRequest, serverlessPrepareRollbackDataRequest.getManifestContent()))
        .isEqualTo(Optional.of("abc1646988531400xyz"));
  }


  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetServiceName() throws IOException {
    String serverlessManifest = "service: ABC";
    assertThat(serverlessAwsCommandTaskHelper.getServiceName(serverlessManifest)).isEqualTo("ABC");
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

  @Test(expected = HintException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void installPluginsFailureTest() throws Exception {
    ServerlessCliResponse serverlessCliResponse =
            ServerlessCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();
    ServerlessAwsLambdaManifestConfig serverlessAwsLambdaManifestConfig =
            ServerlessAwsLambdaManifestConfig.builder().configOverridePath("c").build();
    doReturn(serverlessCliResponse)
            .when(serverlessTaskPluginHelper)
            .installServerlessPlugin(serverlessDelegateTaskParams, serverlessClient, "asfd", logCallback, 10, "c");
    doReturn(serverlessCliResponse)
            .when(serverlessTaskPluginHelper)
            .installServerlessPlugin(serverlessDelegateTaskParams, serverlessClient, "asfdasdf", logCallback, 10, "c");
    doReturn(pluginCommand).when(serverlessClient).plugin();
    doReturn("serverless command").when(pluginCommand).command();
    serverlessAwsCommandTaskHelper.installPlugins(serverlessAwsLambdaManifestSchema, serverlessDelegateTaskParams,
            logCallback, serverlessClient, 10, serverlessAwsLambdaManifestConfig);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void fetchFunctionOutputFromCloudFormationTemplateEmptyTest() throws Exception {
    String cloudDir = Paths.get("cloudDir",UUIDGenerator.convertBase64UuidToCanonicalForm(UUIDGenerator.generateUuid()))
            .normalize().toAbsolutePath().toString();
    FileIo.createDirectoryIfDoesNotExist(cloudDir);
    FileIo.writeUtf8StringToFile(cloudDir+"/"+CLOUDFORMATION_UPDATE_FILE,"");
//    System.out.println("hi vamsi");
//    System.out.println(Paths.get(cloudDir, CLOUDFORMATION_UPDATE_FILE).toString());
    serverlessAwsCommandTaskHelper.fetchFunctionOutputFromCloudFormationTemplate(cloudDir);
    FileIo.deleteDirectoryAndItsContentIfExists(cloudDir);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void fetchFunctionOutputFromCloudFormationTemplateTest() throws Exception {
    String cloudFormationContent = "AWSTemplateFormatVersion: '2010-09-09'\n" +
            "Description: VPC function.\n" +
            "Resources:\n" +
            "  Function:\n" +
            "    Type: AWS::Lambda::Function\n" +
            "    Properties:\n" +
            "      FunctionName: fun\n" +
            "      Handler: index.handler\n" +
            "      Role: arn:aws:iam::123456789012:role/lambda-role\n" +
            "      Runtime: nodejs12.x\n" +
            "      MemorySize: 1024\n" +
            "      Timeout: 5\n";
    String cloudDir = Paths.get("cloudDir",UUIDGenerator.convertBase64UuidToCanonicalForm(UUIDGenerator.generateUuid()))
            .normalize().toAbsolutePath().toString();
    FileIo.createDirectoryIfDoesNotExist(cloudDir);
    FileIo.writeUtf8StringToFile(cloudDir+"/"+CLOUDFORMATION_UPDATE_FILE, cloudFormationContent);
    ServerlessAwsLambdaFunction serverlessAwsLambdaFunction =
            serverlessAwsCommandTaskHelper.fetchFunctionOutputFromCloudFormationTemplate(cloudDir).get(0);
    ServerlessAwsLambdaFunction serverlessAwsLambdaFunctionTest =
            ServerlessAwsLambdaFunction.builder()
                    .functionName("fun").handler("index.handler").memorySize("1024")
                    .runTime("nodejs12.x").timeout(5)
                    .build();
    assertThat(serverlessAwsLambdaFunction).isEqualTo(serverlessAwsLambdaFunctionTest);
    FileIo.deleteDirectoryAndItsContentIfExists(cloudDir);
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

  @Test(expected = HintException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void parseServerlessManifestExceptionTest() throws Exception {
    String serverlessManifest = "ABC";
    serverlessAwsCommandTaskHelper.parseServerlessManifest(logCallback, serverlessManifest);
  }
}

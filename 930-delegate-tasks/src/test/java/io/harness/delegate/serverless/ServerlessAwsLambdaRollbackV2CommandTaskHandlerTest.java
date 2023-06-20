/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AWSCloudformationClient;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.serverless.Parameter;
import io.harness.delegate.beans.serverless.RollbackConfiguration;
import io.harness.delegate.beans.serverless.RollbackTrigger;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunction;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.beans.serverless.StackDetails;
import io.harness.delegate.beans.serverless.Tag;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.serverless.ServerlessAwsCommandTaskHelper;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaInfraConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaManifestConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaRollbackV2Config;
import io.harness.delegate.task.serverless.ServerlessInfraConfig;
import io.harness.delegate.task.serverless.ServerlessInfraConfigHelper;
import io.harness.delegate.task.serverless.ServerlessManifestConfig;
import io.harness.delegate.task.serverless.ServerlessTaskHelperBase;
import io.harness.delegate.task.serverless.request.ServerlessRollbackV2Request;
import io.harness.delegate.task.serverless.response.ServerlessRollbackResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.serverless.ServerlessCliResponse;
import io.harness.serverless.ServerlessCommandUnitConstants;
import io.harness.serverless.model.ServerlessAwsLambdaConfig;
import io.harness.serverless.model.ServerlessDelegateTaskParams;

import com.amazonaws.services.cloudformation.model.UpdateStackResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class ServerlessAwsLambdaRollbackV2CommandTaskHandlerTest extends CategoryTest {
  @Mock private ServerlessTaskHelperBase serverlessTaskHelperBase;
  @Mock private ServerlessInfraConfigHelper serverlessInfraConfigHelper;
  @Mock private ServerlessAwsCommandTaskHelper serverlessAwsCommandTaskHelper;
  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;

  @Mock protected AWSCloudformationClient awsHelperService;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @InjectMocks
  @Spy
  private ServerlessAwsLambdaRollbackV2CommandTaskHandler serverlessAwsLambdaRollbackCommandTaskHandler;

  private Integer timeout = 10;
  private String accountId = "account";
  private String output = "output";
  private String previousVersionTimeStamp = "123";
  private String service = "serv";
  private String region = "regi";
  private String stage = "stag";
  private String manifestContent = "manifest";
  private String workingDir = "/asdf/";
  private ServerlessInfraConfig serverlessInfraConfig =
      ServerlessAwsLambdaInfraConfig.builder().region(region).stage(stage).build();
  private ServerlessManifestConfig serverlessManifestConfig = ServerlessAwsLambdaManifestConfig.builder().build();
  private ServerlessDelegateTaskParams serverlessDelegateTaskParams =
      ServerlessDelegateTaskParams.builder().serverlessClientPath("/qwer").workingDirectory(workingDir).build();
  private CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
  private ServerlessAwsLambdaManifestSchema serverlessAwsLambdaManifestSchema =
      ServerlessAwsLambdaManifestSchema.builder().service(service).build();
  private ServerlessCliResponse intiServerlessCliResponse =
      ServerlessCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).output(output).build();
  private ServerlessCliResponse rollbackServerlessCliResponse =
      ServerlessCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
  private List<ServerlessAwsLambdaFunction> serverlessAwsLambdaFunctionsList = new ArrayList<>();
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenFirstDeploymentIsFalse() throws Exception {
    StackDetails stackDetails =
        StackDetails.builder()
            .stackName("serverless-prod31")
            .templateBody(
                "{\"AWSTemplateFormatVersion\":\"2010-09-09\",\"Description\":\"The AWS CloudFormation template for this Serverless application\",\"Resources\":{\"ServerlessDeploymentBucket\":{\"Type\":\"AWS::S3::Bucket\",\"Properties\":{\"BucketEncryption\":{\"ServerSideEncryptionConfiguration\":[{\"ServerSideEncryptionByDefault\":{\"SSEAlgorithm\":\"AES256\"}}]}}},\"ServerlessDeploymentBucketPolicy\":{\"Type\":\"AWS::S3::BucketPolicy\",\"Properties\":{\"Bucket\":{\"Ref\":\"ServerlessDeploymentBucket\"},\"PolicyDocument\":{\"Statement\":[{\"Action\":\"s3:*\",\"Effect\":\"Deny\",\"Principal\":\"*\",\"Resource\":[{\"Fn::Join\":[\"\",[\"arn:\",{\"Ref\":\"AWS::Partition\"},\":s3:::\",{\"Ref\":\"ServerlessDeploymentBucket\"},\"/*\"]]},{\"Fn::Join\":[\"\",[\"arn:\",{\"Ref\":\"AWS::Partition\"},\":s3:::\",{\"Ref\":\"ServerlessDeploymentBucket\"}]]}],\"Condition\":{\"Bool\":{\"aws:SecureTransport\":false}}}]}}},\"HelloWorldLogGroup\":{\"Type\":\"AWS::Logs::LogGroup\",\"Properties\":{\"LogGroupName\":\"/aws/lambda/serverless-prod31-helloWorld\"}},\"IamRoleLambdaExecution\":{\"Type\":\"AWS::IAM::Role\",\"Properties\":{\"AssumeRolePolicyDocument\":{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"Service\":[\"lambda.amazonaws.com\"]},\"Action\":[\"sts:AssumeRole\"]}]},\"Policies\":[{\"PolicyName\":{\"Fn::Join\":[\"-\",[\"serverless\",\"prod31\",\"lambda\"]]},\"PolicyDocument\":{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":[\"logs:CreateLogStream\",\"logs:CreateLogGroup\",\"logs:TagResource\"],\"Resource\":[{\"Fn::Sub\":\"arn:${AWS::Partition}:logs:${AWS::Region}:${AWS::AccountId}:log-group:/aws/lambda/serverless-prod31*:*\"}]},{\"Effect\":\"Allow\",\"Action\":[\"logs:PutLogEvents\"],\"Resource\":[{\"Fn::Sub\":\"arn:${AWS::Partition}:logs:${AWS::Region}:${AWS::AccountId}:log-group:/aws/lambda/serverless-prod31*:*:*\"}]}]}}],\"Path\":\"/\",\"RoleName\":{\"Fn::Join\":[\"-\",[\"serverless\",\"prod31\",{\"Ref\":\"AWS::Region\"},\"lambdaRole\"]]}}},\"HelloWorldLambdaFunction\":{\"Type\":\"AWS::Lambda::Function\",\"Properties\":{\"Code\":{\"S3Bucket\":{\"Ref\":\"ServerlessDeploymentBucket\"},\"S3Key\":\"serverless/serverless/prod31/1686818405902-2023-06-15T08:40:05.902Z/serverless.zip\"},\"Handler\":\"handler.helloWorld\",\"Runtime\":\"nodejs18.x\",\"FunctionName\":\"serverless-prod31-helloWorld\",\"MemorySize\":1024,\"Timeout\":6,\"Role\":{\"Fn::GetAtt\":[\"IamRoleLambdaExecution\",\"Arn\"]}},\"DependsOn\":[\"HelloWorldLogGroup\"]},\"HelloWorldLambdaVersionItWi95D1hGkoch3xt3azVB0k6rD99JtDTU1J6VAqk\":{\"Type\":\"AWS::Lambda::Version\",\"DeletionPolicy\":\"Retain\",\"Properties\":{\"FunctionName\":{\"Ref\":\"HelloWorldLambdaFunction\"},\"CodeSha256\":\"Ii3S/xP7QJCeme0mhHBa0OlFxUI1XRcqOHNCxW+cUQk=\"}},\"ApiGatewayRestApi\":{\"Type\":\"AWS::ApiGateway::RestApi\",\"Properties\":{\"Name\":\"prod31-serverless\",\"EndpointConfiguration\":{\"Types\":[\"EDGE\"]},\"Policy\":\"\"}},\"ApiGatewayResourceHelloDashworld\":{\"Type\":\"AWS::ApiGateway::Resource\",\"Properties\":{\"ParentId\":{\"Fn::GetAtt\":[\"ApiGatewayRestApi\",\"RootResourceId\"]},\"PathPart\":\"hello-world\",\"RestApiId\":{\"Ref\":\"ApiGatewayRestApi\"}}},\"ApiGatewayMethodHelloDashworldOptions\":{\"Type\":\"AWS::ApiGateway::Method\",\"Properties\":{\"AuthorizationType\":\"NONE\",\"HttpMethod\":\"OPTIONS\",\"MethodResponses\":[{\"StatusCode\":\"200\",\"ResponseParameters\":{\"method.response.header.Access-Control-Allow-Origin\":true,\"method.response.header.Access-Control-Allow-Headers\":true,\"method.response.header.Access-Control-Allow-Methods\":true},\"ResponseModels\":{}}],\"RequestParameters\":{},\"Integration\":{\"Type\":\"MOCK\",\"RequestTemplates\":{\"application/json\":\"{statusCode:200}\"},\"ContentHandling\":\"CONVERT_TO_TEXT\",\"IntegrationResponses\":[{\"StatusCode\":\"200\",\"ResponseParameters\":{\"method.response.header.Access-Control-Allow-Origin\":\"'*'\",\"method.response.header.Access-Control-Allow-Headers\":\"'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token,X-Amz-User-Agent,X-Amzn-Trace-Id'\",\"method.response.header.Access-Control-Allow-Methods\":\"'OPTIONS,GET'\"},\"ResponseTemplates\":{\"application/json\":\"\"}}]},\"ResourceId\":{\"Ref\":\"ApiGatewayResourceHelloDashworld\"},\"RestApiId\":{\"Ref\":\"ApiGatewayRestApi\"}}},\"ApiGatewayMethodHelloDashworldGet\":{\"Type\":\"AWS::ApiGateway::Method\",\"Properties\":{\"HttpMethod\":\"GET\",\"RequestParameters\":{},\"ResourceId\":{\"Ref\":\"ApiGatewayResourceHelloDashworld\"},\"RestApiId\":{\"Ref\":\"ApiGatewayRestApi\"},\"ApiKeyRequired\":false,\"AuthorizationType\":\"NONE\",\"Integration\":{\"IntegrationHttpMethod\":\"POST\",\"Type\":\"AWS_PROXY\",\"Uri\":{\"Fn::Join\":[\"\",[\"arn:\",{\"Ref\":\"AWS::Partition\"},\":apigateway:\",{\"Ref\":\"AWS::Region\"},\":lambda:path/2015-03-31/functions/\",{\"Fn::GetAtt\":[\"HelloWorldLambdaFunction\",\"Arn\"]},\"/invocations\"]]}},\"MethodResponses\":[]},\"DependsOn\":[\"HelloWorldLambdaPermissionApiGateway\"]},\"ApiGatewayDeployment1686818390533\":{\"Type\":\"AWS::ApiGateway::Deployment\",\"Properties\":{\"RestApiId\":{\"Ref\":\"ApiGatewayRestApi\"},\"StageName\":\"prod31\"},\"DependsOn\":[\"ApiGatewayMethodHelloDashworldOptions\",\"ApiGatewayMethodHelloDashworldGet\"]},\"HelloWorldLambdaPermissionApiGateway\":{\"Type\":\"AWS::Lambda::Permission\",\"Properties\":{\"FunctionName\":{\"Fn::GetAtt\":[\"HelloWorldLambdaFunction\",\"Arn\"]},\"Action\":\"lambda:InvokeFunction\",\"Principal\":\"apigateway.amazonaws.com\",\"SourceArn\":{\"Fn::Join\":[\"\",[\"arn:\",{\"Ref\":\"AWS::Partition\"},\":execute-api:\",{\"Ref\":\"AWS::Region\"},\":\",{\"Ref\":\"AWS::AccountId\"},\":\",{\"Ref\":\"ApiGatewayRestApi\"},\"/*/*\"]]}}}},\"Outputs\":{\"ServerlessDeploymentBucketName\":{\"Value\":{\"Ref\":\"ServerlessDeploymentBucket\"},\"Export\":{\"Name\":\"sls-serverless-prod31-ServerlessDeploymentBucketName\"}},\"HelloWorldLambdaFunctionQualifiedArn\":{\"Description\":\"Current Lambda function version\",\"Value\":{\"Ref\":\"HelloWorldLambdaVersionItWi95D1hGkoch3xt3azVB0k6rD99JtDTU1J6VAqk\"},\"Export\":{\"Name\":\"sls-serverless-prod31-HelloWorldLambdaFunctionQualifiedArn\"}},\"ServiceEndpoint\":{\"Description\":\"URL of the service endpoint\",\"Value\":{\"Fn::Join\":[\"\",[\"https://\",{\"Ref\":\"ApiGatewayRestApi\"},\".execute-api.\",{\"Ref\":\"AWS::Region\"},\".\",{\"Ref\":\"AWS::URLSuffix\"},\"/prod31\"]]},\"Export\":{\"Name\":\"sls-serverless-prod31-ServiceEndpoint\"}}}}")
            .capabilities(Arrays.asList("CAPABILITY_IAM", "CAPABILITY_NAMED_IAM"))
            .tags(Arrays.asList(Tag.builder().key("STAGE").value("prod31").build()))
            .parameters(Arrays.asList(Parameter.builder().parameterKey("key").parameterValue("value").build()))
            .tags(Arrays.asList(Tag.builder().key("key").value("value").build()))
            .rollbackConfiguration(
                RollbackConfiguration.builder()
                    .rollbackTriggers(Arrays.asList(RollbackTrigger.builder().type("type").arn("arn").build()))
                    .build())
            .build();
    ServerlessAwsLambdaRollbackV2Config serverlessRollbackConfig =
        ServerlessAwsLambdaRollbackV2Config.builder().stackDetails(stackDetails).isFirstDeployment(false).build();

    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig = mock(ServerlessAwsLambdaInfraConfig.class);
    AwsConnectorDTO awsConnectorDTO = mock(AwsConnectorDTO.class);
    doReturn(region).when(serverlessAwsLambdaInfraConfig).getRegion();
    doReturn(stage).when(serverlessAwsLambdaInfraConfig).getStage();
    doReturn(awsConnectorDTO).when(serverlessAwsLambdaInfraConfig).getAwsConnectorDTO();

    ServerlessRollbackV2Request serverlessCommandRequest = ServerlessRollbackV2Request.builder()
                                                               .timeoutIntervalInMin(timeout)
                                                               .serverlessInfraConfig(serverlessAwsLambdaInfraConfig)
                                                               .serverlessRollbackConfig(serverlessRollbackConfig)
                                                               .accountId(accountId)
                                                               .build();

    LogCallback rollbackLogCallback = mock(LogCallback.class);

    doReturn(rollbackLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    ServerlessAwsLambdaConfig serverlessAwsLambdaConfig = ServerlessAwsLambdaConfig.builder().build();

    doReturn(serverlessAwsLambdaConfig)
        .when(serverlessInfraConfigHelper)
        .createServerlessConfig(serverlessAwsLambdaInfraConfig);

    AwsInternalConfig awsInternalConfig = mock(AwsInternalConfig.class);
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(any());

    UpdateStackResult updateStackResult = mock(UpdateStackResult.class);
    doReturn("id").when(updateStackResult).getStackId();
    doReturn(updateStackResult).when(awsHelperService).updateStack(any(), any(), any());

    ServerlessRollbackResponse serverlessRollbackResponse =
        (ServerlessRollbackResponse) serverlessAwsLambdaRollbackCommandTaskHandler.executeTaskInternal(
            serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(serverlessRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(awsHelperService, times(1)).updateStack(any(), any(), any());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenFirstDeploymentIsTrue() throws Exception {
    ServerlessAwsLambdaRollbackV2Config serverlessRollbackConfig =
        ServerlessAwsLambdaRollbackV2Config.builder().isFirstDeployment(true).build();
    ServerlessRollbackV2Request serverlessCommandRequest = ServerlessRollbackV2Request.builder()
                                                               .timeoutIntervalInMin(timeout)
                                                               .serverlessInfraConfig(serverlessInfraConfig)
                                                               .serverlessRollbackConfig(serverlessRollbackConfig)
                                                               .accountId(accountId)
                                                               .build();

    LogCallback rollbackLogCallback = mock(LogCallback.class);

    doReturn(rollbackLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    ServerlessAwsLambdaConfig serverlessAwsLambdaConfig = ServerlessAwsLambdaConfig.builder().build();

    doReturn(serverlessAwsLambdaConfig).when(serverlessInfraConfigHelper).createServerlessConfig(serverlessInfraConfig);

    doNothing().when(serverlessAwsLambdaRollbackCommandTaskHandler).updateStackDetails(any(), any());

    ServerlessRollbackResponse serverlessRollbackResponse =
        (ServerlessRollbackResponse) serverlessAwsLambdaRollbackCommandTaskHandler.executeTaskInternal(
            serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(serverlessRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test(expected = Exception.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenRollbackConfigIsEmpty() throws Exception {
    ServerlessAwsLambdaRollbackV2Config serverlessRollbackConfig =
        ServerlessAwsLambdaRollbackV2Config.builder().isFirstDeployment(false).build();
    ServerlessRollbackV2Request serverlessCommandRequest = ServerlessRollbackV2Request.builder()
                                                               .timeoutIntervalInMin(timeout)
                                                               .serverlessInfraConfig(serverlessInfraConfig)
                                                               .serverlessRollbackConfig(null)
                                                               .accountId(accountId)
                                                               .build();

    LogCallback rollbackLogCallback = mock(LogCallback.class);

    doReturn(rollbackLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    ServerlessAwsLambdaConfig serverlessAwsLambdaConfig = ServerlessAwsLambdaConfig.builder().build();

    doReturn(serverlessAwsLambdaConfig).when(serverlessInfraConfigHelper).createServerlessConfig(serverlessInfraConfig);

    doNothing().when(serverlessAwsLambdaRollbackCommandTaskHandler).updateStackDetails(any(), any());

    ServerlessRollbackResponse serverlessRollbackResponse =
        (ServerlessRollbackResponse) serverlessAwsLambdaRollbackCommandTaskHandler.executeTaskInternal(
            serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
  }
}
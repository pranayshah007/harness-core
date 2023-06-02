/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.SATYAM;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCESS_KEY;
import static software.wings.utils.WingsTestConstants.ARTIFACT_FILE_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_PATH;
import static software.wings.utils.WingsTestConstants.BUCKET_NAME;
import static software.wings.utils.WingsTestConstants.BUILD_NO;
import static software.wings.utils.WingsTestConstants.S3_URL;
import static software.wings.utils.WingsTestConstants.SECRET_KEY;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.service.impl.aws.model.AwsLambdaExecuteFunctionRequest;
import software.wings.service.impl.aws.model.AwsLambdaExecuteWfRequest;
import software.wings.service.impl.aws.model.AwsLambdaFunctionParams;
import software.wings.service.impl.aws.model.AwsLambdaVpcConfig;
import software.wings.service.impl.aws.model.request.AwsLambdaDetailsRequest;
import software.wings.service.impl.aws.model.response.AwsLambdaDetailsResponse;
import software.wings.service.impl.delegate.AwsEcrApiHelperServiceDelegateBase;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.utils.WingsTestConstants;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.AliasConfiguration;
import com.amazonaws.services.lambda.model.CreateAliasResult;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.GetFunctionConfigurationResult;
import com.amazonaws.services.lambda.model.GetFunctionRequest;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.ListAliasesRequest;
import com.amazonaws.services.lambda.model.ListAliasesResult;
import com.amazonaws.services.lambda.model.PublishVersionResult;
import com.amazonaws.services.lambda.model.ResourceNotFoundException;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeResult;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationResult;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AwsLambdaHelperServiceDelegateImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private EncryptionService mockEncryptionService;
  @Mock private AwsCallTracker mockTracker;
  @Mock private DelegateFileManager mockDelegateFileManager;
  @Mock private AwsEcrApiHelperServiceDelegateBase awsEcrApiHelperServiceDelegateBase;
  private TimeLimiter timeLimiter = mock(TimeLimiter.class);
  @Spy @InjectMocks private AwsLambdaHelperServiceDelegateImpl awsLambdaHelperServiceDelegate;
  private SettingAttribute awsSetting =
      aSettingAttribute()
          .withUuid(SETTING_ID)
          .withValue(AwsConfig.builder().secretKey(SECRET_KEY).accessKey(ACCESS_KEY.toCharArray()).build())
          .build();

  private static final String functionName = "function-name";

  @Before
  public void before() throws Exception {
    FieldUtils.writeField(awsLambdaHelperServiceDelegate, "timeLimiter", timeLimiter, true);
  }

  private GetFunctionConfigurationResult getFuncResultWithState(String state) {
    return new GetFunctionConfigurationResult().withState(state);
  }

  private GetFunctionConfigurationResult getFuncResultWithLastStatus(String status) {
    return new GetFunctionConfigurationResult().withLastUpdateStatus(status);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testWaitForFunctionToCreate() throws Exception {
    AWSLambdaClient mockClient = mock(AWSLambdaClient.class);
    ExecutionLogCallback mockCallBack = mock(ExecutionLogCallback.class);
    FieldUtils.writeField(
        awsLambdaHelperServiceDelegate, "timeLimiter", SimpleTimeLimiter.create(Executors.newCachedThreadPool()), true);
    FieldUtils.writeField(awsLambdaHelperServiceDelegate, "WAIT_SLEEP_IN_SECONDS", 0, true);
    assertThatThrownBy(
        () -> awsLambdaHelperServiceDelegate.waitForFunctionToCreate(mockClient, functionName, mockCallBack))
        .isInstanceOf(InvalidRequestException.class);
    doReturn(getFuncResultWithState(awsLambdaHelperServiceDelegate.PENDING_FUNCTION_STATE))
        .doReturn(getFuncResultWithState(awsLambdaHelperServiceDelegate.PENDING_FUNCTION_STATE))
        .doReturn(getFuncResultWithState(awsLambdaHelperServiceDelegate.ACTIVE_FUNCTION_STATE))
        .when(mockClient)
        .getFunctionConfiguration(any());

    awsLambdaHelperServiceDelegate.waitForFunctionToCreate(mockClient, functionName, mockCallBack);
    verify(mockClient, times(4)).getFunctionConfiguration(any());

    doReturn(getFuncResultWithState(awsLambdaHelperServiceDelegate.PENDING_FUNCTION_STATE))
        .doReturn(getFuncResultWithState(awsLambdaHelperServiceDelegate.PENDING_FUNCTION_STATE))
        .doReturn(getFuncResultWithState(awsLambdaHelperServiceDelegate.FAILED_FUNCTION_STATE))
        .when(mockClient)
        .getFunctionConfiguration(any());
    assertThatThrownBy(
        () -> awsLambdaHelperServiceDelegate.waitForFunctionToCreate(mockClient, functionName, mockCallBack))
        .isInstanceOf(InvalidRequestException.class);
    verify(mockClient, times(7)).getFunctionConfiguration(any());

    doReturn(getFuncResultWithState(awsLambdaHelperServiceDelegate.ACTIVE_FUNCTION_STATE))
        .when(mockClient)
        .getFunctionConfiguration(any());
    awsLambdaHelperServiceDelegate.waitForFunctionToCreate(mockClient, functionName, mockCallBack);
    verify(mockClient, times(8)).getFunctionConfiguration(any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testWaitForFunctionToUpdate() throws Exception {
    AWSLambdaClient mockClient = mock(AWSLambdaClient.class);
    ExecutionLogCallback mockCallBack = mock(ExecutionLogCallback.class);
    FieldUtils.writeField(
        awsLambdaHelperServiceDelegate, "timeLimiter", SimpleTimeLimiter.create(Executors.newCachedThreadPool()), true);
    FieldUtils.writeField(awsLambdaHelperServiceDelegate, "WAIT_SLEEP_IN_SECONDS", 0, true);
    assertThatThrownBy(
        () -> awsLambdaHelperServiceDelegate.waitForFunctionToUpdate(mockClient, functionName, mockCallBack))
        .isInstanceOf(InvalidRequestException.class);
    doReturn(getFuncResultWithLastStatus(awsLambdaHelperServiceDelegate.PENDING_LAST_UPDATE_STATUS))
        .doReturn(getFuncResultWithLastStatus(awsLambdaHelperServiceDelegate.PENDING_LAST_UPDATE_STATUS))
        .doReturn(getFuncResultWithLastStatus(awsLambdaHelperServiceDelegate.ACTIVE_LAST_UPDATE_STATUS))
        .when(mockClient)
        .getFunctionConfiguration(any());

    awsLambdaHelperServiceDelegate.waitForFunctionToUpdate(mockClient, functionName, mockCallBack);
    verify(mockClient, times(4)).getFunctionConfiguration(any());

    doReturn(getFuncResultWithLastStatus(awsLambdaHelperServiceDelegate.PENDING_LAST_UPDATE_STATUS))
        .doReturn(getFuncResultWithLastStatus(awsLambdaHelperServiceDelegate.PENDING_LAST_UPDATE_STATUS))
        .doReturn(getFuncResultWithLastStatus(awsLambdaHelperServiceDelegate.FAILED_LAST_UPDATE_STATUS))
        .when(mockClient)
        .getFunctionConfiguration(any());
    assertThatThrownBy(
        () -> awsLambdaHelperServiceDelegate.waitForFunctionToUpdate(mockClient, functionName, mockCallBack))
        .isInstanceOf(InvalidRequestException.class);
    verify(mockClient, times(7)).getFunctionConfiguration(any());

    doReturn(getFuncResultWithLastStatus(awsLambdaHelperServiceDelegate.ACTIVE_LAST_UPDATE_STATUS))
        .when(mockClient)
        .getFunctionConfiguration(any());
    awsLambdaHelperServiceDelegate.waitForFunctionToUpdate(mockClient, functionName, mockCallBack);
    verify(mockClient, times(8)).getFunctionConfiguration(any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecuteFunction() {
    AWSLambdaClient mockClient = mock(AWSLambdaClient.class);
    doReturn(mockClient).when(awsLambdaHelperServiceDelegate).getAmazonLambdaClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new InvokeResult().withStatusCode(1).withFunctionError("err").withLogResult("log").withPayload(
                 StandardCharsets.UTF_8.encode("payload")))
        .when(mockClient)
        .invoke(any());
    doNothing().when(mockTracker).trackLambdaCall(any());
    awsLambdaHelperServiceDelegate.executeFunction(AwsLambdaExecuteFunctionRequest.builder()
                                                       .awsConfig(AwsConfig.builder().build())
                                                       .encryptionDetails(emptyList())
                                                       .region("us-east-1")
                                                       .functionName("fxName")
                                                       .payload("payload")
                                                       .qualifier("qual")
                                                       .build());
    verify(mockClient).invoke(any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecuteWf_FxDoesNotExistS3() {
    AWSLambdaClient mockClient = mock(AWSLambdaClient.class);
    doReturn(mockClient).when(awsLambdaHelperServiceDelegate).getAmazonLambdaClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    ExecutionLogCallback mockCallBack = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallBack).saveExecutionLog(any(), any());
    doReturn(null).when(mockClient).getFunction(any());
    doReturn(new CreateFunctionResult()
                 .withCodeSha256("sha256")
                 .withVersion("version")
                 .withFunctionArn("arn")
                 .withFunctionName("name"))
        .when(mockClient)
        .createFunction(any());
    doReturn(new CreateAliasResult().withName("aliasName").withAliasArn("aliasArn"))
        .when(mockClient)
        .createAlias(any());
    ArtifactStreamAttributes artifactStreamAttributesForS3 =
        ArtifactStreamAttributes.builder()
            .artifactStreamType(ArtifactStreamType.AMAZON_S3.name())
            .metadataOnly(true)
            .metadata(mockMetadata(ArtifactStreamType.AMAZON_S3))
            .serverSetting(awsSetting.toDTO())
            .artifactServerEncryptedDataDetails(Collections.emptyList())
            .build();

    AwsLambdaExecuteWfRequest request =
        AwsLambdaExecuteWfRequest.builder()
            .awsConfig(AwsConfig.builder().build())
            .encryptionDetails(emptyList())
            .region("use-east-1")
            .roleArn("arn")
            .artifactStreamAttributes(artifactStreamAttributesForS3)
            .evaluatedAliases(singletonList("eval"))
            .serviceVariables(ImmutableMap.of("k1", "v1"))
            .lambdaVpcConfig(AwsLambdaVpcConfig.builder().build())
            .functionParams(singletonList(AwsLambdaFunctionParams.builder().functionName("fxName").build()))
            .build();

    doNothing().when(mockTracker).trackLambdaCall(any());
    awsLambdaHelperServiceDelegate.executeWf(request, mockCallBack);
    verify(mockClient).createFunction(any());
    verify(mockClient).createAlias(any());
  }

  @SneakyThrows
  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testExecuteWf_FxDoesNotExistNonS3() {
    AWSLambdaClient mockClient = mock(AWSLambdaClient.class);
    doReturn(mockClient).when(awsLambdaHelperServiceDelegate).getAmazonLambdaClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    ExecutionLogCallback mockCallBack = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallBack).saveExecutionLog(any(), any());
    doReturn(null).when(mockClient).getFunction(any());
    doReturn(new CreateFunctionResult()
                 .withCodeSha256("sha256")
                 .withVersion("version")
                 .withFunctionArn("arn")
                 .withFunctionName("name"))
        .when(mockClient)
        .createFunction(any());
    doReturn(new CreateAliasResult().withName("aliasName").withAliasArn("aliasArn"))
        .when(mockClient)
        .createAlias(any());
    ArtifactStreamAttributes artifactStreamAttributesForS3 =
        ArtifactStreamAttributes.builder()
            .artifactStreamType(ArtifactStreamType.ARTIFACTORY.name())
            .metadataOnly(true)
            .metadata(mockMetadata(ArtifactStreamType.ARTIFACTORY))
            .serverSetting(awsSetting.toDTO())
            .artifactServerEncryptedDataDetails(Collections.emptyList())
            .build();

    AwsLambdaExecuteWfRequest request =
        AwsLambdaExecuteWfRequest.builder()
            .awsConfig(AwsConfig.builder().build())
            .encryptionDetails(emptyList())
            .region("use-east-1")
            .roleArn("arn")
            .artifactStreamAttributes(artifactStreamAttributesForS3)
            .evaluatedAliases(singletonList("eval"))
            .serviceVariables(ImmutableMap.of("k1", "v1"))
            .lambdaVpcConfig(AwsLambdaVpcConfig.builder().build())
            .functionParams(singletonList(AwsLambdaFunctionParams.builder().functionName("fxName").build()))
            .build();
    InputStream mockInputStream = new ByteArrayInputStream(new byte[0]);
    doReturn(mockInputStream)
        .when(mockDelegateFileManager)
        .downloadArtifactAtRuntime(any(), any(), any(), any(), any(), any());
    doNothing().when(mockTracker).trackLambdaCall(any());
    awsLambdaHelperServiceDelegate.executeWf(request, mockCallBack);
    verify(mockClient).createFunction(any());
    verify(mockClient).createAlias(any());
  }

  @SneakyThrows
  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecuteWf_FxExistsS3() {
    AWSLambdaClient mockClient = mock(AWSLambdaClient.class);
    doReturn(mockClient).when(awsLambdaHelperServiceDelegate).getAmazonLambdaClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    ExecutionLogCallback mockCallBack = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallBack).saveExecutionLog(any(), any());
    doReturn(new GetFunctionResult().withConfiguration(new FunctionConfiguration().withCodeSha256("sha256_old")))
        .when(mockClient)
        .getFunction(any());
    doReturn(new UpdateFunctionCodeResult().withCodeSha256("sha256_new").withFunctionArn("new-arn"))
        .when(mockClient)
        .updateFunctionCode(any());
    doReturn(new UpdateFunctionConfigurationResult().withFunctionName("name").withCodeSha256("sha256_new"))
        .when(mockClient)
        .updateFunctionConfiguration(any());
    doReturn(new PublishVersionResult().withVersion("version").withFunctionArn("arn"))
        .when(mockClient)
        .publishVersion(any());
    doReturn(new ListAliasesResult().withAliases(emptyList())).when(mockClient).listAliases(any());
    doReturn(new CreateAliasResult().withName("aliasName").withAliasArn("aliasArn"))
        .when(mockClient)
        .createAlias(any());
    doReturn(new GetFunctionConfigurationResult().withState("Active")).when(mockClient).getFunctionConfiguration(any());

    ArtifactStreamAttributes artifactStreamAttributesForS3 =
        ArtifactStreamAttributes.builder()
            .artifactStreamType(ArtifactStreamType.AMAZON_S3.name())
            .metadataOnly(true)
            .metadata(mockMetadata(ArtifactStreamType.AMAZON_S3))
            .serverSetting(awsSetting.toDTO())
            .artifactServerEncryptedDataDetails(Collections.emptyList())
            .build();

    AwsLambdaExecuteWfRequest request = AwsLambdaExecuteWfRequest.builder()
                                            .awsConfig(AwsConfig.builder().build())
                                            .encryptionDetails(emptyList())
                                            .region("use-east-1")
                                            .roleArn("arn")
                                            .artifactStreamAttributes(artifactStreamAttributesForS3)
                                            .evaluatedAliases(singletonList("eval"))
                                            .serviceVariables(ImmutableMap.of("k1", "v1"))
                                            .lambdaVpcConfig(AwsLambdaVpcConfig.builder().build())
                                            .functionParams(singletonList(AwsLambdaFunctionParams.builder().build()))
                                            .build();

    doNothing().when(mockTracker).trackLambdaCall(any());
    awsLambdaHelperServiceDelegate.executeWf(request, mockCallBack);
    verify(mockClient, times(2)).updateFunctionCode(any());
    verify(mockClient).updateFunctionConfiguration(any());
    verify(mockClient).publishVersion(any());
    verify(mockClient).createAlias(any());
  }

  @SneakyThrows
  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testExecuteWf_FxExistsNonS3() {
    AWSLambdaClient mockClient = mock(AWSLambdaClient.class);
    doReturn(mockClient).when(awsLambdaHelperServiceDelegate).getAmazonLambdaClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    ExecutionLogCallback mockCallBack = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallBack).saveExecutionLog(any(), any());
    doReturn(new GetFunctionResult().withConfiguration(new FunctionConfiguration().withCodeSha256("sha256_old")))
        .when(mockClient)
        .getFunction(any());
    doReturn(new UpdateFunctionCodeResult().withCodeSha256("sha256_new").withFunctionArn("new-arn"))
        .when(mockClient)
        .updateFunctionCode(any());
    doReturn(new UpdateFunctionConfigurationResult().withFunctionName("name").withCodeSha256("sha256_new"))
        .when(mockClient)
        .updateFunctionConfiguration(any());
    doReturn(new PublishVersionResult().withVersion("version").withFunctionArn("arn"))
        .when(mockClient)
        .publishVersion(any());
    doReturn(new ListAliasesResult().withAliases(emptyList())).when(mockClient).listAliases(any());
    doReturn(new CreateAliasResult().withName("aliasName").withAliasArn("aliasArn"))
        .when(mockClient)
        .createAlias(any());
    doReturn(new GetFunctionConfigurationResult().withState("Active")).when(mockClient).getFunctionConfiguration(any());

    ArtifactStreamAttributes artifactStreamAttributesForS3 =
        ArtifactStreamAttributes.builder()
            .artifactStreamType(ArtifactStreamType.ARTIFACTORY.name())
            .metadataOnly(true)
            .metadata(mockMetadata(ArtifactStreamType.ARTIFACTORY))
            .serverSetting(awsSetting.toDTO())
            .artifactServerEncryptedDataDetails(Collections.emptyList())
            .build();

    AwsLambdaExecuteWfRequest request = AwsLambdaExecuteWfRequest.builder()
                                            .awsConfig(AwsConfig.builder().build())
                                            .encryptionDetails(emptyList())
                                            .region("use-east-1")
                                            .roleArn("arn")
                                            .artifactStreamAttributes(artifactStreamAttributesForS3)
                                            .evaluatedAliases(singletonList("eval"))
                                            .serviceVariables(ImmutableMap.of("k1", "v1"))
                                            .lambdaVpcConfig(AwsLambdaVpcConfig.builder().build())
                                            .functionParams(singletonList(AwsLambdaFunctionParams.builder().build()))
                                            .build();

    InputStream mockInputStream = new ByteArrayInputStream(new byte[0]);
    doReturn(mockInputStream)
        .when(mockDelegateFileManager)
        .downloadArtifactAtRuntime(any(), any(), any(), any(), any(), any());
    doNothing().when(mockTracker).trackLambdaCall(any());
    awsLambdaHelperServiceDelegate.executeWf(request, mockCallBack);
    verify(mockClient, times(2)).updateFunctionCode(any());
    verify(mockClient).updateFunctionConfiguration(any());
    verify(mockClient).publishVersion(any());
    verify(mockClient).createAlias(any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testTagExistingFunction() {
    AWSLambdaClient mockClient = mock(AWSLambdaClient.class);
    String functionArn = "functionArn";
    Map<String, String> initialTags = ImmutableMap.of("k1", "v1");
    GetFunctionResult getFunctionResult =
        new GetFunctionResult()
            .withConfiguration(new FunctionConfiguration().withFunctionArn(functionArn))
            .withTags(initialTags);
    Map<String, String> finalTags = ImmutableMap.of("k2", "v2");
    ExecutionLogCallback mockCallBack = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallBack).saveExecutionLog(any());
    doNothing().when(mockTracker).trackLambdaCall(any());
    awsLambdaHelperServiceDelegate.tagExistingFunction(getFunctionResult, finalTags, mockCallBack, mockClient);
    verify(mockClient).untagResource(any());
    verify(mockClient).tagResource(any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetAlternateNormalizedFunctionName() {
    assertThat(awsLambdaHelperServiceDelegate.getAlternateNormalizedFunctionName("foo_bar")).isEqualTo("foo-bar");
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getFunctionDetails() {
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    AWSLambdaClient mockClient = mock(AWSLambdaClient.class);
    final GetFunctionResult getFunctionResult =
        new GetFunctionResult()
            .withConfiguration(new FunctionConfiguration().withLastModified("2019-10-09T11:49:13.585-0700"))
            .withTags(ImmutableMap.of("key", "value"));

    doReturn(getFunctionResult).when(mockClient).getFunction(any(GetFunctionRequest.class));
    final ListAliasesResult listAliasesResult =
        new ListAliasesResult().withAliases(new AliasConfiguration().withName("alias"));
    doReturn(listAliasesResult).when(mockClient).listAliases(any(ListAliasesRequest.class));

    doReturn(mockClient).when(awsLambdaHelperServiceDelegate).getAmazonLambdaClient(any(), any());
    final AwsLambdaDetailsRequest awsLambdaDetailsRequest = AwsLambdaDetailsRequest.builder().loadAliases(true).build();
    doNothing().when(mockTracker).trackLambdaCall(any());
    final AwsLambdaDetailsResponse functionDetails =
        awsLambdaHelperServiceDelegate.getFunctionDetails(awsLambdaDetailsRequest, false);
    assertThat(functionDetails.getLambdaDetails().getLastModified()).isNotNull();
    assertThat(functionDetails.getLambdaDetails().getTags()).containsKeys("key");
    assertThat(functionDetails.getLambdaDetails().getAliases()).contains("alias");
    verify(mockClient, times(1)).listAliases(any(ListAliasesRequest.class));
    verify(mockClient, times(1)).getFunction(any(GetFunctionRequest.class));
  }

  @Test()
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getFunctionDetails_error() {
    AWSLambdaClient mockClient = mock(AWSLambdaClient.class);
    doReturn(mockClient).when(awsLambdaHelperServiceDelegate).getAmazonLambdaClient(any(), any());
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).handleAmazonServiceException(any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doThrow(new AmazonServiceException("service exception"))
        .when(mockClient)
        .getFunction(any(GetFunctionRequest.class));

    final AwsLambdaDetailsRequest awsLambdaDetailsRequest = AwsLambdaDetailsRequest.builder().loadAliases(true).build();
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> awsLambdaHelperServiceDelegate.getFunctionDetails(awsLambdaDetailsRequest, false));

    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).handleAmazonClientException(any());
    doThrow(new AmazonClientException("client exception")).when(mockClient).getFunction(any(GetFunctionRequest.class));
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> awsLambdaHelperServiceDelegate.getFunctionDetails(awsLambdaDetailsRequest, false));

    doThrow(new ResourceNotFoundException("resource not found"))
        .when(mockClient)
        .getFunction(any(GetFunctionRequest.class));
    doNothing().when(mockTracker).trackLambdaCall(any());
    final AwsLambdaDetailsResponse functionDetails =
        awsLambdaHelperServiceDelegate.getFunctionDetails(awsLambdaDetailsRequest, false);
    assertThat(functionDetails.getLambdaDetails()).isNull();
  }

  private Map<String, String> mockMetadata(ArtifactStreamType artifactStreamType) {
    Map<String, String> map = new HashMap<>();
    switch (artifactStreamType) {
      case AMAZON_S3:
        map.put(ArtifactMetadataKeys.bucketName, BUCKET_NAME);
        map.put(ArtifactMetadataKeys.artifactFileName, ARTIFACT_FILE_NAME);
        map.put(ArtifactMetadataKeys.artifactPath, ARTIFACT_PATH);
        map.put(ArtifactMetadataKeys.buildNo, BUILD_NO);
        map.put(ArtifactMetadataKeys.artifactFileSize, String.valueOf(WingsTestConstants.ARTIFACT_FILE_SIZE));
        map.put(ArtifactMetadataKeys.key, ACCESS_KEY);
        map.put(ArtifactMetadataKeys.url, S3_URL);
        break;
      case ARTIFACTORY:
        map.put(ArtifactMetadataKeys.artifactFileName, ARTIFACT_FILE_NAME);
        map.put(ArtifactMetadataKeys.artifactPath, ARTIFACT_PATH);
        map.put(ArtifactMetadataKeys.buildNo, BUILD_NO);
        map.put(ArtifactMetadataKeys.artifactFileSize, String.valueOf(WingsTestConstants.ARTIFACT_FILE_SIZE));
        break;
      case AZURE_ARTIFACTS:
        map.put(ArtifactMetadataKeys.version, BUILD_NO);
        map.put(ArtifactMetadataKeys.buildNo, BUILD_NO);
        break;
      case JENKINS:
        map.put(ArtifactMetadataKeys.buildNo, BUILD_NO);
        break;
      case BAMBOO:
        map.put(ArtifactMetadataKeys.buildNo, "11");
        break;
      case NEXUS:
        map.put(ArtifactMetadataKeys.buildNo, "7.0");
        break;
      default:
        break;
    }
    return map;
  }
}

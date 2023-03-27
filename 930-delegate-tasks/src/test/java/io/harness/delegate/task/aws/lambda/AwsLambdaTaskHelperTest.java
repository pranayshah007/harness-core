/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.lambda.AwsLambdaClient;
import io.harness.category.element.UnitTests;
import io.harness.delegate.exception.AwsLambdaException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Charsets;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.AliasConfiguration;
import software.amazon.awssdk.services.lambda.model.CreateAliasResponse;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.FunctionCodeLocation;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.ListAliasesResponse;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionResponse;
import software.amazon.awssdk.services.lambda.model.PublishVersionResponse;
import software.amazon.awssdk.services.lambda.model.UpdateAliasResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AwsLambdaTaskHelper.class)
@OwnedBy(CDP)
public class AwsLambdaTaskHelperTest extends CategoryTest {
  public static final String FUNCTION_NAME = "functionName";
  @InjectMocks AwsLambdaTaskHelper awsLambdaTaskHelper;
  @Mock AwsLambdaClient awsLambdaClient;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;

  @Mock private AwsLambdaTaskHelperBase awsLambdaTaskHelperBase;
  @Mock private LogCallback logCallback;

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testListVersionsByFunction() {
    ListVersionsByFunctionResponse listVersionsByFunctionResponse =
        ListVersionsByFunctionResponse.builder().versions(FunctionConfiguration.builder().build()).build();

    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doReturn(listVersionsByFunctionResponse).when(awsLambdaClient).listVersionsByFunction(any(), any());

    ListVersionsByFunctionResponse functionResponse =
        awsLambdaTaskHelper.listVersionsByFunction(FUNCTION_NAME, AwsLambdaFunctionsInfraConfig.builder().build());
    assertThat(functionResponse).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGetActiveVersions() {
    ListVersionsByFunctionResponse listVersionsByFunctionResponse =
        ListVersionsByFunctionResponse.builder().versions(FunctionConfiguration.builder().build()).build();

    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doReturn(listVersionsByFunctionResponse).when(awsLambdaClient).listVersionsByFunction(any(), any());
    List<String> activeVersions =
        awsLambdaTaskHelper.getActiveVersions(FUNCTION_NAME, AwsLambdaFunctionsInfraConfig.builder().build());
    assertThat(activeVersions).isNotEmpty();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGetAwsLambdaFunctionWithActiveVersions() throws AwsLambdaException {
    ListVersionsByFunctionResponse listVersionsByFunctionResponse =
        ListVersionsByFunctionResponse.builder()
            .versions(FunctionConfiguration.builder().version("v1").build())
            .build();
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doReturn(Optional.of(getFunctionResponse())).when(awsLambdaClient).getFunction(any(), any());
    doReturn(listVersionsByFunctionResponse).when(awsLambdaClient).listVersionsByFunction(any(), any());
    AwsLambdaFunctionWithActiveVersions awsLambdaFunctionWithActiveVersions =
        awsLambdaTaskHelper.getAwsLambdaFunctionWithActiveVersions(
            AwsLambdaFunctionsInfraConfig.builder().build(), FUNCTION_NAME);
    assertThat(awsLambdaFunctionWithActiveVersions).isNotNull();
    assertThat(awsLambdaFunctionWithActiveVersions.getVersions().get(0).equals("v1"));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testListAliases() {
    ListAliasesResponse listAliasesResponse =
        ListAliasesResponse.builder()
            .aliases(Arrays.asList(AliasConfiguration.builder().functionVersion("v1").name("alias-for-v1").build(),
                AliasConfiguration.builder().functionVersion("v2").name("alias-for-v2").build()))
            .build();
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doReturn(listAliasesResponse).when(awsLambdaClient).listAliases(any(), any());
    ListAliasesResponse listAliases =
        awsLambdaTaskHelper.listAliases(FUNCTION_NAME, AwsLambdaFunctionsInfraConfig.builder().build());
    assertThat(listAliases).isNotNull();
    assertThat(listAliases.aliases().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGetPublishVersionResponse() {
    PublishVersionResponse publishVersionResponse = PublishVersionResponse.builder().version("v1").build();

    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doReturn(publishVersionResponse).when(awsLambdaClient).publishVersion(any(), any());

    PublishVersionResponse response = awsLambdaTaskHelper.getPublishVersionResponse(
        logCallback, AwsLambdaFunctionsInfraConfig.builder().build(), FUNCTION_NAME, "asdg234");
    assertThat(response).isNotNull();
    assertThat(response.version()).isEqualTo("v1");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGetUpdateFunctionCodeRequestForECR() throws IOException {
    FunctionCodeLocation functionCodeLocation =
        FunctionCodeLocation.builder().repositoryType("ECR").imageUri("s3://ecr.image.repository/hello-world").build();
    UpdateFunctionCodeRequest request =
        awsLambdaTaskHelper.getUpdateFunctionCodeRequest(FUNCTION_NAME, functionCodeLocation,
            "functionConfigurationAsString", logCallback, AwsLambdaFunctionsInfraConfig.builder().build());
    assertThat(request).isNotNull();
    assertThat(request.imageUri()).isNotEmpty();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGetUpdateFunctionCodeRequestForS3() throws IOException {
    AwsLambdaTaskHelper spyAwsLambdaTaskHelper = spy(awsLambdaTaskHelper);

    doReturn(getFunctionResponse()).when(spyAwsLambdaTaskHelper).fetchExistingFunctionWithFunctionArn(any(), any());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doReturn(Optional.of(getFunctionResponse())).when(awsLambdaClient).getFunction(any(), any());
    doReturn(getSdkBytes()).when(awsLambdaTaskHelperBase).downloadArtifactFromS3BucketAndPrepareSdkBytes(any(), any());

    FunctionCodeLocation functionCodeLocation =
        FunctionCodeLocation.builder().repositoryType("S3").location("s3://somelocation").build();
    UpdateFunctionCodeRequest request =
        awsLambdaTaskHelper.getUpdateFunctionCodeRequest(FUNCTION_NAME, functionCodeLocation,
            getFunctionConfigurationAsString(), logCallback, AwsLambdaFunctionsInfraConfig.builder().build());
    assertThat(request).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testDeleteFunction() throws AwsLambdaException {
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doReturn(Optional.of(getFunctionResponse())).when(awsLambdaClient).getFunction(any(), any());
    doReturn(DeleteFunctionResponse.builder().build()).when(awsLambdaClient).deleteFunction(any(), any());
    DeleteFunctionResponse response =
        awsLambdaTaskHelper.deleteFunction(AwsLambdaFunctionsInfraConfig.builder().build(), FUNCTION_NAME, logCallback);
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCreateFunction() throws IOException {
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doReturn(Optional.of(getFunctionResponse())).when(awsLambdaClient).getFunction(any(), any());
    doReturn(getCreateFunctionResponse()).when(awsLambdaClient).createFunction(any(), any());

    CreateFunctionResponse response = awsLambdaTaskHelper.createFunction(AwsLambdaEcrArtifactConfig.builder().build(),
        logCallback, AwsLambdaFunctionsInfraConfig.builder().build(), CreateFunctionRequest.builder(), FUNCTION_NAME);
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testDeployFunctionRequiresFunctionName() throws IOException {
    String manifestContent = "{\n"
        + "   \"runtime\": \"nodejs16.x\",\n"
        + "   \"handler\": \"handler.hello\",\n"
        + "   \"role\": \"arn:aws:iam::806630305776:role/service-role/avengers-test-role-ypjbn4a8\"\n"
        + "}";
    assertThatThrownBy(()
                           -> awsLambdaTaskHelper.deployFunction(AwsLambdaFunctionsInfraConfig.builder().build(),
                               AwsLambdaEcrArtifactConfig.builder().build(), manifestContent,
                               Arrays.asList("aliasManifestContent"), logCallback))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testDeployFunction() throws IOException {
    String manifestContent = "{\n"
        + "   \"runtime\": \"nodejs16.x\",\n"
        + "   \"functionName\": \"test-lambda-10\",\n"
        + "   \"handler\": \"handler.hello\",\n"
        + "   \"role\": \"arn:aws:iam::806630305776:role/service-role/avengers-test-role-ypjbn4a8\"\n"
        + "}";

    AwsLambdaTaskHelper spyAwsLambdaTaskHelper = spy(awsLambdaTaskHelper);

    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doReturn(Optional.empty()).when(awsLambdaClient).getFunction(any(), any());
    doReturn(getCreateFunctionResponse()).when(awsLambdaClient).createFunction(any(), any());
    doReturn(getCreateFunctionResponse())
        .when(spyAwsLambdaTaskHelper)
        .createFunction(any(), any(), any(), any(), any());

    CreateFunctionResponse functionResponse =
        awsLambdaTaskHelper.deployFunction(AwsLambdaFunctionsInfraConfig.builder().build(),
            AwsLambdaEcrArtifactConfig.builder().build(), manifestContent, Collections.emptyList(), logCallback);
    assertThat(functionResponse).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCreateAlias() {
    String aliasManifestContent = "{\n"
        + "   \"description\": \"description for alias\",\n"
        + "   \"name\": \"Alias name\"\n"
        + "}";
    List<String> listOfaliasManifest = Arrays.asList(aliasManifestContent);

    ListAliasesResponse listAliasesResponse =
        ListAliasesResponse.builder()
            .aliases(AliasConfiguration.builder().name(FUNCTION_NAME).functionVersion("v1").build())
            .build();

    CreateAliasResponse createAliasResponse =
        CreateAliasResponse.builder().name("Alias name").functionVersion("v1").build();

    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doReturn(listAliasesResponse).when(awsLambdaClient).listAliases(any(), any());
    doReturn(createAliasResponse).when(awsLambdaClient).createAlias(any(), any());

    awsLambdaTaskHelper.createOrUpdateAlias(
        listOfaliasManifest, FUNCTION_NAME, "v1", AwsLambdaFunctionsInfraConfig.builder().build(), logCallback);
    verify(logCallback, times(4));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testUpdateAlias() {
    String aliasManifestContent = "{\n"
        + "   \"description\": \"description for alias\",\n"
        + "   \"name\": \"Alias name\"\n"
        + "}";
    List<String> listOfaliasManifest = Arrays.asList(aliasManifestContent);

    ListAliasesResponse listAliasesResponse =
        ListAliasesResponse.builder()
            .aliases(AliasConfiguration.builder().name("Alias name").functionVersion("v1").build())
            .build();

    UpdateAliasResponse updateAliasResponse =
        UpdateAliasResponse.builder().name("Alias name").functionVersion("v1").build();

    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doReturn(listAliasesResponse).when(awsLambdaClient).listAliases(any(), any());
    doReturn(updateAliasResponse).when(awsLambdaClient).updateAlias(any(), any());

    awsLambdaTaskHelper.createOrUpdateAlias(
        listOfaliasManifest, FUNCTION_NAME, "v1", AwsLambdaFunctionsInfraConfig.builder().build(), logCallback);
    verify(logCallback, times(4));
  }

  private static GetFunctionResponse getFunctionResponse() {
    return GetFunctionResponse.builder()
        .configuration(FunctionConfiguration.builder()
                           .functionArn("arn")
                           .functionName(FUNCTION_NAME)
                           .runtime("runtime")
                           .role("role")
                           .handler("handler")
                           .codeSize(10L)
                           .description("description")
                           .timeout(10)
                           .memorySize(128)
                           .codeSha256("asdf234wdfag")
                           .version("v1")
                           .kmsKeyArn("kmskeyarn")
                           .masterArn("masterArn")
                           .revisionId("revisionId")
                           .build())
        .code(FunctionCodeLocation.builder().location("s3://somelocation").build())
        .build();
  }

  private static CreateFunctionResponse getCreateFunctionResponse() {
    return CreateFunctionResponse.builder()
        .functionName(FUNCTION_NAME)
        .version("v1")
        .functionArn("FunctionArn")
        .build();
  }

  private static String getFunctionConfigurationAsString() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    FunctionConfiguration configuration = FunctionConfiguration.builder().functionArn("functionArn").build();
    return mapper.writeValueAsString(configuration);
  }

  @NotNull
  private static SdkBytes getSdkBytes() throws IOException {
    File tempFile = File.createTempFile("test", ".yaml");
    FileUtils.writeStringToFile(tempFile, "This is text in the file", Charsets.UTF_8.name());
    SdkBytes sdkBytes = SdkBytes.fromInputStream(new FileInputStream(tempFile));
    return sdkBytes;
  }
}

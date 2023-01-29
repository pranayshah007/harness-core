/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.ami;

import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ami.AMITagsResponse;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.runtime.AMIServerRuntimeException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.DummyLogCallbackImpl;
import io.harness.rule.Owner;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class AMIArtifactTaskHelperTest extends CategoryTest {
  @Mock private AMIArtifactTaskHandler amiArtifactTaskHandler;

  @InjectMocks private AMIArtifactTaskHelper amiArtifactTaskHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetBuilds() {
    SecretRefData accessKeyRef =
        SecretRefData.builder().identifier("id").decryptedValue("accessKey".toCharArray()).scope(Scope.ACCOUNT).build();

    SecretRefData secretKeyRef =
        SecretRefData.builder().identifier("id").decryptedValue("secretKey".toCharArray()).scope(Scope.ACCOUNT).build();

    AwsManualConfigSpecDTO awsManualConfigSpecDTO = AwsManualConfigSpecDTO.builder()
                                                        .accessKey("accessKey")
                                                        .accessKeyRef(accessKeyRef)
                                                        .secretKeyRef(secretKeyRef)
                                                        .build();

    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                            .config(awsManualConfigSpecDTO)
                                            .testRegion("us-east-1")
                                            .build();

    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder().credential(awsCredentialDTO).executeOnDelegate(true).build();

    AMIArtifactDelegateRequest amiArtifactDelegateRequest = AMIArtifactDelegateRequest.builder()
                                                                .awsConnectorDTO(awsConnectorDTO)
                                                                .region("region")
                                                                .connectorRef("connectorRef")
                                                                .encryptedDataDetails(new ArrayList<>())
                                                                .tags(new HashMap<>())
                                                                .filters(new HashMap<>())
                                                                .versionRegex("*")
                                                                .sourceType(ArtifactSourceType.AMI)
                                                                .build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(amiArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .build();

    List<BuildDetails> builds = new ArrayList<>();

    BuildDetails build1 = new BuildDetails();
    build1.setNumber("b1");
    build1.setUiDisplayName("Version# b1");

    BuildDetails build2 = new BuildDetails();
    build2.setNumber("b2");
    build2.setUiDisplayName("Version# b2");

    BuildDetails build3 = new BuildDetails();
    build3.setNumber("b3");
    build3.setUiDisplayName("Version# b3");

    BuildDetails build4 = new BuildDetails();
    build4.setNumber("b4");
    build4.setUiDisplayName("Version# b4");

    BuildDetails build5 = new BuildDetails();
    build5.setNumber("b5");
    build5.setUiDisplayName("Version# b5");

    builds.add(build1);
    builds.add(build2);
    builds.add(build3);
    builds.add(build4);
    builds.add(build5);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(builds)
                                                                      .artifactDelegateResponses(new ArrayList<>())
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    doNothing().when(amiArtifactTaskHandler).decryptRequestDTOs(amiArtifactDelegateRequest);

    when(amiArtifactTaskHandler.getBuilds(amiArtifactDelegateRequest)).thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        amiArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(amiArtifactTaskHandler).decryptRequestDTOs(amiArtifactDelegateRequest);
    verify(amiArtifactTaskHandler).getBuilds(amiArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild() {
    SecretRefData accessKeyRef =
        SecretRefData.builder().identifier("id").decryptedValue("accessKey".toCharArray()).scope(Scope.ACCOUNT).build();

    SecretRefData secretKeyRef =
        SecretRefData.builder().identifier("id").decryptedValue("secretKey".toCharArray()).scope(Scope.ACCOUNT).build();

    AwsManualConfigSpecDTO awsManualConfigSpecDTO = AwsManualConfigSpecDTO.builder()
                                                        .accessKey("accessKey")
                                                        .accessKeyRef(accessKeyRef)
                                                        .secretKeyRef(secretKeyRef)
                                                        .build();

    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                            .config(awsManualConfigSpecDTO)
                                            .testRegion("us-east-1")
                                            .build();

    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder().credential(awsCredentialDTO).executeOnDelegate(true).build();

    AMIArtifactDelegateRequest amiArtifactDelegateRequest = AMIArtifactDelegateRequest.builder()
                                                                .awsConnectorDTO(awsConnectorDTO)
                                                                .region("region")
                                                                .connectorRef("connectorRef")
                                                                .encryptedDataDetails(new ArrayList<>())
                                                                .tags(new HashMap<>())
                                                                .filters(new HashMap<>())
                                                                .versionRegex("*")
                                                                .sourceType(ArtifactSourceType.AMI)
                                                                .build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(amiArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();

    List<BuildDetails> builds = new ArrayList<>();

    BuildDetails build1 = new BuildDetails();
    build1.setNumber("b1");
    build1.setUiDisplayName("Version# b1");

    builds.add(build1);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(builds)
                                                                      .artifactDelegateResponses(new ArrayList<>())
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    doNothing().when(amiArtifactTaskHandler).decryptRequestDTOs(amiArtifactDelegateRequest);

    when(amiArtifactTaskHandler.getLastSuccessfulBuild(amiArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        amiArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(amiArtifactTaskHandler).decryptRequestDTOs(amiArtifactDelegateRequest);
    verify(amiArtifactTaskHandler).getLastSuccessfulBuild(amiArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetTags() {
    SecretRefData accessKeyRef =
        SecretRefData.builder().identifier("id").decryptedValue("accessKey".toCharArray()).scope(Scope.ACCOUNT).build();

    SecretRefData secretKeyRef =
        SecretRefData.builder().identifier("id").decryptedValue("secretKey".toCharArray()).scope(Scope.ACCOUNT).build();

    AwsManualConfigSpecDTO awsManualConfigSpecDTO = AwsManualConfigSpecDTO.builder()
                                                        .accessKey("accessKey")
                                                        .accessKeyRef(accessKeyRef)
                                                        .secretKeyRef(secretKeyRef)
                                                        .build();

    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                            .config(awsManualConfigSpecDTO)
                                            .testRegion("us-east-1")
                                            .build();

    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder().credential(awsCredentialDTO).executeOnDelegate(true).build();

    AMIArtifactDelegateRequest amiArtifactDelegateRequest = AMIArtifactDelegateRequest.builder()
                                                                .awsConnectorDTO(awsConnectorDTO)
                                                                .region("region")
                                                                .connectorRef("connectorRef")
                                                                .encryptedDataDetails(new ArrayList<>())
                                                                .tags(new HashMap<>())
                                                                .filters(new HashMap<>())
                                                                .versionRegex("*")
                                                                .sourceType(ArtifactSourceType.AMI)
                                                                .build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(amiArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_AMI_TAGS)
                                                        .build();

    List<String> tags = new ArrayList<>();

    tags.add("t1");
    tags.add("t2");
    tags.add("t3");
    tags.add("t4");
    tags.add("t5");

    AMITagsResponse amiTagsResponse =
        AMITagsResponse.builder().tags(tags).commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(null)
                                                                      .amiTags(amiTagsResponse)
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    doNothing().when(amiArtifactTaskHandler).decryptRequestDTOs(amiArtifactDelegateRequest);

    when(amiArtifactTaskHandler.listTags(amiArtifactDelegateRequest)).thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        amiArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(amiArtifactTaskHandler).decryptRequestDTOs(amiArtifactDelegateRequest);
    verify(amiArtifactTaskHandler).listTags(amiArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testSaveLogs() {
    DummyLogCallbackImpl logCallback = new DummyLogCallbackImpl();

    logCallback.saveExecutionLog("hello");

    amiArtifactTaskHelper.saveLogs(logCallback, "world");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testInvalidTaskType() {
    SecretRefData accessKeyRef =
        SecretRefData.builder().identifier("id").decryptedValue("accessKey".toCharArray()).scope(Scope.ACCOUNT).build();

    SecretRefData secretKeyRef =
        SecretRefData.builder().identifier("id").decryptedValue("secretKey".toCharArray()).scope(Scope.ACCOUNT).build();

    AwsManualConfigSpecDTO awsManualConfigSpecDTO = AwsManualConfigSpecDTO.builder()
                                                        .accessKey("accessKey")
                                                        .accessKeyRef(accessKeyRef)
                                                        .secretKeyRef(secretKeyRef)
                                                        .build();

    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                            .config(awsManualConfigSpecDTO)
                                            .testRegion("us-east-1")
                                            .build();

    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder().credential(awsCredentialDTO).executeOnDelegate(true).build();

    AMIArtifactDelegateRequest amiArtifactDelegateRequest = AMIArtifactDelegateRequest.builder()
                                                                .awsConnectorDTO(awsConnectorDTO)
                                                                .region("region")
                                                                .connectorRef("connectorRef")
                                                                .encryptedDataDetails(new ArrayList<>())
                                                                .tags(new HashMap<>())
                                                                .filters(new HashMap<>())
                                                                .versionRegex("*")
                                                                .sourceType(ArtifactSourceType.AMI)
                                                                .build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(amiArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_JOBS)
                                                        .build();

    ArtifactTaskResponse expectedArtifactTaskResponse =
        ArtifactTaskResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage(
                "There is no such AMI Artifacts Delegate task - " + artifactTaskParameters.getArtifactTaskType().name())
            .errorCode(ErrorCode.INVALID_ARGUMENT)
            .build();

    doNothing().when(amiArtifactTaskHandler).decryptRequestDTOs(amiArtifactDelegateRequest);

    ArtifactTaskResponse artifactTaskResponse =
        amiArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getCommandExecutionStatus())
        .isEqualTo(expectedArtifactTaskResponse.getCommandExecutionStatus());
    assertThat(artifactTaskResponse.getErrorMessage()).isEqualTo(expectedArtifactTaskResponse.getErrorMessage());
    assertThat(artifactTaskResponse.getErrorCode()).isEqualTo(expectedArtifactTaskResponse.getErrorCode());

    verify(amiArtifactTaskHandler).decryptRequestDTOs(amiArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testThrowException() {
    SecretRefData accessKeyRef =
        SecretRefData.builder().identifier("id").decryptedValue("accessKey".toCharArray()).scope(Scope.ACCOUNT).build();

    SecretRefData secretKeyRef =
        SecretRefData.builder().identifier("id").decryptedValue("secretKey".toCharArray()).scope(Scope.ACCOUNT).build();

    AwsManualConfigSpecDTO awsManualConfigSpecDTO = AwsManualConfigSpecDTO.builder()
                                                        .accessKey("accessKey")
                                                        .accessKeyRef(accessKeyRef)
                                                        .secretKeyRef(secretKeyRef)
                                                        .build();

    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                            .config(awsManualConfigSpecDTO)
                                            .testRegion("us-east-1")
                                            .build();

    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder().credential(awsCredentialDTO).executeOnDelegate(true).build();

    AMIArtifactDelegateRequest amiArtifactDelegateRequest = AMIArtifactDelegateRequest.builder()
                                                                .awsConnectorDTO(awsConnectorDTO)
                                                                .region("region")
                                                                .connectorRef("connectorRef")
                                                                .encryptedDataDetails(new ArrayList<>())
                                                                .tags(new HashMap<>())
                                                                .filters(new HashMap<>())
                                                                .versionRegex("*")
                                                                .sourceType(ArtifactSourceType.AMI)
                                                                .build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(amiArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .build();

    List<BuildDetails> builds = new ArrayList<>();

    BuildDetails build1 = new BuildDetails();
    build1.setNumber("b1");
    build1.setUiDisplayName("Version# b1");

    BuildDetails build2 = new BuildDetails();
    build2.setNumber("b2");
    build2.setUiDisplayName("Version# b2");

    BuildDetails build3 = new BuildDetails();
    build3.setNumber("b3");
    build3.setUiDisplayName("Version# b3");

    BuildDetails build4 = new BuildDetails();
    build4.setNumber("b4");
    build4.setUiDisplayName("Version# b4");

    BuildDetails build5 = new BuildDetails();
    build5.setNumber("b5");
    build5.setUiDisplayName("Version# b5");

    builds.add(build1);
    builds.add(build2);
    builds.add(build3);
    builds.add(build4);
    builds.add(build5);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(builds)
                                                                      .artifactDelegateResponses(new ArrayList<>())
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    doNothing().when(amiArtifactTaskHandler).decryptRequestDTOs(amiArtifactDelegateRequest);

    when(amiArtifactTaskHandler.getBuilds(amiArtifactDelegateRequest))
        .thenThrow(new AMIServerRuntimeException("error"));

    assertThatThrownBy(() -> amiArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters))
        .isInstanceOf(AMIServerRuntimeException.class);
  }
}

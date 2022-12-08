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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ami.AMITagsResponse;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
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
import io.harness.exception.GeneralException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class AMIArtifactTaskNGTest extends CategoryTest {
  @Mock AMIArtifactTaskHelper amiArtifactTaskHelper;

  @InjectMocks
  private AMIArtifactTaskNG amiArtifactTaskNG =
      new AMIArtifactTaskNG(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRunObjectParams() throws IOException {
    assertThatThrownBy(() -> amiArtifactTaskNG.run(new Object[10]))
        .hasMessage("not implemented")
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRunForBuilds() {
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

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    when(amiArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters)).thenReturn(artifactTaskResponse);

    assertThat(amiArtifactTaskNG.run(artifactTaskParameters)).isEqualTo(artifactTaskResponse);

    verify(amiArtifactTaskHelper).getArtifactCollectResponse(artifactTaskParameters);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRunForLastSuccessfulBuild() {
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

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    when(amiArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters)).thenReturn(artifactTaskResponse);

    assertThat(amiArtifactTaskNG.run(artifactTaskParameters)).isEqualTo(artifactTaskResponse);

    verify(amiArtifactTaskHelper).getArtifactCollectResponse(artifactTaskParameters);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRunForGetTags() {
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

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    when(amiArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters)).thenReturn(artifactTaskResponse);

    assertThat(amiArtifactTaskNG.run(artifactTaskParameters)).isEqualTo(artifactTaskResponse);

    verify(amiArtifactTaskHelper).getArtifactCollectResponse(artifactTaskParameters);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRunDoThrowException() {
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

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    String message = "General Exception";

    when(amiArtifactTaskHelper.getArtifactCollectResponse(any())).thenThrow(new GeneralException(message));

    assertThatThrownBy(() -> amiArtifactTaskNG.run(artifactTaskParameters))
        .isInstanceOf(GeneralException.class)
        .hasMessage(message);

    verify(amiArtifactTaskHelper).getArtifactCollectResponse(artifactTaskParameters);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsSupportingErrorFramework() {
    assertThat(amiArtifactTaskNG.isSupportingErrorFramework()).isEqualTo(true);
  }
}

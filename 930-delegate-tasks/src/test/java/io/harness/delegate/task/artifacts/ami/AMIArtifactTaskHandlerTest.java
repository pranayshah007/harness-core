/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.ami;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.ami.AMITagsResponse;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.ami.service.AMIRegistryService;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.encryption.SecretRefData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDC)
public class AMIArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock AMIRegistryService amiRegistryService;

  @InjectMocks AMIArtifactTaskHandler amiArtifactTaskHandler;

  @Mock private AwsNgConfigMapper awsNgConfigMapper;

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetBuilds() {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder()
                                              .accessKey("access-key".toCharArray())
                                              .secretKey("secret-key".toCharArray())
                                              .defaultRegion("region")
                                              .build();

    SecretRefData secretKeyRef = SecretRefData.builder().decryptedValue("secret-key".toCharArray()).build();

    SecretRefData accessKeyRef = SecretRefData.builder().decryptedValue("access-key".toCharArray()).build();

    AwsManualConfigSpecDTO awsCredentialSpecDTO = AwsManualConfigSpecDTO.builder()
                                                      .accessKey("access-key")
                                                      .accessKeyRef(accessKeyRef)
                                                      .secretKeyRef(secretKeyRef)
                                                      .build();

    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                            .config(awsCredentialSpecDTO)
                                            .build();

    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();

    AMIArtifactDelegateRequest sourceAttributes = AMIArtifactDelegateRequest.builder()
                                                      .awsConnectorDTO(awsConnectorDTO)
                                                      .region("region")
                                                      .connectorRef("connectorRef")
                                                      .encryptedDataDetails(new ArrayList<>())
                                                      .tags(new HashMap<>())
                                                      .filters(new HashMap<>())
                                                      .versionRegex("*")
                                                      .sourceType(ArtifactSourceType.AMI)
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

    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);

    doReturn(builds)
        .when(amiRegistryService)
        .listBuilds(awsInternalConfig, sourceAttributes.getRegion(), sourceAttributes.getTags(),
            sourceAttributes.getFilters(), sourceAttributes.getVersionRegex());

    ArtifactTaskExecutionResponse executionResponse = amiArtifactTaskHandler.getBuilds(sourceAttributes);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getBuildDetails()).isNotNull();
    assertThat(executionResponse.getBuildDetails().size()).isEqualTo(5);
    assertThat(executionResponse.getBuildDetails().get(0).getNumber()).isEqualTo("b1");
    assertThat(executionResponse.getBuildDetails().get(1).getUiDisplayName()).isEqualTo("Version# b2");
    assertThat(executionResponse.getBuildDetails().get(2).getUiDisplayName()).isEqualTo("Version# b3");
    assertThat(executionResponse.getBuildDetails().get(3).getUiDisplayName()).isEqualTo("Version# b4");
    assertThat(executionResponse.getBuildDetails().get(4).getNumber()).isEqualTo("b5");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetTags() {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder()
                                              .accessKey("access-key".toCharArray())
                                              .secretKey("secret-key".toCharArray())
                                              .defaultRegion("region")
                                              .build();

    SecretRefData secretKeyRef = SecretRefData.builder().decryptedValue("secret-key".toCharArray()).build();

    SecretRefData accessKeyRef = SecretRefData.builder().decryptedValue("access-key".toCharArray()).build();

    AwsManualConfigSpecDTO awsCredentialSpecDTO = AwsManualConfigSpecDTO.builder()
                                                      .accessKey("access-key")
                                                      .accessKeyRef(accessKeyRef)
                                                      .secretKeyRef(secretKeyRef)
                                                      .build();

    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                            .config(awsCredentialSpecDTO)
                                            .build();

    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();

    AMIArtifactDelegateRequest sourceAttributes = AMIArtifactDelegateRequest.builder()
                                                      .awsConnectorDTO(awsConnectorDTO)
                                                      .region("region")
                                                      .connectorRef("connectorRef")
                                                      .encryptedDataDetails(new ArrayList<>())
                                                      .sourceType(ArtifactSourceType.AMI)
                                                      .build();

    List<String> tags = new ArrayList<>();
    tags.add("t1");
    tags.add("t2");
    tags.add("t3");
    tags.add("t4");
    tags.add("t5");

    AMITagsResponse amiTagsResponse =
        AMITagsResponse.builder().tags(tags).commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);

    doReturn(amiTagsResponse).when(amiRegistryService).listTags(awsInternalConfig, sourceAttributes.getRegion());

    ArtifactTaskExecutionResponse executionResponse = amiArtifactTaskHandler.listTags(sourceAttributes);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getAmiTags()).isNotNull();
    assertThat(executionResponse.getAmiTags().getTags().size()).isEqualTo(5);
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.gar;

import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.gar.beans.GarInternalConfig;
import io.harness.artifacts.gar.beans.GarPackageVersionResponse;
import io.harness.artifacts.gar.beans.GarTags;
import io.harness.artifacts.gar.service.GARApiServiceImpl;
import io.harness.artifacts.gar.service.GarApiService;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.artifacts.googleartifactregistry.GARArtifactTaskHandler;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.exception.runtime.SecretNotFoundRuntimeException;
import io.harness.rule.Owner;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class GarTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks GARArtifactTaskHandler garArtifactTaskHandler;
  @Mock GcpHelperService gcpHelperService;
  @Mock GarApiService garApiService;
  private static final String TEST_PROJECT_ID = "project-a";
  private static final String TEST_ACCESS_TOKEN = String.format("{\"access_token\": \"%s\"}", TEST_PROJECT_ID);
  private final char[] serviceAccountKeyFileContent =
      String.format("{\"project_id\": \"%s\"}", TEST_PROJECT_ID).toCharArray();

  private GoogleCredential googleCredential;

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex() throws IOException {
    googleCredential = new GoogleCredential();
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("version").build();
    GarInternalConfig garInternalConfig;
    garInternalConfig = GarInternalConfig.builder()
                            .region("us")
                            .project("cd-play")
                            .pkg("mongo")
                            .bearerToken("Bearer null")
                            .repositoryName("vivek-repo")
                            .maxBuilds(Integer.MAX_VALUE)
                            .build();
    doReturn(buildDetailsInternal).when(garApiService).getLastSuccessfulBuildFromRegex(garInternalConfig, "v");
    doReturn(googleCredential).when(gcpHelperService).getGoogleCredential(serviceAccountKeyFileContent, false);
    GarDelegateRequest garDelegateRequest =
        GarDelegateRequest.builder()
            .region("us")
            .project("cd-play")
            .maxBuilds(Integer.MAX_VALUE)
            .repositoryName("vivek-repo")
            .pkg("mongo")
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .versionRegex("v")
            .build();
    ArtifactTaskExecutionResponse lastSuccessfulBuild =
        garArtifactTaskHandler.getLastSuccessfulBuild(garDelegateRequest);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0)).isInstanceOf(GarDelegateResponse.class);
    GarDelegateResponse attributes = (GarDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getVersion()).isEqualTo("version");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testverifyBuildNumber() throws IOException {
    googleCredential = new GoogleCredential();
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("version").build();
    GarInternalConfig garInternalConfig;
    garInternalConfig = GarInternalConfig.builder()
                            .region("us")
                            .project("cd-play")
                            .pkg("mongo")
                            .bearerToken("Bearer null")
                            .repositoryName("vivek-repo")
                            .maxBuilds(Integer.MAX_VALUE)
                            .build();
    doReturn(buildDetailsInternal).when(garApiService).verifyBuildNumber(garInternalConfig, "version");
    doReturn(googleCredential).when(gcpHelperService).getGoogleCredential(serviceAccountKeyFileContent, false);
    GarDelegateRequest garDelegateRequest =
        GarDelegateRequest.builder()
            .region("us")
            .project("cd-play")
            .maxBuilds(Integer.MAX_VALUE)
            .repositoryName("vivek-repo")
            .pkg("mongo")
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .version("version")
            .build();
    ArtifactTaskExecutionResponse lastSuccessfulBuild =
        garArtifactTaskHandler.getLastSuccessfulBuild(garDelegateRequest);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0)).isInstanceOf(GarDelegateResponse.class);
    GarDelegateResponse attributes = (GarDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getVersion()).isEqualTo("version");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetBuilds() throws IOException {
    googleCredential = new GoogleCredential();
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("version").build();
    List<BuildDetailsInternal> buildDetailsInternals = new ArrayList<>();
    buildDetailsInternals.add(buildDetailsInternal);
    GarInternalConfig garInternalConfig;
    garInternalConfig = GarInternalConfig.builder()
                            .region("us")
                            .project("cd-play")
                            .pkg("mongo")
                            .bearerToken("Bearer null")
                            .repositoryName("vivek-repo")
                            .maxBuilds(Integer.MAX_VALUE)
                            .build();
    doReturn(buildDetailsInternals).when(garApiService).getBuilds(garInternalConfig, "v", Integer.MAX_VALUE);
    doReturn(googleCredential).when(gcpHelperService).getGoogleCredential(serviceAccountKeyFileContent, false);
    GarDelegateRequest garDelegateRequest =
        GarDelegateRequest.builder()
            .region("us")
            .project("cd-play")
            .maxBuilds(Integer.MAX_VALUE)
            .repositoryName("vivek-repo")
            .pkg("mongo")
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .versionRegex("v")
            .build();
    ArtifactTaskExecutionResponse getbuild = garArtifactTaskHandler.getBuilds(garDelegateRequest);
    assertThat(getbuild).isNotNull();
    assertThat(getbuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(getbuild.getArtifactDelegateResponses().get(0)).isInstanceOf(GarDelegateResponse.class);
    GarDelegateResponse attributes = (GarDelegateResponse) getbuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getVersion()).isEqualTo("version");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSecretExceptions() throws IOException {
    GarDelegateRequest garDelegateRequest =
        GarDelegateRequest.builder()
            .region("us")
            .project("cd-play")
            .maxBuilds(Integer.MAX_VALUE)
            .repositoryName("vivek-repo")
            .pkg("mongo")
            .gcpConnectorDTO(GcpConnectorDTO.builder()
                                 .credential(GcpConnectorCredentialDTO.builder()
                                                 .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                                                 .config(GcpManualDetailsDTO.builder()
                                                             .secretKeyRef(SecretRefData.builder()
                                                                               .identifier("identifier")
                                                                               .scope(Scope.ACCOUNT)
                                                                               .decryptedValue(null)
                                                                               .build())
                                                             .build())
                                                 .build())
                                 .build())
            .versionRegex("v")
            .build();
    assertThatThrownBy(() -> garArtifactTaskHandler.getBuilds(garDelegateRequest))
        .extracting(ex -> ((SecretNotFoundRuntimeException) ex).getMessage())
        .isEqualTo("Google Artifact Registry: Could not find secret identifier under the scope of current ACCOUNT");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testIoExceptionsLastSuccesfulBuild() throws IOException {
    googleCredential = new GoogleCredential();
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("version").build();
    GarInternalConfig garInternalConfig;
    garInternalConfig = GarInternalConfig.builder()
                            .region("us")
                            .project("cd-play")
                            .pkg("mongo")
                            .bearerToken("Bearer null")
                            .repositoryName("vivek-repo")
                            .maxBuilds(Integer.MAX_VALUE)
                            .build();
    doReturn(buildDetailsInternal).when(garApiService).getLastSuccessfulBuildFromRegex(garInternalConfig, "v");

    doThrow(new IOException("hello-world"))
        .when(gcpHelperService)
        .getGoogleCredential(serviceAccountKeyFileContent, false);
    GarDelegateRequest garDelegateRequest =
        GarDelegateRequest.builder()
            .region("us")
            .project("cd-play")
            .maxBuilds(Integer.MAX_VALUE)
            .repositoryName("vivek-repo")
            .pkg("mongo")
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .versionRegex("v")
            .build();
    assertThatThrownBy(() -> garArtifactTaskHandler.getLastSuccessfulBuild(garDelegateRequest))
        .isInstanceOf(HintException.class)
        .hasMessage("Google Artifact Registry: Could not get Bearer Token");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetBuild() throws IOException {
    googleCredential = new GoogleCredential();
    GarInternalConfig garInternalConfig;
    garInternalConfig = GarInternalConfig.builder()
                            .region("us")
                            .project("cd-play")
                            .pkg("mongo")
                            .bearerToken("Bearer null")
                            .repositoryName("vivek-repo")
                            .maxBuilds(Integer.MAX_VALUE)
                            .build();
    doThrow(new IOException("hello-world"))
        .when(gcpHelperService)
        .getGoogleCredential(serviceAccountKeyFileContent, false);
    GarDelegateRequest garDelegateRequest =
        GarDelegateRequest.builder()
            .region("us")
            .project("cd-play")
            .maxBuilds(Integer.MAX_VALUE)
            .repositoryName("vivek-repo")
            .pkg("mongo")
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .versionRegex("v")
            .build();
    assertThatThrownBy(() -> garArtifactTaskHandler.getBuilds(garDelegateRequest))
        .isInstanceOf(HintException.class)
        .hasMessage("Google Artifact Registry: Could not get Bearer Token");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testProcessPage() {
    GarApiService garApiServiceLocal = new GARApiServiceImpl();
    GarInternalConfig garInternalConfig = GarInternalConfig.builder()
                                              .region("us")
                                              .project("cd-play")
                                              .pkg("mongo")
                                              .bearerToken("Bearer null")
                                              .repositoryName("vivek-repo")
                                              .maxBuilds(Integer.MAX_VALUE)
                                              .build();
    GarTags garTag =
        GarTags.builder()
            .name("projects/cd-play/locations/us/repositories/vivek-repo/packages/mongo/tags/tag1")
            .version(
                "projects/cd-play/locations/us/repositories/vivek-repo/packages/mongo/versions/sha256:2548c62c97b3f328d1938d9f6dcc0e85476fb88fcf1a01751940148c6824dc93")
            .build();
    GarPackageVersionResponse tagsPage =
        GarPackageVersionResponse.builder().Tags(Collections.singletonList(garTag)).build();
    List<BuildDetailsInternal> processedPage = garApiServiceLocal.processPage(tagsPage, "tag*", garInternalConfig);
    assertThat(processedPage.get(0).getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("tag1");
    assertThat(processedPage.get(0).getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo("us-docker.pkg.dev/cd-play/vivek-repo/mongo:tag1");
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifactory;

import static io.harness.delegate.beans.artifactory.ArtifactoryTaskParams.TaskType.FETCH_REPOSITORIES;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryImagePath;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.artifactory.ArtifactoryValidationHandler;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.artifactory.ArtifactoryTaskParams;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryDelegateTaskTest extends CategoryTest {
  @Mock SecretDecryptionService decryptionService;
  @Mock ArtifactoryRequestMapper artifactoryRequestMapper;
  @Mock NGErrorHelper ngErrorHelper;
  @Mock ArtifactoryValidationHandler artifactoryValidationHandler;
  @Mock ArtifactoryNgService artifactoryNgService;
  @InjectMocks
  private ArtifactoryDelegateTask artifactoryDelegateTask = new ArtifactoryDelegateTask(
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRunObjectParams() throws IOException {
    assertThatThrownBy(() -> artifactoryDelegateTask.run(new Object[10]))
        .hasMessage("not implemented")
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRunFetchRepositoriesTask() {
    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ArtifactoryConnectorDTO.builder()
            .auth(ArtifactoryAuthenticationDTO.builder()
                      .credentials(ArtifactoryUsernamePasswordAuthDTO.builder().build())
                      .build())
            .build();
    ArtifactoryTaskParams artifactoryTaskParams = ArtifactoryTaskParams.builder()
                                                      .artifactoryConnectorDTO(artifactoryConnectorDTO)
                                                      .taskType(FETCH_REPOSITORIES)
                                                      .build();
    doReturn(ArtifactoryUsernamePasswordAuthDTO.builder().build()).when(decryptionService).decrypt(any(), any());
    doReturn(ArtifactoryConfigRequest.builder().build())
        .when(artifactoryRequestMapper)
        .toArtifactoryRequest(eq(artifactoryConnectorDTO));
    doReturn(Collections.singletonMap("repo", "repo")).when(artifactoryNgService).getRepositories(any(), any());

    artifactoryDelegateTask.run(artifactoryTaskParams);

    verify(decryptionService, times(1)).decrypt(any(), any());
    verify(artifactoryRequestMapper, times(1)).toArtifactoryRequest(eq(artifactoryConnectorDTO));
    verify(artifactoryNgService, times(1)).getRepositories(any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRunFetchBuilds() {
    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ArtifactoryConnectorDTO.builder()
            .auth(ArtifactoryAuthenticationDTO.builder()
                      .credentials(ArtifactoryUsernamePasswordAuthDTO.builder().build())
                      .build())
            .build();
    ArtifactoryTaskParams artifactoryTaskParams = ArtifactoryTaskParams.builder()
                                                      .artifactoryConnectorDTO(artifactoryConnectorDTO)
                                                      .taskType(ArtifactoryTaskParams.TaskType.FETCH_BUILDS)
                                                      .build();
    doReturn(ArtifactoryUsernamePasswordAuthDTO.builder().build()).when(decryptionService).decrypt(any(), any());
    doReturn(ArtifactoryConfigRequest.builder().build())
        .when(artifactoryRequestMapper)
        .toArtifactoryRequest(eq(artifactoryConnectorDTO));
    doReturn(Collections.singletonMap("repo", "repo")).when(artifactoryNgService).getRepositories(any(), any());

    artifactoryDelegateTask.run(artifactoryTaskParams);

    verify(decryptionService, times(1)).decrypt(any(), any());
    verify(artifactoryRequestMapper, times(1)).toArtifactoryRequest(eq(artifactoryConnectorDTO));
    verify(artifactoryNgService, times(1)).getBuildDetails(any(), any(), any(), anyInt());
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testRunFetchImagePaths() {
    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
        ArtifactoryUsernamePasswordAuthDTO.builder().build();
    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ArtifactoryConnectorDTO.builder()
            .auth(ArtifactoryAuthenticationDTO.builder().credentials(artifactoryUsernamePasswordAuthDTO).build())
            .build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    ArtifactoryTaskParams artifactoryTaskParams = ArtifactoryTaskParams.builder()
                                                      .encryptedDataDetails(encryptedDataDetails)
                                                      .artifactoryConnectorDTO(artifactoryConnectorDTO)
                                                      .repoName("repoName")
                                                      .taskType(ArtifactoryTaskParams.TaskType.FETCH_IMAGE_PATHS)
                                                      .build();
    doReturn(ArtifactoryUsernamePasswordAuthDTO.builder().build())
        .when(decryptionService)
        .decrypt(artifactoryConnectorDTO, encryptedDataDetails);
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    doReturn(artifactoryConfigRequest).when(artifactoryRequestMapper).toArtifactoryRequest(eq(artifactoryConnectorDTO));
    List<ArtifactoryImagePath> artifactoryImagePath = new ArrayList<>();
    doReturn(artifactoryImagePath).when(artifactoryNgService).getImagePaths(artifactoryConfigRequest, "repoName");

    artifactoryDelegateTask.run(artifactoryTaskParams);

    verify(decryptionService, times(1)).decrypt(artifactoryUsernamePasswordAuthDTO, encryptedDataDetails);
    verify(artifactoryRequestMapper, times(1)).toArtifactoryRequest(eq(artifactoryConnectorDTO));
    verify(artifactoryNgService, times(1)).getImagePaths(artifactoryConfigRequest, "repoName");
  }
}

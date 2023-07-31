/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.git.GithubAppToGitMapperDelegate;
import io.harness.connector.task.git.ScmConnectorMapperDelegate;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ScmConnectorMapperDelegateTest extends CategoryTest {
  @Mock GithubAppToGitMapperDelegate githubAppToGitMapperDelegate;
  @InjectMocks ScmConnectorMapperDelegate scmConnectorMapperDelegate;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testScmConnectorMapperDelegate() {
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .authentication(
                GithubAuthenticationDTO.builder()
                    .authType(GitAuthType.HTTP)
                    .credentials(GithubHttpCredentialsDTO.builder()
                                     .type(GithubHttpAuthenticationType.GITHUB_APP)
                                     .httpCredentialsSpec(GithubAppDTO.builder()
                                                              .installationId("id")
                                                              .applicationId("app")
                                                              .privateKeyRef(SecretRefData.builder().build())
                                                              .build())
                                     .build())
                    .build())
            .build();
    GitConfigDTO gitConfigDTO = GitConfigDTO.builder().gitAuthType(GitAuthType.HTTP).url("url").build();
    doReturn(gitConfigDTO).when(githubAppToGitMapperDelegate).mapToGitConfigDTO(any(), any());
    GitConfigDTO gitConfig = scmConnectorMapperDelegate.toGitConfigDTO(githubConnectorDTO, Collections.emptyList());

    assertThat(gitConfig).isEqualTo(gitConfigDTO);
  }
}

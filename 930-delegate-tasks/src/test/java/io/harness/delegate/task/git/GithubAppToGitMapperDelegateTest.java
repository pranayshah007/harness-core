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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.git.GitHubAppAuthenticationHelper;
import io.harness.connector.task.git.GithubAppToGitMapperDelegate;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GithubAppToGitMapperDelegateTest extends CategoryTest {
  @Mock private GitHubAppAuthenticationHelper gitHubAppAuthenticationHelper;
  @InjectMocks private GithubAppToGitMapperDelegate mapperDelegate;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testMapToGitConfigDTO() {
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder().build();
    githubConnectorDTO.setConnectionType(GitConnectionType.ACCOUNT);
    githubConnectorDTO.setUrl("https://github.com/my-repo");
    githubConnectorDTO.setValidationRepo("https://github.com/validate-repo");

    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    SecretRefData passwordRef = new SecretRefData();
    when(gitHubAppAuthenticationHelper.getGithubAppSecretFromConnector(any(), any())).thenReturn(passwordRef);

    GitConfigDTO gitConfigDTO = mapperDelegate.mapToGitConfigDTO(githubConnectorDTO, encryptedDataDetails);

    assertThat(GitConnectionType.ACCOUNT).isEqualTo(gitConfigDTO.getGitConnectionType());
    assertThat("https://github.com/my-repo").isEqualTo(gitConfigDTO.getUrl());
    assertThat("https://github.com/validate-repo").isEqualTo(gitConfigDTO.getValidationRepo());
    verify(gitHubAppAuthenticationHelper, times(1)).getGithubAppSecretFromConnector(any(), any());
  }
}

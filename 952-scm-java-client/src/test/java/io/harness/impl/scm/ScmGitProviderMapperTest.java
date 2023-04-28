/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.impl.scm;

import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.product.ci.scm.proto.Provider;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class ScmGitProviderMapperTest {
  @InjectMocks ScmGitProviderMapper scmGitProviderMapper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testMapToGitlabProviderWithApiUrl() {
    final String url = "https://harness.io/gitlab/rutvij.mehta1/spring-cloud-alibaba.git\n";
    SecretRefData tokenRef = SecretRefHelper.createSecretRef("tokenRef");
    tokenRef.setDecryptedValue("tokenRef".toCharArray());

    GitlabHttpCredentialsDTO gitlabHttpCredentialsDTO =
        GitlabHttpCredentialsDTO.builder().httpCredentialsSpec(GitlabUsernameTokenDTO.builder().build()).build();
    GitlabAuthenticationDTO gitlabAuthenticationDTO =
        GitlabAuthenticationDTO.builder().authType(HTTP).credentials(gitlabHttpCredentialsDTO).build();
    GitlabApiAccessDTO gitlabApiAccessDTO =
        GitlabApiAccessDTO.builder()
            .type(GitlabApiAccessType.TOKEN)
            .spec(GitlabTokenSpecDTO.builder().tokenRef(tokenRef).apiUrl("https://harness.io/gitlab/").build())
            .build();

    final GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                      .connectionType(GitConnectionType.REPO)
                                                      .url(url)
                                                      .authentication(gitlabAuthenticationDTO)
                                                      .apiAccess(gitlabApiAccessDTO)
                                                      .build();

    final Provider provider = scmGitProviderMapper.mapToSCMGitProvider(gitlabConnectorDTO);
    assertThat(provider).isNotNull();
    assertThat(provider.getEndpoint()).isEqualTo("https://harness.io/gitlab/");
  }

  public void testMapToGitlabProviderWithApiUrlNoSlash() {
    final String url = "https://harness.io/gitlab/rutvij.mehta1/spring-cloud-alibaba.git\n";
    SecretRefData tokenRef = SecretRefHelper.createSecretRef("tokenRef");
    tokenRef.setDecryptedValue("tokenRef".toCharArray());

    GitlabHttpCredentialsDTO gitlabHttpCredentialsDTO =
        GitlabHttpCredentialsDTO.builder().httpCredentialsSpec(GitlabUsernameTokenDTO.builder().build()).build();
    GitlabAuthenticationDTO gitlabAuthenticationDTO =
        GitlabAuthenticationDTO.builder().authType(HTTP).credentials(gitlabHttpCredentialsDTO).build();
    GitlabApiAccessDTO gitlabApiAccessDTO =
        GitlabApiAccessDTO.builder()
            .type(GitlabApiAccessType.TOKEN)
            .spec(GitlabTokenSpecDTO.builder().tokenRef(tokenRef).apiUrl("https://harness.io/gitlab").build())
            .build();

    final GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                      .connectionType(GitConnectionType.REPO)
                                                      .url(url)
                                                      .authentication(gitlabAuthenticationDTO)
                                                      .apiAccess(gitlabApiAccessDTO)
                                                      .build();

    final Provider provider = scmGitProviderMapper.mapToSCMGitProvider(gitlabConnectorDTO);
    assertThat(provider).isNotNull();
    assertThat(provider.getEndpoint()).isEqualTo("https://harness.io/gitlab/");
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testMapToGitlabProviderWithoutApiUrl() {
    final String url = "https://harness.io/gitlab/rutvij.mehta1/spring-cloud-alibaba.git\n";
    SecretRefData tokenRef = SecretRefHelper.createSecretRef("tokenRef");
    tokenRef.setDecryptedValue("tokenRef".toCharArray());

    GitlabHttpCredentialsDTO gitlabHttpCredentialsDTO =
        GitlabHttpCredentialsDTO.builder().httpCredentialsSpec(GitlabUsernameTokenDTO.builder().build()).build();
    GitlabAuthenticationDTO gitlabAuthenticationDTO =
        GitlabAuthenticationDTO.builder().authType(HTTP).credentials(gitlabHttpCredentialsDTO).build();
    GitlabApiAccessDTO gitlabApiAccessDTO = GitlabApiAccessDTO.builder()
                                                .type(GitlabApiAccessType.TOKEN)
                                                .spec(GitlabTokenSpecDTO.builder().tokenRef(tokenRef).build())
                                                .build();

    final GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                      .connectionType(GitConnectionType.REPO)
                                                      .url(url)
                                                      .authentication(gitlabAuthenticationDTO)
                                                      .apiAccess(gitlabApiAccessDTO)
                                                      .build();

    final Provider provider = scmGitProviderMapper.mapToSCMGitProvider(gitlabConnectorDTO);
    assertThat(provider).isNotNull();
    assertThat(provider.getEndpoint()).isEqualTo("https://harness.io/");
  }
}

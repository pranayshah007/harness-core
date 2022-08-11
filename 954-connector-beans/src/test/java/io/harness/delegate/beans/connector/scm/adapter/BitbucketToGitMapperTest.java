/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.adapter;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.delegate.beans.connector.scm.GitAuthType.SSH;
import static io.harness.delegate.beans.connector.scm.GitConnectionType.ACCOUNT;
import static io.harness.delegate.beans.connector.scm.GitConnectionType.REPO;
import static io.harness.rule.OwnerRule.DEEPAK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(DX)
public class BitbucketToGitMapperTest extends CategoryTest {
  Set<String> delegateSelectors = new HashSet<>();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    delegateSelectors.add("abc");
    delegateSelectors.add("def");
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testMappingToHTTPGitConfigDTO() {
    final String url = "url";
    final String passwordRef = "passwordRef";
    final String username = "username";
    final String appId = "appId";
    final String insId = "insId";
    final String privateKeyRef = "privateKeyRef";
    final String validationRepo = "validationRepo";

    final BitbucketAuthenticationDTO bitbucketAuthenticationDTO =
        BitbucketAuthenticationDTO.builder()
            .authType(HTTP)
            .credentials(BitbucketHttpCredentialsDTO.builder()
                             .type(BitbucketHttpAuthenticationType.USERNAME_AND_PASSWORD)
                             .httpCredentialsSpec(BitbucketUsernamePasswordDTO.builder()
                                                      .passwordRef(SecretRefHelper.createSecretRef(passwordRef))
                                                      .username(username)
                                                      .build())
                             .build())
            .build();
    final BitbucketConnectorDTO bitbucketConnectorDTO = BitbucketConnectorDTO.builder()
                                                            .url(url)
                                                            .validationRepo(validationRepo)
                                                            .connectionType(GitConnectionType.ACCOUNT)
                                                            .authentication(bitbucketAuthenticationDTO)
                                                            .delegateSelectors(delegateSelectors)
                                                            .executeOnDelegate(false)
                                                            .build();
    GitConfigDTO gitConfigDTO = BitbucketToGitMapper.mapToGitConfigDTO(bitbucketConnectorDTO);
    assertThat(gitConfigDTO).isNotNull();
    assertThat(gitConfigDTO.getGitAuthType()).isEqualTo(HTTP);
    assertThat(gitConfigDTO.getExecuteOnDelegate()).isEqualTo(false);
    assertThat(gitConfigDTO.getDelegateSelectors()).isEqualTo(delegateSelectors);
    GitHTTPAuthenticationDTO gitAuthentication = (GitHTTPAuthenticationDTO) gitConfigDTO.getGitAuth();
    assertThat(gitConfigDTO.getGitConnectionType()).isEqualTo(ACCOUNT);
    assertThat(gitConfigDTO.getUrl()).isEqualTo(url);
    assertThat(gitConfigDTO.getValidationRepo()).isEqualTo(validationRepo);
    assertThat(gitAuthentication.getUsername()).isEqualTo(username);
    assertThat(gitAuthentication.getPasswordRef().toSecretRefStringValue()).isEqualTo(passwordRef);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testMappingToSSHGitConfigDTO() {
    final String url = "url";
    String sshKeyReference = "sshKeyReference";
    final BitbucketAuthenticationDTO bitbucketAuthenticationDTO =
        BitbucketAuthenticationDTO.builder()
            .authType(GitAuthType.SSH)
            .credentials(BitbucketSshCredentialsDTO.builder()
                             .sshKeyRef(SecretRefHelper.createSecretRef(sshKeyReference))
                             .build())
            .build();

    final BitbucketConnectorDTO bitbucketConnectorDTO = BitbucketConnectorDTO.builder()
                                                            .url(url)
                                                            .connectionType(GitConnectionType.REPO)
                                                            .authentication(bitbucketAuthenticationDTO)
                                                            .delegateSelectors(delegateSelectors)
                                                            .build();
    GitConfigDTO gitConfigDTO = BitbucketToGitMapper.mapToGitConfigDTO(bitbucketConnectorDTO);
    assertThat(gitConfigDTO).isNotNull();
    assertThat(gitConfigDTO.getGitAuthType()).isEqualTo(SSH);
    GitSSHAuthenticationDTO gitAuthentication = (GitSSHAuthenticationDTO) gitConfigDTO.getGitAuth();
    assertThat(gitAuthentication.getEncryptedSshKey()).isEqualTo(SecretRefHelper.createSecretRef(sshKeyReference));
    assertThat(gitConfigDTO.getUrl()).isEqualTo(url);
    assertThat(gitConfigDTO.getDelegateSelectors()).isEqualTo(delegateSelectors);
    assertThat(gitConfigDTO.getGitConnectionType()).isEqualTo(REPO);
  }
}

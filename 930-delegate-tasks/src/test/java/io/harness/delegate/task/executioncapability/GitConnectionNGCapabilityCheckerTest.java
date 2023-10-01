/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import static io.harness.delegate.beans.connector.scm.GitAuthType.HTTP;
import static io.harness.delegate.beans.connector.scm.GitConnectionType.ACCOUNT;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.connector.task.git.ScmConnectorMapperDelegate;
import io.harness.connector.task.shell.SshSessionConfigMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.GitConnectionNGCapability;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.runtime.SCMRuntimeException;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.service.ScmServiceClient;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GitConnectionNGCapabilityCheckerTest extends CategoryTest {
  @Mock private SecretDecryptionService decryptionService;
  @Mock private NGGitService gitService;
  @Mock private DelegateConfiguration delegateConfiguration;
  @Mock private SshSessionConfigMapper sshSessionConfigMapper;
  @Mock private GitDecryptionHelper gitDecryptionHelper;
  @Mock private ScmDelegateClient scmDelegateClient;
  @Mock private ScmServiceClient scmServiceClient;
  @Mock private ScmConnectorMapperDelegate scmConnectorMapperDelegate;
  @InjectMocks GitConnectionNGCapabilityChecker gitConnectionNGCapabilityChecker;

  private final GetUserReposResponse userReposResponse = GetUserReposResponse.newBuilder().build();
  private final GithubHttpCredentialsDTO githubHttpCredentialsDTO =
      GithubHttpCredentialsDTO.builder()
          .httpCredentialsSpec(
              GithubUsernamePasswordDTO.builder()
                  .username("user")
                  .passwordRef(
                      SecretRefData.builder().identifier("passRef").decryptedValue("passarray".toCharArray()).build())
                  .build())
          .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
          .build();
  private final GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                                .gitAuthType(HTTP)
                                                .gitConnectionType(ACCOUNT)
                                                .url("url")
                                                .gitAuth(GitHTTPAuthenticationDTO.builder()
                                                             .username("username")
                                                             .passwordRef(SecretRefData.builder()
                                                                              .identifier("gitPassword")
                                                                              .scope(Scope.ACCOUNT)
                                                                              .decryptedValue("password".toCharArray())
                                                                              .build())
                                                             .build())
                                                .build();

  @Before
  public void setup() {
    doNothing().when(gitDecryptionHelper).decryptApiAccessConfig(any(), any());
    doReturn(gitConfigDTO).when(scmConnectorMapperDelegate).toGitConfigDTO(any(), any());
    doReturn(githubHttpCredentialsDTO).when(decryptionService).decrypt(any(), any());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void performCapabilityCheckForScmConnectorTest() {
    GitConnectionNGCapability capability = GitConnectionNGCapability.builder()
                                               .gitConfig(GithubConnectorDTO.builder().build())
                                               .encryptedDataDetails(Collections.emptyList())
                                               .optimizedFilesFetch(true)
                                               .sshKeySpecDTO(SSHKeySpecDTO.builder().build())
                                               .build();
    doReturn(userReposResponse).when(scmDelegateClient).processScmRequest(any());
    CapabilityResponse capabilityResponse = gitConnectionNGCapabilityChecker.performCapabilityCheck(capability);

    assertThat(capabilityResponse.isValidated()).isTrue();
    assertThat(capabilityResponse.getDelegateCapability()).isEqualTo(capability);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void performCapabilityCheckForJgitTest() {
    GitConnectionNGCapability capability = GitConnectionNGCapability.builder()
                                               .gitConfig(GithubConnectorDTO.builder().build())
                                               .encryptedDataDetails(Collections.emptyList())
                                               .optimizedFilesFetch(false)
                                               .sshKeySpecDTO(SSHKeySpecDTO.builder().build())
                                               .build();
    doNothing().when(gitService).validateOrThrow(any(), any(), any());
    CapabilityResponse capabilityResponse = gitConnectionNGCapabilityChecker.performCapabilityCheck(capability);

    assertThat(capabilityResponse.isValidated()).isTrue();
    assertThat(capabilityResponse.getDelegateCapability()).isEqualTo(capability);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void performCapabilityCheckForScmConnectorTestForFailure() {
    GitConnectionNGCapability capability = GitConnectionNGCapability.builder()
                                               .gitConfig(GithubConnectorDTO.builder().build())
                                               .encryptedDataDetails(Collections.emptyList())
                                               .optimizedFilesFetch(true)
                                               .sshKeySpecDTO(SSHKeySpecDTO.builder().build())
                                               .build();
    doThrow(new SCMRuntimeException("")).when(scmDelegateClient).processScmRequest(any());
    CapabilityResponse capabilityResponse = gitConnectionNGCapabilityChecker.performCapabilityCheck(capability);

    assertThat(capabilityResponse.isValidated()).isFalse();
  }
}

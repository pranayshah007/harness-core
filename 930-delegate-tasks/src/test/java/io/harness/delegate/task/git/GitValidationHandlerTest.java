/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.DEEPAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.task.git.GitCommandTaskHandler;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.connector.task.git.GitValidationHandler;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.ScmValidationParams;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.shell.SshSessionConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(DX)
public class GitValidationHandlerTest extends CategoryTest {
  @Mock private GitCommandTaskHandler gitCommandTaskHandler;
  @Mock private GitDecryptionHelper gitDecryptionHelper;
  @Mock private DecryptionHelper decryptionHelper;
  @InjectMocks GitValidationHandler gitValidationHandler;

  private SshSessionConfig sshSessionConfig;
  private DecryptableEntity decryptableEntity;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doNothing().when(gitDecryptionHelper).decryptGitConfig(any(GitConfigDTO.class), any());
    doReturn(sshSessionConfig).when(gitDecryptionHelper).getSSHSessionConfig(any(), any());
    doReturn(decryptableEntity).when(decryptionHelper).decrypt(any(DecryptableEntity.class), anyList());
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testValidationForAccountLevelConnector() {
    ConnectorValidationResult result = ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build();
    doReturn(result)
        .when(gitCommandTaskHandler)
        .validateGitCredentials(any(GitConfigDTO.class), any(ScmConnector.class), any(String.class), any());

    GitConfigDTO gitconfigDTO = GitConfigDTO.builder()
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .gitAuth(GitHTTPAuthenticationDTO.builder()
                                                 .username("username")
                                                 .passwordRef(SecretRefData.builder().identifier("passwordRef").build())
                                                 .build())
                                    .gitAuthType(GitAuthType.HTTP)
                                    .build();
    ScmValidationParams gitValidationParameters =
        ScmValidationParams.builder().scmConnector(gitconfigDTO).gitConfigDTO(gitconfigDTO).build();
    ConnectorValidationResult validationResult =
        gitValidationHandler.validate(gitValidationParameters, "accountIdentifier");
    assertThat(validationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    verify(decryptionHelper, times(0)).decrypt(any(DecryptableEntity.class), anyList());
    verify(gitDecryptionHelper, times(1)).decryptGitConfig(any(GitConfigDTO.class), any());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testValidationForRepoLevelConnector() {
    ConnectorValidationResult result = ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build();
    doReturn(result)
        .when(gitCommandTaskHandler)
        .validateGitCredentials(any(GitConfigDTO.class), any(ScmConnector.class), any(String.class), any());

    ScmValidationParams gitValidationParameters =
        ScmValidationParams.builder()
            .gitConfigDTO(GitConfigDTO.builder().gitConnectionType(GitConnectionType.REPO).build())
            .scmConnector(GithubConnectorDTO.builder()
                              .apiAccess(GithubApiAccessDTO.builder().spec(GithubAppSpecDTO.builder().build()).build())
                              .build())
            .build();
    ConnectorValidationResult validationResult =
        gitValidationHandler.validate(gitValidationParameters, "accountIdentifier");

    assertThat(validationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }
}

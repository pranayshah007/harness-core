/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.connector.task.shell.SshSessionConfigMapper;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;

import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class GitDecryptionHelperTest extends CategoryTest {
  @Mock private SecretDecryptionService decryptionService;
  @Mock private SshSessionConfigMapper sshSessionConfigMapper;
  @InjectMocks GitDecryptionHelper gitDecryptionHelper;
  @Mock private DecryptionHelper decryptionHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testDecryptGitConfig() {
    gitDecryptionHelper.decryptGitConfig(
        GitConfigDTO.builder().gitAuth(mock(GitAuthenticationDTO.class)).build(), new ArrayList<>());
    verify(decryptionHelper, times(1)).decrypt(any(GitAuthenticationDTO.class), anyList());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSshSessionConfig() {
    SshSessionConfig expectedSshSessionConfig = SshSessionConfig.Builder.aSshSessionConfig().build();
    doReturn(expectedSshSessionConfig)
        .when(sshSessionConfigMapper)
        .getSSHSessionConfig(any(SSHKeySpecDTO.class), anyList());
    SshSessionConfig actualSshSessionConfig =
        gitDecryptionHelper.getSSHSessionConfig(SSHKeySpecDTO.builder().build(), new ArrayList<>());
    assertThat(actualSshSessionConfig).isEqualTo(expectedSshSessionConfig);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSshSessionConfigGivenNullSpec() {
    SshSessionConfig sshSessionConfig = gitDecryptionHelper.getSSHSessionConfig(null, new ArrayList<>());
    assertThat(sshSessionConfig).isNull();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testDecryptApiAccessConfig() {
    ScmConnector connector = GithubConnectorDTO.builder()
                                 .apiAccess(GithubApiAccessDTO.builder()
                                                .type(GithubApiAccessType.GITHUB_APP)
                                                .spec(GithubAppSpecDTO.builder().build())
                                                .build())
                                 .build();
    doReturn(connector).when(decryptionService).decrypt(any(DecryptableEntity.class), anyList());

    gitDecryptionHelper.decryptApiAccessConfig(connector, new ArrayList<>());
    verify(decryptionHelper, times(1)).decrypt(any(DecryptableEntity.class), anyList());
  }
}

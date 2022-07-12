package io.harness.gitsync.scm;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class BitbucketConnectorDTOTest extends GitSyncTestBase {
  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetRepositoryDetailsForBitbucketServerSSH() {
    String repoUrl = "ssh://git@bitbucket.dev.harness.io:7999/repoOrg/repoName.git";
    BitbucketConnectorDTO bitbucketConnectorDTO =
        BitbucketConnectorDTO.builder()
            .connectionType(GitConnectionType.REPO)
            .authentication(BitbucketAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
            .url(repoUrl)
            .build();
    assertThat(bitbucketConnectorDTO.getGitRepositoryDetails().getName()).isEqualTo("repoName");
    assertThat(bitbucketConnectorDTO.getGitRepositoryDetails().getOrg()).isEqualTo("repoOrg");
  }
}

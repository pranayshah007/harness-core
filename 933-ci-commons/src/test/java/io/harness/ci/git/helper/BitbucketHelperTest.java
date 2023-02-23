/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ci.git.helper;

import static io.harness.rule.OwnerRule.MEENA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.git.helper.BitbucketHelper;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class BitbucketHelperTest extends CategoryTest {
  private static final String USERNAME = "testuser";
  private static final String CLOUD_URL = "https://bitbucket.org/meenaharness/test-repo.git";
  private static final String CLOUD_PR_LINK = "https://bitbucket.org/meenaharness/test-repo/pull-requests/1";
  private static final String ONPREM_PR_LINK =
      "https://bitbucket.dev.harness.io/projects/HAR/repos/test-repo/pull-requests/1";
  private static final String ONPREM_URL = "https://bitbucket.dev.harness.io/scm/har/test-repo.git";

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testFetchSaaSUserName() {
    BitbucketConnectorDTO bitbucketConnectorDTO =
        BitbucketConnectorDTO.builder()
            .url(CLOUD_URL)
            .apiAccess(BitbucketApiAccessDTO.builder()
                           .type(BitbucketApiAccessType.USERNAME_AND_TOKEN)
                           .spec(BitbucketUsernameTokenApiAccessDTO.builder()
                                     .username(USERNAME)
                                     .tokenRef(SecretRefData.builder().build())
                                     .build())
                           .build())
            .build();
    String username = BitbucketHelper.fetchUserName(bitbucketConnectorDTO, "testconnector");
    assertThat(username).isEqualTo(USERNAME);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testFetchOnpremUserName() {
    BitbucketConnectorDTO bitbucketConnectorDTO =
        BitbucketConnectorDTO.builder()
            .url(ONPREM_URL)
            .apiAccess(BitbucketApiAccessDTO.builder()
                           .type(BitbucketApiAccessType.USERNAME_AND_TOKEN)
                           .spec(BitbucketUsernameTokenApiAccessDTO.builder()
                                     .username(USERNAME)
                                     .tokenRef(SecretRefData.builder().build())
                                     .build())
                           .build())
            .build();
    String username = BitbucketHelper.fetchUserName(bitbucketConnectorDTO, "testconnector");
    assertThat(username).isEqualTo(USERNAME);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testFetchUserNameInvalid() {
    BitbucketConnectorDTO bitbucketConnectorDTO =
        BitbucketConnectorDTO.builder()
            .url(CLOUD_URL)
            .apiAccess(BitbucketApiAccessDTO.builder()
                           .spec(BitbucketUsernameTokenApiAccessDTO.builder()
                                     .username(USERNAME)
                                     .tokenRef(SecretRefData.builder().build())
                                     .build())
                           .build())
            .build();
    BitbucketHelper.fetchUserName(bitbucketConnectorDTO, "testconnector");
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetOnPremPRLink() {
    BitbucketConnectorDTO bitbucketConnectorDTO =
        BitbucketConnectorDTO.builder()
            .url(ONPREM_URL)
            .connectionType(GitConnectionType.REPO)
            .authentication(BitbucketAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
            .apiAccess(BitbucketApiAccessDTO.builder()
                           .type(BitbucketApiAccessType.USERNAME_AND_TOKEN)
                           .spec(BitbucketUsernameTokenApiAccessDTO.builder()
                                     .username(USERNAME)
                                     .tokenRef(SecretRefData.builder().build())
                                     .build())
                           .build())
            .build();
    String prLink = BitbucketHelper.getBitbucketPRLink(bitbucketConnectorDTO, 1);
    assertThat(prLink).isEqualTo(ONPREM_PR_LINK);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetSaaSPRLink() {
    BitbucketConnectorDTO bitbucketConnectorDTO =
        BitbucketConnectorDTO.builder()
            .url(CLOUD_URL)
            .connectionType(GitConnectionType.REPO)
            .authentication(BitbucketAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
            .apiAccess(BitbucketApiAccessDTO.builder()
                           .type(BitbucketApiAccessType.USERNAME_AND_TOKEN)
                           .spec(BitbucketUsernameTokenApiAccessDTO.builder()
                                     .username(USERNAME)
                                     .tokenRef(SecretRefData.builder().build())
                                     .build())
                           .build())
            .build();
    String prLink = BitbucketHelper.getBitbucketPRLink(bitbucketConnectorDTO, 1);
    assertThat(prLink).isEqualTo(CLOUD_PR_LINK);
  }
}

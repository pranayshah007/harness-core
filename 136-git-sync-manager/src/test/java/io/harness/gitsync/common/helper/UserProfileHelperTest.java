/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.ng.userprofile.commons.BitbucketSCMDTO;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.ng.userprofile.services.api.SourceCodeManagerService;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class UserProfileHelperTest extends GitSyncTestBase {
  private static final String USER = "TEST_USER";
  private static final String ACCOUNT_ID = "ACCOUNT_ID";

  @Mock SourceCodeManagerService sourceCodeManagerService;
  @Inject UserProfileHelper userProfileHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    userProfileHelper = new UserProfileHelper(sourceCodeManagerService);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testValidateIfScmUserProfileIsSet_whenScmNotSet() {
    when(sourceCodeManagerService.get(any())).thenReturn(Collections.emptyList());

    assertThatThrownBy(() -> userProfileHelper.validateIfScmUserProfileIsSet(ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex
            -> ex.getMessage().equals("We don’t have your git credentials for the selected folder."
                + " Please update the credentials in user profile."));
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void scmUserShouldBeReturnedUserSCMProfileIsSet() {
    setUserPrincipal(USER);

    when(sourceCodeManagerService.get(USER, ACCOUNT_ID)).thenReturn(Collections.singletonList(getBitBucketSCMDTO()));

    String scmUserName = userProfileHelper.getScmUserName(ACCOUNT_ID, SCMType.BITBUCKET);
    assertThat(scmUserName).isEqualTo("SCM_USER");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void getScmUserWillFailWhenSCMProfileIsNotSet() {
    setUserPrincipal(USER);

    userProfileHelper.getScmUserName(ACCOUNT_ID, SCMType.GITHUB);
  }

  private void setUserPrincipal(String userName) {
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new UserPrincipal(userName, "DUMMY_USER_EMAIL", userName, ACCOUNT_ID));
  }

  private BitbucketSCMDTO getBitBucketSCMDTO() {
    BitbucketAuthenticationDTO bitbucketAuthenticationDTO =
        BitbucketAuthenticationDTO.builder()
            .authType(GitAuthType.HTTP)
            .credentials(BitbucketHttpCredentialsDTO.builder()
                             .httpCredentialsSpec(BitbucketUsernamePasswordDTO.builder()
                                                      .username("SCM_USER")
                                                      .passwordRef(SecretRefData.builder().identifier("SECRET").build())
                                                      .build())
                             .build())
            .build();

    return BitbucketSCMDTO.builder()
        .userIdentifier(USER)
        .accountIdentifier(ACCOUNT_ID)
        .name("BITBUCKET_SCM")
        .authentication(bitbucketAuthenticationDTO)
        .build();
  }
}

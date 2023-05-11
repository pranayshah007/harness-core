/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.gitsync.common.dtos.GithubSCMDTO;
import io.harness.gitsync.common.dtos.UserDetailsResponseDTO;
import io.harness.gitsync.common.service.UserSourceCodeManagerService;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class UserSourceCodeManagerHelperTest {
  @Mock UserSourceCodeManagerService userSourceCodeManagerService;
  @InjectMocks UserSourceCodeManagerHelper userSourceCodeManagerHelper;
  GithubSCMDTO githubSCMDTO;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    githubSCMDTO = GithubSCMDTO.builder().userEmail("email").userName("userName").build();
    doReturn(githubSCMDTO).when(userSourceCodeManagerService).getByType("accountId", "userId", SCMType.GITHUB);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFetchUserSourceCodeManagerDTO() {
    MockedStatic<GitSyncUtils> gitSyncUtilsMockedStatic = mockStatic(GitSyncUtils.class);
    gitSyncUtilsMockedStatic.when(GitSyncUtils::getUserIdentifier).thenReturn(Optional.of("userId"));
    assertEquals(
        userSourceCodeManagerHelper.fetchUserSourceCodeManagerDTO("accountId", GithubConnectorDTO.builder().build())
            .get(),
        githubSCMDTO);
    gitSyncUtilsMockedStatic.close();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetUerDetails() {
    MockedStatic<GitSyncUtils> gitSyncUtilsMockedStatic = mockStatic(GitSyncUtils.class);
    gitSyncUtilsMockedStatic.when(GitSyncUtils::getUserIdentifier).thenReturn(Optional.of("userId"));
    UserDetailsResponseDTO userDetailsResponseDTO =
        userSourceCodeManagerHelper.getUserDetails("accountId", GithubConnectorDTO.builder().build()).get();
    assertEquals(userDetailsResponseDTO.getUserName(), "userName");
    assertEquals(userDetailsResponseDTO.getUserEmail(), "email");
    gitSyncUtilsMockedStatic.close();
  }
}

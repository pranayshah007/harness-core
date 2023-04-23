/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.HarnessToGitPushInfoServiceGrpc;
import io.harness.gitsync.UserDetailsResponse;
import io.harness.gitsync.common.beans.UserSourceCodeManager;
import io.harness.gitsync.common.dtos.GithubSCMDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerDTO;
import io.harness.gitsync.common.helper.SCMMapperHelper;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.repositories.userSourceCodeManager.UserSourceCodeManagerRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class UserSourceCodeManagerServiceImplTest extends GitSyncTestBase {
  @Mock private UserSourceCodeManagerRepository userSourceCodeManagerRepository;
  @Inject private SCMMapperHelper scmMapperHelper;
  @Mock
  private HarnessToGitPushInfoServiceGrpc
      .HarnessToGitPushInfoServiceBlockingStub harnessToGitPushInfoServiceBlockingStub;
  @Inject @InjectMocks UserSourceCodeManagerServiceImpl userSourceCodeManagerService;
  UserSourceCodeManagerDTO userSourceCodeManagerDTO;
  String accountIdentifier = "accountId";
  String userIdentifier = "userId";
  SCMType scmType = SCMType.GITHUB;

  @Before
  public void setup() {
    userSourceCodeManagerDTO =
        GithubSCMDTO.builder()
            .accountIdentifier(accountIdentifier)
            .userIdentifier(userIdentifier)
            .userName("user1")
            .userEmail("email")
            .type(scmType)
            .apiAccess(
                GithubApiAccessDTO.builder()
                    .type(GithubApiAccessType.OAUTH)
                    .spec(GithubOauthDTO.builder()
                              .tokenRef(SecretRefData.builder().scope(Scope.ACCOUNT).identifier("tokenRef").build())
                              .build())
                    .build())
            .build();
    UserSourceCodeManager userSourceCodeManager = scmMapperHelper.toEntity(userSourceCodeManagerDTO);
    doReturn(userSourceCodeManager)
        .when(userSourceCodeManagerRepository)
        .findByAccountIdentifierAndUserIdentifierAndType(accountIdentifier, userIdentifier, scmType);
    doReturn(List.of(userSourceCodeManager))
        .when(userSourceCodeManagerRepository)
        .findByAccountIdentifierAndUserIdentifier(accountIdentifier, userIdentifier);
    doReturn(1L)
        .when(userSourceCodeManagerRepository)
        .deleteByAccountIdentifierAndUserIdentifier(accountIdentifier, userIdentifier);
    doReturn(1L)
        .when(userSourceCodeManagerRepository)
        .deleteByAccountIdentifierAndUserIdentifierAndType(accountIdentifier, userIdentifier, scmType);
    doReturn(userSourceCodeManager).when(userSourceCodeManagerRepository).save(userSourceCodeManager);
    on(userSourceCodeManagerService).set("userSourceCodeManagerRepository", userSourceCodeManagerRepository);
    on(userSourceCodeManagerService)
        .set("harnessToGitPushInfoServiceBlockingStub", harnessToGitPushInfoServiceBlockingStub);
    doReturn(UserDetailsResponse.newBuilder().setUserEmail("email").setUserName("user1").build())
        .when(harnessToGitPushInfoServiceBlockingStub)
        .getUserDetails(any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetByType() {
    assertEquals(
        userSourceCodeManagerService.getByType(accountIdentifier, userIdentifier, scmType), userSourceCodeManagerDTO);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testSave() {
    assertEquals(userSourceCodeManagerService.save(userSourceCodeManagerDTO), userSourceCodeManagerDTO);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGet() {
    assertEquals(
        userSourceCodeManagerService.get(accountIdentifier, userIdentifier), List.of(userSourceCodeManagerDTO));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testDelete() {
    assertEquals(1, userSourceCodeManagerService.delete(accountIdentifier, userIdentifier, null));
    assertEquals(1, userSourceCodeManagerService.delete(accountIdentifier, userIdentifier, scmType));
  }
}

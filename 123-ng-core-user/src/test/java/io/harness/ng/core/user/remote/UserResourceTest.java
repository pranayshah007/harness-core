/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.SAHIBA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.NGRemoveUserFilter;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.entities.UserMetadata;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.repositories.user.spring.UserMetadataRepository;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(PL)
public class UserResourceTest extends CategoryTest {
  private static final String ACCOUNT = "account";
  @Mock private NgUserService ngUserService;
  @Mock private ProjectService projectService;
  @Mock private UserMetadataRepository userMetadataRepository;

  @Mock private AccessControlClient accessControlClient;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Spy @Inject @InjectMocks private UserResource userResource;

  @Before
  public void setup() throws NoSuchFieldException {
    initMocks(this);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetUserProjectInfo() {
    PageRequest pageRequest = PageRequest.builder().build();
    PageResponse<ProjectDTO> page = PageResponse.<ProjectDTO>builder()
                                        .content(Arrays.asList(ProjectDTO.builder().identifier("id").build()))
                                        .totalPages(10)
                                        .build();
    doReturn(page).when(projectService).listProjectsForUser(any(), eq(ACCOUNT), eq(pageRequest));
    doReturn(Optional.of("userid")).when(userResource).getUserIdentifierFromSecurityContext();
    ResponseDTO<PageResponse<ProjectDTO>> response = userResource.getUserProjectInfo(ACCOUNT, pageRequest);
    assertThat(response).isNotNull();
    PageResponse<ProjectDTO> data = response.getData();
    assertThat(data.getContent()).isEqualTo(page.getContent());
    assertThat(data.getTotalPages()).isEqualTo(10);
    verify(projectService).listProjectsForUser(any(), eq(ACCOUNT), eq(pageRequest));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testRemoveExternallyManagedUser() {
    String userId = randomAlphabetic(10);
    Optional<UserInfo> userInfo = Optional.ofNullable(UserInfo.builder().externallyManaged(true).uuid(userId).build());
    doReturn(userInfo).when(ngUserService).getUserByIdAndAccount(userId, ACCOUNT);
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(ACCOUNT, FeatureName.PL_USER_DELETION_V2);

    userResource.removeUser(userId, ACCOUNT, null, null);

    verify(userResource).removeUserInternal(userId, ACCOUNT, null, null, NGRemoveUserFilter.ACCOUNT_LAST_ADMIN_CHECK);
    verify(ngUserService).removeUser(userId, ACCOUNT);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testDeleteCGUserWhenAllScopeInNGIsDeleted() {
    String userId = randomAlphabetic(10);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    doReturn(true).when(ngUserService).removeUserFromScope(anyString(), any(), any(), any());
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(ACCOUNT, FeatureName.PL_USER_DELETION_V2);
    doReturn(false).when(ngUserService).isUserAtScope(anyString(), any());
    userResource.removeUser(userId, ACCOUNT, null, null);
    verify(userResource).removeUserInternal(userId, ACCOUNT, null, null, NGRemoveUserFilter.ACCOUNT_LAST_ADMIN_CHECK);
    verify(ngUserService).removeUser(userId, ACCOUNT);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testUserMetadataDelete() {
    String userId = randomAlphabetic(10);
    Optional<UserMetadata> userMetadata = Optional.ofNullable(UserMetadata.builder().userId(userId).build());
    doReturn(userMetadata).when(userMetadataRepository).findDistinctByUserId(userId);
    userResource.deleteUserMetadata(userId);
    verify(userResource).deleteUserMetadata(userId);
    verify(ngUserService).deleteUserMetadata(userId);
  }
}

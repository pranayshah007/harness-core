/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.remote;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.joor.Reflect.on;
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
import io.harness.gitsync.common.dtos.GithubSCMDTO;
import io.harness.gitsync.common.dtos.GithubSCMRequestDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerResponseDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerResponseDTOList;
import io.harness.gitsync.common.mappers.UserSourceCodeManagerMapper;
import io.harness.gitsync.common.service.UserSourceCodeManagerService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class UserSourceCodeManagerResourceTest extends GitSyncTestBase {
  @Mock UserSourceCodeManagerService userSourceCodeManagerService;

  @Inject Map<SCMType, UserSourceCodeManagerMapper> scmMapBinder;
  @Inject @InjectMocks UserSourceCodeManagerResource userSourceCodeManagerResource;
  String accountIdentifier = "accountId";
  String userIdentifier = "userId";
  SCMType scmType = SCMType.GITHUB;
  UserSourceCodeManagerDTO userSourceCodeManagerDTO;

  @Before
  public void setup() {
    userSourceCodeManagerDTO =
        GithubSCMDTO.builder()
            .accountIdentifier(accountIdentifier)
            .userIdentifier(userIdentifier)
            .type(scmType)
            .apiAccess(
                GithubApiAccessDTO.builder()
                    .type(GithubApiAccessType.OAUTH)
                    .spec(GithubOauthDTO.builder()
                              .tokenRef(SecretRefData.builder().scope(Scope.ACCOUNT).identifier("tokenRef").build())
                              .build())
                    .build())
            .build();
    doReturn(userSourceCodeManagerDTO)
        .when(userSourceCodeManagerService)
        .getByType(accountIdentifier, userIdentifier, scmType);
    doReturn(List.of(userSourceCodeManagerDTO))
        .when(userSourceCodeManagerService)
        .get(accountIdentifier, userIdentifier);
    doReturn(userSourceCodeManagerDTO).when(userSourceCodeManagerService).save(userSourceCodeManagerDTO);
    on(userSourceCodeManagerResource).set("userSourceCodeManagerService", userSourceCodeManagerService);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGet() {
    ResponseDTO<UserSourceCodeManagerResponseDTOList> responseDTO =
        userSourceCodeManagerResource.get(accountIdentifier, userIdentifier, scmType);
    assertEquals(responseDTO.getData().getUserSourceCodeManagerResponseDTOList().size(), 1);
    assertEquals(responseDTO.getData().getUserSourceCodeManagerResponseDTOList().get(0),
        scmMapBinder.get(SCMType.GITHUB).toResponseDTO(userSourceCodeManagerDTO));
    ResponseDTO<UserSourceCodeManagerResponseDTOList> responseDTO1 =
        userSourceCodeManagerResource.get(accountIdentifier, userIdentifier, null);
    assertEquals(responseDTO1.getData().getUserSourceCodeManagerResponseDTOList().size(), 1);
    assertEquals(responseDTO1.getData().getUserSourceCodeManagerResponseDTOList().get(0),
        scmMapBinder.get(SCMType.GITHUB).toResponseDTO(userSourceCodeManagerDTO));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testSave() {
    ResponseDTO<UserSourceCodeManagerResponseDTO> responseDTO = userSourceCodeManagerResource.save(
        GithubSCMRequestDTO.builder()
            .apiAccess(
                GithubApiAccessDTO.builder()
                    .type(GithubApiAccessType.OAUTH)
                    .spec(GithubOauthDTO.builder()
                              .tokenRef(SecretRefData.builder().identifier("tokenRef").scope(Scope.ACCOUNT).build())
                              .build())
                    .build())
            .build());
    assertEquals(responseDTO.getData(), scmMapBinder.get(SCMType.GITHUB).toResponseDTO(userSourceCodeManagerDTO));
  }
}

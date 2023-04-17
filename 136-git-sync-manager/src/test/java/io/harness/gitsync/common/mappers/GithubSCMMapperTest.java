/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.mappers;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.githubconnector.GithubApiAccess;
import io.harness.connector.entities.embedded.githubconnector.GithubOauth;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.beans.GithubSCM;
import io.harness.gitsync.common.dtos.GithubSCMDTO;
import io.harness.gitsync.common.dtos.GithubSCMRequestDTO;
import io.harness.gitsync.common.dtos.GithubSCMResponseDTO;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class GithubSCMMapperTest extends GitSyncTestBase {
  @Inject private Map<SCMType, UserSourceCodeManagerMapper> scmMapBinder;
  GithubSCMMapper githubSCMMapper;
  GithubApiAccessSpecDTO githubApiAccessSpecDTO;
  GithubApiAccessDTO githubApiAccessDTO;
  GithubSCMDTO githubSCMDTO;
  GithubApiAccess githubApiAccess;
  GithubSCM githubSCM;
  GithubSCM githubSCMInternal;
  GithubSCMRequestDTO githubSCMRequestDTO;
  GithubSCMResponseDTO githubSCMResponseDTO;
  String accountIdentifier = "accountId";
  SCMType scmType = SCMType.GITHUB;
  String userIdentifier = "userId";

  @Before
  public void setup() {
    githubSCMMapper = (GithubSCMMapper) scmMapBinder.get(SCMType.GITHUB);
    githubApiAccessSpecDTO = GithubOauthDTO.builder()
                                 .tokenRef(SecretRefData.builder().identifier("tokenRef").scope(Scope.ACCOUNT).build())
                                 .build();
    githubApiAccessDTO =
        GithubApiAccessDTO.builder().type(GithubApiAccessType.OAUTH).spec(githubApiAccessSpecDTO).build();
    githubSCMDTO = GithubSCMDTO.builder()
                       .accountIdentifier(accountIdentifier)
                       .userIdentifier(userIdentifier)
                       .type(scmType)
                       .apiAccess(githubApiAccessDTO)
                       .build();
    githubApiAccess = GithubOauth.builder().tokenRef("account.tokenRef").build();
    githubSCMInternal =
        GithubSCM.builder().apiAccessType(GithubApiAccessType.OAUTH).githubApiAccess(githubApiAccess).build();
    githubSCM = GithubSCM.builder()
                    .accountIdentifier(accountIdentifier)
                    .userIdentifier(userIdentifier)
                    .type(scmType)
                    .apiAccessType(GithubApiAccessType.OAUTH)
                    .githubApiAccess(githubApiAccess)
                    .build();
    githubSCMRequestDTO = GithubSCMRequestDTO.builder()
                              .accountIdentifier(accountIdentifier)
                              .userIdentifier(userIdentifier)
                              .type(scmType)
                              .apiAccess(githubApiAccessDTO)
                              .build();
    githubSCMResponseDTO = GithubSCMResponseDTO.builder()
                               .accountIdentifier(accountIdentifier)
                               .userIdentifier(userIdentifier)
                               .type(scmType)
                               .apiAccess(githubApiAccessDTO)
                               .build();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToEntityInternal() {
    assertEquals(githubSCMMapper.toEntityInternal(githubSCMDTO), githubSCMInternal);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToDTOInternal() {
    assertEquals(githubSCMMapper.toDTOInternal(githubSCM), githubSCMDTO);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToResponseDTOInternal() {
    assertEquals(githubSCMMapper.toResponseDTO(githubSCMDTO), githubSCMResponseDTO);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToServiceDTOInternal() {
    assertEquals(githubSCMMapper.toServiceDTO(githubSCMRequestDTO), githubSCMDTO);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToApiAccess() {
    assertEquals(githubSCMMapper.toApiAccess(githubApiAccessSpecDTO, GithubApiAccessType.OAUTH), githubApiAccess);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToApiAccessDTO() {
    assertEquals(githubApiAccessDTO, githubSCMMapper.toApiAccessDTO(GithubApiAccessType.OAUTH, githubApiAccess));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToEntity() {
    assertEquals(githubSCM, githubSCMMapper.toEntity(githubSCMDTO));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToDTO() {
    assertEquals(githubSCMDTO, githubSCMMapper.toDTO(githubSCM));
  }
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToServiceDTO() {
    assertEquals(githubSCMDTO, githubSCMMapper.toServiceDTO(githubSCMRequestDTO));
  }
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToResponseDTO() {
    assertEquals(githubSCMResponseDTO, githubSCMMapper.toResponseDTO(githubSCMDTO));
  }
}

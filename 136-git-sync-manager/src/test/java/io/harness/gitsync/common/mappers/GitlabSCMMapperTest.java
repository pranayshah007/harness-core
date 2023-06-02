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
import io.harness.connector.entities.embedded.gitlabconnector.GitlabApiAccess;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabOauth;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabOauthDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.beans.GitlabSCM;
import io.harness.gitsync.common.dtos.GitlabAuthenticationDTO;
import io.harness.gitsync.common.dtos.GitlabSCMDTO;
import io.harness.gitsync.common.dtos.GitlabSCMRequestDTO;
import io.harness.gitsync.common.dtos.GitlabSCMResponseDTO;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitlabSCMMapperTest extends GitSyncTestBase {
  @Inject private Map<SCMType, UserSourceCodeManagerMapper> scmMapBinder;
  GitlabSCMMapper gitlabSCMMapper;
  GitlabApiAccessSpecDTO gitlabApiAccessSpecDTO;
  GitlabApiAccessDTO gitlabApiAccessDTO;
  GitlabSCMDTO gitlabSCMDTO;
  GitlabApiAccess gitlabApiAccess;
  GitlabSCM gitlabSCM;

  @Before
  public void setup() {
    gitlabSCMMapper = (GitlabSCMMapper) scmMapBinder.get(SCMType.GITLAB);
    gitlabApiAccessSpecDTO =
        GitlabOauthDTO.builder()
            .tokenRef(SecretRefData.builder().identifier("tokenRef").scope(Scope.ACCOUNT).build())
            .refreshTokenRef(SecretRefData.builder().identifier("refreshTokenRef").scope(Scope.ACCOUNT).build())
            .build();
    gitlabApiAccessDTO =
        GitlabApiAccessDTO.builder().type(GitlabApiAccessType.OAUTH).spec(gitlabApiAccessSpecDTO).build();
    gitlabSCMDTO = GitlabSCMDTO.builder().apiAccess(gitlabApiAccessDTO).build();
    gitlabApiAccess =
        GitlabOauth.builder().tokenRef("account.tokenRef").refreshTokenRef("account.refreshTokenRef").build();
    gitlabSCM = GitlabSCM.builder().apiAccessType(GitlabApiAccessType.OAUTH).gitlabApiAccess(gitlabApiAccess).build();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToEntityInternal() {
    assertEquals(gitlabSCM, gitlabSCMMapper.toEntityInternal(gitlabSCMDTO));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToDTOInternal() {
    assertEquals(gitlabSCMMapper.toDTOInternal(gitlabSCM), gitlabSCMDTO);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToResponseDTOInternal() {
    assertEquals(gitlabSCMMapper.toResponseDTO(gitlabSCMDTO),
        GitlabSCMResponseDTO.builder().apiAccess(gitlabApiAccessDTO).build());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToServiceDTOInternal() {
    assertEquals(gitlabSCMMapper.toServiceDTO(
                     GitlabSCMRequestDTO.builder()
                         .authentication(GitlabAuthenticationDTO.builder().apiAccessDTO(gitlabApiAccessDTO).build())
                         .build()),
        gitlabSCMDTO);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToApiAccess() {
    assertEquals(gitlabSCMMapper.toApiAccess(gitlabApiAccessSpecDTO, GitlabApiAccessType.OAUTH), gitlabApiAccess);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToApiAccessDTO() {
    assertEquals(gitlabApiAccessDTO, gitlabSCMMapper.toApiAccessDTO(GitlabApiAccessType.OAUTH, gitlabApiAccess));
  }
}

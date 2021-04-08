package io.harness.gitsync.common.remote;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.beans.GitFileLocation;
import io.harness.gitsync.common.dtos.GitSyncEntityListDTO;
import io.harness.gitsync.common.impl.GitEntityServiceImpl;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.repositories.gitFileLocation.GitFileLocationRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(DX)
public class GitEntityResourceTest extends GitSyncTestBase {
  @Inject GitEntityResource gitEntityResource;
  @Inject GitFileLocationRepository gitFileLocationRepository;
  @Inject GitEntityServiceImpl gitEntityService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testListByType() {
    final String gitSyncConfigId = "repo";
    final String branch = "branch";
    final String accountId = "accountId";
    final String pipeline = EntityType.PIPELINES.name();
    final String id = "id";
    final String connector = EntityType.CONNECTORS.name();
    final String id1 = "id1";
    final GitFileLocation gitFileLocation = buildGitFileLocation(gitSyncConfigId, branch, accountId, pipeline, id);
    final GitFileLocation gitFileLocation1 = buildGitFileLocation(gitSyncConfigId, branch, accountId, connector, id1);
    gitFileLocationRepository.saveAll(Arrays.asList(gitFileLocation, gitFileLocation, gitFileLocation1));

    final ResponseDTO<PageResponse<GitSyncEntityListDTO>> ngPageResponseResponseDTO =
        gitEntityResource.listByType(null, null, accountId, "repo", "branch", EntityType.PIPELINES, 0, 5, "cd");
    final PageResponse<GitSyncEntityListDTO> data = ngPageResponseResponseDTO.getData();
    assertThat(data).isNotNull();
    assertThat(data.getContent()
                   .stream()
                   .flatMap(gitSyncEntityListDTO -> gitSyncEntityListDTO.getGitSyncEntities().stream())
                   .collect(Collectors.toList())
                   .size())
        .isEqualTo(2);
  }

  public GitFileLocation buildGitFileLocation(
      String gitSyncConfigId, String branch, String accountId, String pipeline, String id) {
    return GitFileLocation.builder()
        .gitSyncConfigId(gitSyncConfigId)
        .branch(branch)
        .entityType(pipeline)
        .entityIdentifier(id)
        .accountId(accountId)
        .scope(Scope.ACCOUNT)
        .isDefault(true)
        .build();
  }
}

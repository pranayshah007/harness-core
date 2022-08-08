/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.service;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.validation.Create;
import io.harness.validation.Update;

import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

@OwnedBy(DX)
public interface YamlGitConfigService {
  Optional<ConnectorInfoDTO> getGitConnector(IdentifierRef identifierRef);

  List<YamlGitConfigDTO> getByConnectorRepoAndBranch(
      String gitConnectorId, String repo, String branchName, String accountId);

  YamlGitConfigDTO get(String projectId, String orgId, String accountId, String identifier);

  List<YamlGitConfigDTO> list(String projectIdentifier, String orgIdentifier, String accountId);

  YamlGitConfigDTO updateDefault(
      String projectIdentifier, String orgId, String accountId, String Id, String folderPath);

  @ValidationGroups(Create.class) YamlGitConfigDTO save(@Valid YamlGitConfigDTO yamlGitConfig);

  @ValidationGroups(Update.class) YamlGitConfigDTO update(@Valid YamlGitConfigDTO yamlGitConfig);

  boolean isGitSyncEnabled(String accountIdentifier, String organizationIdentifier, String projectIdentifier);

  Boolean isRepoExists(String repo);

  List<YamlGitConfigDTO> getByAccountAndRepo(String accountIdentifier, String repo);

  YamlGitConfigDTO getByProjectIdAndRepo(String accountId, String orgId, String projectId, String repo);
  Optional<YamlGitConfigDTO> getByProjectIdAndRepoOptional(
      String accountId, String orgId, String projectId, String repo);

  boolean deleteAll(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  void updateTheConnectorRepoAndBranch(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String yamlGitConfigIdentifier, String repo, String branch);

  void deleteAllEntities(String accountIdentifier, String orgIdentifier, String projectIdentifier);
}

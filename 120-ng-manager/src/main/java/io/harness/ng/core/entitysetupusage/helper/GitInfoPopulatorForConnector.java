/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.NextGenModule.CONNECTOR_DECORATOR_SERVICE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.services.ConnectorService;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccess;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

@OwnedBy(HarnessTeam.DX)
@Slf4j
@Singleton
public class GitInfoPopulatorForConnector {
  ConnectorService connectorService;

  @Inject
  public GitInfoPopulatorForConnector(@Named(CONNECTOR_DECORATOR_SERVICE) ConnectorService connectorService) {
    this.connectorService = connectorService;
  }

  public void populateRepoAndBranchForConnector(
      List<EntityDetail> connectorEntities, String branch, String repo, Boolean isReferredByBranchDefault) {
    // todo @deepak: When the account and org level git sync is enabled, this code
    // needs to be changed
    if (isEmpty(connectorEntities)) {
      return;
    }
    final List<EntityReference> connectorRefs =
        connectorEntities.stream().map(EntityDetail::getEntityRef).collect(Collectors.toList());
    final List<EntityReference> projectLevelConnectors =
        connectorRefs.stream().filter(ref -> isNotEmpty(ref.getProjectIdentifier())).collect(Collectors.toList());

    // Assumption: Since someone is referring this connectors, he/she will be referring from the same project
    if (isEmpty(projectLevelConnectors)) {
      return;
    }
    EntityReference entityReference = projectLevelConnectors.get(0);
    String accountId = entityReference.getAccountIdentifier();
    String orgId = entityReference.getOrgIdentifier();
    String projectId = entityReference.getProjectIdentifier();
    List<String> connectorIdentifiers =
        projectLevelConnectors.stream().map(NGAccess::getIdentifier).collect(Collectors.toList());
    List<ConnectorResponseDTO> connectorResponseDTOS =
        getConnectorsList(accountId, orgId, projectId, connectorIdentifiers, repo, branch);
    populateRepoAndBranchInternal(
        connectorResponseDTOS, projectLevelConnectors, accountId, repo, isReferredByBranchDefault);
  }

  private void populateRepoAndBranchInternal(List<ConnectorResponseDTO> connectorResponseDTOS,
      List<EntityReference> projectLevelConnectors, String accountId, String referredEntityRepo,
      Boolean isReferredByBranchDefault) {
    if (isEmpty(connectorResponseDTOS)) {
      return;
    }
    Map<String, ConnectorResponseDTO> fqnConnectorMap = new HashMap<>();
    for (ConnectorResponseDTO connectorResponseDTO : connectorResponseDTOS) {
      ConnectorInfoDTO connector = connectorResponseDTO.getConnector();
      String fqn = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
          accountId, connector.getOrgIdentifier(), connector.getProjectIdentifier(), connector.getIdentifier());
      fqnConnectorMap.put(fqn, connectorResponseDTO);
    }

    for (EntityReference entityReference : projectLevelConnectors) {
      String fqn = entityReference.getFullyQualifiedName();
      if (fqnConnectorMap.containsKey(fqn)) {
        ConnectorResponseDTO connectorResponseDTO = fqnConnectorMap.get(fqn);
        EntityGitDetails gitDetails = connectorResponseDTO.getGitDetails();
        if (gitDetails != null && gitDetails.getRepoIdentifier() != null) {
          String repo = gitDetails.getRepoIdentifier();
          String branch = gitDetails.getBranch();
          boolean isDefault = true;
          if (referredEntityRepo.equals(repo)) {
            isDefault = isReferredByBranchDefault;
          }
          entityReference.setRepoIdentifier(repo);
          entityReference.setBranch(branch);
          entityReference.setIsDefault(isDefault);
        } else {
          entityReference.setIsDefault(true);
        }
      }
    }
  }

  private List<ConnectorResponseDTO> getConnectorsList(
      String accountId, String orgId, String projectId, List<String> connectorIdentifiers, String repo, String branch) {
    List<ConnectorResponseDTO> connectorResponseDTOS = new ArrayList<>();
    final GitEntityInfo newBranch =
        GitEntityInfo.builder().branch(branch).yamlGitConfigId(repo).findDefaultFromOtherRepos(true).build();
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(newBranch).build());
      Page<ConnectorResponseDTO> connectorPage = null;
      int page = 0;
      int size = 100;
      do {
        connectorPage = connectorService.list(accountId,
            ConnectorFilterPropertiesDTO.builder().connectorIdentifiers(connectorIdentifiers).build(), orgId, projectId,
            null, null, null, false,
            PageUtils.getPageRequest(
                page, size, List.of(ConnectorKeys.lastModifiedAt, Sort.Direction.DESC.toString())));
        if (connectorPage != null && isNotEmpty(connectorPage.getContent())) {
          connectorResponseDTOS.addAll(connectorPage.getContent());
        }
        page++;
      } while (connectorPage != null && isNotEmpty(connectorPage.getContent()));
      return connectorResponseDTOS;
    }
  }
}

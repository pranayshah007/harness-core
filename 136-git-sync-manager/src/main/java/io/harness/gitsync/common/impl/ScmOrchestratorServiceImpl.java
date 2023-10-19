/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.gitsync.GitSyncModule.SCM_ON_DELEGATE;
import static io.harness.gitsync.GitSyncModule.SCM_ON_MANAGER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.exception.ScmInternalServerErrorException;
import io.harness.exception.UnexpectedException;
import io.harness.gitsync.common.dtos.GitSyncSettingsDTO;
import io.harness.gitsync.common.service.GitSyncConnectorService;
import io.harness.gitsync.common.service.GitSyncSettingsService;
import io.harness.gitsync.common.service.ScmClientFacilitatorService;
import io.harness.gitsync.common.service.ScmOrchestratorService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Optional;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@Singleton
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@OwnedBy(DX)
public class ScmOrchestratorServiceImpl implements ScmOrchestratorService {
  GitSyncSettingsService gitSyncSettingsService;
  ScmClientFacilitatorService scmClientManagerService;
  ScmClientFacilitatorService scmClientDelegateService;
  GitSyncConnectorService gitSyncConnectorService;

  @Inject
  public ScmOrchestratorServiceImpl(GitSyncSettingsService gitSyncSettingsService,
      @Named(SCM_ON_MANAGER) ScmClientFacilitatorService scmClientManagerService,
      @Named(SCM_ON_DELEGATE) ScmClientFacilitatorService scmClientDelegateService,
      GitSyncConnectorService gitSyncConnectorService) {
    this.gitSyncSettingsService = gitSyncSettingsService;
    this.scmClientManagerService = scmClientManagerService;
    this.scmClientDelegateService = scmClientDelegateService;
    this.gitSyncConnectorService = gitSyncConnectorService;
  }

  @Override
  public <R> R processScmRequest(Function<ScmClientFacilitatorService, R> scmRequest, String projectIdentifier,
      String orgIdentifier, String accountId) {
    final boolean executeOnDelegate = isExecuteOnDelegate(projectIdentifier, orgIdentifier, accountId);
    if (executeOnDelegate) {
      return scmRequest.apply(scmClientDelegateService);
    }
    return scmRequest.apply(scmClientManagerService);
  }

  @Override
  public boolean isExecuteOnDelegate(String projectIdentifier, String orgIdentifier, String accountId) {
    final Optional<GitSyncSettingsDTO> gitSyncSettingsDTO =
        gitSyncSettingsService.get(accountId, orgIdentifier, projectIdentifier);
    GitSyncSettingsDTO gitSyncSettings = gitSyncSettingsDTO.orElse(GitSyncSettingsDTO.builder()
                                                                       .accountIdentifier(accountId)
                                                                       .projectIdentifier(projectIdentifier)
                                                                       .orgIdentifier(orgIdentifier)
                                                                       .executeOnDelegate(true)
                                                                       .build());
    return gitSyncSettings.isExecuteOnDelegate();
  }

  @Override
  public <R> R processScmRequestUsingManager(Function<ScmClientFacilitatorService, R> scmRequest) {
    return scmRequest.apply(scmClientManagerService);
  }

  @Override
  public <R> R processScmRequestUsingDelegate(Function<ScmClientFacilitatorService, R> scmRequest) {
    return scmRequest.apply(scmClientDelegateService);
  }

  @Override
  public <R> R processScmRequestUsingConnectorSettings(Function<ScmClientFacilitatorService, R> scmRequest,
      String projectIdentifier, String orgIdentifier, String accountId, String connectorIdentifierRef,
      String connectorRepo, String connectorBranch) {
    final ScmConnector scmConnector = gitSyncConnectorService.getScmConnector(
        accountId, orgIdentifier, projectIdentifier, connectorIdentifierRef, connectorRepo, connectorBranch);
    if (scmConnector instanceof ManagerExecutable) {
      final Boolean executeOnDelegate = ((ManagerExecutable) scmConnector).getExecuteOnDelegate();
      if (executeOnDelegate == Boolean.FALSE) {
        return scmRequest.apply(scmClientManagerService);
      }
    }
    return scmRequest.apply(scmClientDelegateService);
  }

  @Override
  public <R> R processScmRequestUsingConnectorSettings(Function<ScmClientFacilitatorService, R> scmRequest,
      String projectIdentifier, String orgIdentifier, String accountId, String connectorIdentifierRef) {
    final ScmConnector scmConnector =
        gitSyncConnectorService.getScmConnector(accountId, orgIdentifier, projectIdentifier, connectorIdentifierRef);
    return processScmRequestUsingConnectorSettings(scmRequest, scmConnector);
  }

  @Override
  public <R> R processScmRequestUsingConnectorSettings(
      Function<ScmClientFacilitatorService, R> scmRequest, ScmConnector scmConnector) {
    try {
      if (scmConnector instanceof ManagerExecutable) {
        final Boolean executeOnDelegate = ((ManagerExecutable) scmConnector).getExecuteOnDelegate();
        if (executeOnDelegate == Boolean.FALSE) {
          return scmRequest.apply(scmClientManagerService);
        }
      }
      return scmRequest.apply(scmClientDelegateService);
    } catch (UnexpectedException ex) {
      throw new ScmInternalServerErrorException(ex.getMessage());
    }
  }
}

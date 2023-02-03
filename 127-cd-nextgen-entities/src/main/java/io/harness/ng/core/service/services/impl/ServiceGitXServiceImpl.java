/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.services.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.DuplicateFileImportException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.InvalidFieldsDTO;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.SCMGitSyncHelper;
import io.harness.ng.core.service.dto.ServiceImportRequestDTO;
import io.harness.ng.core.service.services.ServiceGitXService;
import io.harness.pms.yaml.YAMLMetadataFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.repositories.service.spring.ServiceRepository;

import software.wings.features.utils.ServiceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDC)
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class ServiceGitXServiceImpl implements ServiceGitXService {
  SCMGitSyncHelper scmGitSyncHelper;
  GitSyncSdkService gitSyncSdkService;
  GitAwareEntityHelper gitAwareEntityHelper;
  ServiceRepository serviceRepository;

  @Override
  public boolean isNewGitXEnabled(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (projectIdentifier != null) {
      return isGitSimplificationEnabledForAProject(accountIdentifier, orgIdentifier, projectIdentifier);
    } else {
      return true;
    }
  }

  @Override
  public String checkForFileUniquenessAndGetRepoURL(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceIdentifier, boolean isForceImport) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    String repoURL = gitAwareEntityHelper.getRepoUrl(accountIdentifier, orgIdentifier, projectIdentifier);

    if (isForceImport) {
      log.info("Importing YAML forcefully with Service Id: {}, RepoURl: {}, FilePath: {}", serviceIdentifier, repoURL,
          gitEntityInfo.getFilePath());
    } else if (isAlreadyImported(accountIdentifier, repoURL, gitEntityInfo.getFilePath())) {
      String error = "The Requested YAML with Service Id: " + serviceIdentifier + ", RepoURl: " + repoURL
          + ", FilePath: " + gitEntityInfo.getFilePath() + " has already been imported.";
      throw new DuplicateFileImportException(error);
    }
    return repoURL;
  }

  @Override
  public String importServiceFromRemote(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    io.harness.beans.Scope scope = io.harness.beans.Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    GitContextRequestParams gitContextRequestParams = GitContextRequestParams.builder()
                                                          .branchName(gitEntityInfo.getBranch())
                                                          .connectorRef(gitEntityInfo.getConnectorRef())
                                                          .filePath(gitEntityInfo.getFilePath())
                                                          .repoName(gitEntityInfo.getRepoName())
                                                          .build();
    return gitAwareEntityHelper.fetchYAMLFromRemote(scope, gitContextRequestParams, Collections.emptyMap());
  }

  @Override
  public void performImportFlowYamlValidations(String orgIdentifier, String projectIdentifier, String serviceIdentifier,
      ServiceImportRequestDTO serviceImportRequest, String importedService) {
    YamlField serviceInnerField =
        ServiceUtils.getServiceYamlFieldElseThrow(orgIdentifier, projectIdentifier, serviceIdentifier, importedService);

    Map<String, String> changedFields = new HashMap<>();

    String identifierFromGit = serviceInnerField.getNode().getIdentifier();
    if (!serviceIdentifier.equals(identifierFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.IDENTIFIER, identifierFromGit);
    }

    String nameFromGit = serviceInnerField.getNode().getName();
    if (!EmptyPredicate.isEmpty(serviceImportRequest.getServiceName())
        && !serviceImportRequest.getServiceName().equals(nameFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.NAME, nameFromGit);
    }

    if (!changedFields.isEmpty()) {
      InvalidFieldsDTO invalidFields = InvalidFieldsDTO.builder().expectedValues(changedFields).build();
      throw new InvalidRequestException(
          "Requested metadata params do not match the values found in the YAML on Git for these fields: "
              + changedFields.keySet(),
          invalidFields);
    }
  }

  private boolean isGitSimplificationEnabledForAProject(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return gitSyncSdkService.isGitSimplificationEnabled(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  private boolean isAlreadyImported(String accountIdentifier, String repoURL, String filePath) {
    Long totalInstancesOfYAML = countFileInstances(accountIdentifier, repoURL, filePath);
    return totalInstancesOfYAML > 0;
  }

  private Long countFileInstances(String accountIdentifier, String repoURL, String filePath) {
    return serviceRepository.count();
  }

  private Criteria getCriteriaForFileUniquenessCheck(String accountId, String repoURl, String filePath) {
    return Criteria.where(ServiceEntityKeys.accountId)
        .is(accountId)
        .and(ServiceEntityKeys.repoURL)
        .is(repoURl)
        .and(ServiceEntityKeys.filePath)
        .is(filePath);
  }
}

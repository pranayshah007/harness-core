/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.DuplicateFileImportException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.InvalidFieldsDTO;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.SCMGitSyncHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YAMLMetadataFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.repositories.NGTemplateRepository;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.resources.beans.TemplateImportRequestDTO;
import io.harness.template.utils.TemplateUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class TemplateGitXServiceImpl implements TemplateGitXService {
  SCMGitSyncHelper scmGitSyncHelper;
  GitSyncSdkService gitSyncSdkService;

  NGTemplateRepository templateRepository;

  GitAwareEntityHelper gitAwareEntityHelper;

  public boolean isNewGitXEnabledAndIsRemoteEntity(TemplateEntity templateToSave, GitEntityInfo gitEntityInfo) {
    return isNewGitXEnabled(templateToSave.getAccountIdentifier(), templateToSave.getOrgIdentifier(),
               templateToSave.getProjectIdentifier())
        && (TemplateUtils.isRemoteEntity(gitEntityInfo) || StoreType.REMOTE.equals(templateToSave.getStoreType()));
  }

  @Override
  public boolean isNewGitXEnabled(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (projectIdentifier != null) {
      return isGitSimplificationEnabledForAProject(accountIdentifier, orgIdentifier, projectIdentifier);
    } else {
      return true;
    }
  }

  public String checkForFileUniquenessAndGetRepoURL(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, boolean isForceImport) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    String repoURL = gitAwareEntityHelper.getRepoUrl(accountIdentifier, orgIdentifier, projectIdentifier);

    if (isForceImport) {
      log.info("Importing YAML forcefully with Template Id: {}, RepoURl: {}, FilePath: {}", templateIdentifier, repoURL,
          gitEntityInfo.getFilePath());
    } else if (isAlreadyImported(accountIdentifier, repoURL, gitEntityInfo.getFilePath())) {
      String error = "The Requested YAML with Template Id: " + templateIdentifier + ", RepoURl: " + repoURL
          + ", FilePath: " + gitEntityInfo.getFilePath() + " has already been imported.";
      throw new DuplicateFileImportException(error);
    }
    return repoURL;
  }

  public String importTemplateFromRemote(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    GitContextRequestParams gitContextRequestParams = GitContextRequestParams.builder()
                                                          .branchName(gitEntityInfo.getBranch())
                                                          .connectorRef(gitEntityInfo.getConnectorRef())
                                                          .filePath(gitEntityInfo.getFilePath())
                                                          .repoName(gitEntityInfo.getRepoName())
                                                          .build();
    return gitAwareEntityHelper.fetchYAMLFromRemote(scope, gitContextRequestParams, Collections.emptyMap());
  }

  public void performImportFlowYamlValidations(String orgIdentifier, String projectIdentifier,
      String templateIdentifier, TemplateImportRequestDTO templateImportRequest, String importedTemplate) {
    YamlField templateInnerField = TemplateUtils.getTemplateYamlFieldElseThrow(
        orgIdentifier, projectIdentifier, templateIdentifier, importedTemplate);

    Map<String, String> changedFields = new HashMap<>();

    String identifierFromGit = templateInnerField.getNode().getIdentifier();
    if (!templateIdentifier.equals(identifierFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.IDENTIFIER, identifierFromGit);
    }

    String nameFromGit = templateInnerField.getNode().getName();
    if (!EmptyPredicate.isEmpty(templateImportRequest.getTemplateName())
        && !templateImportRequest.getTemplateName().equals(nameFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.NAME, nameFromGit);
    }

    String orgIdentifierFromGit = templateInnerField.getNode().getStringValue(YAMLFieldNameConstants.ORG_IDENTIFIER);
    if (orgIdentifier != null && orgIdentifierFromGit != null) {
      if (!orgIdentifier.equals(orgIdentifierFromGit)) {
        changedFields.put(YAMLMetadataFieldNameConstants.ORG_IDENTIFIER, orgIdentifierFromGit);
      }
    } else if (orgIdentifier == null && orgIdentifierFromGit != null) {
      changedFields.put(YAMLMetadataFieldNameConstants.ORG_IDENTIFIER, orgIdentifierFromGit);
    } else if (orgIdentifier != null) {
      changedFields.put(YAMLMetadataFieldNameConstants.ORG_IDENTIFIER, orgIdentifierFromGit);
    }

    String projectIdentifierFromGit =
        templateInnerField.getNode().getStringValue(YAMLFieldNameConstants.PROJECT_IDENTIFIER);
    if (projectIdentifier != null && projectIdentifierFromGit != null) {
      if (!projectIdentifier.equals(projectIdentifierFromGit)) {
        changedFields.put(YAMLMetadataFieldNameConstants.PROJECT_IDENTIFIER, projectIdentifierFromGit);
      }
    } else if (projectIdentifier == null && projectIdentifierFromGit != null) {
      changedFields.put(YAMLMetadataFieldNameConstants.PROJECT_IDENTIFIER, projectIdentifierFromGit);
    } else if (projectIdentifier != null) {
      changedFields.put(YAMLMetadataFieldNameConstants.PROJECT_IDENTIFIER, projectIdentifierFromGit);
    }

    String templateVersionFromGit =
        templateInnerField.getNode().getStringValue(YAMLFieldNameConstants.TEMPLATE_VERSION);
    if (!(EmptyPredicate.isEmpty(templateImportRequest.getTemplateVersion())
            && EmptyPredicate.isEmpty(templateVersionFromGit))
        && !templateImportRequest.getTemplateVersion().equals(templateVersionFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.TEMPLATE_VERSION, templateVersionFromGit);
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
    Long totalInstancesOfYAML = templateRepository.countFileInstances(accountIdentifier, repoURL, filePath);
    return totalInstancesOfYAML > 0;
  }
}

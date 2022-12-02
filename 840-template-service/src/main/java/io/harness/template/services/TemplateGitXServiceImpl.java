/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.Scope.ScopeBuilder;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.DuplicateFileImportException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.InvalidFieldsDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
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
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.NGTemplateRepository;
import io.harness.template.beans.TemplateImportRequestDTO;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;
import io.harness.template.utils.TemplateUtils;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
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
  NGTemplateFeatureFlagHelperService ngTemplateFeatureFlagHelperService;
  GitSyncSdkService gitSyncSdkService;

  NGTemplateRepository templateRepository;

  GitAwareEntityHelper gitAwareEntityHelper;

  public String getWorkingBranch(String entityRepoURL) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    Scope scope = buildScope(gitEntityInfo);
    String branchName = gitEntityInfo.getBranch();
    if (isParentReferenceEntityNotPresent(gitEntityInfo)) {
      return branchName;
    }
    String parentEntityRepoUrl = getRepoUrl(scope);
    if (gitEntityInfo.isNewBranch()) {
      branchName = gitEntityInfo.getBaseBranch();
    }
    if (null != parentEntityRepoUrl && !parentEntityRepoUrl.equals(entityRepoURL)) {
      branchName = "";
    }
    return branchName;
  }

  private String getRepoUrl(Scope scope) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    if (!GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityRepoUrl())) {
      return gitEntityInfo.getParentEntityRepoUrl();
    }
    String parentEntityRepoUrl = scmGitSyncHelper
                                     .getRepoUrl(scope, gitEntityInfo.getParentEntityRepoName(),
                                         gitEntityInfo.getParentEntityConnectorRef(), Collections.emptyMap())
                                     .getRepoUrl();

    gitEntityInfo.setParentEntityRepoUrl(parentEntityRepoUrl);
    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);

    return parentEntityRepoUrl;
  }

  private Scope buildScope(GitEntityInfo gitEntityInfo) {
    ScopeBuilder scope = Scope.builder();
    if (gitEntityInfo != null) {
      if (!GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityAccountIdentifier())) {
        scope.accountIdentifier(gitEntityInfo.getParentEntityAccountIdentifier());
      }
      if (!GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityOrgIdentifier())) {
        scope.orgIdentifier(gitEntityInfo.getParentEntityOrgIdentifier());
      }
      if (!GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityProjectIdentifier())) {
        scope.projectIdentifier(gitEntityInfo.getParentEntityProjectIdentifier());
      }
    }
    return scope.build();
  }

  private boolean isParentReferenceEntityNotPresent(GitEntityInfo gitEntityInfo) {
    return GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityRepoName())
        && GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityConnectorRef());
  }

  public boolean isNewGitXEnabledAndIsRemoteEntity(TemplateEntity templateToSave, GitEntityInfo gitEntityInfo) {
    return isNewGitXEnabled(templateToSave.getAccountIdentifier(), templateToSave.getOrgIdentifier(),
               templateToSave.getProjectIdentifier())
        && (TemplateUtils.isRemoteEntity(gitEntityInfo) || StoreType.REMOTE.equals(templateToSave.getStoreType()));
  }

  @Override
  public boolean isNewGitXEnabled(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (ngTemplateFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.NG_TEMPLATE_GITX)) {
      if (projectIdentifier != null) {
        return isGitSimplificationEnabledForAProject(accountIdentifier, orgIdentifier, projectIdentifier);
      } else {
        return true;
      }
    }
    return false;
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
    if (EmptyPredicate.isEmpty(importedTemplate)) {
      String errorMessage =
          format("Empty YAML found on Git in branch [%s] for template [%s] under Project[%s], Organization [%s].",
              GitAwareContextHelper.getBranchInRequest(), templateIdentifier, projectIdentifier, orgIdentifier);
      throw buildInvalidYamlException(errorMessage, importedTemplate);
    }
    YamlField templateYamlField;
    try {
      templateYamlField = YamlUtils.readTree(importedTemplate);
    } catch (IOException e) {
      String errorMessage = format("File found on Git in branch [%s] for filepath [%s] is not a YAML.",
          GitAwareContextHelper.getBranchInRequest(), GitAwareContextHelper.getFilepathInRequest());
      throw buildInvalidYamlException(errorMessage, importedTemplate);
    }
    YamlField templateInnerField = templateYamlField.getNode().getField(YAMLFieldNameConstants.TEMPLATE);
    if (templateInnerField == null) {
      String errorMessage = format("File found on Git in branch [%s] for filepath [%s] is not a Template YAML.",
          GitAwareContextHelper.getBranchInRequest(), GitAwareContextHelper.getFilepathInRequest());
      throw buildInvalidYamlException(errorMessage, importedTemplate);
    }

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

  private InvalidYamlException buildInvalidYamlException(String errorMessage, String pipelineYaml) {
    YamlSchemaErrorWrapperDTO errorWrapperDTO =
        YamlSchemaErrorWrapperDTO.builder()
            .schemaErrors(
                Collections.singletonList(YamlSchemaErrorDTO.builder().message(errorMessage).fqn("$.template").build()))
            .build();
    InvalidYamlException invalidYamlException = new InvalidYamlException(errorMessage, errorWrapperDTO);
    invalidYamlException.setYaml(pipelineYaml);
    return invalidYamlException;
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.context.GlobalContext;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ScmException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitx.USER_FLOW;
import io.harness.manage.GlobalContextManager;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.template.entity.TemplateEntity;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.ThreadOperationContextHelper;
import io.harness.yaml.validator.InvalidYamlException;

import java.io.IOException;
import java.util.Collections;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TemplateUtils {
  public static final String TEMPLATE_FIELD_NAME = "template";
  public Scope buildScope(TemplateEntity templateEntity) {
    return Scope.of(templateEntity.getAccountIdentifier(), templateEntity.getOrgIdentifier(),
        templateEntity.getProjectIdentifier());
  }

  public boolean isInlineEntity(GitEntityInfo gitEntityInfo) {
    return StoreType.INLINE.equals(gitEntityInfo.getStoreType()) || gitEntityInfo.getStoreType() == null;
  }

  public boolean isRemoteEntity(GitEntityInfo gitEntityInfo) {
    if (gitEntityInfo == null) {
      return false;
    }
    return StoreType.REMOTE.equals(gitEntityInfo.getStoreType());
  }

  public ScmException getScmException(Throwable ex) {
    while (ex != null) {
      if (ex instanceof ScmException) {
        return (ScmException) ex;
      }
      ex = ex.getCause();
    }
    return null;
  }

  public void setupGitParentEntityDetails(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String repoFromTemplate, String connectorFromTemplate) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    if (null != gitEntityInfo) {
      // Set Parent's Repo
      if (isNotEmpty(repoFromTemplate)) {
        gitEntityInfo.setParentEntityRepoName(repoFromTemplate);
      } else if (!GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getRepoName())) {
        gitEntityInfo.setParentEntityRepoName(gitEntityInfo.getRepoName());
      }

      // Set Parent's ConnectorRef
      if (isNotEmpty(connectorFromTemplate)) {
        gitEntityInfo.setParentEntityConnectorRef(connectorFromTemplate);
      } else if (!GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getConnectorRef())) {
        gitEntityInfo.setParentEntityConnectorRef(gitEntityInfo.getConnectorRef());
      }
      // Set Parent's Org identifier
      if (!GitAwareContextHelper.isNullOrDefault(orgIdentifier)) {
        gitEntityInfo.setParentEntityOrgIdentifier(orgIdentifier);
      }
      // Set Parent's Project Identifier
      if (!GitAwareContextHelper.isNullOrDefault(projectIdentifier)) {
        gitEntityInfo.setParentEntityProjectIdentifier(projectIdentifier);
      }
      gitEntityInfo.setParentEntityAccountIdentifier(accountIdentifier);
      GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);
    }
  }

  @NonNull
  public YamlField getTemplateYamlFieldElseThrow(
      String orgIdentifier, String projectIdentifier, String templateIdentifier, String importedTemplate) {
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
    return templateInnerField;
  }

  private InvalidYamlException buildInvalidYamlException(String errorMessage, String pipelineYaml) {
    YamlSchemaErrorWrapperDTO errorWrapperDTO =
        YamlSchemaErrorWrapperDTO.builder()
            .schemaErrors(
                Collections.singletonList(YamlSchemaErrorDTO.builder().message(errorMessage).fqn("$.template").build()))
            .build();
    return new InvalidYamlException(errorMessage, errorWrapperDTO, pipelineYaml);
  }

  public static YamlNode validateAndGetYamlNode(String yaml, String templateIdentifier) {
    if (isEmpty(yaml)) {
      throw new NGTemplateException(String.format("Template with path %s not found.", templateIdentifier));
    }
    YamlNode yamlNode;
    try {
      yamlNode = YamlUtils.readTree(yaml).getNode();
    } catch (IOException e) {
      throw new NGTemplateException(
          String.format("Could not convert %s template yaml to JsonNode: ", templateIdentifier) + e.getMessage());
    }
    return yamlNode;
  }

  public static YamlNode validateAndGetYamlNode(String yaml) {
    if (isEmpty(yaml)) {
      throw new NGTemplateException("Yaml to applyTemplates cannot be empty.");
    }
    YamlNode yamlNode;
    try {
      yamlNode = YamlUtils.readTree(yaml).getNode();
    } catch (IOException e) {
      throw new NGTemplateException("Could not convert yaml to JsonNode: " + e.getMessage());
    }
    return yamlNode;
  }

  public static IdentifierRef getIdentifierRef(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return IdentifierRefHelper.getIdentifierRefOrThrowException(
        identifier, accountIdentifier, orgIdentifier, projectIdentifier, TEMPLATE_FIELD_NAME);
  }

  public boolean isExecutionFlow() {
    USER_FLOW user_flow = ThreadOperationContextHelper.getThreadOperationContextUserFlow();
    if (user_flow != null) {
      return user_flow.equals(USER_FLOW.EXECUTION);
    }
    return false;
  }

  public void setUserFlowContext(USER_FLOW userFlow) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(
        ThreadOperationContextHelper.getThreadOperationContext().withUserFlow(userFlow));
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmException;
import io.harness.git.model.ChangeType;
import io.harness.ng.core.template.TemplateListType;
import io.harness.repositories.NGGlobalTemplateRepository;
import io.harness.template.entity.GlobalTemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.events.TemplateUpdateEventType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@Singleton
@Slf4j
@OwnedBy(CDC)
public class NGGlobalTemplateServiceHelper {
  private final NGGlobalTemplateRepository ngGlobalTemplateRepository;

  //  public static String TEMPLATE_SAVE = "template_save";
  //  public static String TEMPLATE_SAVE_ACTION_TYPE = "action";
  public static String TEMPLATE_NAME = "templateName";
  public static String TEMPLATE_ID = "templateId";
  public static String ORG_ID = "orgId";
  public static String PROJECT_ID = "projectId";
  public static String MODULE_NAME = "moduleName";

  @Inject
  public NGGlobalTemplateServiceHelper(NGGlobalTemplateRepository templateRepository) {
    this.ngGlobalTemplateRepository = templateRepository;
  }

  private GlobalTemplateEntity makeUpdateCall(GlobalTemplateEntity templateToUpdate,
      GlobalTemplateEntity oldTemplateEntity, ChangeType changeType, String comments,
      TemplateUpdateEventType templateUpdateEventType, boolean skipAudits, boolean makeOnlyDbUpdate) {
    try {
      GlobalTemplateEntity updatedTemplate = null;

      if (makeOnlyDbUpdate) {
        updatedTemplate = ngGlobalTemplateRepository.updateTemplateInDb(
            templateToUpdate, oldTemplateEntity, changeType, comments, templateUpdateEventType, skipAudits);
      }

      if (updatedTemplate == null) {
        throw new InvalidRequestException(format(
            "Unexpected exception occurred while updating template with identifier [%s] and versionLabel [%s], under Project[%s], Organization [%s] could not be updated.",
            templateToUpdate.getIdentifier(), templateToUpdate.getVersionLabel(),
            templateToUpdate.getProjectIdentifier(), templateToUpdate.getOrgIdentifier()));
      }

      return updatedTemplate;
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(
          String.format(
              "Unexpected exception occurred while updating template [%s] and versionLabel [%s], under Project[%s], Organization [%s]",
              templateToUpdate.getIdentifier(), templateToUpdate.getVersionLabel(),
              templateToUpdate.getProjectIdentifier(), templateToUpdate.getOrgIdentifier()),
          e);
      throw e;
    } catch (Exception e) {
      log.error(
          String.format(
              "Unexpected exception occurred while updating template with identifier [%s] and versionLabel [%s], under Project[%s], Organization [%s]",
              templateToUpdate.getIdentifier(), templateToUpdate.getVersionLabel(),
              templateToUpdate.getProjectIdentifier(), templateToUpdate.getOrgIdentifier()),
          e);
      throw new InvalidRequestException(String.format(
          "Unexpected exception occurred while updating template with identifier [%s] and versionLabel [%s], under Project[%s], Organization [%s] : %s",
          templateToUpdate.getIdentifier(), templateToUpdate.getVersionLabel(), templateToUpdate.getProjectIdentifier(),
          templateToUpdate.getOrgIdentifier(), e.getMessage()));
    }
  }
  public Page<GlobalTemplateEntity> listTemplate(String accountIdentifier, Criteria criteria, Pageable pageable) {
    return ngGlobalTemplateRepository.findAll(accountIdentifier, criteria, pageable);
  }

  public Optional<GlobalTemplateEntity> getStableTemplate(
      String templateIdentifier, boolean deleted, boolean getMetadataOnly) {
    return ngGlobalTemplateRepository.findGlobalTemplateByIdentifierAndIsStableAndDeletedNot(
        templateIdentifier, !deleted, getMetadataOnly);
  }

  /* Commenting the method, will be adding the usage in next PR
  public Optional<GlobalTemplateEntity> getGlobalTemplateWithVersionLabel(
      String templateIdentifier, String versionLabel, boolean deleted, boolean getMetadataOnly) {
    return ngGlobalTemplateRepository.findGlobalTemplateByIdentifierAndVersionLabelAndDeletedNot(
        templateIdentifier, versionLabel, !deleted, getMetadataOnly);
  }

  public Page<GlobalTemplateEntity> getGlobalTemplate(
      Criteria criteria, boolean deleted, boolean getMetadataOnly, Pageable pageable) {
    return ngGlobalTemplateRepository.findALLGlobalTemplateAndDeletedNot(!deleted, getMetadataOnly, pageable, criteria);
  }

  public Optional<GlobalTemplateEntity> getGlobalTemplateWithVersionLabel(String accountId, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, String versionLabel, boolean deleted,
      boolean getMetadataOnly, boolean loadFromCache, boolean loadFromFallbackBranch) {
    return ngGlobalTemplateRepository
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNotForGlobalTemplate(
            accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, !deleted, getMetadataOnly,
            loadFromCache, loadFromFallbackBranch);
  }
*/
  public GlobalTemplateEntity makeTemplateUpdateCall(GlobalTemplateEntity templateToUpdate,
      GlobalTemplateEntity oldTemplateEntity, ChangeType changeType, String comments,
      TemplateUpdateEventType templateUpdateEventType, boolean skipAudits) {
    return makeUpdateCall(
        templateToUpdate, oldTemplateEntity, changeType, comments, templateUpdateEventType, skipAudits, true);
  }

  public Criteria formCriteria(Criteria criteria, TemplateListType templateListType) {
    if (templateListType.equals(TemplateListType.LAST_UPDATED_TEMPLATE_TYPE)) {
      return criteria.and(TemplateEntityKeys.isLastUpdatedTemplate).is(true);
    } else if (templateListType.equals(TemplateListType.STABLE_TEMPLATE_TYPE)) {
      return criteria.and(TemplateEntityKeys.isStableTemplate).is(true);
    }
    return criteria;
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.template.beans.NGTemplateConstants.STABLE_VERSION;
import static io.harness.template.beans.NGTemplateConstants.TEMPLATE;
import static io.harness.template.beans.NGTemplateConstants.TEMPLATE_REF;
import static io.harness.template.beans.NGTemplateConstants.TEMPLATE_VERSION_LABEL;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.common.EntityReferenceHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.services.NGTemplateService;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j

/**
 * Class containing common methods w.r.t NGTemplateService and TemplateMergeService
 */
public class TemplateMergeServiceHelper {
  private NGTemplateService templateService;

  // Gets the Template Entity linked to a YAML
  public TemplateEntity getLinkedTemplateEntity(
      String accountId, String orgId, String projectId, JsonNode yaml, Map<String, TemplateEntity> templateCacheMap) {
    String identifier = yaml.get(TEMPLATE_REF).asText();
    String versionLabel = "";
    String versionMarker = STABLE_VERSION;
    if (yaml.get(TEMPLATE_VERSION_LABEL) != null) {
      versionLabel = yaml.get(TEMPLATE_VERSION_LABEL).asText();
      versionMarker = versionLabel;
    }

    IdentifierRef templateIdentifierRef = IdentifierRefHelper.getIdentifierRef(identifier, accountId, orgId, projectId);

    String templateUniqueIdentifier = generateUniqueTemplateIdentifier(templateIdentifierRef.getAccountIdentifier(),
        templateIdentifierRef.getOrgIdentifier(), templateIdentifierRef.getProjectIdentifier(),
        templateIdentifierRef.getIdentifier(), versionMarker);
    if (templateCacheMap.containsKey(templateUniqueIdentifier)) {
      return templateCacheMap.get(templateUniqueIdentifier);
    }

    Optional<TemplateEntity> templateEntity = templateService.getOrThrowExceptionIfInvalid(
        templateIdentifierRef.getAccountIdentifier(), templateIdentifierRef.getOrgIdentifier(),
        templateIdentifierRef.getProjectIdentifier(), templateIdentifierRef.getIdentifier(), versionLabel, false);
    if (!templateEntity.isPresent()) {
      throw new NGTemplateException(String.format(
          "The template identifier %s and version label %s does not exist. Could not replace this template",
          templateIdentifierRef.getIdentifier(), versionLabel));
    }
    TemplateEntity template = templateEntity.get();
    templateCacheMap.put(templateUniqueIdentifier, template);
    return template;
  }

  // Checks if the current Json node is a Template node with fieldName as TEMPLATE and Non-null Value
  public boolean isTemplatePresent(String fieldName, JsonNode templateValue) {
    return TEMPLATE.equals(fieldName) && templateValue.isObject() && templateValue.get(TEMPLATE_REF) != null;
  }

  // Generates a unique Template Identifier
  private String generateUniqueTemplateIdentifier(
      String accountId, String orgId, String projectId, String templateIdentifier, String versionLabel) {
    List<String> fqnList = new LinkedList<>();
    fqnList.add(accountId);
    if (EmptyPredicate.isNotEmpty(orgId)) {
      fqnList.add(orgId);
    }
    if (EmptyPredicate.isNotEmpty(projectId)) {
      fqnList.add(projectId);
    }
    fqnList.add(templateIdentifier);
    fqnList.add(versionLabel);

    return EntityReferenceHelper.createFQN(fqnList);
  }
}

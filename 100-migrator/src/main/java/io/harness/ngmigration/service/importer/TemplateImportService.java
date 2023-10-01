/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.importer;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.IN;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.TemplateFilter;
import io.harness.ngmigration.dto.TemplateScope;
import io.harness.ngmigration.service.DiscoveryService;
import io.harness.ngmigration.template.TemplateFactory;

import software.wings.beans.template.Template;
import software.wings.beans.template.Template.TemplateKeys;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.template.TemplateService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class TemplateImportService implements ImportService {
  @Inject DiscoveryService discoveryService;
  @Inject TemplateService templateService;

  public DiscoveryResult discover(ImportDTO importTemplateDTO) {
    TemplateFilter filter = (TemplateFilter) importTemplateDTO.getFilter();
    String accountId = importTemplateDTO.getAccountIdentifier();
    String appId = filter.getAppId();

    List<Template> templates;
    if (filter.getScope() == TemplateScope.ACCOUNT) {
      switch (filter.getImportType()) {
        case ALL:
          // Note: All here means all the templates we support today
          templates = templateService.listAccountLevelTemplates(accountId);
          break;
        case SPECIFIC:
          templates = templateService.batchGet(filter.getIds(), accountId);
          break;
        default:
          templates = new ArrayList<>();
      }
    } else {
      switch (filter.getImportType()) {
        case ALL:
          // Note: All here means all the templates we support today
          templates = getTemplatesList(accountId, appId, null);
          break;
        case SPECIFIC:
          templates = getTemplatesList(accountId, appId, filter.getIds());
          break;
        default:
          templates = new ArrayList<>();
      }
    }
    if (EmptyPredicate.isEmpty(templates)) {
      throw new InvalidRequestException("No templates found for given filter");
    }
    return discoveryService.discoverMulti(accountId,
        DiscoveryInput.builder()
            .entities(templates.stream()
                          .filter(template -> TemplateFactory.getTemplateService(template).isMigrationSupported())
                          .map(template
                              -> DiscoverEntityInput.builder()
                                     .entityId(template.getUuid())
                                     .appId(appId)
                                     .type(NGMigrationEntityType.TEMPLATE)
                                     .build())
                          .collect(Collectors.toList()))
            .exportImage(false)
            .build());
  }

  public List<Template> getTemplatesList(String accountId, String appId, List<String> specificIds) {
    PageRequest<Template> pageRequest = new PageRequest<>();
    pageRequest.addFilter(TemplateKeys.accountId, EQ, accountId);
    if (EmptyPredicate.isNotEmpty(appId)) {
      pageRequest.addFilter(TemplateKeys.appId, EQ, appId);
    }
    if (EmptyPredicate.isNotEmpty(specificIds)) {
      pageRequest.addFilter("_id", IN, specificIds.toArray());
    }
    PageResponse<Template> pageResponse = templateService.list(pageRequest, new ArrayList<>(), accountId, true);
    return pageResponse.getResponse();
  }
}

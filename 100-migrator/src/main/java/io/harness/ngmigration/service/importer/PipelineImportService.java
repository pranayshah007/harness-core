/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.importer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.PipelineFilter;
import io.harness.ngmigration.service.DiscoveryService;
import io.harness.persistence.HPersistence;

import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.CDC)
public class PipelineImportService implements ImportService {
  @Inject DiscoveryService discoveryService;
  @Inject HPersistence hPersistence;

  public DiscoveryResult discover(ImportDTO importDTO) {
    PipelineFilter filter = (PipelineFilter) importDTO.getFilter();
    String accountId = importDTO.getAccountIdentifier();
    String appId = filter.getAppId();
    Set<String> pipelineIds = filter.getPipelineIds();
    if (EmptyPredicate.isEmpty(pipelineIds)) {
      List<Pipeline> pipelines = hPersistence.createQuery(Pipeline.class)
                                     .filter(Pipeline.ACCOUNT_ID_KEY, accountId)
                                     .filter(PipelineKeys.appId, appId)
                                     .project(PipelineKeys.uuid, true)
                                     .asList();
      if (EmptyPredicate.isNotEmpty(pipelines)) {
        pipelineIds = pipelines.stream().map(Pipeline::getUuid).collect(Collectors.toSet());
      }
    }
    if (EmptyPredicate.isEmpty(pipelineIds)) {
      return null;
    }
    return discoveryService.discoverMulti(accountId,
        DiscoveryInput.builder()
            .exportImage(false)
            .entities(pipelineIds.stream()
                          .map(pipelineId
                              -> DiscoverEntityInput.builder()
                                     .entityId(pipelineId)
                                     .type(NGMigrationEntityType.PIPELINE)
                                     .appId(appId)
                                     .build())
                          .collect(Collectors.toList()))
            .build());
  }
}

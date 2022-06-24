/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.template;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.FilterCreationBlobResponse;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.contracts.service.EntityReferenceRequest;
import io.harness.pms.contracts.service.EntityReferenceResponse;
import io.harness.pms.filter.creation.FilterCreatorMergeService;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class EntityReferenceService {
  @Inject FilterCreatorMergeService filterCreatorMergeService;

  public EntityReferenceResponse getReferences(EntityReferenceRequest request) {
    try {
      Dependencies dependencies = filterCreatorMergeService.getDependencies(request.getYaml());

      Map<String, String> filters = new HashMap<>();
      SetupMetadata.Builder setupMetadataBuilder = filterCreatorMergeService.getSetupMetadataBuilder(
          request.getAccountIdentifier(), request.getOrgIdentifier(), request.getProjectIdentifier());
      setupMetadataBuilder.setGitSyncBranchContext(request.getGitSyncBranchContext());
      FilterCreationBlobResponse response = filterCreatorMergeService.obtainFiltersRecursively(
          filterCreatorMergeService.getServices(), dependencies, filters, setupMetadataBuilder.build());
      filterCreatorMergeService.validateFilterCreationBlobResponse(response);
      EntityReferenceResponse entityReferenceResponse =
              EntityReferenceResponse.newBuilder()
                      .addAllReferredEntities(response.getReferredEntitiesList())
                      .addModuleInfo(response.getFilter())
                      .build();
      return entityReferenceResponse;
    } catch (IOException e) {
      log.error("Error while getting references for template ", e);
      throw new InvalidRequestException(
          String.format("Exception while getting references for template. Invalid yaml in node %s",
              YamlUtils.getErrorNodePartialFQN(e)));
    }
  }
}

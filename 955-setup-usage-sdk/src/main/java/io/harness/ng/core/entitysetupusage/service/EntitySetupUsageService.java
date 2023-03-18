/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.service;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.core.entitysetupusage.dto.EntityReferencesDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.entity.EntitySetupUsage;

import java.util.List;
import javax.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(DX)
public interface EntitySetupUsageService {
  Page<EntitySetupUsageDTO> listAllEntityUsage(int page, int size, String accountIdentifier, String referredEntityFQN,
      EntityType referredEntityType, String searchTerm);

  Page<EntitySetupUsageDTO> listAllEntityUsage(int page, int size, String accountIdentifier, String referredEntityFQN,
      EntityType referredEntityType, EntityType referredByEntityType, String searchTerm, Sort sort);

  Page<EntitySetupUsageDTO> listAllEntityUsageWithSupportForTwoFqnForASingleEntity(int page, int size,
      String accountIdentifier, String referredEntityFQN1, String referredEntityFQN2, EntityType referredEntityType,
      String searchTerm);
  List<EntitySetupUsageDTO> listAllReferredUsages(int page, int size, String accountIdentifier,
      String referredByEntityFQN, EntityType referredEntityType, String searchTerm);

  Page<EntitySetupUsageDTO> list(int page, int size, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String referredEntityIdentifier, EntityType referredEntityType, String searchTerm);

  @Deprecated EntitySetupUsageDTO save(EntitySetupUsageDTO entitySetupUsageDTO);

  Boolean delete(String accountIdentifier, String referredEntityFQN, EntityType referredEntityType,
      String referredByEntityFQN, EntityType referredByEntityType);

  Boolean isEntityReferenced(String accountIdentifier, String referredEntityFQN, EntityType referredEntityType);

  Long referredByEntityCount(String accountIdentifier, String referredEntityFQN, EntityType referredEntityType);

  // todo(abhinav): make delete and create a transactional operation
  Boolean flushSave(List<EntitySetupUsage> entitySetupUsage, EntityType entityTypeFromChannel,
      boolean deleteOldReferredByRecords, String accountId);

  EntityReferencesDTO listAllReferredUsagesBatch(String accountIdentifier, List<String> referredByEntityFQNList,
      EntityType referredByEntityType, EntityType referredEntityType);

  long deleteByReferredByEntityType(EntityType referredByEntityType);

  List<String> listAllReferredEntityIdentifiersPerReferredEntityScope(Scope scope, String referredEntityFQScope,
      EntityType referredEntityType, EntityType referredByEntityType, String referredByEntityName, Sort sort);

  CloseableIterator<EntitySetupUsage> streamAllEntityUsagePerReferredEntityScope(Scope scope,
      String referredEntityFQScope, EntityType referredEntityType, @NotNull EntityType referredByEntityType,
      String referredByEntityName);

  Long countReferredByEntitiesByFQNsIn(String accountIdentifier, List<String> referredEntityFQNs);
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.spring;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.repositories.custom.NGTriggerRepositoryCustom;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
@OwnedBy(PIPELINE)
public interface NGTriggerRepository
    extends PagingAndSortingRepository<NGTriggerEntity, String>, NGTriggerRepositoryCustom {
  Optional<NGTriggerEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot(String accountId,
      String orgIdentifier, String projectIdentifier, String targetIdentifier, String identifier, boolean notDeleted);

  Optional<List<NGTriggerEntity>> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnabledAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, boolean enabled, boolean notDeleted);

  Optional<List<NGTriggerEntity>> findByAccountIdAndOrgIdentifierAndEnabledAndDeletedNot(
      String accountId, String orgIdentifier, boolean enabled, boolean notDeleted);

  Optional<List<NGTriggerEntity>> findByAccountIdAndEnabledAndDeletedNot(
      String accountId, boolean enabled, boolean notDeleted);

  Optional<List<NGTriggerEntity>> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String targetIdentifier, boolean notDeleted);

  Optional<NGTriggerEntity> findByCustomWebhookToken(String customWebhookToken);
}

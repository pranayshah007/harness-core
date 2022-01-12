/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.plan.execution.AccountExecutionMetadata;

import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
@OwnedBy(PIPELINE)
public interface AccountExecutionMetadataRepository
    extends PagingAndSortingRepository<AccountExecutionMetadata, String>, AccountExecutionMetadataRepositoryCustom {
  Optional<AccountExecutionMetadata> findByAccountId(String accountId);
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.executions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.executions.CDAccountExecutionMetadata;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.repositories.executions.CDAccountExecutionMetadataRepository;

import com.google.inject.Inject;
import java.util.Optional;

@OwnedBy(HarnessTeam.PIPELINE)
public class InitialDeploymentRestrictionUsageImpl
    implements RestrictionUsageInterface<StaticLimitRestrictionMetadataDTO> {
  @Inject CDAccountExecutionMetadataRepository accountExecutionMetadataRepository;

  @Override
  public long getCurrentValue(String accountIdentifier, StaticLimitRestrictionMetadataDTO restrictionMetadataDTO) {
    Optional<CDAccountExecutionMetadata> accountExecutionMetadata =
        accountExecutionMetadataRepository.findByAccountId(accountIdentifier);
    if (!accountExecutionMetadata.isPresent()) {
      return 0;
    }
    return accountExecutionMetadata.get().getExecutionCount();
  }
}

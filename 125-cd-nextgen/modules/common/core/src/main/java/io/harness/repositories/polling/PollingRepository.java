/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.polling;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.PollingInfo;

import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.CDC)
public interface PollingRepository
    extends PagingAndSortingRepository<PollingDocument, String>, PollingRepositoryCustom {
  PollingDocument findByUuidAndAccountId(String uuid, String accountId);
  PollingDocument findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPollingInfo(
      String accountId, String orgIdentifier, String projectIdentifier, PollingInfo pollingInfo);
}

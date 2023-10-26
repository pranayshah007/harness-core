/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.license.usage.entities.ActiveDevelopersDailyCountEntity;

import java.util.Date;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface ActiveDevelopersDailyCountRepository extends CrudRepository<ActiveDevelopersDailyCountEntity, String> {
  List<ActiveDevelopersDailyCountEntity> findByAccountIdentifierAndDateInDateFormatBetween(
      String accountIdentifier, Date from, Date to);
}

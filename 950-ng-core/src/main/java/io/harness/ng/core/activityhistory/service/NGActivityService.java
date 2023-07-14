/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.activityhistory.service;

import io.harness.EntityType;
import io.harness.ng.core.activityhistory.NGActivityStatus;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.activityhistory.dto.ConnectivityCheckSummaryDTO;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;

import java.util.Set;
import org.springframework.data.domain.Page;

public interface NGActivityService {
  Page<NGActivityDTO> list(int page, int size, String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String referredEntityIdentifier, long start, long end, NGActivityStatus status, EntityType referredEntityType,
      EntityType referredByEntityType, Set<NGActivityType> ngActivityTypes);

  NGActivityDTO save(NGActivityDTO activityHistory);

  boolean deleteAllActivitiesOfAnEntity(
      String accountIdentifier, String referredEntityFQN, EntityType referredEntityType);

  ConnectivityCheckSummaryDTO getConnectivityCheckSummary(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String referredEntityIdentifier, long start, long end);
}

/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.activityhistory;

import static io.harness.EntityType.CONNECTORS;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.EntityType;
import io.harness.beans.EntityReference;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.activityhistory.dto.EntityUsageActivityDetailDTO;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.utils.IdentifierRefHelper;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class ActivityHistoryTestHelper {
  public static NGActivityDTO createActivityHistoryDTO(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String identifier, NGActivityStatus status, long activityTime,
      NGActivityType activityType, EntityType referredByEntityType) {
    String identifier1;
    if (isNotEmpty(projectIdentifier)) {
      identifier1 = "identifier1";
    } else if (isNotEmpty(orgIdentifier)) {
      identifier1 = "org.identifier1";
    } else {
      identifier1 = "account.identifier";
    }
    EntityReference referredEntityRef =
        IdentifierRefHelper.getIdentifierRef(identifier, accountIdentifier, orgIdentifier, projectIdentifier);
    EntityReference referredByEntityRef =
        IdentifierRefHelper.getIdentifierRef(identifier1, accountIdentifier, orgIdentifier, projectIdentifier);
    EntityDetail referredEntity = EntityDetail.builder().entityRef(referredEntityRef).type(CONNECTORS).build();
    EntityDetail referredByEntity =
        EntityDetail.builder().entityRef(referredByEntityRef).type(referredByEntityType).build();
    return NGActivityDTO.builder()
        .description("description")
        .accountIdentifier(accountIdentifier)
        .activityTime(activityTime)
        .activityStatus(status)
        .type(activityType)
        .accountIdentifier("accountIdentifier")
        .referredEntity(referredEntity)
        .detail(EntityUsageActivityDetailDTO.builder().referredByEntity(referredByEntity).build())
        .build();
  }
}

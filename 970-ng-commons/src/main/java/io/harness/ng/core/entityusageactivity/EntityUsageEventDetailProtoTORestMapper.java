/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.entityusageactivity;

import io.harness.eventsframework.schemas.entity.EntityUsageDetailProto;

public class EntityUsageEventDetailProtoTORestMapper {
  public EntityUsageDetail getEventDetail(EntityUsageDetailProto detail) {
    EntityUsageData usageData = getUsageData(detail);
    return EntityUsageDetail.builder().usageType(detail.getUsageType()).usageData(usageData).build();
  }

  private EntityUsageData getUsageData(EntityUsageDetailProto detail) {
    // populate for specific event types (Eg- Pipeline execution)
    return null;
  }
}

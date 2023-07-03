/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.activityhistory.mapper;

import io.harness.exception.UnknownEnumTypeException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.activityhistory.NGActivityStatus;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.activityhistory.dto.ActivityDetail;
import io.harness.ng.core.activityhistory.dto.ConnectivityCheckActivityDetailDTO;
import io.harness.ng.core.activityhistory.dto.EntityUsageActivityDetailDTO;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.ng.core.activityhistory.entity.EntityUsageActivityDetail;
import io.harness.ng.core.activityhistory.entity.NGActivity;

import com.google.inject.Singleton;

@Singleton
public class NGActivityEntityToDTOMapper {
  public NGActivityDTO writeDTO(NGActivity activity) {
    ActivityDetail activityDetail = getActivityDetail(activity);
    EntityDetail referredEntity = activity.getReferredEntity();
    return NGActivityDTO.builder()
        .accountIdentifier(activity.getAccountIdentifier())
        .referredEntity(referredEntity)
        .activityStatus(NGActivityStatus.valueOf(activity.getActivityStatus()))
        .activityTime(activity.getActivityTime())
        .description(activity.getDescription())
        .type(NGActivityType.valueOf(activity.getType()))
        .detail(activityDetail)
        .build();
  }

  private ActivityDetail getActivityDetail(NGActivity activity) {
    switch (NGActivityType.valueOf(activity.getType())) {
      case CONNECTIVITY_CHECK:
        return ConnectivityCheckActivityDetailDTO.builder().build();
      case ENTITY_USAGE:
        EntityUsageActivityDetail entityUsageActivity = (EntityUsageActivityDetail) activity;
        EntityDetail referredByEntity = entityUsageActivity.getReferredByEntity();
        return EntityUsageActivityDetailDTO.builder()
            .referredByEntity(referredByEntity)
            .usageDetail(entityUsageActivity.getUsageDetail())
            .activityStatusMessage(((EntityUsageActivityDetail) activity).getActivityStatusMessage())
            .errors(entityUsageActivity.getErrors())
            .errorSummary(entityUsageActivity.getErrorSummary())
            .status(entityUsageActivity.getStatus())
            .build();
      case ENTITY_UPDATE:
      case ENTITY_CREATION:
        return null;
      default:
        throw new UnknownEnumTypeException("NGActivity", String.valueOf(activity.getType()));
    }
  }
}

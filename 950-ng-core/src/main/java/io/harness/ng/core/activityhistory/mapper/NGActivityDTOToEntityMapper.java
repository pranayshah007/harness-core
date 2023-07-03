/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.activityhistory.mapper;

import io.harness.exception.UnknownEnumTypeException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.activityhistory.dto.EntityUsageActivityDetailDTO;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.ng.core.activityhistory.entity.ConnectivityCheckDetail;
import io.harness.ng.core.activityhistory.entity.EntityUsageActivityDetail;
import io.harness.ng.core.activityhistory.entity.NGActivity;
import io.harness.ng.core.activityhistory.entity.NGActivity.NGActivityBuilder;

import com.google.inject.Singleton;

@Singleton
public class NGActivityDTOToEntityMapper {
  public NGActivity toActivityHistoryEntity(NGActivityDTO activityHistoryDTO) {
    NGActivity activityEntity = null;
    switch (activityHistoryDTO.getType()) {
      case CONNECTIVITY_CHECK:
        activityEntity = getActivityForConnectivityCheck(activityHistoryDTO);
        break;
      case ENTITY_USAGE:
        activityEntity = getActivityForEntityUsage(activityHistoryDTO);
        break;
      case ENTITY_CREATION:
      case ENTITY_UPDATE:
        activityEntity = getBaseActivityObject(activityHistoryDTO);
        break;
      default:
        throw new UnknownEnumTypeException("NGActivity", String.valueOf(activityHistoryDTO.getType()));
    }
    return activityEntity;
  }

  private NGActivity getActivityForConnectivityCheck(NGActivityDTO activityDTO) {
    NGActivityBuilder builder = ConnectivityCheckDetail.builder();
    populateCommonActivityProperties(builder, activityDTO);
    return builder.build();
  }

  private NGActivity getBaseActivityObject(NGActivityDTO activityDTO) {
    NGActivityBuilder builder = NGActivity.builder();
    populateCommonActivityProperties(builder, activityDTO);
    return builder.build();
  }

  private void populateCommonActivityProperties(NGActivityBuilder activityBuilder, NGActivityDTO activityDTO) {
    EntityDetail referredEntity = activityDTO.getReferredEntity();
    activityBuilder.accountIdentifier(activityDTO.getAccountIdentifier())
        .referredEntityFQN(referredEntity.getEntityRef().getFullyQualifiedName())
        .activityStatus(String.valueOf(activityDTO.getActivityStatus()))
        .activityTime(activityDTO.getActivityTime())
        .description(activityDTO.getDescription())
        .referredEntityType(String.valueOf(activityDTO.getReferredEntity().getType()))
        .referredEntity(referredEntity)
        .type(String.valueOf(activityDTO.getType()));
  }

  private NGActivity getActivityForEntityUsage(NGActivityDTO activityDTO) {
    EntityUsageActivityDetailDTO entityUsageDTO = (EntityUsageActivityDetailDTO) activityDTO.getDetail();
    EntityDetail referredByEntity = entityUsageDTO.getReferredByEntity();
    NGActivityBuilder builder = EntityUsageActivityDetail.builder()
                                    .referredByEntityFQN(referredByEntity.getEntityRef().getFullyQualifiedName())
                                    .referredByEntityType(String.valueOf(referredByEntity.getType()))
                                    .referredByEntity(referredByEntity)
                                    .usageDetail(entityUsageDTO.getUsageDetail())
                                    .activityStatusMessage(entityUsageDTO.getActivityStatusMessage())
                                    .errors(entityUsageDTO.getErrors())
                                    .errorSummary(entityUsageDTO.getErrorSummary())
                                    .status(entityUsageDTO.getStatus());
    populateCommonActivityProperties(builder, activityDTO);
    return builder.build();
  }
}

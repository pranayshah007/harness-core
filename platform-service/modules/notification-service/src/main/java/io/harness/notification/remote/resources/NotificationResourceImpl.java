/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.resources;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.notification.remote.mappers.NotificationMapper.toDTO;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.Team;
import io.harness.notification.entities.Notification;
import io.harness.notification.remote.dto.NotificationDTO;
import io.harness.notification.service.api.NotificationService;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;

@OwnedBy(PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class NotificationResourceImpl implements NotificationResource {
  private final NotificationService notificationService;

  public ResponseDTO<NotificationDTO> get(String id) {
    Optional<Notification> notificationOptional = notificationService.getnotification(id);
    return ResponseDTO.newResponse(toDTO(notificationOptional.orElse(null)).orElse(null));
  }

  public ResponseDTO<PageResponse<NotificationDTO>> list(Team team, PageRequest pageRequest) {
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order = SortOrder.Builder.aSortOrder().withField("lastModifiedAt", SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }
    Page<NotificationDTO> results = notificationService.list(team, pageRequest).map(x -> toDTO(x).orElse(null));
    return ResponseDTO.newResponse(getNGPageResponse(results));
  }
}

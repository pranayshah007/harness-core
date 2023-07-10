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
import io.harness.exception.UnexpectedException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.Team;
import io.harness.notification.entities.Notification;
import io.harness.notification.remote.dto.NotificationDTO;
import io.harness.notification.service.api.NotificationService;

import software.wings.beans.notification.BotQuestion;
import software.wings.beans.notification.BotResponse;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.StringEntity;
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

  @Override
  public ResponseDTO<BotResponse> answer(BotQuestion question) {
    return ResponseDTO.newResponse(new BotResponse(executeRequest(question)));
  }

  private String executeRequest(BotQuestion question) {
    try {
      Request request =
          Request.Post("http://34.123.82.236:80/chat")
              .connectTimeout(5000)
              .socketTimeout(30000)
              .addHeader("Content-Type", "application/json")
              .body(new StringEntity(new Gson().toJson(new BotQuestion(question.getModel(), question.getQuestion()))));
      Content content = request.execute().returnContent();
      request.abort();
      return content.asString();
    } catch (Exception ex) {
      throw new UnexpectedException("An unexpected exception has occurred", ex);
    }
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.event;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EventDetail;
import io.harness.beans.EventStatus;
import io.harness.beans.GenericEventDetail;
import io.harness.beans.WebHookEventDetail;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.service.EventService;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import software.wings.beans.HttpStateExecutionResponse;

import com.google.inject.Inject;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Data
@Slf4j
public class EventsDeliveryCallback implements OldNotifyCallback {
  private String accountId;
  private String eventConfigId;
  private String eventId;
  private String permitId;
  private String appId;
  private static final int MAX_ARTIFACTS_COLLECTION_FOR_WARN = 100;
  private static final int MAX_LOGS = 2000;

  @Inject private transient EventService eventService;

  public EventsDeliveryCallback(String accountId, String appId, String eventConfigId, String eventId, String permitId) {
    this.accountId = accountId;
    this.eventConfigId = eventConfigId;
    this.eventId = eventId;
    this.permitId = permitId;
    this.appId = appId;
  }

  @Override
  public void notify(Map<String, ResponseData> response) {
    DelegateResponseData notifyResponseData = (DelegateResponseData) response.values().iterator().next();
    log.info("{}", notifyResponseData);
    if (notifyResponseData instanceof HttpStateExecutionResponse) {
      HttpStateExecutionResponse resp = (HttpStateExecutionResponse) notifyResponseData;
      int httpRespStatus = resp.getHttpResponseCode();
      // TODO: Add all the details
      EventDetail eventDetail = WebHookEventDetail.builder()
                                    .responseStatusCode(httpRespStatus)
                                    .requestHeaders(null)
                                    .responseHeaders(null)
                                    .url(null)
                                    .responseBody(resp.getHttpResponseBody())
                                    .build();
      EventStatus eventStatus =
          httpRespStatus >= 200 && httpRespStatus < 300 ? EventStatus.SUCCESS : EventStatus.FAILED;
      eventService.updateEventStatus(eventId, eventStatus, eventDetail);
    } else {
      log.error("Unexpected  notify response:[{}] during artifact collection", response);
    }
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    DelegateResponseData notifyResponseData = (DelegateResponseData) response.values().iterator().next();
    if (notifyResponseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData err = (ErrorNotifyResponseData) notifyResponseData;
      log.info("Request failed :[{}]", err.getErrorMessage());
      eventService.updateEventStatus(eventId, EventStatus.FAILED, new GenericEventDetail(err.getErrorMessage()));
    } else {
      log.error("Unexpected  notify response:[{}] during http event delivery", response);
    }
  }
}

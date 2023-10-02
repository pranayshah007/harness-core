/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.heartbeat.stream;

import static io.harness.delegate.message.ManagerMessageConstants.MONGO_TIMEOUT;
import static io.harness.delegate.message.ManagerMessageConstants.SELF_DESTRUCT;
import static io.harness.delegate.utils.DelegateServiceConstants.STREAM_DELEGATE;
import static io.harness.eraro.ErrorCode.DELEGATE_NOT_REGISTERED;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_REGISTRATION_FAILED;

import io.harness.beans.DelegateHeartbeatParams;
import io.harness.beans.DelegateHeartbeatResponseStreaming;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.heartbeat.DelegateHeartbeatService;
import io.harness.delegate.utils.DelegateValidityCheckHelper;
import io.harness.exception.WingsException;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.service.intfc.DelegateCache;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.MongoTimeoutException;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.atmosphere.cpr.BroadcasterFactory;

@Slf4j
@Singleton
public class DelegateStreamHeartbeatService
    extends DelegateHeartbeatService<DelegateHeartbeatResponseStreamingWrapper> {
  @Inject private DelegateValidityCheckHelper delegateValidityCheckHelper;
  @Inject private DelegateCache delegateCache;
  @Inject private BroadcasterFactory broadcasterFactory;
  @Inject private DelegateMetricsService delegateMetricsService;

  @Override
  public Optional<DelegateHeartbeatResponseStreamingWrapper> precheck(
      @NotNull final Delegate existingDelegate, @NotNull final DelegateHeartbeatParams params) {
    super.precheck(existingDelegate, params);
    return delegateValidityCheckHelper.getBroadcastMessageFromDelegateValidityCheck(existingDelegate)
        .map(message -> {
          // TODO: define better metrics to show different types of error.
          delegateMetricsService.recordDelegateMetrics(existingDelegate, DELEGATE_REGISTRATION_FAILED);
          return DelegateHeartbeatResponseStreamingWrapper.builder()
              .isHeartbeatAsObject(false)
              .responseMessage(message)
              .build();
        })
        .or(Optional::empty);
  }

  @Override
  public void finish(
      @NotNull DelegateHeartbeatResponseStreamingWrapper response, @NotNull DelegateHeartbeatParams params) {
    broadcasterFactory.lookup(STREAM_DELEGATE + params.getAccountId(), true).broadcast(response.get());
  }

  @Override
  public DelegateHeartbeatResponseStreamingWrapper buildHeartbeatResponseOnFailure(
      @NotNull DelegateHeartbeatParams params, @NotNull final WingsException e) {
    if (e.getCode().equals(DELEGATE_NOT_REGISTERED)) {
      return DelegateHeartbeatResponseStreamingWrapper.builder()
          .isHeartbeatAsObject(false)
          .responseMessage(SELF_DESTRUCT + params.getDelegateId())
          .build();
    }
    // If the heartbeat processing hit unknown exception, fail early.
    return null;
  }

  @Override
  public DelegateHeartbeatResponseStreamingWrapper buildHeartBeatResponseOnMongoException(
      DelegateHeartbeatParams params, MongoTimeoutException e) {
    return DelegateHeartbeatResponseStreamingWrapper.builder()
        .isHeartbeatAsObject(false)
        .responseMessage(MONGO_TIMEOUT + params.getDelegateId())
        .build();
  }

  @Override
  public DelegateHeartbeatResponseStreamingWrapper buildHeartbeatResponseOnSuccess(
      @NotNull final DelegateHeartbeatParams params, @NotNull final Delegate existingDelegate) {
    if (existingDelegate.isHeartbeatAsObject()) {
      return DelegateHeartbeatResponseStreamingWrapper.builder()
          .isHeartbeatAsObject(true)
          .delegateHeartbeatResponseStreaming(DelegateHeartbeatResponseStreaming.builder()
                                                  .delegateId(params.getDelegateId())
                                                  .responseSentAt(clock.millis())
                                                  .build())
          .build();
    } else {
      return DelegateHeartbeatResponseStreamingWrapper.builder()
          .isHeartbeatAsObject(false)
          .responseMessage(new StringBuilder(128).append("[X]").append(params.getDelegateId()).toString())
          .build();
    }
  }
}

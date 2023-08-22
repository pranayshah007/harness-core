/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.heartbeat.polling;

import static io.harness.eraro.ErrorCode.DELEGATE_NOT_REGISTERED;

import io.harness.beans.DelegateHeartbeatParams;
import io.harness.beans.DelegateHeartbeatResponse;
import io.harness.beans.DelegateHeartbeatResponse.DelegateHeartbeatResponseBuilder;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.heartbeat.DelegateHeartbeatService;
import io.harness.delegate.utils.DelegateJreVersionHelper;
import io.harness.exception.WingsException;

import software.wings.app.MainConfiguration;
import software.wings.licensing.LicenseService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.MongoTimeoutException;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DelegatePollingHeartbeatService extends DelegateHeartbeatService<DelegateHeartbeatResponse> {
  @Inject private LicenseService licenseService;
  @Inject private MainConfiguration mainConfiguration;
  @Inject private DelegateJreVersionHelper delegateJreVersionHelper;

  @Override
  public Optional<DelegateHeartbeatResponse> precheck(
      @NotNull final Delegate existingDelegate, @NotNull final DelegateHeartbeatParams params) {
    super.precheck(existingDelegate, params);
    if (licenseService.isAccountDeleted(existingDelegate.getAccountId())) {
      return Optional.of(getResponseBuilder(existingDelegate.getAccountId(), existingDelegate.getUuid())
                             .status(DelegateInstanceStatus.DELETED.toString())
                             .build());
    }
    return Optional.empty();
  }

  @Override
  public void finish(@NotNull final DelegateHeartbeatResponse response, @NotNull final DelegateHeartbeatParams params) {
    log.debug("Polling-mode heartbeat sending response at {} for delegate {}", clock.millis(), params.getDelegateId());
  }

  @Override
  public DelegateHeartbeatResponse buildHeartbeatResponseOnFailure(
      final DelegateHeartbeatParams params, final WingsException e) {
    if (e.getCode().equals(DELEGATE_NOT_REGISTERED)) {
      // Adding delegate deleted check to heartbeat.
      return getResponseBuilder(params.getAccountId(), params.getDelegateId())
          .status(DelegateInstanceStatus.DELETED.toString())
          .build();
    }
    // If the heartbeat processing hit unknown exception, fail early.
    return null;
  }

  @Override
  public DelegateHeartbeatResponse buildHeartBeatResponseOnMongoException(
      DelegateHeartbeatParams params, MongoTimeoutException e) {
    return null;
  }

  @Override
  public DelegateHeartbeatResponse buildHeartbeatResponseOnSuccess(
      @NotNull final DelegateHeartbeatParams params, @NotNull Delegate existingDelegate) {
    return getResponseBuilder(params.getAccountId(), params.getDelegateId())
        .status(DelegateInstanceStatus.ENABLED.toString())
        .build();
  }

  private DelegateHeartbeatResponseBuilder getResponseBuilder(
      @NotNull final String accountId, @NotNull final String delegateId) {
    return DelegateHeartbeatResponse.builder()
        .delegateId(delegateId)
        .jreVersion(delegateJreVersionHelper.getTargetJreVersion())
        .useCdn(mainConfiguration.useCdnForDelegateStorage());
  }
}

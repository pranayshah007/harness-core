/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import static java.time.Duration.ofMinutes;

import io.harness.annotations.dev.OwnedBy;

import java.time.Duration;

@OwnedBy(DEL)
public interface DelegateServiceConstants {
  String STREAM_DELEGATE = "/stream/delegate/";

  Duration HEARTBEAT_EXPIRY_TIME = ofMinutes(10);

  Duration HEARTBEAT_EXPIRY_TIME_FIVE_MINS = ofMinutes(5);
}

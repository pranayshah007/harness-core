/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.listener;

import io.harness.ccm.commons.entities.events.PublishedMessage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LoggingStepSkipListener implements SkipListener<PublishedMessage, PublishedMessage> {
  @Override
  public void onSkipInRead(Throwable throwable) {
    log.error("Skip in read", throwable);
  }

  @Override
  public void onSkipInWrite(PublishedMessage publishedMessage, Throwable throwable) {
    log.error("Skip in write for message {} ", publishedMessage.getUuid(), throwable);
  }

  @Override
  public void onSkipInProcess(PublishedMessage publishedMessage, Throwable throwable) {
    log.error("Skip in process for message {}", publishedMessage.getUuid(), throwable);
  }
}

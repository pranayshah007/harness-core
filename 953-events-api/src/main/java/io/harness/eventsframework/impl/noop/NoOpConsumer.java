/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventsframework.impl.noop;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.AbstractConsumer;
import io.harness.eventsframework.consumer.Message;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PL)
public class NoOpConsumer extends AbstractConsumer {
  public NoOpConsumer(String topicName, String groupName) {
    super(topicName, groupName);
  }

  @Override
  public List<Message> read(Duration maxWaitTime) {
    return Collections.emptyList();
  }

  @Override
  public void acknowledge(String... messageId) {}

  @Override
  public void shutdown() {}

  @Override
  public int getBatchSize() {
    return 0;
  }

  public static NoOpConsumer of(String topicName, String groupName) {
    return new NoOpConsumer(topicName, groupName);
  }
}

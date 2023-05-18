package io.harness.ci.execution;


import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.harness.waiter.RedisNotifyQueuePublisher;

@OwnedBy(HarnessTeam.CI)
public class CINotifyEventPublisher extends RedisNotifyQueuePublisher {
    @Inject
    public CINotifyEventPublisher(@Named(EventsFrameworkConstants.CI_ORCHESTRATION_NOTIFY_EVENT) Producer producer) {
        super(producer);
    }
}
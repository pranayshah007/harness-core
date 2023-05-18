package io.harness.ci.execution;


import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.pms.events.base.PmsAbstractRedisConsumer;
import io.harness.queue.QueueController;

import javax.cache.Cache;

import static io.harness.eventsframework.EventsFrameworkConstants.CI_ORCHESTRATION_NOTIFY_EVENT;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public final class CINotifyEventConsumerRedis extends PmsAbstractRedisConsumer<CINotifyEventMessageListener> {
    @Inject
    public CINotifyEventConsumerRedis(@Named(CI_ORCHESTRATION_NOTIFY_EVENT) Consumer redisConsumer,
                                      CINotifyEventMessageListener messageListener, @Named("ciEventsCache") Cache<String, Integer> eventsCache,
                                      QueueController queueController) {
        super(redisConsumer, messageListener, eventsCache, queueController);
    }
}

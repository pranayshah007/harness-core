package io.harness.pms.redisConsumer;


import com.google.inject.Singleton;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.debezium.redisconsumer.DebeziumAbstractRedisConsumer;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.eventsframework.api.Consumer;
import io.harness.queue.QueueController;
import lombok.extern.slf4j.Slf4j;

import javax.cache.Cache;
import javax.inject.Named;

import static io.harness.eventsframework.EventsFrameworkConstants.MODULE_LICENSES_REDIS_EVENT_CONSUMER_CD;

@Slf4j
@OwnedBy(HarnessTeam.PLG)
@Singleton
public class ModuleLicensesCDRedisEventConsumer extends DebeziumAbstractRedisConsumer {

    public ModuleLicensesCDRedisEventConsumer(@Named(MODULE_LICENSES_REDIS_EVENT_CONSUMER_CD) Consumer redisConsumer,
                                              QueueController queueController, ModuleLicensesCDChangeEventHandler eventHandler,
                                              Cache<String, Long> eventsCache) {
        super(redisConsumer, queueController, eventHandler, eventsCache);
    }
}

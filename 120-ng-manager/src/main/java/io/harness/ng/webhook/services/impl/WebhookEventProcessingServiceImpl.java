/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.AbstractProducer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.ng.webhook.WebhookHelper;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.entities.WebhookEvent.WebhookEventsKeys;
import io.harness.ng.webhook.services.api.WebhookEventProcessingService;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.repositories.ng.webhook.spring.WebhookEventRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class WebhookEventProcessingServiceImpl
    implements WebhookEventProcessingService, MongoPersistenceIterator.Handler<WebhookEvent> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private WebhookHelper webhookHelper;
  @Inject WebhookEventRepository webhookEventRepository;

  @Override
  public void registerIterators(int threadPoolSize) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("WebhookEventProcessor")
            .poolSize(threadPoolSize)
            .interval(ofSeconds(5))
            .build(),
        WebhookEventProcessingService.class,
        MongoPersistenceIterator.<WebhookEvent, SpringFilterExpander>builder()
            .clazz(WebhookEvent.class)
            .fieldName(WebhookEventsKeys.nextIteration)
            .targetInterval(ofMinutes(5))
            .acceptableExecutionTime(ofMinutes(2))
            .acceptableNoAlertDelay(ofSeconds(30))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  @Override
  public void handle(WebhookEvent event) {
    log.info("Processing the webhook event with uuid = [{}]", event.getUuid());
    ParseWebhookResponse parseWebhookResponse = null;
    SourceRepoType sourceRepoType = webhookHelper.getSourceRepoType(event);
    if (sourceRepoType != SourceRepoType.UNRECOGNIZED) {
      parseWebhookResponse = webhookHelper.invokeScmService(event);
    }

    try {
      publishWebhookEvent(event, parseWebhookResponse, sourceRepoType);
    } catch (Exception e) {
      log.error(new StringBuilder(128)
                    .append("Error while publishing Webhook Event: ")
                    .append(event.getUuid())
                    .append(", Exception: ")
                    .append(e)
                    .toString());

    } finally {
      webhookEventRepository.delete(event);
    }
  }

  public void publishWebhookEvent(
      WebhookEvent event, ParseWebhookResponse parseWebhookResponse, SourceRepoType sourceRepoType) {
    WebhookDTO webhookDTO = webhookHelper.generateWebhookDTO(event, parseWebhookResponse, sourceRepoType);

    // if publish fails for one of the producers, still continue for rest of the producers.
    webhookHelper.getProducerListForEvent(webhookDTO).forEach(producer -> {
      try {
        producer.send(Message.newBuilder().setData(webhookDTO.toByteString()).build());
      } catch (EventsFrameworkDownException e) {
        String topicName =
            producer instanceof AbstractProducer ? ((AbstractProducer) producer).getTopicName() : StringUtils.EMPTY;

        log.error(new StringBuilder(128)
                      .append("Error while publishing Webhook Event: ")
                      .append(webhookDTO.getEventId())
                      .append(" to Topic")
                      .append(topicName)
                      .append(", Exception: ")
                      .append(e)
                      .toString());
      }
    });
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pubsub.consumer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.BatchProcessingException;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.pricing.gcp.bigquery.BigQueryHelperService;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingHistoryService;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.Subscription;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.helpers.MessageFormatter;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@Singleton
public class BigQueryUpdateTopicSubscriber {
  private static final String GOOGLE_CREDENTIALS_PATH = "CE_GCP_CREDENTIALS_PATH";
  private static final String FULL_SUBSCRIPTION_FORMAT = "projects/{}/subscriptions/{}";
  private static final String FULL_TOPIC_NAME_FORMAT = "projects/{}/topics/{}";
  @Inject private BatchMainConfig config;
  @Inject BigQueryService bigQueryService;
  @Inject BigQueryHelper bigQueryHelper;
  @Inject BigQueryHelperService bigQueryHelperService;
  @Inject BusinessMappingService businessMappingService;
  @Inject BusinessMappingHistoryService businessMappingHistoryService;
  @Inject ViewsQueryBuilder viewsQueryBuilder;
  Subscriber subscriber;

  public void subscribeAsync() throws IOException {
    log.info("Starting BigQueryUpdateTopic Subscriber");

    String gcpProjectId = config.getGcpConfig().getGcpProjectId();
    String topicName = config.getGcpConfig().getBigQueryUpdatePubSubTopic().getTopicName();
    String fullTopicName = MessageFormatter.format(FULL_TOPIC_NAME_FORMAT, gcpProjectId, topicName).getMessage();
    String gcpSubscriptionName = config.getGcpConfig().getBigQueryUpdatePubSubTopic().getSubscriptionName();
    String fullSubscriptionName =
        MessageFormatter.format(FULL_SUBSCRIPTION_FORMAT, gcpProjectId, gcpSubscriptionName).getMessage();

    GoogleCredentials credentials = bigQueryService.getCredentials(GOOGLE_CREDENTIALS_PATH);
    if (credentials == null) {
      try {
        log.info("WI: Using Google ADC");
        credentials = GoogleCredentials.getApplicationDefault();
      } catch (IOException e) {
        log.error("Exception in using Google ADC", e);
      }
    }
    FixedCredentialsProvider credentialsProvider = FixedCredentialsProvider.create(credentials);
    SubscriptionAdminSettings subscriptionAdminSettings =
        SubscriptionAdminSettings.newBuilder().setCredentialsProvider(credentialsProvider).build();

    try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create(subscriptionAdminSettings)) {
      Subscription sub = Subscription.newBuilder().setName(fullSubscriptionName).setTopic(fullTopicName).build();
      try {
        subscriptionAdminClient.createSubscription(sub);
      } catch (AlreadyExistsException e) {
        log.info("Subscription Already Exists. Subscription Name: {}", fullSubscriptionName);
      }
    }

    ProjectSubscriptionName projectSubscriptionName = ProjectSubscriptionName.of(gcpProjectId, gcpSubscriptionName);
    subscriber = Subscriber
                     .newBuilder(projectSubscriptionName,
                         new BigQueryUpdateMessageReceiver(
                             bigQueryHelper, bigQueryHelperService, businessMappingHistoryService, viewsQueryBuilder))
                     .setCredentialsProvider(credentialsProvider)
                     .build();

    log.info("Starting listening to pub/sub topic: {}; subscription: {}", fullTopicName, gcpSubscriptionName);
    try {
      subscriber.startAsync().awaitRunning(1, TimeUnit.MINUTES);
    } catch (TimeoutException e) {
      log.error("Failed to start listening to pub/sub topic: {}", fullTopicName);
      throw new BatchProcessingException(
          String.format("Failed to start listening to pub/sub topic: %s.", fullTopicName), e);
    }
  }
}

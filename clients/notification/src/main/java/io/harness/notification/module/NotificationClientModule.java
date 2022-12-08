/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.module;

import io.harness.govern.ProviderModule;
import io.harness.mongo.queue.NGMongoQueuePublisher;
import io.harness.notification.NotificationClientBackendConfiguration;
import io.harness.notification.NotificationClientConfiguration;
import io.harness.notification.entities.MongoNotificationRequest;
import io.harness.notification.messageclient.MessageClient;
import io.harness.notification.messageclient.MongoClient;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationClientImpl;
import io.harness.notification.remote.NotificationHTTPClient;
import io.harness.notification.remote.NotificationHTTPFactory;
import io.harness.queue.QueuePublisher;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import org.springframework.data.mongodb.core.MongoTemplate;

public class NotificationClientModule extends AbstractModule {
  private final NotificationClientConfiguration notificationClientConfiguration;

  public NotificationClientModule(NotificationClientConfiguration configuration) {
    this.notificationClientConfiguration = configuration;
  }

  @Override
  protected void configure() {
    bind(NotificationClientConfiguration.class).toInstance(notificationClientConfiguration);
    install(new ProviderModule() {
      @Provides
      @Singleton
      NotificationClientBackendConfiguration getNotificationClientConfiguration() {
        return notificationClientConfiguration.getNotificationClientBackendConfiguration();
      }
    });
    assert notificationClientConfiguration.getNotificationClientBackendConfiguration().getType().equalsIgnoreCase(
        "mongo");
    bind(MessageClient.class).to(MongoClient.class);
    bind(NotificationHTTPClient.class).toProvider(NotificationHTTPFactory.class).in(Scopes.SINGLETON);
    bind(NotificationClient.class).to(NotificationClientImpl.class);
    bind(NotificationClientConfiguration.class).toInstance(notificationClientConfiguration);
  }

  @Provides
  @Singleton
  public QueuePublisher<MongoNotificationRequest> getQueuePublisher(
      @Named("notification-channel") MongoTemplate mongoTemplate) {
    return new NGMongoQueuePublisher<>("Notification-queue", new ArrayList<>(), mongoTemplate);
  }

  @Provides
  private NotificationHTTPFactory notificationHTTPFactory(KryoConverterFactory kryoConverterFactory) {
    ServiceHttpClientConfig serviceHttpClientConfig = this.notificationClientConfiguration.getServiceHttpClientConfig();
    return new NotificationHTTPFactory(serviceHttpClientConfig,
        this.notificationClientConfiguration.getNotificationSecrets().getNotificationClientSecret(),
        new ServiceTokenGenerator(), kryoConverterFactory);
  }
}

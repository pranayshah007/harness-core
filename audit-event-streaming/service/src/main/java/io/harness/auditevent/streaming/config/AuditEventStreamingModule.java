/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.config;

import static io.harness.authorization.AuthorizationServiceHeader.AUDIT_EVENT_STREAMING;

import io.harness.audit.client.remote.streaming.StreamingDestinationClientModule;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.remote.client.ClientMode;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.service.impl.DelegateAsyncServiceImpl;
import io.harness.service.impl.DelegateProgressServiceImpl;
import io.harness.service.impl.DelegateSyncServiceImpl;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.guice.annotation.EnableGuiceModules;

@Slf4j
@EnableGuiceModules
public class AuditEventStreamingModule extends AbstractModule {
  AuditEventStreamingConfig auditEventStreamingConfig;

  public AuditEventStreamingModule(AuditEventStreamingConfig auditEventStreamingConfig) {
    this.auditEventStreamingConfig = auditEventStreamingConfig;
  }

  @Override
  protected void configure() {
    install(new StreamingDestinationClientModule(auditEventStreamingConfig.getAuditClientConfig(),
        auditEventStreamingConfig.getServiceSecrets().getPlatformServiceSecret(),
        AUDIT_EVENT_STREAMING.getServiceId()));
    install(DelegateServiceDriverModule.getInstance(false, false));
    install(new io.harness.service.DelegateServiceModule());
    install(new DelegateServiceDriverGrpcClientModule(
        auditEventStreamingConfig.getServiceSecrets().getManagerServiceSecret(),
        auditEventStreamingConfig.getGrpcClientConfig().getTarget(),
        auditEventStreamingConfig.getGrpcClientConfig().getAuthority(), true));
    install(new ConnectorResourceClientModule(auditEventStreamingConfig.getNgManagerClientConfig(),
        auditEventStreamingConfig.getServiceSecrets().getNgManagerServiceSecret(), AUDIT_EVENT_STREAMING.getServiceId(),
        ClientMode.PRIVILEGED));
    install(new SecretNGManagerClientModule(auditEventStreamingConfig.getNgManagerClientConfig(),
        auditEventStreamingConfig.getServiceSecrets().getNgManagerServiceSecret(),
        AUDIT_EVENT_STREAMING.getServiceId()));
  }

  @Bean
  public void registerScheduleJobs(Injector injector) {
    log.info("Initializing scheduled jobs...");
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateSyncServiceImpl.class), 0L, 2L, TimeUnit.SECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateAsyncServiceImpl.class), 0L, 5L, TimeUnit.SECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateProgressServiceImpl.class), 0L, 5L, TimeUnit.SECONDS);
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  Map<Class, String> morphiaCustomCollectionNames() {
    return ImmutableMap.<Class, String>builder()
        .put(DelegateSyncTaskResponse.class, "auditEventStreaming_delegateSyncTaskResponses")
        .put(DelegateAsyncTaskResponse.class, "auditEventStreaming_delegateAsyncTaskResponses")
        .put(DelegateTaskProgressResponse.class, "auditEventStreaming_delegateTaskProgressResponses")
        .build();
  }

  private DelegateCallbackToken getDelegateCallbackToken(
      DelegateServiceGrpcClient delegateServiceClient, AuditEventStreamingConfig appConfig) {
    log.info("Generating Delegate callback token");
    final DelegateCallbackToken delegateCallbackToken =
        delegateServiceClient.registerCallback(DelegateCallback.newBuilder()
                                                   .setMongoDatabase(MongoDatabase.newBuilder()
                                                                         .setCollectionNamePrefix("auditEventStreaming")
                                                                         .setConnection("") // Register the URI
                                                                         .build())
                                                   .build());
    log.info("delegate callback token generated =[{}]", delegateCallbackToken.getToken());
    return delegateCallbackToken;
  }

  @Provides
  @Singleton
  Supplier<DelegateCallbackToken> getDelegateCallbackTokenSupplier(
      DelegateServiceGrpcClient delegateServiceGrpcClient) {
    return Suppliers.memoize(() -> getDelegateCallbackToken(delegateServiceGrpcClient, auditEventStreamingConfig));
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.client.impl.tailer;

import io.harness.event.EventPublisherGrpc;
import io.harness.event.EventPublisherGrpc.EventPublisherBlockingStub;
import io.harness.event.client.impl.EventPublisherConstants;
import io.harness.flow.BackoffScheduler;
import io.harness.govern.ProviderModule;
import io.harness.grpc.client.HarnessRoutingGrpcInterceptor;
import io.harness.security.X509KeyManagerBuilder;
import io.harness.security.X509TrustManagerBuilder;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.grpc.CallCredentials;
import io.grpc.Channel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import java.time.Duration;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.RollingChronicleQueue;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
public class DelegateTailerModule extends ProviderModule {
  private final Config config;

  @Provides
  @Singleton
  @Named("tailer")
  RollingChronicleQueue chronicleQueue() {
    return ChronicleQueue.singleBuilder(config.queueFilePath)
        .rollCycle(EventPublisherConstants.QUEUE_ROLL_CYCLE)
        .timeoutMS(EventPublisherConstants.QUEUE_TIMEOUT_MS)
        .build();
  }

  @Provides
  @Singleton
  @Named("tailer")
  BackoffScheduler backoffScheduler() {
    return new BackoffScheduler(ChronicleEventTailer.class.getSimpleName(), config.getMinDelay(), config.getMaxDelay());
  }

  @Named("event-server-channel")
  @Provides
  @Singleton
  @SneakyThrows
  Channel channel() {
    X509TrustManager trustManager = new X509TrustManagerBuilder().trustAllCertificates().build();
    SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient().trustManager(trustManager);

    if (StringUtils.isNotEmpty(config.clientCertificateFilePath)
        && StringUtils.isNotEmpty(config.clientCertificateKeyFilePath)) {
      X509KeyManager keyManager =
          new X509KeyManagerBuilder()
              .withClientCertificateFromFile(config.clientCertificateFilePath, config.clientCertificateKeyFilePath)
              .build();
      sslContextBuilder.keyManager(keyManager);
    }

    SslContext sslContext = sslContextBuilder.build();
    return NettyChannelBuilder.forTarget(config.publishTarget)
        .overrideAuthority(config.publishAuthority)
        .sslContext(sslContext)
        .intercept(HarnessRoutingGrpcInterceptor.EVENTS)
        .build();
  }

  @Provides
  @Singleton
  EventPublisherBlockingStub eventPublisherBlockingStub(
      @Named("event-server-channel") Channel channel, CallCredentials callCredentials) {
    return EventPublisherGrpc.newBlockingStub(channel).withCallCredentials(callCredentials);
  }

  @Value
  @Builder
  public static class Config {
    String publishTarget;
    String publishAuthority;
    String queueFilePath;
    @Builder.Default Duration minDelay = Duration.ofSeconds(1);
    @Builder.Default Duration maxDelay = Duration.ofMinutes(5);
    String clientCertificateFilePath;
    String clientCertificateKeyFilePath;

    // As of now ignored (always trusts all certs)
    boolean trustAllCertificates;
  }
}

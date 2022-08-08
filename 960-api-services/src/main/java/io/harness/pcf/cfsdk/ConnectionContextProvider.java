/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf.cfsdk;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pcf.model.PcfConstants.PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.network.Http;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRequestConfig;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.ProxyConfiguration;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class ConnectionContextProvider {
  private static final LoadingCache<String, ConnectionContext> contextCache =
      CacheBuilder.newBuilder()
          .maximumSize(100)
          .expireAfterAccess(30, TimeUnit.MINUTES)
          .removalListener((RemovalListener<String, ConnectionContext>)
                               entry -> ((DefaultConnectionContext) entry.getValue()).dispose())
          .build(new CacheLoader<String, ConnectionContext>() {
            @Override
            public ConnectionContext load(@NotNull String url) throws Exception {
              DefaultConnectionContext.Builder builder = DefaultConnectionContext.builder()
                                                             .apiHost(url)
                                                             .skipSslValidation(true)
                                                             .connectTimeout(Duration.ofMinutes(5))
                                                             .proxyConfiguration(getProxyConfiguration(url));
              return builder.build();
            }
          });

  private static Optional<ProxyConfiguration> getProxyConfiguration(String url) {
    String proxyHostName = Http.getProxyHostName();
    if (Http.shouldUseNonProxy(url) || isEmpty(proxyHostName)) {
      return Optional.empty();
    }

    Optional<Integer> port = Optional.empty();
    String proxyPort = Http.getProxyPort();
    if (isNotEmpty(proxyPort)) {
      port = Optional.of(Integer.parseInt(proxyPort));
    }

    return Optional.of(ProxyConfiguration.builder()
                           .host(proxyHostName)
                           .port(port)
                           .password(Optional.ofNullable(Http.getProxyPassword()))
                           .username(Optional.ofNullable(Http.getProxyUserName()))
                           .build());
  }

  public ConnectionContext getConnectionContext(CfRequestConfig pcfRequestConfig) throws PivotalClientApiException {
    try {
      if (pcfRequestConfig.isIgnorePcfConnectionContextCache()) {
        return createNewConnectionContext(pcfRequestConfig);
      }
      return contextCache.get(pcfRequestConfig.getEndpointUrl());
    } catch (Exception t) {
      throw new PivotalClientApiException(ExceptionUtils.getMessage(t));
    }
  }

  private ConnectionContext createNewConnectionContext(CfRequestConfig pcfRequestConfig) {
    long timeout = pcfRequestConfig.getTimeOutIntervalInMins() <= 0 ? 5 : pcfRequestConfig.getTimeOutIntervalInMins();
    DefaultConnectionContext.Builder builder =
        DefaultConnectionContext.builder()
            .apiHost(pcfRequestConfig.getEndpointUrl())
            .skipSslValidation(true)
            .connectTimeout(Duration.ofMinutes(timeout))
            .proxyConfiguration(getProxyConfiguration(pcfRequestConfig.getEndpointUrl()));
    if (pcfRequestConfig.isLimitPcfThreads()) {
      builder.threadPoolSize(1);
    } else {
      log.info(PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX + "Not limiting Pcf threads for Connection Context");
    }
    return builder.build();
  }
}

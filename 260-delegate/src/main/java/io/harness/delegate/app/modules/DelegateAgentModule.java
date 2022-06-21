/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules;

import static io.harness.configuration.DeployMode.DEPLOY_MODE;
import static io.harness.configuration.DeployMode.isOnPrem;
import static io.harness.delegate.service.DelegateAgentServiceImpl.getDelegateId;
import static io.harness.grpc.utils.DelegateGrpcConfigExtractor.extractAuthority;
import static io.harness.grpc.utils.DelegateGrpcConfigExtractor.extractTarget;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.delegate.app.DelegateGrpcServiceModule;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.task.citasks.CITaskFactoryModule;
import io.harness.delegate.task.k8s.apiclient.KubernetesApiClientFactoryModule;
import io.harness.event.client.impl.appender.AppenderModule;
import io.harness.event.client.impl.appender.AppenderModule.Config;
import io.harness.event.client.impl.tailer.DelegateTailerModule;
import io.harness.grpc.delegateservice.DelegateServiceGrpcAgentClientModule;
import io.harness.grpc.pingpong.PingPongModule;
import io.harness.logstreaming.LogStreamingModule;
import io.harness.managerclient.DelegateManagerClientModule;
import io.harness.metrics.MetricRegistryModule;
import io.harness.perpetualtask.PerpetualTaskWorkerModule;
import io.harness.serializer.KryoModule;

import software.wings.delegatetasks.k8s.client.KubernetesClientFactoryModule;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class DelegateAgentModule extends AbstractModule {
  private final DelegateConfiguration configuration;
  private final boolean isImmutableDelegate;

  @Override
  protected void configure() {
    super.configure();

    install(new DelegateHealthModule());
    install(KryoModule.getInstance());
    install(new DelegateKryoModule());
    install(new MetricRegistryModule(new MetricRegistry()));

    install(new DelegateManagerClientModule(configuration.getManagerUrl(), configuration.getVerificationServiceUrl(),
        configuration.getCvNextGenUrl(), configuration.getAccountId(), configuration.getDelegateToken()));

    install(new LogStreamingModule(configuration.getLogStreamingServiceBaseUrl()));
    install(new DelegateGrpcClientModule(configuration));

    configureCcmEventPublishing();
    install(new PerpetualTaskWorkerModule());
    install(new PingPongModule());

    install(KubernetesClientFactoryModule.getInstance());
    install(KubernetesApiClientFactoryModule.getInstance());
    install(new CITaskFactoryModule());
    install(new DelegateModule(configuration));

    if (configuration.isGrpcServiceEnabled()) {
      install(DelegateServiceGrpcAgentClientModule.getInstance());
      install(
          new DelegateGrpcServiceModule(configuration.getGrpcServiceConnectorPort(), configuration.getDelegateToken()));
    }

    install(new DelegateTokensModule(configuration));
  }

  private void configureCcmEventPublishing() {
    final String deployMode = System.getenv(DEPLOY_MODE);
    if (!isOnPrem(deployMode) && isImmutableDelegate) {
      final String managerHostAndPort = System.getenv("MANAGER_HOST_AND_PORT");
      if (isNotBlank(managerHostAndPort)) {
        log.info("Running immutable delegate, starting CCM event tailer");
        final DelegateTailerModule.Config tailerConfig =
            DelegateTailerModule.Config.builder()
                .accountId(configuration.getAccountId())
                .accountSecret(configuration.getDelegateToken())
                .queueFilePath(configuration.getQueueFilePath())
                .publishTarget(extractTarget(managerHostAndPort))
                .publishAuthority(extractAuthority(managerHostAndPort, "events"))
                .build();
        install(new DelegateTailerModule(tailerConfig));
      } else {
        log.warn("Unable to configure event publisher configs. Event publisher will be disabled");
      }
    } else {
      log.info("Skip running tailer by delegate. For mutable it runs in watcher, for on prem we never run it.");
    }
    final Config appenderConfig = Config.builder().queueFilePath(configuration.getQueueFilePath()).build();
    install(new AppenderModule(appenderConfig, () -> getDelegateId().orElse("UNREGISTERED")));
  }
}

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
import static io.harness.grpc.utils.DelegateGrpcConfigExtractor.extractAndPrepareAuthority;
import static io.harness.grpc.utils.DelegateGrpcConfigExtractor.extractTarget;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.delegate.app.DelegateGrpcServiceModule;
import io.harness.delegate.app.modules.common.DelegateHealthModule;
import io.harness.delegate.app.modules.common.DelegateTokensModule;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.task.citasks.CITaskFactoryModule;
import io.harness.delegate.task.k8s.apiclient.KubernetesApiClientFactoryModule;
import io.harness.event.client.impl.appender.AppenderModule;
import io.harness.event.client.impl.appender.AppenderModule.Config;
import io.harness.event.client.impl.tailer.DelegateTailerModule;
import io.harness.grpc.delegateservice.DelegateServiceGrpcAgentClientModule;
import io.harness.logstreaming.LogStreamingModule;
import io.harness.managerclient.DelegateManagerEncryptionDecryptionHarnessSMModule;
import io.harness.managerclient.VerificationServiceClientModule;
import io.harness.metrics.MetricRegistryModule;
import io.harness.perpetualtask.PerpetualTaskWorkerModule;
import io.harness.serializer.KryoModule;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
@Slf4j
public class DelegateAgentModule extends AbstractModule {
  private final DelegateConfiguration configuration;

  @Override
  protected void configure() {
    super.configure();

    if (StringUtils.isNotEmpty(configuration.getClientCertificateFilePath())
        && StringUtils.isNotEmpty(configuration.getClientCertificateKeyFilePath())) {
      log.info("Delegate is running with mTLS enabled.");
    }

    install(new DelegateTokensModule(configuration));
    install(new DelegateServiceTokenModule(configuration));
    install(new DelegateHealthModule());
    install(KryoModule.getInstance());
    install(new DelegateKryoModule());
    install(new MetricRegistryModule(new MetricRegistry()));

    install(new DelegateManagerClientModule());
    install(new VerificationServiceClientModule(configuration.getCvNextGenUrl(),
        configuration.getClientCertificateFilePath(), configuration.getClientCertificateKeyFilePath(),
        configuration.isTrustAllCertificates()));

    install(new LogStreamingModule(configuration.getLogStreamingServiceBaseUrl(),
        configuration.getClientCertificateFilePath(), configuration.getClientCertificateKeyFilePath(),
        configuration.isTrustAllCertificates()));
    install(new DelegateManagerGrpcClientModule(configuration));

    configureCcmEventPublishing();
    install(new PerpetualTaskWorkerModule());

    install(KubernetesApiClientFactoryModule.getInstance());
    install(new CITaskFactoryModule());
    install(new DelegateModule(configuration));
    install(new DelegateManagerEncryptionDecryptionHarnessSMModule(configuration));

    if (configuration.isGrpcServiceEnabled()) {
      install(DelegateServiceGrpcAgentClientModule.getInstance());
      install(
          new DelegateGrpcServiceModule(configuration.getGrpcServiceConnectorPort(), configuration.getDelegateToken()));
    }
  }

  private void configureCcmEventPublishing() {
    final String deployMode = System.getenv(DEPLOY_MODE);
    final String managerHostAndPort = System.getenv("MANAGER_HOST_AND_PORT");
    if (isNotBlank(managerHostAndPort)) {
      log.info("Running delegate, starting CCM event tailer");
      final DelegateTailerModule.Config tailerConfig;
      if (isOnPrem(deployMode)) {
        log.info("fetching OnPrem ChronicleEventsTailer Config");
        tailerConfig = getOnPremTailerConfig();
      } else {
        tailerConfig = getTailerConfig(managerHostAndPort);
      }
      install(new DelegateTailerModule(tailerConfig));
    } else {
      log.warn("Unable to configure event publisher configs. Event publisher will be disabled");
    }
    final Config appenderConfig = Config.builder().queueFilePath(configuration.getQueueFilePath()).build();
    install(new AppenderModule(appenderConfig, () -> getDelegateId().orElse("UNREGISTERED")));
    install(new EventPublisherModule());
  }

  private DelegateTailerModule.Config getTailerConfig(String managerHostAndPort) {
    return DelegateTailerModule.Config.builder()
        .queueFilePath(configuration.getQueueFilePath())
        .publishTarget(extractTarget(managerHostAndPort))
        .publishAuthority(extractAndPrepareAuthority(
            managerHostAndPort, "events", configuration.isGrpcAuthorityModificationDisabled()))
        .clientCertificateFilePath(configuration.getClientCertificateFilePath())
        .clientCertificateKeyFilePath(configuration.getClientCertificateKeyFilePath())
        .trustAllCertificates(configuration.isTrustAllCertificates())
        .build();
  }

  private DelegateTailerModule.Config getOnPremTailerConfig() {
    return DelegateTailerModule.Config.builder()
        .queueFilePath(configuration.getQueueFilePath())
        .clientCertificateFilePath(configuration.getClientCertificateFilePath())
        .clientCertificateKeyFilePath(configuration.getClientCertificateKeyFilePath())
        .trustAllCertificates(configuration.isTrustAllCertificates())
        .build();
  }
}

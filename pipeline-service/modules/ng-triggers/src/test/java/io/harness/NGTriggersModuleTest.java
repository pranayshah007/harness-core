/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.account.AccountClient;
import io.harness.callback.DelegateCallbackToken;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.DelegateServiceGrpc;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.eventsframework.api.Producer;
import io.harness.ff.FeatureFlagService;
import io.harness.govern.ProviderModule;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngtriggers.TriggerConfiguration;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.service.impl.NGTriggerServiceImpl;
import io.harness.outbox.api.OutboxService;
import io.harness.polling.client.PollingResourceClient;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.repositories.spring.TriggerWebhookEventRepository;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.NGTriggerRegistrars;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.webhook.remote.WebhookEventClient;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class NGTriggersModuleTest extends CategoryTest {
  private NGTriggersModule ngTriggersModule;
  @Mock TriggerConfiguration triggerConfig;
  @Mock List<YamlSchemaRootClass> yamlSchemaRootClassList;
  @Mock Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;

  @Before
  public void setup() {
    initMocks(this);
  }
  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testNGTriggerModule() {
    ServiceHttpClientConfig pmsHttpClientConfig =
        ServiceHttpClientConfig.builder().baseUrl("http://localhost:7143").build();
    ngTriggersModule = NGTriggersModule.getInstance(triggerConfig, pmsHttpClientConfig, "");

    List<Module> modules = new ArrayList<>();
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      EntitySetupUsageClient getEntitySetupUsageClient() {
        return mock(EntitySetupUsageClient.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> registrars() {
        return NGTriggerRegistrars.kryoRegistrars;
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      SecretNGManagerClient getSecretNGManagerClient() {
        return mock(SecretNGManagerClient.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      FeatureFlagService getSFeatureFlagService() {
        return mock(FeatureFlagService.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Supplier<DelegateCallbackToken> getSFeatureFlagService() {
        return delegateCallbackTokenSupplier;
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      List<YamlSchemaRootClass> getYamlSchemaRootClass() {
        return yamlSchemaRootClassList;
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Named("referenceFalseKryoSerializer")
      @Singleton
      KryoSerializer getReferenceFalseKryoSerializer() {
        return mock(KryoSerializer.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Named("setup_usage")
      @Singleton
      Producer getProducer() {
        return mock(Producer.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Named("driver-installed-in-ng-service")
      @Singleton
      BooleanSupplier getBooleanSupplier() {
        return mock(BooleanSupplier.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      AccessControlClient getAccessControlClient() {
        return mock(AccessControlClient.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      PmsFeatureFlagService getPmsFeatureFlagService() {
        return mock(PmsFeatureFlagService.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      SecretManagerClientService getSecretManagerClientService() {
        return mock(SecretManagerClientService.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      TriggerWebhookEventRepository getTriggerWebhookEventRepository() {
        return mock(TriggerWebhookEventRepository.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      DelegateSyncService getDelegateSyncService() {
        return mock(DelegateSyncService.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      NGTriggerRepository getNGTriggerRepository() {
        return mock(NGTriggerRepository.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      SCMGrpc.SCMBlockingStub getSCMGrpcSCMBlockingStub() {
        return mock(SCMGrpc.SCMBlockingStub.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      DelegateServiceGrpc.DelegateServiceBlockingStub getDelegateServiceGrpcDelegateServiceBlockingStub() {
        return mock(DelegateServiceGrpc.DelegateServiceBlockingStub.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      AccountClient getAccountClient() {
        return mock(AccountClient.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      TriggerEventHistoryRepository getTriggerEventHistoryRepository() {
        return mock(TriggerEventHistoryRepository.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      DelegateAsyncService getDelegateAsyncService() {
        return mock(DelegateAsyncService.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      PollingResourceClient getPollingResourceClient() {
        return mock(PollingResourceClient.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      OutboxService getOutboxService() {
        return mock(OutboxService.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      NGSettingsClient getNGSettingsClient() {
        return mock(NGSettingsClient.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      ConnectorResourceClient getConnectorResourceClient() {
        return mock(ConnectorResourceClient.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      WebhookEventClient getWebhookEventClient() {
        return mock(WebhookEventClient.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      ExecutorService getExecutorService() {
        return mock(ExecutorService.class);
      }
    });
    modules.add(ngTriggersModule);
    Injector injector = Guice.createInjector(modules);

    NGTriggerService ngTriggerService = injector.getInstance(NGTriggerService.class);
    assertThat(ngTriggerService).isNotNull();
    assertThat(ngTriggerService).isInstanceOf(NGTriggerServiceImpl.class);
  }
}

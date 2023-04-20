/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core;

import static io.harness.ConnectorConstants.CONNECTOR_DECORATOR_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.rule.OwnerRule.VIKAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheModule;
import io.harness.category.element.UnitTests;
import io.harness.connector.impl.DefaultConnectorServiceImpl;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.ff.FeatureFlagService;
import io.harness.govern.ProviderModule;
import io.harness.ng.ConnectorServiceImpl;
import io.harness.ng.core.activityhistory.service.NGActivityService;
import io.harness.ng.core.api.NGSecretManagerService;
import io.harness.ng.core.api.impl.NGSecretManagerServiceImpl;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.eventsframework.EventsFrameworkModule;
import io.harness.ng.opa.OpaService;
import io.harness.ng.opa.OpaServiceImpl;
import io.harness.ng.opa.entities.secret.OpaSecretService;
import io.harness.ng.opa.entities.secret.OpaSecretServiceImpl;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.opaclient.OpaServiceClient;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.redisConsumer.DebeziumConsumersConfig;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.repositories.ConnectorRepository;
import io.harness.repositories.accountsetting.AccountSettingRepository;
import io.harness.repositories.ng.core.spring.SecretRepository;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SecretManagementClientModule;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.NextGenRegistrars;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.template.remote.TemplateResourceClient;

import software.wings.service.intfc.FileService;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class SecretManagementModuleTest extends CategoryTest {
  private SecretManagementModule secretManagementModule;
  private CacheModule cacheModule;
  private SecretManagementClientModule secretManagementClientModule;
  @Mock private SecretRepository secretRepository;
  @Mock private ConnectorRepository connectorRepository;
  @Mock private ConnectorService connectorService;
  @Mock private AccountClient accountClient;
  @Mock private AccessControlClient accessControlClient;
  @Mock private NGConnectorSecretManagerService ngConnectorSecretManagerService;
  public static final String OUTBOX_TRANSACTION_TEMPLATE = "OUTBOX_TRANSACTION_TEMPLATE";

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testSecretManagementModule() {
    ServiceHttpClientConfig secretManagerClientConfig =
        ServiceHttpClientConfig.builder().baseUrl("http://localhost:7143").build();
    String serviceSecret = "test_secret";
    secretManagementModule = new SecretManagementModule();
    secretManagementClientModule =
        new SecretManagementClientModule(secretManagerClientConfig, serviceSecret, "NextGenManager");
    cacheModule = new CacheModule(CacheConfig.builder().build());

    List<Module> modules = new ArrayList<>();
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> registrars() {
        return NextGenRegistrars.kryoRegistrars;
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Map<Class<? extends Exception>, ExceptionHandler> exceptionHandler() {
        return new HashMap<>();
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      SecretRepository repository() {
        return mock(SecretRepository.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      ConnectorRepository connectorRepository() {
        return mock(ConnectorRepository.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      AccountSettingRepository accountSettingRepository() {
        return mock(AccountSettingRepository.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      MongoTemplate getMongoTemplate() {
        return mock(MongoTemplate.class);
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
      @Named("PRIVILEGED")
      AccountClient getAccountClient() {
        return mock(AccountClient.class);
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
      EntitySetupUsageService entitySetupUsageService() {
        return mock(EntitySetupUsageService.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      FileService fileService() {
        return mock(FileService.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      DelegateGrpcClientWrapper registerDelegateGrpcClientWrapper() {
        return mock(DelegateGrpcClientWrapper.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      NGActivityService registerNGActivityService() {
        return mock(NGActivityService.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      SecretManagerClientService registerNGSecretManagerClientService() {
        return mock(SecretManagerClientService.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      FeatureFlagService registerFeatureFlagService() {
        return mock(FeatureFlagService.class);
      }
    });
    modules.add(new EventsFrameworkModule(EventsFrameworkConfiguration.builder()
                                              .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                                              .build(),
        DebeziumConsumersConfig.builder().build()));
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      OutboxService registerOutboxService() {
        return mock(OutboxService.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      @Named(OUTBOX_TRANSACTION_TEMPLATE)
      TransactionTemplate registerTransactionTemplate() {
        return mock(TransactionTemplate.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      @Named(DEFAULT_CONNECTOR_SERVICE)
      ConnectorService registerConnecterService() {
        return mock(DefaultConnectorServiceImpl.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      @Named(CONNECTOR_DECORATOR_SERVICE)
      ConnectorService registerConnecterService() {
        return mock(ConnectorServiceImpl.class);
      }
    });
    modules.add(secretManagementModule);
    modules.add(secretManagementClientModule);
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      OpaService registerConnecterService() {
        return mock(OpaServiceImpl.class);
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      OpaSecretService registerConnecterService() {
        return mock(OpaSecretServiceImpl.class);
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      OpaServiceClient registerOpaServiceClientService() {
        return mock(OpaServiceClient.class);
      }
    });
    modules.add(cacheModule);
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      TemplateResourceClient getTemplateResourceClient() {
        return mock(TemplateResourceClient.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      NGSettingsClient getNGSettingsClient() {
        return mock(NGSettingsClient.class);
      }
    });

    Injector injector = Guice.createInjector(modules);

    NGSecretManagerService ngSecretManagerService = injector.getInstance(NGSecretManagerService.class);
    assertThat(ngSecretManagerService).isNotNull();
    assertThat(ngSecretManagerService).isInstanceOf(NGSecretManagerServiceImpl.class);
  }
}

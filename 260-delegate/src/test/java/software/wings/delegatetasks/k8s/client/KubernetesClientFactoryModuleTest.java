/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.client;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.govern.ProviderModule;
import io.harness.k8s.KubernetesContainerService;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@Slf4j
@RunWith(JUnit4.class)
@TargetModule(_930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class KubernetesClientFactoryModuleTest extends CategoryTest {
  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testKubernetesClientFactoryModule() {
    List<Module> modules = new ArrayList<>();
    modules.add(KubernetesClientFactoryModule.getInstance());
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      GkeClusterService gkeClusterService() {
        return mock(GkeClusterService.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      SecretDecryptionService secretDecryptionService() {
        return mock(SecretDecryptionService.class);
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      KubernetesContainerService kubernetesContainerService() {
        return mock(KubernetesContainerService.class);
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      EncryptionService encryptionService() {
        return mock(EncryptionService.class);
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      TimeLimiter timeLimiter() {
        return mock(TimeLimiter.class);
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper() {
        return mock(ContainerDeploymentDelegateHelper.class);
      }
    });

    Injector injector = Guice.createInjector(modules);

    KubernetesClientFactory k8sClientFactory = injector.getInstance(KubernetesClientFactory.class);
    assertThat(k8sClientFactory).isNotNull();
    assertThat(k8sClientFactory).isInstanceOf(HarnessKubernetesClientFactory.class);
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.mongo.MongoConfig;
import io.harness.rule.Owner;

import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Element;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.TypeConverterBinding;
import com.mongodb.MongoClient;
import com.mongodb.ReadPreference;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineOutboxPersistenceConfigTest extends CategoryTest {
  PipelineOutboxPersistenceConfig persistenceConfig;

  int connectTimeout = 100;
  int serverSelectionTimeout = 50;
  int maxConnectionIdleTime = 10;
  int connectionsPerHost = 5;
  MongoConfig mongoConfig = MongoConfig.builder()
                                .connectTimeout(connectTimeout)
                                .serverSelectionTimeout(serverSelectionTimeout)
                                .maxConnectionIdleTime(maxConnectionIdleTime)
                                .connectionsPerHost(connectionsPerHost)
                                .build();
  @Before
  public void setup() throws IOException {
    persistenceConfig = spy(new PipelineOutboxPersistenceConfig(new NoOpInjector()));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetMongoClient() {
    on(persistenceConfig).set("mongoConfig", mongoConfig);
    MongoClient mongoClient = persistenceConfig.mongoClient();

    assertEquals(mongoClient.getMongoClientOptions().getConnectTimeout(), connectTimeout);
    assertEquals(mongoClient.getMongoClientOptions().getServerSelectionTimeout(), serverSelectionTimeout);
    assertEquals(mongoClient.getMongoClientOptions().getMaxConnectionIdleTime(), maxConnectionIdleTime);
    assertEquals(mongoClient.getMongoClientOptions().getConnectionsPerHost(), connectionsPerHost);
    assertEquals(mongoClient.getMongoClientOptions().getReadPreference(), ReadPreference.secondary());
    assertTrue(mongoClient.getMongoClientOptions().getRetryWrites());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testAutoIndexCreation() {
    assertFalse(persistenceConfig.autoIndexCreation());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testMongoTemplate() throws Exception {
    assertThatCode(() -> persistenceConfig.mongoTemplate()).doesNotThrowAnyException();
  }

  private class NoOpInjector implements Injector {
    @Override
    public void injectMembers(Object o) {}

    @Override
    public <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> typeLiteral) {
      return null;
    }

    @Override
    public <T> MembersInjector<T> getMembersInjector(Class<T> aClass) {
      return null;
    }

    @Override
    public Map<Key<?>, Binding<?>> getBindings() {
      return null;
    }

    @Override
    public Map<Key<?>, Binding<?>> getAllBindings() {
      return null;
    }

    @Override
    public <T> Binding<T> getBinding(Key<T> key) {
      return null;
    }

    @Override
    public <T> Binding<T> getBinding(Class<T> aClass) {
      return null;
    }

    @Override
    public <T> Binding<T> getExistingBinding(Key<T> key) {
      return null;
    }

    @Override
    public <T> List<Binding<T>> findBindingsByType(TypeLiteral<T> typeLiteral) {
      return null;
    }

    @Override
    public <T> Provider<T> getProvider(Key<T> key) {
      return null;
    }

    @Override
    public <T> Provider<T> getProvider(Class<T> aClass) {
      return null;
    }

    @Override
    public <T> T getInstance(Key<T> key) {
      return null;
    }

    @Override
    public <T> T getInstance(Class<T> aClass) {
      return (T) mongoConfig;
    }

    @Override
    public Injector getParent() {
      return null;
    }

    @Override
    public Injector createChildInjector(Iterable<? extends Module> iterable) {
      return null;
    }

    @Override
    public Injector createChildInjector(Module... modules) {
      return null;
    }

    @Override
    public Map<Class<? extends Annotation>, Scope> getScopeBindings() {
      return null;
    }

    @Override
    public Set<TypeConverterBinding> getTypeConverterBindings() {
      return null;
    }

    @Override
    public List<Element> getElements() {
      return null;
    }

    @Override
    public Map<TypeLiteral<?>, List<InjectionPoint>> getAllMembersInjectorInjectionPoints() {
      return null;
    }
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.waiter;

import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.config.PublisherConfiguration;
import io.harness.rule.Owner;
import io.harness.version.VersionInfoManager;

import com.google.inject.Injector;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.data.mongodb.core.MongoTemplate;

public class PmsNotifyEventListenerTest {
  @Mock Injector injector;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() throws IOException {
    doReturn(null).when(injector).getInstance(MongoTemplate.class);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateEventListener() {
    VersionInfoManager versionInfoManager = new VersionInfoManager();
    PublisherConfiguration publisherConfiguration = new PublisherConfiguration();
    PmsNotifyEventListener pmsNotifyEventListener = new PmsNotifyEventListener(
        injector, versionInfoManager, publisherConfiguration, WaiterConfiguration.PersistenceLayer.SPRING);
    PmsNotifyEventListener pmsNotifyEventListener2 = new PmsNotifyEventListener(
        injector, versionInfoManager, publisherConfiguration, WaiterConfiguration.PersistenceLayer.MORPHIA);
    assertThat(pmsNotifyEventListener).isNotEqualTo(pmsNotifyEventListener2);
  }
}

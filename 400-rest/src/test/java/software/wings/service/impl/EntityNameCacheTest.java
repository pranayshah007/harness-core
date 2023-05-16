/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.ADWAIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.persistence.NameAccess;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.EntityType;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class EntityNameCacheTest extends WingsBaseTest {
  @Mock private WingsPersistence mockWingsPersistence;
  @Inject @InjectMocks private EntityNameCache entityNameCache;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testEntityCache() throws Exception {
    Query mockQuery = mock(Query.class);
    doReturn(mockQuery).when(mockWingsPersistence).createQuery(any());
    doReturn(mockQuery).when(mockQuery).filter(anyString(), anyString());
    doReturn(mockQuery).when(mockQuery).project(anyString(), anyBoolean());

    NameAccess nameAccessApp = new NameAccess() {
      @Override
      public String getName() {
        return "App1";
      }
    };

    NameAccess nameAccessService = new NameAccess() {
      @Override
      public String getName() {
        return "Service1";
      }
    };

    NameAccess nameAccessTrigger = new NameAccess() {
      @Override
      public String getName() {
        return "Trigger1";
      }
    };

    doReturn(nameAccessApp).doReturn(nameAccessService).doReturn(nameAccessTrigger).when(mockQuery).get();

    String name = entityNameCache.getEntityName(EntityType.APPLICATION, "12345");
    assertThat(name).isEqualTo("App1");
    verify(mockWingsPersistence, times(1)).createQuery(any());

    name = entityNameCache.getEntityName(EntityType.APPLICATION, "12345");
    assertThat(name).isEqualTo("App1");
    verify(mockWingsPersistence, times(1)).createQuery(any());

    name = entityNameCache.getEntityName(EntityType.SERVICE, "12345");
    assertThat(name).isEqualTo("Service1");
    verify(mockWingsPersistence, times(2)).createQuery(any());

    name = entityNameCache.getEntityName(EntityType.SERVICE, "12345");
    assertThat(name).isEqualTo("Service1");
    verify(mockWingsPersistence, times(2)).createQuery(any());

    name = entityNameCache.getEntityName(EntityType.TRIGGER, "12345");
    assertThat(name).isEqualTo("Trigger1");
    verify(mockWingsPersistence, times(3)).createQuery(any());

    name = entityNameCache.getEntityName(EntityType.TRIGGER, "12345");
    assertThat(name).isEqualTo("Trigger1");
    verify(mockWingsPersistence, times(3)).createQuery(any());

    try {
      entityNameCache.getEntityName(EntityType.APPLICATION, null);
      assertThat(false).isTrue();
    } catch (Exception e) {
      assertThat(true).isTrue();
    }

    try {
      entityNameCache.getEntityName(null, "12345");
      assertThat(false).isTrue();
    } catch (Exception e) {
      assertThat(true).isTrue();
    }
  }
}

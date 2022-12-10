/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migrations;

import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.VUK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.migration.CVNGBackgroundMigrationList;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.migration.service.MigrationChecklist;
import io.harness.rule.Owner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

public class CVNGMigrationServiceTest extends CvNextGenTestBase {
  private static Answer executeRunnable(ArgumentCaptor<Runnable> runnableCaptor) {
    return invocation -> {
      runnableCaptor.getValue().run();
      return null;
    };
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void versionsShouldBeUnique() {
    ExecutorService executorService = Mockito.mock(ExecutorService.class);
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));

    Set<Integer> versions = new HashSet<>();
    CVNGBackgroundMigrationList.getMigrations().forEach(pair -> {
      assertThat(versions.contains(pair.getKey())).isFalse();
      versions.add(pair.getKey());
    });
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void versionsShouldBeSequential() {
    ExecutorService executorService = Mockito.mock(ExecutorService.class);
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));

    AtomicInteger last = new AtomicInteger(-1);
    CVNGBackgroundMigrationList.getMigrations().forEach(pair -> {
      if (last.get() == -1) {
        last.set(pair.getKey() - 1);
      }
      assertThat(pair.getKey() - 1).isEqualTo(last.get());
      last.set(pair.getKey());
    });
    assertThat(last.get()).isNotEqualTo(-1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testMigrationChecklist_returnValidDescInChecklist()
      throws IllegalAccessException, InstantiationException, InvocationTargetException {
    for (Pair<Integer, Class<? extends CVNGMigration>> pair : CVNGBackgroundMigrationList.getMigrations()) {
      Method[] methods = MigrationChecklist.class.getDeclaredMethods();
      CVNGMigration CVNGMigration = pair.getValue().newInstance();
      for (Method method : methods) {
        ChecklistItem checklistItem = (ChecklistItem) method.invoke(CVNGMigration);
        assertThat(checklistItem)
            .isNotNull()
            .withFailMessage(
                "Checklist item can not be null, please put a checklist with desc for method name: %s, migration: %s",
                method.getName(), CVNGMigration.getClass().getName());
        assertThat(checklistItem.getDesc())
            .isNotBlank()
            .withFailMessage(
                "Checklist desc can not be empty, please put a checklist with desc for method name %s, migration: %s",
                method.getName(), CVNGMigration.getClass().getName());
      }
    }
  }
}

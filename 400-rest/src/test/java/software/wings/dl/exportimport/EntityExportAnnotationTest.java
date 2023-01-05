/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.dl.exportimport;

import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.fail;

import io.harness.CategoryTest;
import io.harness.annotation.HarnessEntity;
import io.harness.beans.FeatureFlag;
import io.harness.category.element.UnitTests;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.rule.Owner;

import software.wings.beans.Schema;

import dev.morphia.annotations.Entity;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.reflections.Reflections;

/**
 * @author marklu on 9/27/19
 */
@Slf4j
public class EntityExportAnnotationTest extends CategoryTest {
  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void findExportableEntityTypes() {
    Set<Class> morphiaClasses = collectMorphiaClasses();

    final AtomicInteger violationCounter = new AtomicInteger();
    morphiaClasses.forEach(clazz -> {
      if (clazz.getDeclaredAnnotation(Entity.class) != null) {
        // Find out non-abstract classes with both 'Entity' and 'HarnessEntity' annotation.
        boolean isExportable = isAnnotatedExportable(clazz);
        if (isExportable) {
          boolean accountOrAppIdPresent = checkAccountIdOrAppIdPresence(clazz);
          if (!accountOrAppIdPresent && !Schema.class.equals(clazz) && !FeatureFlag.class.equals(clazz)) {
            violationCounter.incrementAndGet();
            log.info(
                "Violation: Entity class {} is annotated as exportable, but doesn't have 'accountId' or 'appId' field defined.",
                clazz.getName());
          }
        }
      }
    });

    if (violationCounter.get() > 0) {
      fail("Entity class should be annotated either with @HarnessEntity or @HarnessEntity(exportable = false)."
          + " Exportable entity need to have either an 'accountId' or 'appId' field defined. Found "
          + violationCounter.get() + " violations.");
    }
  }

  private boolean isAnnotatedExportable(Class<?> clazz) {
    HarnessEntity harnessEntity = clazz.getAnnotation(HarnessEntity.class);
    return harnessEntity != null && harnessEntity.exportable();
  }

  private boolean checkAccountIdOrAppIdPresence(Class clazz) {
    return isFieldPresent(clazz, "accountId") || isFieldPresent(clazz, "appId");
  }

  private boolean isFieldPresent(Class clazz, String fieldName) {
    Field field = null;
    try {
      // log.info("Search for field '{}' in class: {}", fieldName, clazz);
      field = clazz.getDeclaredField(fieldName);
    } catch (Exception e) {
      // Ignore.
    }
    if (field != null) {
      return true;
    } else {
      Class superClazz = clazz.getSuperclass();
      // log.info("Search for field '{}' in super class: {}", fieldName, superClazz);
      return superClazz != null && isFieldPresent(superClazz, fieldName);
    }
  }

  private synchronized Set<Class> collectMorphiaClasses() {
    Set<Class> morphiaClasses = new ConcurrentHashSet<>();

    try {
      Reflections reflections = new Reflections("io.harness.serializer.morphia");
      for (Class clazz : reflections.getSubTypesOf(MorphiaRegistrar.class)) {
        Constructor<?> constructor = clazz.getConstructor();
        final MorphiaRegistrar morphiaRegistrar = (MorphiaRegistrar) constructor.newInstance();

        morphiaRegistrar.registerClasses(morphiaClasses);
      }
    } catch (Exception e) {
      log.error("Failed to initialize morphia object factory", e);
      System.exit(1);
    }

    return morphiaClasses;
  }
}

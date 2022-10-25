/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import static io.harness.packages.HarnessPackages.IO_HARNESS;
import static io.harness.packages.HarnessPackages.SOFTWARE_WINGS;
import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.kryo.KryoSerializer;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.appmanifest.ApplicationManifest;

import com.google.inject.Inject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.reflections.Reflections;

@Slf4j
public class KryoUtilTest extends WingsBaseTest {
  @Inject KryoSerializer kryoSerializer;

  Class<?> classToRunTest = ApplicationManifest.class;

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testKryoSerializable() {
    Reflections reflection = new Reflections(IO_HARNESS, SOFTWARE_WINGS);
    Set<Class<?>> classesInvolved = new HashSet<>();
    getAllInvolvedClasses(classesInvolved, classToRunTest, reflection);
    Set<Class<?>> errorClasses = new HashSet<>();
    classesInvolved.forEach(clazz -> {
      if (!kryoSerializer.isRegistered(clazz) && !isAbstractOrInterface(clazz)) {
        log.error("Class [{}] not registered in kryo.", clazz);
        errorClasses.add(clazz);
      }
    });
    assertThat(errorClasses).isEmpty();
  }

  private void getAllInvolvedClasses(Set<Class<?>> classesInvolved, Class<?> clazz, Reflections reflections) {
    if (classesInvolved.contains(clazz)) {
      return;
    }
    classesInvolved.add(clazz);
    // if class has subtypes traverse them.
    Set<Class<?>> subTypesOf = reflections.getSubTypesOf((Class<Object>) clazz);
    if (!subTypesOf.isEmpty()) {
      subTypesOf.forEach(subtype -> {
        if (checkIfClassShouldBeTraversed(subtype)) {
          getAllInvolvedClasses(classesInvolved, subtype, reflections);
        }
      });
    }

    // traverse all fields of that class.
    for (Field field : clazz.getDeclaredFields()) {
      field.setAccessible(true);
      final Class<?> type = field.getType();
      if (classesInvolved.contains(type)) {
        continue;
      }
      if (checkIfClassShouldBeTraversed(type)) {
        getAllInvolvedClasses(classesInvolved, type, reflections);
      }
    }
  }

  private boolean isAbstractOrInterface(Class<?> clazz) {
    return Modifier.isAbstract(clazz.getModifiers());
  }

  public boolean checkIfClassShouldBeTraversed(Class<?> clazz) {
    // Generating only for harness classes hence checking if package is software.wings or io.harness.
    if (clazz.getCanonicalName() == null) {
      return false;
    }
    return !clazz.isPrimitive()
        && (clazz.getCanonicalName().startsWith("io.harness") || clazz.getCanonicalName().startsWith("software.wings"));
  }
}

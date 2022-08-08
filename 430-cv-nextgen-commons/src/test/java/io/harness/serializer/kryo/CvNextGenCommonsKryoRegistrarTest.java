/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.packages.HarnessPackages.IO_HARNESS;
import static io.harness.packages.HarnessPackages.SOFTWARE_WINGS;
import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenCommonsTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.DataCollectionInfo;
import io.harness.reflection.CodeUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.reflections.Reflections;

@Slf4j
public class CvNextGenCommonsKryoRegistrarTest extends CvNextGenCommonsTestBase {
  KryoSerializer kryoSerializer;

  @Before
  public void before() {
    kryoSerializer = new KryoSerializer(
        new HashSet<>(Arrays.asList(CvNextGenCommonsKryoRegistrar.class, CvNextGenBeansKryoRegistrar.class)));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testKryoSerializableForAllDataCollectionInfoSubClasses() {
    Reflections reflection = new Reflections(IO_HARNESS, SOFTWARE_WINGS);
    Set<Class<? extends DataCollectionInfo>> classesToTest = reflection.getSubTypesOf(DataCollectionInfo.class);
    Set<Class<?>> classesInvolved = new HashSet<>();
    classesToTest.forEach(classToTest -> getAllInvolvedClasses(classesInvolved, classToTest, reflection));
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
      Class<?> type = field.getType();
      if (Collection.class.isAssignableFrom(type)) {
        type = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
      }
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
    final String location = CodeUtils.location(clazz);
    final String currentLocation = CodeUtils.location(CvNextGenCommonsKryoRegistrar.class);
    if (!currentLocation.equals(location)) {
      return false;
    }
    return !clazz.isPrimitive()
        && (clazz.getCanonicalName().startsWith(IO_HARNESS) || clazz.getCanonicalName().startsWith(SOFTWARE_WINGS));
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.VerificationApplication;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.persistence.PersistentEntity;
import io.harness.reflection.HarnessReflections;
import io.harness.rule.Owner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DatabaseEntitiesTest extends CvNextGenTestBase {
  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCompoudIndexNameIsPresent() {
    Set<Class<? extends PersistentEntity>> reflections =
        HarnessReflections.get()
            .getSubTypesOf(PersistentEntity.class)
            .stream()
            .filter(klazz
                -> StringUtils.startsWithAny(
                    klazz.getPackage().getName(), VerificationApplication.class.getPackage().getName()))
            .collect(Collectors.toSet());
    Set<String> entitiesWithoutProperIndex = new HashSet<>();

    reflections.forEach(entity -> {
      List<CompoundMongoIndex> mongoDbCompoundIndexes = getMongoDbCompoundIndexes(entity);
      if (entityDoesNotHaveNamedCompoundIndex(mongoDbCompoundIndexes)) {
        entitiesWithoutProperIndex.add(entity.getCanonicalName());
      }
    });
    assertThat(entitiesWithoutProperIndex)
        .withFailMessage("The following classes do not have a named compound index: " + entitiesWithoutProperIndex)
        .isEmpty();
  }

  private boolean entityDoesNotHaveNamedCompoundIndex(List<CompoundMongoIndex> mongoDbCompoundIndexes) {
    for (CompoundMongoIndex compoundIndex : mongoDbCompoundIndexes) {
      if (StringUtils.isBlank(compoundIndex.getName())) {
        return true;
      }
    }
    return false;
  }

  private List<CompoundMongoIndex> getMongoDbCompoundIndexes(Class<? extends PersistentEntity> entity) {
    List<CompoundMongoIndex> mongoDbCompoundIndexes = new ArrayList<>();
    try {
      Method methodToFetchMongoDbCompoundIndexes = entity.getMethod("mongoIndexes");
      List<Object> mongoDbIndexes = (List<Object>) methodToFetchMongoDbCompoundIndexes.invoke(null);
      for (Object mongoIndex : mongoDbIndexes) {
        try {
          CompoundMongoIndex compoundMongoIndex = (CompoundMongoIndex) mongoIndex;
          mongoDbCompoundIndexes.add(compoundMongoIndex);
        } catch (ClassCastException e) {
        }
      }
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
    }
    return mongoDbCompoundIndexes;
  }
}

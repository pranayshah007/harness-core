/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk;

import static io.harness.reflection.CodeUtils.isHarnessClass;

import io.harness.annotation.RecasterAlias;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exceptions.RecasterException;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.steps.executables.StepDetailsInfo;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.PipelineViewObject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.reflections.Reflections;

@Slf4j
public class PmsSdkComponentTester {
  private static final String folderPath = "recaster/alias/";
  private static final String fileName = "alias.txt";

  public static void testRecasterAlias() {
    Reflections reflections = new Reflections("io.harness");
    Set<Class<?>> unregisteredClasses = new HashSet<>();

    log.info("Test ExecutionSweepingOutput");
    testRecasterAliasInternal(reflections, unregisteredClasses, ExecutionSweepingOutput.class);

    log.info("Test PassThroughData");
    testRecasterAliasInternal(reflections, unregisteredClasses, PassThroughData.class);

    log.info("Test StepDetailsInfo");
    testRecasterAliasInternal(reflections, unregisteredClasses, StepDetailsInfo.class);

    log.info("TestPipelineViewObject");
    testRecasterAliasInternal(reflections, unregisteredClasses, PipelineViewObject.class);

    if (!unregisteredClasses.isEmpty()) {
      throw new RecasterException("Some classes are missing RecastAlias annotation");
    }
  }

  public static void ensureRecasterAliasImmutability() {
    Reflections reflections = new Reflections("io.harness");

    try {
      final String aliasesFromFile = IOUtils.resourceToString(
          folderPath + fileName, StandardCharsets.UTF_8, PmsSdkComponentTester.class.getClassLoader());
      assert EmptyPredicate.isNotEmpty(aliasesFromFile);

      String[] arrayOfAliasClass = aliasesFromFile.split("\\s");
      Set<String> aliasSetFromFile = new HashSet<>();
      Collections.addAll(aliasSetFromFile, arrayOfAliasClass);
      if (aliasSetFromFile.size() != arrayOfAliasClass.length) {
        log.error("Recaster alias is repeated {}",
            CollectionUtils.disjunction(aliasSetFromFile, Arrays.asList(arrayOfAliasClass)));
        throw new RecasterException("Exception while testing recaster alias immutablity");
      }

      List<String> aliasListFromReflection = reflections.getTypesAnnotatedWith(RecasterAlias.class)
                                                 .stream()
                                                 .filter(clazz -> !clazz.isInterface())
                                                 .map(c -> c.getAnnotation(RecasterAlias.class).value())
                                                 .collect(Collectors.toList());
      Set<String> aliasSetFromReflection = new HashSet<>(aliasListFromReflection);
      if (aliasListFromReflection.size() != aliasSetFromReflection.size()) {
        log.error("Recaster alias is repeated {}",
            CollectionUtils.disjunction(aliasListFromReflection, aliasSetFromReflection));
        throw new RecasterException("Exception while testing recaster alias immutablity");
      }

      Collection<String> difference = CollectionUtils.disjunction(aliasSetFromReflection, aliasSetFromFile);
      if (!difference.isEmpty()) {
        log.error("Entries differing {}", difference);
        throw new RecasterException("Exception while testing recaster alias immmutablity");
      }

    } catch (IOException e) {
      throw new RecasterException("Exception while testing recaster alias immutablity", e);
    }
  }

  private static void testRecasterAliasInternal(
      Reflections reflections, Set<Class<?>> unregisteredClasses, Class<?> testedClazz) {
    Set<Class<?>> resultSet = new HashSet<>();
    Set<Class<?>> subtypesSet = getAllSubtypes(reflections, testedClazz);

    getAllHarnessClasses(reflections, subtypesSet, resultSet);

    resultSet.addAll(subtypesSet);

    Set<Class<?>> filteredResultSet = resultSet.stream()
                                          .filter(c -> !c.isAnnotationPresent(RecasterAlias.class) && !c.isEnum())
                                          .collect(Collectors.toSet());

    if (!filteredResultSet.isEmpty()) {
      log.error("Following classes should contain RecasterAlias annotation : {}",
          filteredResultSet.stream().map(Class::getName).collect(Collectors.joining(System.lineSeparator())));
      unregisteredClasses.addAll(filteredResultSet);
    }
  }

  private static void getAllHarnessClasses(
      Reflections reflections, Set<? extends Class<?>> set, Set<Class<?>> resultSet) {
    for (Class<?> c : set) {
      Field[] allModelFields = FieldUtils.getAllFields(c);

      Set<Class<?>> result = new HashSet<>();
      for (Field modelField : allModelFields) {
        Class<?> type = modelField.getType();
        if (!isHarnessClass(type) || type.isEnum()) {
          continue;
        }

        if (type.isInterface()) {
          Set<? extends Class<?>> allSubtypes = getAllSubtypes(reflections, type);
          result.addAll(allSubtypes);

          allSubtypes.remove(type);

          getAllHarnessClasses(reflections, allSubtypes, resultSet);
        } else {
          result.add(type);
        }
      }

      resultSet.addAll(result);

      result.remove(c);

      getAllHarnessClasses(reflections, result, resultSet);
    }
  }

  private static Set<Class<?>> getAllSubtypes(Reflections reflections, Class<?> testedClazz) {
    return reflections.getSubTypesOf(testedClazz)
        .stream()
        .filter(clazz -> !clazz.isInterface())
        .collect(Collectors.toSet());
  }
}

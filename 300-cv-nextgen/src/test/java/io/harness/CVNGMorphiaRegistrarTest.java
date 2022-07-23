/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.RAGHU;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cvng.VerificationApplication;
import io.harness.iterator.PersistentIterable;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.persistence.PersistentEntity;
import io.harness.reflection.HarnessReflections;
import io.harness.rule.Owner;
import io.harness.serializer.morphia.CVNextGenMorphiaRegister;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CVNGMorphiaRegistrarTest extends CvNextGenTestBase {
  @Inject private CVNextGenMorphiaRegister cvNextGenMorphiaRegister;

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testMorphiaRegistrar() {
    Set<Class> excludedClasses = Sets.newHashSet(PersistentIterable.class, PersistentRegularIterable.class);
    Set<Class> registeredClasses = new HashSet<>();
    cvNextGenMorphiaRegister.registerClasses(registeredClasses);
    Set<Class<? extends PersistentEntity>> cvngEntityClasses =
        HarnessReflections.get()
            .getSubTypesOf(PersistentEntity.class)
            .stream()
            .filter(klazz
                -> StringUtils.startsWithAny(
                    klazz.getPackage().getName(), VerificationApplication.class.getPackage().getName()))
            .collect(Collectors.toSet());
    cvngEntityClasses.removeAll(excludedClasses);
    cvngEntityClasses.removeAll(registeredClasses);
    assertThat(cvngEntityClasses.isEmpty())
        .withFailMessage("the following classes are not registered with morphia registrar %s", cvngEntityClasses)
        .isTrue();
  }
}

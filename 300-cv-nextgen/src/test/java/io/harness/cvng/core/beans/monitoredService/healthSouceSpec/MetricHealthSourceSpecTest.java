/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import static io.harness.packages.HarnessPackages.IO_HARNESS;
import static io.harness.packages.HarnessPackages.SOFTWARE_WINGS;
import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.models.VerificationType;
import io.harness.reflection.HarnessReflections;
import io.harness.rule.Owner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MetricHealthSourceSpecTest extends CvNextGenTestBase {
  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  @SneakyThrows
  public void validateAllTimeSeriesSpecsHaveMetricHealthSourceSpec() {
    // Used for SLI metrics
    Set<Class<? extends HealthSourceSpec>> allHealthSourceSpecClasses =
        HarnessReflections.get()
            .getSubTypesOf(HealthSourceSpec.class)
            .stream()
            .filter(klazz -> StringUtils.startsWithAny(klazz.getPackage().getName(), IO_HARNESS, SOFTWARE_WINGS))
            .collect(Collectors.toSet());

    assertThat(allHealthSourceSpecClasses.stream()
                   .filter(healthSourceClass -> !Modifier.isAbstract(healthSourceClass.getModifiers()))
                   .filter(healthSourceClass
                       -> getSourceType(healthSourceClass).getVerificationType().equals(VerificationType.TIME_SERIES))
                   .filter(healthSourceClass -> !MetricHealthSourceSpec.class.isAssignableFrom(healthSourceClass)))
        .isEmpty();
  }

  @SneakyThrows
  private DataSourceType getSourceType(Class<? extends HealthSourceSpec> healthSourceSpecClass) {
    Constructor constructor = healthSourceSpecClass.getConstructors()[0];
    HealthSourceSpec healthSourceSpec = (HealthSourceSpec) constructor.newInstance();
    return healthSourceSpec.getType();
  }
}

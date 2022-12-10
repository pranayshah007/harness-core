/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.sm.StepType;

import com.google.inject.Injector;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class StepYamlBuilderFactoryTest {
  @InjectMocks private StepYamlBuilderFactory factory;

  @Mock private Injector injector;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotCreateValidator() {
    final List<StepType> types = Arrays.stream(StepType.values())
                                     .filter(type -> type.getYamlValidatorClass() == null)
                                     .collect(Collectors.toList());
    types.forEach(type -> {
      assertThat(factory.getStepYamlBuilderForStepType(type)).isNull();
      verify(injector, never()).getInstance((Class<Object>) null);
    });
  }
}

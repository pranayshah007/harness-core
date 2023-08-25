/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.validation;

import static io.harness.rule.OwnerRule.HEN;
import static io.harness.rule.OwnerRule.JAMIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.category.element.UnitTests;
import io.harness.ci.execution.integrationstage.K8InitializeTaskUtilsHelper;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class CIYAMLSanitizationServiceTest extends CIExecutionTestBase {
  @Mock CIMiningPatternJob ciMiningPatternJob;
  @InjectMocks CIYAMLSanitizationServiceImpl ciyamlSanitizationService;

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testValidRun() {
    List<ExecutionWrapperConfig> steps = K8InitializeTaskUtilsHelper.getExecutionWrapperConfigList();
    boolean validate = false;
    try {
      validate = ciyamlSanitizationService.validate(steps);
    } catch (Exception e) {
    }

    assertThat(validate).isEqualTo(true);
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testValidRunTests() {
    List<ExecutionWrapperConfig> steps = K8InitializeTaskUtilsHelper.getRunTestExecutionWrapperConfigList();
    boolean validate = ciyamlSanitizationService.validate(steps);
    assertThat(validate).isEqualTo(true);
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testMaliciousRun() {
    List<ExecutionWrapperConfig> steps = K8InitializeTaskUtilsHelper.getExecutionMinerWrapperConfigList();
    Set<String> maliciousMiningPatterns = new HashSet<>();
    maliciousMiningPatterns.add("dero-stratum-miner");
    Mockito.when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(maliciousMiningPatterns);
    boolean validate = false;
    try {
      validate = ciyamlSanitizationService.validate(steps);
    } catch (CIStageExecutionException e) {
    }
    assertThat(validate).isEqualTo(false);
  }
}

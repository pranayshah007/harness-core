/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.entities;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.job.Sensitivity;
import io.harness.cvng.cdng.beans.v2.BaselineType;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.rule.Owner;

import java.time.Duration;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TestVerificationJobTest extends CategoryTest {
  @Test
  @Owner(developers = KAMAL)
  @Category({UnitTests.class})
  public void testValidateParams() {
    TestVerificationJob testVerificationJob = createTestVerificationJob();
    testVerificationJob.validateParams();
    testVerificationJob.setSensitivity(null);
    assertThatThrownBy(() -> testVerificationJob.validateParams())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("sensitivity should not be null");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category({UnitTests.class})
  public void testResolveAdditionsFields_emptyLastExecution() {
    TestVerificationJob testVerificationJob = createTestVerificationJob();
    assertThat(testVerificationJob.getBaselineVerificationJobInstanceId()).isNull();
    VerificationJobInstanceService verificationJobInstanceService = mock(VerificationJobInstanceService.class);
    when(verificationJobInstanceService.getLastSuccessfulTestVerificationJobExecutionId(any()))
        .thenReturn(Optional.empty());
    testVerificationJob.resolveAdditionsFields(verificationJobInstanceService, null);
    assertThat(testVerificationJob.getBaselineVerificationJobInstanceId()).isNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category({UnitTests.class})
  public void testResolveAdditionsFields_withValidBaseline() {
    TestVerificationJob testVerificationJob = createTestVerificationJob();
    assertThat(testVerificationJob.getBaselineVerificationJobInstanceId()).isNull();
    VerificationJobInstanceService verificationJobInstanceService = mock(VerificationJobInstanceService.class);
    String baseline = generateUuid();
    when(verificationJobInstanceService.getLastSuccessfulTestVerificationJobExecutionId(any()))
        .thenReturn(Optional.of(baseline));
    testVerificationJob.resolveAdditionsFields(verificationJobInstanceService, BaselineType.LAST);
    assertThat(testVerificationJob.getBaselineVerificationJobInstanceId()).isEqualTo(baseline);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category({UnitTests.class})
  public void testGetDTO_ignoreCasesInSensitivity() {
    TestVerificationJob testVerificationJob = createTestVerificationJob();
    testVerificationJob.setSensitivity("High", false);
    String baseline = generateUuid();
    testVerificationJob.setBaselineVerificationJobInstanceId(baseline);
    assertThat(testVerificationJob.getSensitivity()).isEqualTo(Sensitivity.HIGH);
  }

  private TestVerificationJob createTestVerificationJob() {
    TestVerificationJob testVerificationJob = new TestVerificationJob();
    testVerificationJob.setDuration(Duration.ofMinutes(2));
    testVerificationJob.setServiceIdentifier("service", false);
    testVerificationJob.setEnvIdentifier("env", false);
    testVerificationJob.setSensitivity(Sensitivity.MEDIUM);
    return testVerificationJob;
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.entities;

import static io.harness.cvng.verificationjob.CVVerificationJobConstants.DURATION_KEY;
import static io.harness.cvng.verificationjob.CVVerificationJobConstants.ENV_IDENTIFIER_KEY;
import static io.harness.cvng.verificationjob.CVVerificationJobConstants.SENSITIVITY_KEY;
import static io.harness.cvng.verificationjob.CVVerificationJobConstants.SERVICE_IDENTIFIER_KEY;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.job.Sensitivity;
import io.harness.cvng.verificationjob.entities.VerificationJob.VerificationJobKeys;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class VerificationJobTest extends CategoryTest {
  private String accountId;

  @Before
  public void setup() {
    this.accountId = generateUuid();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidate_forRequiredFields() throws IllegalAccessException {
    testFieldForNotNull(VerificationJobKeys.accountId);
    testFieldForNotNull(VerificationJobKeys.identifier);
    testFieldForNotNull(VerificationJobKeys.jobName);
    testFieldForNotNull(VerificationJobKeys.monitoringSources);

    testFieldForNotNull(VerificationJobKeys.duration);

    testFieldForNotNull(VerificationJobKeys.envIdentifier);
    testFieldForNotNull(VerificationJobKeys.serviceIdentifier);
    testFieldForNotNull(VerificationJobKeys.serviceIdentifier);
    createVerificationJob().validate();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidate_callsValidateParams() {
    VerificationJob verificationJob = createVerificationJob();
    verificationJob = spy(verificationJob);
    verificationJob.validate();
    verify(verificationJob, times(1)).validateParams();
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testValidate_allMonitoringSourcesEnabled() {
    VerificationJob verificationJob = createVerificationJob();
    verificationJob.setAllMonitoringSourcesEnabled(true);
    assertThatThrownBy(() -> verificationJob.validate())
        .hasMessage("Monitoring Sources should be null or empty if allMonitoringSources is enabled");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidate_shouldThrowExceptionWhenDurationWhenZero() {
    VerificationJob verificationJob = createVerificationJob();
    verificationJob.setDuration(Duration.ofSeconds(0));
    assertThatThrownBy(() -> verificationJob.validate())
        .hasMessage("Minimum allowed duration is 5 mins. Current value(mins): 0");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidate_shouldThrowExceptionWhenDurationIsLessThen5() {
    VerificationJob verificationJob = createVerificationJob();
    verificationJob.setDuration(Duration.ofMinutes(4));
    assertThatThrownBy(() -> verificationJob.validate())
        .hasMessage("Minimum allowed duration is 5 mins. Current value(mins): 4");
  }

  @Test(expected = Test.None.class)
  @Owner(developers = KAMAL)
  @Category({UnitTests.class})
  public void testValidate_validDuration() {
    VerificationJob verificationJob = createVerificationJob();
    verificationJob.setDuration(Duration.ofMinutes(5));
    verificationJob.validate();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category({UnitTests.class})
  public void testValidate_emptyMonitoringSources() {
    VerificationJob verificationJob = createVerificationJob();
    verificationJob.setMonitoringSources(Collections.emptyList());
    assertThatThrownBy(() -> verificationJob.validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Monitoring Sources can not be empty");
  }

  @Test(expected = Test.None.class)
  @Owner(developers = KAMAL)
  @Category({UnitTests.class})
  public void testValidate_validDataSources() {
    VerificationJob verificationJob = createVerificationJob();
    verificationJob.setDataSources(Collections.singletonList(DataSourceType.SPLUNK));
    verificationJob.validate();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category({UnitTests.class})
  public void testResolveCommonJobRuntimeParams_validArgs() {
    VerificationJob verificationJob = createVerificationJob();
    verificationJob.setServiceIdentifier("<+input>", true);
    verificationJob.setEnvIdentifier("<+input>", true);
    assertThat(verificationJob.getServiceIdentifier()).isNotEqualTo("cvngService");
    assertThat(verificationJob.getEnvIdentifier()).isNotEqualTo("production");
    assertThat(verificationJob.getDuration().toMinutes()).isEqualTo(5);

    Map<String, String> runtimeParams = new HashMap<>();
    runtimeParams.put(SERVICE_IDENTIFIER_KEY, "cvngService");
    runtimeParams.put(ENV_IDENTIFIER_KEY, "production");
    runtimeParams.put(DURATION_KEY, "30m");
    verificationJob = verificationJob.resolveVerificationJob(runtimeParams);
    assertThat(verificationJob.getServiceIdentifier()).isEqualTo("cvngService");
    assertThat(verificationJob.getEnvIdentifier()).isEqualTo("production");
    assertThat(verificationJob.getDuration().toMinutes()).isEqualTo(5);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category({UnitTests.class})
  public void testResolveCommonJobRuntimeParams_onlyServiceAndEnvOverride() {
    VerificationJob verificationJob = createVerificationJob();
    verificationJob.setServiceIdentifier("<+input>", true);
    verificationJob.setEnvIdentifier("<+input>", true);
    assertThat(verificationJob.getServiceIdentifier()).isNotEqualTo("cvngService");
    assertThat(verificationJob.getEnvIdentifier()).isNotEqualTo("production");
    assertThat(verificationJob.getDuration().toMinutes()).isEqualTo(5);

    Map<String, String> runtimeParams = new HashMap<>();
    runtimeParams.put(SERVICE_IDENTIFIER_KEY, "cvngService");
    runtimeParams.put(ENV_IDENTIFIER_KEY, "production");

    verificationJob = verificationJob.resolveVerificationJob(runtimeParams);
    assertThat(verificationJob.getServiceIdentifier()).isEqualTo("cvngService");
    assertThat(verificationJob.getEnvIdentifier()).isEqualTo("production");
    assertThat(verificationJob.getDuration().toMinutes()).isEqualTo(5);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category({UnitTests.class})
  public void testResolveCommonJobRuntimeParams_duationOverrideWithoutRuntimeParam() {
    VerificationJob verificationJob = createVerificationJob();
    assertThat(verificationJob.getServiceIdentifier()).isNotEqualTo("cvngService");
    assertThat(verificationJob.getEnvIdentifier()).isNotEqualTo("production");
    assertThat(verificationJob.getDuration().toMinutes()).isEqualTo(5);

    Map<String, String> runtimeParams = new HashMap<>();
    runtimeParams.put(DURATION_KEY, "30m");

    VerificationJob resolvedVerificationJob = verificationJob.resolveVerificationJob(runtimeParams);
    assertThat(resolvedVerificationJob.getServiceIdentifier()).isEqualTo(verificationJob.getServiceIdentifier());
    assertThat(resolvedVerificationJob.getEnvIdentifier()).isEqualTo(verificationJob.getEnvIdentifier());
    assertThat(resolvedVerificationJob.getDuration().toMinutes()).isEqualTo(5);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category({UnitTests.class})
  public void testResolveCommonJobRuntimeParams_sensitivity() {
    TestVerificationJob verificationJob = (TestVerificationJob) createVerificationJob();
    verificationJob.setSensitivity("<+input>", true);
    verificationJob.setDuration("<+input>", true);
    Map<String, String> runtimeParams = new HashMap<>();
    runtimeParams.put(SENSITIVITY_KEY, "High");

    TestVerificationJob resolvedVerificationJob =
        (TestVerificationJob) verificationJob.resolveVerificationJob(runtimeParams);
    assertThat(resolvedVerificationJob.getSensitivity()).isEqualTo(Sensitivity.HIGH);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category({UnitTests.class})
  public void testResolveCommonJobRuntimeParams_duationOverrideWithRuntimeParam() {
    VerificationJob verificationJob = createVerificationJob();
    verificationJob.setDuration("<+input>", true);

    Map<String, String> runtimeParams = new HashMap<>();
    runtimeParams.put(DURATION_KEY, "30m");

    VerificationJob resolvedVerificationJob = verificationJob.resolveVerificationJob(runtimeParams);
    assertThat(resolvedVerificationJob.getServiceIdentifier()).isEqualTo(verificationJob.getServiceIdentifier());
    assertThat(resolvedVerificationJob.getEnvIdentifier()).isEqualTo(verificationJob.getEnvIdentifier());
    assertThat(resolvedVerificationJob.getDuration().toMinutes()).isEqualTo(30);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category({UnitTests.class})
  public void testSetVerificationJobUrl() {
    VerificationJob verificationJob = createVerificationJob();
    String url = verificationJob.getVerificationJobUrl();
    assertThat(url).isEqualTo("/cv/api/verification-job?accountId=" + accountId + "&orgIdentifier="
        + verificationJob.getOrgIdentifier() + "&projectIdentifier=" + verificationJob.getProjectIdentifier()
        + "&identifier=" + verificationJob.getIdentifier());
  }

  private void testFieldForNotNull(String fieldName) throws IllegalAccessException {
    VerificationJob verificationJob = createVerificationJob();
    FieldUtils.writeField(verificationJob, fieldName, null, true);
    assertThatThrownBy(() -> verificationJob.validate())
        .isInstanceOf(NullPointerException.class)
        .hasMessage(fieldName + " should not be null");
  }

  private VerificationJob createVerificationJob() {
    TestVerificationJob testVerificationJob = new TestVerificationJob();
    testVerificationJob.setAccountId(accountId);
    testVerificationJob.setIdentifier("identifier");
    testVerificationJob.setJobName(generateUuid());
    testVerificationJob.setDataSources(Lists.newArrayList(DataSourceType.APP_DYNAMICS));
    testVerificationJob.setMonitoringSources(Arrays.asList("monitoringIdentifier"));
    testVerificationJob.setSensitivity(Sensitivity.MEDIUM);
    testVerificationJob.setServiceIdentifier(generateUuid(), false);
    testVerificationJob.setEnvIdentifier(generateUuid(), false);
    testVerificationJob.setBaselineVerificationJobInstanceId(generateUuid());
    testVerificationJob.setDuration(Duration.ofMinutes(5));
    testVerificationJob.setProjectIdentifier(generateUuid());
    testVerificationJob.setOrgIdentifier(generateUuid());
    return testVerificationJob;
  }
}

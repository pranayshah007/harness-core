/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.integration;

import static io.harness.rule.OwnerRule.HANTANG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import java.time.Instant;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class K8sJobTest extends CategoryTest {
  @Autowired private HPersistence hPersistence;
  @Autowired private JobLauncher jobLauncher;
  @Autowired @Qualifier(value = "k8sJob") private Job job;

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  @Ignore("TODO: Failing in bazel. Changes required to make it work in bazel")
  public void testK8sJob() throws Exception {
    Instant startInstant = Instant.now();
    Instant endInstant = Instant.now().plusSeconds(60);

    JobParameters params = new JobParametersBuilder()
                               .addString("JobID", String.valueOf(System.currentTimeMillis()))
                               .addString("startDate", String.valueOf(startInstant.toEpochMilli()))
                               .addString("endDate", String.valueOf(endInstant.toEpochMilli()))
                               .toJobParameters();

    JobExecution jobExecution = jobLauncher.run(job, params);
    assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.statusviewer;

import static io.harness.k8s.steadystate.statusviewer.JobStatusViewer.ResponseMessages.FAILED;
import static io.harness.k8s.steadystate.statusviewer.JobStatusViewer.ResponseMessages.SUCCESS;
import static io.harness.k8s.steadystate.statusviewer.JobStatusViewer.ResponseMessages.WAITING;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.k8s.steadystate.model.K8ApiResponseDTO;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import java.util.List;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class JobStatusViewerTest extends CategoryTest {
  @InjectMocks private JobStatusViewer statusViewer;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testWaitingJobStatus() {
    K8ApiResponseDTO response = statusViewer.extractRolloutStatus(new V1Job());

    assertThat(response.isDone()).isFalse();
    assertThat(response.getMessage()).isEqualTo(String.format(WAITING, ""));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testFailedJobStatus() {
    V1Job job = new V1Job();
    V1JobStatus status = new V1JobStatus();
    V1JobCondition condition = new V1JobCondition().type("Failed").status("True");
    status.setConditions(List.of(condition));
    job.setStatus(status);

    K8ApiResponseDTO response = statusViewer.extractRolloutStatus(job);
    assertThat(response.isFailed()).isTrue();
    assertThat(response.getMessage()).isEqualTo(String.format(FAILED, status));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSuccessfulJobStatus() {
    V1Job job = new V1Job();
    V1JobStatus status = new V1JobStatus();
    V1JobCondition condition = new V1JobCondition().type("Complete").status("True");
    status.setConditions(List.of(condition));
    status.setCompletionTime(DateTime.now());
    job.setStatus(status);

    K8ApiResponseDTO response = statusViewer.extractRolloutStatus(job);
    assertThat(response.isDone()).isTrue();
    assertThat(response.getMessage()).isEqualTo(String.format(SUCCESS, status));
  }
}

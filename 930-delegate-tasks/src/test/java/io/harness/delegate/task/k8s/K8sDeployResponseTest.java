/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.cdng.execution.StepExecutionInstanceInfo;
import io.harness.k8s.model.K8sPod;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import junitparams.JUnitParamsRunner;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@OwnedBy(CDP)
@RunWith(JUnitParamsRunner.class)
@Slf4j
public class K8sDeployResponseTest extends CategoryTest {
  @Inject K8sDeployResponse k8sDeployResponse;

  @Before
  public void setup() throws Exception {
    k8sDeployResponse =
        K8sDeployResponse.builder().k8sNGTaskResponse(K8sRollingDeployResponse.builder().build()).build();
  }
  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void getStepExecutionInstanceInfoNullCase() {
    StepExecutionInstanceInfo stepExecutionInstanceInfo = k8sDeployResponse.getStepExecutionInstanceInfo();
    assertThat(stepExecutionInstanceInfo).isNotNull();
    assertThat(stepExecutionInstanceInfo.getDeployedServiceInstances()).isEmpty();
    assertThat(stepExecutionInstanceInfo.getServiceInstancesBefore()).isEmpty();
    assertThat(stepExecutionInstanceInfo.getServiceInstancesAfter()).isEmpty();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void getStepExecutionInstanceInfo() {
    k8sDeployResponse =
        K8sDeployResponse.builder()
            .k8sNGTaskResponse(K8sRollingDeployResponse.builder()
                                   .k8sPodList(List.of(K8sPod.builder().newPod(true).name("pod1").build(),
                                       K8sPod.builder().newPod(false).name("pod2").build()))
                                   .previousK8sPodList(List.of(K8sPod.builder().newPod(false).name("pod2").build()))
                                   .build())
            .build();
    StepExecutionInstanceInfo stepExecutionInstanceInfo = k8sDeployResponse.getStepExecutionInstanceInfo();
    assertThat(stepExecutionInstanceInfo).isNotNull();
    assertThat(stepExecutionInstanceInfo.getDeployedServiceInstances().size()).isEqualTo(1);
    assertThat(stepExecutionInstanceInfo.getDeployedServiceInstances().get(0).getInstanceName()).isEqualTo("pod1");
    assertThat(stepExecutionInstanceInfo.getServiceInstancesBefore().size()).isEqualTo(1);
    assertThat(stepExecutionInstanceInfo.getServiceInstancesBefore().get(0).getInstanceName()).isEqualTo("pod2");
    assertThat(stepExecutionInstanceInfo.getServiceInstancesAfter().size()).isEqualTo(2);
  }
}

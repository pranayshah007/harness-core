/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.settings.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import software.wings.beans.settings.helm.repoconfigvalidation.HelmRepoConfigValidationTaskParams;

@OwnedBy(CDP)
public class HelmRepoConfigValidationTaskParamsTest extends CategoryTest {
  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testFetchRequiredExecutionCapabilities() {
    HelmRepoConfigValidationTaskParams taskParams = HelmRepoConfigValidationTaskParams.builder().build();
    assertThat(taskParams.fetchRequiredExecutionCapabilities(null)).hasSize(0);
    taskParams.setDelegateSelectors(Collections.singleton("delegate1"));
    List<ExecutionCapability> executionCapabilities = taskParams.fetchRequiredExecutionCapabilities(null);
    assertThat(executionCapabilities).hasSize(1);
    assertThat(executionCapabilities.get(0) instanceof SelectorCapability).isTrue();
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.state.io;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.harness.OrchestrationBeansTestBase;
import io.harness.category.element.UnitTests;
import io.harness.pms.sdk.core.data.StepTransput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.steps.io.ResolvedRefInput;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.rule.Owner;
import io.harness.utils.DummyOutcome;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StepInputPackageTest extends OrchestrationBeansTestBase {
  io.harness.pms.sdk.core.steps.io.StepInputPackage inputPackage =
      StepInputPackage.builder()
          .input(io.harness.pms.sdk.core.steps.io.ResolvedRefInput.builder()
                     .refObject(RefObjectUtils.getSweepingOutputRefObject("refName1", "refKey", generateUuid()))
                     .transput(new DummyOutcome("name1"))
                     .build())
          .input(io.harness.pms.sdk.core.steps.io.ResolvedRefInput.builder()
                     .refObject(RefObjectUtils.getSweepingOutputRefObject("refName2", "refKey", generateUuid()))
                     .transput(new DummyOutcome("name2"))
                     .build())
          .input(ResolvedRefInput.builder()
                     .refObject(RefObjectUtils.getSweepingOutputRefObject("refName1", "refKeyBlah", generateUuid()))
                     .transput(new DummyOutcome("name3"))
                     .build())
          .build();
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestFindByRefKey() {
    assumeThat(false).isTrue();
    List<io.harness.pms.sdk.core.data.StepTransput> transputList = inputPackage.findByRefKey("refKey");
    assertThat(transputList).isNotEmpty();
    assertThat(transputList).hasSize(2);
    assertThat(transputList.stream().map(transput -> ((DummyOutcome) transput).getName()))
        .containsExactly("name1", "name2");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldReturnEmptyIfNoKey() {
    List<StepTransput> transputList = inputPackage.findByRefKey("refKeyDummy");
    assertThat(transputList).isEmpty();
  }
}

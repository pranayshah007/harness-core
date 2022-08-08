/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.metadata;

import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.sm.ExecutionInterrupt.ExecutionInterruptBuilder.anExecutionInterrupt;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.ExecutionInterruptType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.StateExecutionInterrupt;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ExecutionInterruptMetadataTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCompareTo() {
    assertThat(ExecutionInterruptMetadata.builder().build().compareTo(null)).isEqualTo(1);
    assertThat(ExecutionInterruptMetadata.builder().build().compareTo(ExecutionInterruptMetadata.builder().build()))
        .isEqualTo(1);
    assertThat(ExecutionInterruptMetadata.builder().build().compareTo(
                   ExecutionInterruptMetadata.builder().tookEffectAt(ZonedDateTime.now()).build()))
        .isEqualTo(-1);

    ZonedDateTime now = ZonedDateTime.now();
    assertThat(ExecutionInterruptMetadata.builder().tookEffectAt(now).build().compareTo(
                   ExecutionInterruptMetadata.builder().tookEffectAt(now).build()))
        .isEqualTo(0);
    assertThat(ExecutionInterruptMetadata.builder()
                   .tookEffectAt(now.plus(1, ChronoUnit.MINUTES))
                   .build()
                   .compareTo(ExecutionInterruptMetadata.builder().tookEffectAt(now).build()))
        .isEqualTo(1);
    assertThat(ExecutionInterruptMetadata.builder()
                   .tookEffectAt(now.minus(1, ChronoUnit.MINUTES))
                   .build()
                   .compareTo(ExecutionInterruptMetadata.builder().tookEffectAt(now).build()))
        .isEqualTo(-1);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFromStateExecutionInterrupts() {
    assertThat(ExecutionInterruptMetadata.fromStateExecutionInterrupts(null)).isNull();

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    List<ExecutionInterruptMetadata> executionInterruptMetadataList =
        ExecutionInterruptMetadata.fromStateExecutionInterrupts(asList(null, prepareStateExecutionInterrupt(now)));
    assertThat(executionInterruptMetadataList).isNotNull();
    assertThat(executionInterruptMetadataList.size()).isEqualTo(1);
    validateExecutionInterruptMetadata(executionInterruptMetadataList.get(0), now);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFromStateExecutionInterrupt() {
    assertThat(ExecutionInterruptMetadata.fromStateExecutionInterrupt(null)).isNull();
    assertThat(ExecutionInterruptMetadata.fromStateExecutionInterrupt(StateExecutionInterrupt.builder().build()))
        .isNull();
    assertThat(ExecutionInterruptMetadata.fromStateExecutionInterrupt(
                   StateExecutionInterrupt.builder().interrupt(anExecutionInterrupt().build()).build()))
        .isNull();

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    ExecutionInterruptMetadata executionInterruptMetadata =
        ExecutionInterruptMetadata.fromStateExecutionInterrupt(prepareStateExecutionInterrupt(now));
    validateExecutionInterruptMetadata(executionInterruptMetadata, now);
  }

  private void validateExecutionInterruptMetadata(ExecutionInterruptMetadata executionInterruptMetadata, Instant now) {
    assertThat(executionInterruptMetadata.getId()).isEqualTo("id");
    assertThat(executionInterruptMetadata.getInterruptType()).isEqualTo(ExecutionInterruptType.RETRY);
    assertThat(executionInterruptMetadata.getCreatedBy()).isNull();
    assertThat(executionInterruptMetadata.getCreatedAt()).isNotNull();
    assertThat(executionInterruptMetadata.getCreatedAt().toInstant()).isEqualTo(now);
    assertThat(executionInterruptMetadata.getTookEffectAt()).isNotNull();
    assertThat(executionInterruptMetadata.getTookEffectAt().toInstant()).isEqualTo(now);
  }

  private StateExecutionInterrupt prepareStateExecutionInterrupt(Instant now) {
    return StateExecutionInterrupt.builder()
        .interrupt(anExecutionInterrupt()
                       .uuid("id")
                       .executionInterruptType(ExecutionInterruptType.RETRY)
                       .createdAt(now.toEpochMilli())
                       .build())
        .tookAffectAt(Date.from(now))
        .build();
  }
}

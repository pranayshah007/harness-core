/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.execution.utils;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidCredentialsException;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.EnumSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class EngineExceptionUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetOrchestrationFailureTypes() {
    assertThat(EngineExceptionUtils.getOrchestrationFailureTypes(new GeneralException("msg"))).isEmpty();
    assertThat(EngineExceptionUtils.getOrchestrationFailureTypes(new InvalidCredentialsException("msg", null)))
        .containsExactly(FailureType.AUTHENTICATION_FAILURE);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testTransformResponseMessagesToFailureInfo() {
    FailureInfo failureInfo = EngineExceptionUtils.transformResponseMessagesToFailureInfo(
        ImmutableList.of(ResponseMessage.builder()
                             .code(ErrorCode.INVALID_CREDENTIAL)
                             .level(Level.ERROR)
                             .message("m1")
                             .failureTypes(EnumSet.of(io.harness.exception.FailureType.AUTHENTICATION))
                             .build(),
            ResponseMessage.builder()
                .code(ErrorCode.JIRA_ERROR)
                .level(Level.ERROR)
                .message("m2")
                .failureTypes(EnumSet.of(io.harness.exception.FailureType.APPLICATION_ERROR))
                .build()));

    assertThat(failureInfo).isNotNull();
    assertThat(failureInfo.getErrorMessage()).isEqualTo("m2");
    assertThat(failureInfo.getFailureTypesList()).containsExactly(FailureType.APPLICATION_FAILURE);
    assertThat(failureInfo.getFailureDataCount()).isEqualTo(2);
    assertThat(failureInfo.getFailureData(0).getFailureTypesList()).containsExactly(FailureType.AUTHENTICATION_FAILURE);
    assertThat(failureInfo.getFailureData(1).getFailureTypesList()).containsExactly(FailureType.APPLICATION_FAILURE);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testTransformToWingsFailureTypes() {
    assertThat(EngineExceptionUtils.transformToWingsFailureTypes(Collections.emptySet())).isEmpty();
    assertThat(EngineExceptionUtils.transformToWingsFailureTypes(
                   EnumSet.of(FailureType.APPLICATION_FAILURE, FailureType.TIMEOUT_FAILURE)))
        .containsExactlyInAnyOrder(
            io.harness.exception.FailureType.APPLICATION_ERROR, io.harness.exception.FailureType.EXPIRED);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testTransformToOrchestrationFailureTypes() {
    assertThat(EngineExceptionUtils.transformToOrchestrationFailureTypes(null)).isEmpty();
    assertThat(EngineExceptionUtils.transformToOrchestrationFailureTypes(Collections.emptySet())).isEmpty();
    assertThat(EngineExceptionUtils.transformToOrchestrationFailureTypes(EnumSet.of(
                   io.harness.exception.FailureType.APPLICATION_ERROR, io.harness.exception.FailureType.EXPIRED)))
        .containsExactlyInAnyOrder(FailureType.APPLICATION_FAILURE, FailureType.TIMEOUT_FAILURE);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testMapToWingsFailureType() {
    for (FailureType failureType : FailureType.values()) {
      io.harness.exception.FailureType wingsFailureType = EngineExceptionUtils.mapToWingsFailureType(failureType);
      assertThat(wingsFailureType).isNotNull();
    }
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testMapToOrchestrationFailureType() {
    for (io.harness.exception.FailureType failureType : io.harness.exception.FailureType.values()) {
      FailureType orchestrationFailureType = EngineExceptionUtils.mapToOrchestrationFailureType(failureType);
      assertThat(orchestrationFailureType).isNotNull();
    }
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps;

import static io.harness.rule.OwnerRule.SAHITHI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CI)
@RunWith(MockitoJUnitRunner.class)
public class CiStepParametersUtilsTest extends CategoryTest {
  @InjectMocks private CiStepParametersUtils ciStepParametersUtils;
  @Mock private HPersistence persistence;
  @Mock UpdateOperations<StepStatusMetadata> mockUpdateOperations;
  @Mock Query<StepStatusMetadata> mockQuery;

  StepStatusMetadata stepStatusMetadata;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    stepStatusMetadata = StepStatusMetadata.builder().build();

    FieldEnd fieldEnd = mock(FieldEnd.class);
    when(persistence.createUpdateOperations(StepStatusMetadata.class)).thenReturn(mockUpdateOperations);
    when(mockUpdateOperations.set(anyString(), any())).thenReturn(mockUpdateOperations);
    when(mockUpdateOperations.push(anyString(), any())).thenReturn(mockUpdateOperations);

    when(persistence.createQuery(StepStatusMetadata.class)).thenReturn(mockQuery);
    when(mockQuery.field(any())).thenReturn(fieldEnd);
    when(fieldEnd.equal(any())).thenReturn(mockQuery);
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void TestSaveCIStep() {
    Ambiance ambiance = Ambiance.newBuilder().setStageExecutionId("stageExecutionId").build();
    String stepIdentifier = "stepIdentifier";
    ciStepParametersUtils.saveCIStepStatusInfo(ambiance, StepExecutionStatus.FAILURE, stepIdentifier);
    verify(persistence, times(1)).upsert(any(), any());
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.graph.stepDetail;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stepDetail.NodeExecutionDetailsInfo;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.category.element.UnitTests;
import io.harness.engine.observers.StepDetailsUpdateObserver;
import io.harness.observer.Subject;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.repositories.stepDetail.NodeExecutionsInfoRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsGraphStepDetailsServiceImplTest extends OrchestrationTestBase {
  @Mock private NodeExecutionsInfoRepository nodeExecutionsInfoRepository;

  @Mock private Subject<StepDetailsUpdateObserver> stepDetailsUpdateObserverSubject;

  @Inject @InjectMocks private PmsGraphStepDetailsServiceImpl pmsGraphStepDetailsService;

  @Before
  public void setUp() {
    Reflect.on(pmsGraphStepDetailsService).set("stepDetailsUpdateObserverSubject", stepDetailsUpdateObserverSubject);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void addStepDetail() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    PmsStepDetails pmsStepDetails = new PmsStepDetails(new HashMap<>());
    String name = "name";
    when(nodeExecutionsInfoRepository.save(any())).thenReturn(null);
    doNothing().when(stepDetailsUpdateObserverSubject).fireInform(any());

    pmsGraphStepDetailsService.addStepDetail(nodeExecutionId, planExecutionId, pmsStepDetails, name);

    verify(stepDetailsUpdateObserverSubject).fireInform(any(), any());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void addStepInputs() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    PmsStepParameters pmsStepDetails = new PmsStepParameters(new HashMap<>());
    pmsGraphStepDetailsService.addStepInputs(nodeExecutionId, planExecutionId, pmsStepDetails);
    verify(stepDetailsUpdateObserverSubject, times(1)).fireInform(any(), any());
    verify(nodeExecutionsInfoRepository, times(1)).save(any());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetStepInputs() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    when(nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId))
        .thenReturn(Optional.of(NodeExecutionsInfo.builder().build()));
    doNothing().when(stepDetailsUpdateObserverSubject).fireInform(any());

    pmsGraphStepDetailsService.getStepInputs(planExecutionId, nodeExecutionId);

    verify(nodeExecutionsInfoRepository, times(1)).findByNodeExecutionId(nodeExecutionId);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetStepInputsWithEmptyOptional() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    when(nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId)).thenReturn(Optional.empty());
    doNothing().when(stepDetailsUpdateObserverSubject).fireInform(any());

    pmsGraphStepDetailsService.getStepInputs(planExecutionId, nodeExecutionId);

    verify(nodeExecutionsInfoRepository, times(1)).findByNodeExecutionId(nodeExecutionId);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void getStepDetails() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();

    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .stepDetails(NodeExecutionDetailsInfo.builder()
                                                                 .stepDetails(PmsStepDetails.parse(new HashMap<>()))
                                                                 .name("name")
                                                                 .build())
                                                .build();

    when(nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId))
        .thenReturn(Optional.of(nodeExecutionsInfo));

    Map<String, PmsStepDetails> stepDetails =
        pmsGraphStepDetailsService.getStepDetails(planExecutionId, nodeExecutionId);

    assertThat(stepDetails).isNotNull();
    assertThat(stepDetails).isNotEmpty();
    assertThat(stepDetails.get("name")).isNotNull();

    PmsStepDetails pmsStepDetails = stepDetails.get("name");
    assertThat(pmsStepDetails).isEmpty();
  }
}

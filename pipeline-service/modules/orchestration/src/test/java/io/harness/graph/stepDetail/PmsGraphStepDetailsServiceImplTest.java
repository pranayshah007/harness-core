/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.graph.stepDetail;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stepDetail.NodeExecutionDetailsInfo;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.beans.stepDetail.NodeExecutionsInfo.NodeExecutionsInfoKeys;
import io.harness.category.element.UnitTests;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.engine.observers.StepDetailsUpdateObserver;
import io.harness.observer.Subject;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.repositories.stepDetail.NodeExecutionsInfoRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsGraphStepDetailsServiceImplTest extends OrchestrationTestBase {
  @Mock private NodeExecutionsInfoRepository nodeExecutionsInfoRepositoryMock;
  @Inject private NodeExecutionsInfoRepository nodeExecutionsInfoRepository;

  @Mock private Subject<StepDetailsUpdateObserver> stepDetailsUpdateObserverSubject;
  @Inject private MongoTemplate mongoTemplate;

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
    when(nodeExecutionsInfoRepositoryMock.save(any())).thenReturn(null);
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
    pmsGraphStepDetailsService.saveNodeExecutionInfo(nodeExecutionId, planExecutionId, pmsStepDetails);
    verify(stepDetailsUpdateObserverSubject, times(1)).fireInform(any(), any());
    verify(nodeExecutionsInfoRepositoryMock, times(1)).save(any());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetStepInputs() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    when(nodeExecutionsInfoRepositoryMock.findByNodeExecutionId(nodeExecutionId))
        .thenReturn(Optional.of(NodeExecutionsInfo.builder().build()));
    doNothing().when(stepDetailsUpdateObserverSubject).fireInform(any());

    pmsGraphStepDetailsService.getStepInputs(planExecutionId, nodeExecutionId);

    verify(nodeExecutionsInfoRepositoryMock, times(1)).findByNodeExecutionId(nodeExecutionId);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetStepInputsWithEmptyOptional() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    when(nodeExecutionsInfoRepositoryMock.findByNodeExecutionId(nodeExecutionId)).thenReturn(Optional.empty());
    doNothing().when(stepDetailsUpdateObserverSubject).fireInform(any());

    pmsGraphStepDetailsService.getStepInputs(planExecutionId, nodeExecutionId);

    verify(nodeExecutionsInfoRepositoryMock, times(1)).findByNodeExecutionId(nodeExecutionId);
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

    when(nodeExecutionsInfoRepositoryMock.findByNodeExecutionId(nodeExecutionId))
        .thenReturn(Optional.of(nodeExecutionsInfo));

    Map<String, PmsStepDetails> stepDetails =
        pmsGraphStepDetailsService.getStepDetails(planExecutionId, nodeExecutionId);

    assertThat(stepDetails).isNotNull();
    assertThat(stepDetails).isNotEmpty();
    assertThat(stepDetails.get("name")).isNotNull();
    PmsStepDetails pmsStepDetails = stepDetails.get("name");
    assertThat(pmsStepDetails).isEmpty();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetNodeExecutionsInfo() {
    String nodeExecutionId = generateUuid();
    when(nodeExecutionsInfoRepositoryMock.findByNodeExecutionId(nodeExecutionId))
        .thenReturn(Optional.of(NodeExecutionsInfo.builder().nodeExecutionId(nodeExecutionId).build()));
    NodeExecutionsInfo nodeExecutionsInfo = pmsGraphStepDetailsService.getNodeExecutionsInfo(nodeExecutionId);
    assertEquals(nodeExecutionsInfo.getNodeExecutionId(), nodeExecutionId);
    verify(nodeExecutionsInfoRepositoryMock, times(1)).findByNodeExecutionId(nodeExecutionId);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetNodeExecutionsInfoWithEmptyOptional() {
    String nodeExecutionId = generateUuid();
    when(nodeExecutionsInfoRepositoryMock.findByNodeExecutionId(nodeExecutionId)).thenReturn(Optional.empty());
    NodeExecutionsInfo nodeExecutionsInfo = pmsGraphStepDetailsService.getNodeExecutionsInfo(nodeExecutionId);
    assertNull(nodeExecutionsInfo);
    verify(nodeExecutionsInfoRepositoryMock, times(1)).findByNodeExecutionId(nodeExecutionId);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testCopyStepDetailsForRetry() {
    String originalNodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    String newNodeExecutionId = generateUuid();
    when(nodeExecutionsInfoRepositoryMock.findByNodeExecutionId(originalNodeExecutionId))
        .thenReturn(Optional.of(NodeExecutionsInfo.builder().build()));
    pmsGraphStepDetailsService.copyStepDetailsForRetry(planExecutionId, originalNodeExecutionId, newNodeExecutionId);
    verify(nodeExecutionsInfoRepositoryMock, times(1)).save(any(NodeExecutionsInfo.class));
    ArgumentCaptor<NodeExecutionsInfo> mCaptor = ArgumentCaptor.forClass(NodeExecutionsInfo.class);
    verify(nodeExecutionsInfoRepositoryMock).save(mCaptor.capture());
    NodeExecutionsInfo actualNodeExecutionsInfo = mCaptor.getValue();
    assertEquals(actualNodeExecutionsInfo.getNodeExecutionId(), newNodeExecutionId);
    assertEquals(actualNodeExecutionsInfo.getPlanExecutionId(), planExecutionId);
    assertNull(actualNodeExecutionsInfo.getUuid());
    assertEquals(actualNodeExecutionsInfo.getNodeExecutionDetailsInfoList().size(), 0);
    assertNull(actualNodeExecutionsInfo.getResolvedInputs());
    assertNull(actualNodeExecutionsInfo.getConcurrentChildInstance());
    verify(stepDetailsUpdateObserverSubject, times(1)).fireInform(any(), any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testCopyStepDetailsForRetryWithEmptyOptional() {
    String originalNodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    String newNodeExecutionId = generateUuid();
    when(nodeExecutionsInfoRepositoryMock.findByNodeExecutionId(originalNodeExecutionId)).thenReturn(Optional.empty());
    pmsGraphStepDetailsService.copyStepDetailsForRetry(planExecutionId, originalNodeExecutionId, newNodeExecutionId);
    verify(nodeExecutionsInfoRepositoryMock, times(0)).save(any(NodeExecutionsInfo.class));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testAddConcurrentChildInformation() {
    String nodeExecutionId = generateUuid();
    List<String> childrenNodeExecutionIds = new ArrayList<>();
    childrenNodeExecutionIds.add("ID1");
    ConcurrentChildInstance concurrentChildInstance =
        ConcurrentChildInstance.builder().childrenNodeExecutionIds(childrenNodeExecutionIds).cursor(9).build();
    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .uuid(generateUuid())
                                                .planExecutionId(generateUuid())
                                                .build();
    mongoTemplate.save(nodeExecutionsInfo);
    pmsGraphStepDetailsService.addConcurrentChildInformation(concurrentChildInstance, nodeExecutionId);
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId);
    List<NodeExecutionsInfo> nodeExecutionsInfos = mongoTemplate.find(new Query(criteria), NodeExecutionsInfo.class);
    assertEquals(1, nodeExecutionsInfos.size());
    assertEquals(9, nodeExecutionsInfos.get(0).getConcurrentChildInstance().getCursor());
    assertEquals("ID1", nodeExecutionsInfos.get(0).getConcurrentChildInstance().getChildrenNodeExecutionIds().get(0));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testIncrementCursor() {
    String nodeExecutionId = generateUuid();
    List<String> childrenNodeExecutionIds = new ArrayList<>();
    childrenNodeExecutionIds.add("ID1");
    assertNull(pmsGraphStepDetailsService.incrementCursor(nodeExecutionId, Status.SUCCEEDED));
    NodeExecutionsInfo nodeExecutionsInfo =
        NodeExecutionsInfo.builder()
            .nodeExecutionId(nodeExecutionId)
            .uuid(generateUuid())
            .planExecutionId(generateUuid())
            .concurrentChildInstance(
                ConcurrentChildInstance.builder().cursor(4).childrenNodeExecutionIds(childrenNodeExecutionIds).build())
            .build();
    mongoTemplate.save(nodeExecutionsInfo);
    pmsGraphStepDetailsService.incrementCursor(nodeExecutionId, Status.SUCCEEDED);
    int cursor = mongoTemplate
                     .find(new Query(Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId)),
                         NodeExecutionsInfo.class)
                     .get(0)
                     .getConcurrentChildInstance()
                     .getCursor();
    assertEquals(5, cursor);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFetchConcurrentChildInstance() {
    String nodeExecutionId = generateUuid();
    List<String> childrenNodeExecutionIds = new ArrayList<>();
    childrenNodeExecutionIds.add("ID1");
    assertNull(pmsGraphStepDetailsService.fetchConcurrentChildInstance(nodeExecutionId));
    NodeExecutionsInfo nodeExecutionsInfo =
        NodeExecutionsInfo.builder()
            .nodeExecutionId(nodeExecutionId)
            .uuid(generateUuid())
            .planExecutionId(generateUuid())
            .concurrentChildInstance(
                ConcurrentChildInstance.builder().cursor(4).childrenNodeExecutionIds(childrenNodeExecutionIds).build())
            .build();
    mongoTemplate.save(nodeExecutionsInfo);
    ConcurrentChildInstance concurrentChildInstance =
        pmsGraphStepDetailsService.fetchConcurrentChildInstance(nodeExecutionId);
    assertEquals(concurrentChildInstance.getCursor(), 4);
    assertEquals(concurrentChildInstance.getChildrenNodeExecutionIds().get(0), "ID1");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteNodeExecutionInfoForGivenIds() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();
    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .uuid(generateUuid())
                                                .planExecutionId(generateUuid())
                                                .build();
    mongoTemplate.save(nodeExecutionsInfo);

    Optional<NodeExecutionsInfo> byNodeExecutionId =
        nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    assertThat(byNodeExecutionId).isPresent();

    pmsGraphStepDetailsService.deleteNodeExecutionInfoForGivenIds(Set.of(nodeExecutionId));
    byNodeExecutionId = nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    assertThat(byNodeExecutionId).isNotPresent();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdateTTLForNodeExecutionInfoForGivenPlanExecutionId() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .uuid(generateUuid())
                                                .planExecutionId(planExecutionId)
                                                .build();
    mongoTemplate.save(nodeExecutionsInfo);
    Optional<NodeExecutionsInfo> byNodeExecutionId =
        nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    assertThat(byNodeExecutionId).isPresent();

    Date ttlExpiry = Date.from(OffsetDateTime.now().plus(Duration.ofMinutes(30)).toInstant());
    pmsGraphStepDetailsService.updateTTLForNodesForGivenPlanExecutionId(planExecutionId, ttlExpiry);
    byNodeExecutionId = nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    assertThat(byNodeExecutionId).isPresent();
    assertThat(byNodeExecutionId.get().getValidUntil()).isEqualTo(ttlExpiry);
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service;

import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.dto.PolledResponse;
import io.harness.dto.PollingInfoForTriggers;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;
import io.harness.ngtriggers.service.impl.NGTriggerEventServiceImpl;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.polling.client.PollingResourceClient;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.rule.Owner;

import com.mongodb.client.result.DeleteResult;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;

public class NGTriggerEventServiceImplTest {
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String IDENTIFIER = "first_trigger";
  private final String NAME = "first trigger";
  private final String PIPELINE_IDENTIFIER = "myPipeline";
  Call<ResponseDTO<PollingInfoForTriggers>> request;
  private MockedStatic<SafeHttpCall> safeHttpCallMockedStatic;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks NGTriggerEventServiceImpl ngTriggerEventService;

  @Mock TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Mock PollingResourceClient pollingResourceClient;
  @Before
  public void setup() throws Exception {}

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testFormEventCriteria() {
    Criteria criteria = ngTriggerEventService.formEventCriteria(
        ACCOUNT_ID, "eventCorrelationId", Collections.singletonList(ExecutionStatus.ABORTED));
    assertThat(criteria).isNotNull();
    assertThat(criteria.toString())
        .isEqualTo(new Criteria()
                       .andOperator(new Criteria()
                                        .and(TriggerEventHistoryKeys.accountId)
                                        .is(ACCOUNT_ID)
                                        .and(TriggerEventHistoryKeys.eventCorrelationId)
                                        .is("eventCorrelationId")
                                        .and(TriggerEventHistoryKeys.finalStatus)
                                        .in(Collections.singletonList(ExecutionStatus.ABORTED)))
                       .toString());
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testFormCriteriaInvalidRegex() {
    Criteria criteria = ngTriggerEventService.formTriggerEventCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PIPELINE_IDENTIFIER, IDENTIFIER, "a^s", Arrays.asList(ExecutionStatus.ABORTED));
    assertThat(criteria).isNotNull();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetPollingInfo() throws IOException {
    safeHttpCallMockedStatic = mockStatic(SafeHttpCall.class);
    when(pollingResourceClient.getPollingInfoForTriggers(ACCOUNT_ID, "pollingDocId")).thenReturn(request);
    Set<String> allPolledKeys = new HashSet<>();
    allPolledKeys.add("key1");
    allPolledKeys.add("key2");
    ResponseDTO<PollingInfoForTriggers> pollingInfoForTriggersResponseDTO =
        ResponseDTO.newResponse(PollingInfoForTriggers.builder()
                                    .polledResponse(PolledResponse.builder().allPolledKeys(allPolledKeys).build())
                                    .build());
    when(SafeHttpCall.executeWithExceptions(request)).thenAnswer(invocationOnMock -> pollingInfoForTriggersResponseDTO);
    assertThat(ngTriggerEventService.getPollingInfo(ACCOUNT_ID, "pollingDocId")
                   .getData()
                   .getPolledResponse()
                   .getAllPolledKeys())
        .isEqualTo(allPolledKeys);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetEventHistory() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TriggerEventHistoryKeys.createdAt));

    TriggerEventHistory eventHistory = TriggerEventHistory.builder()
                                           .triggerIdentifier(IDENTIFIER)
                                           .accountId(ACCOUNT_ID)
                                           .orgIdentifier(ORG_IDENTIFIER)
                                           .projectIdentifier(PROJ_IDENTIFIER)
                                           .targetIdentifier(PIPELINE_IDENTIFIER)
                                           .eventCorrelationId("event_correlation_id")
                                           .finalStatus("NOT_AVAILABLE")
                                           .build();

    Page<TriggerEventHistory> eventHistoryPage = new PageImpl<>(Collections.singletonList(eventHistory), pageable, 1);
    Criteria criteria = ngTriggerEventService.formTriggerEventCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PIPELINE_IDENTIFIER, IDENTIFIER, "a^s", Arrays.asList(ExecutionStatus.ABORTED));
    doReturn(eventHistoryPage).when(triggerEventHistoryRepository).findAll(criteria, pageable);

    Page<TriggerEventHistory> eventHistories = ngTriggerEventService.getEventHistory(criteria, pageable);
    assertThat(eventHistories).isNotNull();
    assertThat(eventHistories.getNumberOfElements()).isEqualTo(1);
    assertThat(eventHistories.getContent().get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testDeleteAllForPipeline() {
    ngTriggerEventService.deleteAllForPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER);
    verify(triggerEventHistoryRepository, times(1)).deleteBatch(any());
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testDeleteTriggerEventHistory() {
    DeleteResult deleteResult = DeleteResult.acknowledged(0);
    when(triggerEventHistoryRepository.deleteTriggerEventHistoryForTriggerIdentifier(any(Criteria.class)))
        .thenReturn(deleteResult);
    ngTriggerEventService.deleteTriggerEventHistory(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER);
    verify(triggerEventHistoryRepository, times(1)).deleteTriggerEventHistoryForTriggerIdentifier(any());
  }
}

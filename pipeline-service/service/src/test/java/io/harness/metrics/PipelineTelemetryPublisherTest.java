/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.telemetry.Destination.AMPLITUDE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.remote.client.CGRestUtils;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class PipelineTelemetryPublisherTest extends CategoryTest {
  @InjectMocks PipelineTelemetryPublisher telemetryPublisher;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PMSExecutionService pmsExecutionService;
  @Mock TelemetryReporter telemetryReporter;
  @Mock AccountClient accountClient;

  String acc = "acc";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRecordTelemetry() {
    List<AccountDTO> accounts = Collections.singletonList(AccountDTO.builder().identifier(acc).build());

    Call<RestResponse<List<AccountDTO>>> requestCall = mock(Call.class);
    doReturn(requestCall).when(accountClient).getAllAccounts();

    long pipelinesCreatedInADay = 20L;
    doReturn(pipelinesCreatedInADay).when(pmsPipelineService).countAllPipelines(any());

    long pipelinesTotal = 24L;
    doReturn(pipelinesTotal).when(pmsPipelineService).countAllPipelines(new Criteria());

    long executionsInADay = 200L;
    doReturn(executionsInADay).when(pmsExecutionService).getCountOfExecutions(any());

    long executionsTotal = 240L;
    doReturn(executionsTotal).when(pmsExecutionService).getCountOfExecutions(new Criteria());

    HashMap<String, Object> map = new HashMap<>();
    map.put("group_type", "Account");
    map.put("group_id", acc);
    map.put("pipelines_create_in_a_day", pipelinesCreatedInADay);
    map.put("total_number_of_pipelines", pipelinesTotal);
    map.put("pipelines_executed_in_a_day", executionsInADay);
    map.put("total_pipeline_executions", executionsTotal);

    try (MockedStatic<CGRestUtils> mockStatic = Mockito.mockStatic(CGRestUtils.class)) {
      mockStatic.when(() -> CGRestUtils.getResponse(requestCall)).thenReturn(accounts);

      telemetryPublisher.recordTelemetry();

      verify(telemetryReporter, times(1))
          .sendGroupEvent(acc, null, map, Collections.singletonMap(AMPLITUDE, true),
              TelemetryOption.builder().sendForCommunity(true).build());
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetAccountId() {
    List<AccountDTO> accounts = Collections.singletonList(AccountDTO.builder().identifier(acc).build());

    Call<RestResponse<List<AccountDTO>>> requestCall = mock(Call.class);
    doReturn(requestCall).when(accountClient).getAllAccounts();
    try (MockedStatic<CGRestUtils> mockStatic = Mockito.mockStatic(CGRestUtils.class)) {
      mockStatic.when(() -> CGRestUtils.getResponse(requestCall)).thenReturn(accounts);
      String accountId = telemetryPublisher.getAccountId();
      assertThat(accountId).isEqualTo(acc);
    }
  }
}

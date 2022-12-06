/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.sumologic.SumologicLogSampleDataRequest;
import io.harness.cvng.beans.sumologic.SumologicMetricSampleDataRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsResponse;
import io.harness.cvng.core.beans.healthsource.LogRecordsResponse;
import io.harness.cvng.core.beans.healthsource.MetricRecordsResponse;
import io.harness.cvng.core.beans.healthsource.QueryRecordsRequest;
import io.harness.cvng.core.beans.healthsource.TimeSeries;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.HealthSourceOnboardingService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;
import io.harness.ng.core.Status;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class HealthSourceOnboardingServiceImpl implements HealthSourceOnboardingService {
  @Inject private OnboardingService onboardingService;

  @Override
  public HealthSourceRecordsResponse fetchSampleRawRecordsForHealthSource(
      HealthSourceRecordsRequest healthSourceRecordsRequest, ProjectParams projectParams) {
    Object result = getResponseFromHealthSourceProvider(healthSourceRecordsRequest,
        projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(),
        MetricPackServiceImpl.SUMOLOGIC_METRIC_SAMPLE_DSL);
    // TODO set properly for error

    HealthSourceRecordsResponse healthSourceRecordsResponse =
        HealthSourceRecordsResponse.builder()
            .status(Status.SUCCESS)
            .providerType(healthSourceRecordsRequest.getProviderType())
            .build();
    healthSourceRecordsResponse.getRawRecords().add(result);
    return healthSourceRecordsResponse;
  }

  private Object getResponseFromHealthSourceProvider(HealthSourceRecordsRequest healthSourceRecordsRequest,
      String accountId, String orgId, String projectId, String dsl) {
    // we need to have tracing id as well TODO
    //  TODO which data collection info to use Metric or Log ?
    //  how to form the data collection Info

    // TODO the idea is we should get the corrector provider data collection info, we cant have it sumologic specific.

    DataCollectionRequest<SumoLogicConnectorDTO> request = null; // TODO fix

    switch (healthSourceRecordsRequest.getProviderType()) {
      case SUMOLOGIC_METRICS:
        request = SumologicMetricSampleDataRequest.builder()
                      .from(healthSourceRecordsRequest.getStartTime())
                      .to(healthSourceRecordsRequest.getEndTime())
                      .dsl(dsl)
                      .query(healthSourceRecordsRequest.getQuery())
                      .type(DataCollectionRequestType.SUMOLOGIC_METRIC_SAMPLE_DATA)
                      .build();
        break;
      case SUMOLOGIC_LOG:
        LocalDateTime startTime = Instant.ofEpochMilli(healthSourceRecordsRequest.getStartTime())
                                      .atZone(ZoneId.systemDefault())
                                      .toLocalDateTime();
        LocalDateTime endTime = Instant.ofEpochMilli(healthSourceRecordsRequest.getEndTime())
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        request = SumologicLogSampleDataRequest.builder()
                      .from(startTime.format(formatter))
                      .to(endTime.format(formatter))
                      .dsl(MetricPackServiceImpl.SUMOLOGIC_LOG_SAMPLE_DSL)
                      .query(healthSourceRecordsRequest.getQuery())
                      .type(DataCollectionRequestType.SUMOLOGIC_LOG_SAMPLE_DATA)
                      .build();
        break;
      default:
        // TODO throw exception
        break;
    }

    OnboardingRequestDTO onboardingRequestDTO =
        OnboardingRequestDTO.builder()
            .dataCollectionRequest(request)
            .connectorIdentifier(healthSourceRecordsRequest.getConnectorIdentifier())
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .tracingId("tracingId")
            .build();

    // TODO DIfferent DSL no service name filed.
    // TODO Option to override sourceHost

    OnboardingResponseDTO onboardingResponseDTO =
        onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);
    return onboardingResponseDTO.getResult();
  }

  @Override
  public MetricRecordsResponse fetchMetricData(QueryRecordsRequest queryRecordsRequest, ProjectParams projectParams) {
    // TODO use actual DSL instead of onboard with the option for hostwise.
    Object result = getResponseFromHealthSourceProvider(queryRecordsRequest, projectParams.getAccountIdentifier(),
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), MetricPackServiceImpl.SUMOLOGIC_DSL);
    TimeSeries timeSeries =
        TimeSeries.builder().timeseriesName("sampleTimeseries").build(); // TODO Understand multiple Timeseries
    MetricRecordsResponse metricRecordsResponse = MetricRecordsResponse.builder().status(Status.SUCCESS).build();
    metricRecordsResponse.getTimeSeriesData().add(timeSeries); // TODO Add actual data.
    return metricRecordsResponse;
  }

  @Override
  public LogRecordsResponse fetchLogData(QueryRecordsRequest queryRecordsRequest, ProjectParams projectParams) {
    // TODO use actual DSL instead of onboard with the option for hostwise.
    return LogRecordsResponse.builder().status(Status.SUCCESS).build();
  }
}
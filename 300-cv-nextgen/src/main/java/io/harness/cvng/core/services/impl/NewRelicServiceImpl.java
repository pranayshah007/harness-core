package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.ThirdPartyApiResponseStatus;
import io.harness.cvng.beans.newrelic.NewRelicApplication;
import io.harness.cvng.beans.newrelic.NewRelicApplicationFetchRequest;
import io.harness.cvng.beans.newrelic.NewRelicFetchSampleDataRequest;
import io.harness.cvng.beans.newrelic.NewRelicMetricPackValidationRequest;
import io.harness.cvng.core.beans.MetricPackValidationResponse;
import io.harness.cvng.core.beans.MetricPackValidationResponse.MetricValidationResponse;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.TimeSeriesSampleDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.NewRelicService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.datacollection.exception.DataCollectionDSLException;
import io.harness.datacollection.exception.DataCollectionException;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JsonOrgJsonProvider;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

@Slf4j
public class NewRelicServiceImpl implements NewRelicService {
  private static final List<String> NEW_RELIC_ENDPOINTS =
      Arrays.asList("https://insights-api.newrelic.com/", "https://insights-api.eu.newrelic.com/");

  @Inject private OnboardingService onboardingService;

  @Override
  public List<String> getNewRelicEndpoints() {
    return NEW_RELIC_ENDPOINTS;
  }

  @Override
  public List<NewRelicApplication> getNewRelicApplications(String accountId, String connectorIdentifier,
      String orgIdentifier, String projectIdentifier, String filter, String tracingId) {
    DataCollectionRequest request = NewRelicApplicationFetchRequest.builder()
                                        .type(DataCollectionRequestType.NEWRELIC_APPS_REQUEST)
                                        .filter(filter)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(request)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(accountId)
                                                    .orgIdentifier(orgIdentifier)
                                                    .tracingId(tracingId)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();

    OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);
    final Gson gson = new Gson();
    Type type = new TypeToken<List<NewRelicApplication>>() {}.getType();
    return gson.fromJson(JsonUtils.asJson(response.getResult()), type);
  }

  @Override
  public MetricPackValidationResponse validateData(String accountId, String connectorIdentifier, String orgIdentifier,
      String projectIdentifier, String appName, String appId, List<MetricPackDTO> metricPacks, String tracingId) {
    try {
      DataCollectionRequest request = NewRelicMetricPackValidationRequest.builder()
                                          .type(DataCollectionRequestType.NEWRELIC_VALIDATION_REQUEST)
                                          .applicationName(appName)
                                          .applicationId(appId)
                                          .metricPackDTOSet(new HashSet(metricPacks))
                                          .build();
      OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                      .dataCollectionRequest(request)
                                                      .connectorIdentifier(connectorIdentifier)
                                                      .accountId(accountId)
                                                      .orgIdentifier(orgIdentifier)
                                                      .tracingId(tracingId)
                                                      .projectIdentifier(projectIdentifier)
                                                      .build();

      OnboardingResponseDTO response = onboardingService.getOnboardingResponse(accountId, onboardingRequestDTO);
      final Gson gson = new Gson();
      Type type = new TypeToken<List<MetricValidationResponse>>() {}.getType();
      List<MetricValidationResponse> metricValidationResponseList =
          gson.fromJson(JsonUtils.asJson(response.getResult()), type);
      MetricPackValidationResponse validationResponse = MetricPackValidationResponse.builder()
                                                            .overallStatus(ThirdPartyApiResponseStatus.SUCCESS)
                                                            .metricValidationResponses(metricValidationResponseList)
                                                            .metricPackName("Performance")
                                                            .build();
      validationResponse.updateStatus();
      return validationResponse;
    } catch (DataCollectionException ex) {
      return MetricPackValidationResponse.builder().overallStatus(ThirdPartyApiResponseStatus.FAILED).build();
    }
  }

  @Override
  public LinkedHashMap fetchSampleData(
      ProjectParams projectParams, String connectorIdentifier, String query, String tracingId) {
    try {
      DataCollectionRequest request = NewRelicFetchSampleDataRequest.builder()
                                          .type(DataCollectionRequestType.NEWRELIC_SAMPLE_FETCH_REQUEST)
                                          .query(query)
                                          .build();
      OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                      .dataCollectionRequest(request)
                                                      .connectorIdentifier(connectorIdentifier)
                                                      .accountId(projectParams.getAccountIdentifier())
                                                      .orgIdentifier(projectParams.getOrgIdentifier())
                                                      .tracingId(tracingId)
                                                      .projectIdentifier(projectParams.getProjectIdentifier())
                                                      .build();

      OnboardingResponseDTO response =
          onboardingService.getOnboardingResponse(projectParams.getAccountIdentifier(), onboardingRequestDTO);

      final Gson gson = new Gson();
      Type type = new TypeToken<LinkedHashMap>() {}.getType();
      return gson.fromJson(JsonUtils.asJson(response.getResult()), type);
    } catch (DataCollectionException ex) {
      return null;
    }
  }

  @Override
  public List<TimeSeriesSampleDTO> parseSampleData(ProjectParams projectParams, String jsonResponse, String groupName,
      String metricValueJsonPath, String timestampJsonPath, String timestampFormat) {
    try {
      List metricValueArr = compute(jsonResponse, metricValueJsonPath);
      List timestampArr = compute(jsonResponse, timestampJsonPath);

      Preconditions.checkState(metricValueArr.size() == timestampArr.size(),
          "List of metric values does not match the list of timestamps in the response.");
      List<TimeSeriesSampleDTO> parsedResponseList = new ArrayList<>();
      int lengthOfValues = metricValueArr.size();

      for (int i = 0; i < lengthOfValues; i++) {
        Long timestamp = parseTimestamp(timestampArr.get(i), timestampFormat);

        parsedResponseList.add(
            TimeSeriesSampleDTO.builder()
                .metricValue(metricValueArr.get(i) == null ? null : Double.valueOf(metricValueArr.get(i).toString()))
                .timestamp(timestamp)
                .txnName(groupName)
                .build());
      }

      return parsedResponseList;
    } catch (Exception ex) {
      log.error("Exception while parsing jsonObject {} and metricPath {} and timestampPath {}", jsonResponse,
          metricValueJsonPath, timestampJsonPath);
      throw new RuntimeException("Unable to parse the response object with the given json paths", ex);
    }
  }

  public List compute(String jsonValue, String jsonPath) {
    JSONObject object = new JSONObject(jsonValue);
    Configuration conf = Configuration.defaultConfiguration()
                             .jsonProvider(new JsonOrgJsonProvider())
                             .addOptions(Option.SUPPRESS_EXCEPTIONS);
    JSONArray responseArray = JsonPath.using(conf).parse(object).read(jsonPath);
    return responseArray.toList();
  }

  private long getTimestampInMillis(long timestamp) {
    long now = Instant.now().toEpochMilli();
    if (timestamp != 0 && String.valueOf(timestamp).length() < String.valueOf(now).length()) {
      // Timestamp is in seconds. Convert to millis
      timestamp = timestamp * 1000;
    } else if (String.valueOf(timestamp).length() == String.valueOf(TimeUnit.MILLISECONDS.toNanos(now)).length()) {
      timestamp = TimeUnit.NANOSECONDS.toMillis(timestamp);
    }
    return timestamp;
  }

  private long parseTimestamp(Object timestampObj, String format) {
    long timestamp;
    if (timestampObj instanceof Long) {
      timestamp = (Long) timestampObj;
      timestamp = getTimestampInMillis(timestamp);
    } else if (timestampObj instanceof Double) {
      timestamp = ((Double) timestampObj).longValue();
      timestamp = getTimestampInMillis(timestamp);
    } else if (timestampObj instanceof Integer) {
      timestamp = ((Integer) timestampObj).longValue();
      timestamp = getTimestampInMillis(timestamp);
    } else {
      try {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
        Date date = simpleDateFormat.parse((String) timestampObj);
        timestamp = date.toInstant().toEpochMilli();
      } catch (ParseException e) {
        throw new DataCollectionDSLException("Unable to parse timestamp", e);
      }
    }
    return timestamp;
  }
}

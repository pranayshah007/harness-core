/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.delegatetasks.cv.CVConstants.VERIFICATION_HOST_PLACEHOLDER;
import static software.wings.service.impl.newrelic.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.network.Http;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;

import software.wings.beans.apm.Method;
import software.wings.helpers.ext.apm.APMRestClient;
import software.wings.service.impl.analysis.MetricElement;
import software.wings.service.impl.apm.APMMetricInfo;
import software.wings.service.impl.apm.APMResponseParser;
import software.wings.service.impl.apm.CustomAPMDataCollectionInfo;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * @author Praveen
 */

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class CustomAPMDataCollector implements MetricsDataCollector<CustomAPMDataCollectionInfo> {
  private static final String URL_BODY_APPENDER = "__harness-body__";
  private DataCollectionExecutionContext dataCollectionExecutionContext;
  private CustomAPMDataCollectionInfo dataCollectionInfo;
  @Inject private EncryptionService encryptionService;
  private static Pattern pattern = Pattern.compile("\\$\\{(.*?)\\}");
  private Map<String, String> decryptedFields = new HashMap<>();
  @Override
  public void init(DataCollectionExecutionContext dataCollectionExecutionContext,
      CustomAPMDataCollectionInfo dataCollectionInfo) throws DataCollectionException {
    this.dataCollectionInfo = dataCollectionInfo;
    this.dataCollectionExecutionContext = dataCollectionExecutionContext;
    if (!EmptyPredicate.isEmpty(dataCollectionInfo.getEncryptedDataDetails())) {
      char[] decryptedValue;
      for (EncryptedDataDetail encryptedDataDetail : dataCollectionInfo.getEncryptedDataDetails()) {
        decryptedValue = encryptionService.getDecryptedValue(encryptedDataDetail, false);
        if (decryptedValue != null) {
          decryptedFields.put(encryptedDataDetail.getFieldName(), new String(decryptedValue));
        }
      }
    }
  }

  @Override
  public int getHostBatchSize() {
    return 1;
  }

  @Override
  public List<MetricElement> fetchMetrics(List<String> hostBatch) throws DataCollectionException {
    List<MetricElement> metricElements = new ArrayList<>();
    if (dataCollectionInfo.isCanaryUrlPresent()) {
      dataCollectionInfo.getCanaryMetricInfos().forEach(canaryInfo -> {
        List<APMResponseParser.APMResponseData> apmResponseDataList = new ArrayList<>();
        apmResponseDataList.addAll(collectData(canaryInfo.getHostName(), Lists.newArrayList(canaryInfo)));
        Collection<NewRelicMetricDataRecord> dataRecords = APMResponseParser.extract(apmResponseDataList);
        metricElements.addAll(convertToMetricElements(dataRecords, canaryInfo.getHostName()));
      });
    } else {
      hostBatch.forEach(host -> {
        List<APMResponseParser.APMResponseData> apmResponseDataList = new ArrayList<>();
        apmResponseDataList.addAll(collectData(host, dataCollectionInfo.getMetricEndpoints()));
        Collection<NewRelicMetricDataRecord> dataRecords = APMResponseParser.extract(apmResponseDataList);
        metricElements.addAll(convertToMetricElements(dataRecords, host));
      });
    }

    return metricElements;
  }

  List<MetricElement> convertToMetricElements(Collection<NewRelicMetricDataRecord> metricDataRecords, String host) {
    List<MetricElement> metricElements = new ArrayList<>();
    metricDataRecords.forEach(dataRecord -> {
      metricElements.add(MetricElement.builder()
                             .name(dataRecord.getName())
                             .groupName(dataRecord.getGroupName())
                             .host(host)
                             .tag(dataRecord.getTag())
                             .values(dataRecord.getValues())
                             .timestamp(dataRecord.getTimeStamp())
                             .build());
    });
    return metricElements;
  }

  @Override
  public List<MetricElement> fetchMetrics() throws DataCollectionException {
    List<MetricElement> metricElements = new ArrayList<>();
    List<APMResponseParser.APMResponseData> apmResponseDataList = new ArrayList<>();
    apmResponseDataList.addAll(collectData(null, dataCollectionInfo.getMetricEndpoints()));
    Collection<NewRelicMetricDataRecord> dataRecords = APMResponseParser.extract(apmResponseDataList);
    metricElements.addAll(convertToMetricElements(dataRecords, "default"));
    return metricElements;
  }

  protected String resolvedUrl(String url, String host, long startTime, long endTime) {
    String result = url;
    if (result.contains("${start_time}")) {
      result = result.replace("${start_time}", String.valueOf(dataCollectionInfo.getStartTime().getEpochSecond()));
    }
    if (result.contains("${end_time}")) {
      result = result.replace("${end_time}", String.valueOf(endTime));
    }
    if (result.contains("${start_time_seconds}")) {
      result = result.replace("${start_time_seconds}", String.valueOf(startTime / 1000L));
    }
    if (result.contains("${end_time_seconds}")) {
      result = result.replace("${end_time_seconds}", String.valueOf(endTime / 1000L));
    }

    if (result.contains(VERIFICATION_HOST_PLACEHOLDER)) {
      result = result.replace(VERIFICATION_HOST_PLACEHOLDER, host);
    }

    Matcher matcher = pattern.matcher(result);
    while (matcher.find()) {
      result = result.replace(
          matcher.group(), decryptedFields.get(matcher.group().substring(2, matcher.group().length() - 1)));
    }

    return result;
  }

  public static OkHttpClient getUnsafeHttpClient(String baseUrl) {
    return Http.getUnsafeOkHttpClient(baseUrl, 15, 60);
  }

  private APMRestClient getAPMRestClient(final String baseUrl) {
    final Retrofit retrofit = new Retrofit.Builder()
                                  .baseUrl(baseUrl)
                                  .addConverterFactory(JacksonConverterFactory.create())
                                  .client(getUnsafeHttpClient(baseUrl))
                                  .build();
    return retrofit.create(APMRestClient.class);
  }

  private Map<String, String> getStringsToMask() {
    Map<String, String> maskFields = new HashMap<>();
    if (isNotEmpty(this.decryptedFields)) {
      decryptedFields.forEach((k, v) -> { maskFields.put(v, "<" + k + ">"); });
    }
    return maskFields;
  }

  private String executeRestCall(Call<Object> request) {
    Object response = dataCollectionExecutionContext.executeRequest(
        "Fetch request to " + dataCollectionInfo.getApmConfig().getUrl(), request, getStringsToMask());
    // check if it's already a well parsed json, if not, convert it.
    String parsedResponse = null;
    try {
      JSONObject parsedObj = new JSONObject(response.toString());
      parsedResponse = response.toString();
    } catch (Exception ex) {
      parsedResponse = JsonUtils.asJson(response);
    }

    return parsedResponse;
  }

  private String fixEncodingIssues(String initialUrl) {
    if (initialUrl.contains("`")) {
      try {
        initialUrl = initialUrl.replaceAll("`", URLEncoder.encode("`", "UTF-8"));
      } catch (Exception e) {
        log.warn("Unsupported exception caught when encoding a back-tick", e);
      }
    }
    return initialUrl;
  }

  protected List<Call<Object>> createRestCallFromDataCollectionInfo(
      String host, CustomAPMDataCollectionInfo dataCollectionInfo, List<APMMetricInfo> collectionInfoList) {
    // Iterate through the metricInfos and create calls for each of them
    List<Call<Object>> callsToFetchData = new ArrayList<>();
    for (APMMetricInfo metricInfo : collectionInfoList) {
      String[] urlAndBody = metricInfo.getUrl().split(URL_BODY_APPENDER);
      String urlToCollect = fixEncodingIssues(urlAndBody[0]), body = urlAndBody.length > 1 ? urlAndBody[1] : "";
      BiMap<String, Object> headersBiMap =
          resolveDollarReferences(dataCollectionInfo.getApmConfig().collectionHeaders());

      BiMap<String, Object> optionsBiMap =
          resolveDollarReferences(dataCollectionInfo.getApmConfig().collectionParams());

      String resolvedUrl = resolvedUrl(urlToCollect, host, dataCollectionInfo.getStartTime().toEpochMilli(),
          dataCollectionInfo.getEndTime().toEpochMilli());

      String resolvedBody = resolvedUrl(
          body, host, dataCollectionInfo.getStartTime().toEpochMilli(), dataCollectionInfo.getEndTime().toEpochMilli());

      Map<String, Object> bodyMap = isNotEmpty(resolvedBody) ? new JSONObject(resolvedBody).toMap() : new HashMap<>();

      if (Method.POST.equals(metricInfo.getMethod())) {
        callsToFetchData.add(getAPMRestClient(dataCollectionInfo.getApmConfig().getUrl())
                                 .postCollect(resolvedUrl, headersBiMap, optionsBiMap, bodyMap));
      } else {
        callsToFetchData.add(getAPMRestClient(dataCollectionInfo.getApmConfig().getUrl())
                                 .collect(resolvedUrl, headersBiMap, optionsBiMap));
      }
    }
    return callsToFetchData;
  }

  protected List<APMResponseParser.APMResponseData> collectData(String host, List<APMMetricInfo> collectionInfoList) {
    List<APMResponseParser.APMResponseData> responseDataList = new ArrayList<>();
    List<Call<Object>> calls = createRestCallFromDataCollectionInfo(host, dataCollectionInfo, collectionInfoList);
    for (Call<Object> restCall : calls) {
      responseDataList.add(new APMResponseParser.APMResponseData(
          host, DEFAULT_GROUP_NAME, executeRestCall(restCall), collectionInfoList));
    }
    return responseDataList;
  }

  protected BiMap<String, Object> resolveDollarReferences(Map<String, String> input) {
    BiMap<String, Object> output = HashBiMap.create();
    if (input == null) {
      return output;
    }
    for (Map.Entry<String, String> entry : input.entrySet()) {
      if (entry.getValue().startsWith("${")) {
        output.put(entry.getKey(), decryptedFields.get(entry.getValue().substring(2, entry.getValue().length() - 1)));
      } else {
        output.put(entry.getKey(), entry.getValue());
      }
    }

    return output;
  }
}

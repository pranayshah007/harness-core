/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
import static software.wings.delegatetasks.cv.CVConstants.AZURE_BASE_URL;
import static software.wings.delegatetasks.cv.CVConstants.AZURE_TOKEN_URL;
import static software.wings.delegatetasks.cv.CVConstants.CONTROL_HOST_NAME;
import static software.wings.delegatetasks.cv.CVConstants.DATA_COLLECTION_RETRY_SLEEP;
import static software.wings.delegatetasks.cv.CVConstants.TEST_HOST_NAME;
import static software.wings.delegatetasks.cv.CVConstants.VERIFICATION_HOST_PLACEHOLDER;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.WingsException;
import io.harness.network.Http;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;
import io.harness.time.Timestamp;

import software.wings.beans.TaskType;
import software.wings.beans.dto.NewRelicMetricDataRecord;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.cv.CVAWSAuthHeaderSigner;
import software.wings.delegatetasks.cv.CVAWSS4SignerBase;
import software.wings.delegatetasks.cv.RequestExecutor;
import software.wings.helpers.ext.apm.APMRestClient;
import software.wings.service.impl.VerificationLogContext;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AzureLogAnalyticsConnectionDetails;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.DataCollectionTaskResult.DataCollectionTaskStatus;
import software.wings.service.impl.apm.APMDataCollectionInfo;
import software.wings.service.impl.apm.APMMetricInfo;
import software.wings.service.impl.apm.APMResponseParser;
import software.wings.service.intfc.analysis.ClusterLevel;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.TreeBasedTable;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.xerces.impl.dv.util.Base64;
import org.json.JSONObject;
import org.slf4j.Logger;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class APMDataCollectionTask extends AbstractDelegateDataCollectionTask {
  private static final int CANARY_DAYS_TO_COLLECT = 7;

  private static final String BATCH_REGEX = "\\$harness_batch\\{([^,]*),([^}]*)\\}";
  private static final String DATADOG_API_MASK = "api_key=([^&]*)&application_key=([^&]*)&";
  private static final String BATCH_TEXT = "$harness_batch";
  private static final int MAX_HOSTS_PER_BATCH = 15;
  private static Pattern pattern = Pattern.compile("\\$\\{(.*?)\\}");
  private static final String URL_BODY_APPENDER = "__harness-body__";
  private static final int FIVE_MINS_IN_SECONDS = 5 * 60;
  private static final int TWO_MINS_IN_SECONDS = 2 * 60;

  @Inject private RequestExecutor requestExecutor;

  private int collectionWindow = 1;

  private APMDataCollectionInfo dataCollectionInfo;
  private Map<String, String> decryptedFields = new HashMap<>();

  // special case for azure. This is unfortunately a hack
  private AzureLogAnalyticsConnectionDetails azureLogAnalyticsConnectionDetails;

  public APMDataCollectionTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  protected boolean is24X7Task() {
    return getTaskType().equalsIgnoreCase(TaskType.APM_24_7_METRIC_DATA_COLLECTION_TASK.name());
  }

  @Override
  protected int getInitialDelayMinutes() {
    if (is24X7Task()) {
      return 0;
    }
    int delayMinsFromDataCollectionTask = (int) TimeUnit.SECONDS.toMinutes(dataCollectionInfo.getInitialDelaySeconds());
    if (delayMinsFromDataCollectionTask > 5 || delayMinsFromDataCollectionTask < 0) {
      return 2;
    }
    return delayMinsFromDataCollectionTask;
  }

  @Override
  protected int getInitialDelaySeconds() {
    if (is24X7Task()) {
      return 0;
    }
    if (dataCollectionInfo.getInitialDelaySeconds() > FIVE_MINS_IN_SECONDS
        || dataCollectionInfo.getInitialDelaySeconds() < 0) {
      return TWO_MINS_IN_SECONDS;
    }
    return dataCollectionInfo.getInitialDelaySeconds();
  }

  @Override
  protected DataCollectionTaskResult initDataCollection(TaskParameters parameters) {
    dataCollectionInfo = (APMDataCollectionInfo) parameters;
    collectionWindow =
        dataCollectionInfo.getDataCollectionFrequency() != 0 ? dataCollectionInfo.getDataCollectionFrequency() : 1;
    log.info("apm collection - dataCollectionInfo: {}", dataCollectionInfo);

    if (!EmptyPredicate.isEmpty(dataCollectionInfo.getEncryptedDataDetails())) {
      char[] decryptedValue;
      for (EncryptedDataDetail encryptedDataDetail : dataCollectionInfo.getEncryptedDataDetails()) {
        decryptedValue = encryptionService.getDecryptedValue(encryptedDataDetail, false);
        if (decryptedValue != null) {
          decryptedFields.put(encryptedDataDetail.getFieldName(), new String(decryptedValue));
        }
      }
    }
    return DataCollectionTaskResult.builder()
        .status(DataCollectionTaskResult.DataCollectionTaskStatus.SUCCESS)
        .stateType(dataCollectionInfo.getStateType())
        .build();
  }

  @Override
  protected Logger getLogger() {
    return log;
  }

  @Override
  protected DelegateStateType getStateType() {
    return dataCollectionInfo.getStateType();
  }

  @Override
  protected Runnable getDataCollector(DataCollectionTaskResult taskResult) throws IOException {
    return new APMMetricCollector(dataCollectionInfo, taskResult,
        this.getTaskType().equalsIgnoreCase(TaskType.APM_24_7_METRIC_DATA_COLLECTION_TASK.name()));
  }

  private class APMMetricCollector implements Runnable {
    private final APMDataCollectionInfo dataCollectionInfo;
    private final DataCollectionTaskResult taskResult;
    private long collectionStartTime;
    private int dataCollectionMinute;
    private boolean firstDataCollectionCompleted;
    private final long collectionStartMinute;
    private long lastEndTime;
    private long currentEndTime;
    private int currentElapsedTime;
    Map<String, Long> hostStartMinuteMap;
    boolean is24x7Task;

    private APMMetricCollector(
        APMDataCollectionInfo dataCollectionInfo, DataCollectionTaskResult taskResult, boolean is24x7Task) {
      this.dataCollectionInfo = dataCollectionInfo;
      this.taskResult = taskResult;
      this.collectionStartMinute = Timestamp.currentMinuteBoundary();
      this.collectionStartTime = Timestamp.minuteBoundary(dataCollectionInfo.getStartTime());
      this.dataCollectionMinute = dataCollectionInfo.getDataCollectionMinute();
      this.lastEndTime = dataCollectionInfo.getStartTime();
      this.currentElapsedTime = 0;
      this.is24x7Task = is24x7Task;
      hostStartMinuteMap = new HashMap<>();
      this.firstDataCollectionCompleted = false;
    }

    private APMRestClient getAPMRestClient(final String baseUrl, final boolean validateCert) {
      final Retrofit retrofit = new Retrofit.Builder()
                                    .baseUrl(baseUrl)
                                    .addConverterFactory(JacksonConverterFactory.create())
                                    .client(validateCert ? Http.getSafeOkHttpClientBuilder(baseUrl, 15, 60).build()
                                                         : getUnsafeHttpClient(baseUrl))
                                    .build();
      return retrofit.create(APMRestClient.class);
    }

    private String resolvedUrl(String url, String host, long startTime, long endTime) {
      String result = url;
      if (result.contains("${start_time}")) {
        result = result.replace("${start_time}", String.valueOf(startTime));
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
        String fieldKey = matcher.group().substring(2, matcher.group().length() - 1);
        Preconditions.checkState(decryptedFields.containsKey(fieldKey), "Could not resolve expression ${%s}", fieldKey);
        result = result.replace(matcher.group(), decryptedFields.get(fieldKey));
      }

      return result;
    }

    private List<String> resolveDollarReferences(String url, String host, AnalysisComparisonStrategy strategy) {
      if (isEmpty(url)) {
        return Collections.EMPTY_LIST;
      }
      // TODO: Come back and clean up the time variables.
      List<String> result = new ArrayList<>();
      long startTime = lastEndTime;
      long possibleEndTime = !firstDataCollectionCompleted ? startTime + TimeUnit.MINUTES.toMillis(1)
                                                           : startTime + TimeUnit.MINUTES.toMillis(collectionWindow);
      long endTime = Math.min(possibleEndTime,
          collectionStartMinute + TimeUnit.MINUTES.toMillis(dataCollectionInfo.getDataCollectionTotalTime()));
      currentEndTime = endTime;

      if (!dataCollectionInfo.isCanaryUrlPresent() && TEST_HOST_NAME.equals(host)
          && strategy == AnalysisComparisonStrategy.COMPARE_WITH_CURRENT) {
        for (int i = 0; i <= CANARY_DAYS_TO_COLLECT; i++) {
          String hostName = getHostNameForTestControl(i);
          long thisEndTime = endTime - TimeUnit.DAYS.toMillis(i);
          long thisStartTime = startTime - TimeUnit.DAYS.toMillis(i);
          if (!hostStartMinuteMap.containsKey(hostName)) {
            hostStartMinuteMap.put(hostName, thisStartTime);
          }
          result.add(resolvedUrl(url, hostName, thisStartTime, thisEndTime));
        }
      } else {
        if (isPredictiveAnalysis() && dataCollectionMinute == 0 && !is24x7Task) {
          startTime = startTime - TimeUnit.MINUTES.toMillis(PREDECTIVE_HISTORY_MINUTES);
        } else if (is24x7Task) {
          endTime = startTime + TimeUnit.MINUTES.toMillis(dataCollectionInfo.getDataCollectionTotalTime());
        }
        result.add(resolvedUrl(url, host, startTime, endTime));
      }
      log.info("Start and end times for minute {} were {} and {}", dataCollectionMinute, startTime, endTime);
      return result;
    }

    private String getHostNameForTestControl(int i) {
      return i == 0 ? TEST_HOST_NAME : CONTROL_HOST_NAME + "-" + i;
    }

    private BiMap<String, Object> resolveDollarReferences(Map<String, String> input) {
      BiMap<String, Object> output = HashBiMap.create();
      if (input == null) {
        return output;
      }
      for (Map.Entry<String, String> entry : input.entrySet()) {
        String entryVal = entry.getValue();
        int placeHolderIndex = entryVal.indexOf("${");
        if (placeHolderIndex != -1) {
          String stringToReplace = entryVal.substring(placeHolderIndex + 2, entryVal.indexOf('}', placeHolderIndex));
          if (decryptedFields.get(stringToReplace) == null) {
            continue;
          }
          String updatedValue =
              entryVal.replace(String.format("${%s}", stringToReplace), decryptedFields.get(stringToReplace));
          output.put(entry.getKey(), updatedValue);
          if (dataCollectionInfo.isBase64EncodingRequired()) {
            output.put(entry.getKey(), resolveBase64Reference(updatedValue));
          }
        } else {
          output.put(entry.getKey(), entryVal);
        }
      }

      return output;
    }

    private Map<String, String> getStringsToMask() {
      Map<String, String> maskFields = new HashMap<>();
      if (isNotEmpty(decryptedFields)) {
        decryptedFields.forEach((k, v) -> { maskFields.put(v, "<" + k + ">"); });
      }
      return maskFields;
    }

    private String collect(Call<Object> request) {
      try {
        ThirdPartyApiCallLog apiCallLog =
            ThirdPartyApiCallLog.fromDetails(createApiCallLog(dataCollectionInfo.getStateExecutionId()));
        apiCallLog.setTitle("Fetch request to: " + dataCollectionInfo.getBaseUrl());
        Object response = requestExecutor.executeRequest(apiCallLog, request, getStringsToMask());
        return JsonUtils.asJson(response);
      } catch (Exception e) {
        throw new WingsException("Error while fetching data. " + ExceptionUtils.getMessage(e), e);
      }
    }

    private String fetchHostKey(BiMap<String, Object> optionsBiMap) {
      for (Map.Entry<String, Object> entry : optionsBiMap.entrySet()) {
        if (entry.getValue() instanceof String) {
          if (((String) entry.getValue()).contains("${host}")) {
            return entry.getKey();
          }
        }
      }
      return "";
    }

    private Map<String, String> fetchAdditionalHeaders(APMDataCollectionInfo dataCollectionInfo) {
      // Special case for getting the bearer token for azure log analytics
      if (!dataCollectionInfo.getBaseUrl().contains(AZURE_BASE_URL)) {
        return null;
      }
      Map<String, Object> resolvedOptions = resolveDollarReferences(dataCollectionInfo.getOptions());
      String clientId = azureLogAnalyticsConnectionDetails == null ? (String) resolvedOptions.get("client_id")
                                                                   : azureLogAnalyticsConnectionDetails.getClientId();
      String clientSecret = azureLogAnalyticsConnectionDetails == null
          ? (String) resolvedOptions.get("client_secret")
          : azureLogAnalyticsConnectionDetails.getClientSecret();
      String tenantId = azureLogAnalyticsConnectionDetails == null ? (String) resolvedOptions.get("tenant_id")
                                                                   : azureLogAnalyticsConnectionDetails.getTenantId();
      if (azureLogAnalyticsConnectionDetails == null) {
        // saving the details in this object so we can remove the details from the request parameters
        azureLogAnalyticsConnectionDetails = AzureLogAnalyticsConnectionDetails.builder()
                                                 .clientId(clientId)
                                                 .clientSecret(clientSecret)
                                                 .tenantId(tenantId)
                                                 .build();

        dataCollectionInfo.getOptions().remove("client_id");
        dataCollectionInfo.getOptions().remove("tenant_id");
        dataCollectionInfo.getOptions().remove("client_secret");
      }

      Preconditions.checkNotNull(
          clientId, "client_id parameter cannot be null when collecting data from azure log analytics");
      Preconditions.checkNotNull(
          tenantId, "tenant_id parameter cannot be null when collecting data from azure log analytics");
      Preconditions.checkNotNull(
          clientSecret, "client_secret parameter cannot be null when collecting data from azure log analytics");
      String urlForToken = tenantId + "/oauth2/token";

      Map<String, String> bearerTokenHeader = new HashMap<>();
      bearerTokenHeader.put("Content-Type", "application/x-www-form-urlencoded");
      Call<Object> bearerTokenCall = getAPMRestClient(AZURE_TOKEN_URL, false)
                                         .getAzureBearerToken(urlForToken, bearerTokenHeader, "client_credentials",
                                             clientId, AZURE_BASE_URL, clientSecret);

      Object response = requestExecutor.executeRequest(bearerTokenCall);
      Map<String, Object> responseMap = new JSONObject(JsonUtils.asJson(response)).toMap();
      String bearerToken = (String) responseMap.get("access_token");

      String headerVal = "Bearer " + bearerToken;
      Map<String, String> header = new HashMap<>();
      header.put("Authorization", headerVal);
      return header;
    }

    private Map<String, String> getQueryMapFromUrl(URL url) {
      final Map<String, String> res = new HashMap<>();
      final String query = url.getQuery();
      if (null != query) {
        final StringTokenizer st = new StringTokenizer(query, "&");
        while (st.hasMoreTokens()) {
          final String tok = st.nextToken();
          final int delimPos = tok.indexOf('=');
          final String key = tok.substring(0, delimPos).trim();
          final String value = tok.substring(delimPos + 1).trim();

          res.put(key, value);
        }
      }
      return res;
    }

    // This is ugly String manipulation. But there's no other way around it for now. AWS requires some params be
    // encoded.
    private String getEncodedUrl(URL endpointUrl, Map<String, String> options) {
      StringBuilder queryParams = new StringBuilder();
      options.forEach((k, v) -> {
        try {
          queryParams.append(k);
          queryParams.append('=');
          queryParams.append(URLEncoder.encode(v, "UTF-8"));
          queryParams.append('&');
        } catch (Exception ex) {
          log.error("Exception while processing the query in AWS Prometheus", ex);
        }
      });
      String finalQparam = queryParams.toString();

      if (finalQparam.endsWith("&")) {
        finalQparam = finalQparam.substring(0, finalQparam.length() - 1);
      }
      return endpointUrl.getProtocol() + "://" + endpointUrl.getHost() + endpointUrl.getPath() + "?" + finalQparam;
    }

    private String getAwsAuthHeader(
        BiMap<String, Object> headersBiMap, BiMap<String, Object> optionsBiMap, String url) {
      URL endpoint = null;
      log.info("In getAwsAuthHeader for AWS Prometheus");
      try {
        endpoint = new URL(url);
        // make query params as a map
        Map<String, String> queryMap = getQueryMapFromUrl(endpoint);

        String secretKey = decryptedFields.get("secretKey");
        String accessKey = dataCollectionInfo.getPrometheusInfo().getAccessKey();
        if (accessKey == null) {
          accessKey = decryptedFields.get("accessKey");
        }
        Map<String, String> headerMap = new HashMap<>();
        optionsBiMap.forEach((k, v) -> queryMap.put(k, (String) v));
        // headersBiMap.forEach((k, v) -> headerMap.put(k, (String) v));

        // this is exclusively written for AWS Managed Prometheus. So we're assuming a GET method.
        // encode the endpoint and it's query params.
        String encodedUrlStr = getEncodedUrl(endpoint, queryMap);
        URL encodedUrl = new URL(encodedUrlStr);

        CVAWSAuthHeaderSigner signer =
            new CVAWSAuthHeaderSigner(encodedUrl, "GET", dataCollectionInfo.getPrometheusInfo().getAwsService(),
                dataCollectionInfo.getPrometheusInfo().getAwsRegion());

        String authSign =
            signer.computeSignature(headerMap, queryMap, CVAWSS4SignerBase.EMPTY_BODY_SHA256, accessKey, secretKey);
        headersBiMap.putAll(headerMap);
        headersBiMap.put("Authorization", authSign);
        return encodedUrlStr.replace(dataCollectionInfo.getBaseUrl(), "");
      } catch (MalformedURLException e) {
        log.error("Exception while parsing URL in AWS Prometheus", e);
      }
      return null;
    }
    private List<APMResponseParser.APMResponseData> collect(String baseUrl, boolean validateCert,
        Map<String, String> headers, Map<String, String> options, String initialUrl, List<APMMetricInfo> metricInfos,
        AnalysisComparisonStrategy strategy) throws IOException {
      // OkHttp seems to have issues encoding backtick, so explictly encoding it.
      String[] urlAndBody = initialUrl.split(URL_BODY_APPENDER);
      initialUrl = urlAndBody[0];
      final String body = urlAndBody.length > 1 ? urlAndBody[1] : "";
      if (initialUrl.contains("`")) {
        try {
          initialUrl = initialUrl.replaceAll("`", URLEncoder.encode("`", "UTF-8"));
        } catch (Exception e) {
          log.warn("Unsupported exception caught when encoding a back-tick", e);
        }
      }
      List<APMResponseParser.APMResponseData> responses = new ArrayList<>();

      BiMap<String, Object> headersBiMap = resolveDollarReferences(headers);
      BiMap<String, Object> optionsBiMap = resolveDollarReferences(options);

      String hostKey = fetchHostKey(optionsBiMap);
      List<Callable<APMResponseParser.APMResponseData>> callabels = new ArrayList<>();
      if (dataCollectionInfo.isCanaryUrlPresent()) {
        dataCollectionInfo.getCanaryMetricInfos().forEach(canaryMetricInfo -> {
          String url =
              resolveDollarReferences(canaryMetricInfo.getUrl(), canaryMetricInfo.getHostName(), strategy).get(0);

          List<String> resolvedBodies = resolveDollarReferences(body, TEST_HOST_NAME, strategy);
          if (isEmpty(body)) {
            callabels.add(
                ()
                    -> new APMResponseParser.APMResponseData(canaryMetricInfo.getHostName(), DEFAULT_GROUP_NAME,
                        collect(getAPMRestClient(baseUrl, validateCert).collect(url, headersBiMap, optionsBiMap)),
                        metricInfos));

          } else {
            resolvedBodies.forEach(resolvedBody -> {
              callabels.add(
                  ()
                      -> new APMResponseParser.APMResponseData(canaryMetricInfo.getHostName(), DEFAULT_GROUP_NAME,
                          collect(
                              getAPMRestClient(baseUrl, validateCert)
                                  .postCollect(url, headersBiMap, optionsBiMap, new JSONObject(resolvedBody).toMap())),
                          metricInfos));
            });
          }
        });
      } else if (!isEmpty(hostKey) || initialUrl.contains(VERIFICATION_HOST_PLACEHOLDER)
          || (isNotEmpty(body) && body.contains(VERIFICATION_HOST_PLACEHOLDER))) {
        if (initialUrl.contains(BATCH_TEXT)) {
          List<String> urlList = resolveBatchHosts(initialUrl);
          for (String url : urlList) {
            // host has already been resolved. So it's ok to pass null here.
            List<String> curUrls = resolveDollarReferences(url, null, strategy);
            curUrls.forEach(curUrl
                -> callabels.add(
                    ()
                        -> new APMResponseParser.APMResponseData(null, DEFAULT_GROUP_NAME,
                            collect(
                                getAPMRestClient(baseUrl, validateCert).collect(curUrl, headersBiMap, optionsBiMap)),
                            metricInfos)));
          }
        } else {
          // This is not a batch query
          for (String host : dataCollectionInfo.getHosts().keySet()) {
            List<String> curUrls = resolveDollarReferences(initialUrl, host, strategy);

            if (!isEmpty(body)) {
              String resolvedBody = resolvedUrl(body, host, lastEndTime, System.currentTimeMillis());
              Map<String, Object> bodyMap =
                  isEmpty(resolvedBody) ? new HashMap<>() : new JSONObject(resolvedBody).toMap();

              curUrls.forEach(curUrl
                  -> callabels.add(
                      ()
                          -> new APMResponseParser.APMResponseData(host, dataCollectionInfo.getHosts().get(host),
                              collect(getAPMRestClient(baseUrl, validateCert)
                                          .postCollect(curUrl, headersBiMap, optionsBiMap, bodyMap)),
                              metricInfos)));
            } else {
              curUrls.forEach(curUrl -> {
                // ADD AWS Header here since the entire URL has been formed - For Amazon Managed Prometheus
                String urlToCall = curUrl;
                if (dataCollectionInfo.isAwsRestCall()) {
                  urlToCall = getAwsAuthHeader(headersBiMap, optionsBiMap, baseUrl + curUrl);
                }
                String finalUrl = urlToCall;
                Map<String, Object> headerMap = new HashMap<>();
                headersBiMap.forEach((k, v) -> headerMap.put(k, v));
                callabels.add(
                    ()
                        -> new APMResponseParser.APMResponseData(host, dataCollectionInfo.getHosts().get(host),
                            collect(getAPMRestClient(baseUrl, validateCert).collect(finalUrl, headerMap, optionsBiMap)),
                            metricInfos));
              });
            }
          }
        }

      } else {
        List<String> curUrls = resolveDollarReferences(initialUrl, TEST_HOST_NAME, strategy);
        List<String> resolvedBodies = resolveDollarReferences(body, TEST_HOST_NAME, strategy);
        if (isEmpty(body)) {
          IntStream.range(0, curUrls.size())
              .forEach(index
                  -> callabels.add(
                      ()
                          -> new APMResponseParser.APMResponseData(getHostNameForTestControl(index), DEFAULT_GROUP_NAME,
                              collect(getAPMRestClient(baseUrl, validateCert)
                                          .collect(curUrls.get(index), headersBiMap, optionsBiMap)),
                              metricInfos)));
        } else {
          IntStream.range(0, curUrls.size()).forEach(index -> {
            resolvedBodies.forEach(resolvedBody
                -> callabels.add(
                    ()
                        -> new APMResponseParser.APMResponseData(getHostNameForTestControl(index), DEFAULT_GROUP_NAME,
                            collect(getAPMRestClient(baseUrl, validateCert)
                                        .postCollect(curUrls.get(index), headersBiMap, optionsBiMap,
                                            new JSONObject(resolvedBody).toMap())),
                            metricInfos)));
          });
        }
      }

      executeParallel(callabels)
          .stream()
          .filter(Optional::isPresent)
          .forEach(response -> responses.add(response.get()));
      return responses;
    }

    private String resolveBase64Reference(Object entry) {
      if (entry == null) {
        return null;
      }
      String headerValue = (String) entry;
      int placeholderIndex = headerValue.indexOf("encodeWithBase64(");
      if (placeholderIndex != -1) {
        String stringToEncode = headerValue.substring(
            placeholderIndex + "encodeWithBase64(".length(), headerValue.indexOf(')', placeholderIndex));
        return headerValue.replace(
            String.format("encodeWithBase64(%s)", stringToEncode), Base64.encode(stringToEncode.getBytes()));
      }
      return headerValue;
    }

    /**
     *
     * @param batchUrl urlData{$harness_batch{pod_name:${host},'|'}}
     * @return urlData{pod_name:host1.pod1.com | pod_name:host2.pod3.com }
     */
    protected List<String> resolveBatchHosts(final String batchUrl) {
      List<String> hostList = new ArrayList<>(dataCollectionInfo.getHosts().keySet());
      List<String> batchResolvedUrls = new ArrayList<>();
      Pattern batchPattern = Pattern.compile(BATCH_REGEX);
      Matcher matcher = batchPattern.matcher(batchUrl);
      while (matcher.find()) {
        final String fullBatchToken = matcher.group();
        final String hostString = matcher.group(1);
        final String separator = matcher.group(2).replaceAll("'", "");
        String batchedHosts = "";
        for (int i = 0; i < hostList.size(); i++) {
          batchedHosts += hostString.replace("${host}", hostList.get(i));
          if (((i + 1) % MAX_HOSTS_PER_BATCH) == 0 || i == hostList.size() - 1) {
            String curUrl = batchUrl.replace(fullBatchToken, batchedHosts);
            batchResolvedUrls.add(curUrl);
            batchedHosts = "";
            continue;
          }
          batchedHosts += separator;
        }
      }
      return batchResolvedUrls;
    }

    private boolean isPredictiveAnalysis() {
      return dataCollectionInfo.getStrategy() == AnalysisComparisonStrategy.PREDICTIVE;
    }

    /**
     * if it is time to collect data, return true. Else false.
     * @return
     */
    private boolean shouldRunCollection() {
      if (!firstDataCollectionCompleted) {
        log.info("First data not yet collected. Returning true");
        return true;
      }
      long currentTime = Timestamp.currentMinuteBoundary();
      long lastCollectionTime = lastEndTime + TimeUnit.MINUTES.toMillis(1);
      if ((int) TimeUnit.MILLISECONDS.toMinutes(currentTime - lastCollectionTime) % collectionWindow == 0) {
        log.info("ShouldCollectDataCollection is {} for minute {}, lastCollectionTime {}", true, currentTime,
            lastCollectionTime);
        return true;
      }

      if (currentTime > collectionStartMinute
              + TimeUnit.MINUTES.toMillis(dataCollectionInfo.getDataCollectionTotalTime() + getInitialDelayMinutes())) {
        log.info("ShouldCollectDataCollection is {} for minute {}, collectionStartMinute {}", true, currentTime,
            collectionStartMinute);
        return true;
      }
      return false;
    }

    @Override
    @SuppressWarnings("PMD")
    public void run() {
      int retry = 0;
      try (VerificationLogContext ignored = new VerificationLogContext(dataCollectionInfo.getAccountId(), null,
               dataCollectionInfo.getStateExecutionId(), dataCollectionInfo.getStateType(), OVERRIDE_ERROR)) {
        boolean shouldRunDataCollection = shouldRunCollection();
        while (shouldRunDataCollection && !completed.get() && retry < RETRIES) {
          try {
            TreeBasedTable<String, Long, NewRelicMetricDataRecord> records = TreeBasedTable.create();

            Map<String, String> additionalHeaders = fetchAdditionalHeaders(dataCollectionInfo);
            if (isNotEmpty(additionalHeaders)) {
              if (dataCollectionInfo.getHeaders() == null) {
                dataCollectionInfo.setHeaders(new HashMap<>());
              }
              additionalHeaders.forEach((key, val) -> dataCollectionInfo.getHeaders().put(key, val));
            }

            List<APMResponseParser.APMResponseData> apmResponseDataList = new ArrayList<>();
            if (isNotEmpty(dataCollectionInfo.getCanaryMetricInfos())) {
              apmResponseDataList.addAll(collect(dataCollectionInfo.getBaseUrl(), dataCollectionInfo.isValidateCert(),
                  dataCollectionInfo.getHeaders(), dataCollectionInfo.getOptions(), dataCollectionInfo.getBaseUrl(),
                  dataCollectionInfo.getCanaryMetricInfos(), dataCollectionInfo.getStrategy()));
            }

            for (Map.Entry<String, List<APMMetricInfo>> metricInfoEntry :
                dataCollectionInfo.getMetricEndpoints().entrySet()) {
              apmResponseDataList.addAll(collect(dataCollectionInfo.getBaseUrl(), dataCollectionInfo.isValidateCert(),
                  dataCollectionInfo.getHeaders(), dataCollectionInfo.getOptions(), metricInfoEntry.getKey(),
                  metricInfoEntry.getValue(), dataCollectionInfo.getStrategy()));
            }
            Set<String> groupNameSet = dataCollectionInfo.getHosts() != null
                ? new HashSet<>(dataCollectionInfo.getHosts().values())
                : new HashSet<>();
            Collection<NewRelicMetricDataRecord> newRelicMetricDataRecords =
                APMResponseParser.extract(apmResponseDataList);

            newRelicMetricDataRecords.forEach(newRelicMetricDataRecord -> {
              if (newRelicMetricDataRecord.getTimeStamp() == 0) {
                newRelicMetricDataRecord.setTimeStamp(currentEndTime);
              }
              newRelicMetricDataRecord.setServiceId(dataCollectionInfo.getServiceId());
              newRelicMetricDataRecord.setStateExecutionId(dataCollectionInfo.getStateExecutionId());
              newRelicMetricDataRecord.setWorkflowExecutionId(dataCollectionInfo.getWorkflowExecutionId());
              newRelicMetricDataRecord.setWorkflowId(dataCollectionInfo.getWorkflowId());
              newRelicMetricDataRecord.setCvConfigId(dataCollectionInfo.getCvConfigId());
              long startTimeMinForHost = collectionStartMinute;
              if (hostStartMinuteMap.containsKey(newRelicMetricDataRecord.getHost())) {
                startTimeMinForHost = hostStartMinuteMap.get(newRelicMetricDataRecord.getHost());
              }

              int collectionMin = resolveDataCollectionMinute(
                  newRelicMetricDataRecord.getTimeStamp(), newRelicMetricDataRecord.getHost(), false);
              newRelicMetricDataRecord.setDataCollectionMinute(collectionMin);

              if (isPredictiveAnalysis()) {
                newRelicMetricDataRecord.setHost(newRelicMetricDataRecord.getGroupName());
              }

              newRelicMetricDataRecord.setStateType(dataCollectionInfo.getStateType());
              groupNameSet.add(newRelicMetricDataRecord.getGroupName());

              newRelicMetricDataRecord.setAppId(dataCollectionInfo.getApplicationId());
              if (newRelicMetricDataRecord.getTimeStamp() >= startTimeMinForHost || is24x7Task) {
                records.put(newRelicMetricDataRecord.getName() + newRelicMetricDataRecord.getHost(),
                    newRelicMetricDataRecord.getTimeStamp(), newRelicMetricDataRecord);
              } else {
                log.info("The data record {} is older than startTime. Ignoring", newRelicMetricDataRecord);
              }
            });

            dataCollectionMinute = (int) (TimeUnit.MILLISECONDS.toMinutes(currentEndTime - collectionStartMinute) - 1);
            addHeartbeatRecords(groupNameSet, records);
            List<NewRelicMetricDataRecord> allMetricRecords = getAllMetricRecords(records);
            log.debug("fetched records: {}", allMetricRecords);
            if (!saveMetrics(dataCollectionInfo.getAccountId(), dataCollectionInfo.getApplicationId(),
                    dataCollectionInfo.getStateExecutionId(), allMetricRecords)) {
              log.error("Error saving metrics to the database. DatacollectionMin: {} StateexecutionId: {}",
                  dataCollectionMinute, dataCollectionInfo.getStateExecutionId());
            } else {
              log.info(dataCollectionInfo.getStateType() + ": Sent {} metric records to the server for minute {}",
                  allMetricRecords.size(), dataCollectionMinute);
              if (!firstDataCollectionCompleted) {
                firstDataCollectionCompleted = true;
              }
            }
            lastEndTime = currentEndTime;
            collectionStartTime += TimeUnit.MINUTES.toMillis(collectionWindow);
            if (dataCollectionMinute >= dataCollectionInfo.getDataCollectionTotalTime() - 1 || is24x7Task) {
              // We are done with all data collection, so setting task status to success and quitting.
              log.info(
                  "Completed APM collection task. So setting task status to success and quitting. StateExecutionId {}",
                  dataCollectionInfo.getStateExecutionId());
              completed.set(true);
              taskResult.setStatus(DataCollectionTaskStatus.SUCCESS);
            }
            break;

          } catch (Throwable ex) {
            if (!(ex instanceof Exception) || ++retry >= RETRIES) {
              log.error("error fetching metrics for {} for minute {} {}", dataCollectionInfo.getStateExecutionId(),
                  dataCollectionMinute, ex);
              taskResult.setStatus(DataCollectionTaskStatus.FAILURE);
              completed.set(true);
              break;
            } else {
              if (retry == 1) {
                taskResult.setErrorMessage(ExceptionUtils.getMessage(ex));
              }
              log.warn("error fetching apm metrics for minute " + dataCollectionMinute + ". retrying in "
                      + DATA_COLLECTION_RETRY_SLEEP + "s",
                  ex);
              sleep(DATA_COLLECTION_RETRY_SLEEP);
            }
          }
        }

        if (completed.get()) {
          log.debug(dataCollectionInfo.getStateType() + ": Shutting down apm data collection");
          shutDownCollection();
          return;
        }
      }
    }

    private int resolveDataCollectionMinute(long timestamp, String host, boolean isHeartbeat) {
      int collectionMinute = -1;
      if (isHeartbeat) {
        collectionMinute = dataCollectionMinute;
        if (is24x7Task) {
          collectionMinute = (int) TimeUnit.MILLISECONDS.toMinutes(dataCollectionInfo.getStartTime())
              + dataCollectionInfo.getDataCollectionTotalTime();
        }
      } else if (is24x7Task) {
        collectionMinute = (int) TimeUnit.MILLISECONDS.toMinutes(timestamp);
      } else {
        long startTimeMinForHost = isPredictiveAnalysis()
            ? collectionStartMinute - TimeUnit.MINUTES.toMillis(PREDECTIVE_HISTORY_MINUTES)
            : collectionStartMinute;
        if (hostStartMinuteMap.containsKey(host)) {
          startTimeMinForHost = hostStartMinuteMap.get(host);
        }
        collectionMinute =
            (int) ((Timestamp.minuteBoundary(timestamp) - startTimeMinForHost) / TimeUnit.MINUTES.toMillis(1));
      }

      return collectionMinute;
    }

    private void addHeartbeatRecords(
        Set<String> groupNameSet, TreeBasedTable<String, Long, NewRelicMetricDataRecord> records) {
      if (isEmpty(groupNameSet)) {
        groupNameSet = new HashSet<>(Arrays.asList(DEFAULT_GROUP_NAME));
      }
      for (String group : groupNameSet) {
        if (group == null) {
          final String errorMsg =
              "Unexpected null groupName received while sending APM Heartbeat. Please contact Harness Support.";
          log.error(errorMsg);
          throw new WingsException(errorMsg);
        }
        // Heartbeat
        int heartbeatCounter = 0;
        NewRelicMetricDataRecord heartbeat =
            NewRelicMetricDataRecord.builder()
                .stateType(getStateType())
                .name(HARNESS_HEARTBEAT_METRIC_NAME)
                .workflowId(dataCollectionInfo.getWorkflowId())
                .workflowExecutionId(dataCollectionInfo.getWorkflowExecutionId())
                .serviceId(dataCollectionInfo.getServiceId())
                .cvConfigId(dataCollectionInfo.getCvConfigId())
                .stateExecutionId(dataCollectionInfo.getStateExecutionId())
                .appId(dataCollectionInfo.getApplicationId())
                .dataCollectionMinute(resolveDataCollectionMinute(Timestamp.currentMinuteBoundary(), null, true))
                .timeStamp(collectionStartTime)
                .level(ClusterLevel.H0)
                .groupName(group)
                .build();
        log.info("adding heartbeat: {}", heartbeat);
        records.put(HARNESS_HEARTBEAT_METRIC_NAME + group, (long) heartbeatCounter++, heartbeat);
      }
    }
  }
}

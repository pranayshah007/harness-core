/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.sumo;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;
import static software.wings.delegatetasks.cv.CVConstants.DATA_COLLECTION_RETRY_SLEEP;
import static software.wings.delegatetasks.cv.CVConstants.DEFAULT_TIME_ZONE;
import static software.wings.delegatetasks.cv.CVConstants.RATE_LIMIT_STATUS;
import static software.wings.delegatetasks.cv.CVConstants.URL_STRING;

import io.harness.network.Http;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.time.Timestamp;

import software.wings.beans.SumoConfig;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse.VerificationLoadResponse;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.sumo.SumoDelegateService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sumologic.client.Credentials;
import com.sumologic.client.SumoLogicClient;
import com.sumologic.client.SumoServerException;
import com.sumologic.client.model.LogMessage;
import com.sumologic.client.searchjob.model.GetMessagesForSearchJobResponse;
import com.sumologic.client.searchjob.model.GetSearchJobStatusResponse;
import java.net.MalformedURLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;

/**
 * Delegate Service impl for Sumo Logic.
 * <p>
 * Created by sriram_parthasarathy on 9/11/17.
 */
@Singleton
@Slf4j
public class SumoDelegateServiceImpl implements SumoDelegateService {
  private static final long DEFAULT_SLEEP_TIME_IN_MILLIS = 5000;
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateLogService delegateLogService;

  @Override
  public boolean validateConfig(SumoConfig config, List<EncryptedDataDetail> encryptedDataDetails) {
    log.info("Starting config validation for SumoConfig : " + config);
    String query = "*exception*";
    String startTime = String.valueOf(Timestamp.currentMinuteBoundary() - 1);
    String endTime = String.valueOf(Timestamp.currentMinuteBoundary());
    getSumoClient(config, encryptedDataDetails, encryptionService)
        .createSearchJob(query, startTime, endTime, TimeZone.getDefault().getID());
    log.info("Valid config provided");
    return true;
  }

  @Override
  public List<LogElement> getLogSample(
      SumoConfig config, String index, List<EncryptedDataDetail> encryptedDataDetails, int duration) {
    log.info("Starting to fetch sample log data for Sumo with config : " + config);
    long startTime = Timestamp.currentMinuteBoundary() - TimeUnit.MINUTES.toMillis(duration);
    long endTime = Timestamp.currentMinuteBoundary();
    String query = "*exception*";
    return getResponse(SumoDataCollectionInfo.builder()
                           .sumoConfig(config)
                           .query(query)
                           .encryptedDataDetails(encryptedDataDetails)
                           .build(),
        "5m", null, startTime, endTime, false, 5, 0, null);
  }

  @Override
  public VerificationNodeDataSetupResponse getLogDataByHost(String accountId, SumoConfig config, String query,
      String hostNameField, String hostName, List<EncryptedDataDetail> encryptedDataDetails,
      ThirdPartyApiCallLog apiCallLog) {
    log.info("Starting to fetch test log data by host for sumo logic");
    if (apiCallLog == null) {
      apiCallLog = createApiCallLog(accountId, null);
    }

    long startTime = Timestamp.currentMinuteBoundary() - TimeUnit.MINUTES.toMillis(5);
    long endTime = Timestamp.currentMinuteBoundary();

    List<LogElement> responseWithoutHost = getResponse(SumoDataCollectionInfo.builder()
                                                           .sumoConfig(config)
                                                           .query(query)
                                                           .encryptedDataDetails(encryptedDataDetails)
                                                           .build(),
        "1m", null, startTime, endTime, true, 5, 0, apiCallLog.copy());

    if (isEmpty(responseWithoutHost) || isEmpty(hostName)) {
      return VerificationNodeDataSetupResponse.builder()
          .providerReachable(true)
          .loadResponse(VerificationLoadResponse.builder()
                            .isLoadPresent(!isEmpty(responseWithoutHost))
                            .loadResponse(responseWithoutHost)
                            .build())
          .build();
    } else {
      List<LogElement> responseWithHost = getResponse(SumoDataCollectionInfo.builder()
                                                          .sumoConfig(config)
                                                          .query(query)
                                                          .encryptedDataDetails(encryptedDataDetails)
                                                          .hostnameField(hostNameField)
                                                          .build(),
          "1m", hostName, startTime, endTime, false, 5, 0, apiCallLog.copy());
      return VerificationNodeDataSetupResponse.builder()
          .providerReachable(true)
          .loadResponse(VerificationLoadResponse.builder()
                            .loadResponse(responseWithoutHost)
                            .isLoadPresent(!responseWithoutHost.isEmpty())
                            .build())
          .dataForNode(responseWithHost)
          .build();
    }
  }

  public List<LogElement> getResponse(SumoDataCollectionInfo dataCollectionInfo, String timeSlice, String hostName,
      long startTime, long endTime, boolean is247Task, int maxMessageCount, int logCollectionMinute,
      ThirdPartyApiCallLog apiCallLog) {
    String searchQuery = dataCollectionInfo.getQuery();
    if (!isEmpty(dataCollectionInfo.getHostnameField())) {
      searchQuery = searchQuery + "| where " + dataCollectionInfo.getHostnameField() + " = \"" + hostName + "\" ";
    }
    if (!isEmpty(timeSlice)) {
      searchQuery = searchQuery + " | timeslice " + timeSlice;
    }

    log.info("Search query to be used in sumoLogic : " + searchQuery);

    Long requestTimeStamp = 0L;
    try {
      // Gets a sumologic client
      SumoLogicClient sumoClient = getSumoClient(
          dataCollectionInfo.getSumoConfig(), dataCollectionInfo.getEncryptedDataDetails(), encryptionService);

      requestTimeStamp = OffsetDateTime.now().toInstant().toEpochMilli();

      // Creates a search job based on given query
      String searchJobId = sumoClient.createSearchJob(
          searchQuery, String.valueOf(startTime), String.valueOf(endTime), DEFAULT_TIME_ZONE);
      GetSearchJobStatusResponse searchJobStatusResponse = getSearchJobStatusResponse(sumoClient, searchJobId);

      Long responseTimeStamp = OffsetDateTime.now().toInstant().toEpochMilli();

      // SaveLogs is called for given job response.
      saveThirdPartyCallLogs(apiCallLog, dataCollectionInfo.getSumoConfig(), searchQuery, String.valueOf(startTime),
          String.valueOf(endTime), searchJobStatusResponse, requestTimeStamp, responseTimeStamp, HttpStatus.SC_OK,
          FieldType.JSON);

      // SumoLogic may end up resulting lot of records that we may not need, so we have a maxMessageCount limit defined.
      // based on the max count. MessageCount value is set.
      int messageCount = searchJobStatusResponse.getMessageCount() > maxMessageCount
          ? maxMessageCount
          : searchJobStatusResponse.getMessageCount();
      log.info("Search job Status Response received from SumoLogic with message Count : "
          + searchJobStatusResponse.getMessageCount());
      return getMessagesForSearchJob(dataCollectionInfo, messageCount, sumoClient, searchJobId, 0, 0,
          Math.min(messageCount, 5), logCollectionMinute, is247Task);
    } catch (SumoServerException sumoServerException) {
      saveThirdPartyCallLogs(apiCallLog.copy(), dataCollectionInfo.getSumoConfig(), searchQuery,
          String.valueOf(startTime), String.valueOf(endTime), sumoServerException, requestTimeStamp,
          OffsetDateTime.now().toInstant().toEpochMilli(), sumoServerException.getHTTPStatus(), FieldType.TEXT);
      if (sumoServerException.getHTTPStatus() == RATE_LIMIT_STATUS) {
        int randomNum = ThreadLocalRandom.current().nextInt(1, 11);
        log.info("Encountered Rate limiting from sumo. Sleeping {} seconds for logCollectionMin {}", 30 + randomNum,
            logCollectionMinute);
        sleep(DATA_COLLECTION_RETRY_SLEEP.plus(Duration.ofSeconds(randomNum)));
      }
      throw sumoServerException;

    } catch (Exception e) {
      throw new DataCollectionException(e);
    }
  }

  /**
   * Method to get sumologic client.
   *
   * @param sumoConfig
   * @param encryptedDataDetails
   * @param encryptionService
   * @return
   */
  public static SumoLogicClient getSumoClient(
      SumoConfig sumoConfig, List<EncryptedDataDetail> encryptedDataDetails, EncryptionService encryptionService) {
    encryptionService.decrypt(sumoConfig, encryptedDataDetails, false);
    final Credentials credentials =
        new Credentials(new String(sumoConfig.getAccessId()), new String(sumoConfig.getAccessKey()));
    SumoLogicClient sumoLogicClient = new SumoLogicClient(credentials);
    HttpHost httpProxyHost = Http.getHttpProxyHost(sumoConfig.getSumoUrl());
    if (httpProxyHost != null) {
      sumoLogicClient.setProxyHost(httpProxyHost.getHostName());
      sumoLogicClient.setProxyPort(httpProxyHost.getPort());
      sumoLogicClient.setProxyProtocol(httpProxyHost.getSchemeName());
    }
    try {
      sumoLogicClient.setURL(sumoConfig.getSumoUrl());
    } catch (MalformedURLException e) {
      throw new DataCollectionException(e);
    }
    return sumoLogicClient;
  }

  /**
   * Method gets the actual messages for given query and returns List of LogElements.
   */
  private List<LogElement> getMessagesForSearchJob(SumoDataCollectionInfo dataCollectionInfo, int messageCount,
      SumoLogicClient sumoClient, String searchJobId, int clusterLabel, int messageOffset, int messageLength,
      int logCollectionMinute, boolean is247Task) {
    List<LogElement> logElements = new ArrayList<>();
    if (messageCount > 0) {
      do {
        GetMessagesForSearchJobResponse getMessagesForSearchJobResponse =
            sumoClient.getMessagesForSearchJob(searchJobId, messageOffset, messageLength);
        for (LogMessage logMessage : getMessagesForSearchJobResponse.getMessages()) {
          final LogElement sumoLogElement = new LogElement();
          sumoLogElement.setQuery(dataCollectionInfo.getQuery());
          sumoLogElement.setClusterLabel(String.valueOf(clusterLabel++));
          sumoLogElement.setCount(1);
          sumoLogElement.setLogMessage(logMessage.getProperties().get("_raw"));
          sumoLogElement.setTimeStamp(Long.parseLong(logMessage.getProperties().get("_timeslice")));
          sumoLogElement.setLogCollectionMinute(
              is247Task ? (int) TimeUnit.MILLISECONDS.toMinutes(sumoLogElement.getTimeStamp()) : logCollectionMinute);
          sumoLogElement.setHost(parseHostName(is247Task, dataCollectionInfo, logMessage));
          logElements.add(sumoLogElement);
        }
        messageCount -= messageLength;
        messageOffset += messageLength;
        messageLength = Math.min(messageCount, 1000);
      } while (messageCount > 0);
    }
    return logElements;
  }

  private String parseHostName(boolean is247Task, SumoDataCollectionInfo dataCollectionInfo, LogMessage logMessage) {
    if (is247Task) {
      return logMessage.getSourceHost();
    }

    Preconditions.checkState(logMessage.getFieldNames().contains(dataCollectionInfo.getHostnameField()),
        "No field " + dataCollectionInfo.getHostnameField()
            + " found in the log message. The fields found in the response are " + logMessage.getFieldNames());

    return logMessage.stringField(dataCollectionInfo.getHostnameField());
  }

  /**
   * Method gets the Search job status response once the state is "DONE GATHERING RESULTS" | "CANCELLED".
   *
   * @param sumoClient
   * @param searchJobId
   * @return
   */
  private GetSearchJobStatusResponse getSearchJobStatusResponse(SumoLogicClient sumoClient, String searchJobId)
      throws InterruptedException {
    GetSearchJobStatusResponse searchJobStatusResponse = null;
    // We will loop until the search job status
    // is either "DONE GATHERING RESULTS" or "CANCELLED".
    while (searchJobStatusResponse == null
        || (!searchJobStatusResponse.getState().equals("DONE GATHERING RESULTS")
            && !searchJobStatusResponse.getState().equals("CANCELLED"))) {
      Thread.sleep(DEFAULT_SLEEP_TIME_IN_MILLIS);

      // Get the latest search job status.
      searchJobStatusResponse = sumoClient.getSearchJobStatus(searchJobId);
      log.info("Waiting on search job ID: " + searchJobId + " status: " + searchJobStatusResponse.getState());
    }
    if (searchJobStatusResponse.getState().equals("CANCELLED")) {
      throw new CancellationException("The job was cancelled by sumoLogic");
    }
    return searchJobStatusResponse;
  }

  /**
   * Method to save third party call logs for given searchJobStatusResponse.
   *
   * @param apiCallLog
   * @param config
   * @param query
   * @param collectionStartTime
   * @param collectionEndTime
   * @param searchJobStatusResponse
   */
  public void saveThirdPartyCallLogs(ThirdPartyApiCallLog apiCallLog, SumoConfig config, String query,
      String collectionStartTime, String collectionEndTime, Object searchJobStatusResponse, Long requestTimeStamp,
      Long responseTimeStamp, int httpStatus, FieldType responseFieldType) {
    if (apiCallLog == null) {
      apiCallLog = createApiCallLog(config.getAccountId(), null);
    }
    apiCallLog.setTitle("Fetch request to " + config.getSumoUrl());
    apiCallLog.addFieldToRequest(
        ThirdPartyApiCallField.builder().name(URL_STRING).value(config.getSumoUrl()).type(FieldType.URL).build());
    apiCallLog.addFieldToRequest(
        ThirdPartyApiCallField.builder().name("Search Query").value(query).type(FieldType.TEXT).build());
    apiCallLog.addFieldToRequest(ThirdPartyApiCallField.builder()
                                     .name("Start Time")
                                     .value(collectionStartTime)
                                     .type(FieldType.TIMESTAMP)
                                     .build());
    apiCallLog.addFieldToRequest(
        ThirdPartyApiCallField.builder().name("End Time").value(collectionEndTime).type(FieldType.TIMESTAMP).build());

    apiCallLog.setRequestTimeStamp(requestTimeStamp);

    apiCallLog.setResponseTimeStamp(responseTimeStamp);

    apiCallLog.addFieldToResponse(httpStatus, searchJobStatusResponse, responseFieldType);
    delegateLogService.save(config.getAccountId(), apiCallLog);
  }
}

/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.bugsnag;

import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;
import static software.wings.delegatetasks.cv.CVConstants.URL_STRING;

import io.harness.exception.WingsException;
import io.harness.network.Http;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.BugsnagConfig;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.delegatetasks.CustomDataCollectionUtils;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.apm.APMRestClient;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.collect.HashBiMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Created by Praveen
 */
@Singleton
@Slf4j
public class BugsnagDelegateServiceImpl implements BugsnagDelegateService {
  private static final String organizationsUrl = "user/organizations";
  private static final String projectsUrl = "organizations/orgId/projects";
  private static final String FETCH_EVENTS_URL =
      "projects/:projectId:/events?filters[event.since][][value]=${iso_start_time}&filters[event.before][][value]=${iso_end_time}&full_reports=true&per_page=1";
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateLogService delegateLogService;

  @Override
  public Set<BugsnagApplication> getOrganizations(
      BugsnagConfig config, List<EncryptedDataDetail> encryptedDataDetails, ThirdPartyApiCallLog apiCallLog) {
    try {
      Map<String, Object> hMap = HashBiMap.create();
      APMRestClient client = getAPMRestClient(config, encryptedDataDetails);
      for (Map.Entry<String, String> entry : config.headersMap().entrySet()) {
        hMap.put(entry.getKey(), entry.getValue());
      }
      Call<Object> request = client.collect(organizationsUrl, hMap, HashBiMap.create());
      Response<Object> response = request.execute();
      List<Map<String, String>> orgs = (List<Map<String, String>>) response.body();
      List<BugsnagApplication> returnList = new ArrayList<>();
      for (Map<String, String> org : orgs) {
        returnList.add(BugsnagApplication.builder().name(org.get("name")).id(org.get("id")).build());
      }
      Set<BugsnagApplication> sorted = new TreeSet<>();
      sorted.addAll(returnList);
      return sorted;
    } catch (Exception ex) {
      log.error("Exception while fetching organizations from bugsnag");
    }
    return null;
  }
  @Override
  public Set<BugsnagApplication> getProjects(BugsnagConfig config, String orgId,
      List<EncryptedDataDetail> encryptedDataDetails, ThirdPartyApiCallLog apiCallLog) {
    try {
      Map<String, Object> hMap = HashBiMap.create();
      APMRestClient client = getAPMRestClient(config, encryptedDataDetails);
      for (Map.Entry<String, String> entry : config.headersMap().entrySet()) {
        hMap.put(entry.getKey(), entry.getValue());
      }
      String url = projectsUrl.replaceAll("orgId", orgId);
      Call<Object> request = client.collect(url, hMap, HashBiMap.create());
      Response<Object> response = request.execute();
      List<Map<String, String>> projects = (List<Map<String, String>>) response.body();
      List<BugsnagApplication> returnList = new ArrayList<>();
      for (Map<String, String> org : projects) {
        returnList.add(BugsnagApplication.builder().name(org.get("name")).id(org.get("id")).build());
      }
      Set<BugsnagApplication> sorted = new TreeSet<>();
      sorted.addAll(returnList);
      return sorted;
    } catch (Exception ex) {
      log.error("Exception while fetching projects from bugsnag");
    }
    return null;
  }

  @Override
  public Object search(BugsnagConfig config, String accountId, BugsnagSetupTestData bugsnagSetupTestData,
      List<EncryptedDataDetail> encryptedDataDetails, ThirdPartyApiCallLog apiCallLog) throws IOException {
    if (apiCallLog == null) {
      apiCallLog = createApiCallLog(accountId, null);
    }
    try {
      Map<String, Object> hMap = HashBiMap.create();
      APMRestClient client = getAPMRestClient(config, encryptedDataDetails);
      for (Map.Entry<String, String> entry : config.headersMap().entrySet()) {
        hMap.put(entry.getKey(), entry.getValue());
      }

      String url = FETCH_EVENTS_URL.replaceAll(":projectId:", bugsnagSetupTestData.getProjectId());
      String resolvedUrl = CustomDataCollectionUtils.resolvedUrl(
          url, null, bugsnagSetupTestData.getFromTime(), bugsnagSetupTestData.getToTime(), null);

      Call<Object> request = client.collect(resolvedUrl, hMap, HashBiMap.create());
      apiCallLog.setTitle("Fetch request to " + resolvedUrl);
      apiCallLog.addFieldToRequest(ThirdPartyApiCallField.builder()
                                       .name(URL_STRING)
                                       .value(request.request().url().toString())
                                       .type(FieldType.URL)
                                       .build());
      apiCallLog.setRequestTimeStamp(OffsetDateTime.now().toInstant().toEpochMilli());
      Response<Object> response = request.execute();
      if (response.isSuccessful()) {
        apiCallLog.addFieldToResponse(response.code(), response.body(), FieldType.JSON);
        delegateLogService.save(accountId, apiCallLog);
        return response.body();
      } else {
        apiCallLog.addFieldToResponse(response.code(), response.errorBody(), FieldType.TEXT);
        delegateLogService.save(accountId, apiCallLog);
        throw new WingsException("Exception while fetching logs from provider. Error code " + response.code());
      }
    } catch (Exception ex) {
      throw new WingsException(ex.getMessage());
    }
  }

  private APMRestClient getAPMRestClient(final BugsnagConfig config, List<EncryptedDataDetail> encryptedDataDetails) {
    encryptionService.decrypt(config, encryptedDataDetails, false);
    final Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(config.getUrl())
            .addConverterFactory(JacksonConverterFactory.create())
            .client(
                Http.getOkHttpClientWithNoProxyValueSet(config.getUrl()).connectTimeout(30, TimeUnit.SECONDS).build())
            .build();
    return retrofit.create(APMRestClient.class);
  }
}

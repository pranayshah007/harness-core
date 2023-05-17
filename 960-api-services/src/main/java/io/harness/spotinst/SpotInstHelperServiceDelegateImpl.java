/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.spotinst;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.network.Http.getOkHttpClientBuilder;
import static io.harness.spotinst.model.SpotInstConstants.SPOTINST_REST_TIMEOUT_MINUTES;
import static io.harness.spotinst.model.SpotInstConstants.listElastiGroupsQueryTime;
import static io.harness.spotinst.model.SpotInstConstants.spotInstBaseUrl;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;

import io.harness.exception.WingsException;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupInstanceHealth;
import io.harness.spotinst.model.SpotInstConstants;
import io.harness.spotinst.model.SpotInstListElastiGroupInstancesHealthResponse;
import io.harness.spotinst.model.SpotInstListElastiGroupsResponse;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@Slf4j
public class SpotInstHelperServiceDelegateImpl implements SpotInstHelperServiceDelegate {
  private SpotInstRestClient getSpotInstRestClient() {
    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(spotInstBaseUrl)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .client(getOkHttpClientBuilder()
                                        .readTimeout(SPOTINST_REST_TIMEOUT_MINUTES, MINUTES)
                                        .connectTimeout(SPOTINST_REST_TIMEOUT_MINUTES, MINUTES)
                                        .build())
                            .build();
    return retrofit.create(SpotInstRestClient.class);
  }

  public static <T> T executeRestCall(Call<T> restRequest) {
    RetryPolicy<Response<T>> retryPolicy =
        new RetryPolicy<Response<T>>()
            .withBackoff(1, 20, ChronoUnit.SECONDS)
            .withMaxAttempts(5)
            .handle(IOException.class)
            .onRetry(e -> log.warn("Failure #{}. Retrying. Exception {}", e.getAttemptCount(), e.getLastFailure()))
            .onRetriesExceeded(e -> log.warn("Failed to connect. Max retries exceeded"));

    try {
      Response<T> response = Failsafe.with(retryPolicy).get(() -> restRequest.clone().execute());
      if (!response.isSuccessful()) {
        String error = null;
        try (ResponseBody responseBody = response.errorBody()) {
          if (null != responseBody) {
            error = responseBody.string();
          }
        }
        throw SpotInstErrorHandler.generateException(error);
      }
      return response.body();
    } catch (FailsafeException | IOException ex) {
      throw new WingsException("Failsafe failure", ex.getCause());
    }
  }

  private String getAuthToken(String spotInstToken) {
    return format("Bearer %s", spotInstToken);
  }

  @Override
  public List<ElastiGroup> listAllElstiGroups(String spotInstToken, String spotInstAccountId) throws Exception {
    String auth = getAuthToken(spotInstToken);
    long max = System.currentTimeMillis();
    long min = max - DAYS.toMillis(listElastiGroupsQueryTime);
    SpotInstListElastiGroupsResponse response =
        executeRestCall(getSpotInstRestClient().listAllElastiGroups(auth, min, max, spotInstAccountId));
    return response.getResponse().getItems();
  }

  private Map<String, Object> convertRawJsonToMap(String jsonToConvert) {
    Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
    Gson gson = new Gson();
    return gson.fromJson(jsonToConvert, mapType);
  }

  @Override
  public Optional<ElastiGroup> getElastiGroupByName(
      String spotInstToken, String spotInstAccountId, String elastiGroupName) throws Exception {
    List<ElastiGroup> items = listAllElstiGroups(spotInstToken, spotInstAccountId);
    if (isEmpty(items)) {
      return empty();
    }
    return items.stream().filter(group -> elastiGroupName.equals(group.getName())).findFirst();
  }

  @Override
  public String getElastigroupJson(String spotInstToken, String spotInstAccountId, String elastiGroupId)
      throws Exception {
    String auth = getAuthToken(spotInstToken);
    Object body = executeRestCall(getSpotInstRestClient().getElastigroupJson(auth, elastiGroupId, spotInstAccountId));
    Object groupMap = ((Map<String, Object>) (((Map<String, Object>) body).get("response"))).get("items");
    Gson gson = new Gson();
    String json = gson.toJson(groupMap);
    json = json.substring(1, json.length() - 2);
    json = format("{\"group\":%s}}", json);
    return json;
  }

  @Override
  public Optional<ElastiGroup> getElastiGroupById(String spotInstToken, String spotInstAccountId, String elastiGroupId)
      throws Exception {
    String auth = getAuthToken(spotInstToken);
    SpotInstListElastiGroupsResponse spotInstListElastiGroupsResponse =
        executeRestCall(getSpotInstRestClient().listElastiGroup(auth, elastiGroupId, spotInstAccountId));
    List<ElastiGroup> items = spotInstListElastiGroupsResponse.getResponse().getItems();
    return isEmpty(items) ? empty() : of(items.get(0));
  }

  @Override
  public List<ElastiGroup> listAllElastiGroups(
      String spotInstToken, String spotInstAccountId, String elastiGroupNamePrefix) throws Exception {
    List<ElastiGroup> items = listAllElstiGroups(spotInstToken, spotInstAccountId);
    if (isEmpty(items)) {
      return emptyList();
    }
    String prefix = format("%s__", elastiGroupNamePrefix);
    return items.stream()
        .filter(item -> {
          String name = item.getName();
          if (!name.startsWith(prefix)) {
            return false;
          }
          String temp = name.substring(prefix.length());
          return temp.matches("[0-9]+");
        })
        .sorted(Comparator.comparingInt(g -> Integer.parseInt(g.getName().substring(prefix.length()))))
        .collect(toList());
  }

  @Override
  public void updateElastiGroup(String spotInstToken, String spotInstAccountId, String elastiGroupId, Object group)
      throws Exception {
    String auth = getAuthToken(spotInstToken);
    Map<String, Object> groupMap = new HashMap<>();
    groupMap.put(SpotInstConstants.GROUP_CONFIG_ELEMENT, group);

    executeRestCall(getSpotInstRestClient().updateElastiGroup(auth, elastiGroupId, spotInstAccountId, groupMap));
  }

  @Override
  public void updateElastiGroupCapacity(
      String spotInstToken, String spotInstAccountId, String elastiGroupId, ElastiGroup group) throws Exception {
    String auth = getAuthToken(spotInstToken);
    Map<String, Object> groupCapacityMap = new HashMap<>();
    groupCapacityMap.put(SpotInstConstants.CAPACITY, group.getCapacity());

    executeRestCall(
        getSpotInstRestClient().updateElastiGroupCapacity(auth, elastiGroupId, spotInstAccountId, groupCapacityMap));
  }

  @Override
  public void updateElastiGroup(
      String spotInstToken, String spotInstAccountId, String elastiGroupId, String jsonPayload) throws Exception {
    String auth = getAuthToken(spotInstToken);
    executeRestCall(getSpotInstRestClient().updateElastiGroup(
        auth, elastiGroupId, spotInstAccountId, convertRawJsonToMap(jsonPayload)));
  }

  @Override
  public ElastiGroup createElastiGroup(String spotInstToken, String spotInstAccountId, String jsonPayload)
      throws Exception {
    String auth = getAuthToken(spotInstToken);
    SpotInstListElastiGroupsResponse spotInstListElastiGroupsResponse = executeRestCall(
        getSpotInstRestClient().createElastiGroup(auth, spotInstAccountId, convertRawJsonToMap(jsonPayload)));
    return spotInstListElastiGroupsResponse.getResponse().getItems().get(0);
  }

  @Override
  public void deleteElastiGroup(String spotInstToken, String spotInstAccountId, String elastiGroupId) throws Exception {
    String auth = getAuthToken(spotInstToken);
    executeRestCall(getSpotInstRestClient().deleteElastiGroup(auth, elastiGroupId, spotInstAccountId));
  }

  @Override
  public void scaleUpElastiGroup(String spotInstToken, String spotInstAccountId, String elastiGroupId, int adjustment)
      throws Exception {
    String auth = getAuthToken(spotInstToken);
    executeRestCall(getSpotInstRestClient().scaleUpElastiGroup(auth, spotInstAccountId, elastiGroupId, adjustment));
  }

  @Override
  public void scaleDownElastiGroup(String spotInstToken, String spotInstAccountId, String elastiGroupId, int adjustment)
      throws Exception {
    String auth = getAuthToken(spotInstToken);
    executeRestCall(getSpotInstRestClient().scaleDownElastiGroup(auth, spotInstAccountId, elastiGroupId, adjustment));
  }

  @Override
  public List<ElastiGroupInstanceHealth> listElastiGroupInstancesHealth(
      String spotInstToken, String spotInstAccountId, String elastiGroupId) throws Exception {
    String auth = getAuthToken(spotInstToken);
    SpotInstListElastiGroupInstancesHealthResponse response =
        executeRestCall(getSpotInstRestClient().listElastiGroupInstancesHealth(auth, elastiGroupId, spotInstAccountId));
    return response.getResponse().getItems();
  }
}

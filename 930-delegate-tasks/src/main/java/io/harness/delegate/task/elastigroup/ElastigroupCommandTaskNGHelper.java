/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialType;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.spotinst.SpotInstRestClient;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupInstanceHealth;
import io.harness.spotinst.model.SpotInstConstants;
import io.harness.spotinst.model.SpotInstListElastiGroupInstancesHealthResponse;
import io.harness.spotinst.model.SpotInstListElastiGroupsResponse;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.network.Http.getOkHttpClientBuilder;
import static io.harness.spotinst.model.SpotInstConstants.CAPACITY;
import static io.harness.spotinst.model.SpotInstConstants.CAPACITY_MAXIMUM_CONFIG_ELEMENT;
import static io.harness.spotinst.model.SpotInstConstants.CAPACITY_MINIMUM_CONFIG_ELEMENT;
import static io.harness.spotinst.model.SpotInstConstants.CAPACITY_TARGET_CONFIG_ELEMENT;
import static io.harness.spotinst.model.SpotInstConstants.CAPACITY_UNIT_CONFIG_ELEMENT;
import static io.harness.spotinst.model.SpotInstConstants.COMPUTE;
import static io.harness.spotinst.model.SpotInstConstants.ELASTI_GROUP_CREATED_AT;
import static io.harness.spotinst.model.SpotInstConstants.ELASTI_GROUP_ID;
import static io.harness.spotinst.model.SpotInstConstants.ELASTI_GROUP_IMAGE_CONFIG;
import static io.harness.spotinst.model.SpotInstConstants.ELASTI_GROUP_UPDATED_AT;
import static io.harness.spotinst.model.SpotInstConstants.ELASTI_GROUP_USER_DATA_CONFIG;
import static io.harness.spotinst.model.SpotInstConstants.GROUP_CONFIG_ELEMENT;
import static io.harness.spotinst.model.SpotInstConstants.LAUNCH_SPECIFICATION;
import static io.harness.spotinst.model.SpotInstConstants.NAME_CONFIG_ELEMENT;
import static io.harness.spotinst.model.SpotInstConstants.SPOTINST_REST_TIMEOUT_MINUTES;
import static io.harness.spotinst.model.SpotInstConstants.UNIT_INSTANCE;
import static io.harness.spotinst.model.SpotInstConstants.listElastiGroupsQueryTime;
import static io.harness.spotinst.model.SpotInstConstants.spotInstBaseUrl;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class ElastigroupCommandTaskNGHelper {

  @Inject private SecretDecryptionService secretDecryptionService;

  public String generateFinalJson(ElastigroupSetupCommandRequest elastigroupSetupCommandRequest, String newElastiGroupName) {
    Map<String, Object> jsonConfigMap = getJsonConfigMapFromElastigroupJson(elastigroupSetupCommandRequest.getElastiGroupJson());
    Map<String, Object> elastiGroupConfigMap = (Map<String, Object>) jsonConfigMap.get(GROUP_CONFIG_ELEMENT);

    removeUnsupportedFieldsForCreatingNewGroup(elastiGroupConfigMap);
    updateName(elastiGroupConfigMap, newElastiGroupName);
    updateInitialCapacity(elastiGroupConfigMap);
    updateWithImageConfig(elastiGroupConfigMap,
            elastigroupSetupCommandRequest.getImage(), elastigroupSetupCommandRequest.getStartupScript(), elastigroupSetupCommandRequest.isBlueGreen());
    Gson gson = new Gson();
    return gson.toJson(jsonConfigMap);
  }

  private void updateWithImageConfig(Map<String, Object> elastiGroupConfigMap, String image, String userData, boolean blueGreen) {
    Map<String, Object> computeConfigMap = (Map<String, Object>) elastiGroupConfigMap.get(COMPUTE);
    Map<String, Object> launchSpecificationMap = (Map<String, Object>) computeConfigMap.get(LAUNCH_SPECIFICATION);

    launchSpecificationMap.put(ELASTI_GROUP_IMAGE_CONFIG, image);
    if (isNotEmpty(userData)) {
      launchSpecificationMap.put(ELASTI_GROUP_USER_DATA_CONFIG, userData);
    }
  }

  void updateInitialCapacity(Map<String, Object> elastiGroupConfigMap) {
    Map<String, Object> capacityConfig = (Map<String, Object>) elastiGroupConfigMap.get(CAPACITY);

    capacityConfig.put(CAPACITY_MINIMUM_CONFIG_ELEMENT, 0);
    capacityConfig.put(CAPACITY_TARGET_CONFIG_ELEMENT, 0);
    capacityConfig.put(CAPACITY_MAXIMUM_CONFIG_ELEMENT, 0);

    if (!capacityConfig.containsKey(CAPACITY_UNIT_CONFIG_ELEMENT)) {
      capacityConfig.put(CAPACITY_UNIT_CONFIG_ELEMENT, UNIT_INSTANCE);
    }
  }

  Map<String, Object> getJsonConfigMapFromElastigroupJson(String elastigroupJson) {
    Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
    Gson gson = new Gson();

    // Map<"group": {...entire config...}>, this is elastiGroupConfig json that spotinst exposes
    return gson.fromJson(elastigroupJson, mapType);
  }

  void removeUnsupportedFieldsForCreatingNewGroup(Map<String, Object> elastiGroupConfigMap) {
    if (elastiGroupConfigMap.containsKey(ELASTI_GROUP_ID)) {
      elastiGroupConfigMap.remove(ELASTI_GROUP_ID);
    }

    if (elastiGroupConfigMap.containsKey(ELASTI_GROUP_CREATED_AT)) {
      elastiGroupConfigMap.remove(ELASTI_GROUP_CREATED_AT);
    }

    if (elastiGroupConfigMap.containsKey(ELASTI_GROUP_UPDATED_AT)) {
      elastiGroupConfigMap.remove(ELASTI_GROUP_UPDATED_AT);
    }
  }

  void updateName(Map<String, Object> elastiGroupConfigMap, String stageElastiGroupName) {
    elastiGroupConfigMap.put(NAME_CONFIG_ELEMENT, stageElastiGroupName);
  }

  Map<String, Object> getGroup(String stageElastiGroupName) {
    Map<String, Object>  groupConfig = new HashMap<>();
    groupConfig.put(NAME_CONFIG_ELEMENT, stageElastiGroupName);

    Map<String, Object> capacityConfig = new HashMap<>();
    capacityConfig.put(CAPACITY_MINIMUM_CONFIG_ELEMENT, 0);
    capacityConfig.put(CAPACITY_TARGET_CONFIG_ELEMENT, 0);
    capacityConfig.put(CAPACITY_MAXIMUM_CONFIG_ELEMENT, 0);
    capacityConfig.put(CAPACITY_UNIT_CONFIG_ELEMENT, UNIT_INSTANCE);

    groupConfig.put(CAPACITY, getCapacity());
    return groupConfig;
  }

  Map<String, Object> getCapacity() {
    Map<String, Object> capacityConfig = new HashMap<>();

    capacityConfig.put(CAPACITY_MINIMUM_CONFIG_ELEMENT, 0);
    capacityConfig.put(CAPACITY_TARGET_CONFIG_ELEMENT, 0);
    capacityConfig.put(CAPACITY_MAXIMUM_CONFIG_ELEMENT, 0);
    capacityConfig.put(CAPACITY_UNIT_CONFIG_ELEMENT, UNIT_INSTANCE);
    return capacityConfig;
  }



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

  public List<ElastiGroup> listAllElstiGroups(String spotInstToken, String spotInstAccountId) throws Exception {
    String auth = getAuthToken(spotInstToken);
    long max = System.currentTimeMillis();
    long min = max - DAYS.toMillis(listElastiGroupsQueryTime);
    SpotInstListElastiGroupsResponse response =
            executeRestCall(getSpotInstRestClient().listAllElastiGroups(auth, min, max, spotInstAccountId));
    return response.getResponse().getItems();
  }

  private String getAuthToken(String spotInstToken) {
    return format("Bearer %s", spotInstToken);
  }

  private <T> T executeRestCall(Call<T> restRequest) throws Exception {
    Response<T> restResponse = restRequest.execute();
    if (!restResponse.isSuccessful()) {
      throw new WingsException(restResponse.errorBody().string(), EnumSet.of(WingsException.ReportTarget.UNIVERSAL));
    }
    return restResponse.body();
  }

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

  public void updateElastiGroup(String spotInstToken, String spotInstAccountId, String elastiGroupId, Object group)
          throws Exception {
    String auth = getAuthToken(spotInstToken);
    Map<String, Object> groupMap = new HashMap<>();
    groupMap.put(SpotInstConstants.GROUP_CONFIG_ELEMENT, group);

    executeRestCall(getSpotInstRestClient().updateElastiGroup(auth, elastiGroupId, spotInstAccountId, groupMap));
  }

  public void updateElastiGroupCapacity(
          String spotInstToken, String spotInstAccountId, String elastiGroupId, ElastiGroup group) throws Exception {
    String auth = getAuthToken(spotInstToken);
    Map<String, Object> groupCapacityMap = new HashMap<>();
    groupCapacityMap.put(SpotInstConstants.CAPACITY, group.getCapacity());

    executeRestCall(
            getSpotInstRestClient().updateElastiGroupCapacity(auth, elastiGroupId, spotInstAccountId, groupCapacityMap));
  }

  public void updateElastiGroup(
          String spotInstToken, String spotInstAccountId, String elastiGroupId, String jsonPayload) throws Exception {
    String auth = getAuthToken(spotInstToken);
    executeRestCall(getSpotInstRestClient().updateElastiGroup(
            auth, elastiGroupId, spotInstAccountId, convertRawJsonToMap(jsonPayload)));
  }

  private Map<String, Object> convertRawJsonToMap(String jsonToConvert) {
    Type mapType = new com.google.common.reflect.TypeToken<Map<String, Object>>() {}.getType();
    Gson gson = new Gson();
    return gson.fromJson(jsonToConvert, mapType);
  }

  public ElastiGroup createElastiGroup(String spotInstToken, String spotInstAccountId, String jsonPayload)
          throws Exception {
    String auth = getAuthToken(spotInstToken);
    SpotInstListElastiGroupsResponse spotInstListElastiGroupsResponse = executeRestCall(
            getSpotInstRestClient().createElastiGroup(auth, spotInstAccountId, convertRawJsonToMap(jsonPayload)));
    return spotInstListElastiGroupsResponse.getResponse().getItems().get(0);
  }


  public void deleteElastiGroup(String spotInstToken, String spotInstAccountId, String elastiGroupId) throws Exception {
    String auth = getAuthToken(spotInstToken);
    executeRestCall(getSpotInstRestClient().deleteElastiGroup(auth, elastiGroupId, spotInstAccountId));
  }

  public void scaleUpElastiGroup(String spotInstToken, String spotInstAccountId, String elastiGroupId, int adjustment)
          throws Exception {
    String auth = getAuthToken(spotInstToken);
    executeRestCall(getSpotInstRestClient().scaleUpElastiGroup(auth, spotInstAccountId, elastiGroupId, adjustment));
  }

  public void scaleDownElastiGroup(String spotInstToken, String spotInstAccountId, String elastiGroupId, int adjustment)
          throws Exception {
    String auth = getAuthToken(spotInstToken);
    executeRestCall(getSpotInstRestClient().scaleDownElastiGroup(auth, spotInstAccountId, elastiGroupId, adjustment));
  }

  public List<ElastiGroupInstanceHealth> listElastiGroupInstancesHealth(
          String spotInstToken, String spotInstAccountId, String elastiGroupId) throws Exception {
    String auth = getAuthToken(spotInstToken);
    SpotInstListElastiGroupInstancesHealthResponse response =
            executeRestCall(getSpotInstRestClient().listElastiGroupInstancesHealth(auth, elastiGroupId, spotInstAccountId));
    return response.getResponse().getItems();
  }

  public void decryptSpotInstConfig(SpotInstConfig spotInstConfig) {
    decryptSpotInstConfig(spotInstConfig.getSpotConnectorDTO(), spotInstConfig.getEncryptionDataDetails());
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(spotInstConfig.getSpotConnectorDTO(), spotInstConfig.getEncryptionDataDetails());
  }

  private void decryptSpotInstConfig(SpotConnectorDTO spotConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    if (spotConnectorDTO.getCredential().getSpotCredentialType() == SpotCredentialType.PERMANENT_TOKEN) {
      SpotPermanentTokenConfigSpecDTO spotPermanentTokenConfigSpecDTO =
              (SpotPermanentTokenConfigSpecDTO) spotConnectorDTO.getCredential().getConfig();
      secretDecryptionService.decrypt(spotPermanentTokenConfigSpecDTO, encryptedDataDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(spotPermanentTokenConfigSpecDTO, encryptedDataDetails);
    }
  }
}

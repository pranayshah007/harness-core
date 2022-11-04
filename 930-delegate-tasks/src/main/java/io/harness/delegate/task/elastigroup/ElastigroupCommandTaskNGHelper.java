/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.EcsV2Client;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialType;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.ecs.EcsTask;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.ecs.request.EcsBlueGreenCreateServiceRequest;
import io.harness.delegate.task.ecs.request.EcsBlueGreenRollbackRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.delegate.task.spotinst.request.SpotInstSetupTaskParameters;
import io.harness.exception.CommandExecutionException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.TimeoutException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serializer.YamlUtils;
import io.harness.spotinst.SpotInstRestClient;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupInstanceHealth;
import io.harness.spotinst.model.ElastiGroupLoadBalancer;
import io.harness.spotinst.model.ElastiGroupLoadBalancerConfig;
import io.harness.spotinst.model.SpotInstConstants;
import io.harness.spotinst.model.SpotInstListElastiGroupInstancesHealthResponse;
import io.harness.spotinst.model.SpotInstListElastiGroupsResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DeleteScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DeregisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.ServiceNamespace;
import software.amazon.awssdk.services.ecs.model.Container;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.CreateServiceResponse;
import software.amazon.awssdk.services.ecs.model.DeleteServiceRequest;
import software.amazon.awssdk.services.ecs.model.DeleteServiceResponse;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.DesiredStatus;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.ServiceEvent;
import software.amazon.awssdk.services.ecs.model.ServiceField;
import software.amazon.awssdk.services.ecs.model.Tag;
import software.amazon.awssdk.services.ecs.model.TagResourceRequest;
import software.amazon.awssdk.services.ecs.model.Task;
import software.amazon.awssdk.services.ecs.model.UntagResourceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Action;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ActionTypeEnum;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyListenerRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyRuleRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule;
import software.wings.beans.LogColor;

import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
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
import static io.harness.spotinst.model.SpotInstConstants.LB_TYPE_TG;
import static io.harness.spotinst.model.SpotInstConstants.LOAD_BALANCERS_CONFIG;
import static io.harness.spotinst.model.SpotInstConstants.NAME_CONFIG_ELEMENT;
import static io.harness.spotinst.model.SpotInstConstants.SPOTINST_REST_TIMEOUT_MINUTES;
import static io.harness.spotinst.model.SpotInstConstants.UNIT_INSTANCE;
import static io.harness.spotinst.model.SpotInstConstants.listElastiGroupsQueryTime;
import static io.harness.spotinst.model.SpotInstConstants.spotInstBaseUrl;
import static io.harness.threading.Morpheus.sleep;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.trim;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class ElastigroupCommandTaskNGHelper {

  @Inject private SecretDecryptionService secretDecryptionService;

  public Map<String, Object> generateFinalJson(ElastigroupSetupCommandRequest setupCommandRequest, String newElastiGroupName) {
    Map<String, Object> jsonConfigMap = getJsonConfigMapFromElastigroupJson(setupCommandRequest.getElastiGroupJson());
    Map<String, Object> elastiGroupConfigMap = new HashMap<>();
    elastiGroupConfigMap.put(GROUP_CONFIG_ELEMENT, getGroup(newElastiGroupName));
    return elastiGroupConfigMap;
  }

  Map<String, Object> getJsonConfigMapFromElastigroupJson(String elastigroupJson) {
    Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
    Gson gson = new Gson();

    // Map<"group": {...entire config...}>, this is elastiGroupConfig json that spotinst exposes
    return gson.fromJson(elastigroupJson, mapType);
  }

//  private void updateWithLoadBalancerAndImageConfig(List<LoadBalancerDetailsForBGDeployment> lbDetailList,
//                                                    Map<String, Object> elastiGroupConfigMap, String image, String userData, boolean blueGreen) {
//    Map<String, Object> computeConfigMap = (Map<String, Object>) elastiGroupConfigMap.get(COMPUTE);
//    Map<String, Object> launchSpecificationMap = (Map<String, Object>) computeConfigMap.get(LAUNCH_SPECIFICATION);
//
//    if (blueGreen) {
//      launchSpecificationMap.put(LOAD_BALANCERS_CONFIG,
//              ElastiGroupLoadBalancerConfig.builder().loadBalancers(generateLBConfigs(lbDetailList)).build());
//    }
//    launchSpecificationMap.put(ELASTI_GROUP_IMAGE_CONFIG, image);
//    if (isNotEmpty(userData)) {
//      launchSpecificationMap.put(ELASTI_GROUP_USER_DATA_CONFIG, userData);
//    }
//  }

  private List<ElastiGroupLoadBalancer> generateLBConfigs(List<LoadBalancerDetailsForBGDeployment> lbDetailList) {
    List<ElastiGroupLoadBalancer> elastiGroupLoadBalancers = new ArrayList<>();
    lbDetailList.forEach(loadBalancerdetail
            -> elastiGroupLoadBalancers.add(ElastiGroupLoadBalancer.builder()
            .arn(loadBalancerdetail.getStageTargetGroupArn())
            .name(loadBalancerdetail.getStageTargetGroupName())
            .type(LB_TYPE_TG)
            .build()));
    return elastiGroupLoadBalancers;
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

  public ElastiGroup createElastiGroup(String spotInstToken, String spotInstAccountId, Map<String, Object> payload)
          throws Exception {
    String auth = getAuthToken(spotInstToken);
    SpotInstListElastiGroupsResponse spotInstListElastiGroupsResponse = executeRestCall(
            getSpotInstRestClient().createElastiGroup(auth, spotInstAccountId, payload));
    return spotInstListElastiGroupsResponse.getResponse().getItems().get(0);
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

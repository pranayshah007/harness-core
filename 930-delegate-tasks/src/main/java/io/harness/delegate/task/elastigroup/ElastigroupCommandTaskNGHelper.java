/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup;

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

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialType;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.spotinst.SpotInstRestClient;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupInstanceHealth;
import io.harness.spotinst.model.ElastiGroupLoadBalancer;
import io.harness.spotinst.model.ElastiGroupLoadBalancerConfig;
import io.harness.spotinst.model.SpotInstConstants;
import io.harness.spotinst.model.SpotInstListElastiGroupInstancesHealthResponse;
import io.harness.spotinst.model.SpotInstListElastiGroupsResponse;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Action;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class ElastigroupCommandTaskNGHelper {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject protected ElbV2Client elbV2Client;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;

  public String generateFinalJson(
      ElastigroupSetupCommandRequest elastigroupSetupCommandRequest, String newElastiGroupName) {
    Map<String, Object> jsonConfigMap =
        getJsonConfigMapFromElastigroupJson(elastigroupSetupCommandRequest.getElastigroupJson());
    Map<String, Object> elastiGroupConfigMap = (Map<String, Object>) jsonConfigMap.get(GROUP_CONFIG_ELEMENT);

    List<LoadBalancerDetailsForBGDeployment> loadBalancerDetailsForBGDeployments =
        elastigroupSetupCommandRequest.getAwsLoadBalancerConfigs() != null
        ? elastigroupSetupCommandRequest.getAwsLoadBalancerConfigs()
        : Arrays.asList();

    removeUnsupportedFieldsForCreatingNewGroup(elastiGroupConfigMap);
    updateName(elastiGroupConfigMap, newElastiGroupName);
    updateInitialCapacity(elastiGroupConfigMap);
    updateWithLoadBalancerAndImageConfig(loadBalancerDetailsForBGDeployments, elastiGroupConfigMap,
        elastigroupSetupCommandRequest.getImage(), elastigroupSetupCommandRequest.getStartupScript(),
        elastigroupSetupCommandRequest.isBlueGreen());
    Gson gson = new Gson();
    return gson.toJson(jsonConfigMap);
  }

  public AwsInternalConfig getAwsInternalConfig(AwsConnectorDTO awsConnectorDTO, String region) {
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    awsInternalConfig.setDefaultRegion(region);
    return awsInternalConfig;
  }

  private void updateWithLoadBalancerAndImageConfig(List<LoadBalancerDetailsForBGDeployment> lbDetailList,
      Map<String, Object> elastiGroupConfigMap, String image, String userData, boolean blueGreen) {
    Map<String, Object> computeConfigMap = (Map<String, Object>) elastiGroupConfigMap.get(COMPUTE);
    Map<String, Object> launchSpecificationMap = (Map<String, Object>) computeConfigMap.get(LAUNCH_SPECIFICATION);

    if (blueGreen) {
      launchSpecificationMap.put(LOAD_BALANCERS_CONFIG,
          ElastiGroupLoadBalancerConfig.builder().loadBalancers(generateLBConfigs(lbDetailList)).build());
    }

    if (isNotEmpty(image)) {
      launchSpecificationMap.put(ELASTI_GROUP_IMAGE_CONFIG, image);
    }
    if (isNotEmpty(userData)) {
      launchSpecificationMap.put(ELASTI_GROUP_USER_DATA_CONFIG, userData);
    }
  }

  public LogCallback getLogCallback(ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName,
      boolean shouldOpenStream, CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, shouldOpenStream, commandUnitsProgress);
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
    Map<String, Object> groupConfig = new HashMap<>();
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
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        spotInstConfig.getSpotConnectorDTO(), spotInstConfig.getEncryptionDataDetails());
  }

  public void decryptAwsCredentialDTO(
      ConnectorConfigDTO connectorConfigDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    secretDecryptionService.decrypt(
        ((AwsConnectorDTO) connectorConfigDTO).getCredential().getConfig(), encryptedDataDetails);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(connectorConfigDTO, encryptedDataDetails);
  }

  private void decryptSpotInstConfig(
      SpotConnectorDTO spotConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    if (spotConnectorDTO.getCredential().getSpotCredentialType() == SpotCredentialType.PERMANENT_TOKEN) {
      SpotPermanentTokenConfigSpecDTO spotPermanentTokenConfigSpecDTO =
          (SpotPermanentTokenConfigSpecDTO) spotConnectorDTO.getCredential().getConfig();
      secretDecryptionService.decrypt(spotPermanentTokenConfigSpecDTO, encryptedDataDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(spotPermanentTokenConfigSpecDTO, encryptedDataDetails);
    }
  }

  //------------------

  private Listener getListenerByPort(String listenerPort, List<Listener> listeners, String loadBalancer) {
    if (EmptyPredicate.isNotEmpty(listeners)) {
      for (Listener listener : listeners) {
        if (isListenerPortMatching(listenerPort, listener)) {
          return listener;
        }
      }
    }
    throw new InvalidRequestException(
        "listener with port:" + listenerPort + "is not present in load balancer: " + loadBalancer);
  }

  private String getFirstTargetGroupFromListener(
      AwsInternalConfig awsInternalConfig, String region, String listenerArn, String listenerRuleArn) {
    List<Rule> rules = newArrayList();
    String nextToken = null;
    do {
      DescribeRulesRequest describeRulesRequest =
          DescribeRulesRequest.builder().listenerArn(listenerArn).marker(nextToken).pageSize(10).build();
      DescribeRulesResponse describeRulesResponse =
          elbV2Client.describeRules(awsInternalConfig, describeRulesRequest, region);
      rules.addAll(describeRulesResponse.rules());
      nextToken = describeRulesResponse.nextMarker();
    } while (nextToken != null);

    if (EmptyPredicate.isNotEmpty(rules)) {
      for (Rule rule : rules) {
        if (isListenerRuleArnMatching(listenerRuleArn, rule)) {
          return getFirstTargetGroupFromListenerRule(rule);
        }
      }
    }
    throw new InvalidRequestException(
        "listener rule with arn: " + listenerRuleArn + " is not present in listener: " + listenerArn);
  }

  private String getTargetGroupName(AwsInternalConfig awsInternalConfig, String region, String targetGroupArn) {
    List<TargetGroup> targetGroups = newArrayList();
    String nextToken = null;
    do {
      DescribeTargetGroupsRequest describeTargetGroupsRequest =
          DescribeTargetGroupsRequest.builder().targetGroupArns(targetGroupArn).marker(nextToken).pageSize(10).build();
      DescribeTargetGroupsResponse describeTargetGroupsResponse =
          elbV2Client.describeTargetGroups(awsInternalConfig, describeTargetGroupsRequest, region);
      targetGroups.addAll(describeTargetGroupsResponse.targetGroups());
      nextToken = describeTargetGroupsResponse.nextMarker();
    } while (nextToken != null);

    if (EmptyPredicate.isNotEmpty(targetGroups)) {
      for (TargetGroup targetGroup : targetGroups) {
        if (isTargetGroupArnMatching(targetGroupArn, targetGroup)) {
          return targetGroup.targetGroupName();
        }
      }
    }
    throw new InvalidRequestException("Target Group with arn: " + targetGroupArn + " is not present");
  }

  private String getFirstTargetGroupFromListenerRule(Rule rule) {
    if (EmptyPredicate.isNotEmpty(rule.actions())) {
      Action action = rule.actions().stream().findFirst().orElse(null);
      if (action == null || EmptyPredicate.isEmpty(action.targetGroupArn())) {
        throw new InvalidRequestException(
            "No action is present in listener rule:" + rule.ruleArn() + " or there is no target group attached");
      }
      return action.targetGroupArn();
    }
    throw new InvalidRequestException("No action is present in listener rule: " + rule.ruleArn());
  }

  private boolean isListenerRuleArnMatching(String listenerRuleArn, Rule rule) {
    if (EmptyPredicate.isNotEmpty(rule.ruleArn()) && listenerRuleArn.equalsIgnoreCase(rule.ruleArn())) {
      return true;
    }
    return false;
  }

  private boolean isTargetGroupArnMatching(String targetGroupArn, TargetGroup targetGroup) {
    if (EmptyPredicate.isNotEmpty(targetGroup.targetGroupArn())
        && targetGroupArn.equalsIgnoreCase(targetGroup.targetGroupArn())) {
      return true;
    }
    return false;
  }

  private boolean isListenerArnMatching(String listenerArn, Listener listener) {
    if (EmptyPredicate.isNotEmpty(listener.listenerArn()) && listenerArn.equalsIgnoreCase(listener.listenerArn())) {
      return true;
    }
    return false;
  }

  private boolean isListenerPortMatching(String listenerPort, Listener listener) {
    if (EmptyPredicate.isNotEmpty(listener.port().toString())
        && listenerPort.equalsIgnoreCase(listener.port().toString())) {
      return true;
    }
    return false;
  }

  public List<LoadBalancerDetailsForBGDeployment> fetchAllLoadBalancerDetails(
      ElastigroupSetupCommandRequest setupTaskParameters, AwsInternalConfig awsConfig, LogCallback logCallback) {
    List<LoadBalancerDetailsForBGDeployment> awsLoadBalancerConfigs = setupTaskParameters.getAwsLoadBalancerConfigs();
    List<LoadBalancerDetailsForBGDeployment> lbDetailsWithArnValues = new ArrayList<>();
    try {
      for (LoadBalancerDetailsForBGDeployment awsLoadBalancerConfig : awsLoadBalancerConfigs) {
        logCallback.saveExecutionLog(
            format("Querying aws to get the stage target group details for load balancer: [%s]",
                awsLoadBalancerConfig.getLoadBalancerName()));

        LoadBalancerDetailsForBGDeployment loadBalancerDetailsForBGDeployment =
            getListenerResponseDetails(awsConfig, setupTaskParameters.getAwsRegion(),
                awsLoadBalancerConfig.getLoadBalancerName(), logCallback, awsLoadBalancerConfig);

        lbDetailsWithArnValues.add(loadBalancerDetailsForBGDeployment);

        logCallback.saveExecutionLog(format("Using TargetGroup: [%s], ARN: [%s] with new Elastigroup",
            loadBalancerDetailsForBGDeployment.getStageTargetGroupName(),
            loadBalancerDetailsForBGDeployment.getStageTargetGroupArn()));
      }
    } catch (NumberFormatException numberFormatEx) {
      String errorMessage =
          "Unable to fetch load balancer listener details. Please verify port numbers are entered correctly.";
      throw new InvalidRequestException(errorMessage, numberFormatEx, WingsException.USER);
    } catch (InvalidRequestException e) {
      throw new InvalidRequestException("Failed while fetching TargetGroup Details", e, WingsException.USER);
    }

    return lbDetailsWithArnValues;
  }

  private LoadBalancerDetailsForBGDeployment getListenerResponseDetails(AwsInternalConfig awsConfig, String region,
      String loadBalancerName, LogCallback logCallback, LoadBalancerDetailsForBGDeployment originalDetails) {
    List<Listener> listeners = getElbListenersForLoadBalancer(originalDetails.getLoadBalancerName(), awsConfig, region);

    Listener prodListener = getListenerByPort(originalDetails.getProdListenerPort(), listeners, loadBalancerName);
    String prodTargetGroupArn = getFirstTargetGroupFromListener(
        awsConfig, region, prodListener.listenerArn(), originalDetails.getProdRuleArn());
    String prodTargetGroupName = getTargetGroupName(awsConfig, region, prodTargetGroupArn);

    Listener stageListener = getListenerByPort(originalDetails.getStageListenerPort(), listeners, loadBalancerName);
    String stageTargetGroupArn = getFirstTargetGroupFromListener(
        awsConfig, region, stageListener.listenerArn(), originalDetails.getStageRuleArn());
    String stageTargetGroupName = getTargetGroupName(awsConfig, region, stageTargetGroupArn);

    return LoadBalancerDetailsForBGDeployment.builder()
        .loadBalancerArn(prodListener.loadBalancerArn())
        .loadBalancerName(loadBalancerName)
        .prodListenerArn(prodListener.listenerArn())
        .prodTargetGroupArn(prodTargetGroupArn)
        .prodTargetGroupName(prodTargetGroupName)
        .stageListenerArn(stageListener.listenerArn())
        .stageTargetGroupArn(stageTargetGroupArn)
        .stageTargetGroupName(stageTargetGroupName)
        .prodListenerPort(Integer.toString(prodListener.port()))
        .stageListenerPort(Integer.toString(stageListener.port()))
        .useSpecificRules(originalDetails.isUseSpecificRules())
        .prodRuleArn(originalDetails.getProdRuleArn())
        .stageRuleArn(originalDetails.getStageRuleArn())
        .build();
  }

  List<Listener> getElbListenersForLoadBalancer(
      String loadBalancerName, AwsInternalConfig awsInternalConfig, String region) {
    DescribeLoadBalancersRequest describeLoadBalancersRequest =
        DescribeLoadBalancersRequest.builder().names(loadBalancerName).build();
    DescribeLoadBalancersResponse describeLoadBalancersResponse =
        elbV2Client.describeLoadBalancer(awsInternalConfig, describeLoadBalancersRequest, region);
    if (EmptyPredicate.isEmpty(describeLoadBalancersResponse.loadBalancers())) {
      throw new InvalidRequestException(
          "load balancer with name:" + loadBalancerName + "is not present in this aws account");
    }
    String loadBalancerArn = describeLoadBalancersResponse.loadBalancers().get(0).loadBalancerArn();
    List<Listener> listeners = newArrayList();
    String nextToken = null;
    do {
      DescribeListenersRequest describeListenersRequest =
          DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).marker(nextToken).pageSize(10).build();
      DescribeListenersResponse describeListenersResponse =
          elbV2Client.describeListener(awsInternalConfig, describeListenersRequest, region);
      listeners.addAll(describeListenersResponse.listeners());
      nextToken = describeLoadBalancersResponse.nextMarker();
    } while (nextToken != null);
    return listeners;
  }
}

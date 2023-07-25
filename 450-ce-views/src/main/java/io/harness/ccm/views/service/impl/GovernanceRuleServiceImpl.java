/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.views.helper.RuleCloudProviderType.AZURE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.dao.RuleDAO;
import io.harness.ccm.views.dto.GovernanceJobEnqueueDTO;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.entities.RuleEnforcement;
import io.harness.ccm.views.entities.RuleExecution;
import io.harness.ccm.views.helper.GovernanceJobDetailsAWS;
import io.harness.ccm.views.helper.GovernanceJobDetailsAzure;
import io.harness.ccm.views.helper.GovernanceRuleFilter;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.helper.RuleExecutionStatusType;
import io.harness.ccm.views.helper.RuleExecutionType;
import io.harness.ccm.views.helper.RuleList;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.ccm.views.service.RuleExecutionService;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.CcmConnectorFilter;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.faktory.FaktoryProducer;
import io.harness.filter.FilterType;
import io.harness.ng.beans.PageResponse;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.reinert.jjschema.Nullable;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;

@Slf4j
@Singleton
@OwnedBy(CE)
public class GovernanceRuleServiceImpl implements GovernanceRuleService {
  @Inject private RuleDAO ruleDAO;
  @Inject private YamlSchemaValidator yamlSchemaValidator;
  @Inject private ConnectorResourceClient connectorResourceClient;
  @Inject private RuleExecutionService ruleExecutionService;
  @Nullable @Inject @Named("governanceConfig") io.harness.remote.GovernanceConfig governanceConfig;

  @Override
  public boolean save(Rule rules) {
    return ruleDAO.save(rules);
  }

  @Override
  public boolean delete(String accountId, String uuid) {
    return ruleDAO.delete(accountId, uuid);
  }

  @Override
  public Rule update(Rule rules, String accountId) {
    return ruleDAO.update(rules, accountId);
  }

  @Override
  public RuleList list(GovernanceRuleFilter governancePolicyFilter) {
    return ruleDAO.list(governancePolicyFilter);
  }

  @Override
  public List<Rule> list(String accountId, List<String> uuid) {
    return ruleDAO.check(accountId, uuid);
  }

  @Override
  public Rule fetchByName(String accountId, String name, boolean create) {
    return ruleDAO.fetchByName(accountId, name, create);
  }

  @Override
  public Rule fetchById(String accountId, String uuid, boolean create) {
    return ruleDAO.fetchById(accountId, uuid, create);
  }

  @Override
  public void check(String accountId, List<String> rulesIdentifiers) {
    List<Rule> rules = ruleDAO.check(accountId, rulesIdentifiers);
    if (rules.size() != rulesIdentifiers.size()) {
      for (Rule it : rules) {
        log.info("{} {} ", it, it.getUuid());
        rulesIdentifiers.remove(it.getUuid());
      }
      if (!rulesIdentifiers.isEmpty()) {
        throw new InvalidRequestException("No such rules exist:" + rulesIdentifiers);
      }
    }
  }

  @Override
  public void customRuleLimit(String accountId) {
    GovernanceRuleFilter governancePolicyFilter = GovernanceRuleFilter.builder().build();
    governancePolicyFilter.setAccountId(accountId);
    governancePolicyFilter.setIsOOTB(false);
    if (list(governancePolicyFilter).getRules().size() >= 300) {
      throw new InvalidRequestException("You have exceeded the limit for rules creation");
    }
  }

  @Override
  public void custodianValidate(Rule rule) {
    try {
      String fileName = String.join("/", "/tmp", String.join("_", rule.getName(), rule.getAccountId() + ".yaml"));
      File file = new File(fileName);
      FileWriter fw = new FileWriter(file, true);
      BufferedWriter bw = new BufferedWriter(fw);
      bw.write(rule.getRulesYaml());
      bw.newLine();
      bw.close();

      log.info("rule yaml: \n{}\n", rule.getRulesYaml());
      final ArrayList<String> Validatecmd = Lists.newArrayList("custodian", "validate", fileName);
      String processResult = getProcessExecutor().command(Validatecmd).readOutput(true).execute().outputString();
      log.info("{}", processResult);

      file.delete();

      if (processResult.contains("Configuration invalid")) {
        processResult =
            processResult.substring(processResult.indexOf("Configuration invalid"), processResult.lastIndexOf('\n'));
        if (processResult.indexOf("custodian.commands:ERROR")
            != processResult.lastIndexOf("custodian.commands:ERROR")) {
          throw new InvalidRequestException(processResult.substring(
              processResult.indexOf("custodian.commands:ERROR") + 24, processResult.lastIndexOf('\n')));
        } else {
          throw new InvalidRequestException(
              processResult.substring(processResult.indexOf("custodian.commands:ERROR") + 24));
        }
      }

    } catch (IOException | InterruptedException | TimeoutException e) {
      throw new InvalidRequestException("Policy YAML is malformed");
    }
  }

  @Override
  public void validateAWSSchema(Rule rule) {
    log.info("yaml: {}", rule.getRulesYaml());
    try {
      YamlUtils.readTree(rule.getRulesYaml());
      Set<String> ValidateMsg = yamlSchemaValidator.validate(rule.getRulesYaml(), EntityType.CCM_GOVERNANCE_RULE_AWS);
      if (ValidateMsg.size() > 0) {
        log.info(ValidateMsg.toString());
      }
    } catch (IOException e) {
      throw new InvalidRequestException("Policy YAML is malformed");
    }
  }

  @Override
  public Set<ConnectorInfoDTO> getConnectorResponse(
      String accountId, Set<String> targets, RuleCloudProviderType cloudProvider) {
    if (cloudProvider == AZURE) {
      return getAzureConnectorResponse(accountId, targets);
    } else {
      return getAwsConnectorResponse(accountId, targets);
    }
  }

  public Set<ConnectorInfoDTO> getAwsConnectorResponse(String accountId, Set<String> targetAccounts) {
    Set<ConnectorInfoDTO> responseDTO = new HashSet<>();
    List<String> accounts = new ArrayList<>();
    for (String targetAccount : targetAccounts) {
      final CacheKey cacheKey = new CacheKey(accountId, targetAccount);
      ConnectorInfoDTO connectorInfoDTO = connectorCache.getIfPresent(cacheKey);
      if (connectorInfoDTO != null) {
        log.info("cache hit for key: {} value: {}", cacheKey, connectorInfoDTO);
        responseDTO.add(connectorInfoDTO);
      } else {
        accounts.add(targetAccount);
        log.info("cache miss for key: {}", cacheKey);
      }
    }
    if (!accounts.isEmpty()) {
      log.info("accounts not cached: {}", accounts);
      List<ConnectorResponseDTO> nextGenConnectorResponses = getAWSConnectorWithTargetAccounts(accounts, accountId);
      for (ConnectorResponseDTO connector : nextGenConnectorResponses) {
        ConnectorInfoDTO connectorInfo = connector.getConnector();
        CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorInfo.getConnectorConfig();
        responseDTO.add(connectorInfo);
        final CacheKey cacheKey = new CacheKey(accountId, ceAwsConnectorDTO.getAwsAccountId());
        connectorCache.put(cacheKey, connectorInfo);
      }
    }
    return responseDTO;
  }

  public Set<ConnectorInfoDTO> getAzureConnectorResponse(String accountId, Set<String> targetSubscriptions) {
    Set<ConnectorInfoDTO> responseDTO = new HashSet<>();
    List<String> subscriptions = new ArrayList<>();
    for (String targetSubscription : targetSubscriptions) {
      final CacheKey cacheKey = new CacheKey(accountId, targetSubscription);
      ConnectorInfoDTO connectorInfoDTO = connectorCache.getIfPresent(cacheKey);
      if (connectorInfoDTO != null) {
        log.info("cache hit for key: {} value: {}", cacheKey, connectorInfoDTO);
        responseDTO.add(connectorInfoDTO);
      } else {
        subscriptions.add(targetSubscription);
        log.info("cache miss for key: {}", cacheKey);
      }
    }
    if (!subscriptions.isEmpty()) {
      log.info("subscriptions not cached: {}", subscriptions);
      List<ConnectorResponseDTO> nextGenConnectorResponses =
          getAzureConnectorWithTargetSubscriptions(subscriptions, accountId);
      for (ConnectorResponseDTO connector : nextGenConnectorResponses) {
        ConnectorInfoDTO connectorInfo = connector.getConnector();
        CEAzureConnectorDTO ceAzureConnectorDTO = (CEAzureConnectorDTO) connectorInfo.getConnectorConfig();
        responseDTO.add(connectorInfo);
        final CacheKey cacheKey = new CacheKey(accountId, ceAzureConnectorDTO.getSubscriptionId());
        connectorCache.put(cacheKey, connectorInfo);
      }
    }
    return responseDTO;
  }

  @Override
  public List<ConnectorResponseDTO> getAWSConnectorWithTargetAccounts(List<String> accounts, String accountId) {
    List<ConnectorResponseDTO> nextGenConnectorResponses = new ArrayList<>();
    PageResponse<ConnectorResponseDTO> response = null;
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder()
            .types(Arrays.asList(ConnectorType.CE_AWS))
            .ccmConnectorFilter(CcmConnectorFilter.builder()
                                    .featuresEnabled(Arrays.asList(CEFeatures.GOVERNANCE))
                                    .awsAccountIds(accounts)
                                    .build())
            .build();
    connectorFilterPropertiesDTO.setFilterType(FilterType.CONNECTOR);
    int page = 0;
    int size = 1000;
    do {
      response = NGRestUtils.getResponse(connectorResourceClient.listConnectors(
          accountId, null, null, page, size, connectorFilterPropertiesDTO, false));
      if (response != null && isNotEmpty(response.getContent())) {
        nextGenConnectorResponses.addAll(response.getContent());
      }
      page++;
    } while (response != null && isNotEmpty(response.getContent()));

    return nextGenConnectorResponses;
  }

  @Override
  public List<ConnectorResponseDTO> getAzureConnectorWithTargetSubscriptions(
      List<String> subscriptions, String accountId) {
    List<ConnectorResponseDTO> nextGenConnectorResponses = new ArrayList<>();
    List<ConnectorResponseDTO> connectorsForGivenSubscriptions = new ArrayList<>();
    PageResponse<ConnectorResponseDTO> response = null;
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder()
            .types(List.of(ConnectorType.CE_AZURE))
            .ccmConnectorFilter(CcmConnectorFilter.builder().featuresEnabled(List.of(CEFeatures.GOVERNANCE)).build())
            .build();
    connectorFilterPropertiesDTO.setFilterType(FilterType.CONNECTOR);
    int page = 0;
    int size = 1000;
    do {
      response = NGRestUtils.getResponse(connectorResourceClient.listConnectors(
          accountId, null, null, page, size, connectorFilterPropertiesDTO, false));
      if (response != null && isNotEmpty(response.getContent())) {
        nextGenConnectorResponses.addAll(response.getContent());
      }
      page++;
    } while (response != null && isNotEmpty(response.getContent()));

    for (ConnectorResponseDTO connector : nextGenConnectorResponses) {
      CEAzureConnectorDTO ceAzureConnectorDTO = (CEAzureConnectorDTO) connector.getConnector().getConnectorConfig();
      if (subscriptions.contains(ceAzureConnectorDTO.getSubscriptionId())) {
        connectorsForGivenSubscriptions.add(connector);
      }
    }

    return connectorsForGivenSubscriptions;
  }

  ProcessExecutor getProcessExecutor() {
    return new ProcessExecutor();
  }

  private final Cache<CacheKey, ConnectorInfoDTO> connectorCache =
      Caffeine.newBuilder().maximumSize(2000).expireAfterWrite(15, TimeUnit.MINUTES).build();

  @Override
  public String enqueueAdhoc(String accountId, GovernanceJobEnqueueDTO governanceJobEnqueueDTO) {
    // TO DO: Refactor and make this method smaller
    // Step-1 Fetch from mongo
    String ruleEnforcementUuid = governanceJobEnqueueDTO.getRuleEnforcementId();
    String response = null;
    // Call is from UI for adhoc evaluation. Directly enqueue in this case
    // TO DO: See if UI adhoc requests can be sent to higher priority queue. This should also change in worker.
    log.info("enqueuing for ad-hoc request");
    if (isEmpty(accountId)) {
      throw new InvalidRequestException("Missing accountId");
    }
    List<Rule> rulesList = list(accountId, Arrays.asList(governanceJobEnqueueDTO.getRuleId()));
    if (rulesList == null) {
      log.error("For rule id {}: no rules exists in mongo. Nothing to enqueue", governanceJobEnqueueDTO.getRuleId());
      return null;
    }
    try {
      String jid;
      if (governanceJobEnqueueDTO.getRuleCloudProviderType() == AZURE) {
        jid = enqueueAdhocAzure(accountId, governanceJobEnqueueDTO);
      } else {
        jid = enqueueAdhocAws(accountId, governanceJobEnqueueDTO);
      }

      // Make a record in Mongo
      RuleExecution ruleExecution =
          RuleExecution.builder()
              .accountId(accountId)
              .jobId(jid)
              .cloudProvider(governanceJobEnqueueDTO.getRuleCloudProviderType())
              .executionLogPath("") // Updated by worker when execution finishes
              .isDryRun(governanceJobEnqueueDTO.getIsDryRun())
              .ruleEnforcementIdentifier(ruleEnforcementUuid)
              .executionCompletedAt(null) // Updated by worker when execution finishes
              .ruleIdentifier(governanceJobEnqueueDTO.getRuleId())
              .targetAccount(governanceJobEnqueueDTO.getTargetAccountDetails().getTargetInfo())
              .targetRegions(Arrays.asList(governanceJobEnqueueDTO.getTargetRegion()))
              .executionLogBucketType("")
              .executionType(governanceJobEnqueueDTO.getExecutionType())
              .resourceCount(0)
              .ruleName(rulesList.get(0).getName())
              .OOTB(rulesList.get(0).getIsOOTB())
              .executionStatus(RuleExecutionStatusType.ENQUEUED)
              .build();
      response = ruleExecutionService.save(ruleExecution);
    } catch (Exception e) {
      log.warn("Exception enqueueing job for ruleEnforcementUuid: {} for targetAccount: {} for targetRegions: {}, {}",
          ruleEnforcementUuid, governanceJobEnqueueDTO.getTargetAccountDetails().getTargetInfo(),
          governanceJobEnqueueDTO.getTargetRegion(), e);
    }
    return response;
  }

  private String enqueueAdhocAzure(String accountId, GovernanceJobEnqueueDTO governanceJobEnqueueDTO)
      throws IOException {
    GovernanceJobDetailsAzure governanceJobDetailsAzure =
        GovernanceJobDetailsAzure.builder()
            .accountId(accountId)
            .subscriptionId(governanceJobEnqueueDTO.getTargetAccountDetails().getTargetInfo())
            .tenantId(governanceJobEnqueueDTO.getTargetAccountDetails().getTenantInfo())
            .isDryRun(governanceJobEnqueueDTO.getIsDryRun())
            .policyId(governanceJobEnqueueDTO.getRuleId())
            .region(governanceJobEnqueueDTO.getTargetRegion())
            .policyEnforcementId("") // This is adhoc run
            .policy(governanceJobEnqueueDTO.getPolicy())
            .isOOTB(governanceJobEnqueueDTO.getIsOOTB())
            .executionType(governanceJobEnqueueDTO.getExecutionType())
            .cloudConnectorID(governanceJobEnqueueDTO.getTargetAccountDetails().getCloudConnectorId())
            .build();

    Gson gson = new GsonBuilder().create();
    String json = gson.toJson(governanceJobDetailsAzure);
    log.info("Enqueuing Azure job in Faktory {}", json);
    // jobType, jobQueue, json
    String jid = FaktoryProducer.push(
        governanceConfig.getAzureFaktoryJobType(), governanceConfig.getAzureFaktoryQueueName(), json);
    log.info("Pushed Azure job in Faktory: {}", jid);
    return jid;
  }

  private String enqueueAdhocAws(String accountId, GovernanceJobEnqueueDTO governanceJobEnqueueDTO) throws IOException {
    GovernanceJobDetailsAWS governanceJobDetailsAWS =
        GovernanceJobDetailsAWS.builder()
            .accountId(accountId)
            .awsAccountId(governanceJobEnqueueDTO.getTargetAccountDetails().getTargetInfo())
            .externalId(governanceJobEnqueueDTO.getTargetAccountDetails().getRoleId())
            .roleArn(governanceJobEnqueueDTO.getTargetAccountDetails().getRoleInfo())
            .isDryRun(governanceJobEnqueueDTO.getIsDryRun())
            .ruleId(governanceJobEnqueueDTO.getRuleId())
            .region(governanceJobEnqueueDTO.getTargetRegion())
            .ruleEnforcementId("") // This is adhoc run
            .policy(governanceJobEnqueueDTO.getPolicy())
            .isOOTB(governanceJobEnqueueDTO.getIsOOTB())
            .executionType(governanceJobEnqueueDTO.getExecutionType())
            .build();

    Gson gson = new GsonBuilder().create();
    String json = gson.toJson(governanceJobDetailsAWS);
    log.info("Enqueuing Aws job in Faktory {}", json);
    // jobType, jobQueue, json
    String jid =
        FaktoryProducer.push(governanceConfig.getAwsFaktoryJobType(), governanceConfig.getAwsFaktoryQueueName(), json);
    log.info("Pushed Aws job in Faktory: {}", jid);
    return jid;
  }

  @Override
  public List<RuleExecution> enqueue(String accountId, RuleEnforcement ruleEnforcement, List<Rule> rulesList,
      ConnectorConfigDTO connectorConfig, String cloudConnectorId, String faktoryJobType, String faktoryQueueName) {
    if (ruleEnforcement.getCloudProvider() == AZURE) {
      return enqueueAzure(
          accountId, ruleEnforcement, rulesList, connectorConfig, cloudConnectorId, faktoryJobType, faktoryQueueName);
    } else {
      return enqueueAws(accountId, ruleEnforcement, rulesList, connectorConfig, faktoryJobType, faktoryQueueName);
    }
  }

  private List<RuleExecution> enqueueAws(String accountId, RuleEnforcement ruleEnforcement, List<Rule> rulesList,
      ConnectorConfigDTO connectorConfig, String faktoryJobType, String faktoryQueueName) {
    CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorConfig;
    List<RuleExecution> ruleExecutions = new ArrayList<>();
    for (String region : ruleEnforcement.getTargetRegions()) {
      for (Rule rule : rulesList) {
        try {
          GovernanceJobDetailsAWS governanceJobDetailsAWS =
              GovernanceJobDetailsAWS.builder()
                  .accountId(accountId)
                  .awsAccountId(ceAwsConnectorDTO.getAwsAccountId())
                  .externalId(ceAwsConnectorDTO.getCrossAccountAccess().getExternalId())
                  .roleArn(ceAwsConnectorDTO.getCrossAccountAccess().getCrossAccountRoleArn())
                  .isDryRun(ruleEnforcement.getIsDryRun())
                  .ruleId(rule.getUuid())
                  .region(region)
                  .ruleEnforcementId(ruleEnforcement.getUuid())
                  .policy(rule.getRulesYaml())
                  .executionType(RuleExecutionType.EXTERNAL)
                  .build();
          Gson gson = new GsonBuilder().create();
          String json = gson.toJson(governanceJobDetailsAWS);
          log.info("For rule enforcement setting {}: Enqueuing Aws job in Faktory {}", ruleEnforcement.getUuid(), json);
          // Bulk enqueue in faktory can lead to difficulties in error handling and retry.
          // order: jobType, jobQueue, json
          String jid = FaktoryProducer.push(faktoryJobType, faktoryQueueName, json);
          log.info("For rule enforcement setting {}: Pushed Aws job in Faktory: {}", ruleEnforcement.getUuid(), jid);
          // Make a record in Mongo
          // TO DO: We can bulk insert in mongo for all successful faktory job pushes
          ruleExecutions.add(RuleExecution.builder()
                                 .accountId(accountId)
                                 .jobId(jid)
                                 .cloudProvider(ruleEnforcement.getCloudProvider())
                                 .executionLogPath("") // Updated by worker when execution finishes
                                 .isDryRun(ruleEnforcement.getIsDryRun())
                                 .ruleEnforcementIdentifier(ruleEnforcement.getUuid())
                                 .ruleEnforcementName(ruleEnforcement.getName())
                                 .executionCompletedAt(null) // Updated by worker when execution finishes
                                 .ruleIdentifier(rule.getUuid())
                                 .targetAccount(ceAwsConnectorDTO.getAwsAccountId())
                                 .targetRegions(Arrays.asList(region))
                                 .executionLogBucketType("")
                                 .ruleName(rule.getName())
                                 .OOTB(rule.getIsOOTB())
                                 .executionStatus(RuleExecutionStatusType.ENQUEUED)
                                 .executionType(RuleExecutionType.EXTERNAL)
                                 .build());
        } catch (Exception e) {
          log.warn(
              "Exception enqueueing Aws job for ruleEnforcementUuid: {} for targetAccount: {} for targetRegions: {}, {}",
              ruleEnforcement.getUuid(), ceAwsConnectorDTO.getAwsAccountId(), region, e);
        }
      }
    }
    return ruleExecutions;
  }

  private List<RuleExecution> enqueueAzure(String accountId, RuleEnforcement ruleEnforcement, List<Rule> rulesList,
      ConnectorConfigDTO connectorConfig, String cloudConnectorId, String faktoryJobType, String faktoryQueueName) {
    CEAzureConnectorDTO ceAzureConnectorDTO = (CEAzureConnectorDTO) connectorConfig;
    List<RuleExecution> ruleExecutions = new ArrayList<>();
    for (String region : ruleEnforcement.getTargetRegions()) {
      for (Rule rule : rulesList) {
        try {
          GovernanceJobDetailsAzure governanceJobDetailsAzure =
              GovernanceJobDetailsAzure.builder()
                  .accountId(accountId)
                  .subscriptionId(ceAzureConnectorDTO.getSubscriptionId())
                  .tenantId(ceAzureConnectorDTO.getTenantId())
                  .isDryRun(ruleEnforcement.getIsDryRun())
                  .policyId(rule.getUuid())
                  .region(region)
                  .policyEnforcementId("") // This is adhoc run
                  .policy(rule.getRulesYaml())
                  .executionType(RuleExecutionType.EXTERNAL)
                  .cloudConnectorID(cloudConnectorId)
                  .build();
          Gson gson = new GsonBuilder().create();
          String json = gson.toJson(governanceJobDetailsAzure);
          log.info(
              "For rule enforcement setting {}: Enqueuing Azure job in Faktory {}", ruleEnforcement.getUuid(), json);
          // Bulk enqueue in faktory can lead to difficulties in error handling and retry.
          // order: jobType, jobQueue, json
          String jid = FaktoryProducer.push(faktoryJobType, faktoryQueueName, json);
          log.info("For rule enforcement setting {}: Pushed Azure job in Faktory: {}", ruleEnforcement.getUuid(), jid);
          // Make a record in Mongo
          // TO DO: We can bulk insert in mongo for all successful faktory job pushes
          ruleExecutions.add(RuleExecution.builder()
                                 .accountId(accountId)
                                 .jobId(jid)
                                 .cloudProvider(ruleEnforcement.getCloudProvider())
                                 .executionLogPath("") // Updated by worker when execution finishes
                                 .isDryRun(ruleEnforcement.getIsDryRun())
                                 .ruleEnforcementIdentifier(ruleEnforcement.getUuid())
                                 .ruleEnforcementName(ruleEnforcement.getName())
                                 .executionCompletedAt(null) // Updated by worker when execution finishes
                                 .ruleIdentifier(rule.getUuid())
                                 .targetAccount(ceAzureConnectorDTO.getSubscriptionId())
                                 .targetRegions(List.of(region))
                                 .executionLogBucketType("")
                                 .ruleName(rule.getName())
                                 .OOTB(rule.getIsOOTB())
                                 .executionStatus(RuleExecutionStatusType.ENQUEUED)
                                 .executionType(RuleExecutionType.EXTERNAL)
                                 .build());
        } catch (Exception e) {
          log.warn(
              "Exception enqueueing Azure job for ruleEnforcementUuid: {} for targetSubscription: {} for targetRegions: {}, {}",
              ruleEnforcement.getUuid(), ceAzureConnectorDTO.getSubscriptionId(), region, e);
        }
      }
    }
    return ruleExecutions;
  }

  @Value
  private static class CacheKey {
    String accountId;
    String targetAccount;
  }
  @Override
  public String getSchema() {
    try {
      final ArrayList<String> schema = Lists.newArrayList("custodian", "schema", "--json");
      return getProcessExecutor().command(schema).readOutput(true).execute().outputString();
    } catch (IOException | TimeoutException e) {
      throw new InvalidRequestException("Can not get schema");
    } catch (InterruptedException e) {
      throw new InvalidRequestException("InterruptedException", e);
    }
  }
}

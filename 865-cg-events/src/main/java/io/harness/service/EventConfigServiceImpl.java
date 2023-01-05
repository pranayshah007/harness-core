/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.CgEventConfig.ACCOUNT_ID_KEY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.validation.PersistenceValidator.duplicateCheck;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CgEventConfig;
import io.harness.beans.CgEventConfig.CgEventConfigKeys;
import io.harness.beans.CgEventRule;
import io.harness.beans.EventType;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import io.dropwizard.validation.Validated;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;

@Singleton
@OwnedBy(CDC)
public class EventConfigServiceImpl implements EventConfigService {
  @Inject private HPersistence hPersistence;

  @Override
  public List<CgEventConfig> listAllEventsConfig(String accountId, String appId) {
    PageRequest<CgEventConfig> req = PageRequestBuilder.aPageRequest()
                                         .addFilter(ACCOUNT_ID_KEY, Operator.EQ, accountId)
                                         .addFilter(CgEventConfigKeys.appId, Operator.EQ, appId)
                                         .build();
    PageResponse<CgEventConfig> pageResponse = hPersistence.query(CgEventConfig.class, req);
    return pageResponse.getResponse();
  }

  @Override
  public CgEventConfig createEventsConfig(String accountId, String appId, @Valid CgEventConfig eventConfig) {
    validateEventConfig(eventConfig);
    duplicateCheck(() -> hPersistence.insert(eventConfig), "name", eventConfig.getName());
    return eventConfig;
  }

  @Override
  public CgEventConfig getEventsConfig(String accountId, String appId, @Valid @NotBlank String eventConfigId) {
    return hPersistence.createQuery(CgEventConfig.class)
        .filter(CgEventConfigKeys.accountId, accountId)
        .filter(CgEventConfigKeys.appId, appId)
        .filter(CgEventConfigKeys.uuid, eventConfigId)
        .get();
  }

  @Override
  public CgEventConfig getEventsConfigByName(String accountId, String appId, @Valid @NotBlank String eventConfigName) {
    return hPersistence.createQuery(CgEventConfig.class)
        .filter(CgEventConfigKeys.accountId, accountId)
        .filter(CgEventConfigKeys.appId, appId)
        .filter(CgEventConfigKeys.name, eventConfigName)
        .get();
  }

  @Override
  public CgEventConfig updateEventsConfig(String accountId, String appId, CgEventConfig eventConfig) {
    validateEventConfig(eventConfig);
    CgEventConfig savedConfig = getEventsConfig(accountId, appId, eventConfig.getUuid());
    if (savedConfig == null) {
      throw new InvalidRequestException("Failed to update event config: No such event config exists");
    }
    if (eventConfig.getDelegateSelectors() == null) {
      eventConfig.setDelegateSelectors(Collections.emptyList());
    }
    CgEventConfig prevConfigByName = getEventsConfigByName(accountId, appId, eventConfig.getName());
    if (prevConfigByName != null && !(prevConfigByName.getUuid()).equals(eventConfig.getUuid())) {
      throw new InvalidRequestException("Duplicate Name " + eventConfig.getName());
    }
    UpdateOperations<CgEventConfig> updateOperations =
        hPersistence.createUpdateOperations(CgEventConfig.class)
            .set(CgEventConfigKeys.rule, eventConfig.getRule())
            .set(CgEventConfigKeys.config, eventConfig.getConfig())
            .set(CgEventConfigKeys.delegateSelectors, eventConfig.getDelegateSelectors())
            .set(CgEventConfigKeys.enabled, eventConfig.isEnabled())
            .set(CgEventConfigKeys.name, eventConfig.getName());
    hPersistence.update(savedConfig, updateOperations);
    return getEventsConfig(accountId, appId, eventConfig.getUuid());
  }

  @Override
  public CgEventConfig updateEventsConfigEnable(
      String accountId, String appId, @Validated @Valid CgEventConfig eventConfig) {
    UpdateOperations<CgEventConfig> updateOperations = hPersistence.createUpdateOperations(CgEventConfig.class)
                                                           .set(CgEventConfigKeys.enabled, eventConfig.isEnabled());

    Query<CgEventConfig> query = hPersistence.createQuery(CgEventConfig.class)
                                     .filter(CgEventConfigKeys.uuid, eventConfig.getUuid())
                                     .filter(CgEventConfigKeys.accountId, eventConfig.getAccountId())
                                     .filter(CgEventConfigKeys.appId, eventConfig.getAppId());

    hPersistence.update(query, updateOperations);
    return getEventsConfig(accountId, appId, eventConfig.getUuid());
  }

  @Override
  public void deleteEventsConfig(String accountId, String appId, String eventConfigId) {
    if (getEventsConfig(accountId, appId, eventConfigId) == null) {
      throw new InvalidRequestException("Event Config does not exist");
    }
    hPersistence.delete(CgEventConfig.class, eventConfigId);
  }

  @Override
  public void pruneByApplication(String appId) {
    hPersistence.delete(hPersistence.createQuery(CgEventConfig.class).filter(CgEventConfigKeys.appId, appId));
  }

  private void validateEventConfig(CgEventConfig eventConfig) {
    CgEventRule rule = eventConfig.getRule();
    if (rule == null) {
      throw new InvalidRequestException("Event config requires rule to be specified");
    }

    CgEventRule.CgRuleType ruleType = rule.getType();
    if (ruleType == null) {
      throw new InvalidRequestException("Event config missing rule type");
    }

    if (!ruleType.equals(CgEventRule.CgRuleType.ALL)) {
      if (ruleType.equals(CgEventRule.CgRuleType.PIPELINE)) {
        validatePipelineRule(rule.getPipelineRule());
      }

      if (ruleType.equals(CgEventRule.CgRuleType.WORKFLOW)) {
        validateWorkflowRule(rule.getWorkflowRule());
      }
    }

    if (eventConfig.getConfig() == null) {
      throw new InvalidRequestException("Http details for configuration is required!");
    }

    if (StringUtils.isBlank(eventConfig.getConfig().getUrl())) {
      throw new InvalidRequestException("URL Required: URL field is blank!");
    }
  }

  private void validatePipelineRule(CgEventRule.PipelineRule pipelineRule) {
    if (pipelineRule == null) {
      throw new InvalidRequestException("For Event rule type Pipeline rule need be declared");
    }

    if (!pipelineRule.isAllEvents() && isEmpty(pipelineRule.getEvents())) {
      throw new InvalidRequestException("For Event rule type Pipeline choose all events or specify at least one event");
    }

    if (!pipelineRule.isAllPipelines() && isEmpty(pipelineRule.getPipelineIds())) {
      throw new InvalidRequestException(
          "For Event rule type Pipeline choose all pipelines or specify at least one pipeline");
    }
    if (!pipelineRule.isAllEvents()) {
      Optional<String> invalidEvent =
          pipelineRule.getEvents().stream().filter(e -> !EventType.getPipelineEvents().contains(e)).findFirst();
      if (invalidEvent.isPresent()) {
        throw new InvalidRequestException(
            "For Event rule type Pipeline we found invalid event - " + invalidEvent.get());
      }
    }
  }

  private void validateWorkflowRule(CgEventRule.WorkflowRule workflowRule) {
    if (workflowRule == null) {
      throw new InvalidRequestException("For Event rule type workflow rule need be declared");
    }

    if (!workflowRule.isAllEvents() && isEmpty(workflowRule.getEvents())) {
      throw new InvalidRequestException("For Event rule type Workflow choose all events or specify at least one event");
    }

    if (!workflowRule.isAllWorkflows() && isEmpty(workflowRule.getWorkflowIds())) {
      throw new InvalidRequestException(
          "For Event rule type workflow choose all workflows or specify at least one workflow");
    }
    if (!workflowRule.isAllEvents()) {
      Optional<String> invalidEvent =
          workflowRule.getEvents().stream().filter(e -> !EventType.getWorkflowEvents().contains(e)).findFirst();
      if (invalidEvent.isPresent()) {
        throw new InvalidRequestException(
            "For Event rule type Workflow we found invalid event - " + invalidEvent.get());
      }
    }
  }
}

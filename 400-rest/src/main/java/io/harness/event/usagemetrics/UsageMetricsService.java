/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.usagemetrics;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.event.model.EventConstants.ACCOUNT_ID;
import static io.harness.event.model.EventConstants.IS_24X7_ENABLED;
import static io.harness.event.model.EventConstants.VERIFICATION_STATE_TYPE;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.util.function.Function.identity;

import io.harness.beans.EnvironmentType;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.metrics.HarnessMetricRegistry;

import software.wings.beans.Account;
import software.wings.beans.Environment;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.trigger.Trigger;
import software.wings.common.VerificationConstants;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class UsageMetricsService {
  @Inject private UsageMetricsHelper usageMetricsHelper;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private AppService appService;
  @Inject private WorkflowService workflowService;
  @Inject private PipelineService pipelineService;
  @Inject private TriggerService triggerService;
  @Inject private EnvironmentService environmentService;
  @Inject private AccountService accountService;
  @Inject private CVConfigurationService cvConfigurationService;
  @Inject private HarnessMetricRegistry harnessMetricRegistry;
  @Inject private EventPublishHelper eventPublishHelper;

  @Getter
  @AllArgsConstructor
  @EqualsAndHashCode
  private class SetupEventGroupByKey {
    String verificationProviderType;
    EnvironmentType environmentType;
    boolean enabled24x7;
  }

  public void createSetupEventsForTimescaleDB(Account account) {
    List<CVConfiguration> cvConfigurationList = cvConfigurationService.listConfigurations(account.getUuid());
    List<Environment> environments = environmentService.getEnvByAccountId(account.getUuid(), true);
    Map<String, Environment> environmentMap =
        environments.stream().collect(Collectors.toMap(Environment::getUuid, identity()));

    Map<SetupEventGroupByKey, List<CVConfiguration>> groupConfigMap =
        cvConfigurationList.stream()
            .filter(config -> environmentMap.containsKey(config.getEnvId()))
            .collect(Collectors.groupingBy(config
                -> new SetupEventGroupByKey(VerificationConstants.getProviderTypeFromStateType(config.getStateType()),
                    environmentMap.get(config.getEnvId()).getEnvironmentType(), config.isEnabled24x7())));

    groupConfigMap.forEach((key, value) -> {
      long alertsSetup = value.stream().filter(CVConfiguration::isAlertEnabled).count();
      List<String> configIds = value.stream().map(CVConfiguration::getUuid).collect(Collectors.toList());
      eventPublishHelper.publishServiceGuardSetupEvent(account, key.getVerificationProviderType(), configIds,
          alertsSetup, key.getEnvironmentType(), key.isEnabled24x7());
    });
  }

  public void createVerificationUsageEvents(Account account) {
    List<StateType> stateTypes = VerificationConstants.getAnalysisStates();
    List<CVConfiguration> cvConfigurationList = cvConfigurationService.listConfigurations(account.getUuid());
    if (isNotEmpty(cvConfigurationList)) {
      for (StateType stateType : stateTypes) {
        Map properties = new HashMap();
        properties.put(ACCOUNT_ID, account.getUuid());
        properties.put(VERIFICATION_STATE_TYPE, stateType.name());
        properties.put(IS_24X7_ENABLED, "true");
        int count = cvConfigurationList.stream()
                        .filter(cvConfiguration
                            -> cvConfiguration.isEnabled24x7()
                                && cvConfiguration.getStateType().name().equals(stateType.name()))
                        .collect(Collectors.toList())
                        .size();
        if (count > 0) {
          emitCVUsageMetrics(properties, count);
        }
      }
    }
  }

  private void emitCVUsageMetrics(Map<String, String> properties, int metricValue) {
    Preconditions.checkNotNull(properties.get(ACCOUNT_ID));
    Preconditions.checkNotNull(properties.get(VERIFICATION_STATE_TYPE));
    Preconditions.checkNotNull(properties.get(IS_24X7_ENABLED));

    harnessMetricRegistry.recordGaugeValue(VerificationConstants.CV_META_DATA,
        new String[] {
            properties.get(ACCOUNT_ID), properties.get(VERIFICATION_STATE_TYPE), properties.get(IS_24X7_ENABLED)},
        metricValue);
  }

  protected List<Account> getAllAccounts() {
    return usageMetricsHelper.listAllAccountsWithDefaults();
  }

  protected List<String> getAppIds(String accountId) {
    return appService.getAppIdsByAccountId(accountId);
  }

  protected long getNumberOfWorkflowsForAccount(String accountId) {
    List<String> appIds = appService.getAppIdsByAccountId(accountId);
    if (appIds.size() > 0) {
      PageRequest<Workflow> pageRequest =
          aPageRequest().addFilter("appId", Operator.IN, appIds.toArray()).addFieldsIncluded("_id").build();
      final PageResponse<Workflow> workflowsList = workflowService.listWorkflows(pageRequest);
      return isEmpty(workflowsList) ? 0 : workflowsList.getTotal();
    }
    return 0;
  }

  protected long getNumberOfServicesPerAccount(List<String> apps) {
    if (apps.size() > 0) {
      PageRequest<Service> svcPageRequest = aPageRequest()
                                                .withLimit(UNLIMITED)
                                                .addFilter("appId", Operator.IN, apps.toArray())
                                                .addFieldsIncluded(ID_KEY)
                                                .build();
      return serviceResourceService.list(svcPageRequest, false, false, false, null).getTotal();
    } else {
      return 0;
    }
  }

  protected long getNumberOfPipelinesPerAccount(List<String> appIds) {
    if (appIds.size() > 0) {
      PageRequest<Pipeline> pageRequest = aPageRequest()
                                              .withLimit(UNLIMITED)
                                              .addFilter("appId", Operator.IN, appIds.toArray())
                                              .addFieldsIncluded("_id")
                                              .build();
      return pipelineService.listPipelines(pageRequest).getTotal();
    } else {
      return 0;
    }
  }

  protected long getNumberOfTriggersPerAccount(List<String> appIds) {
    if (appIds.size() > 0) {
      PageRequest<Trigger> pageRequest = aPageRequest()
                                             .withLimit(UNLIMITED)
                                             .addFilter("appId", Operator.IN, appIds.toArray())
                                             .addFieldsIncluded("_id")
                                             .build();
      return triggerService.list(pageRequest, false, null).getTotal();
    } else {
      return 0;
    }
  }

  protected long getNumberOfEnvironmentsPerAccount(List<String> appIds) {
    if (appIds.size() > 0) {
      PageRequest<Environment> pageRequest = aPageRequest()
                                                 .withLimit(UNLIMITED)
                                                 .addFilter("appId", Operator.IN, appIds.toArray())
                                                 .addFieldsIncluded("_id")
                                                 .build();
      return environmentService.list(pageRequest, false, null, false).getTotal();
    } else {
      return 0;
    }
  }
}

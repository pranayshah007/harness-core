/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.app.datafetcher.delegate;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.schema.mutation.delegate.input.QLAddDelegateScopeInput;
import io.harness.app.schema.type.delegate.QLDelegate.QLDelegateBuilder;
import io.harness.app.schema.type.delegate.QLDelegateScope;
import io.harness.app.schema.type.delegate.QLDelegateScope.QLDelegateScopeBuilder;
import io.harness.app.schema.type.delegate.QLTaskGroup;
import io.harness.beans.EnvironmentType;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateScope;
import io.harness.delegate.beans.DelegateScope.DelegateScopeBuilder;
import io.harness.delegate.beans.TaskGroup;

import software.wings.beans.DelegateConnection;
import software.wings.graphql.schema.type.QLEnvironmentType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(DEL)
public class DelegateController {
  private static final Map<String, TaskGroup> taskGroupMapping = taskGroupMapping();

  public static void populateQLDelegate(
      Delegate delegate, QLDelegateBuilder qlDelegateBuilder, List<DelegateConnection> delegateConnections) {
    qlDelegateBuilder.accountId(delegate.getAccountId())
        .delegateName(delegate.getDelegateName())
        .delegateType(delegate.getDelegateType())
        .description(delegate.getDescription())
        .hostName(delegate.getHostName())
        .pollingModeEnabled(delegate.isPolllingModeEnabled())
        .ip(delegate.getIp())
        .uuid(delegate.getUuid())
        .status(delegate.getStatus().toString())
        .delegateProfileId(delegate.getDelegateProfileId())
        .version(delegate.getVersion())
        .pollingModeEnabled(delegate.isPolllingModeEnabled())
        .lastHeartBeat(delegate.getLastHeartBeat())
        .lastHeartbeat(delegate.getLastHeartBeat())
        .includeScopes(delegate.getIncludeScopes())
        .excludeScopes(delegate.getExcludeScopes())
        .supportedTasks(delegate.getSupportedTaskTypes())
        .tags(delegate.getTags())
        .connections(delegateConnections)
        .build();
  }

  public static Map<String, TaskGroup> taskGroupMapping() {
    Map<String, TaskGroup> taskGroupTaskMap = new HashMap<>();
    Set<TaskGroup> taskGroupSet = EnumSet.allOf(TaskGroup.class);
    taskGroupSet.forEach(taskGroup -> taskGroupTaskMap.put(taskGroup.name(), taskGroup));
    return taskGroupTaskMap;
  }

  public static void populateDelegateScope(
      String accountId, QLAddDelegateScopeInput delegateScopeInput, DelegateScopeBuilder delegateScopeBuilder) {
    List<TaskGroup> taskGroupList = new ArrayList<>();
    if (delegateScopeInput.getTaskGroup() != null) {
      taskGroupList.add(taskGroupMapping().get(delegateScopeInput.getTaskGroup().name()));
    }

    List<String> applicationList = new ArrayList<>();
    if (delegateScopeInput.getApplication() != null) {
      applicationList = Arrays.asList(delegateScopeInput.getApplication().getValues());
    }
    List<String> serviceList = new ArrayList<>();
    if (delegateScopeInput.getService() != null) {
      serviceList = Arrays.asList(delegateScopeInput.getService().getValues());
    }
    List<String> environmentList = new ArrayList<>();
    if (delegateScopeInput.getEnvironment() != null) {
      environmentList = Arrays.asList(delegateScopeInput.getEnvironment().getValues());
    }
    List<String> infrastructureDefinitionList = new ArrayList<>();
    if (delegateScopeInput.getInfrastructureDefinition() != null) {
      infrastructureDefinitionList = Arrays.asList(delegateScopeInput.getInfrastructureDefinition().getValues());
    }

    delegateScopeBuilder.name(delegateScopeInput.getName())
        .accountId(accountId)
        .taskTypes(taskGroupList)
        .environments(environmentList)
        .environmentTypes(populateEnvironmentTypeList(delegateScopeInput.getEnvironmentTypes()))
        .services(serviceList)
        .applications(applicationList)
        .infrastructureDefinitions(infrastructureDefinitionList)
        .build();
  }

  public static void populateQLDelegateScope(
      DelegateScope delegateScope, QLDelegateScopeBuilder qlDelegateScopeBuilder) {
    qlDelegateScopeBuilder.name(delegateScope.getName())
        .applications(delegateScope.getApplications())
        .services(delegateScope.getServices())
        .environments(delegateScope.getEnvironments())
        .uuid(delegateScope.getUuid())
        .environmentTypes(populateQLEnvironmentTypeList(delegateScope.getEnvironmentTypes()))
        .build();
  }

  public static QLDelegateScope populateQLDelegateScope(DelegateScope delegateScope) {
    QLDelegateScopeBuilder qlDelegateScopeBuilder = QLDelegateScope.builder();
    populateQLDelegateScope(delegateScope, qlDelegateScopeBuilder);
    return qlDelegateScopeBuilder.build();
  }

  public static List<TaskGroup> populateTaskGroup(List<QLTaskGroup> qlTaskGroup) {
    return qlTaskGroup.stream()
        .filter(Objects::nonNull)
        .map(taskGroup -> taskGroupMapping().get(taskGroup.name()))
        .collect(Collectors.toList());
  }

  public static List<EnvironmentType> populateEnvironmentTypeList(List<QLEnvironmentType> qlEnvironmentTypeList) {
    List<EnvironmentType> environmentTypeList = new ArrayList<>();
    if (isEmpty(qlEnvironmentTypeList)) {
      return environmentTypeList;
    }
    environmentTypeList = qlEnvironmentTypeList.stream()
                              .filter(Objects::nonNull)
                              .map(DelegateController::toEnvironmentType)
                              .collect(Collectors.toList());
    return environmentTypeList;
  }

  public static List<QLEnvironmentType> populateQLEnvironmentTypeList(List<EnvironmentType> environmentTypeList) {
    List<QLEnvironmentType> qlEnvironmentTypeList = new ArrayList<>();
    if (isEmpty(environmentTypeList)) {
      return qlEnvironmentTypeList;
    }
    qlEnvironmentTypeList = environmentTypeList.stream()
                                .filter(Objects::nonNull)
                                .map(DelegateController::toQLEnvironmentType)
                                .collect(Collectors.toList());
    return qlEnvironmentTypeList;
  }

  private EnvironmentType toEnvironmentType(QLEnvironmentType qlEnvironmentType) {
    if (qlEnvironmentType == QLEnvironmentType.PROD) {
      return EnvironmentType.PROD;
    }
    if (qlEnvironmentType == QLEnvironmentType.NON_PROD) {
      return EnvironmentType.NON_PROD;
    }
    return null;
  }

  private QLEnvironmentType toQLEnvironmentType(EnvironmentType environmentType) {
    if (environmentType == EnvironmentType.PROD) {
      return QLEnvironmentType.PROD;
    }
    if (environmentType == EnvironmentType.NON_PROD) {
      return QLEnvironmentType.NON_PROD;
    }
    return null;
  }
}

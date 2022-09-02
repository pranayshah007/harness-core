/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.delegatetasks.validation.DelegateConnectionResult.DelegateConnectionResultKeys;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.TASK_VALIDATION_FAILED;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.persistence.HPersistence;

import software.wings.delegatetasks.validation.DelegateConnectionResult;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.DEL)
@Singleton
@Slf4j
public final class ValidationFailedTaskMessageHelper {
  @Inject private HPersistence persistence;

  public String generateValidationError(DelegateTask delegateTask) {
    final List<ExecutionCapability> capabilities = delegateTask.getExecutionCapabilities();
    if (Objects.isNull(capabilities) || capabilities.size() == 0) {
      log.error("Task {} has no required capabilities, but validation failed", delegateTask.getUuid());
      return "Task has no required capabilities";
    }
    return format("%s [ %s ]", TASK_VALIDATION_FAILED, generateMissingCapabilitiesMessage(delegateTask));
  }

  private String generateMissingCapabilitiesMessage(final DelegateTask delegateTask) {
    Set<String> requiredCapabilitiesCriterias = delegateTask.getExecutionCapabilities()
                                                    .stream()
                                                    .map(ExecutionCapability::fetchCapabilityBasis)
                                                    .collect(Collectors.toSet());
    Set<String> eligibleDelegateIdsSet = new HashSet<>(delegateTask.getEligibleToExecuteDelegateIds());

    // Find which are the missing capabilities
    List<DelegateConnectionResult> results =
        persistence.createQuery(DelegateConnectionResult.class, excludeAuthority)
            .filter(DelegateConnectionResultKeys.accountId, delegateTask.getAccountId())
            .field(DelegateConnectionResultKeys.criteria)
            .in(requiredCapabilitiesCriterias)
            .asList();
    Set<String> validatedCriterias =
        results.stream()
            .filter(
                delegateConnectionResult -> eligibleDelegateIdsSet.contains(delegateConnectionResult.getDelegateId()))
            .filter(DelegateConnectionResult::isValidated)
            .map(DelegateConnectionResult::getCriteria)
            .collect(Collectors.toSet());

    return requiredCapabilitiesCriterias.stream()
        .filter(required -> !validatedCriterias.contains(required))
        .collect(joining(", "));
  }
}

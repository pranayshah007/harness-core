/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.custom.entities;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.iterator.PersistentIrregularIterable;
import io.harness.ng.DbAliases;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.servicenow.misc.TicketNG;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapperDTO;
import io.harness.steps.approval.step.custom.CustomApprovalOutcome;
import io.harness.steps.approval.step.custom.CustomApprovalSpecParameters;
import io.harness.steps.approval.step.custom.beans.CustomApprovalTicketNG;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellScriptStepParameters;
import io.harness.steps.shellscript.ShellType;
import io.harness.yaml.core.timeout.Timeout;

import dev.morphia.annotations.Entity;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "CustomApprovalInstanceKeys")
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.PMS)
@Entity(value = "approvalInstances", noClassnameStored = true)
@Persistent
@TypeAlias("customApprovalInstances")
public class CustomApprovalInstance extends ApprovalInstance implements PersistentIrregularIterable {
  @NotNull ShellType shellType;
  @NotNull ShellScriptSourceWrapper source;
  ParameterField<Timeout> retryInterval;
  Map<String, Object> outputVariables;
  Set<String> secretOutputVariables;
  Map<String, Object> environmentVariables;
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;
  @NotNull CriteriaSpecWrapperDTO approvalCriteria;
  CriteriaSpecWrapperDTO rejectionCriteria;
  ParameterField<Timeout> scriptTimeout;
  List<Long> nextIterations;

  public static CustomApprovalInstance fromStepParameters(Ambiance ambiance, StepElementParameters stepParameters) {
    if (stepParameters == null) {
      return null;
    }

    CustomApprovalSpecParameters specParameters = (CustomApprovalSpecParameters) stepParameters.getSpec();
    CustomApprovalInstance instance =
        CustomApprovalInstance.builder()
            .shellType(specParameters.getShellType())
            .source(specParameters.getSource())
            .retryInterval(getTimeout("retryInterval", specParameters.getRetryInterval().getValue()))
            .delegateSelectors(specParameters.getDelegateSelectors())
            .environmentVariables(specParameters.getEnvironmentVariables())
            .outputVariables(specParameters.getOutputVariables())
            .secretOutputVariables(specParameters.getSecretOutputVariables())
            .approvalCriteria(
                CriteriaSpecWrapperDTO.fromCriteriaSpecWrapper(specParameters.getApprovalCriteria(), false))
            .rejectionCriteria(
                CriteriaSpecWrapperDTO.fromCriteriaSpecWrapper(specParameters.getRejectionCriteria(), true))
            .scriptTimeout(getTimeout("scriptTimeout", specParameters.getScriptTimeout().getValue()))
            .build();
    instance.updateFromStepParameters(ambiance, stepParameters);
    instance.setNextIterations(instance.recalculateNextIterations());
    return instance;
  }

  public CustomApprovalOutcome toCustomApprovalOutcome(TicketNG ticketNG) {
    return CustomApprovalOutcome.builder().outputVariables(((CustomApprovalTicketNG) ticketNG).getFields()).build();
  }

  public ShellScriptStepParameters toShellScriptStepParameters() {
    return ShellScriptStepParameters.infoBuilder()
        .environmentVariables(getEnvironmentVariables())
        .shellType(getShellType())
        .delegateSelectors(getDelegateSelectors())
        .outputVariables(getOutputVariables())
        .secretOutputVariables(secretOutputVariables)
        .onDelegate(ParameterField.createValueField(true))
        .source(getSource())
        .uuid(getUuid())
        .build();
  }

  private long getGap() {
    long timeout = getScriptTimeout().getValue().getTimeoutInMillis();
    long retryTime = getRetryInterval().getValue().getTimeoutInMillis();
    // Adding a buffer on top of the timeout + retry.
    return timeout + retryTime + 60000L;
  }

  @Override
  public List<Long> recalculateNextIterations(String fieldName, boolean skipMissed, long throttled) {
    long nextAttempt = ZonedDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli();
    long gap = getGap();
    if (EmptyPredicate.isNotEmpty(this.getNextIterations())) {
      nextAttempt = nextAttempt + gap;
    }
    return getAllIterations(nextAttempt, gap);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return EmptyPredicate.isEmpty(nextIterations) ? ZonedDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli()
                                                  : nextIterations.get(0);
  }

  private long calculateNextRun() {
    // If it is empty it means it is the first run
    long now = ZonedDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli();
    if (EmptyPredicate.isEmpty(nextIterations)) {
      return now;
    }
    return now + getRetryInterval().getValue().getTimeoutInMillis();
  }

  private static List<Long> getAllIterations(final long firstRun, final long interval) {
    return IntStream.range(0, 10).mapToObj(i -> firstRun + interval * i).collect(Collectors.toList());
  }

  public List<Long> recalculateNextIterations() {
    long nextRun = calculateNextRun();
    long gap = getGap();
    return getAllIterations(nextRun, gap);
  }
}

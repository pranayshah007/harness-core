/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.servicenow.entities;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.iterator.PersistentIrregularIterable;
import io.harness.ng.DbAliases;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.servicenow.ServiceNowFieldValueNG;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapperDTO;
import io.harness.steps.approval.step.beans.ServiceNowChangeWindowSpecDTO;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalOutCome;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalSpecParameters;
import io.harness.yaml.core.timeout.Timeout;

import dev.morphia.annotations.Entity;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "ServiceNowApprovalInstanceKeys")
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.PMS)
@Entity(value = "approvalInstances", noClassnameStored = true)
@Persistent
@TypeAlias("serviceNowApprovalInstances")
public class ServiceNowApprovalInstance extends ApprovalInstance implements PersistentIrregularIterable {
  @NotEmpty String connectorRef;
  @NotEmpty String ticketNumber;
  @NotEmpty String ticketType;
  @NotNull CriteriaSpecWrapperDTO approvalCriteria;
  CriteriaSpecWrapperDTO rejectionCriteria;
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;
  ServiceNowChangeWindowSpecDTO changeWindow;
  Map<String, ServiceNowFieldValueNG> ticketFields;
  ParameterField<Timeout> retryInterval;
  List<Long> nextIterations;

  public static ServiceNowApprovalInstance fromStepParameters(Ambiance ambiance, StepElementParameters stepParameters) {
    if (stepParameters == null) {
      return null;
    }

    ServiceNowApprovalSpecParameters specParameters = (ServiceNowApprovalSpecParameters) stepParameters.getSpec();
    String ticketNumber = specParameters.getTicketNumber().getValue();
    String connectorRef = specParameters.getConnectorRef().getValue();
    String ticketType = specParameters.getTicketType().getValue();

    if (isBlank(ticketNumber)) {
      throw new InvalidRequestException("ticketNumber can't be empty");
    }
    if (isBlank(connectorRef)) {
      throw new InvalidRequestException("connectorRef can't be empty");
    }
    if (isBlank(ticketType)) {
      throw new InvalidRequestException(String.format("%s can't be empty", ServiceNowApprovalInstanceKeys.ticketType));
    }
    // allowing custom tables too , hence ticketType is not validated to be in ServiceNowTicketTypeNG
    if (ParameterField.isNotNull(specParameters.getRetryInterval())
        && specParameters.getRetryInterval().isExpression()) {
      throw new InvalidRequestException(String.format("Could not resolve expression %s for retry interval field",
          specParameters.getRetryInterval().getExpressionValue()));
    }
    ServiceNowApprovalInstance instance =
        ServiceNowApprovalInstance.builder()
            .connectorRef(connectorRef)
            .ticketNumber(ticketNumber)
            .ticketType(ticketType)
            .approvalCriteria(
                CriteriaSpecWrapperDTO.fromCriteriaSpecWrapper(specParameters.getApprovalCriteria(), false))
            .rejectionCriteria(
                CriteriaSpecWrapperDTO.fromCriteriaSpecWrapper(specParameters.getRejectionCriteria(), true))
            .retryInterval(ParameterField.isBlank(specParameters.getRetryInterval())
                    ? ParameterField.createValueField(Timeout.fromString("1m"))
                    : getTimeout("retryInterval", specParameters.getRetryInterval().getValue()))
            .delegateSelectors(specParameters.getDelegateSelectors())
            .changeWindow(
                ServiceNowChangeWindowSpecDTO.fromServiceNowChangeWindowSpec(specParameters.getChangeWindowSpec()))
            .ticketFields(new HashMap<>())
            .build();

    if (ParameterField.isNull(instance.getRetryInterval())) {
      throw new InvalidRequestException(String.format("retry interval field for ServiceNow approval cannot be empty"));
    }

    if (instance.getRetryInterval().getValue().getTimeoutInMillis() < 10000L) {
      throw new InvalidRequestException(
          String.format("retry interval field for ServiceNow approval cannot be less than 10s"));
    }

    instance.setNextIterations(instance.recalculateNextIterations());

    instance.updateFromStepParameters(ambiance, stepParameters);
    return instance;
  }

  public ServiceNowApprovalOutCome toServiceNowApprovalOutcome() {
    return ServiceNowApprovalOutCome.builder().build();
  }

  private long getGap() {
    long retryTime = getRetryInterval().getValue().getTimeoutInMillis();
    // Adding a buffer on top of the timeout + retry.
    return ASYNC_DELEGATE_TIMEOUT + retryTime + APPROVAL_LEEWAY_IN_MILLIS;
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

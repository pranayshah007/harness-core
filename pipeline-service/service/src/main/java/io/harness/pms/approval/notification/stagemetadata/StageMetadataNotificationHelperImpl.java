/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.approval.notification.stagemetadata;

import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.cdstage.remote.CDNGStageSummaryResourceClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class StageMetadataNotificationHelperImpl implements StageMetadataNotificationHelper {
  private final CDNGStageSummaryResourceClient cdngStageSummaryResourceClient;

  @Inject
  public StageMetadataNotificationHelperImpl(CDNGStageSummaryResourceClient cdngStageSummaryResourceClient) {
    this.cdngStageSummaryResourceClient = cdngStageSummaryResourceClient;
  }

  @Override
  public void setFormattedSummaryOfFinishedStages(
      @NotNull Set<StageSummary> finishedStages, @NotNull Set<String> formattedFinishedStages, @NotNull Scope scope) {
    if (EmptyPredicate.isEmpty(finishedStages)) {
      log.warn("Missing finishedStages in setFormattedSummaryOfFinishedStages, returning");
      return;
    }
    if (isNull(formattedFinishedStages) || isNull(scope)) {
      throw new InvalidRequestException("Formatted finished stages and scope is required");
    }
    // IMP: changing the reference, as we will be removing stages from finishedStages
    finishedStages = new HashSet<>(finishedStages);

    handleFinishedStagesWithExecutionIdentifierAbsent(finishedStages, formattedFinishedStages);
    // now we have stages with stage execution identifiers
    handleCDFinishedStages(finishedStages, formattedFinishedStages, scope);
    handleGenericStages(finishedStages, formattedFinishedStages);

    if (!finishedStages.isEmpty()) {
      // this isn't expected
      log.error("Unknown error in setFormattedSummaryOfFinishedStages: unable to process [{}] stages", finishedStages);
    }
  }

  /**
   * removes stages with execution identifiers missing from stages and adds name or else identifier in formattedStages
   * set
   */
  private void handleFinishedStagesWithExecutionIdentifierAbsent(
      @NotNull Set<StageSummary> stages, @NotNull Set<String> formattedFinishedStages) {
    Set<String> namesForStagesWithoutExecutionIds =
        stages.stream()
            .filter(stageSummary -> StringUtils.isBlank(stageSummary.getStageExecutionIdentifier()))
            .map(stageSummary -> {
              stages.remove(stageSummary);
              return stageSummary.getFormattedEntityName();
            })
            .collect(Collectors.toSet());

    log.warn(
        "Stage Execution ids not found for [{}] stages, defaulting to stage names", namesForStagesWithoutExecutionIds);
    formattedFinishedStages.addAll(namesForStagesWithoutExecutionIds);
  }

  /**
   * assumes that each stage in finalStages has a stage execution identifier
   *
   * removes CD stages from finalStages and adds formatted summary in formattedFinishedStages set
   */
  private void handleCDFinishedStages(
      @NotNull Set<StageSummary> finalStages, @NotNull Set<String> formattedFinishedStages, @NotNull Scope scope) {
    Map<String, CDStageSummary> cdStagesSummaryMap = new HashMap<>();
    Iterator<StageSummary> iterator = finalStages.iterator();

    while (iterator.hasNext()) {
      StageSummary stageSummary = iterator.next();

      if (stageSummary instanceof CDStageSummary) {
        iterator.remove(); // Remove the current element from the iterator
        CDStageSummary cdStageSummary = (CDStageSummary) stageSummary;
        cdStagesSummaryMap.put(cdStageSummary.getStageExecutionIdentifier(), cdStageSummary);
      }
    }
    if (cdStagesSummaryMap.isEmpty()) {
      // no CD finished stage found
      return;
    }

    Set<String> cdStageExecutionIdentifiers = cdStagesSummaryMap.keySet();

    Map<String, CDStageSummaryResponseDTO> cdFinishedFormattedSummary = new HashMap<>();
    try {
      cdFinishedFormattedSummary =
          getResponse(cdngStageSummaryResourceClient.listStageExecutionFormattedSummary(scope.getAccountIdentifier(),
              scope.getOrgIdentifier(), scope.getProjectIdentifier(), new ArrayList<>(cdStageExecutionIdentifiers)));
    } catch (Exception ex) {
      log.warn(
          "Error occurred while listStageExecutionFormattedSummary with accountId:{}, orgId:{}, projectId:{}, executionIds:[{}] ",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(),
          cdStageExecutionIdentifiers, ex);
      log.warn("Defaulting to stage names for these finished CD stages: [{}]", cdStageExecutionIdentifiers);
    }
    if (isNull(cdFinishedFormattedSummary)) {
      log.warn(
          "Null response obtained while listStageExecutionFormattedSummary with accountId:{}, orgId:{}, projectId:{}, executionIds:[{}] ",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(),
          cdStageExecutionIdentifiers);
      log.warn("Defaulting to stage names for these finished CD stages: [{}]", cdStageExecutionIdentifiers);
      cdFinishedFormattedSummary = new HashMap<>();
    }

    // add names for stages without summary
    SetView<String> stageExecutionIdsWithoutSummary =
        Sets.difference(cdStageExecutionIdentifiers, cdFinishedFormattedSummary.keySet());
    log.warn("Unable to fetch summary for {} via listStageExecutionFormattedSummary", stageExecutionIdsWithoutSummary);

    formattedFinishedStages.addAll(stageExecutionIdsWithoutSummary.stream()
                                       .map(cdStagesSummaryMap::get)
                                       .map(StageSummary::getFormattedEntityName)
                                       .collect(Collectors.toSet()));

    // add metadata for stages with summary
    SetView<String> stageExecutionIdsWithSummary =
        Sets.intersection(cdStageExecutionIdentifiers, cdFinishedFormattedSummary.keySet());
    Map<String, CDStageSummaryResponseDTO> finalCdFinishedFormattedSummary = cdFinishedFormattedSummary;
    formattedFinishedStages.addAll(
        stageExecutionIdsWithSummary.stream()
            .map(idWithSummary
                -> StageMetadataNotificationHelper.formatCDStageMetadata(
                    finalCdFinishedFormattedSummary.get(idWithSummary), cdStagesSummaryMap.get(idWithSummary)))
            .collect(Collectors.toSet()));
  }

  /**
   * removes common/generic stages from stages and adds names in formattedStages set
   */
  private void handleGenericStages(@NotNull Set<StageSummary> stages, @NotNull Set<String> formattedStages) {
    Iterator<StageSummary> iterator = stages.iterator();

    while (iterator.hasNext()) {
      StageSummary stageSummary = iterator.next();

      if (stageSummary instanceof GenericStageSummary) {
        iterator.remove(); // Remove the current element from the iterator
        GenericStageSummary genericStageSummary = (GenericStageSummary) stageSummary;
        formattedStages.add(genericStageSummary.getFormattedEntityName());
      }
    }
  }

  @Override
  public void setFormattedSummaryOfRunningStages(@NotNull Set<StageSummary> runningStages,
      @NotNull Set<String> formattedRunningStages, @NotNull Scope scope, @NotNull String planExecutionId) {
    if (EmptyPredicate.isEmpty(runningStages)) {
      log.warn("Missing running stages in setFormattedSummaryOfRunningStages, returning");
      return;
    }
    if (isNull(formattedRunningStages) || isNull(scope) || StringUtils.isBlank(planExecutionId)) {
      throw new InvalidRequestException("Formatted running stages and scope and planExecutionId is required");
    }
    // TODO: optimistically use execution info wherever applicable
    setFormattedSummaryOfUpcomingStages(runningStages, formattedRunningStages, scope, planExecutionId);
  }

  @Override
  public void setFormattedSummaryOfUpcomingStages(@NotNull Set<StageSummary> upcomingStages,
      @NotNull Set<String> formattedUpcomingStages, @NotNull Scope scope, @NotNull String planExecutionId) {
    if (EmptyPredicate.isEmpty(upcomingStages)) {
      log.warn("Missing upcoming stages in setFormattedSummaryOfUpcomingStages, returning");
      return;
    }
    if (isNull(formattedUpcomingStages) || isNull(scope) || StringUtils.isBlank(planExecutionId)) {
      throw new InvalidRequestException("Formatted Upcoming stages and scope and plan id is required");
    }

    // IMP: changing the reference, as we will be removing stages from stagesSummary
    upcomingStages = new HashSet<>(upcomingStages);
    handleCDUpcomingFormattedStages(upcomingStages, formattedUpcomingStages, scope, planExecutionId);
    handleGenericStages(upcomingStages, formattedUpcomingStages);

    if (!upcomingStages.isEmpty()) {
      // this isn't expected
      log.error("Unknown error in setFormattedSummaryOfUpcomingStages: unable to process [{}] stages", upcomingStages);
    }
  }

  /**
   *
   * removes CD stages from upcomingStages and adds formatted summary in formattedUpcomingStages set
   */
  private void handleCDUpcomingFormattedStages(@NotNull Set<StageSummary> upcomingStages,
      @NotNull Set<String> formattedUpcomingStages, @NotNull Scope scope, @NotBlank String planExecutionId) {
    Map<String, CDStageSummary> cdStagesSummaryMap = new HashMap<>();
    Iterator<StageSummary> iterator = upcomingStages.iterator();

    while (iterator.hasNext()) {
      StageSummary stageSummary = iterator.next();

      if (stageSummary instanceof CDStageSummary) {
        iterator.remove(); // Remove the current element from the iterator
        CDStageSummary cdStageSummary = (CDStageSummary) stageSummary;
        cdStagesSummaryMap.put(cdStageSummary.getStageIdentifier(), cdStageSummary);
      }
    }

    if (cdStagesSummaryMap.isEmpty()) {
      // no CD upcoming stage found
      return;
    }

    Set<String> cdStageIdentifiers = cdStagesSummaryMap.keySet();

    Map<String, CDStageSummaryResponseDTO> cdUpcomingFormattedSummary = new HashMap<>();
    try {
      cdUpcomingFormattedSummary = getResponse(cdngStageSummaryResourceClient.listStagePlanCreationFormattedSummary(
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), planExecutionId,
          new ArrayList<>(cdStageIdentifiers)));
    } catch (Exception ex) {
      log.warn(
          "Error occurred while listStagePlanCreationFormattedSummary with accountId:{}, orgId:{}, projectId:{}, planId:{}, stageIds:[{}] ",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), planExecutionId,
          cdStageIdentifiers, ex);
      log.warn("Defaulting to stage names for these upcoming CD stages: [{}]", cdStageIdentifiers);
    }
    if (isNull(cdUpcomingFormattedSummary)) {
      log.warn(
          "Null response obtained while listStagePlanCreationFormattedSummary with accountId:{}, orgId:{}, projectId:{}, planId:{}, stageIds:[{}] ",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), planExecutionId,
          cdStageIdentifiers);
      log.warn("Defaulting to stage names for these upcoming CD stages: [{}]", cdStageIdentifiers);
      cdUpcomingFormattedSummary = new HashMap<>();
    }

    // add names for stages without summary
    SetView<String> stageIdsWithoutSummary = Sets.difference(cdStageIdentifiers, cdUpcomingFormattedSummary.keySet());
    log.warn("Unable to fetch summary for {} via listStagePlanCreationFormattedSummary", stageIdsWithoutSummary);

    formattedUpcomingStages.addAll(stageIdsWithoutSummary.stream()
                                       .map(cdStagesSummaryMap::get)
                                       .map(StageSummary::getFormattedEntityName)
                                       .collect(Collectors.toSet()));

    // add metadata for stages with summary
    SetView<String> stageIdsWithSummary = Sets.intersection(cdStageIdentifiers, cdUpcomingFormattedSummary.keySet());
    Map<String, CDStageSummaryResponseDTO> finalCdUpcomingFormattedSummary = cdUpcomingFormattedSummary;
    formattedUpcomingStages.addAll(
        stageIdsWithSummary.stream()
            .map(idWithSummary
                -> StageMetadataNotificationHelper.formatCDStageMetadata(
                    finalCdUpcomingFormattedSummary.get(idWithSummary), cdStagesSummaryMap.get(idWithSummary)))
            .collect(Collectors.toSet()));
  }
}

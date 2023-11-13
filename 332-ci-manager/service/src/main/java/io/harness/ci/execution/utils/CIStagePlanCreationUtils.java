/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import io.harness.beans.FeatureName;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.execution.execution.CIAccountExecutionMetadata;
import io.harness.ci.execution.validation.CIAccountValidationService;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.core.ci.dashboard.CIOverviewDashboardService;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.steps.common.StageElementParameters.StageElementParametersBuilder;
import io.harness.pms.tags.TagUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIAccountExecutionMetadataRepository;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.yaml.utils.NGVariablesUtils;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CIStagePlanCreationUtils {
  @Inject CIAccountValidationService validationService;
  @Inject CILicenseService ciLicenseService;
  @Inject CIAccountExecutionMetadataRepository accountExecutionMetadataRepository;
  @Inject CIOverviewDashboardService ciOverviewDashboardService;
  @Inject CIFeatureFlagService ciFeatureFlagService;
  static final String NOT_VERIFIED_ERROR =
      "We apologize, but your account is not verified for Harness Cloud. To resolve this issue, please use your work email or contact support to request account verification: support@harness.io";
  static final String CREDIT_CARD_ERROR =
      "To use Harness Cloud, you must provide a credit card to validate your account";
  static final String ACCOUNT_LIMIT_ERROR =
      "You have reached the account build limit. Please contact support: support@harness.io";

  public StageElementParametersBuilder getStageParameters(IntegrationStageNode stageNode) {
    TagUtils.removeUuidFromTags(stageNode.getTags());

    StageElementParametersBuilder stageBuilder = StageElementParameters.builder();
    stageBuilder.name(stageNode.getName());
    stageBuilder.identifier(stageNode.getIdentifier());
    stageBuilder.description(SdkCoreStepUtils.getParameterFieldHandleValueNull(stageNode.getDescription()));
    stageBuilder.failureStrategies(
        stageNode.getFailureStrategies() != null ? stageNode.getFailureStrategies().getValue() : null);
    stageBuilder.skipCondition(stageNode.getSkipCondition());
    stageBuilder.when(stageNode.getWhen() != null ? stageNode.getWhen().getValue() : null);
    stageBuilder.type(stageNode.getType());
    stageBuilder.uuid(stageNode.getUuid());
    stageBuilder.variables(
        ParameterField.createValueField(NGVariablesUtils.getMapOfVariables(stageNode.getVariables())));
    stageBuilder.tags(CollectionUtils.emptyIfNull(stageNode.getTags()));

    return stageBuilder;
  }

  public static boolean isHostedInfra(Infrastructure infrastructure) {
    return infrastructure.getType().equals(Infrastructure.Type.HOSTED_VM);
  }

  public void validateFreeAccountStageExecutionLimit(String accountId, Infrastructure infrastructure) {
    if (isHostedInfra(infrastructure)) {
      LicensesWithSummaryDTO licensesWithSummaryDTO = ciLicenseService.getLicenseSummary(accountId);

      if (licensesWithSummaryDTO == null) {
        throw new CIStageExecutionException("Please enable CI free plan or reach out to support.");
      }

      if (licensesWithSummaryDTO != null && licensesWithSummaryDTO.getEdition() == Edition.FREE) {
        Optional<CIAccountExecutionMetadata> accountExecutionMetadata =
            accountExecutionMetadataRepository.findByAccountId(accountId);

        if (accountExecutionMetadata.isPresent()) {
          if (ciFeatureFlagService.isEnabled(FeatureName.CI_CREDIT_CARD_ONBOARDING, accountId)) {
            enforceCreditsCount(accountId, accountExecutionMetadata);
          } else {
            enforceBuildsCount(accountId, accountExecutionMetadata);
          }
        }
      }
    }
  }

  private void enforceCreditsCount(String accountId, Optional<CIAccountExecutionMetadata> accountExecutionMetadata) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    long startOfMonthMillis = startOfMonth.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    long currentTimeMillis = System.currentTimeMillis();
    long creditsUsed =
        ciOverviewDashboardService.getHostedCreditUsage(accountId, startOfMonthMillis, currentTimeMillis);
    long monthlyLimit = validationService.getMaxCreditsPerMonth(accountId);
    if (creditsUsed >= monthlyLimit) {
      if (monthlyLimit == 0) {
        log.error(CREDIT_CARD_ERROR);
        throw new CIStageExecutionException(CREDIT_CARD_ERROR);
      } else {
        log.error(ACCOUNT_LIMIT_ERROR);
        throw new CIStageExecutionException(ACCOUNT_LIMIT_ERROR);
      }
    }
  }

  private void enforceBuildsCount(String accountId, Optional<CIAccountExecutionMetadata> accountExecutionMetadata) {
    LocalDate startDate = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate();
    YearMonth yearMonth = YearMonth.of(startDate.getYear(), startDate.getMonth());
    String day = yearMonth + "-" + startDate.getDayOfMonth();
    Map<String, Long> countPerDay = accountExecutionMetadata.get().getAccountExecutionInfo().getCountPerDay();
    if (countPerDay != null) {
      long maxBuildsPerDay = validationService.getMaxBuildPerDay(accountId);
      if (countPerDay.getOrDefault(day, 0L) >= maxBuildsPerDay) {
        if (maxBuildsPerDay == 0) {
          log.error(NOT_VERIFIED_ERROR);
          throw new CIStageExecutionException(NOT_VERIFIED_ERROR);
        } else {
          log.error(ACCOUNT_LIMIT_ERROR);
          throw new CIStageExecutionException(ACCOUNT_LIMIT_ERROR);
        }
      }
    }
  }
}

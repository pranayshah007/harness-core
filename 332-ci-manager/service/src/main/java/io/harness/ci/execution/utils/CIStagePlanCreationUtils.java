/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.execution.CIAccountExecutionMetadata;
import io.harness.ci.validation.CIAccountValidationService;
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
    return infrastructure.getType().equals(Infrastructure.Type.HOSTED_VM)
        || infrastructure.getType().equals(Infrastructure.Type.KUBERNETES_HOSTED);
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
          LocalDate startDate = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate();
          YearMonth yearMonth = YearMonth.of(startDate.getYear(), startDate.getMonth());
          String day = yearMonth + "-" + startDate.getDayOfMonth();
          Map<String, Long> countPerDay = accountExecutionMetadata.get().getAccountExecutionInfo().getCountPerDay();
          if (countPerDay != null) {
            long maxBuildsPerDay = validationService.getMaxBuildPerDay(accountId);
            if (countPerDay.getOrDefault(day, 0L) >= maxBuildsPerDay) {
              if (maxBuildsPerDay == 0) {
                log.error("Your account is not verified. To request verification, contact support: support@harness.io");
                throw new CIStageExecutionException(
                    "Your account is not verified. To request verification, contact support: support@harness.io");
              } else {
                log.error("You have reached your account limits. Please contact support: support@harness.io");
                throw new CIStageExecutionException(
                    "You have reached your account limits. Please contact support: support@harness.io");
              }
            }
          }
        }
      }
    }
  }
}

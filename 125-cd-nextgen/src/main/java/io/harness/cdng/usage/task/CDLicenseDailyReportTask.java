/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.task;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.usage.task.CDLicenseReportAccounts.CDLicenseReportAccountsKeys;
import io.harness.entities.DeploymentSummary;
import io.harness.entities.DeploymentSummary.DeploymentSummaryKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PLG_LICENSING})
@Singleton
@OwnedBy(CDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class CDLicenseDailyReportTask implements Runnable {
  private MongoTemplate mongoTemplate;

  @Override
  public void run() {
    try {
      execute();
    } catch (Exception e) {
      log.error("Exception while running task: {}", CDLicenseDailyReportTask.class.getName(), e);
    }
  }

  private void execute() {
    Query query = new Query();
    final List<String> accountIdsToBeInserted = mongoTemplate.findDistinct(
        query, DeploymentSummaryKeys.accountIdentifier, DeploymentSummary.class, String.class);
    final List<String> accountIdsAlreadyExist = mongoTemplate.findDistinct(
        query, CDLicenseReportAccountsKeys.accountIdentifier, CDLicenseReportAccounts.class, String.class);
    accountIdsToBeInserted.removeAll(accountIdsAlreadyExist);
    if (accountIdsToBeInserted.size() != 0) {
      final List<CDLicenseReportAccounts> cdLicenseReportAccounts = accountIdsToBeInserted.stream()
                                                                        .map(item
                                                                            -> CDLicenseReportAccounts.builder()
                                                                                   .accountIdentifier(item)
                                                                                   .cdLicenseDailyReportIteration(0L)
                                                                                   .build())
                                                                        .collect(Collectors.toList());
      mongoTemplate.insertAll(cdLicenseReportAccounts);
    }
  }
}

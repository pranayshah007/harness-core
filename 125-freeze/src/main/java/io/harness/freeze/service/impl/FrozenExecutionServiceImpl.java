/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.service.impl;

import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.entity.FrozenExecution;
import io.harness.freeze.mappers.FrozenExecutionMapper;
import io.harness.freeze.service.FrozenExecutionService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.repositories.FrozenExecutionRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.transaction.support.TransactionTemplate;

@Singleton
@Slf4j
public class FrozenExecutionServiceImpl implements FrozenExecutionService {
  private final FrozenExecutionRepository frozenExecutionRepository;
  private final TransactionTemplate transactionTemplate;

  @Inject
  public FrozenExecutionServiceImpl(
      FrozenExecutionRepository frozenExecutionRepository, TransactionTemplate transactionTemplate) {
    this.frozenExecutionRepository = frozenExecutionRepository;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public void createFrozenExecution(Ambiance ambiance, List<FreezeSummaryResponseDTO> manualFreezeConfigs,
      List<FreezeSummaryResponseDTO> globalFreezeConfigs) {
    try {
      FrozenExecution frozenExecution =
          FrozenExecutionMapper.toFreezeWithExecution(ambiance, manualFreezeConfigs, globalFreezeConfigs);
      if (frozenExecution != null) {
        Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
          return frozenExecutionRepository.save(frozenExecution);
        }));
      }
    } catch (Exception e) {
      String accountId = null;
      String planExecutionId = null;
      if (ambiance != null) {
        accountId = AmbianceUtils.getAccountId(ambiance);
        planExecutionId = ambiance.getPlanExecutionId();
      }
      log.error("Exception occurred while saving frozen execution for account: {} planExecutionId: {}", accountId,
          planExecutionId, e);
    }
  }
}

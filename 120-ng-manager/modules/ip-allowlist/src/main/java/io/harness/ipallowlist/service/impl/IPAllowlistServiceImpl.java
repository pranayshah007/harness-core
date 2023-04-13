/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ipallowlist.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ipallowlist.IPAllowlistResourceUtils;
import io.harness.ipallowlist.service.IPAllowlistService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ipallowlist.spring.IPAllowlistRepository;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class IPAllowlistServiceImpl implements IPAllowlistService {
  private final IPAllowlistRepository ipAllowlistRepository;
  OutboxService outboxService;
  TransactionTemplate transactionTemplate;

  private final IPAllowlistResourceUtils ipAllowlistResourceUtil;

  @Inject
  public IPAllowlistServiceImpl(IPAllowlistRepository ipAllowlistRepository, OutboxService outboxService,
      TransactionTemplate transactionTemplate, IPAllowlistResourceUtils ipAllowlistResourceUtil) {
    this.ipAllowlistRepository = ipAllowlistRepository;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
    this.ipAllowlistResourceUtil = ipAllowlistResourceUtil;
  }
}

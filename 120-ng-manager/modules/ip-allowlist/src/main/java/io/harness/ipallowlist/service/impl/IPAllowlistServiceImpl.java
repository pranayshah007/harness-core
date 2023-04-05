/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ipallowlist.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ipallowlist.entity.IPAllowlistEntity;
import io.harness.ipallowlist.service.IPAllowlistService;
import io.harness.repositories.ipallowlist.spring.IPAllowlistRepository;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.PL)
public class IPAllowlistServiceImpl implements IPAllowlistService {
  @Inject IPAllowlistRepository ipAllowlistRepository;
  @Override
  public IPAllowlistEntity create(IPAllowlistEntity ipAllowlistEntity) {
    ipAllowlistRepository.save(ipAllowlistEntity);
    return ipAllowlistEntity;
  }
}

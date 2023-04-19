/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ipallowlist.service;

import io.harness.ipallowlist.dto.IPAllowlistFilterDTO;
import io.harness.ipallowlist.entity.IPAllowlistEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IPAllowlistService {
  IPAllowlistEntity create(IPAllowlistEntity ipAllowlistEntity);
  IPAllowlistEntity get(String accountIdentifier, String identifier);
  IPAllowlistEntity update(String ipConfigIdentifier, IPAllowlistEntity ipAllowlistEntity);

  boolean delete(String accountIdentifier, String identifier);

  boolean validateUniqueness(String accountIdentifier, String identifier);

  Page<IPAllowlistEntity> list(String harnessAccount, Pageable pageable, IPAllowlistFilterDTO ipAllowlistFilterDTO);
}

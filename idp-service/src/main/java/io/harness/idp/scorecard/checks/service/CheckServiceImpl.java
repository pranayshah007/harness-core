/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.mappers.CheckDetailsMapper;
import io.harness.idp.scorecard.checks.mappers.CheckMapper;
import io.harness.idp.scorecard.checks.repositories.CheckRepository;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.CheckListItem;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class CheckServiceImpl implements CheckService {
  private final CheckRepository checkRepository;
  @Inject
  public CheckServiceImpl(CheckRepository checkRepository) {
    this.checkRepository = checkRepository;
  }

  @Override
  public void createCheck(CheckDetails checkDetails, String accountIdentifier) {
    checkRepository.save(CheckDetailsMapper.fromDTO(checkDetails, accountIdentifier));
  }

  @Override
  public void updateCheck(CheckDetails checkDetails, String accountIdentifier) {
    checkRepository.update(CheckDetailsMapper.fromDTO(checkDetails, accountIdentifier));
  }

  @Override
  public List<CheckListItem> getChecksByAccountId(boolean custom, String accountIdentifier) {
    List<CheckEntity> entities = checkRepository.findByAccountIdentifierAndIsCustom(accountIdentifier, custom);
    List<CheckListItem> checks = new ArrayList<>();
    entities.forEach(entity -> checks.add(CheckMapper.toDTO(entity)));
    return checks;
  }

  @Override
  public CheckDetails getCheckDetails(String accountIdentifier, String identifier) {
    CheckEntity checkEntity = checkRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    return CheckDetailsMapper.toDTO(checkEntity);
  }

  @Override
  public List<CheckEntity> getChecksByAccountIdAndIdentifiers(String accountIdentifier, List<String> identifiers) {
    return checkRepository.findByAccountIdentifierAndIdentifierIn(accountIdentifier, identifiers);
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dao.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dao.NGEncryptedDataDao;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.repositories.NGEncryptedDataRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NGEncryptedDaoServiceImpl implements NGEncryptedDataDao {
  private final NGEncryptedDataRepository encryptedDataRepository;

  @Override
  public NGEncryptedData save(NGEncryptedData ngEncryptedData) {
    return encryptedDataRepository.save(ngEncryptedData);
  }

  @Override
  public NGEncryptedData get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Optional<NGEncryptedData> ngEncryptedData =
        encryptedDataRepository.findNGEncryptedDataByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return ngEncryptedData.orElse(null);
  }

  @Override
  public boolean delete(String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return encryptedDataRepository
               .deleteNGEncryptedDataByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
                   accountIdentifier, orgIdentifier, projectIdentifier, identifier)
        > 0;
  }

  @Override
  public NGEncryptedData hardDelete(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return encryptedDataRepository
        .hardDeleteNGEncryptedDataByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }
}

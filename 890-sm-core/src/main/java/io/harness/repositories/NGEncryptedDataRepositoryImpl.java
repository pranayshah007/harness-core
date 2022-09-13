/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.ng.core.entities.NGEncryptedData.NGEncryptedDataKeys;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PL)
@HarnessRepo
public class NGEncryptedDataRepositoryImpl implements NGEncryptedDataRepository {
  @Inject MongoTemplate mongoTemplate;

  private final String NG_ENCRYPTED_DATA_COLLECTION_NAME = "ngEncryptedRecords";

  @Override
  public Optional<NGEncryptedData>
  findNGEncryptedDataByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Criteria criteria = Criteria.where(NGEncryptedDataKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(NGEncryptedDataKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(NGEncryptedDataKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(NGEncryptedDataKeys.identifier)
                            .is(identifier);
    Query query = new Query();
    query.addCriteria(criteria);
    List<NGEncryptedData> result = mongoTemplate.find(query, NGEncryptedData.class, getCollectionName());
    return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
  }

  @Override
  public Long deleteNGEncryptedDataByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Criteria criteria = Criteria.where(NGEncryptedDataKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(NGEncryptedDataKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(NGEncryptedDataKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(NGEncryptedDataKeys.identifier)
                            .is(identifier);
    Query query = new Query();
    query.addCriteria(criteria);
    return mongoTemplate.remove(query, NGEncryptedData.class, getCollectionName()).getDeletedCount();
  }

  @Override
  public String getCollectionName() {
    return NG_ENCRYPTED_DATA_COLLECTION_NAME;
  }

  @Override
  public NGEncryptedData save(NGEncryptedData ngEncryptedData) {
    return mongoTemplate.save(ngEncryptedData, getCollectionName());
  }
}

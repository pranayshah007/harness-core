/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.polling;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.PollingDocument.PollingDocumentKeys;
import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.PollingType;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDC)
public class PollingRepositoryCustomImpl implements PollingRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public PollingDocument addSubscribersToExistingPollingDoc(String accountId, String orgId, String projectId,
      PollingType pollingType, PollingInfo pollingInfo, List<String> signatures,
      Map<String, List<String>> signaturesLock) {
    Query query = getQuery(accountId, orgId, projectId, pollingType, pollingInfo);
    Update update = new Update().addToSet(PollingDocumentKeys.signatures).each(signatures);
    if (isNotEmpty(signaturesLock)) {
      for (Map.Entry<String, List<String>> signatureLock : signaturesLock.entrySet()) {
        update.set(PollingDocumentKeys.signaturesLock + "." + signatureLock.getKey(), signatureLock.getValue());
      }
    }
    return mongoTemplate.findAndModify(query, update, PollingDocument.class);
  }

  @Override
  public PollingDocument addSubscribersToExistingPollingDoc(
      String accountId, String uuid, List<String> signatures, Map<String, List<String>> signaturesLock) {
    Query query = getQuery(accountId, uuid);
    Update update = new Update().addToSet(PollingDocumentKeys.signatures).each(signatures);
    if (isNotEmpty(signaturesLock)) {
      for (Map.Entry<String, List<String>> signatureLock : signaturesLock.entrySet()) {
        update.set(PollingDocumentKeys.signaturesLock + "." + signatureLock.getKey(), signatureLock.getValue());
      }
    }
    return mongoTemplate.findAndModify(query, update, PollingDocument.class);
  }

  public Query getQuery(
      String accountId, String orgId, String projectId, PollingType pollingType, PollingInfo pollingInfo) {
    return new Query().addCriteria(new Criteria()
                                       .and(PollingDocumentKeys.accountId)
                                       .is(accountId)
                                       .and(PollingDocumentKeys.orgIdentifier)
                                       .is(orgId)
                                       .and(PollingDocumentKeys.projectIdentifier)
                                       .is(projectId)
                                       .and(PollingDocumentKeys.pollingType)
                                       .is(pollingType)
                                       .and(PollingDocumentKeys.pollingInfo)
                                       .is(pollingInfo));
  }

  public Query getQuery(String accountId, String uuId) {
    return new Query().addCriteria(
        new Criteria().and(PollingDocumentKeys.accountId).is(accountId).and(PollingDocumentKeys.uuid).is(uuId));
  }

  @Override
  public PollingDocument deleteDocumentIfOnlySubscriber(String accountId, String orgId, String projectId,
      PollingType pollingType, PollingInfo pollingInfo, List<String> signatures) {
    Query query = new Query().addCriteria(new Criteria()
                                              .and(PollingDocumentKeys.accountId)
                                              .is(accountId)
                                              .and(PollingDocumentKeys.orgIdentifier)
                                              .is(orgId)
                                              .and(PollingDocumentKeys.projectIdentifier)
                                              .is(projectId)
                                              .and(PollingDocumentKeys.pollingType)
                                              .is(pollingType)
                                              .and(PollingDocumentKeys.pollingInfo)
                                              .is(pollingInfo)
                                              .and(PollingDocumentKeys.signatures)
                                              .is(signatures));
    return mongoTemplate.findAndRemove(query, PollingDocument.class);
  }

  @Override
  public PollingDocument removeDocumentIfOnlySubscriber(
      String accountId, String pollingDocId, List<String> signatures) {
    Criteria criteria = new Criteria()
                            .and(PollingDocumentKeys.accountId)
                            .is(accountId)
                            .and(PollingDocumentKeys.signatures)
                            .is(signatures);
    if (pollingDocId != null) {
      /* pollingDocId may be empty in the case of unsubscription for MultiRegionArtifact triggers.
       So we add pollingDocId to the query only if it is not null. */
      criteria.and(PollingDocumentKeys.uuid).is(pollingDocId);
    }
    Query query = new Query().addCriteria(criteria);
    return mongoTemplate.findAndRemove(query, PollingDocument.class);
  }

  @Override
  public PollingDocument deleteSubscribersFromExistingPollingDoc(String accountId, String orgId, String projectId,
      PollingType pollingType, PollingInfo pollingInfo, List<String> signatures) {
    Object[] signatureList = signatures.toArray();
    Query query = getQuery(accountId, orgId, projectId, pollingType, pollingInfo);
    Update update = new Update().pullAll(PollingDocumentKeys.signatures, signatureList);
    for (String signature : signatures) {
      update.unset(PollingDocumentKeys.signaturesLock + "." + signature);
    }
    return mongoTemplate.findAndModify(query, update, PollingDocument.class);
  }

  @Override
  public PollingDocument removeSubscribersFromExistingPollingDoc(
      String accountId, String uuId, List<String> signatures) {
    Object[] signatureList = signatures.toArray();
    Query query;
    if (uuId == null) {
      /* If uuId is null, we remove based on accountId and signatures only.
      This case happens for MultiRegionArtifact triggers. */
      Criteria criteria = new Criteria()
                              .and(PollingDocumentKeys.accountId)
                              .is(accountId)
                              .and(PollingDocumentKeys.signatures)
                              .all(signatures);
      query = new Query().addCriteria(criteria);
    } else {
      query = getQuery(accountId, uuId);
    }
    Update update = new Update().pullAll(PollingDocumentKeys.signatures, signatureList);
    for (String signature : signatures) {
      update.unset(PollingDocumentKeys.signaturesLock + "." + signature);
    }
    return mongoTemplate.findAndModify(query, update, PollingDocument.class);
  }

  @Override
  public UpdateResult updateSelectiveEntity(String accountId, String pollDocId, String key, Object value) {
    Query query = new Query().addCriteria(
        new Criteria().and(PollingDocumentKeys.accountId).is(accountId).and(PollingDocumentKeys.uuid).is(pollDocId));
    Update update = new Update().set(key, value);
    long timeOfUpdate = System.currentTimeMillis();
    update.set(PollingDocumentKeys.lastModifiedAt, timeOfUpdate);
    return mongoTemplate.updateFirst(query, update, PollingDocument.class);
  }

  @Override
  public PollingDocument findByUuidAndAccountIdAndSignature(
      String pollingDocId, String accountId, List<String> signatures) {
    Query query = new Query().addCriteria(new Criteria()
                                              .and(PollingDocumentKeys.accountId)
                                              .is(accountId)
                                              .and(PollingDocumentKeys.uuid)
                                              .is(pollingDocId)
                                              .and(PollingDocumentKeys.signatures)
                                              .in(signatures));
    return mongoTemplate.findOne(query, PollingDocument.class);
  }

  @Override
  public DeleteResult deleteAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, PollingDocument.class);
  }

  @Override
  public List<PollingDocument> findManyByUuidsAndAccountId(List<String> pollingDocIds, String accountId) {
    Query query = new Query().addCriteria(new Criteria()
                                              .and(PollingDocumentKeys.accountId)
                                              .is(accountId)
                                              .and(PollingDocumentKeys.uuid)
                                              .in(pollingDocIds));
    return mongoTemplate.find(query, PollingDocument.class);
  }

  @Override
  public List<PollingDocument> findUuidsBySignaturesAndAccountId(List<String> signatures, String accountId) {
    Query query = new Query().addCriteria(new Criteria()
                                              .and(PollingDocumentKeys.accountId)
                                              .is(accountId)
                                              .and(PollingDocumentKeys.signatures)
                                              .in(signatures));
    return mongoTemplate.find(query, PollingDocument.class);
  }
}

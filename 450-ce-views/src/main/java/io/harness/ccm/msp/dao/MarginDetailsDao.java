/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.msp.dao;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.msp.entities.AmountDetails;
import io.harness.ccm.msp.entities.MarginDetails;
import io.harness.ccm.msp.entities.MarginDetails.MarginDetailsKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class MarginDetailsDao {
  @Inject private HPersistence hPersistence;

  public String save(MarginDetails marginDetails) {
    return hPersistence.save(marginDetails);
  }

  public MarginDetails get(String uuid) {
    return hPersistence.get(MarginDetails.class, uuid);
  }

  public MarginDetails getMarginDetailsForAccount(String mspAccountId, String accountId) {
    return hPersistence.createQuery(MarginDetails.class)
        .field(MarginDetailsKeys.accountId)
        .equal(accountId)
        .field(MarginDetailsKeys.mspAccountId)
        .equal(mspAccountId)
        .first();
  }

  public List<MarginDetails> list(String mspAccountId) {
    return hPersistence.createQuery(MarginDetails.class)
        .field(MarginDetailsKeys.mspAccountId)
        .equal(mspAccountId)
        .asList();
  }

  public MarginDetails update(MarginDetails marginDetails) {
    Query<MarginDetails> query = hPersistence.createQuery(MarginDetails.class)
                                     .field(MarginDetailsKeys.accountId)
                                     .equal(marginDetails.getAccountId())
                                     .field(MarginDetailsKeys.mspAccountId)
                                     .equal(marginDetails.getMspAccountId());

    hPersistence.update(query, getUpdateOperations(marginDetails));
    return marginDetails;
  }

  private UpdateOperations<MarginDetails> getUpdateOperations(MarginDetails marginDetails) {
    UpdateOperations<MarginDetails> updateOperations = hPersistence.createUpdateOperations(MarginDetails.class);

    if (marginDetails.getMarginRules() != null) {
      setUnsetUpdateOperations(updateOperations, MarginDetailsKeys.marginRules, marginDetails.getMarginRules());
    }
    if (marginDetails.getMarkupAmountDetails() != null) {
      setUnsetUpdateOperations(
          updateOperations, MarginDetailsKeys.markupAmountDetails, marginDetails.getMarkupAmountDetails());
    }
    if (marginDetails.getTotalSpendDetails() != null) {
      setUnsetUpdateOperations(
          updateOperations, MarginDetailsKeys.totalSpendDetails, marginDetails.getTotalSpendDetails());
    }

    return updateOperations;
  }

  private void setUnsetUpdateOperations(UpdateOperations<MarginDetails> updateOperations, String key, Object value) {
    if (Objects.nonNull(value)) {
      updateOperations.set(key, value);
    } else {
      updateOperations.unset(key);
    }
  }

  public void updateMarkupAndTotalSpend(
      String mspAccountId, String accountId, AmountDetails markupAmountDetails, AmountDetails totalSpendDetails) {
    Query<MarginDetails> query = hPersistence.createQuery(MarginDetails.class)
                                     .field(MarginDetailsKeys.accountId)
                                     .equal(accountId)
                                     .field(MarginDetailsKeys.mspAccountId)
                                     .equal(mspAccountId);
    UpdateOperations<MarginDetails> updateOperations = hPersistence.createUpdateOperations(MarginDetails.class);
    setUnsetUpdateOperations(updateOperations, MarginDetailsKeys.markupAmountDetails, markupAmountDetails);
    setUnsetUpdateOperations(updateOperations, MarginDetailsKeys.totalSpendDetails, totalSpendDetails);
    hPersistence.update(query, updateOperations);
  }

  public MarginDetails unsetMarginRules(String uuid) {
    Query<MarginDetails> query =
        hPersistence.createQuery(MarginDetails.class).field(MarginDetailsKeys.uuid).equal(uuid);
    UpdateOperations<MarginDetails> updateOperations = hPersistence.createUpdateOperations(MarginDetails.class);
    setUnsetUpdateOperations(updateOperations, MarginDetailsKeys.marginRules, null);
    hPersistence.update(query, updateOperations);
    return get(uuid);
  }

  public boolean delete(String accountId) {
    Query<MarginDetails> query =
        hPersistence.createQuery(MarginDetails.class).field(MarginDetailsKeys.accountId).equal(accountId);
    return hPersistence.delete(query);
  }
}
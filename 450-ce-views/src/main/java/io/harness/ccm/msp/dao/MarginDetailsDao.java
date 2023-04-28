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
                                     .field(MarginDetailsKeys.uuid)
                                     .equal(marginDetails.getUuid());

    hPersistence.update(query, getUpdateOperations(marginDetails));
    return marginDetails;
  }

  private UpdateOperations<MarginDetails> getUpdateOperations(MarginDetails marginDetails) {
    UpdateOperations<MarginDetails> updateOperations = hPersistence.createUpdateOperations(MarginDetails.class);

    setUnsetUpdateOperations(updateOperations, MarginDetailsKeys.marginRules, marginDetails.getMarginRules());
    setUnsetUpdateOperations(
        updateOperations, MarginDetailsKeys.markupAmountDetails, marginDetails.getMarkupAmountDetails());
    setUnsetUpdateOperations(
        updateOperations, MarginDetailsKeys.totalSpendDetails, marginDetails.getTotalSpendDetails());

    return updateOperations;
  }

  private void setUnsetUpdateOperations(UpdateOperations<MarginDetails> updateOperations, String key, Object value) {
    if (Objects.nonNull(value)) {
      updateOperations.set(key, value);
    } else {
      updateOperations.unset(key);
    }
  }

  public void updateMarkupAmount(String uuid, String accountId, AmountDetails markupAmountDetails) {
    Query<MarginDetails> query = hPersistence.createQuery(MarginDetails.class)
                                     .field(MarginDetailsKeys.uuid)
                                     .equal(uuid)
                                     .field(MarginDetailsKeys.accountId)
                                     .equal(accountId);
    UpdateOperations<MarginDetails> updateOperations = hPersistence.createUpdateOperations(MarginDetails.class);
    setUnsetUpdateOperations(updateOperations, MarginDetailsKeys.markupAmountDetails, markupAmountDetails);
    hPersistence.update(query, updateOperations);
  }

  public void updateTotalSpend(String uuid, String accountId, AmountDetails totalSpendDetails) {
    Query<MarginDetails> query = hPersistence.createQuery(MarginDetails.class)
                                     .field(MarginDetailsKeys.uuid)
                                     .equal(uuid)
                                     .field(MarginDetailsKeys.accountId)
                                     .equal(accountId);
    UpdateOperations<MarginDetails> updateOperations = hPersistence.createUpdateOperations(MarginDetails.class);
    setUnsetUpdateOperations(updateOperations, MarginDetailsKeys.totalSpendDetails, totalSpendDetails);
    hPersistence.update(query, updateOperations);
  }

  public MarginDetails unsetMarginRules(String uuid, String accountId) {
    Query<MarginDetails> query =
        hPersistence.createQuery(MarginDetails.class).field(MarginDetailsKeys.uuid).equal(uuid);
    UpdateOperations<MarginDetails> updateOperations = hPersistence.createUpdateOperations(MarginDetails.class);
    setUnsetUpdateOperations(updateOperations, MarginDetailsKeys.marginRules, null);
    hPersistence.update(query, updateOperations);
    return get(uuid);
  }

  public boolean delete(String uuid, String accountId) {
    Query<MarginDetails> query = hPersistence.createQuery(MarginDetails.class)
                                     .field(MarginDetailsKeys.accountId)
                                     .equal(accountId)
                                     .field(MarginDetailsKeys.uuid)
                                     .equal(uuid);
    return hPersistence.delete(query);
  }
}

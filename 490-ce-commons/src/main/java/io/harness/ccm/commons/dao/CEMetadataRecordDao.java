/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao;

import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.ccm.commons.beans.currency.CurrencyPreferenceRecord;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord.CEMetadataRecordKeys;
import io.harness.ccm.currency.Currency;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.Objects;

public class CEMetadataRecordDao {
  private final HPersistence persistence;

  @Inject
  public CEMetadataRecordDao(HPersistence persistence) {
    this.persistence = persistence;
  }

  public CEMetadataRecord upsert(CEMetadataRecord ceMetadataRecord) {
    Query<CEMetadataRecord> query = persistence.createQuery(CEMetadataRecord.class, excludeValidate)
                                        .filter(CEMetadataRecordKeys.accountId, ceMetadataRecord.getAccountId());

    UpdateOperations<CEMetadataRecord> updateOperations = persistence.createUpdateOperations(CEMetadataRecord.class);

    if (null != ceMetadataRecord.getClusterDataConfigured()) {
      updateOperations.set(CEMetadataRecordKeys.clusterDataConfigured, ceMetadataRecord.getClusterDataConfigured());
    }

    if (null != ceMetadataRecord.getClusterConnectorConfigured()) {
      updateOperations.set(
          CEMetadataRecordKeys.clusterConnectorConfigured, ceMetadataRecord.getClusterConnectorConfigured());
    }

    if (null != ceMetadataRecord.getAwsConnectorConfigured()) {
      updateOperations.set(CEMetadataRecordKeys.awsConnectorConfigured, ceMetadataRecord.getAwsConnectorConfigured());
    }

    if (null != ceMetadataRecord.getAwsDataPresent()) {
      updateOperations.set(CEMetadataRecordKeys.awsDataPresent, ceMetadataRecord.getAwsDataPresent());
    }

    if (null != ceMetadataRecord.getGcpConnectorConfigured()) {
      updateOperations.set(CEMetadataRecordKeys.gcpConnectorConfigured, ceMetadataRecord.getGcpConnectorConfigured());
    }

    if (null != ceMetadataRecord.getGcpDataPresent()) {
      updateOperations.set(CEMetadataRecordKeys.gcpDataPresent, ceMetadataRecord.getGcpDataPresent());
    }

    if (null != ceMetadataRecord.getAzureConnectorConfigured()) {
      updateOperations.set(
          CEMetadataRecordKeys.azureConnectorConfigured, ceMetadataRecord.getAzureConnectorConfigured());
    }

    if (null != ceMetadataRecord.getAzureDataPresent()) {
      updateOperations.set(CEMetadataRecordKeys.azureDataPresent, ceMetadataRecord.getAzureDataPresent());
    }

    if (null != ceMetadataRecord.getSegmentDataReadyEventSent()) {
      updateOperations.set(
          CEMetadataRecordKeys.segmentDataReadyEventSent, ceMetadataRecord.getSegmentDataReadyEventSent());
    }

    if (null != ceMetadataRecord.getSegmentModuleInterfaceLoadedEventSent()) {
      updateOperations.set(CEMetadataRecordKeys.segmentModuleInterfaceLoadedEventSent,
          ceMetadataRecord.getSegmentModuleInterfaceLoadedEventSent());
    }

    if (null != ceMetadataRecord.getApplicationDataPresent()) {
      updateOperations.set(CEMetadataRecordKeys.applicationDataPresent, ceMetadataRecord.getApplicationDataPresent());
    }

    if (null != ceMetadataRecord.getDataGeneratedForCloudProvider()) {
      updateOperations.set(
          CEMetadataRecordKeys.dataGeneratedForCloudProvider, ceMetadataRecord.getDataGeneratedForCloudProvider());
    }

    FindAndModifyOptions findAndModifyOptions = new FindAndModifyOptions().upsert(true).returnNew(true);
    return persistence.upsert(query, updateOperations, findAndModifyOptions);
  }

  public void updateCurrencyPreferenceOnce(String accountId, CurrencyPreferenceRecord currencyPreference) {
    Query<CEMetadataRecord> query =
        persistence.createQuery(CEMetadataRecord.class, excludeValidate)
            .filter(CEMetadataRecordKeys.accountId, accountId)
            .filter(CEMetadataRecordKeys.currencyPreference, new BasicDBObject("$exists", false));
    UpdateOperations<CEMetadataRecord> updateOperations = persistence.createUpdateOperations(CEMetadataRecord.class);
    if (Objects.nonNull(currencyPreference)) {
      updateOperations.set(CEMetadataRecordKeys.currencyPreference, currencyPreference);
    }
    persistence.update(query, updateOperations);
  }

  public CEMetadataRecord getByAccountId(String accountId) {
    return persistence.createQuery(CEMetadataRecord.class).field(CEMetadataRecordKeys.accountId).equal(accountId).get();
  }

  public Currency getDestinationCurrency(String accountId) {
    Currency currency = Currency.NONE;
    if (Objects.nonNull(accountId)) {
      final CEMetadataRecord ceMetadataRecord = getByAccountId(accountId);
      if (Objects.nonNull(ceMetadataRecord.getCurrencyPreference())
          && Objects.nonNull(ceMetadataRecord.getCurrencyPreference().getDestinationCurrency())) {
        currency = Currency.valueOf(ceMetadataRecord.getCurrencyPreference().getDestinationCurrency());
      }
    }
    return currency;
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.communication;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.communication.entities.CECommunications;
import io.harness.ccm.communication.entities.CECommunications.CECommunicationsKeys;
import io.harness.ccm.communication.entities.CommunicationType;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;

@OwnedBy(CE)
@TargetModule(HarnessModule._490_CE_COMMONS)
public class CECommunicationsDao {
  @Inject private HPersistence persistence;

  public String save(CECommunications entry) {
    return persistence.save(entry);
  }

  public String save(String accountId, String emailId, CommunicationType type, boolean enable) {
    CECommunications entry =
        CECommunications.builder().accountId(accountId).emailId(emailId).type(type).enabled(enable).build();
    return save(entry);
  }

  public CECommunications get(String uuid) {
    Query<CECommunications> query =
        persistence.createQuery(CECommunications.class).field(CECommunicationsKeys.uuid).equal(uuid);
    return query.get();
  }

  public CECommunications get(String accountId, String emailId, CommunicationType type) {
    Query<CECommunications> query = persistence.createQuery(CECommunications.class)
                                        .field(CECommunicationsKeys.accountId)
                                        .equal(accountId)
                                        .field(CECommunicationsKeys.emailId)
                                        .equal(emailId)
                                        .field(CECommunicationsKeys.type)
                                        .equal(type);
    return query.get();
  }

  public List<CECommunications> list(String accountId, String emailId) {
    Query<CECommunications> query = persistence.createQuery(CECommunications.class)
                                        .field(CECommunicationsKeys.accountId)
                                        .equal(accountId)
                                        .field(CECommunicationsKeys.emailId)
                                        .equal(emailId);
    return query.asList(new FindOptions());
  }

  public void update(String accountId, String emailId, CommunicationType type, boolean enable) {
    Query query = persistence.createQuery(CECommunications.class)
                      .field(CECommunicationsKeys.accountId)
                      .equal(accountId)
                      .field(CECommunicationsKeys.emailId)
                      .equal(emailId)
                      .field(CECommunicationsKeys.type)
                      .equal(type);
    UpdateOperations<CECommunications> updateOperations =
        persistence.createUpdateOperations(CECommunications.class).set(CECommunicationsKeys.enabled, enable);
    persistence.update(query, updateOperations);
  }

  public boolean delete(String uuid) {
    return persistence.delete(CECommunications.class, uuid);
  }

  public List<CECommunications> getEnabledEntries(String accountId, CommunicationType type) {
    Query<CECommunications> query = persistence.createQuery(CECommunications.class)
                                        .field(CECommunicationsKeys.accountId)
                                        .equal(accountId)
                                        .field(CECommunicationsKeys.enabled)
                                        .equal(true)
                                        .field(CECommunicationsKeys.type)
                                        .equal(type);
    return query.asList(new FindOptions());
  }

  public List<CECommunications> getEntriesEnabledViaEmail(String accountId) {
    Query<CECommunications> query = persistence.createQuery(CECommunications.class)
                                        .field(CECommunicationsKeys.accountId)
                                        .equal(accountId)
                                        .field(CECommunicationsKeys.selfEnabled)
                                        .equal(false);
    return query.asList(new FindOptions());
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.CEView.CEViewKeys;
import io.harness.ccm.views.entities.ViewState;
import io.harness.ccm.views.entities.ViewType;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@Singleton
public class CEViewDao {
  @Inject private HPersistence hPersistence;

  public boolean save(CEView ceView) {
    return hPersistence.save(ceView) != null;
  }

  public CEView update(CEView ceView) {
    Query query = hPersistence.createQuery(CEView.class)
                      .field(CEViewKeys.accountId)
                      .equal(ceView.getAccountId())
                      .field(CEViewKeys.uuid)
                      .equal(ceView.getUuid());
    UpdateOperations<CEView> updateOperations = hPersistence.createUpdateOperations(CEView.class)
                                                    .set(CEViewKeys.viewVersion, ceView.getViewVersion())
                                                    .set(CEViewKeys.name, ceView.getName())
                                                    .set(CEViewKeys.viewTimeRange, ceView.getViewTimeRange())
                                                    .set(CEViewKeys.viewRules, ceView.getViewRules())
                                                    .set(CEViewKeys.viewVisualization, ceView.getViewVisualization())
                                                    .set(CEViewKeys.viewPreferences, ceView.getViewPreferences())
                                                    .set(CEViewKeys.viewType, ceView.getViewType())
                                                    .set(CEViewKeys.viewState, ViewState.COMPLETED)
                                                    .set(CEViewKeys.dataSources, ceView.getDataSources());
    hPersistence.update(query, updateOperations);
    return (CEView) query.asList().get(0);
  }

  public CEView updateTotalCost(String viewId, String accountId, double totalCost) {
    Query query = hPersistence.createQuery(CEView.class)
                      .field(CEViewKeys.accountId)
                      .equal(accountId)
                      .field(CEViewKeys.uuid)
                      .equal(viewId);

    UpdateOperations<CEView> updateOperations =
        hPersistence.createUpdateOperations(CEView.class).set(CEViewKeys.totalCost, totalCost);
    hPersistence.update(query, updateOperations);
    return (CEView) query.asList().get(0);
  }

  public boolean delete(String uuid, String accountId) {
    Query query = hPersistence.createQuery(CEView.class)
                      .field(CEViewKeys.accountId)
                      .equal(accountId)
                      .field(CEViewKeys.uuid)
                      .equal(uuid);
    return hPersistence.delete(query);
  }

  public CEView get(String uuid) {
    return hPersistence.createQuery(CEView.class, excludeValidate).filter(CEViewKeys.uuid, uuid).get();
  }

  public List<CEView> list(String accountId, List<String> perspectiveIds) {
    return hPersistence.createQuery(CEView.class)
        .field(CEViewKeys.accountId)
        .equal(accountId)
        .field(CEViewKeys.uuid)
        .in(perspectiveIds)
        .asList();
  }

  public CEView findByName(String accountId, String name) {
    return hPersistence.createQuery(CEView.class)
        .filter(CEViewKeys.accountId, accountId)
        .filter(CEViewKeys.name, name)
        .get();
  }

  public List<CEView> findByAccountId(String accountId) {
    return hPersistence.createQuery(CEView.class).filter(CEViewKeys.accountId, accountId).asList();
  }

  public List<CEView> findByAccountIdAndState(String accountId, ViewState viewState) {
    return hPersistence.createQuery(CEView.class)
        .filter(CEViewKeys.accountId, accountId)
        .filter(CEViewKeys.viewState, viewState)
        .asList();
  }

  public List<CEView> findByAccountIdAndType(String accountId, ViewType viewType) {
    return hPersistence.createQuery(CEView.class)
        .filter(CEViewKeys.accountId, accountId)
        .filter(CEViewKeys.viewType, viewType)
        .asList();
  }

  public Long findCountByAccountIdAndType(String accountId, ViewType viewType) {
    return hPersistence.createQuery(CEView.class)
        .filter(CEViewKeys.accountId, accountId)
        .filter(CEViewKeys.viewType, viewType)
        .count();
  }
}

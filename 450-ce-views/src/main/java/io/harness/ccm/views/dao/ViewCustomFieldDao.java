/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import io.harness.ccm.views.entities.ViewCustomField;
import io.harness.ccm.views.entities.ViewCustomField.ViewCustomFieldKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ViewCustomFieldDao {
  @Inject private HPersistence hPersistence;

  public boolean save(ViewCustomField viewCustomField) {
    return hPersistence.save(viewCustomField) != null;
  }

  public ViewCustomField findByName(String accountId, String viewId, String name) {
    return hPersistence.createQuery(ViewCustomField.class)
        .filter(ViewCustomFieldKeys.accountId, accountId)
        .filter(ViewCustomFieldKeys.viewId, viewId)
        .filter(ViewCustomFieldKeys.name, name)
        .get();
  }

  public List<ViewCustomField> findByAccountId(String accountId) {
    return hPersistence.createQuery(ViewCustomField.class).filter(ViewCustomFieldKeys.accountId, accountId).asList();
  }

  public List<ViewCustomField> findByViewId(String viewId, String accountId) {
    return hPersistence.createQuery(ViewCustomField.class)
        .filter(ViewCustomFieldKeys.accountId, accountId)
        .filter(ViewCustomFieldKeys.viewId, viewId)
        .asList();
  }

  public ViewCustomField getById(String uuid) {
    return hPersistence.createQuery(ViewCustomField.class).field(ViewCustomFieldKeys.uuid).equal(uuid).get();
  }

  public boolean delete(String uuid, String accountId) {
    Query query = hPersistence.createQuery(ViewCustomField.class)
                      .field(ViewCustomFieldKeys.accountId)
                      .equal(accountId)
                      .field(ViewCustomFieldKeys.uuid)
                      .equal(uuid);
    return hPersistence.delete(query);
  }

  public boolean deleteByViewId(String viewId, String accountId) {
    Query query = hPersistence.createQuery(ViewCustomField.class)
                      .field(ViewCustomFieldKeys.accountId)
                      .equal(accountId)
                      .field(ViewCustomFieldKeys.viewId)
                      .equal(viewId);
    return hPersistence.delete(query);
  }

  public ViewCustomField update(ViewCustomField viewCustomField) {
    Query query = hPersistence.createQuery(ViewCustomField.class)
                      .field(ViewCustomFieldKeys.accountId)
                      .equal(viewCustomField.getAccountId())
                      .field(ViewCustomFieldKeys.uuid)
                      .equal(viewCustomField.getUuid());
    UpdateOperations<ViewCustomField> updateOperations =
        hPersistence.createUpdateOperations(ViewCustomField.class)
            .set(ViewCustomFieldKeys.name, viewCustomField.getName())
            .set(ViewCustomFieldKeys.viewFields, viewCustomField.getViewFields())
            .set(ViewCustomFieldKeys.sqlFormula, viewCustomField.getSqlFormula())
            .set(ViewCustomFieldKeys.displayFormula, viewCustomField.getDisplayFormula())
            .set(ViewCustomFieldKeys.description, viewCustomField.getDescription())
            .set(ViewCustomFieldKeys.userDefinedExpression, viewCustomField.getUserDefinedExpression());
    hPersistence.update(query, updateOperations);
    log.info(query.toString());
    return (ViewCustomField) query.asList().get(0);
  }
}

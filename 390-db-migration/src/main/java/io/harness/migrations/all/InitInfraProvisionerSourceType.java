/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Base;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.Key;
import software.wings.service.intfc.InfrastructureProvider;


@Slf4j
public class InitInfraProvisionerSourceType implements Migration {
  @Inject
  protected WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    try (HIterator<Account> accounts =
                 new HIterator<>(wingsPersistence.createQuery(Account.class).filter("_id":"").fetch())) {
      while (accounts.hasNext()) {
        final Account account = accounts.next();

        List<Key<InfrastructureProvisioner>> infraProvisionersKeyIdList = wingsPersistence.createQuery(InfrastructureProvisioner.class)
                .filter(InfrastructureProvisioner.ACCOUNT_ID_KEY, account.getUuid())
                .filter(InfrastructureProvisioner.INFRASTRUCTURE_PROVISIONER_TYPE_KEY, "TERRAFORM")
                .asKeyList();
        if (isNotEmpty(infraProvisionersKeyIdList)) {
          Set<String> infraProvisionersIdSet =
                  infraProvisionersKeyIdList.stream().map(infrastructureProvisionerKey -> (String) infrastructureProvisionerKey.getId()).collect(Collectors.toSet());
          bulkSetTerraformSource(account.getUuid(), InfrastructureProvisioner.class, infraProvisionersIdSet);
        }
      }
    }
  }

  protected <T extends Base> void bulkSetTerraformSource(String accountId, Class<T> clazz, Set<String> infrastructureProvisionersIdSet) {
    final DBCollection collection = wingsPersistence.getCollection(clazz);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();

    int i = 1;
    try (HIterator<InfrastructureProvisioner> infrastructureProvisioners = new HIterator<>(wingsPersistence.createQuery(InfrastructureProvisioner.class)
            .field("sourceType")
            .doesNotExist()
            .field("_id")
            .in(infrastructureProvisionersIdSet)
            .fetch())) {
      while (infrastructureProvisioners.hasNext()) {
        final InfrastructureProvisioner terraformProvisioner = infrastructureProvisioners.next();
        terraformProvisioner.setDescription("abc");
      }
    }
  }
}

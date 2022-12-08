/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Base;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.TerraformSourceType;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.InfrastructureProvider;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.Key;

@OwnedBy(CDP)
@Slf4j
public class InitTerraformProvisionersSourceType implements Migration {
  @Inject protected WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    try (HIterator<Account> accounts =
             new HIterator<>(wingsPersistence.createQuery(Account.class, excludeAuthority).fetch())) {
      while (accounts.hasNext()) {
        final Account account = accounts.next();

        List<Key<InfrastructureProvisioner>> infraProvisionersKeyIdList =
            wingsPersistence.createQuery(InfrastructureProvisioner.class)
                .filter(InfrastructureProvisioner.ACCOUNT_ID_KEY, account.getUuid())
                .filter(InfrastructureProvisioner.INFRASTRUCTURE_PROVISIONER_TYPE_KEY, "TERRAFORM")
                .asKeyList();
        if (isNotEmpty(infraProvisionersKeyIdList)) {
          Set<String> infraProvisionersIdSet =
              infraProvisionersKeyIdList.stream()
                  .map(infrastructureProvisionerKey -> (String) infrastructureProvisionerKey.getId())
                  .collect(Collectors.toSet());
          bulkSetTerraformSource(account.getUuid(), InfrastructureProvisioner.class, infraProvisionersIdSet);
        }
      }
    }
  }

  protected <T extends Base> void bulkSetTerraformSource(
      String accountId, Class<T> clazz, Set<String> infrastructureProvisionersIdSet) {
    final DBCollection collection = wingsPersistence.getCollection(clazz);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();

    int i = 1;
    try (HIterator<TerraformInfrastructureProvisioner> terraformInfrastructureProvisioners =
             new HIterator<>(wingsPersistence.createQuery(TerraformInfrastructureProvisioner.class)
                                 .field("_id")
                                 .in(infrastructureProvisionersIdSet)
                                 .field("sourceType")
                                 .doesNotExist()
                                 .fetch())) {
      while (terraformInfrastructureProvisioners.hasNext()) {
        final TerraformInfrastructureProvisioner terraformProvisioner = terraformInfrastructureProvisioners.next();
        terraformProvisioner.setSourceType(TerraformSourceType.GIT);
        wingsPersistence.save(terraformProvisioner);
      }
    }
  }
}

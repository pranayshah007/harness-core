/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class AwsConfigEc2IamRoleMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    List<SettingAttribute> settingAttributes = wingsPersistence.createQuery(SettingAttribute.class, excludeValidate)
                                                   .filter(SettingAttributeKeys.value_type, "AWS")
                                                   .filter("value.useEc2IamCredentials", true)
                                                   .field("value.encryptedSecretKey")
                                                   .exists()
                                                   .asList();
    log.info("SettingAttribute found {}", settingAttributes.size());
    settingAttributes.forEach(settingAttribute -> {
      try {
        log.info("Updating settingAttribute: {}", settingAttribute.getUuid());
        UpdateOperations<SettingAttribute> operations = wingsPersistence.createUpdateOperations(SettingAttribute.class);
        setUnset(operations, "value.encryptedSecretKey", null);
        wingsPersistence.update(settingAttribute, operations);
        log.info("Updated settingAttribute: {}", settingAttribute.getUuid());
      } catch (Exception ex) {
        log.error("Exception while updating setting attribute: " + settingAttribute.getUuid(), ex);
      }
    });
    log.info("Completed migration for Aws Config Ec2 Iam Role");
  }
}

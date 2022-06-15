/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.cloudProvider;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.RancherConfig;
import software.wings.beans.RancherConfig.RancherConfigBuilder;
import software.wings.beans.SettingAttribute;
import software.wings.graphql.schema.mutation.cloudProvider.QLRancherCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.QLUpdateRancherCloudProviderInput;

import com.google.inject.Singleton;

@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class RancherDataFetcherHelper {
  public SettingAttribute toSettingAttribute(QLRancherCloudProviderInput input, String accountId) {
    RancherConfigBuilder configBuilder = RancherConfig.builder().accountId(accountId);

    if (input.getRancherUrl().isPresent()) {
      input.getRancherUrl().getValue().ifPresent(configBuilder::rancherUrl);
    }

    if (input.getBearerTokenSecretId().isPresent()) {
      input.getBearerTokenSecretId().getValue().ifPresent(configBuilder::encryptedBearerToken);
    }

    SettingAttribute.Builder settingAttributeBuilder = SettingAttribute.Builder.aSettingAttribute()
                                                           .withValue(configBuilder.build())
                                                           .withAccountId(accountId)
                                                           .withCategory(SettingAttribute.SettingCategory.SETTING);

    if (input.getName().isPresent()) {
      input.getName().getValue().ifPresent(settingAttributeBuilder::withName);
    }

    return settingAttributeBuilder.build();
  }

  public void updateSettingAttribute(
      SettingAttribute settingAttribute, QLUpdateRancherCloudProviderInput input, String accountId) {
    RancherConfig config = (RancherConfig) settingAttribute.getValue();

    if (input.getRancherUrl().isPresent()) {
      input.getRancherUrl().getValue().ifPresent(config::setRancherUrl);
    }

    if (input.getBearerTokenSecretId().isPresent()) {
      input.getBearerTokenSecretId().getValue().ifPresent(config::setEncryptedBearerToken);
    }

    settingAttribute.setValue(config);

    if (input.getName().isPresent()) {
      input.getName().getValue().ifPresent(settingAttribute::setName);
    }
  }
}

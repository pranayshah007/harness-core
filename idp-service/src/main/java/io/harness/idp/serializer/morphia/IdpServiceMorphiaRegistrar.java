/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.serializer.morphia;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.beans.entity.AppConfigEntity;
import io.harness.idp.configmanager.beans.entity.MergedAppConfigEntity;
import io.harness.idp.configmanager.beans.entity.PluginConfigEnvVariablesEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.onboarding.entities.AsyncCatalogImportEntity;
import io.harness.idp.plugin.beans.PluginInfoEntity;
import io.harness.idp.plugin.beans.PluginRequestEntity;
import io.harness.idp.settings.beans.entity.BackstagePermissionsEntity;
import io.harness.idp.status.beans.StatusInfoEntity;
import io.harness.idp.user.beans.entity.UserEventEntity;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public class IdpServiceMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(AppConfigEntity.class);
    set.add(MergedAppConfigEntity.class);
    set.add(BackstageEnvVariableEntity.class);
    set.add(StatusInfoEntity.class);
    set.add(NamespaceEntity.class);
    set.add(BackstagePermissionsEntity.class);
    set.add(CatalogConnectorEntity.class);
    set.add(PluginInfoEntity.class);
    set.add(UserEventEntity.class);
    set.add(PluginConfigEnvVariablesEntity.class);
    set.add(PluginRequestEntity.class);
    set.add(AsyncCatalogImportEntity.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // Nothing to register
  }
}

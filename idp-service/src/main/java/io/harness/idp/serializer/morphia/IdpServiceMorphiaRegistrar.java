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
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.onboarding.entities.CatalogConnector;
import io.harness.idp.secret.beans.entity.EnvironmentSecretEntity;
import io.harness.idp.settings.beans.entity.BackstagePermissionsEntity;
import io.harness.idp.status.beans.StatusInfoEntity;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public class IdpServiceMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(AppConfigEntity.class);
    set.add(EnvironmentSecretEntity.class);
    set.add(StatusInfoEntity.class);
    set.add(NamespaceEntity.class);
    set.add(BackstagePermissionsEntity.class);
    set.add(CatalogConnector.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // Nothing to register
  }
}

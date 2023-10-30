/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstagebeans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.IDP)
public enum BackstageCatalogEntityTypes {
  API("API"),
  COMPONENT("Component"),
  DOMAIN("Domain"),
  GROUP("Group"),
  LOCATION("Location"),
  RESOURCE("Resource"),
  SYSTEM("System"),
  TEMPLATE("Template"),
  USER("User");

  public final String kind;

  BackstageCatalogEntityTypes(String kind) {
    this.kind = kind;
  }

  public static BackstageCatalogEntityTypes fromString(String text) {
    for (BackstageCatalogEntityTypes type : BackstageCatalogEntityTypes.values()) {
      if (type.kind.equalsIgnoreCase(text)) {
        return type;
      }
    }
    throw new IllegalArgumentException(String.format("Could not find type for %s", text));
  }

  public static Class<?> getTypeReference(String kind) {
    switch (BackstageCatalogEntityTypes.fromString(kind)) {
      case DOMAIN:
        return BackstageCatalogDomainEntity.class;
      case SYSTEM:
        return BackstageCatalogSystemEntity.class;
      case COMPONENT:
        return BackstageCatalogComponentEntity.class;
      case API:
        return BackstageCatalogApiEntity.class;
      case USER:
        return BackstageCatalogUserEntity.class;
      case GROUP:
        return BackstageCatalogGroupEntity.class;
      case RESOURCE:
        return BackstageCatalogResourceEntity.class;
      case LOCATION:
        return BackstageCatalogLocationEntity.class;
      case TEMPLATE:
        return BackstageCatalogTemplateEntity.class;
      default:
        throw new IllegalArgumentException(String.format("Could not get TypeReference for unknown kind %s", kind));
    }
  }

  public static String getEntityType(BackstageCatalogEntity entity) {
    switch (BackstageCatalogEntityTypes.fromString(entity.getKind())) {
      case API:
        return ((BackstageCatalogApiEntity) entity).getSpec().getType();
      case COMPONENT:
        return ((BackstageCatalogComponentEntity) entity).getSpec().getType();
      case LOCATION:
        return ((BackstageCatalogLocationEntity) entity).getSpec().getType();
      case TEMPLATE:
        return ((BackstageCatalogTemplateEntity) entity).getSpec().getType();
      case RESOURCE:
        return ((BackstageCatalogResourceEntity) entity).getSpec().getType();
      default:
        return null;
    }
  }

  public static String getEntityOwner(BackstageCatalogEntity entity) {
    switch (BackstageCatalogEntityTypes.fromString(entity.getKind())) {
      case API:
        return ((BackstageCatalogApiEntity) entity).getSpec().getOwner();
      case COMPONENT:
        return ((BackstageCatalogComponentEntity) entity).getSpec().getOwner();
      case RESOURCE:
        return ((BackstageCatalogResourceEntity) entity).getSpec().getOwner();
      case DOMAIN:
        return ((BackstageCatalogDomainEntity) entity).getSpec().getOwner();
      case SYSTEM:
        return ((BackstageCatalogSystemEntity) entity).getSpec().getOwner();
      case GROUP:
        return ((BackstageCatalogGroupEntity) entity).getSpec().getOwner();
      case TEMPLATE:
        return ((BackstageCatalogTemplateEntity) entity).getSpec().getOwner();
      default:
        return null;
    }
  }

  public static String getEntityLifecycle(BackstageCatalogEntity entity) {
    switch (BackstageCatalogEntityTypes.fromString(entity.getKind())) {
      case API:
        return ((BackstageCatalogApiEntity) entity).getSpec().getLifecycle();
      case COMPONENT:
        return ((BackstageCatalogComponentEntity) entity).getSpec().getLifecycle();
      default:
        return null;
    }
  }
}

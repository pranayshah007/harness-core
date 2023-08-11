/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public enum BackstageCatalogEntityTypes {
  DOMAIN("Domain"),
  SYSTEM("System"),
  COMPONENT("Component"),
  GROUP("Group"),
  LOCATION("Location"),
  TEMPLATE("Template");

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

  public static TypeReference getTypeReference(String kind) {
    switch (BackstageCatalogEntityTypes.fromString(kind)) {
      case DOMAIN:
        return new TypeReference<List<BackstageCatalogDomainEntity>>() {};
      case SYSTEM:
        return new TypeReference<List<BackstageCatalogSystemEntity>>() {};
      case COMPONENT:
        return new TypeReference<List<BackstageCatalogComponentEntity>>() {};
      case GROUP:
        return new TypeReference<List<BackstageCatalogGroupEntity>>() {};
      case LOCATION:
        return new TypeReference<List<BackstageCatalogLocationEntity>>() {};
      case TEMPLATE:
        return new TypeReference<List<BackstageCatalogTemplateEntity>>() {};
      default:
        throw new IllegalArgumentException(String.format("Could not get TypeReference for unknown kind %s", kind));
    }
  }

  public static String getEntityType(BackstageCatalogEntity entity) {
    switch (BackstageCatalogEntityTypes.fromString(entity.getKind())) {
      case COMPONENT:
        return ((BackstageCatalogComponentEntity) entity).getSpec().getType();
      case LOCATION:
        return ((BackstageCatalogLocationEntity) entity).getSpec().getType();
      case TEMPLATE:
        return ((BackstageCatalogTemplateEntity) entity).getSpec().getType();
      case DOMAIN:
      case SYSTEM:
      case GROUP:
      default:
        return null;
    }
  }
}

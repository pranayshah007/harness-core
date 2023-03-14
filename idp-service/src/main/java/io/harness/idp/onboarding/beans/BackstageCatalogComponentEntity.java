/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.beans;

import static io.harness.idp.onboarding.utils.Constants.ENTITY_UNKNOWN_OWNER;
import static io.harness.idp.onboarding.utils.Constants.PIPE_DELIMITER;
import static io.harness.idp.onboarding.utils.Constants.SERVICE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.HarnessBackstageEntities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BackstageCatalogComponentEntity extends BackstageCatalogEntity {
  private String kind = BackstageCatalogEntityTypes.COMPONENT.kind;
  private Spec spec;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Spec {
    private String type;
    private String lifecycle;
    private String owner;
    @JsonIgnore private String domain;
    private String system;
  }

  public static List<HarnessBackstageEntities> map(
      List<BackstageCatalogComponentEntity> backstageCatalogComponentEntities) {
    return backstageCatalogComponentEntities.stream()
        .map(BackstageCatalogComponentEntity::convert)
        .collect(Collectors.toList());
  }

  private static HarnessBackstageEntities convert(BackstageCatalogComponentEntity backstageCatalogComponentEntity) {
    HarnessBackstageEntities harnessIdpServiceEntity = new HarnessBackstageEntities();

    harnessIdpServiceEntity.setIdentifier(backstageCatalogComponentEntity.getSpec().getDomain() + PIPE_DELIMITER
        + backstageCatalogComponentEntity.getSpec().getSystem() + PIPE_DELIMITER
        + backstageCatalogComponentEntity.getMetadata().getIdentifier());
    harnessIdpServiceEntity.setEntityType(SERVICE);
    harnessIdpServiceEntity.setName(backstageCatalogComponentEntity.getMetadata().getName());
    harnessIdpServiceEntity.setType(SERVICE);
    harnessIdpServiceEntity.setOwner(ENTITY_UNKNOWN_OWNER);
    harnessIdpServiceEntity.setSystem(backstageCatalogComponentEntity.getSpec().getSystem());

    return harnessIdpServiceEntity;
  }
}

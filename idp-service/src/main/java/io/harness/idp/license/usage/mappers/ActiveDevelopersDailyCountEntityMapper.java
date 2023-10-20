/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.license.usage.dto.ActiveDevelopersTrendCountDTO;
import io.harness.idp.license.usage.entities.ActiveDevelopersDailyCountEntity;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class ActiveDevelopersDailyCountEntityMapper {
  public ActiveDevelopersTrendCountDTO toDto(ActiveDevelopersDailyCountEntity activeDevelopersDailyCountEntity) {
    return ActiveDevelopersTrendCountDTO.builder()
        .date(activeDevelopersDailyCountEntity.getDateInStringFormat())
        .count(activeDevelopersDailyCountEntity.getCount())
        .build();
  }
}

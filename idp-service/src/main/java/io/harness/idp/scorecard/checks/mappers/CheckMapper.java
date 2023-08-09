/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.spec.server.idp.v1.model.CheckListItem;
import io.harness.spec.server.idp.v1.model.CheckResponse;
import io.harness.spec.server.idp.v1.model.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class CheckMapper {
  public CheckListItem toDTO(CheckEntity checkEntity) {
    CheckListItem checks = new CheckListItem();
    checks.setName(checkEntity.getName());
    checks.setIdentifier(checkEntity.getIdentifier());
    checks.setDescription(checkEntity.getDescription());
    checks.setExpression(checkEntity.getExpression());
    checks.setLabels(checkEntity.getLabels());
    checks.setDataSource(
        checkEntity.getRules().stream().map(Rule::getDataSourceIdentifier).collect(Collectors.toList()));
    return checks;
  }

  public List<CheckResponse> toResponseList(List<CheckListItem> checkList) {
    List<CheckResponse> response = new ArrayList<>();
    checkList.forEach(check -> response.add(new CheckResponse().check(check)));
    return response;
  }
}

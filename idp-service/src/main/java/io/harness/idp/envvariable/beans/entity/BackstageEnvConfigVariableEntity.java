/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.beans.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldNameConstants(innerTypeName = "BackstageEnvConfigVariableKeys")
@StoreIn(DbAliases.IDP)
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.secret.beans.entity.BackstageEnvConfigVariableEntity")
public class BackstageEnvConfigVariableEntity extends BackstageEnvVariableEntity {
  private String value;

  @Override
  public BackstageEnvVariableType getType() {
    return BackstageEnvVariableType.CONFIG;
  }

  public static class BackstageEnvConfigVariableMapper
      implements BackstageEnvVariableMapper<BackstageEnvConfigVariable, BackstageEnvConfigVariableEntity> {
    @Override
    public BackstageEnvConfigVariableEntity fromDto(BackstageEnvConfigVariable envVariable, String accountIdentifier) {
      BackstageEnvConfigVariableEntity envConfigVariableEntity =
          BackstageEnvConfigVariableEntity.builder().value(envVariable.getValue()).build();
      setCommonFieldsEntity(envVariable, envConfigVariableEntity, accountIdentifier);
      return envConfigVariableEntity;
    }

    @Override
    public BackstageEnvConfigVariable toDto(BackstageEnvConfigVariableEntity envVariableEntity) {
      BackstageEnvConfigVariable envConfigVariable = new BackstageEnvConfigVariable();
      envConfigVariable.setType(BackstageEnvVariable.TypeEnum.CONFIG);
      envConfigVariable.setValue(envVariableEntity.getValue());
      setCommonFieldsDto(envVariableEntity, envConfigVariable);
      return envConfigVariable;
    }
  }
}

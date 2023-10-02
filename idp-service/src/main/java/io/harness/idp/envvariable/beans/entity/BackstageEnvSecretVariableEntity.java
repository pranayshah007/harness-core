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
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldNameConstants(innerTypeName = "BackstageEnvSecretVariableKeys")
@StoreIn(DbAliases.IDP)
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.secret.beans.entity.BackstageEnvSecretVariableEntity")
public class BackstageEnvSecretVariableEntity extends BackstageEnvVariableEntity {
  private String harnessSecretIdentifier;
  private boolean isDeleted;

  @Override
  public BackstageEnvVariableType getType() {
    return BackstageEnvVariableType.SECRET;
  }

  public static class BackstageEnvSecretVariableMapper
      implements BackstageEnvVariableMapper<BackstageEnvSecretVariable, BackstageEnvSecretVariableEntity> {
    @Override
    public BackstageEnvSecretVariableEntity fromDto(BackstageEnvSecretVariable envVariable, String accountIdentifier) {
      BackstageEnvSecretVariableEntity envSecretVariableEntity =
          BackstageEnvSecretVariableEntity.builder()
              .harnessSecretIdentifier(envVariable.getHarnessSecretIdentifier())
              .isDeleted(envVariable.isIsDeleted())
              .build();
      setCommonFieldsEntity(envVariable, envSecretVariableEntity, accountIdentifier);
      return envSecretVariableEntity;
    }

    @Override
    public BackstageEnvSecretVariable toDto(BackstageEnvSecretVariableEntity envVariableEntity) {
      BackstageEnvSecretVariable envSecretVariable = new BackstageEnvSecretVariable();
      envSecretVariable.setHarnessSecretIdentifier(envVariableEntity.getHarnessSecretIdentifier());
      envSecretVariable.setIsDeleted(envVariableEntity.isDeleted);
      setCommonFieldsDto(envVariableEntity, envSecretVariable);
      envSecretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
      return envSecretVariable;
    }
  }
}

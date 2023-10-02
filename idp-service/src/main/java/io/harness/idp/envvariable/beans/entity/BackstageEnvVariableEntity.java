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
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariableResponse;
import io.harness.spec.server.idp.v1.model.ResolvedEnvVariable;
import io.harness.spec.server.idp.v1.model.ResolvedEnvVariableResponse;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@FieldNameConstants(innerTypeName = "BackstageEnvVariableKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "backstageEnvVariables", noClassnameStored = true)
@Document("backstageEnvVariables")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public abstract class BackstageEnvVariableEntity implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_envName")
                 .unique(true)
                 .field(BackstageEnvVariableKeys.accountIdentifier)
                 .field(BackstageEnvVariableKeys.envName)
                 .build())
        .build();
  }
  @Id @org.mongodb.morphia.annotations.Id private String id;
  private String envName;
  private String accountIdentifier;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  public abstract BackstageEnvVariableType getType();
  public interface BackstageEnvVariableMapper<S extends BackstageEnvVariable, T extends BackstageEnvVariableEntity> {
    T fromDto(S backstageEnvVariable, String accountIdentifier);

    S toDto(T backstageEnvVariableEntity);

    default void setCommonFieldsEntity(
        BackstageEnvVariable envVariable, BackstageEnvVariableEntity envVariableEntity, String accountIdentifier) {
      envVariableEntity.setId(envVariable.getIdentifier());
      envVariableEntity.setEnvName(envVariable.getEnvName());
      envVariableEntity.setAccountIdentifier(accountIdentifier);
      envVariableEntity.setCreatedAt(envVariable.getCreated());
      envVariableEntity.setLastModifiedAt(envVariable.getUpdated());
    }

    default void setCommonFieldsDto(BackstageEnvVariableEntity envVariableEntity, BackstageEnvVariable envVariable) {
      envVariable.identifier(envVariableEntity.getId());
      envVariable.envName(envVariableEntity.getEnvName());
      envVariable.created(envVariableEntity.getCreatedAt());
      envVariable.updated(envVariableEntity.getLastModifiedAt());
    }

    static List<BackstageEnvVariableResponse> toResponseList(List<BackstageEnvVariable> variables) {
      List<BackstageEnvVariableResponse> response = new ArrayList<>();
      variables.forEach(variable -> response.add(new BackstageEnvVariableResponse().envVariable(variable)));
      return response;
    }

    static ResolvedEnvVariableResponse toResolvedVariableResponse(String variables) {
      ResolvedEnvVariableResponse response = new ResolvedEnvVariableResponse();
      response.setResolvedEnvVariables(variables);
      return response;
    }
  }
}

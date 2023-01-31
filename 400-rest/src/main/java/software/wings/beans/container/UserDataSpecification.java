/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import static software.wings.ngmigration.NGMigrationEntityType.AMI_STARTUP_SCRIPT;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;

import software.wings.beans.DeploymentSpecification;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGMigrationEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.morphia.annotations.Entity;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by anubhaw on 12/18/17.
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "UserDataSpecificationKeys")
@StoreIn(DbAliases.HARNESS)
@Entity("userDataSpecifications")
@HarnessEntity(exportable = true)
public class UserDataSpecification extends DeploymentSpecification implements NGMigrationEntity {
  @NotEmpty @FdUniqueIndex private String serviceId;
  @NotNull private String data;

  @JsonIgnore
  @Override
  public CgBasicInfo getCgBasicInfo() {
    return CgBasicInfo.builder()
        .id(getUuid())
        .name(getMigrationEntityName())
        .type(AMI_STARTUP_SCRIPT)
        .appId(getAppId())
        .accountId(getAccountId())
        .build();
  }

  @JsonIgnore
  @Override
  public String getMigrationEntityName() {
    return getUuid();
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends DeploymentSpecification.Yaml {
    private String data;

    @Builder
    public Yaml(String type, String harnessApiVersion, String data) {
      super(type, harnessApiVersion);
      this.data = data;
    }
  }
}

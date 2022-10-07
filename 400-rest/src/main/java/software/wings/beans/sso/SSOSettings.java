/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.sso;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import software.wings.beans.Base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

@OwnedBy(PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "SSOSettingsKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "ssoSettings")
@HarnessEntity(exportable = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY)
public abstract class SSOSettings extends Base implements AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountIdTypeIdx")
                 .field(SSOSettingsKeys.accountId)
                 .field(SSOSettingsKeys.type)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("typeNextIterations")
                 .field(SSOSettingsKeys.type)
                 .field(SSOSettingsKeys.nextIterations)
                 .build())
        .build();
  }

  @NotNull protected SSOType type;
  @NotBlank protected String displayName;
  @NotEmpty protected String url;
  private Long nextIteration;
  @FdIndex List<Long> nextIterations = new ArrayList<>();

  public SSOSettings(SSOType type, String displayName, String url) {
    this.type = type;
    this.displayName = displayName;
    this.url = url;
    appId = GLOBAL_APP_ID;
  }

  // TODO: Return list of all sso settings instead with the use of @JsonIgnore to trim the unnecessary elements
  @JsonIgnore public abstract SSOSettings getPublicSSOSettings();

  public abstract SSOType getType();

  public static final class SSOSettingsKeys { public static final String accountId = AccountAccess.ACCOUNT_ID_KEY; }
}

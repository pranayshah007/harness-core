/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.infrastructure;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.GitFileConfig;
import software.wings.beans.NameValuePair;

import com.github.reinert.jjschema.SchemaIgnore;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "terraformConfig")
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "TerraformConfigKeys")
@OwnedBy(CDP)
@TargetModule(HarnessModule._960_API_SERVICES)
public class TerraformConfig implements PersistentEntity, UuidAware, CreatedAtAware, AccountAccess {
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @FdIndex @NotNull @SchemaIgnore protected String appId;
  @SchemaIgnore @FdIndex private long createdAt;
  @FdIndex private String accountId;

  private final String sourceRepoSettingId;
  /**
   * This is generally represented by commit SHA in git.
   */
  private final String sourceRepoReference;

  /**
   * All variables of type TEXT & ENCRYPTED_TEXT.
   * Encrypted variables are not persisted in plain text. The uuid of the corresponding EncryptedRecord is stored.
   */
  private final List<NameValuePair> variables;
  private final List<NameValuePair> backendConfigs;
  private final List<NameValuePair> environmentVariables;
  private final List<String> targets;
  private final List<String> tfVarFiles;
  private final GitFileConfig tfVarGitFileConfig;
  private final GitFileConfig remoteBackendConfig;
  private final String backendConfigStoreType;
  private final TerraformCommand command;

  @FdIndex private final String entityId;
  private final String workflowExecutionId;
  private final String delegateTag;
  private final String awsConfigId;
  private final String awsRoleArn;
  private final String awsRegion;
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.infrastructure;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.NameValuePair;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder(toBuilder = true)
@EqualsAndHashCode(callSuper = false)
@Entity(value = "cloudFormationRollbackConfig")
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "CloudFormationRollbackConfigKeys")
@OwnedBy(CDP)
public class CloudFormationRollbackConfig implements PersistentEntity, UuidAware, CreatedAtAware, AccountAccess {
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @FdIndex private String accountId;
  @FdIndex @NotNull @SchemaIgnore protected String appId;
  @SchemaIgnore @FdIndex private long createdAt;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .field(CloudFormationRollbackConfigKeys.appId)
                 .field(CloudFormationRollbackConfigKeys.entityId)
                 .field(CloudFormationRollbackConfigKeys.awsConfigId)
                 .field(CloudFormationRollbackConfigKeys.createdAt)
                 .name("byAppAndEntityAndAwsConfig")
                 .build())
        .build();
  }

  private String url;
  private String body;
  private String createType;
  private List<NameValuePair> variables;

  private String region;
  @FdIndex private String awsConfigId;
  private String customStackName;
  private String workflowExecutionId;
  private String cloudFormationRoleArn;
  private boolean skipBasedOnStackStatus;
  private List<String> stackStatusesToMarkAsSuccess;

  @FdIndex private String entityId;
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.awscdk.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "AwsCdkConfigKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "awsCdkConfig", noClassnameStored = true)
@Document("awsCdkConfig")
@TypeAlias("awsCdkConfig")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CDP)
public class AwsCdkConfig implements PersistentEntity, CreatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_provisionerIdentifier_stageExecutionId_createdAt")
                 .field(AwsCdkConfigKeys.accountId)
                 .field(AwsCdkConfigKeys.orgId)
                 .field(AwsCdkConfigKeys.projectId)
                 .field(AwsCdkConfigKeys.provisionerIdentifier)
                 .field(AwsCdkConfigKeys.stageExecutionId)
                 .descSortField(AwsCdkConfigKeys.createdAt)
                 .build())
        .build();
  }
  @Id @dev.morphia.annotations.Id private String uuid;
  @NotNull String accountId;
  @NotNull String orgId;
  @NotNull String projectId;
  @NotNull String stageExecutionId;
  @NotNull String provisionerIdentifier;
  @NotNull long createdAt;

  private ContainerResourceConfig resources;
  private Integer runAsUser;
  private String connectorRef;
  private String image;
  private ImagePullPolicy imagePullPolicy;
  private Map<String, String> envVariables;
  private Boolean privileged;
  private String commitId;
}

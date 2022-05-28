/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "CloudformationConfigKeys")
@Entity(value = "cloudformationConfig", noClassnameStored = true)
@Document("cloudformationConfig")
@TypeAlias("cloudformationConfig")
@HarnessEntity(exportable = true)
@StoreIn(DbAliases.NG_MANAGER)
@OwnedBy(HarnessTeam.CDP)
public class CloudformationConfig implements PersistentEntity, CreatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_provisionerIdentifier_stageExecutionId_createdAt")
                 .field(CloudformationConfigKeys.accountId)
                 .field(CloudformationConfigKeys.orgId)
                 .field(CloudformationConfigKeys.projectId)
                 .field(CloudformationConfigKeys.provisionerIdentifier)
                 .field(CloudformationConfigKeys.stageExecutionId)
                 .descSortField(CloudformationConfigKeys.createdAt)
                 .build())
        .build();
  }
  @Id @org.mongodb.morphia.annotations.Id private String uuid;
  @NotNull String accountId;
  @NotNull String orgId;
  @NotNull String projectId;
  @NotNull String stageExecutionId;
  @NotNull String provisionerIdentifier;
  @NotNull long createdAt;

  private String templateBody;
  private String templateUrl;
  private LinkedHashMap<String, List<String>> parametersFiles;
  private Map<String, String> parameterOverrides;
  private String stackName;
  private String tags;
  private String connectorRef;
  private String region;
  private String roleArn;
  private List<String> capabilities;
  private List<String> stackStatusesToMarkAsSuccess;
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.ModuleType;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEventData;
import io.harness.audit.beans.Environment;
import io.harness.audit.beans.Environment.EnvironmentKeys;
import io.harness.audit.beans.Principal.PrincipalKeys;
import io.harness.audit.entities.AuthenticationInfo.AuthenticationInfoKeys;
import io.harness.audit.entities.Resource.ResourceKeys;
import io.harness.audit.entities.ResourceScope.ResourceScopeKeys;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.KeyValuePair;
import io.harness.ng.core.common.beans.KeyValuePair.KeyValuePairKeys;
import io.harness.request.HttpRequestInfo;
import io.harness.request.RequestMetadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.Instant;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PL)
@Data
@Builder
@FieldNameConstants(innerTypeName = "AuditEventKeys")
@StoreIn(DbAliases.AUDITS)
@Entity(value = "auditEvents", noClassnameStored = true)
@Document("auditEvents")
@TypeAlias("auditEvents")
@JsonInclude(NON_NULL)
public class AuditEvent {
  @NotNull @NotBlank String insertId;
  @Id @dev.morphia.annotations.Id String id;

  @Valid @NotNull ResourceScope resourceScope;

  @Valid HttpRequestInfo httpRequestInfo;
  @Valid RequestMetadata requestMetadata;

  @NotNull Instant timestamp;

  @NotNull @Valid AuthenticationInfo authenticationInfo;

  @NotNull ModuleType module;
  @Valid Environment environment;

  @NotNull @Valid Resource resource;
  @NotNull Action action;

  @Valid AuditEventData auditEventData;

  List<KeyValuePair> internalInfo;

  @CreatedDate Long createdAt;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("ngAuditTimeAccountResourceIdx")
                 .field(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY)
                 .field(AuditEventKeys.RESOURCE_TYPE_KEY)
                 .field(AuditEventKeys.RESOURCE_IDENTIFIER_KEY)
                 .field(AuditEventKeys.timestamp)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("ngAuditTimeAccountOrgResourceIdx")
                 .field(AuditEventKeys.timestamp)
                 .field(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY)
                 .field(AuditEventKeys.ORG_IDENTIFIER_KEY)
                 .field(AuditEventKeys.RESOURCE_TYPE_KEY)
                 .field(AuditEventKeys.RESOURCE_IDENTIFIER_KEY)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("ngAuditTimeAccountOrgProjectResourceIdx")
                 .field(AuditEventKeys.timestamp)
                 .field(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY)
                 .field(AuditEventKeys.ORG_IDENTIFIER_KEY)
                 .field(AuditEventKeys.PROJECT_IDENTIFIER_KEY)
                 .field(AuditEventKeys.RESOURCE_TYPE_KEY)
                 .field(AuditEventKeys.RESOURCE_IDENTIFIER_KEY)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("ngAuditCreatedAtIdx")
                 .field(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY)
                 .sortField(AuditEventKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("uniqueNgAuditEventIdx")
                 .field(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY)
                 .field(AuditEventKeys.timestamp)
                 .field(AuditEventKeys.insertId)
                 .unique(true)
                 .build())
        .build();
  }

  @UtilityClass
  public static final class AuditEventKeys {
    public static final String ACCOUNT_IDENTIFIER_KEY =
        AuditEventKeys.resourceScope + "." + ResourceScopeKeys.accountIdentifier;
    public static final String ORG_IDENTIFIER_KEY =
        AuditEventKeys.resourceScope + "." + ResourceScopeKeys.orgIdentifier;
    public static final String PROJECT_IDENTIFIER_KEY =
        AuditEventKeys.resourceScope + "." + ResourceScopeKeys.projectIdentifier;
    public static final String RESOURCE_SCOPE_LABEL_KEY = AuditEventKeys.resourceScope + "." + ResourceScopeKeys.labels;
    public static final String RESOURCE_SCOPE_LABEL_KEYS_KEY =
        AuditEventKeys.resourceScope + "." + ResourceScopeKeys.labels + "." + KeyValuePairKeys.key;
    public static final String RESOURCE_SCOPE_LABEL_VALUES_KEY =
        AuditEventKeys.resourceScope + "." + ResourceScopeKeys.labels + "." + KeyValuePairKeys.value;

    public static final String PRINCIPAL_TYPE_KEY =
        AuditEventKeys.authenticationInfo + "." + AuthenticationInfoKeys.principal + "." + PrincipalKeys.type;
    public static final String PRINCIPAL_IDENTIFIER_KEY =
        AuditEventKeys.authenticationInfo + "." + AuthenticationInfoKeys.principal + "." + PrincipalKeys.identifier;

    public static final String ENVIRONMENT_TYPE_KEY = AuditEventKeys.environment + "." + EnvironmentKeys.type;
    public static final String ENVIRONMENT_IDENTIFIER_KEY =
        AuditEventKeys.environment + "." + EnvironmentKeys.identifier;

    public static final String RESOURCE_TYPE_KEY = AuditEventKeys.resource + "." + ResourceKeys.type;
    public static final String RESOURCE_IDENTIFIER_KEY = AuditEventKeys.resource + "." + ResourceKeys.identifier;
    public static final String RESOURCE_LABEL_KEY = AuditEventKeys.resource + "." + ResourceKeys.labels;
    public static final String RESOURCE_LABEL_KEYS_KEY =
        AuditEventKeys.resource + "." + ResourceKeys.labels + "." + KeyValuePairKeys.key;
    public static final String RESOURCE_LABEL_VALUES_KEY =
        AuditEventKeys.resource + "." + ResourceKeys.labels + "." + KeyValuePairKeys.value;
  }
}

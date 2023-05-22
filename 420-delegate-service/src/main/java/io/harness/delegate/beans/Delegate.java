/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import static java.time.Duration.ofDays;
import static java.time.Duration.ofHours;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.SecondaryStoreIn;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateEntityOwner.DelegateEntityOwnerKeys;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.Transient;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@FieldNameConstants(innerTypeName = "DelegateKeys")
@StoreIn(DbAliases.HARNESS)
@SecondaryStoreIn(DbAliases.DMS)
@Entity(value = "delegates", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.DEL)
public class Delegate implements PersistentEntity, UuidAware, CreatedAtAware, AccountAccess, PersistentRegularIterable {
  private static final Duration TTL = ofDays(7);
  private static final Duration IMMUTABLE_TTL = ofHours(6);

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .field(DelegateKeys.accountId)
                 .field(DelegateKeys.ng)
                 .field(DelegateKeys.delegateGroupId)
                 .field(DelegateKeys.owner)
                 .name("byAcctNgGroupIdOwner")
                 .build())
        .build();
  }

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @SchemaIgnore @FdIndex private long createdAt;
  // Will be used by ECS delegate, when hostName is mentioned in TaskSpec.
  @NotEmpty private String accountId;

  // Will be used for NG to hold delegate size details
  private DelegateSizeDetails sizeDetails;
  // Will be used for NG to hold information about delegate if it is owned at Org / Project
  private DelegateEntityOwner owner;

  // Will be used for segregation of CG vs. NG delegates.
  private boolean ng;

  @Default private DelegateInstanceStatus status = DelegateInstanceStatus.ENABLED;
  private String description;
  private String ip;
  private String hostName;
  private String delegateGroupName;
  private String delegateGroupId;
  private String delegateName;
  private String delegateProfileId;

  //@TODO: Move below 2 to Redis
  private String delegateConnectionId;
  @FdIndex private long lastHeartBeat;

  private String version;
  private transient String sequenceNum;
  private String delegateType;
  private transient String delegateRandomToken;
  private transient boolean keepAlivePacket;
  private boolean polllingModeEnabled;
  private boolean proxy;
  private boolean ceEnabled;
  private DelegateCapacity delegateCapacity;
  private boolean disconnected;

  private List<String> supportedTaskTypes;

  @Transient private List<String> currentlyExecutingDelegateTasks;

  @Transient private boolean useCdn;

  @Transient private String useJreVersion;

  private String location;

  private List<DelegateScope> includeScopes;
  private List<DelegateScope> excludeScopes;
  // these are tags added from UI/APIs
  private List<String> tags;
  // these are tags added from delegate.yaml
  private List<String> tagsFromYaml;
  private String profileResult;
  private boolean profileError;
  private long profileExecutedAt;
  private long expirationTime;

  @FdIndex Long capabilitiesCheckNextIteration;

  @FdTtlIndex private Date validUntil;

  @SchemaIgnore private List<String> keywords;

  @FdIndex @Deprecated Long taskExpiryCheckNextIteration;

  @FdIndex Long delegateDisconnectDetectorNextIteration;

  Long lastExpiredEventHeartbeatTime;

  private String delegateTokenName;

  private boolean heartbeatAsObject;

  private boolean immutable;

  private boolean mtls;

  @Transient private Integer numberOfTaskAssigned;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (DelegateKeys.delegateDisconnectDetectorNextIteration.equals(fieldName)) {
      this.delegateDisconnectDetectorNextIteration = nextIteration;
      return;
    }

    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (DelegateKeys.delegateDisconnectDetectorNextIteration.equals(fieldName)) {
      return this.delegateDisconnectDetectorNextIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @UtilityClass
  public static final class DelegateKeys {
    public static final String owner_identifier = owner + "." + DelegateEntityOwnerKeys.identifier;
    public static final String searchTermFilter = "searchTermFilter";
  }

  public static Delegate getDelegateFromParams(DelegateParams delegateParams, boolean connectedUsingMtls) {
    return Delegate.builder()
        .uuid(delegateParams.getDelegateId())
        .accountId(delegateParams.getAccountId())
        .description(delegateParams.getDescription())
        .ip(delegateParams.getIp())
        .hostName(delegateParams.getHostName())
        .delegateGroupName(delegateParams.getDelegateGroupName())
        .delegateGroupId(delegateParams.getDelegateGroupId())
        .delegateName(delegateParams.getDelegateName())
        .delegateProfileId(delegateParams.getDelegateProfileId())
        .lastHeartBeat(delegateParams.getLastHeartBeat())
        .version(delegateParams.getVersion())
        .sequenceNum(delegateParams.getSequenceNum())
        .delegateType(delegateParams.getDelegateType())
        .delegateRandomToken(delegateParams.getDelegateRandomToken())
        .keepAlivePacket(delegateParams.isKeepAlivePacket())
        .polllingModeEnabled(delegateParams.isPollingModeEnabled())
        .ng(delegateParams.isNg())
        .currentlyExecutingDelegateTasks(delegateParams.getCurrentlyExecutingDelegateTasks())
        .location(delegateParams.getLocation())
        .mtls(connectedUsingMtls)
        .delegateConnectionId(delegateParams.getDelegateConnectionId())
        .heartbeatAsObject(delegateParams.isHeartbeatAsObject())
        .supportedTaskTypes(delegateParams.getSupportedTaskTypes())
        .proxy(delegateParams.isProxy())
        .ceEnabled(delegateParams.isCeEnabled())
        .immutable(delegateParams.isImmutable())
        .tags(delegateParams.getTags())
        .build();
  }

  public boolean hasCapacityRegistered() {
    return this.getDelegateCapacity() != null;
  }

  public long ttlMillis() {
    return isImmutable() ? IMMUTABLE_TTL.toMillis() : TTL.toMillis();
  }
}

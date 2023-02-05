/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.appmanifest;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.NameAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import software.wings.beans.entityinterface.ApplicationAccess;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.Transient;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@StoreIn(DbAliases.HARNESS)
@Entity(value = "helmCharts", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "HelmChartKeys")
@TargetModule(HarnessModule._957_CG_BEANS)
public class HelmChart implements AccountAccess, NameAccess, PersistentEntity, UuidAware, CreatedAtAware,
                                  UpdatedAtAware, ApplicationAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("appId_serviceId")
                 .field(HelmChartKeys.appId)
                 .field(HelmChartKeys.serviceId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("account_appManifestId")
                 .field(HelmChartKeys.accountId)
                 .field(HelmChartKeys.applicationManifestId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("appId_appManifestId")
                 .field(HelmChartKeys.appId)
                 .field(HelmChartKeys.applicationManifestId)
                 .build())
        .build();
  }

  @Id private String uuid;
  @Trimmed private String version;
  @FdIndex private String applicationManifestId;
  @Transient private String appManifestName;
  private String name;
  private String displayName;
  private String accountId;
  private String appId;
  private String serviceId;
  private long createdAt;
  private long lastUpdatedAt;
  private String appVersion;
  private String description;
  private Map<String, String> metadata;

  public software.wings.beans.dto.HelmChart toDto() {
    return software.wings.beans.dto.HelmChart.builder()
        .uuid(getUuid())
        .version(getVersion())
        .applicationManifestId(getApplicationManifestId())
        .appManifestName(getAppManifestName())
        .name(getName())
        .displayName(getDisplayName())
        .accountId(getAccountId())
        .appId(getAppId())
        .serviceId(getServiceId())
        .createdAt(getCreatedAt())
        .lastUpdatedAt(getLastUpdatedAt())
        .appVersion(getAppVersion())
        .description(getDescription())
        .metadata(getMetadata())
        .build();
  }

  public static HelmChart fromDto(software.wings.beans.dto.HelmChart dto) {
    if (dto == null) {
      return null;
    }

    return HelmChart.builder()
        .uuid(dto.getUuid())
        .version(dto.getVersion())
        .applicationManifestId(dto.getApplicationManifestId())
        .appManifestName(dto.getAppManifestName())
        .name(dto.getName())
        .displayName(dto.getDisplayName())
        .accountId(dto.getAccountId())
        .appId(dto.getAppId())
        .serviceId(dto.getServiceId())
        .createdAt(dto.getCreatedAt())
        .lastUpdatedAt(dto.getLastUpdatedAt())
        .appVersion(dto.getAppVersion())
        .description(dto.getDescription())
        .metadata(dto.getMetadata())
        .build();
  }

  public static List<HelmChart> fromDtos(List<software.wings.beans.dto.HelmChart> dtos) {
    if (isEmpty(dtos)) {
      return Collections.emptyList();
    }
    return dtos.stream().map(dto -> fromDto(dto)).collect(Collectors.toList());
  }
}

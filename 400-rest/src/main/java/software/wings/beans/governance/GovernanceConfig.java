/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.governance;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.governance.TimeRangeBasedFreezeConfig;
import io.harness.governance.WeeklyFreezeConfig;
import io.harness.iterator.PersistentCronIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;

import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.yaml.BaseEntityYaml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

/**
 * @author rktummala on 02/04/19
 */
@JsonInclude(NON_EMPTY)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "GovernanceConfigKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "governanceConfig", noClassnameStored = true)
@HarnessEntity(exportable = true)
@Slf4j
public class GovernanceConfig
    implements PersistentEntity, UuidAware, UpdatedByAware, AccountAccess, ApplicationAccess, PersistentCronIterable {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("enableNextIterations_nextIterations")
                 .field(GovernanceConfigKeys.enableNextIterations)
                 .field(GovernanceConfigKeys.nextIterations)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("enableNextCloseIterations_nextCloseIterations")
                 .field(GovernanceConfigKeys.enableNextCloseIterations)
                 .field(GovernanceConfigKeys.nextCloseIterations)
                 .build())
        .build();
  }

  @Id private String uuid;
  @Setter @JsonIgnore @SchemaIgnore private transient boolean syncFromGit;
  @FdIndex private String accountId;
  @NotNull @SchemaIgnore protected String appId = GLOBAL_APP_ID;
  private boolean deploymentFreeze;
  private EmbeddedUser lastUpdatedBy;
  private List<TimeRangeBasedFreezeConfig> timeRangeBasedFreezeConfigs;
  private List<WeeklyFreezeConfig> weeklyFreezeConfigs;
  private boolean enableNextIterations;
  private boolean enableNextCloseIterations;
  @FdIndex
  private List<Long> nextIterations; // List of activation times for all freeze windows used by activation handler
  @FdIndex
  private List<Long>
      nextCloseIterations; // List of deactivation time for all freeze windows used by deactivation handler

  @Builder
  public GovernanceConfig(String accountId, boolean deploymentFreeze,
      List<TimeRangeBasedFreezeConfig> timeRangeBasedFreezeConfigs, List<WeeklyFreezeConfig> weeklyFreezeConfigs) {
    this.accountId = accountId;
    this.deploymentFreeze = deploymentFreeze;
    this.timeRangeBasedFreezeConfigs = timeRangeBasedFreezeConfigs;
    this.weeklyFreezeConfigs = weeklyFreezeConfigs;
  }

  @Nonnull
  public List<TimeRangeBasedFreezeConfig> getTimeRangeBasedFreezeConfigs() {
    return CollectionUtils.emptyIfNull(timeRangeBasedFreezeConfigs);
  }

  @Nonnull
  public List<WeeklyFreezeConfig> getWeeklyFreezeConfigs() {
    return CollectionUtils.emptyIfNull(weeklyFreezeConfigs);
  }

  @Override
  public List<Long> recalculateNextIterations(String fieldName, boolean skipMissing, long throttled) {
    if (EmptyPredicate.isEmpty(timeRangeBasedFreezeConfigs)) {
      nextIterations = new ArrayList<>();
      nextCloseIterations = new ArrayList<>();
      return new ArrayList<>();
    }
    try {
      long currentTime = System.currentTimeMillis();
      if (GovernanceConfigKeys.nextIterations.equals(fieldName)) {
        nextIterations = timeRangeBasedFreezeConfigs.stream()
                             .filter(TimeRangeBasedFreezeConfig::isApplicable)
                             .map(freeze -> freeze.getTimeRange().getFrom())
                             .distinct()
                             .sorted()
                             .filter(time -> time > currentTime)
                             .collect(Collectors.toList());
        return nextIterations;
      } else {
        nextCloseIterations = timeRangeBasedFreezeConfigs.stream()
                                  .filter(TimeRangeBasedFreezeConfig::isApplicable)
                                  .map(freeze -> freeze.getTimeRange().getTo())
                                  .distinct()
                                  .sorted()
                                  .filter(time -> time > currentTime)
                                  .collect(Collectors.toList());
        return nextCloseIterations;
      }
    } catch (Exception ex) {
      log.error("Failed to schedule notification for governance config {}", uuid, ex);
      throw ex;
    }
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (GovernanceConfigKeys.nextIterations.equals(fieldName)) {
      return EmptyPredicate.isEmpty(nextIterations) ? null : nextIterations.get(0);
    }
    return EmptyPredicate.isEmpty(nextCloseIterations) ? null : nextCloseIterations.get(0);
  }

  @Override
  public String getAppId() {
    return GLOBAL_APP_ID;
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = false)
  public static final class Yaml extends BaseEntityYaml {
    private boolean disableAllDeployments;
    private List<TimeRangeBasedFreezeConfig.Yaml> timeRangeBasedFreezeConfigs;

    @lombok.Builder
    public Yaml(String type, String harnessApiVersion, boolean disableAllDeployments,
        List<TimeRangeBasedFreezeConfig.Yaml> timeRangeBasedFreezeConfigs) {
      super(type, harnessApiVersion);
      this.disableAllDeployments = disableAllDeployments;
      this.timeRangeBasedFreezeConfigs = timeRangeBasedFreezeConfigs;
    }
  }
}

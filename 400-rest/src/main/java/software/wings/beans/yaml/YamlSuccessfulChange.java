/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.yaml;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import com.google.common.collect.ImmutableList;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@StoreIn(DbAliases.HARNESS)
@FieldNameConstants(innerTypeName = "YamlSuccessfulChangeKeys")
@Entity(value = "yamlSuccessfulChange", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class YamlSuccessfulChange implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware,
                                             UpdatedAtAware, UpdatedByAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_yaml_file_path")
                 .unique(true)
                 .field(YamlSuccessfulChangeKeys.accountId)
                 .field(YamlSuccessfulChangeKeys.yamlFilePath)
                 .build())
        .build();
  }

  @Id @NotNull(groups = {Update.class}) private String uuid;
  private String accountId;
  private String yamlFilePath;
  private Long changeRequestTS;
  private Long changeProcessedTS;
  private String changeSource;
  private SuccessfulChangeDetail changeDetail;
  private EmbeddedUser createdBy;
  private long createdAt;
  private EmbeddedUser lastUpdatedBy;
  private long lastUpdatedAt;

  @FdTtlIndex(24 * 60 * 60) @Default private Date validUntil = new Date();

  public enum ChangeSource { GIT, HARNESS }
}

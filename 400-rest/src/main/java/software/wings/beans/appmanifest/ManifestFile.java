/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.appmanifest;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import software.wings.beans.Base;
import software.wings.yaml.BaseEntityYaml;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

@Data
@Builder
@FieldNameConstants(innerTypeName = "ManifestFileKeys")
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity("manifestFile")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CDP)
@TargetModule(_957_CG_BEANS)
public class ManifestFile extends Base implements AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("manifestFileIdx")
                 .unique(true)
                 .field(ManifestFileKeys.applicationManifestId)
                 .field(ManifestFileKeys.fileName)
                 .build())
        .build();
  }

  public static final String VALUES_YAML_KEY = "values.yaml";

  @NotEmpty String fileName;
  private String fileContent;
  private String applicationManifestId;
  @FdIndex private String accountId;

  public ManifestFile cloneInternal() {
    ManifestFile manifestFile = ManifestFile.builder().fileName(this.fileName).fileContent(this.fileContent).build();
    manifestFile.setAppId(this.appId);
    manifestFile.setAccountId(this.accountId);
    return manifestFile;
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = false)
  public static final class Yaml extends BaseEntityYaml {
    private String fileContent;

    @Builder
    public Yaml(String type, String harnessApiVersion, String fileContent) {
      super(type, harnessApiVersion);
      this.fileContent = fileContent;
    }
  }
}

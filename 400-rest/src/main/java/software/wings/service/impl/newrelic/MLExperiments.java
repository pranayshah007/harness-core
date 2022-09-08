/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.newrelic;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;

import software.wings.beans.Base;
import software.wings.service.impl.analysis.MLAnalysisType;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;

@Data
@FieldNameConstants(innerTypeName = "MLExperimentsKeys")
@Builder
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "mlExperiments", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class MLExperiments extends Base {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_experiment")
                 .unique(true)
                 .field(MLExperimentsKeys.ml_analysis_type)
                 .field(MLExperimentsKeys.experimentName)
                 .build())
        .build();
  }

  private MLAnalysisType ml_analysis_type;
  private String experimentName;
  private boolean is24x7;
}

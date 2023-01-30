/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.environment.batch;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.persistence.HIterator;

import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.environment.EnvironmentController;
import software.wings.graphql.schema.type.QLEnvironment;
import software.wings.graphql.schema.type.QLEnvironment.QLEnvironmentBuilder;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.dataloader.MappedBatchLoader;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class EnvironmentBatchDataLoader implements MappedBatchLoader<String, QLEnvironment> {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public CompletionStage<Map<String, QLEnvironment>> load(Set<String> environmentIds) {
    return CompletableFuture.supplyAsync(() -> {
      Map<String, QLEnvironment> environmentMap = null;
      if (!CollectionUtils.isEmpty(environmentIds)) {
        environmentMap = getEnvironmentMap(environmentIds);
      } else {
        environmentMap = Collections.EMPTY_MAP;
      }
      return environmentMap;
    });
  }

  public Map<String, QLEnvironment> getEnvironmentMap(@NotNull Set<String> environmentIds) {
    Query<Environment> query = wingsPersistence.createQuery(Environment.class, excludeAuthority)
                                   .field(EnvironmentKeys.uuid)
                                   .in(environmentIds);
    Map<String, QLEnvironment> environmentMap = new HashMap<>();

    try (HIterator<Environment> environments = new HIterator<>(query.fetch())) {
      environments.forEach(environment -> {
        final QLEnvironmentBuilder builder = QLEnvironment.builder();
        EnvironmentController.populateEnvironment(environment, builder);
        environmentMap.put(environment.getUuid(), builder.build());
      });
    }
    return environmentMap;
  }
}

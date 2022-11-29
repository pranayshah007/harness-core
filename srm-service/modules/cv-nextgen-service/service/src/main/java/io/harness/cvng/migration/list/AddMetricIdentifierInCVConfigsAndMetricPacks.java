/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.models.VerificationType;
import io.harness.data.structure.CollectionUtils;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class AddMetricIdentifierInCVConfigsAndMetricPacks implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Adding identifiers for Metric Packs");
    Query<MetricPack> metricPackQuery = hPersistence.createQuery(MetricPack.class);
    try (HIterator<MetricPack> iterator = new HIterator<>(metricPackQuery.fetch())) {
      while (iterator.hasNext()) {
        MetricPack metricPack = iterator.next();

        metricPack.getMetrics().forEach(metricDefinition -> {
          if (isEmpty(metricDefinition.getIdentifier()) || containsUpperCaseLetter(metricDefinition.getIdentifier())) {
            String identifier = metricDefinition.getName().replaceAll(" ", "_").toLowerCase();
            identifier = identifier.replaceAll("\\(", "");
            identifier = identifier.replaceAll("\\)", "");
            metricDefinition.setIdentifier(identifier);
          }
        });
        hPersistence.save(metricPack);
        log.info("Identifier updation for metric pack {}, {}", metricPack.getProjectIdentifier(),
            metricPack.getIdentifier());
      }
    }

    log.info("Adding identifiers for metric pack in cvconfigs");
    Query<CVConfig> cvConfigQuery = hPersistence.createQuery(CVConfig.class);
    try (HIterator<CVConfig> iterator = new HIterator<>(cvConfigQuery.fetch())) {
      while (iterator.hasNext()) {
        CVConfig cvConfig = iterator.next();
        if (cvConfig.getVerificationType().equals(VerificationType.TIME_SERIES)) {
          MetricCVConfig metricCVConfig = (MetricCVConfig) cvConfig;
          Collection<AnalysisInfo> analysisInfos = CollectionUtils.emptyIfNull(metricCVConfig.getMetricInfos());
          Set<String> identifiers = analysisInfos.stream()
                                        .filter(analysisInfo
                                            -> analysisInfo.getLiveMonitoring().isEnabled()
                                                || analysisInfo.getDeploymentVerification().isEnabled())
                                        .map(analysisInfo -> analysisInfo.getIdentifier())
                                        .collect(Collectors.toSet());
          if (metricCVConfig.getMetricPack() != null && metricCVConfig.getMetricPack().getMetrics() != null) {
            metricCVConfig.getMetricPack().getMetrics().forEach(metricDefinition -> {
              if (metricDefinition != null && isEmpty(metricDefinition.getIdentifier())) {
                String identifier = metricDefinition.getName().replaceAll(" ", "_").toLowerCase();
                identifier = identifier.replaceAll("\\(", "");
                identifier = identifier.replaceAll("\\)", "");
                metricDefinition.setIdentifier(identifier);
              }
              if (analysisInfos.isEmpty()) {
                if (metricDefinition != null) {
                  metricDefinition.setIdentifier(metricDefinition.getIdentifier().toLowerCase());
                }
              } else {
                if (metricDefinition != null && !identifiers.contains(metricDefinition.getIdentifier())) {
                  if (identifiers.contains(metricDefinition.getIdentifier().toLowerCase())) {
                    log.info("Changing mismatched metric identifier to lowercase {}", cvConfig);
                    metricDefinition.setIdentifier(metricDefinition.getIdentifier().toLowerCase());
                  } else {
                    log.error("MetricDef identifier does not match the identifier list. Please check: {}", cvConfig);
                  }
                }
              }
            });

            hPersistence.save(cvConfig);
            log.info("Identifier updation for cvConfig {}, {}", cvConfig.getProjectIdentifier(), cvConfig.getUuid());
          }
        }
      }
    }
  }
  public boolean containsUpperCaseLetter(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (Character.isUpperCase(s.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.NA;
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.NA;
  }
}

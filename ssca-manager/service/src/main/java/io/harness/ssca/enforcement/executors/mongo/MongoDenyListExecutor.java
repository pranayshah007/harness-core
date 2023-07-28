/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.executors.mongo;

import io.harness.repositories.EnforcementResultRepo;
import io.harness.repositories.SBOMComponentRepo;
import io.harness.ssca.beans.DenyList;
import io.harness.ssca.beans.DenyList.DenyListItem;
import io.harness.ssca.enforcement.constants.ViolationType;
import io.harness.ssca.enforcement.rule.Engine;
import io.harness.ssca.enforcement.rule.IRuleExecutor;
import io.harness.ssca.enforcement.rule.IRuleInterpreter;
import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.services.EnforcementResultService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
public class MongoDenyListExecutor implements IRuleExecutor<DenyList> {
  IRuleInterpreter mongoInterpreter = new MongoInterpreter();
  @Inject SBOMComponentRepo sbomComponentRepo;

  @Inject EnforcementResultRepo enforcementResultRepo;
  @Inject EnforcementResultService enforcementResultService;

  @Override
  public List<EnforcementResultEntity> execute(Engine<DenyList> engine) {
    DenyList denyList = engine.getRules();
    List<EnforcementResultEntity> result = new ArrayList<>();

    for (DenyListItem denyListItem : denyList.getDenyListItems()) {
      Query query = mongoInterpreter.interpretRules(denyListItem, engine.getArtifact().getOrchestrationId(), null);
      log.info(String.format("Query is: %s", query));
      List<NormalizedSBOMComponentEntity> violatedComponents = sbomComponentRepo.findAllByQuery(query);

      result.addAll(enforcementResultService.getEnforcementResults(violatedComponents,
          ViolationType.DENYLIST_VIOLATION.getViolation(), enforcementResultService.getViolationDetails(denyListItem),
          engine.getArtifact(), engine.getEnforcementId()));
    }

    enforcementResultRepo.saveAll(result);

    return result;
  }
}

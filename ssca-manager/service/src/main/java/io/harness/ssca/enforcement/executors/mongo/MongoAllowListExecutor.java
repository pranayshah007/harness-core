/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.executors.mongo;

import io.harness.repositories.EnforcementResultRepo;
import io.harness.repositories.SBOMComponentRepo;
import io.harness.ssca.beans.AllowList;
import io.harness.ssca.beans.AllowList.AllowListRuleType;
import io.harness.ssca.enforcement.constants.ViolationType;
import io.harness.ssca.enforcement.rule.Engine;
import io.harness.ssca.enforcement.rule.IRuleExecutor;
import io.harness.ssca.enforcement.rule.IRuleInterpreter;
import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.services.EnforcementResultService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
public class MongoAllowListExecutor implements IRuleExecutor<AllowList> {
  IRuleInterpreter mongoInterpreter = new MongoInterpreter();
  @Inject SBOMComponentRepo sbomComponentRepo;

  @Inject EnforcementResultRepo enforcementResultRepo;
  @Inject EnforcementResultService enforcementResultService;

  @Override
  public List<EnforcementResultEntity> execute(Engine<AllowList> engine) {
    AllowList allowList = engine.getRules();
    List<EnforcementResultEntity> result = new ArrayList<>();

    if (allowList.getAllowListItem().getPurls() != null && allowList.getAllowListItem().getPurls().size() > 0) {
      result.addAll(executeAllowListRule(allowList, engine, AllowListRuleType.ALLOW_PURL_ITEM));
    }
    if (allowList.getAllowListItem().getLicenses() != null && allowList.getAllowListItem().getLicenses().size() > 0) {
      result.addAll(executeAllowListRule(allowList, engine, AllowListRuleType.ALLOW_LICENSE_ITEM));
    }
    if (allowList.getAllowListItem().getSuppliers() != null && allowList.getAllowListItem().getSuppliers().size() > 0) {
      result.addAll(executeAllowListRule(allowList, engine, AllowListRuleType.ALLOW_SUPPLIER_ITEM));
    }

    enforcementResultRepo.saveAll(result);

    return result;
  }

  private List<EnforcementResultEntity> executeAllowListRule(
      AllowList allowList, Engine<AllowList> engine, AllowListRuleType type) {
    List<EnforcementResultEntity> result = new ArrayList<>();

    Query query =
        mongoInterpreter.interpretRules(allowList.getAllowListItem(), engine.getArtifact().getOrchestrationId(), type);
    log.info(String.format("Query is: %s", query));
    List<NormalizedSBOMComponentEntity> violatedComponents = sbomComponentRepo.findAllByQuery(query);

    for (NormalizedSBOMComponentEntity component : violatedComponents) {
      String violationDetails =
          enforcementResultService.getViolationDetails(component, allowList.getAllowListItem(), type);
      result.addAll(enforcementResultService.getEnforcementResults(Collections.singletonList(component),
          ViolationType.ALLOWLIST_VIOLATION.getViolation(), violationDetails, engine.getArtifact(),
          engine.getEnforcementId()));
    }
    return result;
  }
}

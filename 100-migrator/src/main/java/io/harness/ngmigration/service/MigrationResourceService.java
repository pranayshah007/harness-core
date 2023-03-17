/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import static io.harness.ngmigration.utils.MigratorUtility.getMigrationInput;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngmigration.dto.ApplicationFilter;
import io.harness.ngmigration.dto.ConnectorFilter;
import io.harness.ngmigration.dto.Filter;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.PipelineFilter;
import io.harness.ngmigration.dto.SaveSummaryDTO;
import io.harness.ngmigration.dto.SecretFilter;
import io.harness.ngmigration.dto.SecretManagerFilter;
import io.harness.ngmigration.dto.ServiceFilter;
import io.harness.ngmigration.dto.SimilarWorkflowDetail;
import io.harness.ngmigration.dto.TemplateFilter;
import io.harness.ngmigration.dto.TriggerFilter;
import io.harness.ngmigration.dto.WorkflowFilter;
import io.harness.ngmigration.service.importer.AppImportService;
import io.harness.ngmigration.service.importer.ConnectorImportService;
import io.harness.ngmigration.service.importer.PipelineImportService;
import io.harness.ngmigration.service.importer.SecretManagerImportService;
import io.harness.ngmigration.service.importer.SecretsImportService;
import io.harness.ngmigration.service.importer.ServiceImportService;
import io.harness.ngmigration.service.importer.TemplateImportService;
import io.harness.ngmigration.service.importer.TriggerImportService;
import io.harness.ngmigration.service.importer.UsergroupImportService;
import io.harness.ngmigration.service.importer.WorkflowImportService;
import io.harness.ngmigration.service.workflow.WorkflowHandlerFactory;
import io.harness.persistence.HPersistence;

import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.WorkflowService;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class MigrationResourceService {
  @Inject private ConnectorImportService connectorImportService;
  @Inject private SecretManagerImportService secretManagerImportService;
  @Inject private SecretsImportService secretsImportService;
  @Inject private AppImportService appImportService;
  @Inject private ServiceImportService serviceImportService;
  @Inject private DiscoveryService discoveryService;
  @Inject private TemplateImportService templateImportService;
  @Inject private WorkflowImportService workflowImportService;
  @Inject private PipelineImportService pipelineImportService;
  @Inject private TriggerImportService triggerImportService;
  @Inject private WorkflowService workflowService;
  @Inject HPersistence hPersistence;
  @Inject WorkflowHandlerFactory workflowHandlerFactory;
  @Inject UsergroupImportService usergroupImportService;

  private DiscoveryResult discover(String authToken, ImportDTO importDTO) {
    // Migrate referenced entities as well.
    importDTO.setMigrateReferencedEntities(true);
    Filter filter = importDTO.getFilter();
    if (importDTO.getEntityType().equals(NGMigrationEntityType.USER_GROUP)) {
      return usergroupImportService.discover(authToken, importDTO);
    }
    if (filter instanceof ConnectorFilter) {
      return connectorImportService.discover(authToken, importDTO);
    }
    if (filter instanceof SecretManagerFilter) {
      return secretManagerImportService.discover(authToken, importDTO);
    }
    if (filter instanceof SecretFilter) {
      return secretsImportService.discover(authToken, importDTO);
    }
    if (filter instanceof ApplicationFilter) {
      return appImportService.discover(authToken, importDTO);
    }
    if (filter instanceof ServiceFilter) {
      return serviceImportService.discover(authToken, importDTO);
    }
    if (filter instanceof TemplateFilter) {
      return templateImportService.discover(authToken, importDTO);
    }
    if (filter instanceof WorkflowFilter) {
      return workflowImportService.discover(authToken, importDTO);
    }
    if (filter instanceof PipelineFilter) {
      return pipelineImportService.discover(authToken, importDTO);
    }
    if (filter instanceof TriggerFilter) {
      return triggerImportService.discover(authToken, importDTO);
    }
    return DiscoveryResult.builder().build();
  }

  public SaveSummaryDTO save(String authToken, ImportDTO importDTO) {
    DiscoveryResult discoveryResult = discover(authToken, importDTO);
    if (discoveryResult == null) {
      return SaveSummaryDTO.builder().build();
    }
    SaveSummaryDTO saveSummaryDTO =
        discoveryService.migrateEntity(authToken, getMigrationInput(importDTO), discoveryResult);
    postMigrationHandler(authToken, importDTO, discoveryResult, saveSummaryDTO);
    return saveSummaryDTO;
  }

  private void postMigrationHandler(
      String authToken, ImportDTO importDTO, DiscoveryResult discoveryResult, SaveSummaryDTO summaryDTO) {
    if (importDTO.getFilter() instanceof WorkflowFilter) {
      workflowImportService.postMigrationSteps(authToken, importDTO, discoveryResult, summaryDTO);
    }
    if (importDTO.getFilter() instanceof TriggerFilter) {
      triggerImportService.postMigrationSteps(authToken, importDTO, discoveryResult, summaryDTO);
    }
  }

  public StreamingOutput exportYaml(String authToken, ImportDTO importDTO) {
    return discoveryService.exportYamlFilesAsZip(getMigrationInput(importDTO), discover(authToken, importDTO));
  }

  public List<Set<SimilarWorkflowDetail>> listSimilarWorkflow(String accountId) {
    Stopwatch startTime = Stopwatch.createStarted();

    Map<String, Workflow> workflowMap = new HashMap<>();
    List<Workflow> workflowsWithAppId = hPersistence.createQuery(Workflow.class)
                                            .filter(WorkflowKeys.accountId, accountId)
                                            .project(WorkflowKeys.name, true)
                                            .project(WorkflowKeys.uuid, true)
                                            .project(WorkflowKeys.appId, true)
                                            .asList();
    int[] list = new int[workflowsWithAppId.size()];
    for (int i = 0; i < list.length; ++i) {
      list[i] = i;
    }
    Map<Pair<String, String>, Long> timeTakenMap = new HashMap<>();
    Stopwatch similarityStartTime = Stopwatch.createStarted();
    for (int i = 0; i < workflowsWithAppId.size(); ++i) {
      for (int j = i + 1; j < workflowsWithAppId.size(); ++j) {
        Stopwatch innerLoopStartTime = Stopwatch.createStarted();
        if (!areConnected(list, i, j)) {
          Workflow workflow1 =
              getWorkflow(workflowMap, workflowsWithAppId.get(i).getUuid(), workflowsWithAppId.get(i).getAppId());
          Workflow workflow2 =
              getWorkflow(workflowMap, workflowsWithAppId.get(j).getUuid(), workflowsWithAppId.get(j).getAppId());
          if (workflowHandlerFactory.areSimilar(workflow1, workflow2)) {
            connect(list, i, j);
          }
        }
        timeTakenMap.put(Pair.of(workflowsWithAppId.get(i).getUuid(), workflowsWithAppId.get(j).getUuid()),
            innerLoopStartTime.elapsed(TimeUnit.MILLISECONDS));
      }
    }
    long similarityTimeTaken = similarityStartTime.elapsed(TimeUnit.MILLISECONDS);
    Stopwatch similarGroupingStartTime = Stopwatch.createStarted();
    Map<Integer, Set<SimilarWorkflowDetail>> similarWorkflows = new HashMap<>();
    for (int i = 0; i < list.length; ++i) {
      Set<SimilarWorkflowDetail> ids = similarWorkflows.getOrDefault(list[i], new HashSet<>());
      Workflow wf = workflowsWithAppId.get(i);
      ids.add(SimilarWorkflowDetail.builder()
                  .workflowName(wf.getName())
                  .appId(wf.getAppId())
                  .workflowId(wf.getUuid())
                  .build());
      similarWorkflows.put(list[i], ids);
    }
    log.info(
        "Total time for processing similarity: [{}], similar group: [{}], and overall total time: [{}], total workflows: [{}]",
        similarityTimeTaken, similarGroupingStartTime.elapsed(TimeUnit.MILLISECONDS),
        startTime.elapsed(TimeUnit.MILLISECONDS), workflowsWithAppId.size());

    log.info("Detailed start time {}", timeTakenMap);

    return similarWorkflows.values().stream().filter(set -> set.size() > 1).collect(Collectors.toList());
  }

  private Workflow getWorkflow(Map<String, Workflow> workflowMap, String workflowId, String appId) {
    if (workflowMap.containsKey(workflowId)) {
      return workflowMap.get(workflowId);
    }
    Workflow workflow = workflowService.readWorkflow(appId, workflowId);
    workflowMap.put(workflowId, workflow);
    return workflow;
  }

  private void connect(int[] list, int i, int j) {
    if (list[i] != list[j]) {
      int temp = list[i];
      list[i] = list[j];
      while (list[temp] != list[j]) {
        temp = list[temp];
        list[temp] = list[j];
      }
    }
  }

  private boolean areConnected(int[] list, int i, int j) {
    return list[i] == list[j];
  }
}

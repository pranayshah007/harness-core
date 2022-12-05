/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.validation.Validator.notNullCheck;

import static software.wings.sm.StepType.HTTP;

import io.harness.beans.KeyValuePair;
import io.harness.exception.ExceptionUtils;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.serializer.JsonUtils;

import software.wings.beans.Account;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateType;
import software.wings.beans.template.VersionedTemplate;
import software.wings.beans.template.command.HttpTemplate;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.template.TemplateService;

import com.google.common.base.Splitter;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;

@Slf4j
public class ConvertHttpHeadersStringTypeToList implements Migration {
  private static final String LOG_PREFIX = "CONVERT_HTTP_HEADER_TO_LIST_MIGRATION:";
  private static final Splitter HEADERS_SPLITTER = Splitter.on(",").trimResults().omitEmptyStrings();
  private static final Splitter HEADER_SPLITTER = Splitter.on(":").trimResults();

  @Inject AccountService accountService;
  @Inject WorkflowService workflowService;
  @Inject WingsPersistence wingsPersistence;
  @Inject TemplateService templateService;

  @Override
  public void migrate() {
    List<Account> allAccounts = accountService.getAccountsWithBasicInfo(false);
    for (Account account : allAccounts) {
      migrateForAccount(account);
    }
    log.info(
        String.format("%s Converting Http Headers from String to List completed for all the workflows", LOG_PREFIX));
  }

  private void migrateForAccount(Account account) {
    String accountId = account.getUuid();
    log.info(String.format(
        "%s Starting migration of Http template Headers from String to List for accountId: %s", LOG_PREFIX, accountId));

    try (HIterator<Template> templates = new HIterator<>(wingsPersistence.createQuery(Template.class)
                                                             .field("accountId")
                                                             .equal(accountId)
                                                             .field("type")
                                                             .equal(TemplateType.HTTP.name())
                                                             .fetch())) {
      for (Template t : templates) {
        String tid = t.getUuid();
        log.info(String.format(
            "%s Starting migration of Http Headers from String to List for template: %s", LOG_PREFIX, tid));
        try (HIterator<VersionedTemplate> versionedTemplates =
                 new HIterator<>(wingsPersistence.createQuery(VersionedTemplate.class)
                                     .field("accountId")
                                     .equal(accountId)
                                     .field("templateId")
                                     .equal(tid)
                                     .fetch())) {
          for (VersionedTemplate versionedTemplate : versionedTemplates) {
            if (migrateTemplate(versionedTemplate)) {
              wingsPersistence.save(versionedTemplate);
            }
          }
        } catch (Exception e) {
          log.error(String.format("%s Migration of Http Headers from String to List failed for template: %s due to: %s",
                        LOG_PREFIX, tid, ExceptionUtils.getMessage(e)),
              e);
        }
      }
    }

    log.info(String.format(
        "%s Starting migration of Http Headers from String to List for accountId: %s", LOG_PREFIX, accountId));

    List<Workflow> workflows = WorkflowAndPipelineMigrationUtils.fetchAllWorkflowsForAccount(
        wingsPersistence, workflowService, account.getUuid());

    for (Workflow workflow : workflows) {
      try {
        log.info(String.format("%s Starting migration of Http Headers from String to List for workflow: %s", LOG_PREFIX,
            workflow.getUuid()));
        if (migrateWorkflow(workflow)) {
          workflowService.updateWorkflow(workflow, true);
        }
      } catch (Exception e) {
        log.error(String.format("%s Migration of Http Headers from String to List failed for workflow: %s due to: %s",
                      LOG_PREFIX, workflow.getUuid(), ExceptionUtils.getMessage(e)),
            e);
      }
    }
  }

  private boolean migrateTemplate(VersionedTemplate template) {
    HttpTemplate httpTemplate = (HttpTemplate) template.getTemplateObject();
    if (StringUtils.isBlank(httpTemplate.getHeader())) {
      return false;
    }
    List<KeyValuePair> headers = new ArrayList<>();
    for (String header : HEADERS_SPLITTER.split(httpTemplate.getHeader())) {
      List<String> headerPair = HEADER_SPLITTER.splitToList(header);
      if (headerPair.size() == 2) {
        headers.add(KeyValuePair.builder().key(headerPair.get(0)).value(headerPair.get(1)).build());
      }
    }
    httpTemplate.setHeaders(headers);
    return true;
  }

  private boolean migrateWorkflow(Workflow workflow) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    notNullCheck(
        "orchestrationWorkflow is null in workflow: " + workflow.getUuid(), workflow.getOrchestrationWorkflow());

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow = (CanaryOrchestrationWorkflow) orchestrationWorkflow;
    boolean updated = false;
    if (canaryOrchestrationWorkflow.getPreDeploymentSteps() != null
        && canaryOrchestrationWorkflow.getPreDeploymentSteps().getSteps() != null) {
      for (GraphNode node : canaryOrchestrationWorkflow.getPreDeploymentSteps().getSteps()) {
        updated = updateGraphNode(node) || updated;
      }
    }

    if (canaryOrchestrationWorkflow.getPostDeploymentSteps().getSteps() != null) {
      for (GraphNode node : canaryOrchestrationWorkflow.getPostDeploymentSteps().getSteps()) {
        updated = updateGraphNode(node) || updated;
      }
    }

    if (canaryOrchestrationWorkflow.getWorkflowPhases() != null) {
      for (WorkflowPhase phase : canaryOrchestrationWorkflow.getWorkflowPhases()) {
        updated = updatePhase(phase) || updated;
      }
    }

    if (canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap() != null) {
      for (Map.Entry<String, WorkflowPhase> phaseEntry :
          canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap().entrySet()) {
        updated = updatePhase(phaseEntry.getValue()) || updated;
      }
    }
    return updated;
  }

  private boolean updatePhase(WorkflowPhase phase) {
    boolean workflowModified = false;
    if (phase.getPhaseSteps() != null) {
      for (PhaseStep phaseStep : phase.getPhaseSteps()) {
        if (phaseStep.getSteps() != null) {
          for (GraphNode node : phaseStep.getSteps()) {
            workflowModified = updateGraphNode(node) || workflowModified;
          }
        }
      }
    }

    if (phase.getPhaseSteps() != null) {
      for (PhaseStep phaseStep : phase.getPhaseSteps()) {
        if (phaseStep.getSteps() != null) {
          for (GraphNode node : phaseStep.getSteps()) {
            workflowModified = updateGraphNode(node) || workflowModified;
          }
        }
      }
    }

    return workflowModified;
  }

  private boolean updateGraphNode(GraphNode node) {
    if (HTTP.name().equals(node.getType())) {
      String headerConfig = (String) node.getProperties().getOrDefault("header", null);
      List<Document> headers = new ArrayList<>();
      if (headerConfig != null) {
        for (String header : HEADERS_SPLITTER.split(headerConfig)) {
          List<String> headerPair = HEADER_SPLITTER.splitToList(header);
          if (headerPair.size() == 2) {
            headers.add(Document.parse(
                JsonUtils.asJson(KeyValuePair.builder().key(headerPair.get(0)).value(headerPair.get(1)).build())));
          }
        }
        node.getProperties().put("headers", headers);
        return true;
      }
    }
    return false;
  }
}

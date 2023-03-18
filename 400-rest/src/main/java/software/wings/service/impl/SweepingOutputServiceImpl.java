/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;

import static java.lang.String.format;

import io.harness.beans.SweepingOutput;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.beans.SweepingOutputInstance.SweepingOutputInstanceBuilder;
import io.harness.beans.SweepingOutputInstance.SweepingOutputInstanceKeys;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.serializer.KryoSerializer;

import software.wings.api.InstanceElement;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.beans.WorkflowExecution;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiryController;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.StateExecutionInstance;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.DuplicateKeyException;
import dev.morphia.query.CriteriaContainer;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@ValidateOnExecution
@Singleton
@Slf4j
public class SweepingOutputServiceImpl implements SweepingOutputService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private WorkflowExecutionService workflowExecutionService;

  @Override
  public SweepingOutputInstance save(SweepingOutputInstance sweepingOutputInstance) {
    try {
      if (sweepingOutputInstance.getValue() != null && isEmpty(sweepingOutputInstance.getValueOutput())) {
        sweepingOutputInstance.setValueOutput(kryoSerializer.asBytes(sweepingOutputInstance.getValue()));
      }
      wingsPersistence.save(sweepingOutputInstance);
      return sweepingOutputInstance;
    } catch (DuplicateKeyException exception) {
      log.error(
          "Details of Sweeping output instance which was tried to be saved again are ID:{}, name:{}, appId: {}, phaseExecutionId: {}, workflowExecutionIds: {}, pipelineExecutionId: {}, stateExecutionId: {}",
          sweepingOutputInstance.getUuid(), sweepingOutputInstance.getName(), sweepingOutputInstance.getAppId(),
          sweepingOutputInstance.getPhaseExecutionId(), sweepingOutputInstance.getWorkflowExecutionIds().toString(),
          sweepingOutputInstance.getPipelineExecutionId(), sweepingOutputInstance.getStateExecutionId());
      throw new InvalidRequestException(
          format(
              "Output with name %s, already saved in the context. Please ensure that there are no duplicate output variable names within the workflow/pipeline scope.",
              sweepingOutputInstance.getName()),
          exception);
    }
  }

  @Override
  public void ensure(SweepingOutputInstance sweepingOutputInstance) {
    wingsPersistence.saveIgnoringDuplicateKeys(Collections.singletonList(sweepingOutputInstance));
  }

  @Override
  public void cleanForStateExecutionInstance(StateExecutionInstance stateExecutionInstance) {
    SweepingOutputInquiry sweepingOutputInquiry =
        SweepingOutputInquiryController.obtainFromStateExecutionInstanceWithoutName(stateExecutionInstance);
    final Query<SweepingOutputInstance> query =
        wingsPersistence.createQuery(SweepingOutputInstance.class)
            .filter(SweepingOutputInstanceKeys.appId, sweepingOutputInquiry.getAppId())
            .filter(SweepingOutputInstanceKeys.stateExecutionId, stateExecutionInstance.getUuid());
    wingsPersistence.delete(query);
  }

  @Override
  public void deleteById(String appId, String uuid) {
    final Query<SweepingOutputInstance> query = wingsPersistence.createQuery(SweepingOutputInstance.class)
                                                    .filter(SweepingOutputInstanceKeys.appId, appId)
                                                    .filter(SweepingOutputInstanceKeys.uuid, uuid);
    wingsPersistence.delete(query);
  }

  @Override
  public void copyOutputsForAnotherWorkflowExecution(
      String appId, String fromWorkflowExecutionId, String toWorkflowExecutionId) {
    if (fromWorkflowExecutionId.equals(toWorkflowExecutionId)) {
      return;
    }
    final Query<SweepingOutputInstance> query =
        wingsPersistence.createQuery(SweepingOutputInstance.class)
            .filter(SweepingOutputInstanceKeys.appId, appId)
            .filter(SweepingOutputInstanceKeys.workflowExecutionIds, fromWorkflowExecutionId);

    UpdateOperations<SweepingOutputInstance> ops =
        wingsPersistence.createUpdateOperations(SweepingOutputInstance.class);
    ops.addToSet(SweepingOutputInstanceKeys.workflowExecutionIds, toWorkflowExecutionId);
    wingsPersistence.update(query, ops);
  }

  @Override
  public Query<SweepingOutputInstance> prepareApprovalStateOutputsQuery(
      String appId, String fromPipelineExecutionId, String fromStateExecutionId) {
    return wingsPersistence.createQuery(SweepingOutputInstance.class)
        .filter(SweepingOutputInstanceKeys.appId, appId)
        .filter(SweepingOutputInstanceKeys.pipelineExecutionId, fromPipelineExecutionId)
        .filter(SweepingOutputInstanceKeys.stateExecutionId, fromStateExecutionId);
  }

  @Override
  public Query<SweepingOutputInstance> prepareEnvStateOutputsQuery(
      String appId, String fromPipelineExecutionId, String fromWorkflowExecutionId) {
    return wingsPersistence.createQuery(SweepingOutputInstance.class)
        .filter(SweepingOutputInstanceKeys.appId, appId)
        .filter(SweepingOutputInstanceKeys.pipelineExecutionId, fromPipelineExecutionId)
        .filter(SweepingOutputInstanceKeys.workflowExecutionIds, fromWorkflowExecutionId);
  }

  @Override
  public <T extends SweepingOutput> T findSweepingOutput(SweepingOutputInquiry inquiry) {
    SweepingOutputInstance sweepingOutputInstance = find(inquiry);
    if (sweepingOutputInstance == null) {
      return null;
    }
    if (!isEmpty(sweepingOutputInstance.getValueOutput())) {
      return (T) kryoSerializer.asObject(sweepingOutputInstance.getValueOutput());
    }
    return (T) sweepingOutputInstance.getValue();
  }

  @Override
  public <T extends SweepingOutput> List<T> findSweepingOutputsWithNamePrefix(
      SweepingOutputInquiry inquiry, Scope scope) {
    List<SweepingOutputInstance> sweepingOutputInstances = findManyWithNamePrefix(inquiry, scope);
    if (sweepingOutputInstances == null) {
      return null;
    }
    return sweepingOutputInstances.stream()
        .map(soi -> {
          if (!isEmpty(soi.getValueOutput())) {
            return (T) kryoSerializer.asObject(soi.getValueOutput());
          }
          return (T) soi.getValue();
        })
        .collect(Collectors.toList());
  }

  @Override
  public List<InstanceDetails> fetchInstanceDetailsFromSweepingOutput(
      SweepingOutputInquiry inquiry, boolean newInstancesOnly) {
    List<InstanceDetails> instanceDetails = new ArrayList<>();
    SweepingOutput sweepingOutput = findSweepingOutput(inquiry);
    if (sweepingOutput instanceof InstanceInfoVariables) {
      InstanceInfoVariables instanceInfoVariables = (InstanceInfoVariables) sweepingOutput;
      if (isNotEmpty(instanceInfoVariables.getInstanceElements())) {
        instanceDetails.addAll(instanceInfoVariables.getInstanceDetails());
        if (newInstancesOnly) {
          instanceDetails = instanceDetails.stream()
                                .filter(instanceDetail -> instanceDetail.isNewInstance())
                                .collect(Collectors.toList());
        }
      }
    }

    return instanceDetails;
  }

  @Override
  public List<InstanceDetails> findInstanceDetailsForWorkflowExecution(String appId, String workflowExecutionId) {
    List<SweepingOutput> sweepingOutputs =
        findSweepingOutputsWithNamePrefix(SweepingOutputInquiry.builder()
                                              .workflowExecutionId(workflowExecutionId)
                                              .appId(appId)
                                              .name(InstanceInfoVariables.SWEEPING_OUTPUT_NAME)
                                              .build(),
            SweepingOutputInstance.Scope.WORKFLOW);

    Set<String> hosts = new HashSet<>();
    List<InstanceDetails> instanceDetails = new ArrayList<>();
    sweepingOutputs.forEach(sweepingOutput -> {
      Preconditions.checkState(sweepingOutput instanceof InstanceInfoVariables,
          "sweepingOutput should be an instanceOf InstanceInfoVariables");
      InstanceInfoVariables instanceInfoVariables = (InstanceInfoVariables) sweepingOutput;
      instanceInfoVariables.getInstanceDetails().forEach(instanceDetail -> {
        if (!hosts.contains(instanceDetail.getHostName())) {
          instanceDetails.add(instanceDetail);
          hosts.add(instanceDetail.getHostName());
        }
      });
    });
    return instanceDetails;
  }

  @Override
  public List<InstanceElement> fetchInstanceElementsFromSweepingOutput(
      SweepingOutputInquiry inquiry, boolean newInstancesOnly) {
    List<InstanceElement> elements = new ArrayList<>();
    SweepingOutput sweepingOutput = findSweepingOutput(inquiry);
    if (sweepingOutput instanceof InstanceInfoVariables) {
      InstanceInfoVariables instanceInfoVariables = (InstanceInfoVariables) sweepingOutput;
      if (isNotEmpty(instanceInfoVariables.getInstanceElements())) {
        elements.addAll(instanceInfoVariables.getInstanceElements());
        if (newInstancesOnly) {
          elements =
              elements.stream().filter(instanceElement -> instanceElement.isNewInstance()).collect(Collectors.toList());
        }
      }
    }

    return elements;
  }

  @Override
  public SweepingOutputInstance find(SweepingOutputInquiry sweepingOutputInquiry) {
    final Query<SweepingOutputInstance> query =
        wingsPersistence.createQuery(SweepingOutputInstance.class)
            .filter(SweepingOutputInstanceKeys.appId, sweepingOutputInquiry.getAppId())
            .filter(SweepingOutputInstanceKeys.name, sweepingOutputInquiry.getName());

    addFilters(sweepingOutputInquiry, query);
    SweepingOutputInstance instance = query.get();
    if (instance != null && !isEmpty(instance.getValueOutput())) {
      instance.setValue((SweepingOutput) kryoSerializer.asObject(instance.getValueOutput()));
      return instance;
    }
    return instance;
  }

  @Override
  public List<SweepingOutputInstance> findManyWithNamePrefix(SweepingOutputInquiry sweepingOutputInquiry, Scope scope) {
    final Query<SweepingOutputInstance> query =
        wingsPersistence.createQuery(SweepingOutputInstance.class)
            .filter(SweepingOutputInstanceKeys.appId, sweepingOutputInquiry.getAppId())
            .field(SweepingOutputInstanceKeys.name)
            .startsWith(sweepingOutputInquiry.getName())
            .order(Sort.ascending(SweepingOutputInstanceKeys.createdAt));

    addFiltersWithScope(sweepingOutputInquiry, query, scope);
    return query.asList();
  }

  private void addFiltersWithScope(
      SweepingOutputInquiry sweepingOutputInquiry, Query<SweepingOutputInstance> query, Scope scope) {
    CriteriaContainer criteria = null;
    switch (scope) {
      case PIPELINE:
        if (sweepingOutputInquiry.getPipelineExecutionId() != null) {
          criteria = query.criteria(SweepingOutputInstanceKeys.pipelineExecutionId)
                         .equal(sweepingOutputInquiry.getPipelineExecutionId());
        }
        break;
      case WORKFLOW:
        criteria = query.criteria(SweepingOutputInstanceKeys.workflowExecutionIds)
                       .equal(sweepingOutputInquiry.getWorkflowExecutionId());
        break;
      case PHASE:
        criteria = query.criteria(SweepingOutputInstanceKeys.phaseExecutionId)
                       .equal(sweepingOutputInquiry.getPhaseExecutionId());
        break;
      case STATE:
        criteria = query.criteria(SweepingOutputInstanceKeys.stateExecutionId)
                       .equal(sweepingOutputInquiry.getStateExecutionId());
        break;
      default:
        log.error("Invalid scope", scope);
    }
    if (criteria != null) {
      query.and(criteria);
    }
  }

  private void addFilters(SweepingOutputInquiry sweepingOutputInquiry, Query<SweepingOutputInstance> query) {
    ArrayList<CriteriaContainer> criteriaContainers = new ArrayList<>();
    final CriteriaContainer workflowCriteria = query.criteria(SweepingOutputInstanceKeys.workflowExecutionIds)
                                                   .equal(sweepingOutputInquiry.getWorkflowExecutionId());
    criteriaContainers.add(workflowCriteria);

    final CriteriaContainer phaseCriteria =
        query.criteria(SweepingOutputInstanceKeys.phaseExecutionId).equal(sweepingOutputInquiry.getPhaseExecutionId());
    criteriaContainers.add(phaseCriteria);

    final CriteriaContainer stateCriteria =
        query.criteria(SweepingOutputInstanceKeys.stateExecutionId).equal(sweepingOutputInquiry.getStateExecutionId());
    criteriaContainers.add(stateCriteria);

    if (sweepingOutputInquiry.getPipelineExecutionId() != null) {
      final CriteriaContainer pipelineCriteria = query.criteria(SweepingOutputInstanceKeys.pipelineExecutionId)
                                                     .equal(sweepingOutputInquiry.getPipelineExecutionId());
      criteriaContainers.add(pipelineCriteria);
    }

    if (sweepingOutputInquiry.getIsOnDemandRollback() != null && sweepingOutputInquiry.getIsOnDemandRollback()) {
      addFiltersForOnDemandRollback(sweepingOutputInquiry, query, criteriaContainers);
    }
    query.or(criteriaContainers.toArray(new CriteriaContainer[criteriaContainers.size()]));
  }

  private void addFiltersForOnDemandRollback(SweepingOutputInquiry sweepingOutputInquiry,
      Query<SweepingOutputInstance> query, ArrayList<CriteriaContainer> criteriaContainers) {
    final WorkflowExecution currentWorkflowExecution =
        workflowExecutionService.fetchWorkflowExecution(sweepingOutputInquiry.getAppId(),
            sweepingOutputInquiry.getWorkflowExecutionId(), WorkflowExecutionKeys.originalExecution);
    if (currentWorkflowExecution.getOriginalExecution() != null
        && currentWorkflowExecution.getOriginalExecution().getExecutionId() != null) {
      final WorkflowExecution originalWorkflowExecution = workflowExecutionService.fetchWorkflowExecution(
          sweepingOutputInquiry.getAppId(), currentWorkflowExecution.getOriginalExecution().getExecutionId(),
          WorkflowExecutionKeys.pipelineExecutionId);
      final String pipelineExecutionId = originalWorkflowExecution.getPipelineExecutionId();
      if (pipelineExecutionId != null) {
        final CriteriaContainer originalPipelineCriteria =
            query.criteria(SweepingOutputInstanceKeys.pipelineExecutionId).equal(pipelineExecutionId);
        criteriaContainers.add(originalPipelineCriteria);
      }
    }
  }

  public static SweepingOutputInstanceBuilder prepareSweepingOutputBuilder(String appId, String pipelineExecutionId,
      String workflowExecutionId, String phaseExecutionId, String stateExecutionId,
      SweepingOutputInstance.Scope sweepingOutputScope) {
    // Default scope is pipeline

    if (pipelineExecutionId == null || Scope.PIPELINE != sweepingOutputScope) {
      pipelineExecutionId = "dummy-" + generateUuid();
    }
    if (workflowExecutionId == null
        || (Scope.PIPELINE != sweepingOutputScope && Scope.WORKFLOW != sweepingOutputScope)) {
      workflowExecutionId = "dummy-" + generateUuid();
    }
    if (phaseExecutionId == null || Scope.STATE == sweepingOutputScope) {
      phaseExecutionId = "dummy-" + generateUuid();
    }
    if (stateExecutionId == null) {
      stateExecutionId = "dummy-" + generateUuid();
    }

    return SweepingOutputInstance.builder()
        .uuid(generateUuid())
        .appId(appId)
        .pipelineExecutionId(pipelineExecutionId)
        .workflowExecutionId(workflowExecutionId)
        .phaseExecutionId(phaseExecutionId)
        .stateExecutionId(stateExecutionId);
  }

  @Override
  public void pruneByApplication(String appId) {
    wingsPersistence.delete(
        wingsPersistence.createQuery(SweepingOutputInstance.class).filter(SweepingOutputInstanceKeys.appId, appId));
  }
}

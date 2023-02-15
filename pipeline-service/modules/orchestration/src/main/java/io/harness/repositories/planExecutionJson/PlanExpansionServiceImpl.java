package io.harness.repositories.planExecutionJson;

import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecutionExpansion;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.ejb.Singleton;
import javax.inject.Inject;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
public class PlanExpansionServiceImpl implements PlanExpansionService {
  @Inject PlanExecutionExpansionRepository planExecutionExpansionRepository;

  @Override
  public void addInputsToJson(Ambiance ambiance, PmsStepParameters stepInputs) {
    if (AmbianceUtils.obtainCurrentLevel(ambiance).getSkipExpressionChain()) {
      return;
    }
    Criteria criteria = Criteria.where("planExecutionId").is(ambiance.getPlanExecutionId());
    Query query = new Query(criteria);
    Update update = new Update();

    update.set(getKeyForUpdate(ambiance) + ".resolvedParams",
        Document.parse(RecastOrchestrationUtils.pruneRecasterAdditions(stepInputs.clone())));
    planExecutionExpansionRepository.update(query, update);
  }

  @Override
  public void addBasicInformationToJson(NodeExecution nodeExecution) {
    if (AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()).getSkipExpressionChain()) {
      return;
    }
    Criteria criteria = Criteria.where("planExecutionId").is(nodeExecution.getPlanExecutionId());
    Query query = new Query(criteria);
    Update update = new Update();
    String key = getKeyForUpdate(nodeExecution.getAmbiance());
    update.set(key + ".name", nodeExecution.getName());
    update.set(key + ".identifier", nodeExecution.getName());
    planExecutionExpansionRepository.update(query, update);
  }

  @Override
  public void addOutcomesToJson(Ambiance ambiance, String name, PmsOutcome outcome) {
    if (AmbianceUtils.obtainCurrentLevel(ambiance).getSkipExpressionChain()) {
      return;
    }
    Criteria criteria = Criteria.where("planExecutionId").is(ambiance.getPlanExecutionId());
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(getKeyForUpdate(ambiance) + ".outcome." + name,
        Document.parse(RecastOrchestrationUtils.pruneRecasterAdditions(outcome.clone())));

    planExecutionExpansionRepository.update(query, update);
  }

  @Override
  public void createPlanExpansionEntity(String planExecutionId) {
    planExecutionExpansionRepository.save(PlanExecutionExpansion.builder().planExecutionId(planExecutionId).build());
  }

  private String getKeyForUpdate(Ambiance ambiance) {
    List<Level> levels = ambiance.getLevelsList();
    List<String> keyList = new ArrayList<>();
    keyList.add("expandedJson");
    for (Level level : levels) {
      if (!level.getSkipExpressionChain()) {
        keyList.add(level.getIdentifier());
      }
    }
    return keyList.stream().collect(Collectors.joining("."));
  }

  @Override
  public String resolveExpression(String planExecutionId, String expression) {
    Criteria criteria = Criteria.where("planExecutionId").is(planExecutionId);
    Query query = new Query(criteria);
    query.fields().include("expandedJson." + expression);
    return RecastOrchestrationUtils.toSimpleJson(planExecutionExpansionRepository.find(query).getExpandedJson());
  }
}

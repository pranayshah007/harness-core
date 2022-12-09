/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.EXISTS;
import static io.harness.beans.SearchFilter.Operator.NOT_EQ;
import static io.harness.beans.SearchFilter.Operator.OR;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.api.AwsLambdaContextElement.AWS_LAMBDA_REQUEST_PARAM;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.context.ContextElementType;

import software.wings.api.AwsLambdaContextElement;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityKeys;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Tag;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.ArtifactService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mongodb.morphia.annotations.Transient;

@OwnedBy(CDP)
public class AwsLambdaRollback extends AwsLambdaState {
  @Inject @Transient protected transient ArtifactService artifactService;

  public AwsLambdaRollback(String name) {
    super(name, StateType.AWS_LAMBDA_ROLLBACK.name());
  }

  @Override
  protected Artifact getArtifact(String appId, String serviceId, String workflowExecutionId, String envId,
      DeploymentExecutionContext deploymentExecutionContext, String infrastructureDefinitionId) {
    SearchFilter op1 = SearchFilter.builder()
                           .fieldName(ActivityKeys.infrastructureDefinitionId)
                           .op(SearchFilter.Operator.NOT_EXISTS)
                           .build();

    SearchFilter op2 = SearchFilter.builder()
                           .fieldName(ActivityKeys.infrastructureDefinitionId)
                           .op(SearchFilter.Operator.EQ)
                           .fieldValues(new Object[] {infrastructureDefinitionId})
                           .build();

    PageResponse<Activity> pageResponse =
        activityService.list(aPageRequest()
                                 .withLimit("1")
                                 .addFilter(ActivityKeys.appId, EQ, appId)
                                 .addFilter(ActivityKeys.serviceId, EQ, serviceId)
                                 .addFilter(ActivityKeys.environmentId, EQ, envId)
                                 .addFilter(ActivityKeys.status, EQ, ExecutionStatus.SUCCESS)
                                 .addFilter(ActivityKeys.workflowExecutionId, NOT_EQ, workflowExecutionId)
                                 .addFilter(ActivityKeys.artifactId, EXISTS)
                                 .addFilter(ActivityKeys.infrastructureDefinitionId, OR, op1, op2)
                                 .build());
    if (isNotEmpty(pageResponse)) {
      return artifactService.get(pageResponse.getResponse().get(0).getArtifactId());
    }
    return null;
  }

  @Override
  protected List<String> getEvaluatedAliases(ExecutionContext context) {
    AwsLambdaContextElement awsLambdaContextElement =
        context.getContextElement(ContextElementType.PARAM, AWS_LAMBDA_REQUEST_PARAM);
    if (awsLambdaContextElement != null) {
      List<String> aliases = awsLambdaContextElement.getAliases();
      if (isNotEmpty(aliases)) {
        return aliases.stream().map(context::renderExpression).collect(toList());
      }
    }
    return emptyList();
  }

  @Override
  protected Map<String, String> getFunctionTags(ExecutionContext context) {
    AwsLambdaContextElement awsLambdaContextElement =
        context.getContextElement(ContextElementType.PARAM, AWS_LAMBDA_REQUEST_PARAM);
    if (awsLambdaContextElement != null) {
      List<Tag> tags = awsLambdaContextElement.getTags();
      if (isNotEmpty(tags)) {
        Map<String, String> functionTags = new HashMap<>();
        tags.forEach(tag -> { functionTags.put(tag.getKey(), context.renderExpression(tag.getValue())); });
        return functionTags;
      }
    }
    return emptyMap();
  }

  @Override
  @SchemaIgnore
  public List<String> getAliases() {
    return super.getAliases();
  }

  @Override
  @SchemaIgnore
  public String getCommandName() {
    return super.getCommandName();
  }

  @Override
  @SchemaIgnore
  public List<Tag> getTags() {
    return super.getTags();
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.execution.expansion;


import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.expressions.functors.ExpandedJsonFunctorUtils;
import io.harness.execution.ExpressionDetailRequest;
import io.harness.execution.ExpressionDetailResponse;
import io.harness.execution.ExpressionDetails;
import io.harness.execution.ExpressionTestDetails;
import io.harness.execution.ExpressionTestRequest;
import io.harness.execution.ExpressionTestResponse;
import io.harness.yaml.utils.JsonPipelineUtils;

import javax.ejb.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Singleton
public class ExpressionTestServiceImpl implements ExpressionTestService {
    @Inject PlanExpansionService expansionService;
    @Override
    public ExpressionDetailResponse getExpressionResponse(String planExecutionId, String expression) {
        String expandedJson = expansionService.get(planExecutionId);
        JsonNode jsonNode = JsonPipelineUtils.readTree(expandedJson);
        return ExpressionDetailResponse.builder().expressionDetails(getAllExpressions(jsonNode,expression)).build();
    }

    public List<ExpressionDetails> getAllExpressions(JsonNode jsonNode, String expression){
        List<ExpressionDetails> finalExpList = new ArrayList<>();
        JsonNode currentJsonPointer = jsonNode;
        //pipeline.stages.stage1.spec.execution.steps.step1.spec.name
        List<String> expressionKeys = Arrays.asList(expression.split("\\."));
        for(int index = 0; index<expressionKeys.size()-1;index++){
            if(currentJsonPointer.get(expressionKeys.get(index)).get("group") != null){
                String group = currentJsonPointer.get(expressionKeys.get(index)).get("group").asText();
                String currentExp = String.join(".",expressionKeys.subList(index,expressionKeys.size()));
                finalExpList.add(ExpressionDetails.builder().scope(group).expressionBlock(currentExp).build());
                if(isCurrentKeyAnId(currentJsonPointer.get(expressionKeys.get(index)),expressionKeys.get(index))){
                    currentExp = group.toLowerCase() +"." + String.join(".",expressionKeys.subList(index+1,expressionKeys.size()));
                    finalExpList.add(ExpressionDetails.builder().scope(group).expressionBlock(currentExp).build());
                }
            }
            currentJsonPointer = currentJsonPointer.get(expressionKeys.get(index));
        }
        return finalExpList;
    }

    private boolean isCurrentKeyAnId(JsonNode jsonNode, String key){
        return jsonNode.get("identifier")!=null && key.equals(jsonNode.get("identifier").asText()) ||
                jsonNode.get("stepInputs") != null &&jsonNode.get("stepInputs").get("identifier")!=null && key.equals(jsonNode.get("stepInputs").get("identifier").asText());
    }
}

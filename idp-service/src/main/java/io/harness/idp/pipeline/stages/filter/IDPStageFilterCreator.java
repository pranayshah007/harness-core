/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.pipeline.stages.filter;

import com.google.common.collect.ImmutableSet;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.filters.FilterCreatorHelper;
import io.harness.filters.GenericStageFilterJsonCreatorV2;
import io.harness.idp.pipeline.stages.IDPStepSpecTypeConstants;
import io.harness.idp.pipeline.stages.node.IDPStageConfigImpl;
import io.harness.idp.pipeline.stages.node.IDPStageNode;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;



@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IDPStageFilterCreator extends GenericStageFilterJsonCreatorV2<IDPStageNode> {

    @Override
    public Set<String> getSupportedStageTypes() {
        return ImmutableSet.of(IDPStepSpecTypeConstants.IDP_STAGE);
    }

    @Override
    public Class<IDPStageNode> getFieldClass() {
        return IDPStageNode.class;
    }

    /**
     * getFilter returns a Pipeline Filter with the information of the CodeBase block.
     * It can also be used to reject a pipeline given missing/not missing fields in the yaml
     * We are going to use it here to validate the pipeline and reject it if there is a problem with the yaml
     * but then return null because we don't want to use the filter function.
     * */

    @Override
    public PipelineFilter getFilter(FilterCreationContext filterCreationContext, IDPStageNode stageNode) {
        log.info("Received filter creation request for IDP stage {}", stageNode.getIdentifier());

        YamlField variablesField =
                filterCreationContext.getCurrentField().getNode().getField(YAMLFieldNameConstants.VARIABLES);
        if (variablesField != null) {
            FilterCreatorHelper.checkIfVariableNamesAreValid(variablesField);
        }

        validateStage(stageNode);

        log.info("Successfully created filter for IDP stage {}", stageNode.getIdentifier());
        return null;
    }

    private void validateExecution(IDPStageConfigImpl integrationStageConfig) {
        ExecutionElementConfig executionElementConfig = integrationStageConfig.getExecution();
        if (executionElementConfig == null) {
            throw new CIStageExecutionException("Execution field is required in this stage");
        }
    }

    private void validateStage(IDPStageNode stageNode) {
//        IntegrationStageConfig integrationStageConfig = (IntegrationStageConfig) stageNode.getStageInfoConfig();
        //    validateInfrastructure(integrationStageConfig); // Disabling this for kubernetes delegate work
        validateExecution(stageNode.getIdpStageConfig());
    }
}

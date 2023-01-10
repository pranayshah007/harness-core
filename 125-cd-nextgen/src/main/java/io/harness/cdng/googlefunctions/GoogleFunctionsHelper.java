package io.harness.cdng.googlefunctions;

import com.google.inject.Inject;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.ecs.EcsStepExecutor;
import io.harness.cdng.ecs.EcsStepHelper;
import io.harness.cdng.ecs.beans.EcsGitFetchPassThroughData;
import io.harness.cdng.ecs.beans.EcsHarnessStoreManifestsContent;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.task.ecs.EcsGitFetchFileConfig;
import io.harness.delegate.task.gitcommon.GitRequestFileConfig;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.logging.LogCallback;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static java.lang.String.format;

@Slf4j
public class GoogleFunctionsHelper extends CDStepHelper {
    @Inject private EngineExpressionService engineExpressionService;


    public TaskChainResponse startChainLink(GoogleFunctionsStepExecutor googleFunctionsStepExecutor, Ambiance ambiance,
                                            StepElementParameters stepElementParameters) {
        // Get ManifestsOutcome
        ManifestsOutcome manifestsOutcome = resolveGoogleFunctionsManifestsOutcome(ambiance);

        // Get InfrastructureOutcome
        InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
                ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

        // Update expressions in ManifestsOutcome
        ExpressionEvaluatorUtils.updateExpressions(
                manifestsOutcome, new CDExpressionResolveFunctor(engineExpressionService, ambiance));

        // Validate ManifestsOutcome
        validateManifestsOutcome(ambiance, manifestsOutcome);

        ManifestOutcome googleFunctionsManifestOutcome = getGoogleFunctionsManifestOutcome(manifestsOutcome.values());

        LogCallback logCallback = getLogCallback(EcsCommandUnitConstants.fetchManifests.toString(), ambiance, true);

        if(isHarnessStoreManifest(googleFunctionsManifestOutcome)) {
            // get Harness Store Manifests Content
            String googleFunctionsHarnessStoreManifestContent =
                    getHarnessStoreManifestFilesContent(ambiance, googleFunctionsManifestOutcome, logCallback);

        }
        else{

        }


    }

    private TaskChainResponse prepareManifestGitFetchTask(GoogleFunctionsStepExecutor stepExecutor, Ambiance ambiance,
                                                             StepElementParameters stepElementParameters,
                                                             ManifestOutcome manifestOutcome) {

        GitRequestFileConfig gitRequestFileConfig = null;

        if (ManifestStoreType.isInGitSubset(manifestOutcome.getStore().getKind())) {
            gitRequestFileConfig =
                    getEcsGitFetchFilesConfigFromManifestOutcome(manifestOutcome, ambiance);
        }

        // Get EcsGitFetchFileConfig for service definition
        ManifestOutcome ecsServiceDefinitionManifestOutcome =
                ecsStepHelper.getEcsServiceDefinitionManifestOutcome(ecsManifestOutcomes);

        EcsGitFetchFileConfig ecsServiceDefinitionGitFetchFileConfig = null;

        if (ManifestStoreType.isInGitSubset(ecsServiceDefinitionManifestOutcome.getStore().getKind())) {
            ecsServiceDefinitionGitFetchFileConfig =
                    getEcsGitFetchFilesConfigFromManifestOutcome(ecsServiceDefinitionManifestOutcome, ambiance, ecsStepHelper);
        }

        // Get EcsGitFetchFileConfig list for scalable targets if present
        List<ManifestOutcome> ecsScalableTargetManifestOutcomes =
                ecsStepHelper.getManifestOutcomesByType(ecsManifestOutcomes, ManifestType.EcsScalableTargetDefinition);

        List<EcsGitFetchFileConfig> ecsScalableTargetGitFetchFileConfigs = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(ecsScalableTargetManifestOutcomes)) {
            for (ManifestOutcome ecsScalableTargetManifestOutcome : ecsScalableTargetManifestOutcomes) {
                if (ManifestStoreType.isInGitSubset(ecsScalableTargetManifestOutcome.getStore().getKind())) {
                    ecsScalableTargetGitFetchFileConfigs.add(
                            getEcsGitFetchFilesConfigFromManifestOutcome(ecsScalableTargetManifestOutcome, ambiance, ecsStepHelper));
                }
            }
        }

        // Get EcsGitFetchFileConfig list for scaling policies if present
        List<ManifestOutcome> ecsScalingPolicyManifestOutcomes =
                ecsStepHelper.getManifestOutcomesByType(ecsManifestOutcomes, ManifestType.EcsScalingPolicyDefinition);

        List<EcsGitFetchFileConfig> ecsScalingPolicyGitFetchFileConfigs = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(ecsScalingPolicyManifestOutcomes)) {
            for (ManifestOutcome ecsScalingPolicyManifestOutcome : ecsScalingPolicyManifestOutcomes) {
                if (ManifestStoreType.isInGitSubset(ecsScalingPolicyManifestOutcome.getStore().getKind())) {
                    ecsScalingPolicyGitFetchFileConfigs.add(
                            getEcsGitFetchFilesConfigFromManifestOutcome(ecsScalingPolicyManifestOutcome, ambiance, ecsStepHelper));
                }
            }
        }

        return getGitFetchFileTaskResponse(ambiance, false, stepElementParameters, ecsGitFetchPassThroughData,
                ecsTaskDefinitionGitFetchFileConfig, ecsServiceDefinitionGitFetchFileConfig,
                ecsScalableTargetGitFetchFileConfigs, ecsScalingPolicyGitFetchFileConfigs);
    }

    private String getHarnessStoreManifestFilesContent(Ambiance ambiance, ManifestOutcome manifestOutcome, LogCallback logCallback) {
        // Harness Store manifest
        String harnessStoreManifestContent = null;
        if (ManifestStoreType.HARNESS.equals(manifestOutcome.getStore().getKind())) {
            harnessStoreManifestContent =
                    fetchFilesContentFromLocalStore(ambiance, manifestOutcome, logCallback).get(0);
        }
        // Render expressions for all file content fetched from Harness File Store

        if (harnessStoreManifestContent != null) {
            harnessStoreManifestContent =
                    engineExpressionService.renderExpression(ambiance, harnessStoreManifestContent);
        }
        return harnessStoreManifestContent;
    }

    public ManifestOutcome getGoogleFunctionsManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes) {
        // Filter only  Google Cloud Functions supported manifest types
        List<ManifestOutcome> googleFunctionsManifests =
                manifestOutcomes.stream()
                        .filter(manifestOutcome -> ManifestType.GOOGLE_FUNCTIONS_SUPPORTED_MANIFEST_TYPES.contains(manifestOutcome.getType()))
                        .collect(Collectors.toList());

        // Check if Google Cloud Functions Manifests are empty
        if (isEmpty(googleFunctionsManifests)) {
            throw new InvalidRequestException("Google Cloud Function Manifest is mandatory.", USER);
        }
        return googleFunctionsManifests.get(0);
    }

    public ManifestsOutcome resolveGoogleFunctionsManifestsOutcome(Ambiance ambiance) {
        OptionalOutcome manifestsOutcome = outcomeService.resolveOptional(
                ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));

        if (!manifestsOutcome.isFound()) {
            String stageName =
                    AmbianceUtils.getStageLevelFromAmbiance(ambiance).map(Level::getIdentifier).orElse("Deployment stage");
            String stepType =
                    Optional.ofNullable(AmbianceUtils.getCurrentStepType(ambiance))
                            .map(StepType::getType).orElse("Google Function");
            throw new GeneralException(
                    format("No manifests found in stage %s. %s step requires a manifest defined in stage service definition",
                            stageName, stepType));
        }
        return (ManifestsOutcome) manifestsOutcome.getOutcome();
    }

    public boolean isHarnessStoreManifest(ManifestOutcome manifestOutcome) {
        return manifestOutcome.getStore() != null && ManifestStoreType.HARNESS.equals(manifestOutcome.getStore().getKind())
    }

}

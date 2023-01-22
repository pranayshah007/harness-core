package io.harness.cdng.googlefunctions.rollback;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.googlefunctions.GoogleFunctionsSpecParameters;
import io.harness.cdng.googlefunctions.deployWithoutTraffic.GoogleFunctionsDeployWithoutTrafficBaseStepInfo;
import io.harness.googlefunctions.GoogleFunctionsCommandUnitConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

import java.util.Arrays;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("googleFunctionsRollbackStepParameters")
@RecasterAlias("io.harness.cdng.googlefunctions.rollback.GoogleFunctionsRollbackStepParameters")
public class GoogleFunctionsRollbackStepParameters extends GoogleFunctionsRollbackBaseStepInfo
        implements GoogleFunctionsSpecParameters {
    @Builder(builderMethodName = "infoBuilder")
    public GoogleFunctionsRollbackStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
                                                 String googleFunctionDeployWithoutTrafficStepFnq,
                                                 String googleFunctionDeployStepFnq) {
        super(delegateSelectors, googleFunctionDeployWithoutTrafficStepFnq, googleFunctionDeployStepFnq);
    }

    public List<String> getCommandUnits() {
        return Arrays.asList(GoogleFunctionsCommandUnitConstants.rollback.toString());
    }
}
package io.harness.cdng.tas;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.tas.TasAppResizeStepParameters")
public class TasAppResizeStepParameters extends TasAppResizeBaseStepInfo implements SpecParameters {
    @Builder(builderMethodName = "infoBuilder")
    public TasAppResizeStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
        super(delegateSelectors);
    }

}

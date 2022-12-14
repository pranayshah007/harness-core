/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.template;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.VariableType.ARTIFACT;

import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SweepingOutputInstance;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.Variable;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@TargetModule(_870_CG_ORCHESTRATION)
public class TemplateUtils {
  private static final Pattern p = Pattern.compile("\\$\\{(.*?)\\}");

  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private KryoSerializer kryoSerializer;

  private void processArtifactVariable(ExecutionContext context, Variable variable) {
    String expression = getExpression(variable.getValue());
    Artifact artifact = (Artifact) context.evaluateExpression(expression);
    ensureArtifactToSweepingOutput(context, variable.getName(), artifact);
  }

  private void ensureArtifactToSweepingOutput(ExecutionContext context, String name, Artifact artifact) {
    if (artifact == null) {
      return;
    }
    sweepingOutputService.ensure(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.STATE)
                                     .name(name)
                                     .output(kryoSerializer.asDeflatedBytes(artifact))
                                     .build());
  }

  public String getExpression(String value) {
    Matcher m = p.matcher(value);
    if (m.find()) {
      return m.group(1);
    }
    return value;
  }

  public Map<String, Object> processTemplateVariables(ExecutionContext context, List<Variable> variables) {
    if (isEmpty(variables)) {
      return null;
    }
    Map<String, Object> map = new HashMap<>();
    for (Variable variable : variables) {
      if (variable.getName() != null && variable.getValue() != null) {
        if (ARTIFACT != variable.getType()) {
          map.put(variable.getName(), variable.getValue());
        } else {
          processArtifactVariable(context, variable);
        }
      }
    }
    return map;
  }
}

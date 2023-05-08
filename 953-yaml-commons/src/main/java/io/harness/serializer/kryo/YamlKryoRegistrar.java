/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.customDeployment.StepTemplateRef;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.serializer.KryoRegistrar;
import io.harness.yaml.clone.Clone;
import io.harness.yaml.clone.Ref;
import io.harness.yaml.clone.RefType;
import io.harness.yaml.clone.Strategy;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.NumberNGVariable;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildSpec;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.PRCloneStrategy;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;
import io.harness.yaml.extended.ci.container.ContainerResource;
import io.harness.yaml.options.Options;
import io.harness.yaml.registry.Registry;
import io.harness.yaml.registry.RegistryCredential;
import io.harness.yaml.repository.Repository;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(PIPELINE)
public class YamlKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(TaskSelectorYaml.class, 35005);
    kryo.register(StringNGVariable.class, 35006);
    kryo.register(NumberNGVariable.class, 35007);
    kryo.register(CodeBase.class, 35011);
    kryo.register(ContainerResource.class, 35013);
    kryo.register(ContainerResource.Limits.class, 35014);
    kryo.register(NGVariableType.class, 88501);
    kryo.register(SecretNGVariable.class, 88502);
    kryo.register(StepTemplateRef.class, 88503);
    kryo.register(Build.class, 88504);
    kryo.register(BuildSpec.class, 88505);
    kryo.register(BranchBuildSpec.class, 88506);
    kryo.register(PRBuildSpec.class, 88507);
    kryo.register(TagBuildSpec.class, 88508);
    kryo.register(BuildType.class, 88509);
    kryo.register(PRCloneStrategy.class, 88510);
    kryo.register(Repository.class, 88511);
    kryo.register(Ref.class, 88512);
    kryo.register(RefType.class, 88513);
    kryo.register(Strategy.class, 88514);
    kryo.register(Registry.class, 88515);
    kryo.register(RegistryCredential.class, 88516);
    kryo.register(Options.class, 88517);
    kryo.register(Clone.class, 88518);
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto;

import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.beans.steps.STOStepSpecTypeConstants.SECURITY;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.steps.nodes.security.AquaTrivyScanNode;
import io.harness.beans.steps.nodes.security.AwsEcrScanNode;
import io.harness.beans.steps.nodes.security.AwsSecurityHubScanNode;
import io.harness.beans.steps.nodes.security.BanditScanNode;
import io.harness.beans.steps.nodes.security.BlackDuckScanNode;
import io.harness.beans.steps.nodes.security.BrakemanScanNode;
import io.harness.beans.steps.nodes.security.BurpScanNode;
import io.harness.beans.steps.nodes.security.CheckmarxScanNode;
import io.harness.beans.steps.nodes.security.ClairScanNode;
import io.harness.beans.steps.nodes.security.CustomIngestScanNode;
import io.harness.beans.steps.nodes.security.DataTheoremScanNode;
import io.harness.beans.steps.nodes.security.DockerContentTrustScanNode;
import io.harness.beans.steps.nodes.security.FortifyOnDemandScanNode;
import io.harness.beans.steps.nodes.security.GrypeScanNode;
import io.harness.beans.steps.nodes.security.JfrogXrayScanNode;
import io.harness.beans.steps.nodes.security.MendScanNode;
import io.harness.beans.steps.nodes.security.MetasploitScanNode;
import io.harness.beans.steps.nodes.security.NessusScanNode;
import io.harness.beans.steps.nodes.security.NexusIQScanNode;
import io.harness.beans.steps.nodes.security.NiktoScanNode;
import io.harness.beans.steps.nodes.security.NmapScanNode;
import io.harness.beans.steps.nodes.security.OpenvasScanNode;
import io.harness.beans.steps.nodes.security.OwaspScanNode;
import io.harness.beans.steps.nodes.security.PrismaCloudScanNode;
import io.harness.beans.steps.nodes.security.ProwlerScanNode;
import io.harness.beans.steps.nodes.security.QualysScanNode;
import io.harness.beans.steps.nodes.security.ReapsawScanNode;
import io.harness.beans.steps.nodes.security.ShiftLeftScanNode;
import io.harness.beans.steps.nodes.security.SniperScanNode;
import io.harness.beans.steps.nodes.security.SnykScanNode;
import io.harness.beans.steps.nodes.security.SonarqubeScanNode;
import io.harness.beans.steps.nodes.security.SysdigScanNode;
import io.harness.beans.steps.nodes.security.TenableScanNode;
import io.harness.beans.steps.nodes.security.VeracodeScanNode;
import io.harness.beans.steps.nodes.security.ZapScanNode;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.sto.plan.creator.step.STOGenericStepPlanCreator;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@OwnedBy(STO)
public enum STOStepType {
  AQUA_TRIVY("AquaTrivy", null, AquaTrivyScanNode.class, EntityType.AQUA_TRIVY, new String[] {SECURITY}),
  AWS_ECR("AWSECR", FeatureName.STO_STEP_PALETTE_Q1_2023, AwsEcrScanNode.class, EntityType.AWS_ECR,
      new String[] {SECURITY}),
  AWS_SECURITY_HUB("AWSSecurityHub", FeatureName.STO_STEP_PALETTE_Q1_2023, AwsSecurityHubScanNode.class,
      EntityType.AWS_SECURITY_HUB, new String[] {SECURITY}),
  BANDIT("Bandit", null, BanditScanNode.class, EntityType.BANDIT, new String[] {SECURITY}),
  BLACKDUCK("BlackDuck", null, BlackDuckScanNode.class, EntityType.BLACKDUCK, new String[] {SECURITY}),
  BRAKEMAN("Brakeman", FeatureName.STO_STEP_PALETTE_Q1_2023, BrakemanScanNode.class, EntityType.BRAKEMAN,
      new String[] {SECURITY}),
  BURP("Burp", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, BurpScanNode.class, EntityType.BURP,
      new String[] {SECURITY}),
  CHECKMARX("Checkmarx", null, CheckmarxScanNode.class, EntityType.CHECKMARX, new String[] {SECURITY}),
  CLAIR("Clair", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, ClairScanNode.class, EntityType.CLAIR,
      new String[] {SECURITY}),
  DATA_THEOREM("DataTheorem", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, DataTheoremScanNode.class,
      EntityType.DATA_THEOREM, new String[] {SECURITY}),
  DOCKER_CONTENT_TRUST("DockerContentTrust", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3,
      DockerContentTrustScanNode.class, EntityType.DOCKER_CONTENT_TRUST, new String[] {SECURITY}),
  CUSTOM_INGEST("CustomIngest", FeatureName.STO_STEP_PALETTE_Q1_2023, CustomIngestScanNode.class, EntityType.EXTERNAL,
      new String[] {SECURITY}),
  FORTIFY_ON_DEMAND("FortifyOnDemand", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, FortifyOnDemandScanNode.class,
      EntityType.FORTIFY_ON_DEMAND, new String[] {SECURITY}),
  GRYPE("Grype", null, GrypeScanNode.class, EntityType.GRYPE, new String[] {SECURITY}),
  JFROG_XRAY("JfrogXray", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, JfrogXrayScanNode.class, EntityType.JFROG_XRAY,
      new String[] {SECURITY}),
  MEND("Mend", null, MendScanNode.class, EntityType.MEND, new String[] {SECURITY}),
  METASPLOIT("Metasploit", FeatureName.STO_STEP_PALETTE_Q1_2023, MetasploitScanNode.class, EntityType.METASPLOIT,
      new String[] {SECURITY}),
  NESSUS("Nessus", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, NessusScanNode.class, EntityType.NESSUS,
      new String[] {SECURITY}),
  NEXUS_IQ("NexusIQ", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, NexusIQScanNode.class, EntityType.NEXUS_IQ,
      new String[] {SECURITY}),
  NIKTO("Nikto", FeatureName.STO_STEP_PALETTE_Q1_2023, NiktoScanNode.class, EntityType.NIKTO, new String[] {SECURITY}),
  NMAP("Nmap", FeatureName.STO_STEP_PALETTE_Q1_2023, NmapScanNode.class, EntityType.NMAP, new String[] {SECURITY}),
  OPENVAS("Openvas", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, OpenvasScanNode.class, EntityType.OPENVAS,
      new String[] {SECURITY}),
  OWASP("Owasp", FeatureName.STO_STEP_PALETTE_Q1_2023, OwaspScanNode.class, EntityType.OWASP, new String[] {SECURITY}),
  PRISMA_CLOUD("PrismaCloud", null, PrismaCloudScanNode.class, EntityType.PRISMA_CLOUD, new String[] {SECURITY}),
  PROWLER("Prowler", FeatureName.STO_STEP_PALETTE_Q1_2023, ProwlerScanNode.class, EntityType.PROWLER,
      new String[] {SECURITY}),
  QUALYS("Qualys", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, QualysScanNode.class, EntityType.QUALYS,
      new String[] {SECURITY}),
  REAPSAW("Reapsaw", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, ReapsawScanNode.class, EntityType.REAPSAW,
      new String[] {SECURITY}),
  SHIFT_LEFT("ShiftLeft", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, ShiftLeftScanNode.class, EntityType.SHIFT_LEFT,
      new String[] {SECURITY}),
  SNIPER("Sniper", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, SniperScanNode.class, EntityType.SNIPER,
      new String[] {SECURITY}),
  SNYK("Snyk", null, SnykScanNode.class, EntityType.SNYK, new String[] {SECURITY}),
  SONARQUBE("Sonarqube", null, SonarqubeScanNode.class, EntityType.SONARQUBE, new String[] {SECURITY}),
  SYSDIG("Sysdig", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, SysdigScanNode.class, EntityType.SYSDIG,
      new String[] {SECURITY}),
  TENABLE("Tenable", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, TenableScanNode.class, EntityType.TENABLE,
      new String[] {SECURITY}),
  VERACODE("Veracode", FeatureName.STO_STEP_PALETTE_Q1_2023, VeracodeScanNode.class, EntityType.VERACODE,
      new String[] {SECURITY}),
  ZAP("Zap", null, ZapScanNode.class, EntityType.ZAP, new String[] {SECURITY});
  @Getter private String name;
  @Getter private FeatureName featureName;
  @Getter private Class<?> node;
  @Getter private EntityType entityType;
  private String[] stepCategories;

  private static final Map<String, STOStepType> lookup = new HashMap<>();

  static {
    for (STOStepType d : STOStepType.values()) {
      lookup.put(d.getName(), d);
    }
  }
  public static STOStepType lookupByName(String name) {
    return lookup.get(name);
  }

  public static StepInfo createStepInfo(STOStepType stoStepType, ModuleType moduleType, String stepCategory) {
    StepInfo.Builder stepInfoBuilder =
        StepInfo.newBuilder()
            .setName(stoStepType.getName())
            .setType(stoStepType.getName())
            .setStepMetaData(StepMetaData.newBuilder().addFolderPaths(stepCategory).build());

    FeatureName featureName = stoStepType.getFeatureName();

    if (featureName != null) {
      stepInfoBuilder.setFeatureFlag(featureName.name());
    }

    if (moduleType == ModuleType.CI) {
      stepInfoBuilder.setFeatureRestrictionName(FeatureRestrictionName.SECURITY.name());
    }

    return stepInfoBuilder.build();
  }

  public static Collection<String> getSupportedSteps() {
    return Arrays.stream(STOStepType.values()).map(e -> e.getName()).collect(Collectors.toSet());
  }

  public static List<StepInfo> getStepInfos(ModuleType moduleType) {
    List<StepInfo> stepInfos = new ArrayList<>();
    Arrays.asList(STOStepType.values())
        .forEach(e
            -> e.getStepCategories().forEach(
                category -> stepInfos.add(STOStepType.createStepInfo(e, moduleType, category))));

    return stepInfos;
  }
  public static Collection<PartialPlanCreator<?>> getPlanCreators() {
    List<PartialPlanCreator<?>> planCreators = new LinkedList<>();
    planCreators.addAll(
        Arrays.asList(STOStepType.values()).stream().map(STOStepType::getPlanCreator).collect(Collectors.toList()));
    return planCreators;
  }

  public static Map<StepType, Class<? extends Step>> addSTOEngineSteps(
      Class<? extends AsyncExecutableWithRbac<StepElementParameters>> clazz) {
    Map<StepType, Class<? extends Step>> stoSteps = new HashMap<>();

    Arrays.asList(STOStepType.values()).forEach(e -> stoSteps.put(e.getStepType(), clazz));

    return stoSteps;
  }

  public static YamlSchemaRootClass createStepYaml(STOStepType stepType) {
    return YamlSchemaRootClass.builder()
        .entityType(stepType.getEntityType())
        .availableAtProjectLevel(true)
        .availableAtOrgLevel(false)
        .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                .modulesSupported(Collections.singletonList(ModuleType.STO))
                                .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                .build())
        .availableAtAccountLevel(false)
        .clazz(stepType.getNode())
        .build();
  }

  public static ImmutableList<YamlSchemaRootClass> createSecurityStepYamlDefinitions() {
    ImmutableList.Builder<YamlSchemaRootClass> stepPaletteListBuilder = ImmutableList.builder();

    Arrays.asList(STOStepType.values()).forEach(e -> stepPaletteListBuilder.add(createStepYaml(e)));

    return stepPaletteListBuilder.build();
  }

  private static PartialPlanCreator<?> getPlanCreator(STOStepType stepType) {
    return new STOGenericStepPlanCreator(stepType);
  }

  public StepType getStepType() {
    return StepType.newBuilder().setType(this.name).setStepCategory(StepCategory.STEP).build();
  }

  public Stream<String> getStepCategories() {
    return Arrays.asList(this.stepCategories).stream();
  }
}

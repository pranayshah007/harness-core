/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto;

import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.beans.steps.STOStepSpecTypeConstants.CONFIGURATION;
import static io.harness.beans.steps.STOStepSpecTypeConstants.CONTAINER_SECURITY;
import static io.harness.beans.steps.STOStepSpecTypeConstants.DAST;
import static io.harness.beans.steps.STOStepSpecTypeConstants.SAST;
import static io.harness.beans.steps.STOStepSpecTypeConstants.SCA;
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
import io.harness.beans.steps.nodes.security.CodeqlScanNode;
import io.harness.beans.steps.nodes.security.CustomIngestScanNode;
import io.harness.beans.steps.nodes.security.DataTheoremScanNode;
import io.harness.beans.steps.nodes.security.DockerContentTrustScanNode;
import io.harness.beans.steps.nodes.security.FortifyOnDemandScanNode;
import io.harness.beans.steps.nodes.security.FossaScanNode;
import io.harness.beans.steps.nodes.security.GitleaksScanNode;
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
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@OwnedBy(STO)
public enum STOStepType {
  AQUA_TRIVY("AquaTrivy", "Aqua Trivy", null, AquaTrivyScanNode.class, EntityType.AQUA_TRIVY,
      new String[] {CONTAINER_SECURITY}),
  AWS_ECR("AWSECR", "AWS ECR Scan", FeatureName.STO_STEP_PALETTE_Q1_2023, AwsEcrScanNode.class, EntityType.AWS_ECR,
      new String[] {CONTAINER_SECURITY}),
  AWS_SECURITY_HUB("AWSSecurityHub", "AWS Security Hub", FeatureName.STO_STEP_PALETTE_Q1_2023,
      AwsSecurityHubScanNode.class, EntityType.AWS_SECURITY_HUB, new String[] {CONFIGURATION}),
  BANDIT("Bandit", null, null, BanditScanNode.class, EntityType.BANDIT, new String[] {SAST}),
  BLACKDUCK("BlackDuck", "Black Duck", null, BlackDuckScanNode.class, EntityType.BLACKDUCK,
      new String[] {SAST, CONTAINER_SECURITY}),
  BRAKEMAN("Brakeman", null, FeatureName.STO_STEP_PALETTE_Q1_2023, BrakemanScanNode.class, EntityType.BRAKEMAN,
      new String[] {SAST}),
  BURP("Burp", null, FeatureName.STO_STEP_PALETTE_BURP_ENTERPRISE, BurpScanNode.class, EntityType.BURP,
      new String[] {DAST}),
  CHECKMARX("Checkmarx", null, null, CheckmarxScanNode.class, EntityType.CHECKMARX, new String[] {SAST}),
  CLAIR("Clair", null, FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, ClairScanNode.class, EntityType.CLAIR,
      new String[] {CONTAINER_SECURITY}),
  DATA_THEOREM("DataTheorem", "Data Theorem", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, DataTheoremScanNode.class,
      EntityType.DATA_THEOREM, new String[] {SECURITY}),
  DOCKER_CONTENT_TRUST("DockerContentTrust", "Docker Content Trust", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3,
      DockerContentTrustScanNode.class, EntityType.DOCKER_CONTENT_TRUST, new String[] {CONTAINER_SECURITY}),
  CUSTOM_INGEST("CustomIngest", "Custom Ingest", FeatureName.STO_STEP_PALETTE_Q1_2023, CustomIngestScanNode.class,
      EntityType.CUSTOM_INGEST, new String[] {SECURITY}),
  CODE_QL("CodeQL", null, FeatureName.STO_STEP_PALETTE_CODEQL, CodeqlScanNode.class, EntityType.CODEQL,
      new String[] {SAST}),
  FOSSA("Fossa", null, FeatureName.STO_STEP_PALETTE_FOSSA, FossaScanNode.class, EntityType.FOSSA, new String[] {SAST}),
  FORTIFY_ON_DEMAND("FortifyOnDemand", "Fortify On Demand", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3,
      FortifyOnDemandScanNode.class, EntityType.FORTIFY_ON_DEMAND, new String[] {SECURITY}),

  GIT_LEAKS("Gitleaks", null, FeatureName.STO_STEP_PALETTE_GIT_LEAKS, GitleaksScanNode.class, EntityType.GIT_LEAKS,
      new String[] {SAST}),
  GRYPE("Grype", null, null, GrypeScanNode.class, EntityType.GRYPE, new String[] {CONTAINER_SECURITY}),
  JFROG_XRAY("JfrogXray", "Jfrog Xray", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, JfrogXrayScanNode.class,
      EntityType.JFROG_XRAY, new String[] {SECURITY}),
  MEND("Mend", null, null, MendScanNode.class, EntityType.MEND, new String[] {SAST}),
  METASPLOIT("Metasploit", null, FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, MetasploitScanNode.class,
      EntityType.METASPLOIT, new String[] {DAST}),
  NESSUS("Nessus", null, FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, NessusScanNode.class, EntityType.NESSUS,
      new String[] {SECURITY}),
  NEXUS_IQ("NexusIQ", null, FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, NexusIQScanNode.class, EntityType.NEXUS_IQ,
      new String[] {SCA}),
  NIKTO(
      "Nikto", null, FeatureName.STO_STEP_PALETTE_Q1_2023, NiktoScanNode.class, EntityType.NIKTO, new String[] {DAST}),
  NMAP("Nmap", null, FeatureName.STO_STEP_PALETTE_Q1_2023, NmapScanNode.class, EntityType.NMAP, new String[] {DAST}),
  OPENVAS("Openvas", null, FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, OpenvasScanNode.class, EntityType.OPENVAS,
      new String[] {DAST}),
  OWASP("Owasp", null, FeatureName.STO_STEP_PALETTE_Q1_2023, OwaspScanNode.class, EntityType.OWASP, new String[] {SCA}),
  PRISMA_CLOUD("PrismaCloud", "Prisma Cloud", null, PrismaCloudScanNode.class, EntityType.PRISMA_CLOUD,
      new String[] {CONTAINER_SECURITY}),
  PROWLER("Prowler", null, FeatureName.STO_STEP_PALETTE_Q1_2023, ProwlerScanNode.class, EntityType.PROWLER,
      new String[] {CONFIGURATION}),
  QUALYS("Qualys", null, FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, QualysScanNode.class, EntityType.QUALYS,
      new String[] {SECURITY}),
  REAPSAW("Reapsaw", null, FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, ReapsawScanNode.class, EntityType.REAPSAW,
      new String[] {SECURITY}),
  SHIFT_LEFT("ShiftLeft", "Shift Left", FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, ShiftLeftScanNode.class,
      EntityType.SHIFT_LEFT, new String[] {SECURITY}),
  SNIPER("Sniper", null, FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, SniperScanNode.class, EntityType.SNIPER,
      new String[] {SECURITY}),
  SNYK("Snyk", null, null, SnykScanNode.class, EntityType.SNYK, new String[] {SCA, SAST, CONTAINER_SECURITY}),
  SONARQUBE("Sonarqube", null, null, SonarqubeScanNode.class, EntityType.SONARQUBE, new String[] {SAST}),
  SYSDIG("Sysdig", null, FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, SysdigScanNode.class, EntityType.SYSDIG,
      new String[] {CONTAINER_SECURITY}),
  TENABLE("Tenable", null, FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, TenableScanNode.class, EntityType.TENABLE,
      new String[] {DAST}),
  VERACODE("Veracode", null, FeatureName.DONT_ENABLE_STO_STEP_PALETTE_V3, VeracodeScanNode.class, EntityType.VERACODE,
      new String[] {SAST, DAST}),
  ZAP("Zap", null, null, ZapScanNode.class, EntityType.ZAP, new String[] {DAST});
  @Getter private String name;
  @Getter private String synonym;
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

  public static StepInfo createStepInfo(STOStepType stoStepType, ModuleType moduleType, String[] stepCategory) {
    StepInfo.Builder stepInfoBuilder =
        StepInfo.newBuilder()
            .setType(stoStepType.getName())
            .setStepMetaData(
                StepMetaData.newBuilder().addFolderPaths(SECURITY).addAllCategory(List.of(stepCategory)).build());

    if (stoStepType.getSynonym() != null) {
      stepInfoBuilder.setName(stoStepType.getSynonym());
    } else {
      stepInfoBuilder.setName(stoStepType.getName());
    }

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
        .forEach(e -> stepInfos.add(STOStepType.createStepInfo(e, moduleType, e.getStepCategories())));

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

  public String[] getStepCategories() {
    return this.stepCategories;
  }
}

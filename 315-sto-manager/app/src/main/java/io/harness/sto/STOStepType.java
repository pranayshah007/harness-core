/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto;

import static io.harness.annotations.dev.HarnessTeam.STO;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.steps.nodes.security.AquaTrivyScanNode;
import io.harness.beans.steps.nodes.security.AwsEcrScanNode;
import io.harness.beans.steps.nodes.security.BanditScanNode;
import io.harness.beans.steps.nodes.security.BlackDuckScanNode;
import io.harness.beans.steps.nodes.security.BrakemanScanNode;
import io.harness.beans.steps.nodes.security.BurpScanNode;
import io.harness.beans.steps.nodes.security.CheckmarxScanNode;
import io.harness.beans.steps.nodes.security.ClairScanNode;
import io.harness.beans.steps.nodes.security.DataTheoremScanNode;
import io.harness.beans.steps.nodes.security.DockerContentTrustScanNode;
import io.harness.beans.steps.nodes.security.ExternalScanNode;
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
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.sto.plan.creator.step.AquaTrivyStepPlanCreator;
import io.harness.sto.plan.creator.step.AwsEcrStepPlanCreator;
import io.harness.sto.plan.creator.step.BanditStepPlanCreator;
import io.harness.sto.plan.creator.step.BlackDuckStepPlanCreator;
import io.harness.sto.plan.creator.step.BrakemanStepPlanCreator;
import io.harness.sto.plan.creator.step.BurpStepPlanCreator;
import io.harness.sto.plan.creator.step.CheckmarxStepPlanCreator;
import io.harness.sto.plan.creator.step.ClairStepPlanCreator;
import io.harness.sto.plan.creator.step.DataTheoremStepPlanCreator;
import io.harness.sto.plan.creator.step.DockerContentTrustStepPlanCreator;
import io.harness.sto.plan.creator.step.ExternalStepPlanCreator;
import io.harness.sto.plan.creator.step.FortifyOnDemandStepPlanCreator;
import io.harness.sto.plan.creator.step.GrypeStepPlanCreator;
import io.harness.sto.plan.creator.step.JfrogXrayStepPlanCreator;
import io.harness.sto.plan.creator.step.MendStepPlanCreator;
import io.harness.sto.plan.creator.step.MetasploitStepPlanCreator;
import io.harness.sto.plan.creator.step.NessusStepPlanCreator;
import io.harness.sto.plan.creator.step.NexusIQStepPlanCreator;
import io.harness.sto.plan.creator.step.NiktoStepPlanCreator;
import io.harness.sto.plan.creator.step.NmapStepPlanCreator;
import io.harness.sto.plan.creator.step.OpenvasStepPlanCreator;
import io.harness.sto.plan.creator.step.OwaspStepPlanCreator;
import io.harness.sto.plan.creator.step.PrismaCloudStepPlanCreator;
import io.harness.sto.plan.creator.step.ProwlerStepPlanCreator;
import io.harness.sto.plan.creator.step.QualysStepPlanCreator;
import io.harness.sto.plan.creator.step.ReapsawStepPlanCreator;
import io.harness.sto.plan.creator.step.ShiftLeftStepPlanCreator;
import io.harness.sto.plan.creator.step.SniperStepPlanCreator;
import io.harness.sto.plan.creator.step.SnykStepPlanCreator;
import io.harness.sto.plan.creator.step.SonarqubeStepPlanCreator;
import io.harness.sto.plan.creator.step.SysdigStepPlanCreator;
import io.harness.sto.plan.creator.step.TenableStepPlanCreator;
import io.harness.sto.plan.creator.step.VeracodeStepPlanCreator;
import io.harness.sto.plan.creator.step.ZapStepPlanCreator;

import java.util.Arrays;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@OwnedBy(STO)
public enum STOStepType {
  AQUA_TRIVY("AquaTrivy", FeatureName.STO_STEP_PALETTE_V2, AquaTrivyScanNode.class, EntityType.AQUA_TRIVY,
      new AquaTrivyStepPlanCreator(), new String[] {"Security"}),
  AWS_ECR("AWSECR", FeatureName.STO_STEP_PALETTE_V2, AwsEcrScanNode.class, EntityType.AWS_ECR,
      new AwsEcrStepPlanCreator(), new String[] {"Security"}),
  BANDIT("Bandit", FeatureName.STO_STEP_PALETTE_V1, BanditScanNode.class, EntityType.BANDIT,
      new BanditStepPlanCreator(), new String[] {"Security"}),
  BLACKDUCK("BlackDuck", FeatureName.STO_STEP_PALETTE_V1, BlackDuckScanNode.class, EntityType.BLACKDUCK,
      new BlackDuckStepPlanCreator(), new String[] {"Security"}),
  BRAKEMAN("Brakeman", FeatureName.STO_STEP_PALETTE_V2, BrakemanScanNode.class, EntityType.BRAKEMAN,
      new BrakemanStepPlanCreator(), new String[] {"Security"}),
  BURP("Burp", FeatureName.STO_STEP_PALETTE_V1, BurpScanNode.class, EntityType.BURP, new BurpStepPlanCreator(),
      new String[] {"Security"}),
  CHECKMARX("Checkmarx", FeatureName.STO_STEP_PALETTE_V1, CheckmarxScanNode.class, EntityType.CHECKMARX,
      new CheckmarxStepPlanCreator(), new String[] {"Security"}),
  CLAIR("Clair", FeatureName.STO_STEP_PALETTE_V2, ClairScanNode.class, EntityType.CLAIR, new ClairStepPlanCreator(),
      new String[] {"Security"}),
  DATA_THEOREM("DataTheorem", FeatureName.STO_STEP_PALETTE_V2, DataTheoremScanNode.class, EntityType.DATA_THEOREM,
      new DataTheoremStepPlanCreator(), new String[] {"Security"}),
  DOCKER_CONTENT_TRUST("DockerContentTrust", FeatureName.STO_STEP_PALETTE_V2, DockerContentTrustScanNode.class,
      EntityType.DOCKER_CONTENT_TRUST, new DockerContentTrustStepPlanCreator(), new String[] {"Security"}),
  EXTERNAL("External", FeatureName.STO_STEP_PALETTE_V2, ExternalScanNode.class, EntityType.EXTERNAL,
      new ExternalStepPlanCreator(), new String[] {"Security"}),
  FORTIFY_ON_DEMAND("FortifyOnDemand", FeatureName.STO_STEP_PALETTE_V1, FortifyOnDemandScanNode.class,
      EntityType.FORTIFY_ON_DEMAND, new FortifyOnDemandStepPlanCreator(), new String[] {"Security"}),
  GRYPE("Grype", FeatureName.STO_STEP_PALETTE_V2, GrypeScanNode.class, EntityType.GRYPE, new GrypeStepPlanCreator(),
      new String[] {"Security"}),
  JFROG_XRAY("JfrogXray", FeatureName.STO_STEP_PALETTE_V2, JfrogXrayScanNode.class, EntityType.JFROG_XRAY,
      new JfrogXrayStepPlanCreator(), new String[] {"Security"}),
  MEND("Mend", FeatureName.STO_STEP_PALETTE_V2, MendScanNode.class, EntityType.MEND, new MendStepPlanCreator(),
      new String[] {"Security"}),
  METASPLOIT("Metasploit", FeatureName.STO_STEP_PALETTE_V2, MetasploitScanNode.class, EntityType.METASPLOIT,
      new MetasploitStepPlanCreator(), new String[] {"Security"}),
  NESSUS("Nessus", FeatureName.STO_STEP_PALETTE_V2, NessusScanNode.class, EntityType.NESSUS,
      new NessusStepPlanCreator(), new String[] {"Security"}),
  NEXUS_IQ("NexusIQ", FeatureName.STO_STEP_PALETTE_V2, NexusIQScanNode.class, EntityType.NEXUS_IQ,
      new NexusIQStepPlanCreator(), new String[] {"Security"}),
  NIKTO("Nikto", FeatureName.STO_STEP_PALETTE_V2, NiktoScanNode.class, EntityType.NIKTO, new NiktoStepPlanCreator(),
      new String[] {"Security"}),
  NMAP("Nmap", FeatureName.STO_STEP_PALETTE_V2, NmapScanNode.class, EntityType.NMAP, new NmapStepPlanCreator(),
      new String[] {"Security"}),
  OPENVAS("Openvas", FeatureName.STO_STEP_PALETTE_V2, OpenvasScanNode.class, EntityType.OPENVAS,
      new OpenvasStepPlanCreator(), new String[] {"Security"}),
  OWASP("Owasp", FeatureName.STO_STEP_PALETTE_V2, OwaspScanNode.class, EntityType.OWASP, new OwaspStepPlanCreator(),
      new String[] {"Security"}),
  PRISMA_CLOUD("PrismaCloud", FeatureName.STO_STEP_PALETTE_V1, PrismaCloudScanNode.class, EntityType.PRISMA_CLOUD,
      new PrismaCloudStepPlanCreator(), new String[] {"Security"}),
  PROWLER("Prowler", FeatureName.STO_STEP_PALETTE_V2, ProwlerScanNode.class, EntityType.PROWLER,
      new ProwlerStepPlanCreator(), new String[] {"Security"}),
  QUALYS("Qualys", FeatureName.STO_STEP_PALETTE_V2, QualysScanNode.class, EntityType.QUALYS,
      new QualysStepPlanCreator(), new String[] {"Security"}),
  REAPSAW("Reapsaw", FeatureName.STO_STEP_PALETTE_V2, ReapsawScanNode.class, EntityType.REAPSAW,
      new ReapsawStepPlanCreator(), new String[] {"Security"}),
  SHIFT_LEFT("ShiftLeft", FeatureName.STO_STEP_PALETTE_V2, ShiftLeftScanNode.class, EntityType.SHIFT_LEFT,
      new ShiftLeftStepPlanCreator(), new String[] {"Security"}),
  SNIPER("Sniper", FeatureName.STO_STEP_PALETTE_V2, SniperScanNode.class, EntityType.SNIPER,
      new SniperStepPlanCreator(), new String[] {"Security"}),
  SNYK("Snyk", FeatureName.STO_STEP_PALETTE_V1, SnykScanNode.class, EntityType.SNYK, new SnykStepPlanCreator(),
      new String[] {"Security"}),
  SONARQUBE("Sonarqube", FeatureName.STO_STEP_PALETTE_V1, SonarqubeScanNode.class, EntityType.SONARQUBE,
      new SonarqubeStepPlanCreator(), new String[] {"Security"}),
  SYSDIG("Sysdig", FeatureName.STO_STEP_PALETTE_V2, SysdigScanNode.class, EntityType.SYSDIG,
      new SysdigStepPlanCreator(), new String[] {"Security"}),
  TENABLE("Tenable", FeatureName.STO_STEP_PALETTE_V2, TenableScanNode.class, EntityType.TENABLE,
      new TenableStepPlanCreator(), new String[] {"Security"}),
  VERACODE("Veracode", FeatureName.STO_STEP_PALETTE_V1, VeracodeScanNode.class, EntityType.VERACODE,
      new VeracodeStepPlanCreator(), new String[] {"Security"}),
  ZAP("Zap", FeatureName.STO_STEP_PALETTE_V1, ZapScanNode.class, EntityType.ZAP, new ZapStepPlanCreator(),
      new String[] {"Security"});
  @Getter private String name;
  @Getter private FeatureName featureName;
  @Getter private Class<?> node;
  @Getter private EntityType entityType;
  @Getter private PartialPlanCreator<?> planCreator;
  private String[] stepCategories;

  public StepType getStepType() {
    return StepType.newBuilder().setType(this.name).setStepCategory(StepCategory.STEP).build();
  }

  public Stream<String> getStepCategories() {
    return Arrays.asList(this.stepCategories).stream();
  }
}

/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.expression;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GEORGE;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializerWrapper;

import software.wings.WingsBaseTest;
import software.wings.api.HostElement;
import software.wings.beans.infrastructure.Host;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Tag;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * The Class ManagerExpressionEvaluatorTest.
 */
public class ManagerExpressionEvaluatorTest extends WingsBaseTest {
  @Inject private ManagerExpressionEvaluator expressionEvaluator;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject KryoSerializerWrapper kryoSerializerWrapper;

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldSubstituteHostUrl() {
    Host host = new Host();
    host.setHostName("app123.application.com");
    Map<String, Object> context = new HashMap<>();
    context.put("host", host);

    String retValue = expressionEvaluator.substitute("http://${host.hostName}:8080/health/status", context);
    assertThat(retValue).isEqualTo("http://app123.application.com:8080/health/status");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldSubstitutePartially() {
    Host host = new Host();
    host.setHostName("${HOST}.$DOMAIN.${COM}");
    Map<String, Object> context = new HashMap<>();
    context.put("host", host);
    String retValue = expressionEvaluator.substitute("http://${host.hostName}:${PORT}/health/status", context);
    assertThat(retValue).isEqualTo("http://${HOST}.$DOMAIN.${COM}:${PORT}/health/status");

    retValue = expressionEvaluator.substitute("http://${host.hostName}:${PORT}/health/status", context, "bar");
    assertThat(retValue).isEqualTo("http://${HOST}.$DOMAIN.${COM}:${PORT}/health/status");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldEvaluateEc2Instance() {
    Instance ec2 = new Instance();
    ec2.setPrivateDnsName("ip-172-31-24-237.ec2.internal");
    ec2.setInstanceId("1qazxsw2");
    Tag tag1 = new Tag();
    tag1.setKey("name");
    tag1.setValue("foo");
    Tag tag2 = new Tag();
    tag2.setKey("type");
    tag2.setValue("bar");
    ec2.setTags(asList(tag1, tag2));

    HostElement host = HostElement.builder().ec2Instance(ec2).build();
    Map<String, Object> map = ImmutableMap.<String, Object>builder().put("host", host).build();

    assertThat(expressionEvaluator.substitute("${host.ec2Instance.privateDnsName.split('\\.')[0]}", map))
        .isEqualTo("ip-172-31-24-237");
    assertThat(expressionEvaluator.substitute("abc-${host.ec2Instance.instanceId}-def", map))
        .isEqualTo("abc-1qazxsw2-def");

    assertThat(expressionEvaluator.substitute("${aws.tags.find(host.ec2Instance.tags, 'name')}", map)).isEqualTo("foo");
    assertThat(expressionEvaluator.substitute("${aws.tags.find(host.ec2Instance.tags, 'type')}", map)).isEqualTo("bar");

    assertThat(expressionEvaluator.substitute("${aws.tags.find(host.ec2Instance.tags, 'missing')}", map)).isEqualTo("");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldRenderSweepingOutputFunctor() {
    String appId = generateUuid();
    String pipelineExecutionId = generateUuid();
    String workflowExecutionId = generateUuid();
    String followingWorkflowExecutionId = generateUuid();

    SweepingOutputInstance sweepingOutputInstance =
        sweepingOutputService.save(SweepingOutputInstance.builder()
                                       .uuid(generateUuid())
                                       .name("jenkins")
                                       .appId(appId)
                                       .pipelineExecutionId(pipelineExecutionId)
                                       .workflowExecutionId(workflowExecutionId)
                                       .output(kryoSerializerWrapper.asDeflatedBytes(ImmutableMap.of("text", "bar")))
                                       .build());

    Map<String, Object> context =
        ImmutableMap.<String, Object>builder()
            .put("context",
                SweepingOutputFunctor.builder()
                    .sweepingOutputService(sweepingOutputService)
                    .kryoSerializerWrapper(kryoSerializerWrapper)
                    .sweepingOutputInquiryBuilder(SweepingOutputInquiry.builder()
                                                      .appId(appId)
                                                      .pipelineExecutionId(pipelineExecutionId)
                                                      .workflowExecutionId(followingWorkflowExecutionId))
                    .build())
            .build();

    assertThat(expressionEvaluator.substitute("${context.output(\"jenkins\").text}", context)).isEqualTo("bar");
    assertThat(expressionEvaluator.substitute("${context.jenkins.text}", context)).isEqualTo("bar");

    // Test that the first pass did not break the functionality
    assertThat(expressionEvaluator.substitute("${context.output(\"jenkins\").text}", context)).isEqualTo("bar");
    assertThat(expressionEvaluator.substitute("${context.jenkins.text}", context)).isEqualTo("bar");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldRenderSweepingOutputValue() {
    String appId = generateUuid();
    String pipelineExecutionId = generateUuid();
    String workflowExecutionId = generateUuid();
    String followingWorkflowExecutionId = generateUuid();

    SweepingOutputInstance sweepingOutputInstance =
        sweepingOutputService.save(SweepingOutputInstance.builder()
                                       .uuid(generateUuid())
                                       .name("workflow")
                                       .appId(appId)
                                       .pipelineExecutionId(pipelineExecutionId)
                                       .workflowExecutionId(workflowExecutionId)
                                       .output(kryoSerializerWrapper.asDeflatedBytes(ImmutableMap.of("text", "bar")))
                                       .build());

    Map<String, Object> context =
        ImmutableMap.<String, Object>builder()
            .put("workflow",
                SweepingOutputValue.builder()
                    .sweepingOutputService(sweepingOutputService)
                    .kryoSerializerWrapper(kryoSerializerWrapper)
                    .sweepingOutputInquiry(SweepingOutputInquiry.builder()
                                               .name("workflow")
                                               .appId(appId)
                                               .pipelineExecutionId(pipelineExecutionId)
                                               .workflowExecutionId(followingWorkflowExecutionId)
                                               .build())
                    .build())
            .build();

    assertThat(expressionEvaluator.substitute("${workflow.text}", context)).isEqualTo("bar");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldRenderSweepingOutputValueByValue() {
    String appId = generateUuid();
    String pipelineExecutionId = generateUuid();
    String workflowExecutionId = generateUuid();
    String followingWorkflowExecutionId = generateUuid();

    SweepingOutputInstance sweepingOutputInstance =
        sweepingOutputService.save(SweepingOutputInstance.builder()
                                       .uuid(generateUuid())
                                       .name("workflow")
                                       .appId(appId)
                                       .pipelineExecutionId(pipelineExecutionId)
                                       .workflowExecutionId(workflowExecutionId)
                                       .value(SweepingOutputData.builder().text("bar").build())
                                       .build());

    Map<String, Object> context =
        ImmutableMap.<String, Object>builder()
            .put("workflow",
                SweepingOutputValue.builder()
                    .sweepingOutputService(sweepingOutputService)
                    .sweepingOutputInquiry(SweepingOutputInquiry.builder()
                                               .name("workflow")
                                               .appId(appId)
                                               .pipelineExecutionId(pipelineExecutionId)
                                               .workflowExecutionId(followingWorkflowExecutionId)
                                               .build())
                    .build())
            .build();

    assertThat(expressionEvaluator.substitute("${workflow.text}", context)).isEqualTo("bar");
  }
}

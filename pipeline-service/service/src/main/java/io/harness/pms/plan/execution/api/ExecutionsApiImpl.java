package io.harness.pms.plan.execution.api;

import io.harness.spec.server.pipeline.ExecutionsApi;
import io.harness.spec.server.pipeline.model.InterruptRequestBody;
import io.harness.spec.server.pipeline.model.PipelineExecuteRequestBody;

import java.util.List;
import javax.ws.rs.core.Response;

public class ExecutionsApiImpl implements ExecutionsApi {
  @Override
  public Response executePipeline(
      PipelineExecuteRequestBody pipelineExecuteRequestBody, String s, String s1, String s2, String s3, String s4) {
    return null;
  }

  @Override
  public Response getExecutionDetails(String s, String s1, String s2, String s3) {
    return null;
  }

  @Override
  public Response getExecutionDetailsGraph(String s, String s1, String s2, String s3, String s4, Boolean aBoolean) {
    return null;
  }

  @Override
  public Response getRuntimeTemplate(String s, String s1, String s2, String s3, List list, String s4) {
    return null;
  }

  @Override
  public Response listExecutions(String s, String s1, String s2, Integer integer, Integer integer1, String s3,
      String s4, String s5, List<String> list, String s6, List<String> list1, Boolean aBoolean, String s7, String s8,
      String s9, String s10, String s11, List<String> list2, List<String> list3, List<String> list4, String s12) {
    return null;
  }

  @Override
  public Response registerInterrupt(
      InterruptRequestBody interruptRequestBody, String s, String s1, String s2, String s3) {
    return null;
  }
}

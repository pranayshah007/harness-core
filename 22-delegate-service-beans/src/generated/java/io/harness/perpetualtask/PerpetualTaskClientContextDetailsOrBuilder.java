// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: io/harness/perpetualtask/perpetual_task_client.proto

package io.harness.perpetualtask;

@javax.annotation.
Generated(value = "protoc", comments = "annotations:PerpetualTaskClientContextDetailsOrBuilder.java.pb.meta")
public interface PerpetualTaskClientContextDetailsOrBuilder extends
    // @@protoc_insertion_point(interface_extends:io.harness.perpetualtask.PerpetualTaskClientContextDetails)
    com.google.protobuf.MessageOrBuilder {
  /**
   * <code>.io.harness.perpetualtask.TaskClientParams task_client_params = 1[json_name = "taskClientParams"];</code>
   * @return Whether the taskClientParams field is set.
   */
  boolean hasTaskClientParams();
  /**
   * <code>.io.harness.perpetualtask.TaskClientParams task_client_params = 1[json_name = "taskClientParams"];</code>
   * @return The taskClientParams.
   */
  io.harness.perpetualtask.TaskClientParams getTaskClientParams();
  /**
   * <code>.io.harness.perpetualtask.TaskClientParams task_client_params = 1[json_name = "taskClientParams"];</code>
   */
  io.harness.perpetualtask.TaskClientParamsOrBuilder getTaskClientParamsOrBuilder();

  /**
   * <code>.io.harness.perpetualtask.PerpetualTaskExecutionBundle execution_bundle = 2[json_name =
   * "executionBundle"];</code>
   * @return Whether the executionBundle field is set.
   */
  boolean hasExecutionBundle();
  /**
   * <code>.io.harness.perpetualtask.PerpetualTaskExecutionBundle execution_bundle = 2[json_name =
   * "executionBundle"];</code>
   * @return The executionBundle.
   */
  io.harness.perpetualtask.PerpetualTaskExecutionBundle getExecutionBundle();
  /**
   * <code>.io.harness.perpetualtask.PerpetualTaskExecutionBundle execution_bundle = 2[json_name =
   * "executionBundle"];</code>
   */
  io.harness.perpetualtask.PerpetualTaskExecutionBundleOrBuilder getExecutionBundleOrBuilder();

  /**
   * <code>.google.protobuf.Timestamp last_context_updated = 3[json_name = "lastContextUpdated"];</code>
   * @return Whether the lastContextUpdated field is set.
   */
  boolean hasLastContextUpdated();
  /**
   * <code>.google.protobuf.Timestamp last_context_updated = 3[json_name = "lastContextUpdated"];</code>
   * @return The lastContextUpdated.
   */
  com.google.protobuf.Timestamp getLastContextUpdated();
  /**
   * <code>.google.protobuf.Timestamp last_context_updated = 3[json_name = "lastContextUpdated"];</code>
   */
  com.google.protobuf.TimestampOrBuilder getLastContextUpdatedOrBuilder();

  public io.harness.perpetualtask.PerpetualTaskClientContextDetails.ParametersCase getParametersCase();
}

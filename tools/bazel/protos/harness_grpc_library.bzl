load("@rules_proto_grpc//java:defs.bzl", "java_grpc_compile", "java_proto_compile")

def harness_proto_library(**kwargs):
    # Compile protos
    name_pb = kwargs.get("name") + "_pb"
    java_proto_compile(
        name = name_pb,
        **{k: v for (k, v) in kwargs.items() if k in ("deps", "verbose")}  # Forward args
    )

    # Create java library
    native.java_library(
        name = kwargs.get("name"),
        srcs = [name_pb],
        deps = PROTO_DEPS,
        visibility = kwargs.get("visibility"),
    )

PROTO_DEPS = [
    "@maven//:com_google_protobuf_protobuf_java",
]

def harness_grpc_library(**kwargs):
    # Compile protos
    name_pb = kwargs.get("name") + "_pb"
    java_grpc_compile(
        name = name_pb,
        **{k: v for (k, v) in kwargs.items() if k in ("deps", "verbose")}  # Forward args
    )

    if kwargs.get("java_sources") != None:
        fail("do not use java_sources for the grpc library")

    native.java_library(
        name = kwargs.get("name"),
        srcs = [name_pb],
        deps = GRPC_DEPS,
        visibility = kwargs.get("visibility"),
    )

GRPC_DEPS = [
    "@maven//:io_grpc_grpc_protobuf",
    "@maven//:io_grpc_grpc_stub",
]

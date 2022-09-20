load("@io_bazel_rules_docker//container:container.bzl", "container_image", "container_pull", "container_push")
load("@io_bazel_rules_docker//docker/util:run.bzl", "container_run_and_commit_layer")
load("@rules_pkg//:pkg.bzl", "pkg_tar")

def docker_layers(
    name = "",
    image = "@ubi-java-base//image",
    commands = [],
    tags = ["manual", "no-cache", "no-ide"],
    ):

    container_run_and_commit_layer(
        name = name,
        image = image,
        commands = commands,
        tags = tags,
    )

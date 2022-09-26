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

def docker_image(
    name = "",
    base = "@ubi-java-base//image",
    dockerlayers = [],
    user = "65534",
    workdir = "/opt/harness",
    cmd = [],
    filestocopy = [],
    env = {},
    tags = ["manual", "no-cache", "no-ide"],
    ):
        container_image(
            name = name,
            base = base,
            layers = dockerlayers,
            user = user,
            workdir = workdir,
            cmd = cmd,
            tars = filestocopy,
            env = env,
            tags = tags,
        )

def docker_push(
    name = "",
    format = "Docker",
    image = "",
    registry = "us.gcr.io",
    repository = "",
    imagetag = "",
    tags = ["manual", "no-cache", "no-ide"],
    ):
        container_push(
            name = name,
            format = format,
            image = image,
            registry = registry,
            repository = repository,
            tag = imagetag,
            tags = tags,
        )

def docker_pkg(
    name = "",
    srcs = [],
    files = {},
    mode = "0500",
    owner = "65534.65534",
    package_dir = "/opt/harness",
    tags = ["manual", "no-cache", "no-ide"],
    ):
        pkg_tar(
            name = name,
            srcs = srcs,
            files = files,
            mode = mode,
            owner = owner,
            package_dir = package_dir,
            tags = tags,
        )

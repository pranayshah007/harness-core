# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

load("@rules_java//java:defs.bzl", orginal_java_binary = "java_binary", orginal_java_library = "java_library")
load("//project/flags:java_library_flags.bzl", "REPORT_UNUSED")
load("//project/flags:java_library_flags.bzl", "REPORT_SIZE")
load("//:tools/bazel/unused_dependencies.bzl", "report_unused")
load("//:tools/bazel/aeriform.bzl", "aeriformAnnotations")
load("//:tools/bazel/brannock.bzl", "estimate_size")
load("//:tools/bazel/brannock.bzl", "brannock_aspect")
load("//:tools/bazel/brannock.bzl", "DepSizeInfo")

_SCRIPT = "cat {srcs} >> {out}"

def _java_library_rule_impl(ctx):
    sizes = []
    for dep in ctx.attr.deps:
        print("For dep %s adding sizes %s" % (dep, dep[OutputGroupInfo].size.to_list()))
        sizes.extend(dep[OutputGroupInfo].size.to_list())

    #        for info in ctx.attr.deps[OutputGroupInfo].size.to_list():
    #            print(info.basename + " has size ")

    out = ctx.outputs.out

    val = "Out file is " + out.root.path + " concat " + str(len(sizes))
    #    print(val)

    cmd = _SCRIPT.format(
        srcs = " ".join([p.path for p in sizes]),
        out = out.path,
    )
    #    ctx.actions.write(
    #        output = out,
    #        content = val,
    #    )

    #        print("Final cmd is " + cmd)
    ctx.actions.run_shell(
        inputs = sizes,
        outputs = [out],
        command = cmd,
    )
    return [DefaultInfo(
        files = depset([out]),
        #        executable = out,
    ), OutputGroupInfo(size = depset([out]))]

java_library_rule = rule(
    implementation = _java_library_rule_impl,
    #    executable = True,
    attrs = {
        "out": attr.output(mandatory = True),
        "deps": attr.label_list(aspects = [brannock_aspect]),
    },
)

def java_library(**kwargs):
    tags = kwargs.pop("tags", [])

    orginal_java_library(tags = tags + ["harness"], **kwargs)

    if REPORT_UNUSED:
        report_unused(orginal_java_library, tags = tags, **kwargs)

    if REPORT_SIZE:
        estimate_size(kwargs.get("name"), kwargs.get("testonly"))

    #aeriformAnnotations(**kwargs)

def harness_sign(jar):
    name = jar.rsplit("/", 1)[-1][:-4] + "_signed"
    signed_jar = jar.rsplit(":", 1)[-1][:-4] + "_signed.jar"

    native.genrule(
        name = name,
        srcs = [jar],
        outs = [signed_jar],
        exec_tools = ["//:tools/bazel/signer.sh"],
        cmd = " && ".join([
            "cp $(location %s) \"$@\"" % (jar),
            " ".join([
                "$(location //:tools/bazel/signer.sh)",
                "\"bazel-out/stable-status.txt\"",
                "$(JAVABASE)/bin/jarsigner",
                "-storetype pkcs12",
                "-keystore \\$${SIGNER_KEY_STORE}",
                "-storepass \\$${SIGNER_KEY_STORE_PASSWORD}",
                "\"$@\"",
                "harnessj",
            ]),
        ]),
        visibility = ["//visibility:public"],
        toolchains = ["@bazel_tools//tools/jdk:current_host_java_runtime"],
        stamp = True,
    )

    return name

def java_binary(**kwargs):
    name = kwargs.get("name")

    sign = kwargs.pop("sign", False)

    tags = kwargs.pop("tags", [])
    orginal_java_binary(tags = tags + ["harness"], **kwargs)

    if sign:
        harness_sign(name + "_deploy.jar")

    if REPORT_UNUSED:
        report_unused(orginal_java_binary, **kwargs)

# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

DepSizeInfo = provider(
    fields = [
        "info",
    ],
)

SizeInfo = provider(
    fields = [
        "size",
        "name",
        "depth",
    ],
)

def _get_size(file, ctx):
    in_file = file

    #    print("Creating file: %s" % file.basename)
    out_file = ctx.actions.declare_file("%s.size" % file.basename)

    ctx.actions.run_shell(
        inputs = [in_file],
        outputs = [out_file],
        progress_message = "Getting size of %s" % in_file.short_path,
        command = "ls -alhL '%s'| grep -v Size | awk '{print \"@%s//%s:%s \" $5}' > '%s'" % (in_file.path, in_file.owner.workspace_name, in_file.owner.package, in_file.owner.name, out_file.path),
        #        command = "wc -c '%s' | awk '{print $1}' > '%s'; cat '%s'" %
        #                  (in_file.path, out_file.path, out_file.path),
    )

    #    return [DefaultInfo(files = depset([out_file]))]
    return out_file

def _brannock_aspect_impl(target, ctx):
    name = target.label.name.replace("/", "").replace(":", "!")
    print("Running brannock for " + name)

    #    [print("Target base: " + tar.basename + " path " + tar.path) for tar in target.files.to_list()]
    #    [print("Target base: " + tar.root.path + " path " + tar.path) for tar in target.files.to_list()]
    out_files = []
    for tar in target.files.to_list():
        print("Processing target: %s" % tar)
        out_files.append(_get_size(file = tar, ctx = ctx))

    #    [_get_size(file = tar, ctx = ctx) for tar in target.files.to_list()]
    #    print("Final brannock files: " + str(out_files))
    return [DepSizeInfo(info = depset(
        [SizeInfo(size = 2, name = target.label.name, depth = 3)],
        #        [estimate_size(target, False)],
        transitive = [dep[DepSizeInfo].info for dep in ctx.rule.attr.deps],
    )), OutputGroupInfo(size = depset(
        out_files,
        transitive = [dep[OutputGroupInfo].size for dep in ctx.rule.attr.deps],
    ))]

    # Make sure the rule has a deps attribute.
    #    if hasattr(ctx.rule.attr, "deps"):
    # Iterate through the sources counting files
    #        for src in ctx.rule.attr.srcs:
    #            for f in src.files.to_list():
    #                if ctx.attr.extension == "*" or ctx.attr.extension == f.extension:
    #                    count = count + 1

    # Get the counts from our dependencies.

#    for dep in ctx.rule.attr.deps:
#        count = count + dep[FileCountInfo].count
#    return [FileCountInfo(count = count)]

brannock_aspect = aspect(
    implementation = _brannock_aspect_impl,
    attr_aspects = ["deps"],
)

#def create_binary(target, testonly, **kwargs):
#    native.java_binary(
#        name = name + "_brannock",
#        main_class = "com.example.dummy",
#        testonly = testonly,
#        visibility = ["//visibility:public"],
#        runtime_deps = [target],
#        deps = [],
#    )

def estimate_size(target, testonly, **kwargs):
    name = target.replace("/", "").replace(":", "!")
    #    name = target.label.name.replace("/", "").replace(":", "!")

    #    print(target)
    #    print(name)

    native.java_binary(
        name = name + "_brannock",
        main_class = "com.example.dummy",
        testonly = testonly,
        visibility = ["//visibility:public"],
        runtime_deps = [target],
        deps = [],
    )

    native.genrule(
        name = name + "_sizer",
        srcs = [":" + name + "_brannock_deploy.jar"],
        outs = [name + "_size.txt"],
        cmd = "echo brannick; echo $@; ls -alhL $(location " + name + "_brannock_deploy.jar) >> $@; cat $@; echo $(execpath " + name + "_brannock_deploy.jar);echo $(rootpath " + name + "_brannock_deploy.jar);",
    )

def _emit_size_impl(ctx):
    # The input file is given to us from the BUILD file via an attribute.
    in_file = ctx.file.file

    # The output file is declared with a name based on the target's name.
    out_file = ctx.actions.declare_file("%s.size" % ctx.attr.name)

    ctx.actions.run_shell(
        # Input files visible to the action.
        inputs = [in_file],
        # Output files that must be created by the action.
        outputs = [out_file],
        # The progress message uses `short_path` (the workspace-relative path)
        # since that's most meaningful to the user. It omits details from the
        # full path that would help distinguish whether the file is a source
        # file or generated, and (if generated) what configuration it is built
        # for.
        progress_message = "Getting size of %s" % in_file.short_path,
        # The command to run. Alternatively we could use '$1', '$2', etc., and
        # pass the values for their expansion to `run_shell`'s `arguments`
        # param (see convert_to_uppercase below). This would be more robust
        # against escaping issues. Note that actions require the full `path`,
        # not the ambiguous truncated `short_path`.
        command = "wc -c '%s' | awk '{print $1}' > '%s'" %
                  (in_file.path, out_file.path),
    )

    # Tell Bazel that the files to build for this target includes
    # `out_file`.
    return [DefaultInfo(files = depset([out_file]))]

emit_size = rule(
    implementation = _emit_size_impl,
    attrs = {
        "file": attr.label(
            mandatory = True,
            allow_single_file = True,
            doc = "The file whose size is computed",
        ),
    },
    doc = """
Given an input file, creates an output file with the extension `.size`
containing the file's size in bytes.
""",
)

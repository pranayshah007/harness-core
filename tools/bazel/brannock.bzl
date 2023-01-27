# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

def _get_size(file, ctx):
    in_file = file
    out_file = ctx.actions.declare_file("%s.size" % file.basename)

    # This shell command will take one file (actual dependency jar).
    # It will out it's size in bytes in first column and target name in second. Final file will only have one line
    # 1. wc -c '%s' ... gets size in bytes
    # 2. ... (index(\"%s\", \"maven\")?\"@\":\"\") \"%s//%s:%s\" ... adds target name, prepending maven targets with @ so they have expected name
    ctx.actions.run_shell(
        inputs = [in_file],
        outputs = [out_file],
        progress_message = "Getting size of %s" % in_file.short_path,
        command = "wc -c '%s' | awk '{print $1 \"\t\" (index(\"%s\", \"maven\")?\"@\":\"\") \"%s//%s:%s\"}' > '%s'" % (in_file.path, in_file.owner.workspace_name, in_file.owner.workspace_name, in_file.owner.package, in_file.owner.name, out_file.path),
    )

    return out_file

def _brannock_aspect_impl(target, ctx):
    name = target.label.name.replace("/", "").replace(":", "!")

    out_files = []
    for tar in target.files.to_list():
        out_files.append(_get_size(file = tar, ctx = ctx))

    return [OutputGroupInfo(size = depset(
        out_files,
        transitive = [dep[OutputGroupInfo].size for dep in ctx.rule.attr.deps],
    ))]

brannock_aspect = aspect(
    implementation = _brannock_aspect_impl,
    attr_aspects = ["deps"],
)

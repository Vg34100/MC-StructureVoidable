#!/usr/bin/env python3
"""Project committed work files onto main without merging development history."""
import argparse
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import tempfile


def git(*args, env=None, data=None):
    return subprocess.run(["git", *args], input=data, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, check=True, env=env).stdout


def excluded(path, rules):
    parts = PurePosixPath(path).parts
    return (any(p in rules["directory_names"] for p in parts[:-1])
            or parts[-1] in rules["file_names"]
            or path in rules["paths"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", default="work")
    parser.add_argument("--apply", action="store_true", help="update local main")
    parser.add_argument("--push", metavar="REMOTE", help="publish directly to remote main")
    args = parser.parse_args()
    root = Path(os.fsdecode(git("rev-parse", "--show-toplevel")).strip())
    os.chdir(root)
    source = git("rev-parse", "--verify", args.source + "^{commit}").strip().decode()
    if args.push:
        git("fetch", args.push, "+refs/heads/main:refs/remotes/" + args.push + "/main",
            "+refs/heads/work:refs/remotes/" + args.push + "/work")
        latest = git("rev-parse", "refs/remotes/" + args.push + "/work").strip().decode()
        if latest != source:
            raise SystemExit("Work advanced: refusing to publish a stale revision. Run again on latest work.")
        target_ref = "refs/remotes/" + args.push + "/main"
    else:
        target_ref = "refs/heads/main"
    parent = git("rev-parse", "--verify", target_ref + "^{commit}").strip().decode()
    rules = json.loads(git("show", source + ":.github/public-sync.json"))
    files = [os.fsdecode(p) for p in git("ls-tree", "-rz", "--name-only", source).split(b"\0") if p]
    removed = [p for p in files if excluded(p, rules)]
    # An isolated index never stages, checks out, or deletes local development files.
    with tempfile.TemporaryDirectory(prefix="public-sync-") as tmp:
        env = dict(os.environ, GIT_INDEX_FILE=str(Path(tmp) / "index"))
        git("read-tree", source, env=env)
        if removed:
            git("update-index", "--force-remove", "-z", "--stdin", env=env,
                data=b"".join(os.fsencode(p) + b"\0" for p in removed))
        tree = git("write-tree", env=env).strip().decode()
    old_tree = git("rev-parse", parent + "^{tree}").strip().decode()
    result = dict(source=source, parent=parent, tree=tree, excluded=removed,
                  changed=tree != old_tree, applied=False)
    if tree != old_tree and (args.apply or args.push):
        checked_out = git("rev-parse", "--abbrev-ref", "HEAD").strip().decode()
        if not args.push and checked_out == "main":
            raise SystemExit("Stay on work: refusing to update checked-out main.")
        message = "Update public source snapshot\n\nDevelopment revision: " + source + "\n"
        commit = git("commit-tree", tree, "-p", parent, data=message.encode()).strip().decode()
        if args.push:
            # Normal fast-forward push: concurrent main edits are never overwritten.
            git("push", args.push, commit + ":refs/heads/main")
        else:
            git("update-ref", "refs/heads/main", commit, parent)
        result.update(applied=True, commit=commit)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        raise SystemExit(error.stderr.decode(errors="replace").strip()) from error

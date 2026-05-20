# Datadatdat Delphix Remote Provider

This is a proof of concept remote provider for storing remote repositories on engines. It makes use of
a Delphix plugin that is not generally available and is therefore not yet usable by the general public.

## Remote configuration

The Delphix provider accepts the following remote properties:

| Property         | Type    | Required | Description |
|------------------|---------|----------|-------------|
| `username`       | string  | yes      | Engine user. |
| `address`        | string  | yes      | Engine hostname or IP. |
| `repository`     | string  | yes      | Name of the engine-hosted repository (VDB) used to store commits. |
| `password`       | string  | no       | Password baked into the remote (use `parameters.password` instead for credentials). |
| `knownHostsFile` | string  | no       | Path to a `known_hosts` file used for host-key verification on the rsync data path. Defaults to `~/.ssh/known_hosts`. |
| `skipHostCheck`  | bool    | no       | Disable host-key verification on the rsync data path. **Default: `false`.** See below. |

### Host-key verification

The Delphix provider uses rsync over SSH (via `remote-sdk`'s `RsyncExecutor`)
to push/pull data to/from a short-lived VDB on the engine. Starting in the
version that resolves [#51](https://github.com/datadatdat/delphix-remote/issues/51),
that rsync invocation verifies the engine's host key by default
(`StrictHostKeyChecking=yes`) against `~/.ssh/known_hosts` (or
`knownHostsFile` if set).

**Before running `d3` against a new engine, populate `known_hosts` and verify
the fingerprint out-of-band:**

```bash
ssh-keyscan -H engine.example.com >> ~/.ssh/known_hosts
ssh-keygen -lf ~/.ssh/known_hosts | grep engine.example.com
# Compare the fingerprint against a trusted source (the engine operator,
# a configuration management system, etc.) before proceeding.
```

To override the file location for a single remote:

```yaml
knownHostsFile: /etc/datadatdat/known_hosts
```

#### Behavior matrix

| `skipHostCheck`                      | `knownHostsFile`               | `ssh -o StrictHostKeyChecking` | `-o UserKnownHostsFile`           |
|--------------------------------------|--------------------------------|--------------------------------|-----------------------------------|
| unset / `false`                      | unset                          | `yes`                          | `~/.ssh/known_hosts`              |
| unset / `false`                      | `/etc/datadatdat/known_hosts`  | `yes`                          | `/etc/datadatdat/known_hosts`     |
| `true`                               | (ignored)                      | `no`                           | `/dev/null`                       |

### Opting out (`skipHostCheck`)

For deployments where host-key verification is impractical — short-lived CI
runners, trusted private networks, ephemeral engines provisioned per test
run — set `skipHostCheck: true` on the remote. This restores the legacy
behavior of `StrictHostKeyChecking=no` + `UserKnownHostsFile=/dev/null`.

```yaml
remote:
  username: admin
  address: engine.example.com
  repository: myrepo
  skipHostCheck: true
```

The property accepts either booleans (`true` / `false`) or the string literals
`"true"` / `"false"` (case-insensitive) so JSON payloads serialized by either
convention work unchanged. Any other value is rejected at `validateRemote`
time so a typo like `"yes"` cannot silently disable a security control.

### Migrating from earlier versions

**BREAKING CHANGE.** Previous versions of the Delphix provider went through
`remote-sdk`'s `RsyncExecutor`, which unconditionally disabled host-key
checking (`StrictHostKeyChecking=no` + `UserKnownHostsFile=/dev/null`). After
the fix lands the rsync data path enforces host-key verification by default,
which means existing deployments will start failing with
`Host key verification failed.` against engines that are not in
`known_hosts`.

To upgrade without service interruption:

1. **Preferred:** populate `~/.ssh/known_hosts` (or a custom
   `knownHostsFile`) on every machine that runs `d3` against a Delphix
   remote, then upgrade. No configuration change required.
2. **Bridge:** add `skipHostCheck: true` to existing remotes to preserve the
   old behavior, then incrementally migrate hosts onto `known_hosts` and
   remove the flag.

Engines are not typically pre-registered in operator `known_hosts` files the
way long-lived bastion hosts are, so the bridge path may be common for
established deployments. The recommended posture is still option (1): verify
the engine fingerprint once, pin it, and let `StrictHostKeyChecking=yes`
catch any future change.

## Contributing

This project follows the Datadatdat community best practices:

  * [Contributing](https://github.com/datadatdat/.github/blob/master/CONTRIBUTING.md)
  * [Code of Conduct](https://github.com/datadatdat/.github/blob/master/CODE_OF_CONDUCT.md)
  * [Community Support](https://github.com/datadatdat/.github/blob/master/SUPPORT.md)

It is maintained by the [Datadatdat community maintainers](https://github.com/datadatdat/.github/blob/master/MAINTAINERS.md)

For more information on how it works, and how to build and release new versions,
see the [Development Guidelines](DEVELOPING.md).


<!--
[comment]: # SPDX-License-Identifier: Apache-2.0
[comment]: # SPDX-FileCopyrightText: 2024 The Linux Foundation
-->

# 📦 TOX Extract Config Action

Extracts a value from the TOX configuration file, can optionally pass it
through other shell commands so the string can be manipulated.

## tox-extract-config-action

## Usage Example

Call as a step in a larger composite action or workflow.

```yaml
steps:
      - uses: lfit/releng-reusable-workflows/.github/actions/automatic-tests@main # v1.0.0
        id: get-python
        with:
          # Equivalent shell command provided below
          # tox config -c docs/tox.ini -edocs | grep base_python | awk '{print $3}' | sed "s:python::g"
          tox_key: "base_python"
          tox_config_path: "docs/tox.ini"
          tox_environment: "docs"
          awk_command: "awk '{print $3}'"
          sed_command: "sed 's:python::g'"
```

## Inputs

<!-- markdownlint-disable MD013 -->

| Variable Name   | Required | Default | Description                              |
| --------------- | -------- | ------- | ---------------------------------------- |
| TOX_KEY         | True     | N/A     | The key for which to find/return a value |
| TOX_CONFIG_PATH | False    | tox.ini | Uses tox.ini at top level of repository  |
| TOX_ENVIRONMENT | False    | None    | Defaults to top level stanza             |
| AWK_COMMAND     | False    | None    | Full shell awk command to run after tox  |
| SED_COMMAND     | False    | None    | Full shell command to run after awk      |

<!-- markdownlint-enable MD013 -->

## Outputs

<!-- markdownlint-disable MD013 -->

| Variable Name | Mandatory | Description                                           |
| ------------- | --------- | ----------------------------------------------------- |
| TOX_VALUE     | No        | May be null if the query or string manipulation fails |

<!-- markdownlint-enable MD013 -->

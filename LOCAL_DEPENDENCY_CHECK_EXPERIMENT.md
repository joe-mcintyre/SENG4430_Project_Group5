# Local Dependency-Check experiment

This branch adds an experiment path for running a local Dependency-Check CLI executable so timing can be compared against the current Maven-plugin flow.

## What changed

- Added `--dependency-check-bin` to point at a local `dependency-check.bat` or `dependency-check.sh`
- Added `--dependency-check-data` to point at a reusable local cache/data directory
- Added `--dependency-check-no-update` to test warm-cache runs without refreshing the data
- Added elapsed-time logging for Dependency-Check report generation
- Preserved the existing Maven-plugin fallback when the local CLI path is not provided

## Suggested timing runs

### 1. Cold run with local CLI and empty cache

```bash
java -jar target/quality-auditor-tool.jar \
  --source src/main/java \
  --dependency-check-bin "C:\\tools\\dependency-check\\bin\\dependency-check.bat" \
  --dependency-check-data "C:\\tools\\dependency-check-data-cold" \
  --output reports/local-cli-cold
```

### 2. Warm run with local CLI and existing cache

```bash
java -jar target/quality-auditor-tool.jar \
  --source src/main/java \
  --dependency-check-bin "C:\\tools\\dependency-check\\bin\\dependency-check.bat" \
  --dependency-check-data "C:\\tools\\dependency-check-data-cold" \
  --dependency-check-no-update \
  --output reports/local-cli-warm
```

### 3. Current Maven-plugin path for comparison

```bash
java -jar target/quality-auditor-tool.jar \
  --source src/main/java \
  --output reports/maven-plugin-baseline
```

## What to record

- Cold run elapsed time
- Warm run elapsed time
- Maven-plugin baseline elapsed time
- Whether the machine already had Java and Maven installed
- Whether the machine was online and on a fast or slow connection

The console now prints the Dependency-Check generation time in milliseconds so the comparison can be copied straight into notes or the report.

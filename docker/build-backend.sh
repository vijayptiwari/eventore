#!/bin/sh
# Build backend JAR with comma-separated Maven profiles in EVENTORE_STREAM_PROFILES.
# Example: provider-kafka  |  kafka-kinesis  |  providers-all
set -eu
cd "${EVENTORE_BACKEND_DIR:-/app/backend}"

if [ "${EVENTORE_STREAM_PROFILES}" = "providers-all" ]; then
  exec mvn -B -Dmaven.test.skip=true package
fi

set --
IFS=,
for profile in ${EVENTORE_STREAM_PROFILES}; do
  profile=$(echo "$profile" | tr -d ' ')
  set -- "$@" "-P${profile}"
done
# Full tests run in the all-provider CI job. Slim images exclude provider-specific
# production classes, so they must also skip compilation of the all-provider tests.
exec mvn -B -Dmaven.test.skip=true "$@" '-P!providers-all' package

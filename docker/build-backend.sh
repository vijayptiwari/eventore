#!/bin/sh
# Build backend JAR with comma-separated Maven profiles in EVENTORE_STREAM_PROFILES.
# Example: provider-kafka  |  provider-kafka,provider-kinesis  |  providers-all
set -eu
cd /app/backend

if [ "${EVENTORE_STREAM_PROFILES}" = "providers-all" ]; then
  exec mvn -B -DskipTests package
fi

set --
IFS=,
for profile in ${EVENTORE_STREAM_PROFILES}; do
  profile=$(echo "$profile" | tr -d ' ')
  set -- "$@" "-P${profile}"
done
exec mvn -B -DskipTests "$@" '-P!providers-all' package

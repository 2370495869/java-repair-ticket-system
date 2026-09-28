#!/usr/bin/env sh
set -eu
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8
export PYTHONIOENCODING=utf-8
cd "$(dirname "$0")"
./mvnw -B -ntp verify
exec java -jar "target/java-repair-ticket-system.jar" --spring.profiles.active=local "$@"

#!/bin/bash
set -e

echo "Initiating..."

SONAR_RESTORE_PROFILE_URL="$SONAR_URL/api/qualityprofiles/restore"
SONAR_DEFAULT_PROFILE_URL="$SONAR_URL/api/qualityprofiles/set_default"

CUSTOM_PROFILE_JAVA="Sonar%20way%20(extended)"
FILE_JAVA="/java_profile.xml"

# INSTALL CURL
apk --no-cache add curl

# WAIT UNTIL SONARQUBE IS UP
until curl --output /dev/null --silent --head --fail -u "$SONAR_USER":"$SONAR_PASSWORD" "$SONAR_URL/api/system/health"; do
  >&2 echo "Sonarqube is unavailable - sleeping"
  sleep 1
done
>&2 echo "Sonarqube is up - executing command"

# PROFILE - JAVA
echo "Importing custom profile ($FILE_JAVA)..."

curl -X POST -u "$SONAR_USER":"$SONAR_PASSWORD" "$SONAR_RESTORE_PROFILE_URL" --form backup=@$FILE_JAVA

echo "Setting the profile as default..."
curl -u admin:admin -d "language=java&qualityProfile=$CUSTOM_PROFILE_JAVA" -X POST "$SONAR_DEFAULT_PROFILE_URL"

echo "Data import done."
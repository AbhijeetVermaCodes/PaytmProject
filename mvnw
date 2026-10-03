#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper Executable for Unix
# ----------------------------------------------------------------------------

PRG="$0"
while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`"/$link"
  fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

if [ -f "$APP_HOME/.mvn/maven/apache-maven-3.9.6/bin/mvn" ]; then
    chmod +x "$APP_HOME/.mvn/maven/apache-maven-3.9.6/bin/mvn"
    exec "$APP_HOME/.mvn/maven/apache-maven-3.9.6/bin/mvn" "$@"
else
    exec mvn "$@"
fi

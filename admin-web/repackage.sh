#! /bin/sh

#-----------------------------------------------------------------------------------
# Repackage a war file by adding specified jars in its WEB-INF/lib directory
# 
# Parameters:
# - $1 is the path to the war file to repackage
#   example: build/jppf-admin-web-6.0.war
# - all subsequent parameters specify the path to one or more jars to add
#   Wildcards * and ? are allowed
#   example: ~/jppf/lib/mylib.jar ~/jppf/lib2/*.jar 
# 
# Full example usage:
# ./repackage.sh build/jppf-admin-web-6.0.war ~/jppf/lib/mylib.jar ~/jppf/lib2/*.jar 
#-----------------------------------------------------------------------------------

# compute the directory of this script
SCRIPT=$(readlink -f "$0")
SCRIPT_PATH=$(dirname "$SCRIPT")
TEMP_FOLDER=$SCRIPT_PATH/tmp
LIB_FOLDER=$TEMP_FOLDER/WEB-INF/lib

rm -rf "$TEMP_FOLDER"
mkdir -p "$LIB_FOLDER"

echo copying jar files to $LIB_FOLDER

for p in $*; do
  if [ "$p" != "$1" ]; then
    cp $p "$LIB_FOLDER"
  fi
done

echo updating $1

jar uf $1 -C "$TEMP_FOLDER" .

rm -rf "$TEMP_FOLDER"

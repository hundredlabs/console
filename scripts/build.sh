#!/usr/bin/env bash

# Build the console after replacing the version
SBT_VERSION_FILE=`cat version.sbt`

IFS=':=' read -ra ADDR <<< "$SBT_VERSION_FILE"
for i in "${ADDR[@]}"; do
    CURRENT_VER=$(eval echo $i)
done
if test "$1"; then
    NEW_VERSION=$1
else
    NEW_VERSION=$CURRENT_VER
fi
CURRENT_VERSION="$(sed -e 's/\./\\./' <<< "$CURRENT_VER")"
echo "> Cleanup the static files"
rm -rf server/sbin/ui/
mkdir server/sbin/ui

if [[ "$NEW_VERSION" == *SNAPSHOT ]]
then
    echo "Building the snapshot version"
    sed -i '' "s/$CURRENT_VERSION/$NEW_VERSION/" scripts/mac_dev.sh
    sed -i '' "s/$CURRENT_VERSION/$NEW_VERSION/" scripts/nix_dev.sh
else
    echo "Building the prod version"
    sed -i '' "s/$CURRENT_VERSION/$NEW_VERSION/" scripts/mac.sh
    sed -i '' "s/$CURRENT_VERSION/$NEW_VERSION/" scripts/nix.sh

fi

echo "> Setting the  version : $NEW_VERSION"
sed -i '' "s/$CURRENT_VERSION/$NEW_VERSION/" console/package.json
sed -i '' "s/$CURRENT_VERSION/$NEW_VERSION/" server/sbin/Darwin/gxc
sed -i '' "s/$CURRENT_VERSION/$NEW_VERSION/" server/sbin/Linux/gxc
sed -i '' "s/$CURRENT_VERSION/$NEW_VERSION/" version.sbt

echo "> Building the Console"
cd console && yarn build
echo "> Copying the to static directory"
cd ../ && cp -r console/build/ server/sbin/ui

echo "> Building the zip and tar"

sbt ";project gigahex-server; dist ;universal:packageZipTarball"



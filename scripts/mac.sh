#!/usr/bin/env bash

#!/usr/bin/env bash

# Install postgres on mac

{ # Prevent execution if this script was only partially downloaded

set -e

green="\033[32m"
red="\033[31m"
reset="\033[0m"
bs="\033[1m"
be="\033[0m"
#Local environment variables
RELEASE_VER="0.3.0"
INSTALL_DIR=${HOME}/gigahex
PACKAGE_NAME=gigahex-server
BIN_PATH=${INSTALL_DIR}/bin/gxc
DOWNLOAD_URL=https://github.com/GigahexHQ/gigahex/releases/download/v${RELEASE_VER}/gigahex-server-${RELEASE_VER}.zip
cmd_exists() {
	command -v "$@" > /dev/null 2>&1
}

setup_local_bin(){
zsh_file=${HOME}/.zshrc
profile_file=${HOME}/.profile
bash_profile=${HOME}/.bash_profile

#Create the local bin directory
mkdir -p ${INSTALL_DIR}/bin

# Setup for local profile
if [ ! -f "$profile_file" ]
then
    printf "$red> local profile file not found. Gigahex commands won't be added to local bin directory.\n$reset"
else
    bin_line="$(grep -n "gigahex" $profile_file | head -n 1 | cut -d: -f1)"
    if [ ! -n "${bin_line}" ]; then
        printf "> Setting up the local profile with support of Gigahex\n"
        echo "export PATH=${INSTALL_DIR}/bin:\$PATH" >> $profile_file

    fi

fi

if [ -f "$bash_profile" ]
then
    lineNum="$(grep -n "gigahex" $bash_profile | head -n 1 | cut -d: -f1)"
    if [ ! -n "${lineNum}" ]; then
        printf "> Setting the local bin for gigahex\n"
        echo "export PATH=${INSTALL_DIR}/bin:\$PATH" >> $bash_profile
    fi

fi

if [ -f "$zsh_file" ]
then
    lineNum="$(grep -n "gigahex" $zsh_file | head -n 1 | cut -d: -f1)"
    if [ ! -n "${lineNum}" ]; then
        printf "> Setting the local bin for gigahex\n"
        echo "export PATH=${INSTALL_DIR}/bin:\$PATH" >> $zsh_file
    fi

fi


}
setup_local_bin

mkdir -p ${HOME}/gigahex/logs
mkdir -p ${HOME}/gigahex/tmp
mkdir -p ${HOME}/gigahex/images

printf "> Downloading $DOWNLOAD_URL\n"
curl -fSL $DOWNLOAD_URL -o ${PACKAGE_NAME}-${RELEASE_VER}.zip

printf "> Installing at $INSTALL_DIR\n"
unzip -d $INSTALL_DIR -o -q ${PACKAGE_NAME}-${RELEASE_VER}.zip
STATIC_PATH="$INSTALL_DIR/${PACKAGE_NAME}-${RELEASE_VER}/sbin/ui"
cat <<EOF | tee $INSTALL_DIR/${PACKAGE_NAME}-${RELEASE_VER}/sbin/Caddyfile >/dev/null
:9080 {
	encode gzip zstd

	@back-end path /api/* /web/* /ws/*
	handle @back-end {
		reverse_proxy 127.0.0.1:9000
        }

	handle {
		root * $INSTALL_DIR/${PACKAGE_NAME}-${RELEASE_VER}/sbin/ui
		try_files {path} /index.html
		file_server
	}
}
EOF

rm ${PACKAGE_NAME}-${RELEASE_VER}.zip

ln -sfn $INSTALL_DIR/${PACKAGE_NAME}-${RELEASE_VER}/sbin/Darwin/gxc $BIN_PATH
printf "$green> Gigahex successfully installed!\n$reset"
printf "$bs> Restart the shell and run 'gxc help' to get started with Gigahex Data Platform\n$be"

} # End of wrapping




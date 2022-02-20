#!/usr/bin/env bash


{ # Prevent execution if this script was only partially downloaded

set -e

green="\033[32m"
red="\033[31m"
reset="\033[0m"
bs="\033[1m"
be="\033[0m"

RELEASE_VER="0.3.0-SNAPSHOT"
INSTALL_DIR=${HOME}/gigahex
PACKAGE_NAME=gigahex-server
BIN_PATH=${INSTALL_DIR}/bin/gxc
DOWNLOAD_URL=https://packages.gigahex.com/snapshots/community/${PACKAGE_NAME}-${RELEASE_VER}.tgz

cmd_exists() {
	command -v "$@" > /dev/null 2>&1
}


setup_local_bin(){
zsh_file=${HOME}/.zshrc
profile_file=${HOME}/.profile

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

}
setup_local_bin
mkdir -p ${HOME}/gigahex/logs

printf "> Downloading $DOWNLOAD_URL\n"
curl -fSL $DOWNLOAD_URL -o ${PACKAGE_NAME}-${RELEASE_VER}.tgz

printf "> Installing at $INSTALL_DIR\n"
tar -zxf ${PACKAGE_NAME}-${RELEASE_VER}.tgz --directory $INSTALL_DIR
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

rm ${PACKAGE_NAME}-${RELEASE_VER}.tgz
#Soft link for gxc
#Make the files executable
chmod +x $INSTALL_DIR/${PACKAGE_NAME}-${RELEASE_VER}/sbin/Linux/gxc
chmod +x $INSTALL_DIR/${PACKAGE_NAME}-${RELEASE_VER}/sbin/Linux/caddy
ln -sfn $INSTALL_DIR/${PACKAGE_NAME}-${RELEASE_VER}/sbin/Linux/gxc $BIN_PATH
printf "$green> Gigahex successfully installed!\n$reset"
printf "$bs> Restart the shell and run 'gxc help' to get started with Gigahex Data Platform\n$be"

} # End of wrapping




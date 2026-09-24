#!/bin/sh
set -e
P=${PREFIX:-/usr/local}
install -Dm755 clouddisk "$P/bin/clouddisk"
echo "installed to $P/bin/clouddisk"

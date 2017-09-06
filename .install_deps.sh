set -e
set -x
# Install Verilator (http://www.veripool.org/projects/verilator/wiki/Installing)
if [ ! -d "$INSTALL_DIR" ]; then
    mkdir -p "$INSTALL_DIR"
fi

set -e
set -x
# Install Verilator (http://www.veripool.org/projects/verilator/wiki/Installing)
if [ ! -d "$INSTALL_DIR" ]; then
    mkdir -p "$INSTALL_DIR"
fi

orgs=( ucb-bar ucb-bar ucb-bar ucb-bar )
repos=( firrtl chisel3 firrtl-interpreter chisel-testers )
branches=( master master master master )

for idx in "${!repos[@]}"; do
    org=${orgs[$idx]}
    repo=${repos[$idx]}
    branch=${branches[$idx]}
    if [ ! -d $INSTALL_DIR/$repo ]; then
        git clone --recursive --depth=2 "https://github.com/$org/$repo.git" "$INSTALL_DIR/$repo" -b $branch
    fi
    cd $INSTALL_DIR/$repo
    git fetch --all
    git checkout $branch
    git pull
    sbt publish-local
done

set -e
set -x
# Install Verilator (http://www.veripool.org/projects/verilator/wiki/Installing)
if [ ! -d $INSTALL_DIR ]; then
    mkdir $INSTALL_DIR
fi

orgs=( ucb-bar ucb-bar ucb-bar ucb-bar ucb-bar ucb-art ucb-art )
repos=( firrtl chisel3 firrtl-interpreter chisel-testers dsptools rocket-chip testchipip )
branches=( master master master master master craftFork chisel3fix )

for idx in "${!repos[@]}"; do
    org=${orgs[$idx]}
    repo=${repos[$idx]}
    branch=${branches[$idx]}
    if [ ! -d $INSTALL_DIR/$repo ]; then
        git clone "https://github.com/$org/$repo.git" "$INSTALL_DIR/$repo"
    fi
    cd $INSTALL_DIR/$repo
    git pull
    git checkout $branch
    sbt publish-local
done

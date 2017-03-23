set -e
set -x
# Install Verilator (http://www.veripool.org/projects/verilator/wiki/Installing)
if [ ! -d "$INSTALL_DIR" ]; then
    mkdir -p "$INSTALL_DIR"
fi

if [ ! -d $TRAVIS_BUILD_DIR/lib ]; then
    mkdir -p $TRAVIS_BUILD_DIR/lib
fi

orgs=(     ucb-bar ucb-bar ucb-bar            ucb-bar        ucb-bar    ucb-art     ucb-bar    ucb-art          ucb-bar )
repos=(    firrtl  chisel3 firrtl-interpreter chisel-testers dsptools   rocket-chip testchipip builtin-debugger barstools )
branches=( master  master  master             master         master     craftFork   master     master           master )

for idx in "${!repos[@]}"; do
    org=${orgs[$idx]}
    repo=${repos[$idx]}
    branch=${branches[$idx]}
    if [ ! -d $INSTALL_DIR/$repo ]; then
        git clone "https://github.com/$org/$repo.git" "$INSTALL_DIR/$repo" -b $branch
    fi
    cd $INSTALL_DIR/$repo
    if [ ! -e lib ]; then
        ln -s $TRAVIS_BUILD_DIR/lib lib
    fi
    git fetch --all
    git checkout $branch
    git pull
    if [ "$repo" == "rocket-chip" ]; then
        if [ -f src/main/scala/rocketchip/PrivateConfigs.scala ]; then
            cat .gitmodules | grep path | awk '{print $3}' | grep -v hwacha | grep -v riscv-tools | xargs git submodule update --init; rm src/main/scala/rocketchip/PrivateConfigs.scala
        fi
        sbt pack
        cp target/pack/lib/*.jar $TRAVIS_BUILD_DIR/lib
    fi
    sbt publish-local
done

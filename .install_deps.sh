set -e
set -x
# Install Verilator (http://www.veripool.org/projects/verilator/wiki/Installing)
if [ ! -d "$INSTALL_DIR" ]; then
    mkdir -p "$INSTALL_DIR"
fi

if [ ! -d $TRAVIS_BUILD_DIR/lib ]; then
    mkdir -p $TRAVIS_BUILD_DIR/lib
fi

orgs=( ucb-bar ucb-bar ucb-bar ucb-bar ucb-bar ucb-art grebe )
repos=(    firrtl chisel3 firrtl-interpreter chisel-testers dsptools   rocket-chip testchipip )
branches=( master master  master             localflatten   bumpChisel craftFork   chisel3switch )

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
    sbt publish-local
    if [ "$repo" == "rocket-chip" ]; then
        cat .gitmodules | grep path | awk '{print $3}' | grep -v hwacha | grep -v riscv-tools | xargs git submodule update --init; rm src/main/scala/rocketchip/PrivateConfigs.scala; cd ..
        sbt pack
        cp target/pack/lib/*.jar $TRAVIS_BUILD_DIR/lib
    fi
done

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
repos=( firrtl chisel3 firrtl-interpreter chisel-testers dsptools rocket-chip testchipip )
branches=( master master master master master craftFork chisel3switch )

for idx in "${!repos[@]}"; do
    org=${orgs[$idx]}
    repo=${repos[$idx]}
    branch=${branches[$idx]}
    if [ ! -d $INSTALL_DIR/$repo ]; then
        git clone --recursive --depth=2 "https://github.com/$org/$repo.git" "$INSTALL_DIR/$repo" -b $branch
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
        sbt pack
        cp target/pack/lib/*.jar $TRAVIS_BUILD_DIR/lib
    fi

done


# this is silly, but necessary
# cd $INSTALL_DIR/rocket-chip
# sbt pack
# cp target/pack/lib/*.jar $TRAVIS_BUILD_DIR/lib

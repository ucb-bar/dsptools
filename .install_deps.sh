set -e
set -x
# Install Verilator (http://www.veripool.org/projects/verilator/wiki/Installing)
if [ ! -d "$INSTALL_DIR" ]; then
    mkdir -p "$INSTALL_DIR"
fi

if [ ! -d $TRAVIS_BUILD_DIR/lib ]; then
    mkdir -p $TRAVIS_BUILD_DIR/lib
fi

orgs=(     grebe                  grebe            )
repos=(    chisel-testers         rocket-chip      )
branches=( implicitModuleRefactor packageAsLibrary )

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
    sbt publishLocal
done

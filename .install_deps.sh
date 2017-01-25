set -e
set -x
# Install Verilator (http://www.veripool.org/projects/verilator/wiki/Installing)
if [ ! -d $INSTALL_DIR ]; then
    mkdir $INSTALL_DIR
fi

if [ ! -d $TRAVIS_BUILD_DIR/lib ]; then
    mkdir $TRAVIS_BUILD_DIR/lib
fi
orgs=( ucb-bar ucb-bar ucb-bar ucb-bar ucb-bar ucb-art ucb-art )
repos=( firrtl chisel3 firrtl-interpreter chisel-testers dsptools rocket-chip testchipip )
branches=( master master master master master master chisel3fix )

for idx in "${!repos[@]}"; do
    org=${orgs[$idx]}
    repo=${repos[$idx]}
    branch=${branches[$idx]}
    if [ ! -d $INSTALL_DIR/$repo ]; then
        git clone --depth=1 "https://github.com/$org/$repo.git" "$INSTALL_DIR/$repo"
    fi
    cd $INSTALL_DIR/$repo
    git remote update
    git checkout $branch
    sbt publish-local
done

cd $INSTALL_DIR/rocket-chip
sbt pack
cp target/pack/lib/*.jar $TRAVIS_BUILD_DIR/lib

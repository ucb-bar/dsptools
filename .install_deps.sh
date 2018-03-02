set -e
set -x
# Install Verilator (http://www.veripool.org/projects/verilator/wiki/Installing)
if [ ! -d "$INSTALL_DIR" ]; then
    mkdir -p "$INSTALL_DIR"
fi

if [ ! -d $TRAVIS_BUILD_DIR/lib ]; then
    mkdir -p $TRAVIS_BUILD_DIR/lib
fi

orgs=(     freechipsproject freechipsproject freechipsproject   grebe                  grebe            )
repos=(    firrtl           chisel3          firrtl-interpreter chisel-testers         rocket-chip      )
branches=( master           master           master             implicitModuleRefactor packageAsLibrary )

for idx in "${!repos[@]}"; do
    org=${orgs[$idx]}
    repo=${repos[$idx]}
    branch=${branches[$idx]}
    if [ ! -d $INSTALL_DIR/$repo ]; then
        git clone "https://github.com/$org/$repo.git" "$INSTALL_DIR/$repo" -b $branch
    fi
    cd $INSTALL_DIR/$repo
    git fetch --all
    git checkout $branch
    git pull
    sbt publishLocal
done

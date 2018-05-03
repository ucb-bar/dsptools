# This makefile invokes mill

SCALA_VERSION_2_11=2.11.12
SCALA_VERSION_2_12=2.12.4
SCALA_VERSION_DEFAULT?=$(SCALA_VERSION_2_11)

MILL?=./bin/mill
$(MILL):
	mkdir -p ./bin
	curl -L -o $@ https://www.github.com/lihaoyi/mill/releases/download/0.2.0/0.2.0 && chmod +x $@

compile-%: $(MILL)
	$(MILL) dsptools[$(patsubst compile-%,%,$@)].compile
.PHONY: compile-%

compile: compile-$(SCALA_VERSION_DEFAULT)
.PHONY: compile

compile-all: compile-$(SCALA_VERSION_2_11) compile-$(SCALA_VERSION_2_12)
.PHONY: compile-all

test-%: $(MILL)
	$(MILL) dsptools[$(patsubst test-%,%,$@)].test
.PHONY: test-%

test: test-$(SCALA_VERSION_DEFAULT)
.PHONY: test

test-all: test-$(SCALA_VERSION_2_11) test-$(SCALA_VERSION_2_12)
.PHONY: test-all

rocket-compile-%: $(MILL)
	$(MILL) rocket[$(patsubst rocket-compile-%,%,$@)].compile
.PHONY: rocket-compile-%

rocket-compile: rocket-compile-$(SCALA_VERSION_DEFAULT)
.PHONY: rocket-compile

rocket-compile-all: rocket-compile-$(SCALA_VERSION_2_11) rocket-compile-$(SCALA_VERSION_2_12)
.PHONY: rocket-compile-all

rocket-test-%: $(MILL)
	$(MILL) rocket[$(patsubst rocket-test-%,%,$@)].test
.PHONY: rocket-test-%

rocket-test: rocket-test-$(SCALA_VERSION_DEFAULT)
.PHONY: rocket-test

rocket-test-all: rocket-test-$(SCALA_VERSION_2_11) rocket-test-$(SCALA_VERSION_2_12)
.PHONY: rocket-test-all

clean:
	rm -rf ./out/dsptools ./out/rocket ./test_run_dir
.PHONY: clean

distclean: clean
	rm -rf ./bin ./out
.PHONY: disclean

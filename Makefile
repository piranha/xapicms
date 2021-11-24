VERSION = $(shell cat VERSION)
#export JAVA_HOME = $(HOME)/var/graalvm-ce-java11-21.1.0/Contents/Home
#export GRAALVM_HOME = $(JAVA_HOME)

# minus means "include but do not fail"
-include ./.config.mk

run:
	clj -M:dev

ancient:
	clojure -M:dev:ancient

upgrade:
	clojure -M:dev:ancient --upgrade

uber:
	clojure -Srepro -T:build uber

compile:
	clojure -Srepro -M:native

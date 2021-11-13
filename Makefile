VERSION = $(shell cat VERSION)
#export JAVA_HOME = $(HOME)/var/graalvm-ce-java11-21.1.0/Contents/Home
#export GRAALVM_HOME = $(JAVA_HOME)

include ./.config.mk

run:
	clj -M:dev

ancient:
	clojure -M:dev:ancient

upgrade:
	clojure -M:dev:ancient --upgrade

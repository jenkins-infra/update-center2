UPDATE_CENTER_ID ?= default

UC_CLI=$(shell ls target/update-center2-*-bin*/update-center2-*.jar)


.PHONY: test-weekly
test-weekly:
	java -jar $(UC_CLI) \
            -id $(UPDATE_CENTER_ID) -connectionCheckUrl http://www.google.com/ \
            -no-experimental -skip-release-history \
            -www ./output/latest -download-fallback ./output/htaccess \
            -cap 2.107.999 -capCore 2.999

.PHONY: test-lts
test-lts:
	java -jar $(UC_CLI) \
        -id $(UPDATE_CENTER_ID) -connectionCheckUrl http://www.google.com/ \
        -no-experimental -skip-release-history \
        -www ./output/stable -cap 2.107.999 -capCore 2.999 -stableCore

.PHONY: test-weekly-experimental
test-weekly-experimental:
	java -jar $(UC_CLI) \
        -id $(UPDATE_CENTER_ID) -connectionCheckUrl http://www.google.com/ \
        -skip-release-history \
        -www ./output/experimental -cap 2.107.999 -capCore 2.999

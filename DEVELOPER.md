Developer Guidelines
-------

## Quickstart

1. Run `mvn clean package` to build the CLI executable
2. Run `make test-weekly` to generate the update site in the `output` directory
  * First run will take a while, 
    because the update center generator will need to checkout many plugins
3. Checkout files generated in `output`

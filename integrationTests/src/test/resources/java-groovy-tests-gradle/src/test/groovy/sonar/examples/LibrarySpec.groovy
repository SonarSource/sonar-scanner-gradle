package sonar.examples

import spock.lang.Specification

class LibrarySpec extends Specification {

    def "should be Spock test"() {
        expect:
            new Library().someLibraryMethodSpock()
    }
}

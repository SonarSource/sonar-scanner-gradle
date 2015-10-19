package sonar.examples

import spock.lang.Specification

class LibraryJavaSpec extends Specification {

    def "should be Spock test"() {
        expect:
            new LibraryJava().someLibraryMethodSpock()
    }
}

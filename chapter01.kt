data class Person(
    val name: String,
    val age: Int? = null // 널이 될 수 있는 타입과 파라미터 디폴트 값
)

// 최상위 함수
fun main(args: Array<String>) {
    val persons = listOf(
        Person("Alice"),
        Person("Bob", age = 29) // 이름 붙은 파라미터
    )

    val oldest = persons.maxBy { it.age ?: 0 } // 람다 식과 엘비스 연산자
    println("The oldest is: $oldest") // 문자열 템플릿
}


val s: String? = null // 널이 될 수 있음
var s2: String = "" // 널이 될 수 없음
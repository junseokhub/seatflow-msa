fun main() {
  printLn("Hello")
}

// vs java
// 함수선언 fun
// 클래스를 선언할 필요가 없다. 선택사항. 클래스 없이 독립된 함수만 따로 존재하는 경우가 훨씬 더 많음.
// println  === java System.out.pringln()

// println
// 단순히 자바 sout 대신 호출 해주는 역할.
// System.out.println("") 써도 전혀 무방
@kotlin.internal.InlineOnly
public actual inline fun println(message: Any?) {
  System.out.pringln(message)
}
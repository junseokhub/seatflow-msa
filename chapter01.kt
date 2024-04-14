fun main(args: Array<String>) {
  println("Hello, world!")
}

fun max(a: Int, b: Int): Int {
  return if (a > b) a else b
}

fun max(a: Int, b: Int): Int = if (a > b) a else b

fun max(a: Int, b: Int) = if (a > b) a else b

val answer = 42 // 타입 생략
val answer: Int = 42 // 타입 지정

val answer: Int
answer = 42

val message: String
if (canPerformOperation()) {
		message = "Success"
		// ... 연산을 수행한다. 
} else {
		message = "Failed"
}

// NullPointerException
val a1: String? = null // null 
val a2: String = null // error

fun strLen(s: String): Int {
	return s.length
}

// ? 를 부여해서 안전한 타입으로 처리 -> 단순히 ? 만 써서 되는게 아니라 return 타입도 허용해줘야함.
fun strLen2(s: String?): Int? {
	return s?.length
}
// !!를 부여해 널이 아님을 확실히 하라고 알려준다.
// 허용은 해줫지만 실제로 null이 들어오면 exception 발생.
// -> !! 사용은 지양..
fun strLen3(s: String?): Int? {
	return s!!.length
}

// 가장 좋은 방법
// null값 여부를 검사해주는 것.
fun strLen4(s: String?): Int {
	return if (s == null) 0 else s.length
}

// 매번 if문으로 검사하기 귀찮다? 엘비스 연산자 사용
fun strLen5(s: String?): Int {
	return s?.length ? : 0
}
// 깔끔한 반복문
// java
// int sum = 0;
// for (int i = 1; i <= 10; i ++) {
//   sum += i;
// }
// System.out.pringln(sum);

var sum = 0
for (i in 1..10) {
  sum += i
}
println(sum)

// 범위 표현식에서 내림차순으로 하거나 2 이상 건너뛰어야 할 떄는
for (i in 10 downTo 0 step 2){
  ...
}

// when 조건
// java
// public String getTypeOfDayWithSwitchStatement(int dayOfWeek) {
//   String typeOfDay;
//   switch (dayOfWeek) {
//     case 1:
//       typeOfDay = "한 주 시작";
//       breat;
//     case 2:
//     case 3:
//     case 4:
//       typeOfDay = "주중";
//       break;
//     case 5:
//       typeOfDay = "한 주 끝";
//     case 6:
//     case 7:
//       typeOfDay = "주말";
//       break;
//     default:
//       throw new IllegalArgumentException("잘못된 날", + dayOfWeek);
//   }
//   return typeOfDay;
// }

fun getTypeOfDayWithSwitchStatement(dayOfWeek: Int): String {
  val typeOfDay: String = when (dayOfWeek) {
    1 -> "한 주 시작"
    2, 3, 4 -> "주중"
    5 -> "한 주 끝"
    6, 7 -> "주말"
    else -> throw IllegalArgumentException("잠롯된 날: $dayOfWeek")
  }
  return typeOfDay
}

// when 조건문에서 괄호 인자 생략 가능 
val result = when {
  number in 3..5 -> "숫자"
  char == "a" -> "문자"
  else -> {
    println("모름")
  }
}

// 편리한 함수 선언
// 자바는 오버로딩시 별개 메소드라 비슷한 역할을 하려면 제각각 구현해야함.
// 코틀린은 별도로 하지 않아도 된다. 명명 인자도 지원 -> 굳이 빌더 패턴을 사용할 필요가 없음.
// 리턴타입을 명시하지 않아도 자동 추론
fun sqaure(x: Int) = x * x
fun sqaure(x: Int) {
  return x * x
}
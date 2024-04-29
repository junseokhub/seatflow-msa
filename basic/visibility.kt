class VisiblityModifiers {
  fun publicMultiplyTwoNumbers(a: Int, b: Int): Int {
    return a * b
  }

  private fun privateMultiplyTwoNumbers(a: Int, b: Int): Int {
    return a * b
  }
}

open class Visibility {
  protected fun protectedMultiplyTwoNumbers(a: Int, b: Int): Int {
    return a * b
  }
}

// 기본적으로 코틀린은 public, final이 디폴트인데, 어디서나 접근할 수 있도록 열려 있되 상속은 허용하지 않는다.
